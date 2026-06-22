package datacenter

import scheduler.RealtimeCandidateScoreRecord

@Suppress("TooManyFunctions") // Compatibility facade keeps existing metric getter names stable.
data class RealtimeAlgorithmResult(
    val algorithmName: String,
    val metrics: RealtimeMetricValues,
    val candidateScores: List<RealtimeCandidateScoreRecord> = emptyList(),
) {
    operator fun get(key: RealtimeMetricKey): Double = metrics[key]

    val makespan: Double get() = metrics[RealtimeMetricKey.MAKESPAN]
    val loadBalance: Double get() = metrics[RealtimeMetricKey.LOAD_BALANCE]
    val cost: Double get() = metrics[RealtimeMetricKey.COST]
    val totalTime: Double get() = metrics[RealtimeMetricKey.TOTAL_TIME]
    val fitness: Double get() = metrics[RealtimeMetricKey.FITNESS]
    val averageRealtimeScore: Double get() = metrics[RealtimeMetricKey.AVERAGE_REALTIME_SCORE]
    val averageSelectedLatenessPenalty: Double get() = metrics[RealtimeMetricKey.AVERAGE_SELECTED_LATENESS_PENALTY]
    val averageSelectedDeadlineSlack: Double get() = metrics[RealtimeMetricKey.AVERAGE_SELECTED_DEADLINE_SLACK]
    val averageCandidateScoreSpread: Double get() = metrics[RealtimeMetricKey.AVERAGE_CANDIDATE_SCORE_SPREAD]
    val averageWaitingTime: Double get() = metrics[RealtimeMetricKey.AVERAGE_WAITING_TIME]
    val averageResponseTime: Double get() = metrics[RealtimeMetricKey.AVERAGE_RESPONSE_TIME]
    val rejectedCount: Int get() = metrics.intValue(RealtimeMetricKey.REJECTED_COUNT)
    val timeoutCount: Int get() = metrics.intValue(RealtimeMetricKey.TIMEOUT_COUNT)
    val failedCount: Int get() = metrics.intValue(RealtimeMetricKey.FAILED_COUNT)
    val retryCount: Int get() = metrics.intValue(RealtimeMetricKey.RETRY_COUNT)
    val permanentFailedCount: Int get() = metrics.intValue(RealtimeMetricKey.PERMANENT_FAILED_COUNT)
    val averageDecisionDelay: Double get() = metrics[RealtimeMetricKey.AVERAGE_DECISION_DELAY]
    val completedCount: Int get() = metrics.intValue(RealtimeMetricKey.COMPLETED_COUNT)
    val submittedCount: Int get() = metrics.intValue(RealtimeMetricKey.SUBMITTED_COUNT)
    val slaViolationCount: Int get() = metrics.intValue(RealtimeMetricKey.SLA_VIOLATION_COUNT)
    val slaViolationRate: Double get() = metrics[RealtimeMetricKey.SLA_VIOLATION_RATE]
    val capacityRejectedCount: Int get() = metrics.intValue(RealtimeMetricKey.CAPACITY_REJECTED_COUNT)
    val deadlineRejectedCount: Int get() = metrics.intValue(RealtimeMetricKey.DEADLINE_REJECTED_COUNT)
    val deadlineDegradedCount: Int get() = metrics.intValue(RealtimeMetricKey.DEADLINE_DEGRADED_COUNT)
    val deadlineRetryLaterCount: Int get() = metrics.intValue(RealtimeMetricKey.DEADLINE_RETRY_LATER_COUNT)
    val deadlineMissAcceptedCount: Int get() = metrics.intValue(RealtimeMetricKey.DEADLINE_MISS_ACCEPTED_COUNT)
    val rescheduleAttemptCount: Int get() = metrics.intValue(RealtimeMetricKey.RESCHEDULE_ATTEMPT_COUNT)
    val rescheduleSuccessCount: Int get() = metrics.intValue(RealtimeMetricKey.RESCHEDULE_SUCCESS_COUNT)
    val rescheduleFailureCount: Int get() = metrics.intValue(RealtimeMetricKey.RESCHEDULE_FAILURE_COUNT)
    val averageRescheduleDelay: Double get() = metrics[RealtimeMetricKey.AVERAGE_RESCHEDULE_DELAY]
    val averageQueueDepth: Double get() = metrics[RealtimeMetricKey.AVERAGE_QUEUE_DEPTH]
    val maxQueueDepth: Int get() = metrics.intValue(RealtimeMetricKey.MAX_QUEUE_DEPTH)
    val p95ResponseTime: Double get() = metrics[RealtimeMetricKey.P95_RESPONSE_TIME]
    val p99ResponseTime: Double get() = metrics[RealtimeMetricKey.P99_RESPONSE_TIME]
    val scaleOutCount: Int get() = metrics.intValue(RealtimeMetricKey.SCALE_OUT_COUNT)
    val scaleInCount: Int get() = metrics.intValue(RealtimeMetricKey.SCALE_IN_COUNT)
    val activeVmPeak: Int get() = metrics.intValue(RealtimeMetricKey.ACTIVE_VM_PEAK)
    val autoscalingCost: Double get() = metrics[RealtimeMetricKey.AUTOSCALING_COST]
    val coldStartDelayTotal: Double get() = metrics[RealtimeMetricKey.COLD_START_DELAY_TOTAL]
    val resourceRejectedCount: Int get() = metrics.intValue(RealtimeMetricKey.RESOURCE_REJECTED_COUNT)
    val runtimeFailureCount: Int get() = metrics.intValue(RealtimeMetricKey.RUNTIME_FAILURE_COUNT)
    val timeoutCancelledCount: Int get() = metrics.intValue(RealtimeMetricKey.TIMEOUT_CANCELLED_COUNT)
    val migrationCount: Int get() = metrics.intValue(RealtimeMetricKey.MIGRATION_COUNT)
    val checkpointRecoveryCount: Int get() = metrics.intValue(RealtimeMetricKey.CHECKPOINT_RECOVERY_COUNT)
    val retrySuccessRate: Double get() = metrics[RealtimeMetricKey.RETRY_SUCCESS_RATE]
    val slaPenalty: Double get() = metrics[RealtimeMetricKey.SLA_PENALTY]
    val preemptedCount: Int get() = metrics.intValue(RealtimeMetricKey.PREEMPTED_COUNT)
    val preemptionSuccessCount: Int get() = metrics.intValue(RealtimeMetricKey.PREEMPTION_SUCCESS_COUNT)
    val preemptionFailedCount: Int get() = metrics.intValue(RealtimeMetricKey.PREEMPTION_FAILED_COUNT)
    val averagePreemptionDelay: Double get() = metrics[RealtimeMetricKey.AVERAGE_PREEMPTION_DELAY]
    val preemptionPenalty: Double get() = metrics[RealtimeMetricKey.PREEMPTION_PENALTY]
    val checkpointLossTotal: Long get() = metrics.longValue(RealtimeMetricKey.CHECKPOINT_LOSS_TOTAL)
    val tenantQuotaRejectedCount: Int get() = metrics.intValue(RealtimeMetricKey.TENANT_QUOTA_REJECTED_COUNT)
    val tenantBudgetRejectedCount: Int get() = metrics.intValue(RealtimeMetricKey.TENANT_BUDGET_REJECTED_COUNT)
    val tenantFairnessIndex: Double get() = metrics[RealtimeMetricKey.TENANT_FAIRNESS_INDEX]
    val fairnessViolationCount: Int get() = metrics.intValue(RealtimeMetricKey.FAIRNESS_VIOLATION_COUNT)
    val tenantSlaPenalty: Double get() = metrics[RealtimeMetricKey.TENANT_SLA_PENALTY]
    val dominantResourceFairnessIndex: Double get() = metrics[RealtimeMetricKey.DOMINANT_RESOURCE_FAIRNESS_INDEX]
    val costSlaTradeoffScore: Double get() = metrics[RealtimeMetricKey.COST_SLA_TRADEOFF_SCORE]
    val retrySuccessByTenant: Double get() = metrics[RealtimeMetricKey.RETRY_SUCCESS_BY_TENANT]
    val crossRackAssignmentCount: Int get() = metrics.intValue(RealtimeMetricKey.CROSS_RACK_ASSIGNMENT_COUNT)
    val crossRegionAssignmentCount: Int get() = metrics.intValue(RealtimeMetricKey.CROSS_REGION_ASSIGNMENT_COUNT)
    val averageTopologyLatency: Double get() = metrics[RealtimeMetricKey.AVERAGE_TOPOLOGY_LATENCY]
    val topologyCost: Double get() = metrics[RealtimeMetricKey.TOPOLOGY_COST]
    val hostFailureCount: Int get() = metrics.intValue(RealtimeMetricKey.HOST_FAILURE_COUNT)
    val rackFailureCount: Int get() = metrics.intValue(RealtimeMetricKey.RACK_FAILURE_COUNT)
    val regionFailureCount: Int get() = metrics.intValue(RealtimeMetricKey.REGION_FAILURE_COUNT)
    val failureDomainSpreadScore: Double get() = metrics[RealtimeMetricKey.FAILURE_DOMAIN_SPREAD_SCORE]
}

private fun RealtimeMetricValues.intValue(key: RealtimeMetricKey): Int = valueForKey(key).toInt()

private fun RealtimeMetricValues.longValue(key: RealtimeMetricKey): Long = valueForKey(key).toLong()
