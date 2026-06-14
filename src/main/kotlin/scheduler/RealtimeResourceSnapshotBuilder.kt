package scheduler

import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import kotlin.math.max

data class ResourceSnapshotRequest(
    val activeCloudlets: List<Cloudlet>,
    val currentTime: Double,
    val reservedVmIndexes: Map<Long, Int> = emptyMap(),
    val lifecycleSnapshots: Map<Int, RealtimeVmLifecycleSnapshot> = emptyMap(),
    val incomingCloudlet: Cloudlet? = null,
)

class ResourceSnapshotBuilder(
    private val vmList: List<Vm>,
    private val vmQueueCapacity: Int = 0,
    private val resourceModel: RealtimeResourceModel = RealtimeResourceModel.Disabled,
    private val topologyModel: RealtimeTopologyModel = RealtimeTopologyModel.Disabled,
) {
    private val vmIndexById: Map<Long, Int> = vmList.mapIndexed { index, vm -> vm.id to index }.toMap()

    fun build(request: ResourceSnapshotRequest): List<RealtimeNodeState> {
        val accumulators = ResourceSnapshotAccumulators(vmList.size)
        request.activeCloudlets.forEach { cloudlet ->
            addActiveCloudlet(cloudlet, request, accumulators)
        }
        request.reservedVmIndexes.forEach { (cloudletId, vmIndex) ->
            addPendingReservation(cloudletId, vmIndex, accumulators)
        }
        val maxQueueDepth = accumulators.maxQueueDepth()
        return vmList.mapIndexed { index, vm ->
            nodeState(index, vm, request, accumulators, maxQueueDepth)
        }
    }

    private fun addActiveCloudlet(
        cloudlet: Cloudlet,
        request: ResourceSnapshotRequest,
        accumulators: ResourceSnapshotAccumulators,
    ) {
        accumulators.activeCloudletIds.add(cloudlet.id)
        val vmIndex = request.reservedVmIndexes[cloudlet.id] ?: vmIndexById[cloudlet.vm?.id] ?: -1
        if (vmIndex < 0) return

        val vm = vmList[vmIndex]
        accumulators.loads[vmIndex] += cloudlet.length.toDouble() / vm.mips + resourceModel.resourceDelay(cloudlet, vm)
        accumulators.ramDemand[vmIndex] += resourceModel.ramDemand(cloudlet)
        accumulators.bwDemand[vmIndex] += resourceModel.bwDemand(cloudlet)
        accumulators.ioDemand[vmIndex] += resourceModel.ioDemand(cloudlet)
        if (cloudlet.status == Cloudlet.Status.INEXEC) {
            accumulators.runningCounts[vmIndex]++
        }
        accumulators.queueDepths[vmIndex]++
        accumulators.trackFailureDomain(topologyModel, vmIndex)
    }

    private fun addPendingReservation(
        cloudletId: Long,
        vmIndex: Int,
        accumulators: ResourceSnapshotAccumulators,
    ) {
        if (cloudletId in accumulators.activeCloudletIds || vmIndex !in vmList.indices) return
        accumulators.pendingCounts[vmIndex]++
        accumulators.queueDepths[vmIndex]++
        accumulators.trackFailureDomain(topologyModel, vmIndex)
    }

    private fun nodeState(
        index: Int,
        vm: Vm,
        request: ResourceSnapshotRequest,
        accumulators: ResourceSnapshotAccumulators,
        maxQueueDepth: Int,
    ): RealtimeNodeState {
        val lifecycle = request.lifecycleSnapshots[index]?.lifecycle ?: RealtimeVmLifecycle.ACTIVE
        val resourcePressures = resourcePressures(index, vm, request, accumulators)
        val availableSlots = availableSlots(index, accumulators)
        val topology = topologyModel.locationOf(index)
        val capacityAccepting = vmQueueCapacity <= 0 || availableSlots > 0
        val lifecycleAccepting = lifecycle == RealtimeVmLifecycle.ACTIVE
        val topologyLatency = topologyModel.latencyFor(topology)
        val topologyFailurePressure = topologyModel.failurePressure(topology)
        val rejectionReason =
            rejectionReason(
                lifecycleAccepting,
                lifecycle,
                capacityAccepting,
                resourcePressures.acceptingWork,
            )

        return RealtimeNodeState(
            vmIndex = index,
            vmId = vm.id,
            lifecycle = lifecycle,
            runningCount = accumulators.runningCounts[index],
            pendingCount = accumulators.pendingCounts[index],
            queueDepth = accumulators.queueDepths[index],
            availableSlots = availableSlots,
            acceptingWork = lifecycleAccepting && capacityAccepting && resourcePressures.acceptingWork,
            estimatedLoad = accumulators.loads[index],
            availableTime = availableTime(request.currentTime, accumulators.loads[index], topologyLatency),
            failurePressure =
                maxOf(
                    accumulators.queueDepths[index].toDouble() / maxQueueDepth.toDouble(),
                    resourcePressures.combined,
                    topologyFailurePressure,
                ),
            ramPressure = resourcePressures.ram,
            bwPressure = resourcePressures.bw,
            ioPressure = resourcePressures.io,
            networkLatency = resourceModel.networkLatency,
            imagePullDelay = resourceModel.imagePullDelay,
            resourcePressure = resourcePressures.combined,
            resourceAcceptingWork = resourcePressures.acceptingWork,
            rejectionReason = rejectionReason,
            regionId = topology.regionId,
            rackId = topology.rackId,
            hostId = topology.hostId,
            failureDomainId = topology.failureDomainId,
            topologyLatency = topologyLatency,
            topologyCost = topologyModel.costFor(topology),
            failureDomainLoad = accumulators.failureDomainLoads[topology.failureDomainId] ?: 0,
            topologyFailurePressure = topologyFailurePressure,
        )
    }

    private fun availableSlots(
        index: Int,
        accumulators: ResourceSnapshotAccumulators,
    ): Int =
        if (vmQueueCapacity <= 0) {
            Int.MAX_VALUE
        } else {
            max(0, vmQueueCapacity - accumulators.queueDepths[index])
        }

    private fun resourcePressures(
        index: Int,
        vm: Vm,
        request: ResourceSnapshotRequest,
        accumulators: ResourceSnapshotAccumulators,
    ): NodeResourcePressures {
        val extraRam = request.incomingCloudlet?.let(resourceModel::ramDemand) ?: 0.0
        val extraBw = request.incomingCloudlet?.let(resourceModel::bwDemand) ?: 0.0
        val extraIo = request.incomingCloudlet?.let(resourceModel::ioDemand) ?: 0.0
        val ramPressure = resourceModel.ramPressure(accumulators.ramDemand[index], vm)
        val bwPressure = resourceModel.bwPressure(accumulators.bwDemand[index], vm)
        val ioPressure = resourceModel.ioPressure(accumulators.ioDemand[index], vm)
        return NodeResourcePressures(
            ram = ramPressure,
            bw = bwPressure,
            io = ioPressure,
            combined = maxOf(ramPressure, bwPressure, ioPressure),
            acceptingWork =
                resourceModel.accepts(
                    accumulators.ramDemand[index] + extraRam,
                    accumulators.bwDemand[index] + extraBw,
                    accumulators.ioDemand[index] + extraIo,
                    vm,
                ),
        )
    }

    private fun availableTime(
        currentTime: Double,
        load: Double,
        topologyLatency: Double,
    ): Double = currentTime + load + resourceModel.networkLatency + resourceModel.imagePullDelay + topologyLatency

    private fun rejectionReason(
        lifecycleAccepting: Boolean,
        lifecycle: RealtimeVmLifecycle,
        capacityAccepting: Boolean,
        resourceAcceptingWork: Boolean,
    ): String? =
        when {
            !lifecycleAccepting -> "vm_lifecycle_$lifecycle"
            !capacityAccepting -> "vm_queue_capacity"
            !resourceAcceptingWork -> "resource_capacity"
            else -> null
        }
}

private class ResourceSnapshotAccumulators(
    size: Int,
) {
    val loads = DoubleArray(size)
    val ramDemand = DoubleArray(size)
    val bwDemand = DoubleArray(size)
    val ioDemand = DoubleArray(size)
    val runningCounts = IntArray(size)
    val pendingCounts = IntArray(size)
    val queueDepths = IntArray(size)
    val activeCloudletIds = mutableSetOf<Long>()
    val failureDomainLoads = mutableMapOf<FailureDomainId, Int>()

    fun maxQueueDepth(): Int = queueDepths.maxOrNull()?.coerceAtLeast(1) ?: 1

    fun trackFailureDomain(
        topologyModel: RealtimeTopologyModel,
        vmIndex: Int,
    ) {
        val failureDomain = topologyModel.locationOf(vmIndex).failureDomainId
        failureDomainLoads[failureDomain] = (failureDomainLoads[failureDomain] ?: 0) + 1
    }
}

private data class NodeResourcePressures(
    val ram: Double,
    val bw: Double,
    val io: Double,
    val combined: Double,
    val acceptingWork: Boolean,
)
