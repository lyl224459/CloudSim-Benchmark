package scheduler.realtime

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
        hostResourceDelay = accepted?.hostResourceDelay ?: hostResourceDelay,
        noisyNeighborPressure = accepted?.noisyNeighborPressure ?: noisyNeighborPressure,
        imagePullDelay = imagePullDelay + (accepted?.imagePullDelay ?: 0.0),
        resourcePressure = resourcePressure + (accepted?.noisyNeighborPressure ?: 0.0),
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
