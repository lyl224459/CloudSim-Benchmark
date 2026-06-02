package scheduler

import config.DatacenterConfig
import config.RealtimeSchedulingConfig
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared
import org.cloudsimplus.vms.Vm
import org.cloudsimplus.vms.VmSimple

enum class RealtimeVmLifecycle {
    PROVISIONING,
    WARMING,
    ACTIVE,
    DRAINING,
    TERMINATED,
}

data class RealtimeVmLifecycleSnapshot(
    val vmIndex: Int,
    val lifecycle: RealtimeVmLifecycle,
    val dynamic: Boolean,
    val createdAt: Double,
    val activeAt: Double,
    val lastBusyAt: Double,
) {
    val acceptingWork: Boolean get() = lifecycle == RealtimeVmLifecycle.ACTIVE
}

class RealtimeVmLifecycleManager(
    initialVms: List<Vm>,
    private val scheduling: RealtimeSchedulingConfig,
    private val topologyModel: RealtimeTopologyModel = RealtimeTopologyModel.Disabled,
) {
    private val mutableVms = initialVms.toMutableList()
    private val lifecycleByIndex = linkedMapOf<Int, RealtimeVmLifecycleSnapshot>()
    private var scaleOutCount = 0
    private var scaleInCount = 0
    private var activeVmPeak = initialVms.size
    private var autoscalingCost = 0.0
    private var coldStartDelayTotal = 0.0

    init {
        initialVms.indices.forEach { index ->
            lifecycleByIndex[index] =
                RealtimeVmLifecycleSnapshot(
                    vmIndex = index,
                    lifecycle = RealtimeVmLifecycle.ACTIVE,
                    dynamic = false,
                    createdAt = 0.0,
                    activeAt = 0.0,
                    lastBusyAt = 0.0,
                )
        }
    }

    val vmList: List<Vm> get() = mutableVms

    fun snapshots(): Map<Int, RealtimeVmLifecycleSnapshot> = lifecycleByIndex.toMap()

    fun getScaleOutCount(): Int = scaleOutCount

    fun getScaleInCount(): Int = scaleInCount

    fun getActiveVmPeak(): Int = activeVmPeak

    fun getAutoscalingCost(): Double = autoscalingCost

    fun getColdStartDelayTotal(): Double = coldStartDelayTotal

    fun hasLiveDynamicVms(): Boolean = lifecycleByIndex.values.any { it.dynamic && it.lifecycle != RealtimeVmLifecycle.TERMINATED }

    fun maybeScaleOut(
        queueDepth: Int,
        currentTime: Double,
        activeVmIndexes: Set<Int> = emptySet(),
    ): List<Vm> {
        if (!scheduling.autoscalingEnabled) return emptyList()
        if (scheduling.maxDynamicVms <= dynamicVmCount()) return emptyList()
        if (queueDepth < scheduling.scaleOutQueueThreshold.coerceAtLeast(1)) return emptyList()
        if (hasProvisioningVm()) return emptyList()

        val vm = createDynamicVm()
        val index = mutableVms.size
        mutableVms.add(vm)
        topologyModel.registerDynamicVm(index, activeVmIndexes)
        val lifecycle =
            if (scheduling.vmColdStartDelay > 0.0) {
                RealtimeVmLifecycle.WARMING
            } else {
                RealtimeVmLifecycle.ACTIVE
            }
        lifecycleByIndex[index] =
            RealtimeVmLifecycleSnapshot(
                vmIndex = index,
                lifecycle = lifecycle,
                dynamic = true,
                createdAt = currentTime,
                activeAt = currentTime + scheduling.vmColdStartDelay,
                lastBusyAt = currentTime,
            )
        scaleOutCount++
        autoscalingCost += scheduling.scaleOutCost
        coldStartDelayTotal += scheduling.vmColdStartDelay
        updatePeak()
        return listOf(vm)
    }

    fun refresh(
        currentTime: Double,
        activeVmIndexes: Set<Int>,
    ) {
        for ((index, snapshot) in lifecycleByIndex.toMap()) {
            val warmed =
                if (snapshot.lifecycle == RealtimeVmLifecycle.WARMING && currentTime >= snapshot.activeAt) {
                    snapshot.copy(lifecycle = RealtimeVmLifecycle.ACTIVE)
                } else {
                    snapshot
                }

            val busyAware =
                if (index in activeVmIndexes) {
                    warmed.copy(lastBusyAt = currentTime)
                } else {
                    warmed
                }

            lifecycleByIndex[index] = busyAware
        }
        updatePeak()
    }

    fun maybeScaleIn(
        currentTime: Double,
        activeVmIndexes: Set<Int>,
    ) {
        if (!scheduling.autoscalingEnabled) return
        if (scheduling.scaleInIdleTime <= 0.0) return

        for ((index, snapshot) in lifecycleByIndex.toMap()) {
            if (!snapshot.dynamic) continue
            if (snapshot.lifecycle != RealtimeVmLifecycle.ACTIVE) continue
            if (index in activeVmIndexes) continue
            if (currentTime - snapshot.createdAt < scheduling.scaleInProtectionTime) continue
            if (currentTime - snapshot.lastBusyAt < scheduling.scaleInIdleTime) continue

            lifecycleByIndex[index] = snapshot.copy(lifecycle = RealtimeVmLifecycle.TERMINATED)
            scaleInCount++
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

    private fun createDynamicVm(): Vm =
        VmSimple(DatacenterConfig.M_MIPS.toDouble(), 1)
            .setRam(DatacenterConfig.RAM.toLong())
            .setBw(DatacenterConfig.BW.toLong())
            .setSize(DatacenterConfig.IMAGE_SIZE)
            .setCloudletScheduler(CloudletSchedulerSpaceShared())

    private fun dynamicVmCount(): Int = lifecycleByIndex.values.count { it.dynamic && it.lifecycle != RealtimeVmLifecycle.TERMINATED }

    private fun hasProvisioningVm(): Boolean =
        lifecycleByIndex.values.any { it.lifecycle == RealtimeVmLifecycle.PROVISIONING || it.lifecycle == RealtimeVmLifecycle.WARMING }

    private fun updatePeak() {
        val active =
            lifecycleByIndex.values.count {
                it.lifecycle == RealtimeVmLifecycle.ACTIVE ||
                    it.lifecycle == RealtimeVmLifecycle.WARMING ||
                    it.lifecycle == RealtimeVmLifecycle.PROVISIONING
            }
        if (active > activeVmPeak) activeVmPeak = active
    }
}
