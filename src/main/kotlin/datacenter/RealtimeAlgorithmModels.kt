package datacenter

import util.StatisticalValue

@Suppress("LongParameterList", "TooManyFunctions")
data class RealtimeAlgorithmResult(
    val algorithmName: String,
    val metrics: RealtimeMetricValues,
) {
    constructor(
        algorithmName: String,
        makespan: Double,
        loadBalance: Double,
        cost: Double,
        totalTime: Double,
        fitness: Double,
        averageWaitingTime: Double,
        averageResponseTime: Double,
        rejectedCount: Int,
        timeoutCount: Int,
        failedCount: Int,
        retryCount: Int,
        permanentFailedCount: Int,
        averageDecisionDelay: Double,
        completedCount: Int,
        submittedCount: Int,
        slaViolationCount: Int,
        slaViolationRate: Double,
        capacityRejectedCount: Int,
        averageQueueDepth: Double,
        maxQueueDepth: Int,
        p95ResponseTime: Double,
        p99ResponseTime: Double,
        scaleOutCount: Int,
        scaleInCount: Int,
        activeVmPeak: Int,
        autoscalingCost: Double,
        coldStartDelayTotal: Double,
        resourceRejectedCount: Int,
        runtimeFailureCount: Int,
        timeoutCancelledCount: Int,
        migrationCount: Int,
        checkpointRecoveryCount: Int,
        retrySuccessRate: Double,
        slaPenalty: Double,
        preemptedCount: Int,
        preemptionSuccessCount: Int,
        preemptionFailedCount: Int,
        averagePreemptionDelay: Double,
        preemptionPenalty: Double,
        checkpointLossTotal: Long,
        tenantQuotaRejectedCount: Int,
        tenantBudgetRejectedCount: Int,
        tenantFairnessIndex: Double,
        fairnessViolationCount: Int,
        tenantSlaPenalty: Double,
        dominantResourceFairnessIndex: Double,
        costSlaTradeoffScore: Double,
        retrySuccessByTenant: Double,
        crossRackAssignmentCount: Int,
        crossRegionAssignmentCount: Int,
        averageTopologyLatency: Double,
        topologyCost: Double,
        hostFailureCount: Int,
        rackFailureCount: Int,
        regionFailureCount: Int,
        failureDomainSpreadScore: Double,
    ) : this(
        algorithmName = algorithmName,
        metrics =
            RealtimeMetricValues.of(
                RealtimeMetricKey.MAKESPAN to makespan,
                RealtimeMetricKey.LOAD_BALANCE to loadBalance,
                RealtimeMetricKey.COST to cost,
                RealtimeMetricKey.TOTAL_TIME to totalTime,
                RealtimeMetricKey.FITNESS to fitness,
                RealtimeMetricKey.AVERAGE_WAITING_TIME to averageWaitingTime,
                RealtimeMetricKey.AVERAGE_RESPONSE_TIME to averageResponseTime,
                RealtimeMetricKey.REJECTED_COUNT to rejectedCount,
                RealtimeMetricKey.TIMEOUT_COUNT to timeoutCount,
                RealtimeMetricKey.FAILED_COUNT to failedCount,
                RealtimeMetricKey.RETRY_COUNT to retryCount,
                RealtimeMetricKey.PERMANENT_FAILED_COUNT to permanentFailedCount,
                RealtimeMetricKey.AVERAGE_DECISION_DELAY to averageDecisionDelay,
                RealtimeMetricKey.COMPLETED_COUNT to completedCount,
                RealtimeMetricKey.SUBMITTED_COUNT to submittedCount,
                RealtimeMetricKey.SLA_VIOLATION_COUNT to slaViolationCount,
                RealtimeMetricKey.SLA_VIOLATION_RATE to slaViolationRate,
                RealtimeMetricKey.CAPACITY_REJECTED_COUNT to capacityRejectedCount,
                RealtimeMetricKey.AVERAGE_QUEUE_DEPTH to averageQueueDepth,
                RealtimeMetricKey.MAX_QUEUE_DEPTH to maxQueueDepth,
                RealtimeMetricKey.P95_RESPONSE_TIME to p95ResponseTime,
                RealtimeMetricKey.P99_RESPONSE_TIME to p99ResponseTime,
                RealtimeMetricKey.SCALE_OUT_COUNT to scaleOutCount,
                RealtimeMetricKey.SCALE_IN_COUNT to scaleInCount,
                RealtimeMetricKey.ACTIVE_VM_PEAK to activeVmPeak,
                RealtimeMetricKey.AUTOSCALING_COST to autoscalingCost,
                RealtimeMetricKey.COLD_START_DELAY_TOTAL to coldStartDelayTotal,
                RealtimeMetricKey.RESOURCE_REJECTED_COUNT to resourceRejectedCount,
                RealtimeMetricKey.RUNTIME_FAILURE_COUNT to runtimeFailureCount,
                RealtimeMetricKey.TIMEOUT_CANCELLED_COUNT to timeoutCancelledCount,
                RealtimeMetricKey.MIGRATION_COUNT to migrationCount,
                RealtimeMetricKey.CHECKPOINT_RECOVERY_COUNT to checkpointRecoveryCount,
                RealtimeMetricKey.RETRY_SUCCESS_RATE to retrySuccessRate,
                RealtimeMetricKey.SLA_PENALTY to slaPenalty,
                RealtimeMetricKey.PREEMPTED_COUNT to preemptedCount,
                RealtimeMetricKey.PREEMPTION_SUCCESS_COUNT to preemptionSuccessCount,
                RealtimeMetricKey.PREEMPTION_FAILED_COUNT to preemptionFailedCount,
                RealtimeMetricKey.AVERAGE_PREEMPTION_DELAY to averagePreemptionDelay,
                RealtimeMetricKey.PREEMPTION_PENALTY to preemptionPenalty,
                RealtimeMetricKey.CHECKPOINT_LOSS_TOTAL to checkpointLossTotal,
                RealtimeMetricKey.TENANT_QUOTA_REJECTED_COUNT to tenantQuotaRejectedCount,
                RealtimeMetricKey.TENANT_BUDGET_REJECTED_COUNT to tenantBudgetRejectedCount,
                RealtimeMetricKey.TENANT_FAIRNESS_INDEX to tenantFairnessIndex,
                RealtimeMetricKey.FAIRNESS_VIOLATION_COUNT to fairnessViolationCount,
                RealtimeMetricKey.TENANT_SLA_PENALTY to tenantSlaPenalty,
                RealtimeMetricKey.DOMINANT_RESOURCE_FAIRNESS_INDEX to dominantResourceFairnessIndex,
                RealtimeMetricKey.COST_SLA_TRADEOFF_SCORE to costSlaTradeoffScore,
                RealtimeMetricKey.RETRY_SUCCESS_BY_TENANT to retrySuccessByTenant,
                RealtimeMetricKey.CROSS_RACK_ASSIGNMENT_COUNT to crossRackAssignmentCount,
                RealtimeMetricKey.CROSS_REGION_ASSIGNMENT_COUNT to crossRegionAssignmentCount,
                RealtimeMetricKey.AVERAGE_TOPOLOGY_LATENCY to averageTopologyLatency,
                RealtimeMetricKey.TOPOLOGY_COST to topologyCost,
                RealtimeMetricKey.HOST_FAILURE_COUNT to hostFailureCount,
                RealtimeMetricKey.RACK_FAILURE_COUNT to rackFailureCount,
                RealtimeMetricKey.REGION_FAILURE_COUNT to regionFailureCount,
                RealtimeMetricKey.FAILURE_DOMAIN_SPREAD_SCORE to failureDomainSpreadScore,
            ),
    )

    operator fun get(key: RealtimeMetricKey): Double = metrics[key]

    val makespan: Double get() = metrics[RealtimeMetricKey.MAKESPAN]
    val loadBalance: Double get() = metrics[RealtimeMetricKey.LOAD_BALANCE]
    val cost: Double get() = metrics[RealtimeMetricKey.COST]
    val totalTime: Double get() = metrics[RealtimeMetricKey.TOTAL_TIME]
    val fitness: Double get() = metrics[RealtimeMetricKey.FITNESS]
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

@Suppress("LongParameterList", "TooManyFunctions")
data class RealtimeAlgorithmStatistics(
    val algorithmName: String,
    val metrics: Map<RealtimeMetricKey, StatisticalValue>,
) {
    constructor(
        algorithmName: String,
        makespan: StatisticalValue,
        loadBalance: StatisticalValue,
        cost: StatisticalValue,
        totalTime: StatisticalValue,
        fitness: StatisticalValue,
        averageWaitingTime: StatisticalValue,
        averageResponseTime: StatisticalValue,
        rejectedCount: StatisticalValue,
        timeoutCount: StatisticalValue,
        failedCount: StatisticalValue,
        retryCount: StatisticalValue,
        permanentFailedCount: StatisticalValue,
        averageDecisionDelay: StatisticalValue,
        completedCount: StatisticalValue,
        submittedCount: StatisticalValue,
        slaViolationCount: StatisticalValue,
        slaViolationRate: StatisticalValue,
        capacityRejectedCount: StatisticalValue,
        averageQueueDepth: StatisticalValue,
        maxQueueDepth: StatisticalValue,
        p95ResponseTime: StatisticalValue,
        p99ResponseTime: StatisticalValue,
        scaleOutCount: StatisticalValue,
        scaleInCount: StatisticalValue,
        activeVmPeak: StatisticalValue,
        autoscalingCost: StatisticalValue,
        coldStartDelayTotal: StatisticalValue,
        resourceRejectedCount: StatisticalValue,
        runtimeFailureCount: StatisticalValue,
        timeoutCancelledCount: StatisticalValue,
        migrationCount: StatisticalValue,
        checkpointRecoveryCount: StatisticalValue,
        retrySuccessRate: StatisticalValue,
        slaPenalty: StatisticalValue,
        preemptedCount: StatisticalValue,
        preemptionSuccessCount: StatisticalValue,
        preemptionFailedCount: StatisticalValue,
        averagePreemptionDelay: StatisticalValue,
        preemptionPenalty: StatisticalValue,
        checkpointLossTotal: StatisticalValue,
        tenantQuotaRejectedCount: StatisticalValue,
        tenantBudgetRejectedCount: StatisticalValue,
        tenantFairnessIndex: StatisticalValue,
        fairnessViolationCount: StatisticalValue,
        tenantSlaPenalty: StatisticalValue,
        dominantResourceFairnessIndex: StatisticalValue,
        costSlaTradeoffScore: StatisticalValue,
        retrySuccessByTenant: StatisticalValue,
        crossRackAssignmentCount: StatisticalValue,
        crossRegionAssignmentCount: StatisticalValue,
        averageTopologyLatency: StatisticalValue,
        topologyCost: StatisticalValue,
        hostFailureCount: StatisticalValue,
        rackFailureCount: StatisticalValue,
        regionFailureCount: StatisticalValue,
        failureDomainSpreadScore: StatisticalValue,
    ) : this(
        algorithmName = algorithmName,
        metrics =
            linkedMapOf(
                RealtimeMetricKey.MAKESPAN to makespan,
                RealtimeMetricKey.LOAD_BALANCE to loadBalance,
                RealtimeMetricKey.COST to cost,
                RealtimeMetricKey.TOTAL_TIME to totalTime,
                RealtimeMetricKey.FITNESS to fitness,
                RealtimeMetricKey.AVERAGE_WAITING_TIME to averageWaitingTime,
                RealtimeMetricKey.AVERAGE_RESPONSE_TIME to averageResponseTime,
                RealtimeMetricKey.REJECTED_COUNT to rejectedCount,
                RealtimeMetricKey.TIMEOUT_COUNT to timeoutCount,
                RealtimeMetricKey.FAILED_COUNT to failedCount,
                RealtimeMetricKey.RETRY_COUNT to retryCount,
                RealtimeMetricKey.PERMANENT_FAILED_COUNT to permanentFailedCount,
                RealtimeMetricKey.AVERAGE_DECISION_DELAY to averageDecisionDelay,
                RealtimeMetricKey.COMPLETED_COUNT to completedCount,
                RealtimeMetricKey.SUBMITTED_COUNT to submittedCount,
                RealtimeMetricKey.SLA_VIOLATION_COUNT to slaViolationCount,
                RealtimeMetricKey.SLA_VIOLATION_RATE to slaViolationRate,
                RealtimeMetricKey.CAPACITY_REJECTED_COUNT to capacityRejectedCount,
                RealtimeMetricKey.AVERAGE_QUEUE_DEPTH to averageQueueDepth,
                RealtimeMetricKey.MAX_QUEUE_DEPTH to maxQueueDepth,
                RealtimeMetricKey.P95_RESPONSE_TIME to p95ResponseTime,
                RealtimeMetricKey.P99_RESPONSE_TIME to p99ResponseTime,
                RealtimeMetricKey.SCALE_OUT_COUNT to scaleOutCount,
                RealtimeMetricKey.SCALE_IN_COUNT to scaleInCount,
                RealtimeMetricKey.ACTIVE_VM_PEAK to activeVmPeak,
                RealtimeMetricKey.AUTOSCALING_COST to autoscalingCost,
                RealtimeMetricKey.COLD_START_DELAY_TOTAL to coldStartDelayTotal,
                RealtimeMetricKey.RESOURCE_REJECTED_COUNT to resourceRejectedCount,
                RealtimeMetricKey.RUNTIME_FAILURE_COUNT to runtimeFailureCount,
                RealtimeMetricKey.TIMEOUT_CANCELLED_COUNT to timeoutCancelledCount,
                RealtimeMetricKey.MIGRATION_COUNT to migrationCount,
                RealtimeMetricKey.CHECKPOINT_RECOVERY_COUNT to checkpointRecoveryCount,
                RealtimeMetricKey.RETRY_SUCCESS_RATE to retrySuccessRate,
                RealtimeMetricKey.SLA_PENALTY to slaPenalty,
                RealtimeMetricKey.PREEMPTED_COUNT to preemptedCount,
                RealtimeMetricKey.PREEMPTION_SUCCESS_COUNT to preemptionSuccessCount,
                RealtimeMetricKey.PREEMPTION_FAILED_COUNT to preemptionFailedCount,
                RealtimeMetricKey.AVERAGE_PREEMPTION_DELAY to averagePreemptionDelay,
                RealtimeMetricKey.PREEMPTION_PENALTY to preemptionPenalty,
                RealtimeMetricKey.CHECKPOINT_LOSS_TOTAL to checkpointLossTotal,
                RealtimeMetricKey.TENANT_QUOTA_REJECTED_COUNT to tenantQuotaRejectedCount,
                RealtimeMetricKey.TENANT_BUDGET_REJECTED_COUNT to tenantBudgetRejectedCount,
                RealtimeMetricKey.TENANT_FAIRNESS_INDEX to tenantFairnessIndex,
                RealtimeMetricKey.FAIRNESS_VIOLATION_COUNT to fairnessViolationCount,
                RealtimeMetricKey.TENANT_SLA_PENALTY to tenantSlaPenalty,
                RealtimeMetricKey.DOMINANT_RESOURCE_FAIRNESS_INDEX to dominantResourceFairnessIndex,
                RealtimeMetricKey.COST_SLA_TRADEOFF_SCORE to costSlaTradeoffScore,
                RealtimeMetricKey.RETRY_SUCCESS_BY_TENANT to retrySuccessByTenant,
                RealtimeMetricKey.CROSS_RACK_ASSIGNMENT_COUNT to crossRackAssignmentCount,
                RealtimeMetricKey.CROSS_REGION_ASSIGNMENT_COUNT to crossRegionAssignmentCount,
                RealtimeMetricKey.AVERAGE_TOPOLOGY_LATENCY to averageTopologyLatency,
                RealtimeMetricKey.TOPOLOGY_COST to topologyCost,
                RealtimeMetricKey.HOST_FAILURE_COUNT to hostFailureCount,
                RealtimeMetricKey.RACK_FAILURE_COUNT to rackFailureCount,
                RealtimeMetricKey.REGION_FAILURE_COUNT to regionFailureCount,
                RealtimeMetricKey.FAILURE_DOMAIN_SPREAD_SCORE to failureDomainSpreadScore,
            ),
    )

    operator fun get(key: RealtimeMetricKey): StatisticalValue = metric(key)

    fun metric(key: RealtimeMetricKey): StatisticalValue = metrics[key] ?: StatisticalValue(0.0, 0.0, 0.0, 0.0)

    val makespan: StatisticalValue get() = metric(RealtimeMetricKey.MAKESPAN)
    val loadBalance: StatisticalValue get() = metric(RealtimeMetricKey.LOAD_BALANCE)
    val cost: StatisticalValue get() = metric(RealtimeMetricKey.COST)
    val totalTime: StatisticalValue get() = metric(RealtimeMetricKey.TOTAL_TIME)
    val fitness: StatisticalValue get() = metric(RealtimeMetricKey.FITNESS)
    val averageWaitingTime: StatisticalValue get() = metric(RealtimeMetricKey.AVERAGE_WAITING_TIME)
    val averageResponseTime: StatisticalValue get() = metric(RealtimeMetricKey.AVERAGE_RESPONSE_TIME)
    val rejectedCount: StatisticalValue get() = metric(RealtimeMetricKey.REJECTED_COUNT)
    val timeoutCount: StatisticalValue get() = metric(RealtimeMetricKey.TIMEOUT_COUNT)
    val failedCount: StatisticalValue get() = metric(RealtimeMetricKey.FAILED_COUNT)
    val retryCount: StatisticalValue get() = metric(RealtimeMetricKey.RETRY_COUNT)
    val permanentFailedCount: StatisticalValue get() = metric(RealtimeMetricKey.PERMANENT_FAILED_COUNT)
    val averageDecisionDelay: StatisticalValue get() = metric(RealtimeMetricKey.AVERAGE_DECISION_DELAY)
    val completedCount: StatisticalValue get() = metric(RealtimeMetricKey.COMPLETED_COUNT)
    val submittedCount: StatisticalValue get() = metric(RealtimeMetricKey.SUBMITTED_COUNT)
    val slaViolationCount: StatisticalValue get() = metric(RealtimeMetricKey.SLA_VIOLATION_COUNT)
    val slaViolationRate: StatisticalValue get() = metric(RealtimeMetricKey.SLA_VIOLATION_RATE)
    val capacityRejectedCount: StatisticalValue get() = metric(RealtimeMetricKey.CAPACITY_REJECTED_COUNT)
    val averageQueueDepth: StatisticalValue get() = metric(RealtimeMetricKey.AVERAGE_QUEUE_DEPTH)
    val maxQueueDepth: StatisticalValue get() = metric(RealtimeMetricKey.MAX_QUEUE_DEPTH)
    val p95ResponseTime: StatisticalValue get() = metric(RealtimeMetricKey.P95_RESPONSE_TIME)
    val p99ResponseTime: StatisticalValue get() = metric(RealtimeMetricKey.P99_RESPONSE_TIME)
    val scaleOutCount: StatisticalValue get() = metric(RealtimeMetricKey.SCALE_OUT_COUNT)
    val scaleInCount: StatisticalValue get() = metric(RealtimeMetricKey.SCALE_IN_COUNT)
    val activeVmPeak: StatisticalValue get() = metric(RealtimeMetricKey.ACTIVE_VM_PEAK)
    val autoscalingCost: StatisticalValue get() = metric(RealtimeMetricKey.AUTOSCALING_COST)
    val coldStartDelayTotal: StatisticalValue get() = metric(RealtimeMetricKey.COLD_START_DELAY_TOTAL)
    val resourceRejectedCount: StatisticalValue get() = metric(RealtimeMetricKey.RESOURCE_REJECTED_COUNT)
    val runtimeFailureCount: StatisticalValue get() = metric(RealtimeMetricKey.RUNTIME_FAILURE_COUNT)
    val timeoutCancelledCount: StatisticalValue get() = metric(RealtimeMetricKey.TIMEOUT_CANCELLED_COUNT)
    val migrationCount: StatisticalValue get() = metric(RealtimeMetricKey.MIGRATION_COUNT)
    val checkpointRecoveryCount: StatisticalValue get() = metric(RealtimeMetricKey.CHECKPOINT_RECOVERY_COUNT)
    val retrySuccessRate: StatisticalValue get() = metric(RealtimeMetricKey.RETRY_SUCCESS_RATE)
    val slaPenalty: StatisticalValue get() = metric(RealtimeMetricKey.SLA_PENALTY)
    val preemptedCount: StatisticalValue get() = metric(RealtimeMetricKey.PREEMPTED_COUNT)
    val preemptionSuccessCount: StatisticalValue get() = metric(RealtimeMetricKey.PREEMPTION_SUCCESS_COUNT)
    val preemptionFailedCount: StatisticalValue get() = metric(RealtimeMetricKey.PREEMPTION_FAILED_COUNT)
    val averagePreemptionDelay: StatisticalValue get() = metric(RealtimeMetricKey.AVERAGE_PREEMPTION_DELAY)
    val preemptionPenalty: StatisticalValue get() = metric(RealtimeMetricKey.PREEMPTION_PENALTY)
    val checkpointLossTotal: StatisticalValue get() = metric(RealtimeMetricKey.CHECKPOINT_LOSS_TOTAL)
    val tenantQuotaRejectedCount: StatisticalValue get() = metric(RealtimeMetricKey.TENANT_QUOTA_REJECTED_COUNT)
    val tenantBudgetRejectedCount: StatisticalValue get() = metric(RealtimeMetricKey.TENANT_BUDGET_REJECTED_COUNT)
    val tenantFairnessIndex: StatisticalValue get() = metric(RealtimeMetricKey.TENANT_FAIRNESS_INDEX)
    val fairnessViolationCount: StatisticalValue get() = metric(RealtimeMetricKey.FAIRNESS_VIOLATION_COUNT)
    val tenantSlaPenalty: StatisticalValue get() = metric(RealtimeMetricKey.TENANT_SLA_PENALTY)
    val dominantResourceFairnessIndex: StatisticalValue
        get() = metric(RealtimeMetricKey.DOMINANT_RESOURCE_FAIRNESS_INDEX)
    val costSlaTradeoffScore: StatisticalValue get() = metric(RealtimeMetricKey.COST_SLA_TRADEOFF_SCORE)
    val retrySuccessByTenant: StatisticalValue get() = metric(RealtimeMetricKey.RETRY_SUCCESS_BY_TENANT)
    val crossRackAssignmentCount: StatisticalValue get() = metric(RealtimeMetricKey.CROSS_RACK_ASSIGNMENT_COUNT)
    val crossRegionAssignmentCount: StatisticalValue get() = metric(RealtimeMetricKey.CROSS_REGION_ASSIGNMENT_COUNT)
    val averageTopologyLatency: StatisticalValue get() = metric(RealtimeMetricKey.AVERAGE_TOPOLOGY_LATENCY)
    val topologyCost: StatisticalValue get() = metric(RealtimeMetricKey.TOPOLOGY_COST)
    val hostFailureCount: StatisticalValue get() = metric(RealtimeMetricKey.HOST_FAILURE_COUNT)
    val rackFailureCount: StatisticalValue get() = metric(RealtimeMetricKey.RACK_FAILURE_COUNT)
    val regionFailureCount: StatisticalValue get() = metric(RealtimeMetricKey.REGION_FAILURE_COUNT)
    val failureDomainSpreadScore: StatisticalValue get() = metric(RealtimeMetricKey.FAILURE_DOMAIN_SPREAD_SCORE)
}

private fun RealtimeMetricValues.intValue(key: RealtimeMetricKey): Int = valueForKey(key).toInt()

private fun RealtimeMetricValues.longValue(key: RealtimeMetricKey): Long = valueForKey(key).toLong()
