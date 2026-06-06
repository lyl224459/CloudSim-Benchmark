package scheduler

import config.DataLocalityPolicy
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

private data class RealtimeTopologySettings(
    val enabled: Boolean,
    val regionCount: Int,
    val racksPerRegion: Int,
    val hostsPerRack: Int,
    val localRegion: RegionId,
    val crossRackLatency: Double,
    val crossRegionLatency: Double,
    val crossRegionCost: Double,
    val hostFailureRate: Double,
    val rackFailureRate: Double,
    val regionFailureRate: Double,
    val physicalTopologyEnabled: Boolean,
    val dataLocalityEnabled: Boolean,
    val imageCacheEnabled: Boolean,
    val hostCpuCapacity: Double,
    val hostRamCapacity: Double,
    val hostBwCapacity: Double,
    val hostIoCapacity: Double,
    val crossRackBandwidth: Double,
    val crossRegionBandwidth: Double,
    val dataLocalityPolicy: DataLocalityPolicy,
    val imageCacheCapacity: Int,
)

class RealtimeTopologyModel private constructor(
    private val settings: RealtimeTopologySettings,
    initialVmCount: Int,
) {
    private val enabled = settings.enabled
    private val regionCount = settings.regionCount
    private val racksPerRegion = settings.racksPerRegion
    private val hostsPerRack = settings.hostsPerRack
    private val localRegion = settings.localRegion
    private val crossRackLatency = settings.crossRackLatency
    private val crossRegionLatency = settings.crossRegionLatency
    private val crossRegionCost = settings.crossRegionCost
    private val hostFailureRate = settings.hostFailureRate
    private val rackFailureRate = settings.rackFailureRate
    private val regionFailureRate = settings.regionFailureRate
    private val physicalTopologyEnabled = settings.physicalTopologyEnabled
    private val dataLocalityEnabled = settings.dataLocalityEnabled
    private val imageCacheEnabled = settings.imageCacheEnabled
    private val hostCpuCapacity = settings.hostCpuCapacity
    private val hostRamCapacity = settings.hostRamCapacity
    private val hostBwCapacity = settings.hostBwCapacity
    private val hostIoCapacity = settings.hostIoCapacity
    private val crossRackBandwidth = settings.crossRackBandwidth
    private val crossRegionBandwidth = settings.crossRegionBandwidth
    private val dataLocalityPolicy = settings.dataLocalityPolicy
    private val imageCacheCapacity = settings.imageCacheCapacity

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
                        hostRamCapacity = 0.0,
                        hostBwCapacity = 0.0,
                        hostIoCapacity = 0.0,
                        crossRackBandwidth = 0.0,
                        crossRegionBandwidth = 0.0,
                        dataLocalityPolicy = DataLocalityPolicy.PREFER_LOCAL,
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
                hostRamCapacity = hostRamCapacity,
                hostBwCapacity = hostBwCapacity,
                hostIoCapacity = hostIoCapacity,
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

    private val locationsByVmIndex = linkedMapOf<Int, RealtimeTopologyLocation>()
    private val imageCacheByHost = linkedMapOf<RealtimeTopologyLocation, LinkedHashSet<String>>()

    init {
        repeat(initialVmCount) { vmIndex ->
            locationsByVmIndex[vmIndex] = locationForOrdinal(vmIndex)
        }
    }

    fun locationOf(vmIndex: Int): RealtimeTopologyLocation {
        val location = locationsByVmIndex.getOrPut(vmIndex) { locationForOrdinal(vmIndex) }
        return location
    }

    fun registerDynamicVm(
        vmIndex: Int,
        activeVmIndexes: Set<Int> = emptySet(),
    ): RealtimeTopologyLocation {
        val location =
            if (!enabled) {
                locationForOrdinal(vmIndex)
            } else {
                val activeByDomain =
                    activeVmIndexes
                        .map { locationOf(it).failureDomainId }
                        .groupingBy { it }
                        .eachCount()
                allLocations().minWithOrNull(
                    compareBy<RealtimeTopologyLocation> { activeByDomain[it.failureDomainId] ?: 0 }
                        .thenBy { latencyFor(it) }
                        .thenBy { it.failureDomainId.value },
                ) ?: locationForOrdinal(vmIndex)
            }
        locationsByVmIndex[vmIndex] = location
        return location
    }

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

    private fun canRecordImageCache(): Boolean = imageCacheEnabled && imageCacheCapacity > 0

    private fun evictImagesUntilWritable(cache: LinkedHashSet<String>) {
        while (cache.size >= imageCacheCapacity) {
            val oldest = cache.firstOrNull() ?: break
            cache.remove(oldest)
        }
    }

    fun physicalHostMetrics(records: List<RealtimeTaskRecord>): RealtimePhysicalHostMetrics =
        if (physicalTopologyEnabled) {
            calculatePhysicalHostMetrics(records)
        } else {
            emptyPhysicalHostMetrics()
        }

    private fun calculatePhysicalHostMetrics(records: List<RealtimeTaskRecord>): RealtimePhysicalHostMetrics {
        val annotator = candidateAnnotator()
        val demandByHost = annotator.activeDemandByHost(records)
        val hosts = allLocations()
        if (hosts.isEmpty()) return emptyPhysicalHostMetrics()
        val states =
            hosts.map { location ->
                annotator.hostState(location, demandByHost[location] ?: RealtimeResourceDemand())
            }
        return RealtimePhysicalHostMetrics(
            averageUtilization = states.map { it.utilization }.average(),
            averageFragmentation = states.map { it.fragmentation }.average(),
        )
    }

    private fun emptyPhysicalHostMetrics(): RealtimePhysicalHostMetrics = RealtimePhysicalHostMetrics(0.0, 0.0)

    fun latencyFor(location: RealtimeTopologyLocation): Double {
        if (!enabled) return 0.0
        return when {
            location.regionId != localRegion -> crossRegionLatency
            location.rackId.value != 0 -> crossRackLatency
            else -> 0.0
        }
    }

    fun costFor(location: RealtimeTopologyLocation): Double {
        val isCrossRegion = enabled && location.regionId != localRegion
        return if (isCrossRegion) crossRegionCost else 0.0
    }

    fun failurePressure(location: RealtimeTopologyLocation): Double {
        if (!enabled) return 0.0
        val regionPressure = if (location.regionId != localRegion) regionFailureRate else 0.0
        return (hostFailureRate + rackFailureRate + regionPressure).coerceIn(0.0, 1.0)
    }

    fun metricsFor(vmIndexes: List<Int>): RealtimeTopologyMetrics {
        if (!enabled || vmIndexes.isEmpty()) {
            return RealtimeTopologyMetrics(0, 0, 0.0, 0.0, 1.0)
        }
        val locations = vmIndexes.map(::locationOf)
        val crossRegionCount = locations.count { it.regionId != localRegion }
        val crossRackCount = locations.count { it.regionId == localRegion && it.rackId.value != 0 } + crossRegionCount
        val averageLatency = locations.map(::latencyFor).average()
        val topologyCost = locations.sumOf(::costFor)
        val domainCounts =
            locations
                .groupingBy { it.failureDomainId }
                .eachCount()
                .values
                .map { it.toDouble() }
        val sum = domainCounts.sum()
        val spread =
            if (sum <= 0.0) {
                1.0
            } else {
                val squareSum = domainCounts.sumOf { it * it }
                if (squareSum <= 0.0) 1.0 else (sum * sum) / (allLocations().size.toDouble() * squareSum)
            }
        return RealtimeTopologyMetrics(crossRackCount, crossRegionCount, averageLatency, topologyCost, spread)
    }

    private fun locationForOrdinal(ordinal: Int): RealtimeTopologyLocation {
        if (!enabled) {
            return RealtimeTopologyLocation(RegionId(0), RackId(0), HostId(0), FailureDomainId(0))
        }
        val region = ordinal.floorMod(regionCount)
        val rack = (ordinal / regionCount).floorMod(racksPerRegion)
        val host = (ordinal / (regionCount * racksPerRegion)).floorMod(hostsPerRack)
        val domain = ((region * racksPerRegion) + rack) * hostsPerRack + host
        return RealtimeTopologyLocation(RegionId(region), RackId(rack), HostId(host), FailureDomainId(domain))
    }

    private fun allLocations(): List<RealtimeTopologyLocation> =
        (0 until regionCount).flatMap { region ->
            (0 until racksPerRegion).flatMap { rack ->
                (0 until hostsPerRack).map { host ->
                    val domain = ((region * racksPerRegion) + rack) * hostsPerRack + host
                    RealtimeTopologyLocation(RegionId(region), RackId(rack), HostId(host), FailureDomainId(domain))
                }
            }
        }

    private fun Int.floorMod(divisor: Int): Int = Math.floorMod(this, divisor)

    private fun annotationConfig(): TopologyCandidateAnnotationConfig =
        TopologyCandidateAnnotationConfig(
            enabled = enabled,
            physicalTopologyEnabled = physicalTopologyEnabled,
            dataLocalityEnabled = dataLocalityEnabled,
            imageCacheEnabled = imageCacheEnabled,
            localRegion = localRegion,
            hostCpuCapacity = hostCpuCapacity,
            hostRamCapacity = hostRamCapacity,
            hostBwCapacity = hostBwCapacity,
            hostIoCapacity = hostIoCapacity,
            crossRackBandwidth = crossRackBandwidth,
            crossRegionBandwidth = crossRegionBandwidth,
            dataLocalityPolicy = dataLocalityPolicy,
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
