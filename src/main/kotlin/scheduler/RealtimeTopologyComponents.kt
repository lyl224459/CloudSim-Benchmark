package scheduler

import config.DataLocalityPolicy

internal data class RealtimeTopologySettings(
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

internal class RealtimeTopologyPlacementMap(
    private val settings: RealtimeTopologySettings,
    initialVmCount: Int,
) {
    private val locationsByVmIndex = linkedMapOf<Int, RealtimeTopologyLocation>()

    init {
        repeat(initialVmCount) { vmIndex -> locationsByVmIndex[vmIndex] = locationForOrdinal(vmIndex) }
    }

    fun locationOf(vmIndex: Int): RealtimeTopologyLocation =
        locationsByVmIndex.getOrPut(vmIndex) {
            locationForOrdinal(vmIndex)
        }

    fun registerDynamicVm(
        vmIndex: Int,
        activeVmIndexes: Set<Int>,
        latencyFor: (RealtimeTopologyLocation) -> Double,
    ): RealtimeTopologyLocation {
        val location =
            if (!settings.enabled) {
                locationForOrdinal(vmIndex)
            } else {
                leastLoadedLocation(activeVmIndexes, latencyFor) ?: locationForOrdinal(vmIndex)
            }
        locationsByVmIndex[vmIndex] = location
        return location
    }

    fun allLocations(): List<RealtimeTopologyLocation> =
        (0 until settings.regionCount).flatMap { region ->
            (0 until settings.racksPerRegion).flatMap { rack ->
                (0 until settings.hostsPerRack).map { host -> location(region, rack, host) }
            }
        }

    private fun leastLoadedLocation(
        activeVmIndexes: Set<Int>,
        latencyFor: (RealtimeTopologyLocation) -> Double,
    ): RealtimeTopologyLocation? {
        val activeByDomain =
            activeVmIndexes
                .map { locationOf(it).failureDomainId }
                .groupingBy { it }
                .eachCount()
        return allLocations().minWithOrNull(
            compareBy<RealtimeTopologyLocation> { activeByDomain[it.failureDomainId] ?: 0 }
                .thenBy(latencyFor)
                .thenBy { it.failureDomainId.value },
        )
    }

    private fun locationForOrdinal(ordinal: Int): RealtimeTopologyLocation {
        if (!settings.enabled) return location(0, 0, 0)
        val region = Math.floorMod(ordinal, settings.regionCount)
        val rack = Math.floorMod(ordinal / settings.regionCount, settings.racksPerRegion)
        val host =
            Math.floorMod(
                ordinal / (settings.regionCount * settings.racksPerRegion),
                settings.hostsPerRack,
            )
        return location(region, rack, host)
    }

    private fun location(
        region: Int,
        rack: Int,
        host: Int,
    ): RealtimeTopologyLocation {
        val domain = ((region * settings.racksPerRegion) + rack) * settings.hostsPerRack + host
        return RealtimeTopologyLocation(RegionId(region), RackId(rack), HostId(host), FailureDomainId(domain))
    }
}

internal class RealtimeTopologyMetricsCalculator(
    private val settings: RealtimeTopologySettings,
    private val placements: RealtimeTopologyPlacementMap,
) {
    fun latencyFor(location: RealtimeTopologyLocation): Double {
        if (!settings.enabled) return 0.0
        return when {
            location.regionId != settings.localRegion -> settings.crossRegionLatency
            location.rackId.value != 0 -> settings.crossRackLatency
            else -> 0.0
        }
    }

    fun costFor(location: RealtimeTopologyLocation): Double =
        if (settings.enabled && location.regionId != settings.localRegion) settings.crossRegionCost else 0.0

    fun failurePressure(location: RealtimeTopologyLocation): Double {
        if (!settings.enabled) return 0.0
        val regionPressure = if (location.regionId != settings.localRegion) settings.regionFailureRate else 0.0
        return (settings.hostFailureRate + settings.rackFailureRate + regionPressure).coerceIn(0.0, 1.0)
    }

    fun metricsFor(vmIndexes: List<Int>): RealtimeTopologyMetrics {
        if (!settings.enabled || vmIndexes.isEmpty()) return emptyMetrics()
        val locations = vmIndexes.map(placements::locationOf)
        val crossRegionCount = locations.count { it.regionId != settings.localRegion }
        val crossRackCount =
            locations.count { it.regionId == settings.localRegion && it.rackId.value != 0 } + crossRegionCount
        return RealtimeTopologyMetrics(
            crossRackAssignmentCount = crossRackCount,
            crossRegionAssignmentCount = crossRegionCount,
            averageTopologyLatency = locations.map(::latencyFor).average(),
            topologyCost = locations.sumOf(::costFor),
            failureDomainSpreadScore = spreadScore(locations),
        )
    }

    fun physicalHostMetrics(
        records: List<RealtimeTaskRecord>,
        annotator: TopologyCandidateAnnotator,
    ): RealtimePhysicalHostMetrics =
        when {
            !settings.physicalTopologyEnabled -> emptyPhysicalHostMetrics()
            placements.allLocations().isEmpty() -> emptyPhysicalHostMetrics()
            else -> calculatePhysicalHostMetrics(records, annotator)
        }

    private fun calculatePhysicalHostMetrics(
        records: List<RealtimeTaskRecord>,
        annotator: TopologyCandidateAnnotator,
    ): RealtimePhysicalHostMetrics {
        val demandByHost = annotator.activeDemandByHost(records)
        val states =
            placements.allLocations().map { location ->
                annotator.hostState(location, demandByHost[location] ?: RealtimeResourceDemand())
            }
        return RealtimePhysicalHostMetrics(
            averageUtilization = states.map { it.utilization }.average(),
            averageFragmentation = states.map { it.fragmentation }.average(),
        )
    }

    private fun spreadScore(locations: List<RealtimeTopologyLocation>): Double {
        val domainCounts =
            locations
                .groupingBy { it.failureDomainId }
                .eachCount()
                .values
                .map(Int::toDouble)
        val sum = domainCounts.sum()
        if (sum <= 0.0) return 1.0
        val squareSum = domainCounts.sumOf { it * it }
        return if (squareSum <= 0.0) 1.0 else (sum * sum) / (placements.allLocations().size.toDouble() * squareSum)
    }

    private fun emptyMetrics(): RealtimeTopologyMetrics = RealtimeTopologyMetrics(0, 0, 0.0, 0.0, 1.0)

    private fun emptyPhysicalHostMetrics(): RealtimePhysicalHostMetrics = RealtimePhysicalHostMetrics(0.0, 0.0)
}
