package broker

import config.RealtimeAutoscalingPolicy
import config.RealtimeSchedulingConfig
import scheduler.RealtimeCandidateScoreCalculator
import scheduler.RealtimeObservationEventScope
import scheduler.RealtimeObservationEventType
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
    private val metrics: RealtimeBrokerMetrics? = null,
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
                queueThresholdScaleOutCommands(queueDepth, currentTime, activeVmIndexes)
            RealtimeAutoscalingPolicy.DEADLINE_PREDICTIVE ->
                advancedScaleOutCommands(queueDepth, currentTime, activeVmIndexes, context)
        }
    }

    fun tickCommands(
        currentTime: Double,
        activeVmIndexes: Set<Int>,
        queueDepth: Int = 0,
        continueEvaluating: Boolean = true,
    ): List<RealtimeBrokerCommand> {
        vmLifecycleManager.refresh(currentTime, activeVmIndexes)
        val scaleOutCommands =
            if (continueEvaluating && usesEvaluationLoop()) {
                advancedScaleOutCommands(queueDepth, currentTime, activeVmIndexes, context = null)
            } else {
                emptyList()
            }
        val scaleInBefore = vmLifecycleManager.getScaleInCount()
        val drainBefore = vmLifecycleManager.getScaleInDrainCount()
        vmLifecycleManager.maybeScaleIn(currentTime, activeVmIndexes)
        recordScaleInObservations(
            currentTime = currentTime,
            queueDepth = queueDepth,
            scaleInDelta = vmLifecycleManager.getScaleInCount() - scaleInBefore,
            drainDelta = vmLifecycleManager.getScaleInDrainCount() - drainBefore,
        )
        return scaleOutCommands + nextTickCommands(continueEvaluating)
    }

    fun initialTickDelay(): Double? = autoscalingTickDelay().takeIf { it > 0.0 }

    private fun queueThresholdScaleOutCommands(
        queueDepth: Int,
        currentTime: Double,
        activeVmIndexes: Set<Int>,
    ): List<RealtimeBrokerCommand> {
        val newVms = vmLifecycleManager.maybeScaleOut(queueDepth, currentTime, activeVmIndexes)
        recordAutoscalingEvaluation(
            currentTime = currentTime,
            queueDepth = queueDepth,
            pressure = null,
            decision = "queue_threshold",
        )
        recordScaleOutObservation(currentTime, queueDepth, newVms.size)
        return scaleOutCommandsFor(newVms)
    }

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
        recordAutoscalingEvaluation(
            currentTime = currentTime,
            queueDepth = queueDepth,
            pressure = pressure,
            decision = scheduling.autoscalingPolicy,
        )
        recordWarmPoolAvailability(currentTime, activeVmIndexes)

        val minActiveNeed = minActiveScaleOutNeed()
        val pressureNeed = pressureScaleOutNeed(pressure, currentTime).takeIf { queueDepth > 0 || context != null } ?: 0
        val warmPoolNeed = warmPoolScaleOutNeed(activeVmIndexes)
        val requested = scaleOutRequest(minActiveNeed, pressureNeed, warmPoolNeed)
        val newVms = vmLifecycleManager.scaleOut(requested, currentTime, activeVmIndexes)
        if (newVms.isNotEmpty()) {
            lastScaleOutAt = currentTime
        }
        recordScaleOutObservation(currentTime, queueDepth, newVms.size)
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
                metrics?.recordBrokerObservation(
                    eventTime = currentTime,
                    eventType = RealtimeObservationEventType.AUTOSCALING_COOLDOWN_SKIPPED,
                    eventScope = RealtimeObservationEventScope.AUTOSCALING,
                    decision = "cooldown",
                    autoscalingPressure = pressure.total,
                )
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

    private fun recordWarmPoolAvailability(
        currentTime: Double,
        activeVmIndexes: Set<Int>,
    ) {
        if (scheduling.warmPoolSize <= 0) return
        val hit = vmLifecycleManager.idleWarmDynamicVmCount(activeVmIndexes) >= scheduling.warmPoolSize
        vmLifecycleManager.recordWarmPoolEvaluation(
            hit,
        )
        metrics?.recordBrokerObservation(
            eventTime = currentTime,
            eventType = RealtimeObservationEventType.AUTOSCALING_WARM_POOL,
            eventScope = RealtimeObservationEventScope.AUTOSCALING,
            decision = if (hit) "hit" else "miss",
            activeVmCount = vmLifecycleManager.activeOrStartingVmCount(),
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

    private fun nextTickCommands(continueEvaluating: Boolean): List<RealtimeBrokerCommand> =
        autoscalingTickDelay()
            .takeIf { it > 0.0 && shouldScheduleNextTick(continueEvaluating) }
            ?.let { listOf(RealtimeBrokerCommand.ScheduleAutoscaleTick(it)) }
            ?: emptyList()

    private fun shouldScheduleNextTick(continueEvaluating: Boolean): Boolean =
        scheduling.autoscalingEnabled &&
            ((continueEvaluating && usesEvaluationLoop()) || vmLifecycleManager.hasLiveDynamicVms())

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

    private fun recordAutoscalingEvaluation(
        currentTime: Double,
        queueDepth: Int,
        pressure: RealtimeAutoscalingPressure?,
        decision: String,
    ) {
        metrics?.recordBrokerObservation(
            eventTime = currentTime,
            eventType = RealtimeObservationEventType.AUTOSCALING_EVALUATED,
            eventScope = RealtimeObservationEventScope.AUTOSCALING,
            decision = decision,
            queueDepth = queueDepth,
            activeVmCount = vmLifecycleManager.activeOrStartingVmCount(),
            autoscalingPressure = pressure?.total,
        )
    }

    private fun recordScaleOutObservation(
        currentTime: Double,
        queueDepth: Int,
        newVmCount: Int,
    ) {
        if (newVmCount <= 0) return
        metrics?.recordBrokerObservation(
            eventTime = currentTime,
            eventType = RealtimeObservationEventType.AUTOSCALING_SCALE_OUT,
            eventScope = RealtimeObservationEventScope.AUTOSCALING,
            decision = "count=$newVmCount",
            queueDepth = queueDepth,
            activeVmCount = vmLifecycleManager.activeOrStartingVmCount(),
        )
    }

    private fun recordScaleInObservations(
        currentTime: Double,
        queueDepth: Int,
        scaleInDelta: Int,
        drainDelta: Int,
    ) {
        if (drainDelta > 0) {
            metrics?.recordBrokerObservation(
                eventTime = currentTime,
                eventType = RealtimeObservationEventType.AUTOSCALING_DRAIN,
                eventScope = RealtimeObservationEventScope.AUTOSCALING,
                decision = "count=$drainDelta",
                queueDepth = queueDepth,
                activeVmCount = vmLifecycleManager.activeOrStartingVmCount(),
            )
        }
        if (scaleInDelta > 0) {
            metrics?.recordBrokerObservation(
                eventTime = currentTime,
                eventType = RealtimeObservationEventType.AUTOSCALING_SCALE_IN,
                eventScope = RealtimeObservationEventScope.AUTOSCALING,
                decision = "count=$scaleInDelta",
                queueDepth = queueDepth,
                activeVmCount = vmLifecycleManager.activeOrStartingVmCount(),
            )
        }
    }
}

private fun List<Double>.averageOrZero(): Double = takeIf { it.isNotEmpty() }?.average() ?: 0.0

private fun Double.finiteOrZero(): Double = if (isFinite()) this else 0.0
