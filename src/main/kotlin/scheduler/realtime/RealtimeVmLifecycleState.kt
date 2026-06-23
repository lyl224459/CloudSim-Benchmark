package scheduler.realtime

import config.RealtimeSchedulingConfig
import org.cloudsimplus.vms.Vm

internal class RealtimeVmLifecycleAccounting(
    initialVmCount: Int,
) {
    var scaleOutCount: Int = 0
        private set
    var scaleInCount: Int = 0
        private set
    var activeVmPeak: Int = initialVmCount
        private set
    var autoscalingCost: Double = 0.0
        private set
    var coldStartDelayTotal: Double = 0.0
        private set
    var scaleCooldownSkippedCount: Int = 0
        private set
    var scaleInDrainCount: Int = 0
        private set
    var autoscalingVmSeconds: Double = 0.0
        private set
    private var autoscalingPressureTotal = 0.0
    private var deadlineSlackPressureTotal = 0.0
    private var arrivalRatePressureTotal = 0.0
    private var autoscalingPressureSamples = 0
    private var warmPoolHitCount = 0
    private var warmPoolDecisionCount = 0
    private var vmSecondsAccountedAt = 0.0

    val averageAutoscalingPressure: Double
        get() = if (autoscalingPressureSamples > 0) autoscalingPressureTotal / autoscalingPressureSamples else 0.0
    val averageDeadlineSlackPressure: Double
        get() =
            if (autoscalingPressureSamples > 0) {
                deadlineSlackPressureTotal / autoscalingPressureSamples
            } else {
                0.0
            }
    val averageArrivalRatePressure: Double
        get() = if (autoscalingPressureSamples > 0) arrivalRatePressureTotal / autoscalingPressureSamples else 0.0
    val warmPoolHitRate: Double
        get() = if (warmPoolDecisionCount > 0) warmPoolHitCount.toDouble() / warmPoolDecisionCount else 0.0

    fun recordScaleOut(
        scaleOutCost: Double,
        coldStartDelay: Double,
    ) {
        scaleOutCount++
        autoscalingCost += scaleOutCost
        coldStartDelayTotal += coldStartDelay
    }

    fun recordScaleIn() {
        scaleInCount++
    }

    fun recordScaleInDrain() {
        scaleInDrainCount++
    }

    fun recordCooldownSkipped() {
        scaleCooldownSkippedCount++
    }

    fun recordWarmPoolEvaluation(hit: Boolean) {
        warmPoolDecisionCount++
        if (hit) warmPoolHitCount++
    }

    fun recordAutoscalingPressure(
        autoscalingPressure: Double,
        deadlineSlackPressure: Double,
        arrivalRatePressure: Double,
    ) {
        autoscalingPressureTotal += autoscalingPressure.finiteOrZero()
        deadlineSlackPressureTotal += deadlineSlackPressure.finiteOrZero()
        arrivalRatePressureTotal += arrivalRatePressure.finiteOrZero()
        autoscalingPressureSamples++
    }

    fun accountDynamicVmSeconds(
        currentTime: Double,
        liveDynamicVmCount: Int,
        dynamicVmCostPerSecond: Double,
    ) {
        val elapsed = (currentTime - vmSecondsAccountedAt).coerceAtLeast(0.0)
        if (elapsed > 0.0 && liveDynamicVmCount > 0) {
            val vmSeconds = elapsed * liveDynamicVmCount
            autoscalingVmSeconds += vmSeconds
            autoscalingCost += vmSeconds * dynamicVmCostPerSecond.coerceAtLeast(0.0)
        }
        if (currentTime > vmSecondsAccountedAt) {
            vmSecondsAccountedAt = currentTime
        }
    }

    fun updatePeak(activeCount: Int) {
        if (activeCount > activeVmPeak) activeVmPeak = activeCount
    }
}

