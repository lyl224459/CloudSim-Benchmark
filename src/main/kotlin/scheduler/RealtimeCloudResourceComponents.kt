package scheduler

import config.DataLocalityPolicy
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
        val loads = DoubleArray(vmList.size)
        val ramDemand = DoubleArray(vmList.size)
        val bwDemand = DoubleArray(vmList.size)
        val ioDemand = DoubleArray(vmList.size)
        val runningCounts = IntArray(vmList.size)
        val pendingCounts = IntArray(vmList.size)
        val queueDepths = IntArray(vmList.size)
        val activeCloudletIds = mutableSetOf<Long>()
        val failureDomainLoads = mutableMapOf<FailureDomainId, Int>()

        request.activeCloudlets.forEach { cloudlet ->
            activeCloudletIds.add(cloudlet.id)
            val vmIndex = request.reservedVmIndexes[cloudlet.id] ?: vmIndexById[cloudlet.vm?.id] ?: -1
            if (vmIndex < 0) return@forEach

            val vm = vmList[vmIndex]
            val estimatedTime = cloudlet.length.toDouble() / vm.mips
            val resourceDelay = resourceModel.resourceDelay(cloudlet, vm)
            loads[vmIndex] += estimatedTime + resourceDelay
            ramDemand[vmIndex] += resourceModel.ramDemand(cloudlet)
            bwDemand[vmIndex] += resourceModel.bwDemand(cloudlet)
            ioDemand[vmIndex] += resourceModel.ioDemand(cloudlet)
            if (cloudlet.status == Cloudlet.Status.INEXEC) {
                runningCounts[vmIndex]++
            }
            queueDepths[vmIndex]++
            val failureDomain = topologyModel.locationOf(vmIndex).failureDomainId
            failureDomainLoads[failureDomain] = (failureDomainLoads[failureDomain] ?: 0) + 1
        }

        request.reservedVmIndexes.forEach { (cloudletId, vmIndex) ->
            if (cloudletId in activeCloudletIds || vmIndex !in vmList.indices) return@forEach
            pendingCounts[vmIndex]++
            queueDepths[vmIndex]++
            val failureDomain = topologyModel.locationOf(vmIndex).failureDomainId
            failureDomainLoads[failureDomain] = (failureDomainLoads[failureDomain] ?: 0) + 1
        }

        val maxQueueDepth = queueDepths.maxOrNull()?.coerceAtLeast(1) ?: 1
        return vmList.mapIndexed { index, vm ->
            val lifecycle = request.lifecycleSnapshots[index]?.lifecycle ?: RealtimeVmLifecycle.ACTIVE
            val lifecycleAccepting = lifecycle == RealtimeVmLifecycle.ACTIVE
            val availableSlots = if (vmQueueCapacity <= 0) Int.MAX_VALUE else max(0, vmQueueCapacity - queueDepths[index])
            val ramPressure = resourceModel.ramPressure(ramDemand[index], vm)
            val bwPressure = resourceModel.bwPressure(bwDemand[index], vm)
            val ioPressure = resourceModel.ioPressure(ioDemand[index], vm)
            val resourcePressure = maxOf(ramPressure, bwPressure, ioPressure)
            val extraRam = request.incomingCloudlet?.let(resourceModel::ramDemand) ?: 0.0
            val extraBw = request.incomingCloudlet?.let(resourceModel::bwDemand) ?: 0.0
            val extraIo = request.incomingCloudlet?.let(resourceModel::ioDemand) ?: 0.0
            val resourceAcceptingWork =
                resourceModel.accepts(
                    ramDemand[index] + extraRam,
                    bwDemand[index] + extraBw,
                    ioDemand[index] + extraIo,
                    vm,
                )
            val topology = topologyModel.locationOf(index)
            val topologyLatency = topologyModel.latencyFor(topology)
            val topologyCost = topologyModel.costFor(topology)
            val topologyFailurePressure = topologyModel.failurePressure(topology)
            val capacityAccepting = vmQueueCapacity <= 0 || availableSlots > 0
            val rejectionReason =
                when {
                    !lifecycleAccepting -> "vm_lifecycle_$lifecycle"
                    !capacityAccepting -> "vm_queue_capacity"
                    !resourceAcceptingWork -> "resource_capacity"
                    else -> null
                }
            RealtimeNodeState(
                vmIndex = index,
                vmId = vm.id,
                lifecycle = lifecycle,
                runningCount = runningCounts[index],
                pendingCount = pendingCounts[index],
                queueDepth = queueDepths[index],
                availableSlots = availableSlots,
                acceptingWork = lifecycleAccepting && capacityAccepting && resourceAcceptingWork,
                estimatedLoad = loads[index],
                availableTime =
                    request.currentTime + loads[index] +
                        resourceModel.networkLatency + resourceModel.imagePullDelay + topologyLatency,
                failurePressure =
                    maxOf(
                        queueDepths[index].toDouble() / maxQueueDepth.toDouble(),
                        resourcePressure,
                        topologyFailurePressure,
                    ),
                ramPressure = ramPressure,
                bwPressure = bwPressure,
                ioPressure = ioPressure,
                networkLatency = resourceModel.networkLatency,
                imagePullDelay = resourceModel.imagePullDelay,
                resourcePressure = resourcePressure,
                resourceAcceptingWork = resourceAcceptingWork,
                rejectionReason = rejectionReason,
                regionId = topology.regionId,
                rackId = topology.rackId,
                hostId = topology.hostId,
                failureDomainId = topology.failureDomainId,
                topologyLatency = topologyLatency,
                topologyCost = topologyCost,
                failureDomainLoad = failureDomainLoads[topology.failureDomainId] ?: 0,
                topologyFailurePressure = topologyFailurePressure,
            )
        }
    }
}

