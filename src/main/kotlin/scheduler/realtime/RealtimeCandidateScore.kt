package scheduler.realtime

import datacenter.ObjectiveFunction

private const val LATENESS_WEIGHT = 4.0
private const val DEFAULT_PRIORITY_PRESSURE = 1.0
private const val PREEMPTION_MISS_COST = 1.0

data class RealtimeScoreBreakdown(
    val totalScore: Double,
    val projectedFinishTime: Double,
    val estimatedRuntime: Double,
    val deadlineSlack: Double,
    val latenessPenalty: Double,
    val priorityPressure: Double,
    val preemptionCost: Double,
    val resourcePressure: Double,
    val topologyLatency: Double,
    val topologyCost: Double,
    val tenantFairnessPressure: Double,
    val queuePressure: Double,
)

data class RealtimeCandidateScore(
    val vmIndex: Int,
    val vmId: Long,
    val accepted: Boolean,
    val breakdown: RealtimeScoreBreakdown,
) {
    val totalScore: Double get() = breakdown.totalScore
}

data class RealtimeCandidateScoreRecord(
    val cloudletId: Long,
    val arrivalTime: Double,
    val selectedVmIndex: Int,
    val candidateVmIndex: Int,
    val accepted: Boolean,
    val selected: Boolean,
    val breakdown: RealtimeScoreBreakdown,
) {
    val totalScore: Double get() = breakdown.totalScore
}

class RealtimeCandidateScoreCalculator {
    fun scoreAccepted(context: RealtimeSchedulingContext): List<RealtimeCandidateScore> =
        acceptedStates(context).map { state -> score(context, state, accepted = true) }

    fun scoreRecords(
        context: RealtimeSchedulingContext,
        selectedVmIndex: Int,
    ): List<RealtimeCandidateScoreRecord> =
        candidateStates(context).map { (state, accepted) ->
            val score = score(context, state, accepted)
            RealtimeCandidateScoreRecord(
                cloudletId = context.taskMetadata.cloudletId,
                arrivalTime = context.taskMetadata.originalArrivalTime,
                selectedVmIndex = selectedVmIndex,
                candidateVmIndex = state.vmIndex,
                accepted = accepted,
                selected = state.vmIndex == selectedVmIndex,
                breakdown = score.breakdown,
            )
        }

    fun score(
        context: RealtimeSchedulingContext,
        state: RealtimeNodeState,
        accepted: Boolean = state.acceptingWork,
    ): RealtimeCandidateScore {
        val estimatedRuntime = estimatedRuntime(context, state).finiteOrZero()
        val projectedFinishTime = projectedFinishTime(state, estimatedRuntime).finiteOrZero()
        val deadlineSlack = deadlineSlack(context, projectedFinishTime)
        val latenessPenalty =
            deadlineSlack
                .takeIf { context.taskMetadata.deadline != null }
                ?.let { (-it).coerceAtLeast(0.0) }
                ?: 0.0
        val priorityPressure = priorityPressure(context.taskMetadata.priority)
        val preemptionCost = preemptionCost(context, state)
        val resourcePressure = state.resourcePressure.nonNegativeFinite()
        val topologyLatency = state.topologyLatency.nonNegativeFinite()
        val topologyCost = state.topologyCost.nonNegativeFinite()
        val tenantFairnessPressure = context.tenantFairnessPressure.nonNegativeFinite()
        val queuePressure = state.queueDepth.coerceAtLeast(0).toDouble()
        val totalScore =
            (
                projectedFinishTime +
                    latenessPenalty * LATENESS_WEIGHT +
                    priorityPressure +
                    preemptionCost +
                    resourcePressure +
                    topologyLatency +
                    topologyCost +
                    tenantFairnessPressure +
                    queuePressure
            ).finiteOrZero()

        return RealtimeCandidateScore(
            vmIndex = state.vmIndex,
            vmId = state.vmId,
            accepted = accepted,
            breakdown =
                RealtimeScoreBreakdown(
                    totalScore = totalScore,
                    projectedFinishTime = projectedFinishTime,
                    estimatedRuntime = estimatedRuntime,
                    deadlineSlack = deadlineSlack,
                    latenessPenalty = latenessPenalty,
                    priorityPressure = priorityPressure,
                    preemptionCost = preemptionCost,
                    resourcePressure = resourcePressure,
                    topologyLatency = topologyLatency,
                    topologyCost = topologyCost,
                    tenantFairnessPressure = tenantFairnessPressure,
                    queuePressure = queuePressure,
                ),
        )
    }

