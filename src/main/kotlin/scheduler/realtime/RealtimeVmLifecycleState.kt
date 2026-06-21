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

    private val RealtimeVmLifecycleSnapshot.isLiveDynamic: Boolean
        get() = dynamic && lifecycle != RealtimeVmLifecycle.TERMINATED

    private val RealtimeVmLifecycleSnapshot.isStarting: Boolean
        get() = lifecycle == RealtimeVmLifecycle.PROVISIONING || lifecycle == RealtimeVmLifecycle.WARMING
}

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
        lifecycleByIndex.toMap().forEach { (index, snapshot) ->
            lifecycleByIndex[index] = snapshot.refreshFor(currentTime, index in activeVmIndexes)
        }
        updatePeak()
    }

    fun scaleInIdle(
        currentTime: Double,
        activeVmIndexes: Set<Int>,
    ) {
        lifecycleByIndex
            .toMap()
            .filter { (index, snapshot) -> shouldScaleIn(index, snapshot, currentTime, activeVmIndexes) }
            .forEach { (index, snapshot) ->
                lifecycleByIndex[index] = snapshot.copy(lifecycle = RealtimeVmLifecycle.TERMINATED)
                accounting.recordScaleIn()
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

    private fun updatePeak() {
        accounting.updatePeak(lifecycleByIndex.values.count { it.isActiveOrStarting })
    }

    private val RealtimeVmLifecycleSnapshot.isActiveOrStarting: Boolean
        get() =
            lifecycle == RealtimeVmLifecycle.ACTIVE ||
                lifecycle == RealtimeVmLifecycle.WARMING ||
                lifecycle == RealtimeVmLifecycle.PROVISIONING
}