internal data class TopologyCandidateAnnotationConfig(
    val enabled: Boolean,
    val physicalTopologyEnabled: Boolean,
    val dataLocalityEnabled: Boolean,
    val imageCacheEnabled: Boolean,
    val localRegion: RegionId,
    val hostCpuCapacity: Double,
    val hostRamCapacity: Double,
    val hostBwCapacity: Double,
    val hostIoCapacity: Double,
    val crossRackBandwidth: Double,
    val crossRegionBandwidth: Double,
    val dataLocalityPolicy: DataLocalityPolicy,
)

class VmCandidateScorer(
    private val filters: List<CandidateFilter> = defaultFilters(),
    private val scorers: List<CandidateScorer> = defaultScorers(),
) {
    fun score(candidates: List<NodeCandidate>): List<NodeCandidate> =
        candidates
            .map { candidate -> candidate.copy(score = scorers.sumOf { scorer -> scorer.score(candidate) }) }
            .filter { candidate ->
                filters.all { filter -> filter.accepts(candidate) } ||
                    candidate.placement is RealtimePlacementDecision.Rejected
            }

    companion object {
        fun defaultFilters(): List<CandidateFilter> =
            listOf(
                CandidateFilter { it.nodeState.acceptingWork },
                CandidateFilter { it.placement is RealtimePlacementDecision.Accepted },
            )

        fun defaultScorers(): List<CandidateScorer> =
            listOf(
                CandidateScorer { it.nodeState.availableTime },
                CandidateScorer { (it.placement as? RealtimePlacementDecision.Accepted)?.score ?: Double.POSITIVE_INFINITY },
            )
    }
}

