package scheduler.realtime

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
    val cpuOvercommitRatio: Double,
    val hostRamCapacity: Double,
    val hostBwCapacity: Double,
    val hostIoCapacity: Double,
    val networkBandwidthSharingEnabled: Boolean,
    val storageIopsSharingEnabled: Boolean,
    val imagePullQueueEnabled: Boolean,
    val noisyNeighborPenaltyWeight: Double,
    val crossRackBandwidth: Double,
    val crossRegionBandwidth: Double,
    val dataLocalityPolicy: DataLocalityPolicy,
)

@Suppress(
    "ReturnCount",
    "TooManyFunctions",
) // Candidate annotator keeps physical, locality and cache placement features in one scoring boundary.
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
        val snapshot = activePlacementSnapshot(records)
        val rawCandidates =
            states.map { state ->
                val location = locationOf(state.vmIndex)
                val demand = snapshot.demandByHost[location] ?: RealtimeResourceDemand()
                val placement = placementFor(state, vmList.getOrNull(state.vmIndex), workload, demand, snapshot)
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
            config.imageCacheEnabled ||
            config.noisyNeighborPenaltyWeight > ZERO_DELAY

    private fun placementFor(
        state: RealtimeNodeState,
        vm: Vm?,
        workload: RealtimeWorkloadDescriptor,
        activeDemand: RealtimeResourceDemand,
        snapshot: ActivePlacementSnapshot,
    ): RealtimePlacementDecision {
        val location = locationOf(state.vmIndex)
        val incomingDemand = workload.toDemand()
        val projectedDemand = activeDemand + incomingDemand
        val activeHostState = hostState(location, activeDemand)
        val capacityReason = capacityRejectionReason(config, projectedDemand)
        if (config.physicalTopologyEnabled && capacityReason != null) {
            return RealtimePlacementDecision.Rejected(VmIndex(state.vmIndex), location, capacityReason)
        }

        val transfer = transferFor(workload, location, snapshot)
        if (config.networkBandwidthSharingEnabled && transfer.bandwidthMissing) {
            return RealtimePlacementDecision.Rejected(VmIndex(state.vmIndex), location, "network_bandwidth_capacity")
        }
        val image = imagePullFor(workload, location, vm, snapshot.imagePullMissCountByHost[location] ?: 0)
        val hostResourceDelay = hostResourceDelay(activeDemand, incomingDemand, projectedDemand)
        val noisyNeighborPressure =
            noisyNeighborPressure(activeHostState, snapshot.taskCountByHost[location] ?: 0)
        val score =
            state.availableTime +
                transfer.networkDelay +
                image.pullDelay +
                hostResourceDelay +
                costFor(location) +
                dataLocalityPenalty(config.dataLocalityPolicy, transfer.dataLocal, transfer.transferGb) +
                activeHostState.utilization +
                activeHostState.fragmentation +
                noisyNeighborPressure
        return RealtimePlacementDecision.Accepted(
            vmIndex = VmIndex(state.vmIndex),
            location = location,
            hostState = hostState(location, projectedDemand),
            dataLocal = transfer.dataLocal,
            imageCacheHit = image.cacheHit,
            networkTransferDelay = transfer.networkDelay,
            networkTransferGb = transfer.transferGb,
            imagePullDelay = image.pullDelay,
            hostResourceDelay = hostResourceDelay,
            noisyNeighborPressure = noisyNeighborPressure,
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

    private fun activePlacementSnapshot(records: List<RealtimeTaskRecord>): ActivePlacementSnapshot {
        val entries =
            records
                .filter { it.lifecycle.isActiveForPhysicalPlacement() }
                .mapNotNull { record ->
                    val vmIndex = record.assignedVmIndex ?: return@mapNotNull null
                    val location = locationOf(vmIndex)
                    val workload = record.workloadDescriptor(config.localRegion)
                    ActivePlacementEntry(location, workload, workload.toDemand())
                }
        val demandByHost =
            entries
                .groupingBy { it.location }
                .fold(RealtimeResourceDemand()) { demand, entry -> demand + entry.demand }
        val taskCountByHost = entries.groupingBy { it.location }.eachCount()
        val transferCountByRoute =
            entries
                .mapNotNull { routeFor(it.workload, it.location) }
                .groupingBy { it }
                .eachCount()
        val imagePullMissCountByHost =
            entries
                .filter { it.workload.imageId != null && !imageCacheHit(it.location, it.workload.imageId) }
                .groupingBy { it.location }
                .eachCount()
        return ActivePlacementSnapshot(
            demandByHost = demandByHost,
            taskCountByHost = taskCountByHost,
            transferCountByRoute = transferCountByRoute,
            imagePullMissCountByHost = imagePullMissCountByHost,
        )
    }

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
        snapshot: ActivePlacementSnapshot,
    ): TopologyTransfer {
        val route = routeFor(workload, location)
        val dataLocal = !config.dataLocalityEnabled || route == null
        val transferGb =
            if (route == null) {
                ZERO_DELAY
            } else {
                workload.inputDataSizeGb.coerceAtLeast(ZERO_DELAY)
            }
        val bandwidth = bandwidthFor(route, transferGb)
        val activeTransferCount =
            if (config.networkBandwidthSharingEnabled && route != null) {
                (snapshot.transferCountByRoute[route] ?: 0) + 1
            } else {
                1
            }
        val effectiveBandwidth =
            if (bandwidth > ZERO_DELAY && activeTransferCount > 1) {
                bandwidth / activeTransferCount.toDouble()
            } else {
                bandwidth
            }
        val transferDelay =
            if (transferGb > ZERO_DELAY && effectiveBandwidth > ZERO_DELAY) {
                transferGb / effectiveBandwidth
            } else {
                ZERO_DELAY
            }
        val networkDelay =
            if (config.dataLocalityEnabled) {
                latencyFor(location) + transferDelay
            } else {
                ZERO_DELAY
            }
        return TopologyTransfer(
            dataLocal = dataLocal,
            transferGb = transferGb,
            networkDelay = networkDelay,
            bandwidthMissing = route != null && transferGb > ZERO_DELAY && bandwidth <= ZERO_DELAY,
        )
    }

    private fun routeFor(
        workload: RealtimeWorkloadDescriptor,
        location: RealtimeTopologyLocation,
    ): TopologyRoute? {
        if (!config.dataLocalityEnabled || workload.inputDataSizeGb <= ZERO_DELAY) return null
        return when {
            workload.dataRegion != location.regionId ->
                TopologyRoute(workload.dataRegion, location.regionId, null)
            config.networkBandwidthSharingEnabled && location.rackId.value != 0 ->
                TopologyRoute(workload.dataRegion, location.regionId, location.rackId)
            else -> null
        }
    }

    private fun bandwidthFor(
        route: TopologyRoute?,
        transferGb: Double,
    ): Double =
        when {
            route == null || transferGb <= ZERO_DELAY -> ZERO_DELAY
            route.sourceRegion != route.targetRegion -> config.crossRegionBandwidth
            route.targetRack != null -> config.crossRackBandwidth
            else -> ZERO_DELAY
        }

    private fun imagePullFor(
        workload: RealtimeWorkloadDescriptor,
        location: RealtimeTopologyLocation,
        vm: Vm?,
        activeMissCount: Int,
    ): ImagePull {
        val imageId = workload.imageId
        val cacheHit = imageCacheHit(location, imageId)
        val baseDelay =
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
        val queueMultiplier =
            if (config.imagePullQueueEnabled && baseDelay > ZERO_DELAY) {
                activeMissCount.coerceAtLeast(0) + 1
            } else {
                1
            }
        val pullDelay = baseDelay * queueMultiplier.toDouble()
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

    private fun hostResourceDelay(
        activeDemand: RealtimeResourceDemand,
        incomingDemand: RealtimeResourceDemand,
        projectedDemand: RealtimeResourceDemand,
    ): Double = cpuThrottleDelay(projectedDemand) + storageDelay(activeDemand, incomingDemand)

    private fun cpuThrottleDelay(projectedDemand: RealtimeResourceDemand): Double =
        if (config.physicalTopologyEnabled && config.hostCpuCapacity > ZERO_DELAY) {
            (projectedDemand.cpu / config.hostCpuCapacity - 1.0).coerceAtLeast(ZERO_DELAY)
        } else {
            ZERO_DELAY
        }

    private fun storageDelay(
        activeDemand: RealtimeResourceDemand,
        incomingDemand: RealtimeResourceDemand,
    ): Double {
        if (!config.storageIopsSharingEnabled || config.hostIoCapacity <= ZERO_DELAY) return ZERO_DELAY
        val incomingPressure = incomingDemand.io.coerceAtLeast(ZERO_DELAY) / config.hostIoCapacity
        val activePressure = activeDemand.io.coerceAtLeast(ZERO_DELAY) / config.hostIoCapacity
        return incomingPressure * (1.0 + activePressure)
    }

    private fun noisyNeighborPressure(
        activeHostState: RealtimeHostState,
        activeTaskCount: Int,
    ): Double {
        val weight = config.noisyNeighborPenaltyWeight
        if (weight <= ZERO_DELAY) return ZERO_DELAY
        val concurrencyPressure =
            if (activeTaskCount <= 0) ZERO_DELAY else activeTaskCount.toDouble() / (activeTaskCount + 1.0)
        return weight * (activeHostState.utilization + concurrencyPressure)
    }
}

private fun capacityRejectionReason(
    config: TopologyCandidateAnnotationConfig,
    demand: RealtimeResourceDemand,
): String? =
    when {
        config.hostCpuCapacity > ZERO_DELAY && demand.cpu > config.effectiveCpuCapacity -> "physical_cpu_capacity"
        config.hostRamCapacity > ZERO_DELAY && demand.ram > config.hostRamCapacity -> "physical_ram_capacity"
        config.hostBwCapacity > ZERO_DELAY && demand.bw > config.hostBwCapacity -> "physical_bw_capacity"
        config.hostIoCapacity > ZERO_DELAY && demand.io > config.hostIoCapacity -> "physical_io_capacity"
        else -> null
    }

private val TopologyCandidateAnnotationConfig.effectiveCpuCapacity: Double
    get() = hostCpuCapacity * cpuOvercommitRatio.coerceAtLeast(Double.MIN_VALUE)

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
    val bandwidthMissing: Boolean,
)

private data class ImagePull(
    val cacheHit: Boolean,
    val pullDelay: Double,
)

private data class TopologyRoute(
    val sourceRegion: RegionId,
    val targetRegion: RegionId,
    val targetRack: RackId?,
)

private data class ActivePlacementEntry(
    val location: RealtimeTopologyLocation,
    val workload: RealtimeWorkloadDescriptor,
    val demand: RealtimeResourceDemand,
)

private data class ActivePlacementSnapshot(
    val demandByHost: Map<RealtimeTopologyLocation, RealtimeResourceDemand>,
    val taskCountByHost: Map<RealtimeTopologyLocation, Int>,
    val transferCountByRoute: Map<TopologyRoute, Int>,
    val imagePullMissCountByHost: Map<RealtimeTopologyLocation, Int>,
)
