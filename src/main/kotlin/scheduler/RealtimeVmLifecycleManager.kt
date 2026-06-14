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

@Suppress("TooManyFunctions") // Lifecycle facade preserves the broker-facing transition and query API.
class RealtimeVmLifecycleManager(
    initialVms: List<Vm>,
    private val scheduling: RealtimeSchedulingConfig,
    private val topologyModel: RealtimeTopologyModel = RealtimeTopologyModel.Disabled,
) {
    private val state = RealtimeVmLifecycleState(initialVms, scheduling)
    private val queries = RealtimeVmLifecycleQueries { state.snapshots().values }

    val vmList: List<Vm> get() = state.vmList

    fun snapshots(): Map<Int, RealtimeVmLifecycleSnapshot> = state.snapshots()

    fun getScaleOutCount(): Int = state.accounting.scaleOutCount

    fun getScaleInCount(): Int = state.accounting.scaleInCount

    fun getActiveVmPeak(): Int = state.accounting.activeVmPeak

    fun getAutoscalingCost(): Double = state.accounting.autoscalingCost

    fun getColdStartDelayTotal(): Double = state.accounting.coldStartDelayTotal

    fun hasLiveDynamicVms(): Boolean = queries.hasLiveDynamicVms()

    fun maybeScaleOut(
        queueDepth: Int,
        currentTime: Double,
        activeVmIndexes: Set<Int> = emptySet(),
    ): List<Vm> {
        if (!canScaleOut(queueDepth)) return emptyList()

        val vm = createDynamicVm()
        val index = state.addDynamicVm(vm, currentTime)
        topologyModel.registerDynamicVm(index, activeVmIndexes)
        return listOf(vm)
    }

    fun refresh(
        currentTime: Double,
        activeVmIndexes: Set<Int>,
    ) {
        state.refresh(currentTime, activeVmIndexes)
    }

    fun maybeScaleIn(
        currentTime: Double,
        activeVmIndexes: Set<Int>,
    ) {
        if (!scheduling.autoscalingEnabled) return
        if (scheduling.scaleInIdleTime <= 0.0) return

        state.scaleInIdle(currentTime, activeVmIndexes)
    }

    fun markBusy(
        vmIndex: Int,
        currentTime: Double,
    ) {
        state.markBusy(vmIndex, currentTime)
    }

    private fun createDynamicVm(): Vm =
        VmSimple(DatacenterConfig.M_MIPS.toDouble(), 1)
            .setRam(DatacenterConfig.RAM.toLong())
            .setBw(DatacenterConfig.BW.toLong())
            .setSize(DatacenterConfig.IMAGE_SIZE)
            .setCloudletScheduler(CloudletSchedulerSpaceShared())

    private fun canScaleOut(queueDepth: Int): Boolean =
        scheduling.autoscalingEnabled &&
            scheduling.maxDynamicVms > queries.dynamicVmCount() &&
            queueDepth >= scheduling.scaleOutQueueThreshold.coerceAtLeast(1) &&
            !queries.hasProvisioningVm()
}