internal class TopologyCandidateAnnotator(
    private val config: TopologyCandidateAnnotationConfig,
    private val locationOf: (Int) -> RealtimeTopologyLocation,
    private val latencyFor: (RealtimeTopologyLocation) -> Double,
    private val costFor: (RealtimeTopologyLocation) -> Double,
    private val imageCacheByHost: Map<RealtimeTopologyLocation, Set<String>>,
    private val scorer: VmCandidateScorer = VmCandidateScorer(),
) {
    fun annotate(
        states: List<RealtimeNodeState>,
        vmList: List<Vm>,
        workload: RealtimeWorkloadDescriptor,
        records: List<RealtimeTaskRecord>,
    ): List<NodeCandidate> {
        if (!config.physicalTopologyEnabled && !config.dataLocalityEnabled && !config.imageCacheEnabled) {
            return emptyList()
        }
        val activeDemandByHost = activeDemandByHost(records)
        val rawCandidates =
            states.map { state ->
                val location = locationOf(state.vmIndex)
                val demand = activeDemandByHost[location] ?: RealtimeResourceDemand()
                val placement = placementFor(state, vmList.getOrNull(state.vmIndex), workload, demand)
                NodeCandidate(
                    nodeState = state.withPlacement(placement),
                    placement = placement,
                    score = 0.0,
                )
            }
        return scorer.score(rawCandidates)
    }

    private fun placementFor(
        state: RealtimeNodeState,
        vm: Vm?,
        workload: RealtimeWorkloadDescriptor,
        activeDemand: RealtimeResourceDemand,
    ): RealtimePlacementDecision {
        val location = locationOf(state.vmIndex)
        val incomingDemand = workload.toDemand()
        val projectedDemand = activeDemand + incomingDemand
        val activeHostState = hostState(location, activeDemand)
        val capacityReason = capacityRejectionReason(projectedDemand)
        if (config.physicalTopologyEnabled && capacityReason != null) {
            return RealtimePlacementDecision.Rejected(VmIndex(state.vmIndex), location, capacityReason)
        }

        val dataLocal = !config.dataLocalityEnabled || workload.dataRegion == location.regionId
        val networkTransferGb =
            if (dataLocal || !config.dataLocalityEnabled) {
                0.0
            } else {
                workload.inputDataSizeGb.coerceAtLeast(0.0)
            }
        val bandwidth =
            when {
                !config.dataLocalityEnabled || networkTransferGb <= 0.0 -> 0.0
                workload.dataRegion != location.regionId -> config.crossRegionBandwidth
                location.rackId.value != 0 -> config.crossRackBandwidth
                else -> 0.0
            }
        val transferDelay = if (networkTransferGb > 0.0 && bandwidth > 0.0) networkTransferGb / bandwidth else 0.0
        val networkDelay = if (config.dataLocalityEnabled) latencyFor(location) + transferDelay else 0.0
        val imageId = workload.imageId
        val imageHit = config.imageCacheEnabled && imageId != null && imageCacheByHost[location]?.contains(imageId) == true
        val pullDelay =
            if (config.imageCacheEnabled && imageId != null && !imageHit) {
                val imageSize = workload.imageSizeGb.coerceAtLeast(0.0)
                if (imageSize > 0.0) imageSize * imagePullUnitDelay(vm) else imagePullUnitDelay(vm)
            } else {
                0.0
            }
        val score =
            state.availableTime +
                networkDelay +
                pullDelay +
                costFor(location) +
                dataLocalityPenalty(dataLocal, networkTransferGb) +
                activeHostState.utilization +
                activeHostState.fragmentation
        return RealtimePlacementDecision.Accepted(
            vmIndex = VmIndex(state.vmIndex),
            location = location,
            hostState = hostState(location, projectedDemand),
            dataLocal = dataLocal,
            imageCacheHit = imageHit,
            networkTransferDelay = networkDelay,
            networkTransferGb = networkTransferGb,
            imagePullDelay = pullDelay,
            topologyCost = costFor(location),
            score = score,
        )
    }

    fun activeDemandByHost(records: List<RealtimeTaskRecord>): Map<RealtimeTopologyLocation, RealtimeResourceDemand> =
        records
            .filter { it.lifecycle.isActiveForPhysicalPlacement() }
            .mapNotNull { record ->
                val vmIndex = record.assignedVmIndex ?: return@mapNotNull null
                locationOf(vmIndex) to record.workloadDescriptor(config.localRegion).toDemand()
            }.groupingBy { it.first }
            .fold(RealtimeResourceDemand()) { demand, pair -> demand + pair.second }

    fun hostState(
        location: RealtimeTopologyLocation,
        demand: RealtimeResourceDemand,
    ): RealtimeHostState =
        RealtimeHostState(
            location = location,
            cpuCapacity = config.hostCpuCapacity,
            ramCapacity = config.hostRamCapacity,
            bwCapacity = config.hostBwCapacity,
            ioCapacity = config.hostIoCapacity,
            allocatedCpu = demand.cpu,
            allocatedRam = demand.ram,
            allocatedBw = demand.bw,
            allocatedIo = demand.io,
            cachedImages = imageCacheByHost[location].orEmpty(),
        )

    private fun capacityRejectionReason(demand: RealtimeResourceDemand): String? =
        when {
            config.hostCpuCapacity > 0.0 && demand.cpu > config.hostCpuCapacity -> "physical_cpu_capacity"
            config.hostRamCapacity > 0.0 && demand.ram > config.hostRamCapacity -> "physical_ram_capacity"
            config.hostBwCapacity > 0.0 && demand.bw > config.hostBwCapacity -> "physical_bw_capacity"
            config.hostIoCapacity > 0.0 && demand.io > config.hostIoCapacity -> "physical_io_capacity"
            else -> null
        }

    private fun dataLocalityPenalty(
        dataLocal: Boolean,
        transferGb: Double,
    ): Double =
        when (config.dataLocalityPolicy) {
            DataLocalityPolicy.IGNORE -> 0.0
            DataLocalityPolicy.BALANCED -> if (dataLocal) 0.0 else transferGb * 0.25
            DataLocalityPolicy.PREFER_LOCAL -> if (dataLocal) 0.0 else transferGb
        }

    private fun imagePullUnitDelay(vm: Vm?): Double {
        val capacity =
            vm
                ?.bw
                ?.capacity
                ?.toDouble()
                ?.takeIf { it > 0.0 } ?: return 1.0
        return 1.0 / capacity
    }
}