internal class RealtimeVmLifecycleQueries(
    private val snapshots: () -> Collection<RealtimeVmLifecycleSnapshot>,
) {
    fun dynamicVmCount(): Int = snapshots().count { it.isLiveDynamic }

    fun hasLiveDynamicVms(): Boolean = snapshots().any { it.isLiveDynamic }

    fun hasProvisioningVm(): Boolean = snapshots().any { it.isStarting }

    fun liveDynamicVmCount(): Int = snapshots().count { it.isLiveDynamic }

    fun activeOrStartingVmCount(): Int = snapshots().count { it.isActiveOrStarting }

    fun acceptingVmCount(): Int = snapshots().count { it.lifecycle == RealtimeVmLifecycle.ACTIVE }

    fun idleWarmDynamicVmCount(activeVmIndexes: Set<Int>): Int =
        snapshots().count { snapshot ->
            snapshot.dynamic &&
                snapshot.lifecycle == RealtimeVmLifecycle.ACTIVE &&
                snapshot.vmIndex !in activeVmIndexes
        }

    private val RealtimeVmLifecycleSnapshot.isLiveDynamic: Boolean
        get() = dynamic && lifecycle != RealtimeVmLifecycle.TERMINATED

    private val RealtimeVmLifecycleSnapshot.isStarting: Boolean
        get() = lifecycle == RealtimeVmLifecycle.PROVISIONING || lifecycle == RealtimeVmLifecycle.WARMING

    private val RealtimeVmLifecycleSnapshot.isActiveOrStarting: Boolean
        get() =
            lifecycle == RealtimeVmLifecycle.ACTIVE ||
                lifecycle == RealtimeVmLifecycle.WARMING ||
                lifecycle == RealtimeVmLifecycle.PROVISIONING
}

