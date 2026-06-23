package scheduler.realtime

data class RealtimeNodeState(
    val vmIndex: Int,
    val vmId: Long,
    val lifecycle: RealtimeVmLifecycle = RealtimeVmLifecycle.ACTIVE,
    val runningCount: Int,
    val pendingCount: Int,
    val queueDepth: Int,
    val availableSlots: Int,
    val acceptingWork: Boolean,
    val estimatedLoad: Double,
    val availableTime: Double,
    val failurePressure: Double,
    val ramPressure: Double = 0.0,
    val bwPressure: Double = 0.0,
    val ioPressure: Double = 0.0,
    val networkLatency: Double = 0.0,
    val imagePullDelay: Double = 0.0,
    val resourcePressure: Double = 0.0,
    val resourceAcceptingWork: Boolean = true,
    val rejectionReason: String? = null,
    val regionId: RegionId = RegionId(0),
    val rackId: RackId = RackId(0),
    val hostId: HostId = HostId(0),
    val failureDomainId: FailureDomainId = FailureDomainId(0),
    val topologyLatency: Double = 0.0,
    val topologyCost: Double = 0.0,
    val failureDomainLoad: Int = 0,
    val topologyFailurePressure: Double = 0.0,
    val physicalHostUtilization: Double = 0.0,
    val hostResourceFragmentation: Double = 0.0,
    val networkTransferDelay: Double = 0.0,
    val hostResourceDelay: Double = 0.0,
    val noisyNeighborPressure: Double = 0.0,
    val dataLocalityHit: Boolean = true,
    val imageCacheHit: Boolean = false,
    val placementFailureReason: String? = null,
)

data class RealtimeHostState(
    val datacenterId: DatacenterId = DatacenterId(0),
    val location: RealtimeTopologyLocation,
    val cpuCapacity: Double,
    val ramCapacity: Double,
    val bwCapacity: Double,
    val ioCapacity: Double,
    val allocatedCpu: Double = 0.0,
    val allocatedRam: Double = 0.0,
    val allocatedBw: Double = 0.0,
    val allocatedIo: Double = 0.0,
    val cachedImages: Set<String> = emptySet(),
) {
    val utilization: Double
        get() =
            listOf(
                allocatedCpu.ratio(cpuCapacity),
                allocatedRam.ratio(ramCapacity),
                allocatedBw.ratio(bwCapacity),
                allocatedIo.ratio(ioCapacity),
            ).maxOrNull() ?: 0.0

    val fragmentation: Double
        get() {
            val capacities = listOf(cpuCapacity, ramCapacity, bwCapacity, ioCapacity)
            val allocations = listOf(allocatedCpu, allocatedRam, allocatedBw, allocatedIo)
            val usableRatios =
                capacities.zip(allocations).mapNotNull { (capacity, used) ->
                    if (capacity <= 0.0) null else ((capacity - used).coerceAtLeast(0.0) / capacity)
                }
            if (usableRatios.isEmpty()) return 0.0
            return (usableRatios.maxOrNull() ?: 0.0) - (usableRatios.minOrNull() ?: 0.0)
        }

    private fun Double.ratio(capacity: Double): Double {
        val ratio = if (capacity <= 0.0) 0.0 else (this / capacity).coerceAtLeast(0.0)
        return ratio
    }
}

sealed interface RealtimePlacementDecision {
    val vmIndex: VmIndex

    data class Accepted(
        override val vmIndex: VmIndex,
        val location: RealtimeTopologyLocation,
        val hostState: RealtimeHostState,
        val dataLocal: Boolean,
        val imageCacheHit: Boolean,
        val networkTransferDelay: Double,
        val networkTransferGb: Double,
        val imagePullDelay: Double,
        val hostResourceDelay: Double,
        val noisyNeighborPressure: Double,
        val topologyCost: Double,
        val score: Double,
    ) : RealtimePlacementDecision {
        val placementDelay: Double get() = networkTransferDelay + imagePullDelay + hostResourceDelay
    }

    data class Rejected(
        override val vmIndex: VmIndex,
        val location: RealtimeTopologyLocation,
        val reason: String,
    ) : RealtimePlacementDecision
}

data class NodeCandidate(
    val nodeState: RealtimeNodeState,
    val placement: RealtimePlacementDecision,
    val score: Double,
) {
    val vmIndex: Int get() = nodeState.vmIndex
    val acceptedPlacement: RealtimePlacementDecision.Accepted? get() = placement as? RealtimePlacementDecision.Accepted
    val isAccepted: Boolean get() = acceptedPlacement != null && nodeState.acceptingWork
}

fun interface CandidateFilter {
    fun accepts(candidate: NodeCandidate): Boolean
}

fun interface CandidateScorer {
    fun score(candidate: NodeCandidate): Double
}
