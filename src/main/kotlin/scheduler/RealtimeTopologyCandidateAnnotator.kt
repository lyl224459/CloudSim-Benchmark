package scheduler

import config.DataLocalityPolicy
import org.cloudsimplus.vms.Vm

private const val BALANCED_DATA_LOCALITY_PENALTY = 0.25
private const val ZERO_DELAY = 0.0
private const val UNIT_DELAY = 1.0

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
        if (!shouldAnnotate()) return emptyList()
        val activeDemandByHost = activeDemandByHost(records)
        val rawCandidates =
            states.map { state ->
                val location = locationOf(state.vmIndex)
                val demand = activeDemandByHost[location] ?: RealtimeResourceDemand()
                val placement = placementFor(state, vmList.getOrNull(state.vmIndex), workload, demand)
                NodeCandidate(
                    nodeState = state.withPlacement(placement),
                    placement = placement,
                    score = ZERO_DELAY,
                )
            }
        return scorer.score(rawCandidates)
    }

    private fun shouldAnnotate(): Boolean =
        config.physicalTopologyEnabled ||
            config.dataLocalityEnabled ||
            config.imageCacheEnabled

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
        val capacityReason = capacityRejectionReason(config, projectedDemand)
        if (config.physicalTopologyEnabled && capacityReason != null) {
            return RealtimePlacementDecision.Rejected(VmIndex(state.vmIndex), location, capacityReason)
        }

        val transfer = transferFor(workload, location)
        val image = imagePullFor(workload, location, vm)
        val score =
            state.availableTime +
                transfer.networkDelay +
                image.pullDelay +
                costFor(location) +
                dataLocalityPenalty(config.dataLocalityPolicy, transfer.dataLocal, transfer.transferGb) +
                activeHostState.utilization +
                activeHostState.fragmentation
        return RealtimePlacementDecision.Accepted(
            vmIndex = VmIndex(state.vmIndex),
            location = location,
            hostState = hostState(location, projectedDemand),
            dataLocal = transfer.dataLocal,
            imageCacheHit = image.cacheHit,
            networkTransferDelay = transfer.networkDelay,
            networkTransferGb = transfer.transferGb,
            imagePullDelay = image.pullDelay,
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

    private fun transferFor(
        workload: RealtimeWorkloadDescriptor,
        location: RealtimeTopologyLocation,
    ): TopologyTransfer {
        val dataLocal = !config.dataLocalityEnabled || workload.dataRegion == location.regionId
        val transferGb =
            if (dataLocal || !config.dataLocalityEnabled) {
                ZERO_DELAY
            } else {
                workload.inputDataSizeGb.coerceAtLeast(ZERO_DELAY)
            }
        val bandwidth = bandwidthFor(workload, location, transferGb)
        val transferDelay =
            if (transferGb > ZERO_DELAY && bandwidth > ZERO_DELAY) {
                transferGb / bandwidth
            } else {
                ZERO_DELAY
            }
        val networkDelay =
            if (config.dataLocalityEnabled) {
                latencyFor(location) + transferDelay
            } else {
                ZERO_DELAY
            }
        return TopologyTransfer(dataLocal, transferGb, networkDelay)
    }

    private fun bandwidthFor(
        workload: RealtimeWorkloadDescriptor,
        location: RealtimeTopologyLocation,
        transferGb: Double,
    ): Double =
        when {
            !config.dataLocalityEnabled || transferGb <= ZERO_DELAY -> ZERO_DELAY
            workload.dataRegion != location.regionId -> config.crossRegionBandwidth
            location.rackId.value != 0 -> config.crossRackBandwidth
            else -> ZERO_DELAY
        }

    private fun imagePullFor(
        workload: RealtimeWorkloadDescriptor,
        location: RealtimeTopologyLocation,
        vm: Vm?,
    ): ImagePull {
        val imageId = workload.imageId
        val cacheHit = imageCacheHit(location, imageId)
        val pullDelay =
            if (config.imageCacheEnabled && imageId != null && !cacheHit) {
                val imageSize = workload.imageSizeGb.coerceAtLeast(ZERO_DELAY)
                if (imageSize > ZERO_DELAY) {
                    imageSize * imagePullUnitDelay(vm)
                } else {
                    imagePullUnitDelay(vm)
                }
            } else {
                ZERO_DELAY
            }
        return ImagePull(cacheHit, pullDelay)
    }

    private fun imageCacheHit(
        location: RealtimeTopologyLocation,
        imageId: String?,
    ): Boolean =
        config.imageCacheEnabled &&
            imageId != null &&
            imageCacheByHost[location]?.contains(imageId) == true

    private fun imagePullUnitDelay(vm: Vm?): Double {
        val capacity =
            vm
                ?.bw
                ?.capacity
                ?.toDouble()
                ?.takeIf { it > ZERO_DELAY } ?: return UNIT_DELAY
        return UNIT_DELAY / capacity
    }
}

private fun capacityRejectionReason(
    config: TopologyCandidateAnnotationConfig,
    demand: RealtimeResourceDemand,
): String? =
    when {
        config.hostCpuCapacity > ZERO_DELAY && demand.cpu > config.hostCpuCapacity -> "physical_cpu_capacity"
        config.hostRamCapacity > ZERO_DELAY && demand.ram > config.hostRamCapacity -> "physical_ram_capacity"
        config.hostBwCapacity > ZERO_DELAY && demand.bw > config.hostBwCapacity -> "physical_bw_capacity"
        config.hostIoCapacity > ZERO_DELAY && demand.io > config.hostIoCapacity -> "physical_io_capacity"
        else -> null
    }

private fun dataLocalityPenalty(
    policy: DataLocalityPolicy,
    dataLocal: Boolean,
    transferGb: Double,
): Double =
    when (policy) {
        DataLocalityPolicy.IGNORE -> ZERO_DELAY
        DataLocalityPolicy.BALANCED ->
            if (dataLocal) ZERO_DELAY else transferGb * BALANCED_DATA_LOCALITY_PENALTY
        DataLocalityPolicy.PREFER_LOCAL -> if (dataLocal) ZERO_DELAY else transferGb
    }

private data class TopologyTransfer(
    val dataLocal: Boolean,
    val transferGb: Double,
    val networkDelay: Double,
)

private data class ImagePull(
    val cacheHit: Boolean,
    val pullDelay: Double,
)