    private fun acceptedStates(context: RealtimeSchedulingContext): List<RealtimeNodeState> =
        if (context.nodeCandidates.isNotEmpty()) {
            context.acceptedCandidates.map { it.nodeState }
        } else {
            context.nodeStates.filter { it.acceptingWork }
        }

    private fun candidateStates(context: RealtimeSchedulingContext): List<Pair<RealtimeNodeState, Boolean>> =
        if (context.nodeCandidates.isNotEmpty()) {
            context.nodeCandidates.map { candidate -> candidate.nodeState to candidate.isAccepted }
        } else {
            context.nodeStates.map { state -> state to state.acceptingWork }
        }

    private fun estimatedRuntime(
        context: RealtimeSchedulingContext,
        state: RealtimeNodeState,
    ): Double {
        val mips = context.vmList.getOrNull(state.vmIndex)?.mips ?: 0.0
        return if (mips > 0.0) context.newCloudlet.length.toDouble() / mips else 0.0
    }

    private fun projectedFinishTime(
        state: RealtimeNodeState,
        estimatedRuntime: Double,
    ): Double =
        state.availableTime +
            estimatedRuntime +
            state.networkTransferDelay +
            state.imagePullDelay

    private fun deadlineSlack(
        context: RealtimeSchedulingContext,
        projectedFinishTime: Double,
    ): Double =
        context.taskMetadata.deadline
            ?.let { deadline -> deadline - projectedFinishTime }
            ?.finiteOrZero() ?: 0.0

    private fun priorityPressure(priority: Int): Double = DEFAULT_PRIORITY_PRESSURE / (priority.coerceAtLeast(0) + 1.0)

    private fun preemptionCost(
        context: RealtimeSchedulingContext,
        state: RealtimeNodeState,
    ): Double {
        if (context.preemptionCandidates.isEmpty()) return 0.0
        val preemptableVmIndexes = context.preemptionCandidates.map { it.victimVmIndex.value }.toSet()
        return if (state.vmIndex in preemptableVmIndexes) 0.0 else PREEMPTION_MISS_COST
    }
}

class RealtimeSchedulingObjectiveFunction(
    context: RealtimeSchedulingContext,
    candidateStates: List<RealtimeNodeState>,
    private val cloudletCount: Int,
    scoreCalculator: RealtimeCandidateScoreCalculator = RealtimeCandidateScoreCalculator(),
) : ObjectiveFunction {
    private val candidateScores =
        candidateStates.map { state ->
            scoreCalculator.score(context, state, accepted = state.acceptingWork)
        }

    init {
        require(candidateScores.isNotEmpty()) { "实时评分目标函数需要至少 1 个候选 VM" }
        require(cloudletCount > 0) { "实时评分目标函数需要至少 1 个待评估任务" }
    }

    override fun calculate(params: IntArray): Double {
        require(params.size == cloudletCount) {
            "任务到 VM 的映射数量 ${params.size} 与任务数量 $cloudletCount 不一致"
        }
        return params
            .sumOf { candidateIndex ->
                val bounded = candidateIndex.coerceIn(candidateScores.indices)
                candidateScores[bounded].totalScore
            }.finiteOrZero()
    }
}

private fun Double.finiteOrZero(): Double = if (isFinite()) this else 0.0

private fun Double.nonNegativeFinite(): Double = coerceAtLeast(0.0).finiteOrZero()
