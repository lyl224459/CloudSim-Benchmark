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

class RealtimeTopologyModel private constructor(
    private val enabled: Boolean,
    private val regionCount: Int,
    private val racksPerRegion: Int,
    private val hostsPerRack: Int,
    private val localRegion: RegionId,
    private val crossRackLatency: Double,
    private val crossRegionLatency: Double,
    private val crossRegionCost: Double,
    private val hostFailureRate: Double,
    private val rackFailureRate: Double,
    private val regionFailureRate: Double,
    private val physicalTopologyEnabled: Boolean,
    private val dataLocalityEnabled: Boolean,
    private val imageCacheEnabled: Boolean,
    private val hostCpuCapacity: Double,
    private val hostRamCapacity: Double,
    private val hostBwCapacity: Double,
    private val hostIoCapacity: Double,
    private val crossRackBandwidth: Double,
    private val crossRegionBandwidth: Double,
    private val dataLocalityPolicy: DataLocalityPolicy,
    private val imageCacheCapacity: Int,
    initialVmCount: Int,
) {
    companion object {
        val Disabled =
            RealtimeTopologyModel(
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
                initialVmCount = 0,
            )

        fun fromConfig(
            scheduling: RealtimeSchedulingConfig,
            initialVmCount: Int,
        ): RealtimeTopologyModel =
            RealtimeTopologyModel(
                enabled =
                    scheduling.topologyEnabled ||
                        scheduling.physicalTopologyEnabled ||
                        scheduling.dataLocalityEnabled ||
                        scheduling.imageCacheEnabled,
                regionCount = scheduling.regionCount.coerceAtLeast(1),
                racksPerRegion = scheduling.racksPerRegion.coerceAtLeast(1),
                hostsPerRack =
                    if (scheduling.physicalTopologyEnabled) {
                        scheduling.hostCountPerRack.coerceAtLeast(1)
                    } else {
                        scheduling.hostsPerRack.coerceAtLeast(1)
                    },
                localRegion = RegionId(scheduling.localRegion.coerceIn(0, scheduling.regionCount.coerceAtLeast(1) - 1)),
                crossRackLatency = scheduling.crossRackLatency,
                crossRegionLatency = scheduling.crossRegionLatency,
                crossRegionCost = scheduling.crossRegionCost,
                hostFailureRate = scheduling.hostFailureRate,
                rackFailureRate = scheduling.rackFailureRate,
                regionFailureRate = scheduling.regionFailureRate,
                physicalTopologyEnabled = scheduling.physicalTopologyEnabled,
                dataLocalityEnabled = scheduling.dataLocalityEnabled,
                imageCacheEnabled = scheduling.imageCacheEnabled,
                hostCpuCapacity = scheduling.hostCpuCapacity,
                hostRamCapacity = scheduling.hostRamCapacity,
                hostBwCapacity = scheduling.hostBwCapacity,
                hostIoCapacity = scheduling.hostIoCapacity,
                crossRackBandwidth = scheduling.crossRackBandwidth,
                crossRegionBandwidth = scheduling.crossRegionBandwidth,
                dataLocalityPolicy = scheduling.normalizedDataLocalityPolicy(),
                imageCacheCapacity = scheduling.imageCacheCapacity,
                initialVmCount = initialVmCount,
            )
    }

    private val locationsByVmIndex = linkedMapOf<Int, RealtimeTopologyLocation>()
    private val imageCacheByHost = linkedMapOf<RealtimeTopologyLocation, LinkedHashSet<String>>()

    init {
        repeat(initialVmCount) { vmIndex ->
            locationsByVmIndex[vmIndex] = locationForOrdinal(vmIndex)
        }
    }

    fun locationOf(vmIndex: Int): RealtimeTopologyLocation = locationsByVmIndex.getOrPut(vmIndex) { locationForOrdinal(vmIndex) }

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

    fun costFor(location: RealtimeTopologyLocation): Double = if (enabled && location.regionId != localRegion) crossRegionCost else 0.0

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