internal fun RealtimeNodeState.withPlacement(placement: RealtimePlacementDecision): RealtimeNodeState {
    val accepted = placement as? RealtimePlacementDecision.Accepted
    return copy(
        acceptingWork = acceptingWork && accepted != null,
        availableTime = availableTime + (accepted?.placementDelay ?: 0.0),
        topologyLatency = accepted?.networkTransferDelay ?: topologyLatency,
        topologyCost = accepted?.topologyCost ?: topologyCost,
        physicalHostUtilization = accepted?.hostState?.utilization ?: physicalHostUtilization,
        hostResourceFragmentation = accepted?.hostState?.fragmentation ?: hostResourceFragmentation,
        networkTransferDelay = accepted?.networkTransferDelay ?: networkTransferDelay,
        imagePullDelay = imagePullDelay + (accepted?.imagePullDelay ?: 0.0),
        dataLocalityHit = accepted?.dataLocal ?: dataLocalityHit,
        imageCacheHit = accepted?.imageCacheHit ?: imageCacheHit,
        placementFailureReason = (placement as? RealtimePlacementDecision.Rejected)?.reason,
    )
}

internal fun RealtimeWorkloadDescriptor.toDemand(): RealtimeResourceDemand =
    RealtimeResourceDemand(
        cpu = requestedCpu.coerceAtLeast(0.0),
        ram = requestedRam.coerceAtLeast(0.0),
        bw = requestedBw.coerceAtLeast(0.0),
        io = requestedIo.coerceAtLeast(0.0),
    )

internal fun RealtimeTaskLifecycle.isActiveForPhysicalPlacement(): Boolean =
    this == RealtimeTaskLifecycle.PENDING_DECISION ||
        this == RealtimeTaskLifecycle.SUBMITTED ||
        this == RealtimeTaskLifecycle.RUNNING ||
        this == RealtimeTaskLifecycle.PREEMPTED ||
        this == RealtimeTaskLifecycle.MIGRATING ||
        this == RealtimeTaskLifecycle.RETRYING
