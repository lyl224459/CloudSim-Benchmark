package broker

import config.RealtimeAutoscalingPolicy
import config.RealtimeSchedulingConfig
import scheduler.RealtimeCandidateScoreCalculator
import scheduler.RealtimeSchedulingContext
import scheduler.RealtimeVmLifecycleManager
import kotlin.math.ceil

private const val MIN_PRESSURE_THRESHOLD = 1.0

internal data class RealtimeAutoscalingPressure(
    val queuePressure: Double,
    val deadlineSlackPressure: Double,
    val arrivalRatePressure: Double,
) {
    val total: Double get() = queuePressure + deadlineSlackPressure + arrivalRatePressure
}

@Suppress("TooManyFunctions") // Autoscaling controller keeps policy steps explicit for broker workflow wiring.
internal class RealtimeAutoscalingController(
    private val scheduling: RealtimeSchedulingConfig,
    private val vmLifecycleManager: RealtimeVmLifecycleManager,
    private val scoreCalculator: RealtimeCandidateScoreCalculator = RealtimeCandidateScoreCalculator(),
) {
    private val arrivalTimes = ArrayDeque<Double>()
    private var lastScaleOutAt: Double? = null

    fun refresh(
        currentTime: Double,
        activeVmIndexes: Set<Int>,
    ) {
        vmLifecycleManager.refresh(currentTime, activeVmIndexes)
    }

    fun recordArrival(currentTime: Double) {
        arrivalTimes.addLast(currentTime)
        pruneArrivalWindow(currentTime)
    }

    fun scaleOutCommands(
        queueDepth: Int,
        currentTime: Double,
        activeVmIndexes: Set<Int>,
        context: RealtimeSchedulingContext? = null,
    ): List<RealtimeBrokerCommand> {
        if (!scheduling.autoscalingEnabled) return emptyList()
        return when (scheduling.normalizedAutoscalingPolicy()) {
            RealtimeAutoscalingPolicy.QUEUE_THRESHOLD ->
                scaleOutCommandsFor(vmLifecycleManager.maybeScaleOut(queueDepth, currentTime, activeVmIndexes))
            RealtimeAutoscalingPolicy.DEADLINE_PREDICTIVE ->
                advancedScaleOutCommands(queueDepth, currentTime, activeVmIndexes, context)
        }
    }

    fun tickCommands(
        currentTime: Double,
        activeVmIndexes: Set<Int>,
        queueDepth: Int = 0,
    ): List<RealtimeBrokerCommand> {
        vmLifecycleManager.refresh(currentTime, activeVmIndexes)
        val scaleOutCommands =
            if (usesEvaluationLoop()) {
                advancedScaleOutCommands(queueDepth, currentTime, activeVmIndexes, context = null)
            } else {
                emptyList()
            }
        vmLifecycleManager.maybeScaleIn(currentTime, activeVmIndexes)
        return scaleOutCommands + nextTickCommands()
    }

    fun initialTickDelay(): Double? = autoscalingTickDelay().takeIf { it > 0.0 }

    private fun advancedScaleOutCommands(
        queueDepth: Int,
        currentTime: Double,
        activeVmIndexes: Set<Int>,
        context: RealtimeSchedulingContext?,
    ): List<RealtimeBrokerCommand> {
        val pressure = pressureFor(queueDepth, currentTime, context)
        vmLifecycleManager.recordAutoscalingPressure(
            autoscalingPressure = pressure.total,
            deadlineSlackPressure = pressure.deadlineSlackPressure,
            arrivalRatePressure = pressure.arrivalRatePressure,
        )
        recordWarmPoolAvailability(activeVmIndexes)

        val minActiveNeed = minActiveScaleOutNeed()
        val pressureNeed = pressureScaleOutNeed(pressure, currentTime)
        val warmPoolNeed = warmPoolScaleOutNeed(activeVmIndexes)
        val requested = scaleOutRequest(minActiveNeed, pressureNeed, warmPoolNeed)
        val newVms = vmLifecycleManager.scaleOut(requested, currentTime, activeVmIndexes)
        if (newVms.isNotEmpty()) {
            lastScaleOutAt = currentTime
        }
        return scaleOutCommandsFor(newVms)
    }

    private fun pressureFor(
        queueDepth: Int,
        currentTime: Double,
        context: RealtimeSchedulingContext?,
    ): RealtimeAutoscalingPressure =
        RealtimeAutoscalingPressure(
            queuePressure = queuePressure(queueDepth),
            deadlineSlackPressure = deadlineSlackPressure(context),
            arrivalRatePressure = arrivalRatePressure(currentTime),
        )

    private fun queuePressure(queueDepth: Int): Double {
        val threshold = scheduling.scaleOutQueueThreshold.coerceAtLeast(1)
        return queueDepth.coerceAtLeast(0).toDouble() / threshold.toDouble()
    }

    private fun deadlineSlackPressure(context: RealtimeSchedulingContext?): Double =
        if (context?.taskMetadata?.deadline == null) {
            0.0
        } else {
            val scores = scoreCalculator.scoreAccepted(context)
            if (scores.isEmpty()) {
                1.0
            } else {
                val worstLateness = scores.maxOf { (-it.breakdown.deadlineSlack).coerceAtLeast(0.0) }
                val runtimeBase = scores.map { it.breakdown.estimatedRuntime }.averageOrZero().coerceAtLeast(1.0)
                (worstLateness / runtimeBase).finiteOrZero()
            }
        }

    private fun arrivalRatePressure(currentTime: Double): Double {
        pruneArrivalWindow(currentTime)
        if (scheduling.arrivalRateWindow <= 0.0 || scheduling.predictiveLookahead <= 0.0) return 0.0
        val arrivalRate = arrivalTimes.size.toDouble() / scheduling.arrivalRateWindow
        val projectedArrivals = arrivalRate * scheduling.predictiveLookahead
        val capacityBase =
            vmLifecycleManager
                .acceptingVmCount()
                .coerceAtLeast(1) * scheduling.scaleOutQueueThreshold.coerceAtLeast(1)
        return (projectedArrivals / capacityBase.toDouble()).finiteOrZero()
    }

    private fun pressureScaleOutNeed(
        pressure: RealtimeAutoscalingPressure,
        currentTime: Double,
    ): Int {
        val threshold = scheduling.scalePressureThreshold.coerceAtLeast(MIN_PRESSURE_THRESHOLD)
        return when {
            pressure.total < threshold -> 0
            isInCooldown(currentTime) -> {
                vmLifecycleManager.recordScaleCooldownSkipped()
                0
            }
            else -> ceil(pressure.total / threshold).toInt().coerceAtLeast(1)
        }
    }

    private fun warmPoolScaleOutNeed(activeVmIndexes: Set<Int>): Int =
        (scheduling.warmPoolSize - vmLifecycleManager.idleWarmDynamicVmCount(activeVmIndexes)).coerceAtLeast(0)

    @Suppress("MaxLineLength") // ktlint requires this simple helper to remain an expression body.
    private fun minActiveScaleOutNeed(): Int = (scheduling.minActiveVms - vmLifecycleManager.activeOrStartingVmCount()).coerceAtLeast(0)

    private fun scaleOutRequest(
        minActiveNeed: Int,
        pressureNeed: Int,
        warmPoolNeed: Int,
    ): Int {
        val nonCriticalNeed = maxOf(pressureNeed, warmPoolNeed)
        val batchLimitedNeed = minOf(nonCriticalNeed, scheduling.scaleOutBatchSize.coerceAtLeast(1))
        return maxOf(minActiveNeed, batchLimitedNeed)
    }

    private fun isInCooldown(currentTime: Double): Boolean {
        val last = lastScaleOutAt ?: return false
        return scheduling.scaleCooldown > 0.0 && currentTime - last < scheduling.scaleCooldown
    }

    private fun recordWarmPoolAvailability(activeVmIndexes: Set<Int>) {
        if (scheduling.warmPoolSize <= 0) return
        vmLifecycleManager.recordWarmPoolEvaluation(
            vmLifecycleManager.idleWarmDynamicVmCount(activeVmIndexes) >= scheduling.warmPoolSize,
        )
    }

    private fun scaleOutCommandsFor(newVms: List<org.cloudsimplus.vms.Vm>): List<RealtimeBrokerCommand> =
        buildList {
            if (newVms.isNotEmpty()) {
                add(RealtimeBrokerCommand.SubmitVms(newVms, scheduling.vmColdStartDelay))
            }
            autoscalingTickDelay().takeIf { newVms.isNotEmpty() && it > 0.0 }?.let { delay ->
                add(RealtimeBrokerCommand.ScheduleAutoscaleTick(delay))
            }
        }

    private fun nextTickCommands(): List<RealtimeBrokerCommand> =
        autoscalingTickDelay()
            .takeIf { it > 0.0 && shouldScheduleNextTick() }
            ?.let { listOf(RealtimeBrokerCommand.ScheduleAutoscaleTick(it)) }
            ?: emptyList()

    private fun shouldScheduleNextTick(): Boolean =
        scheduling.autoscalingEnabled &&
            (usesEvaluationLoop() || vmLifecycleManager.hasLiveDynamicVms())

    private fun autoscalingTickDelay(): Double {
        if (!scheduling.autoscalingEnabled) return 0.0
        val delays =
            listOfNotNull(
                scheduling.scaleInIdleTime.takeIf { it > 0.0 },
                scheduling.autoscalingEvaluationInterval.takeIf { usesEvaluationLoop() && it > 0.0 },
            )
        return delays.minOrNull() ?: 0.0
    }

    private fun usesEvaluationLoop(): Boolean =
        scheduling.autoscalingEnabled &&
            (
                scheduling.normalizedAutoscalingPolicy() == RealtimeAutoscalingPolicy.DEADLINE_PREDICTIVE ||
                    scheduling.warmPoolSize > 0 ||
                    scheduling.minActiveVms > 0
            )

    private fun pruneArrivalWindow(currentTime: Double) {
        val earliest = currentTime - scheduling.arrivalRateWindow.coerceAtLeast(0.0)
        while (arrivalTimes.isNotEmpty() && arrivalTimes.first() < earliest) {
            arrivalTimes.removeFirst()
        }
    }
}

private fun List<Double>.averageOrZero(): Double = takeIf { it.isNotEmpty() }?.average() ?: 0.0

private fun Double.finiteOrZero(): Double = if (isFinite()) this else 0.0
