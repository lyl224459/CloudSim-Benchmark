package datacenter

import scheduler.RealtimeCandidateScoreRecord

internal const val REALTIME_CANDIDATE_SCORE_FILE = "realtime_candidate_scores.csv"

internal val realtimeCandidateScoreCsvHeaders =
    listOf(
        "Algorithm",
        "Run",
        "CloudletId",
        "ArrivalTime",
        "SelectedVmIndex",
        "CandidateVmIndex",
        "Accepted",
        "Selected",
        "TotalScore",
        "ProjectedFinishTime",
        "EstimatedRuntime",
        "DeadlineSlack",
        "LatenessPenalty",
        "PriorityPressure",
        "PreemptionCost",
        "ResourcePressure",
        "TopologyLatency",
        "TopologyCost",
        "TenantFairnessPressure",
        "QueuePressure",
    )

internal fun RealtimeCandidateScoreRecord.toCsvRow(
    algorithmName: String,
    run: Int,
): List<Any?> =
    listOf(
        algorithmName,
        run,
        cloudletId,
        arrivalTime,
        selectedVmIndex,
        candidateVmIndex,
        accepted,
        selected,
        breakdown.totalScore,
        breakdown.projectedFinishTime,
        breakdown.estimatedRuntime,
        breakdown.deadlineSlack,
        breakdown.latenessPenalty,
        breakdown.priorityPressure,
        breakdown.preemptionCost,
        breakdown.resourcePressure,
        breakdown.topologyLatency,
        breakdown.topologyCost,
        breakdown.tenantFairnessPressure,
        breakdown.queuePressure,
    )