@Suppress("TooManyFunctions") // Lifecycle state owns transition, accounting and snapshot mutations together.
internal class RealtimeVmLifecycleState(
    initialVms: List<Vm>,
    private val scheduling: RealtimeSchedulingConfig,
) {
    private val mutableVms = initialVms.toMutableList()
    private val lifecycleByIndex = linkedMapOf<Int, RealtimeVmLifecycleSnapshot>()
    val accounting = RealtimeVmLifecycleAccounting(initialVms.size)

    init {
        initialVms.indices.forEach { index ->
            lifecycleByIndex[index] = activeInitialSnapshot(index)
        }
    }

    val vmList: List<Vm> get() = mutableVms

    fun snapshots(): Map<Int, RealtimeVmLifecycleSnapshot> = lifecycleByIndex.toMap()

    fun addDynamicVm(
        vm: Vm,
        currentTime: Double,
    ): Int {
        accountDynamicVmSeconds(currentTime)
        val index = mutableVms.size
        mutableVms.add(vm)
        lifecycleByIndex[index] =
            RealtimeVmLifecycleSnapshot(
                vmIndex = index,
                lifecycle = initialDynamicLifecycle(),
                dynamic = true,
                createdAt = currentTime,
                activeAt = currentTime + scheduling.vmColdStartDelay,
                lastBusyAt = currentTime,
            )
        accounting.recordScaleOut(scheduling.scaleOutCost, scheduling.vmColdStartDelay)
        updatePeak()
        return index
    }

    fun refresh(
        currentTime: Double,
        activeVmIndexes: Set<Int>,
    ) {
        accountDynamicVmSeconds(currentTime)
        lifecycleByIndex.toMap().forEach { (index, snapshot) ->
            lifecycleByIndex[index] = snapshot.refreshFor(currentTime, index in activeVmIndexes)
        }
        updatePeak()
    }

    fun scaleInIdle(
        currentTime: Double,
        activeVmIndexes: Set<Int>,
    ) {
        accountDynamicVmSeconds(currentTime)
        terminateDrained(activeVmIndexes)
        val removableCount = removableActiveDynamicCount()
        if (removableCount <= 0) {
            updatePeak()
            return
        }
        lifecycleByIndex
            .toMap()
            .entries
            .filter { (index, snapshot) -> shouldScaleIn(index, snapshot, currentTime, activeVmIndexes) }
            .take(removableCount)
            .forEach { (index, snapshot) ->
                if (scheduling.scaleInDrainEnabled) {
                    lifecycleByIndex[index] = snapshot.copy(lifecycle = RealtimeVmLifecycle.DRAINING)
                    accounting.recordScaleInDrain()
                } else {
                    lifecycleByIndex[index] = snapshot.copy(lifecycle = RealtimeVmLifecycle.TERMINATED)
                    accounting.recordScaleIn()
                }
            }
        updatePeak()
    }

    fun markBusy(
        vmIndex: Int,
        currentTime: Double,
    ) {
        val snapshot = lifecycleByIndex[vmIndex] ?: return
        lifecycleByIndex[vmIndex] = snapshot.copy(lastBusyAt = currentTime)
    }

    private fun activeInitialSnapshot(index: Int): RealtimeVmLifecycleSnapshot =
        RealtimeVmLifecycleSnapshot(
            vmIndex = index,
            lifecycle = RealtimeVmLifecycle.ACTIVE,
            dynamic = false,
            createdAt = 0.0,
            activeAt = 0.0,
            lastBusyAt = 0.0,
        )

    private fun initialDynamicLifecycle(): RealtimeVmLifecycle =
        if (scheduling.vmColdStartDelay > 0.0) RealtimeVmLifecycle.WARMING else RealtimeVmLifecycle.ACTIVE

    private fun RealtimeVmLifecycleSnapshot.refreshFor(
        currentTime: Double,
        busy: Boolean,
    ): RealtimeVmLifecycleSnapshot {
        val warmed =
            if (lifecycle == RealtimeVmLifecycle.WARMING && currentTime >= activeAt) {
                copy(lifecycle = RealtimeVmLifecycle.ACTIVE)
            } else {
                this
            }
        return if (busy) warmed.copy(lastBusyAt = currentTime) else warmed
    }

    private fun shouldScaleIn(
        index: Int,
        snapshot: RealtimeVmLifecycleSnapshot,
        currentTime: Double,
        activeVmIndexes: Set<Int>,
    ): Boolean =
        snapshot.dynamic &&
            snapshot.lifecycle == RealtimeVmLifecycle.ACTIVE &&
            index !in activeVmIndexes &&
            currentTime - snapshot.createdAt >= scheduling.scaleInProtectionTime &&
            currentTime - snapshot.lastBusyAt >= scheduling.scaleInIdleTime

    private fun terminateDrained(activeVmIndexes: Set<Int>) {
        lifecycleByIndex
            .toMap()
            .filter { (index, snapshot) ->
                snapshot.dynamic &&
                    snapshot.lifecycle == RealtimeVmLifecycle.DRAINING &&
                    index !in activeVmIndexes
            }.forEach { (index, snapshot) ->
                lifecycleByIndex[index] = snapshot.copy(lifecycle = RealtimeVmLifecycle.TERMINATED)
                accounting.recordScaleIn()
            }
    }

    private fun removableActiveDynamicCount(): Int {
        val activeOrStarting = lifecycleByIndex.values.count { it.isActiveOrStarting }
        return (activeOrStarting - scheduling.minActiveVms.coerceAtLeast(0)).coerceAtLeast(0)
    }

    private fun accountDynamicVmSeconds(currentTime: Double) {
        accounting.accountDynamicVmSeconds(
            currentTime = currentTime,
            liveDynamicVmCount = lifecycleByIndex.values.count { it.isLiveDynamic },
            dynamicVmCostPerSecond = scheduling.dynamicVmCostPerSecond,
        )
    }

    private fun updatePeak() {
        accounting.updatePeak(lifecycleByIndex.values.count { it.isActiveOrStarting })
    }

    private val RealtimeVmLifecycleSnapshot.isLiveDynamic: Boolean
        get() = dynamic && lifecycle != RealtimeVmLifecycle.TERMINATED

    private val RealtimeVmLifecycleSnapshot.isActiveOrStarting: Boolean
        get() =
            lifecycle == RealtimeVmLifecycle.ACTIVE ||
                lifecycle == RealtimeVmLifecycle.WARMING ||
                lifecycle == RealtimeVmLifecycle.PROVISIONING
}

private fun Double.finiteOrZero(): Double = if (isFinite()) this else 0.0
