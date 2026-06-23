package scheduler.realtime

import config.RealtimeSchedulingConfig
import org.cloudsimplus.vms.Vm

data class RealtimeTopologyLocation(
    val regionId: RegionId,
    val rackId: RackId,
    val hostId: HostId,
    val failureDomainId: FailureDomainId,
)

data class RealtimeTopologyMetrics(
    val crossRackAssignmentCount: Int,
    val crossRegionAssignmentCount: Int,
    val averageTopologyLatency: Double,
    val topologyCost: Double,
    val failureDomainSpreadScore: Double,
)

@Suppress("TooManyFunctions") // Topology facade preserves the scheduler-facing location and metrics API.
class RealtimeTopologyModel private constructor(
    private val settings: RealtimeTopologySettings,
    initialVmCount: Int,
) {
    private val placements = RealtimeTopologyPlacementMap(settings, initialVmCount)
    private val metricsCalculator = RealtimeTopologyMetricsCalculator(settings, placements)

    companion object {
        val Disabled =
            RealtimeTopologyModel(
                settings =
                    RealtimeTopologySettings(
                        enabled = false,
                        regionCount = 1,
                        racksPerRegion = 1,
                        hostsPerRack = 1,
                        localRegion = RegionId(0),
                        crossRackLatency = 0.0,
                        crossRegionLatency = 0.0,
                        crossRegionCost = 0.0,
                        hostFailureRate = 0.0,
                        rackFailureRate = 0.0,
                        regionFailureRate = 0.0,
                        physicalTopologyEnabled = false,
                        dataLocalityEnabled = false,
                        imageCacheEnabled = false,
                        hostCpuCapacity = 0.0,
                        cpuOvercommitRatio = 1.0,
                        hostRamCapacity = 0.0,
                        hostBwCapacity = 0.0,
                        hostIoCapacity = 0.0,
                        networkBandwidthSharingEnabled = false,
                        storageIopsSharingEnabled = false,
                        imagePullQueueEnabled = false,
                        noisyNeighborPenaltyWeight = 0.0,
                        crossRackBandwidth = 0.0,
                        crossRegionBandwidth = 0.0,
                        dataLocalityPolicy = config.DataLocalityPolicy.PREFER_LOCAL,
                        imageCacheCapacity = 0,
                    ),
                initialVmCount = 0,
            )

        fun fromConfig(
            scheduling: RealtimeSchedulingConfig,
            initialVmCount: Int,
        ): RealtimeTopologyModel =
            RealtimeTopologyModel(
                settings = scheduling.toTopologySettings(),
                initialVmCount = initialVmCount,
            )

        private fun RealtimeSchedulingConfig.toTopologySettings(): RealtimeTopologySettings =
            RealtimeTopologySettings(
                enabled = topologyEnabled || physicalTopologyEnabled || dataLocalityEnabled || imageCacheEnabled,
                regionCount = regionCount.coerceAtLeast(1),
                racksPerRegion = racksPerRegion.coerceAtLeast(1),
                hostsPerRack = effectiveHostsPerRack(),
                localRegion = RegionId(localRegion.coerceIn(0, regionCount.coerceAtLeast(1) - 1)),
                crossRackLatency = crossRackLatency,
                crossRegionLatency = crossRegionLatency,
                crossRegionCost = crossRegionCost,
                hostFailureRate = hostFailureRate,
                rackFailureRate = rackFailureRate,
                regionFailureRate = regionFailureRate,
                physicalTopologyEnabled = physicalTopologyEnabled,
                dataLocalityEnabled = dataLocalityEnabled,
                imageCacheEnabled = imageCacheEnabled,
                hostCpuCapacity = hostCpuCapacity,
                cpuOvercommitRatio = cpuOvercommitRatio,
                hostRamCapacity = hostRamCapacity,
                hostBwCapacity = hostBwCapacity,
                hostIoCapacity = hostIoCapacity,
                networkBandwidthSharingEnabled = networkBandwidthSharingEnabled,
                storageIopsSharingEnabled = storageIopsSharingEnabled,
                imagePullQueueEnabled = imagePullQueueEnabled,
                noisyNeighborPenaltyWeight = noisyNeighborPenaltyWeight,
                crossRackBandwidth = crossRackBandwidth,
                crossRegionBandwidth = crossRegionBandwidth,
                dataLocalityPolicy = normalizedDataLocalityPolicy(),
                imageCacheCapacity = imageCacheCapacity,
            )

        private fun RealtimeSchedulingConfig.effectiveHostsPerRack(): Int =
            if (physicalTopologyEnabled) {
                hostCountPerRack.coerceAtLeast(1)
            } else {
                hostsPerRack.coerceAtLeast(1)
            }
    }

    private val imageCacheByHost = linkedMapOf<RealtimeTopologyLocation, LinkedHashSet<String>>()

    fun locationOf(vmIndex: Int): RealtimeTopologyLocation = placements.locationOf(vmIndex)

    fun registerDynamicVm(
        vmIndex: Int,
        activeVmIndexes: Set<Int> = emptySet(),
    ): RealtimeTopologyLocation = placements.registerDynamicVm(vmIndex, activeVmIndexes, metricsCalculator::latencyFor)

    fun candidatesFor(
        states: List<RealtimeNodeState>,
        vmList: List<Vm>,
        workload: RealtimeWorkloadDescriptor,
        records: List<RealtimeTaskRecord>,
    ): List<NodeCandidate> = candidateAnnotator().annotate(states, vmList, workload, records)

    fun recordSubmission(
        vmIndex: Int,
        workload: RealtimeWorkloadDescriptor,
    ) {
        val imageId = workload.imageId ?: return
        if (!canRecordImageCache()) return
        val location = locationOf(vmIndex)
        val cache = imageCacheByHost.getOrPut(location) { LinkedHashSet() }
        if (cache.remove(imageId)) {
            cache.add(imageId)
        } else {
            evictImagesUntilWritable(cache)
            cache.add(imageId)
        }
    }

    private fun canRecordImageCache(): Boolean = settings.imageCacheEnabled && settings.imageCacheCapacity > 0

    private fun evictImagesUntilWritable(cache: LinkedHashSet<String>) {
        while (cache.size >= settings.imageCacheCapacity) {
            val oldest = cache.firstOrNull() ?: break
            cache.remove(oldest)
        }
    }

    fun physicalHostMetrics(records: List<RealtimeTaskRecord>): RealtimePhysicalHostMetrics =
        metricsCalculator.physicalHostMetrics(records, candidateAnnotator())

    fun latencyFor(location: RealtimeTopologyLocation): Double = metricsCalculator.latencyFor(location)

    fun costFor(location: RealtimeTopologyLocation): Double = metricsCalculator.costFor(location)

    fun failurePressure(location: RealtimeTopologyLocation): Double = metricsCalculator.failurePressure(location)

    fun metricsFor(vmIndexes: List<Int>): RealtimeTopologyMetrics = metricsCalculator.metricsFor(vmIndexes)

    private fun annotationConfig(): TopologyCandidateAnnotationConfig =
        TopologyCandidateAnnotationConfig(
            enabled = settings.enabled,
            physicalTopologyEnabled = settings.physicalTopologyEnabled,
            dataLocalityEnabled = settings.dataLocalityEnabled,
            imageCacheEnabled = settings.imageCacheEnabled,
            localRegion = settings.localRegion,
            hostCpuCapacity = settings.hostCpuCapacity,
            cpuOvercommitRatio = settings.cpuOvercommitRatio,
            hostRamCapacity = settings.hostRamCapacity,
            hostBwCapacity = settings.hostBwCapacity,
            hostIoCapacity = settings.hostIoCapacity,
            networkBandwidthSharingEnabled = settings.networkBandwidthSharingEnabled,
            storageIopsSharingEnabled = settings.storageIopsSharingEnabled,
            imagePullQueueEnabled = settings.imagePullQueueEnabled,
            noisyNeighborPenaltyWeight = settings.noisyNeighborPenaltyWeight,
            crossRackBandwidth = settings.crossRackBandwidth,
            crossRegionBandwidth = settings.crossRegionBandwidth,
            dataLocalityPolicy = settings.dataLocalityPolicy,
        )

    private fun candidateAnnotator(): TopologyCandidateAnnotator =
        TopologyCandidateAnnotator(
            config = annotationConfig(),
            locationOf = ::locationOf,
            latencyFor = ::latencyFor,
            costFor = ::costFor,
            imageCacheByHost = imageCacheByHost,
        )
}
