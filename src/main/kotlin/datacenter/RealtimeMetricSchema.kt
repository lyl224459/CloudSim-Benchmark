package datacenter

object RealtimeMetricSchema {
    val summaryMetadataHeaders =
        listOf(
            "Algorithm",
            "Status",
            "ErrorType",
            "ErrorMessage",
            "Runs",
            "SuccessfulRuns",
            "FailedRuns",
        )
    val trialMetadataHeaders = listOf("Trial", "Status", "ErrorType", "ErrorMessage")

    val metrics: List<RealtimeMetricDefinition> =
        listOf(
            metric(
                RealtimeMetricKey.MAKESPAN,
                "Makespan",
                "seconds",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "所有完成任务中的最大完成时间。",
            ),
            metric(
                RealtimeMetricKey.LOAD_BALANCE,
                "LoadBalance",
                "ratio",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "VM 负载不均衡程度。",
            ),
            metric(
                RealtimeMetricKey.COST,
                "Cost",
                "cost",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "根据 VM 价格估算的总执行成本。",
            ),
            metric(
                RealtimeMetricKey.TOTAL_TIME,
                "TotalTime",
                "seconds",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "按调度结果估算的总执行时间。",
            ),
            metric(
                RealtimeMetricKey.FITNESS,
                "Fitness",
                "score",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "多目标权重计算出的调度适应度。",
            ),
            metric(
                RealtimeMetricKey.AVERAGE_WAITING_TIME,
                "AvgWaitingTime",
                "seconds",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "任务从到达到开始执行的平均等待时间。",
            ),
            metric(
                RealtimeMetricKey.AVERAGE_RESPONSE_TIME,
                "AvgResponseTime",
                "seconds",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "任务从到达到完成的平均响应时间。",
            ),
            metric(
                RealtimeMetricKey.REJECTED_COUNT,
                "RejectedCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "被实时准入或资源策略拒绝的任务数。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.TIMEOUT_COUNT,
                "TimeoutCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "达到 SLA 超时时间的任务数。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.FAILED_COUNT,
                "FailedCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "最终进入失败状态的任务数。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.RETRY_COUNT,
                "RetryCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "Broker 发起的重试次数。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.PERMANENT_FAILED_COUNT,
                "PermanentFailedCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "重试耗尽或不可恢复失败的任务数。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.AVERAGE_DECISION_DELAY,
                "AvgDecisionDelay",
                "seconds",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "调度决策延迟的平均值。",
            ),
            metric(
                RealtimeMetricKey.COMPLETED_COUNT,
                "CompletedCount",
                "count",
                RealtimeMetricDirection.HIGHER_IS_BETTER,
                "成功完成的任务数。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.SUBMITTED_COUNT,
                "SubmittedCount",
                "count",
                RealtimeMetricDirection.HIGHER_IS_BETTER,
                "已提交给 CloudSim 的任务数。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.SLA_VIOLATION_COUNT,
                "SlaViolationCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "完成但超过 deadline 的任务数。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.SLA_VIOLATION_RATE,
                "SlaViolationRate",
                "ratio",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "SLA 违约数除以成功完成任务数。",
            ),
            metric(
                RealtimeMetricKey.CAPACITY_REJECTED_COUNT,
                "CapacityRejectedCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "VM 队列容量导致的拒绝数。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.AVERAGE_QUEUE_DEPTH,
                "AvgQueueDepth",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "被选中 VM 的队列深度采样平均值。",
            ),
            metric(
                RealtimeMetricKey.MAX_QUEUE_DEPTH,
                "MaxQueueDepth",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "队列深度采样最大值。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.P95_RESPONSE_TIME,
                "P95ResponseTime",
                "seconds",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "响应时间第 95 百分位。",
            ),
            metric(
                RealtimeMetricKey.P99_RESPONSE_TIME,
                "P99ResponseTime",
                "seconds",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "响应时间第 99 百分位。",
            ),
            metric(
                RealtimeMetricKey.SCALE_OUT_COUNT,
                "ScaleOutCount",
                "count",
                RealtimeMetricDirection.NEUTRAL,
                "自动扩容次数。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.SCALE_IN_COUNT,
                "ScaleInCount",
                "count",
                RealtimeMetricDirection.NEUTRAL,
                "自动缩容次数。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.ACTIVE_VM_PEAK,
                "ActiveVmPeak",
                "count",
                RealtimeMetricDirection.NEUTRAL,
                "运行过程中活跃 VM 峰值。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.AUTOSCALING_COST,
                "AutoscalingCost",
                "cost",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "自动伸缩带来的额外成本。",
            ),
            metric(
                RealtimeMetricKey.COLD_START_DELAY_TOTAL,
                "ColdStartDelayTotal",
                "seconds",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "VM 冷启动延迟总量。",
            ),
            metric(
                RealtimeMetricKey.RESOURCE_REJECTED_COUNT,
                "ResourceRejectedCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "RAM、带宽或 I/O 资源约束导致的拒绝数。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.RUNTIME_FAILURE_COUNT,
                "RuntimeFailureCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "运行期失败事件数。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.TIMEOUT_CANCELLED_COUNT,
                "TimeoutCancelledCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "超时处理中被取消的任务数。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.MIGRATION_COUNT,
                "MigrationCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "任务迁移事件数。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.CHECKPOINT_RECOVERY_COUNT,
                "CheckpointRecoveryCount",
                "count",
                RealtimeMetricDirection.HIGHER_IS_BETTER,
                "重试或迁移中成功复用 checkpoint 的次数。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.RETRY_SUCCESS_RATE,
                "RetrySuccessRate",
                "ratio",
                RealtimeMetricDirection.HIGHER_IS_BETTER,
                "重试成功数除以重试总数。",
            ),
            metric(
                RealtimeMetricKey.SLA_PENALTY,
                "SlaPenalty",
                "score",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "SLA 延迟按权重累计的惩罚值。",
            ),
            metric(
                RealtimeMetricKey.PREEMPTED_COUNT,
                "PreemptedCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "被高优先级任务抢占的任务数。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.PREEMPTION_SUCCESS_COUNT,
                "PreemptionSuccessCount",
                "count",
                RealtimeMetricDirection.HIGHER_IS_BETTER,
                "成功执行的抢占决策数。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.PREEMPTION_FAILED_COUNT,
                "PreemptionFailedCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "未能执行的抢占决策数。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.AVERAGE_PREEMPTION_DELAY,
                "AvgPreemptionDelay",
                "seconds",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "抢占引入的平均延迟。",
            ),
            metric(
                RealtimeMetricKey.PREEMPTION_PENALTY,
                "PreemptionPenalty",
                "score",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "抢占策略累计的惩罚值。",
            ),
            metric(
                RealtimeMetricKey.CHECKPOINT_LOSS_TOTAL,
                "CheckpointLossTotal",
                "MI",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "重试或迁移中未恢复的工作量。",
                RealtimeMetricValueKind.LONG,
            ),
            metric(
                RealtimeMetricKey.TENANT_QUOTA_REJECTED_COUNT,
                "TenantQuotaRejectedCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "租户配额导致的拒绝数。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.TENANT_BUDGET_REJECTED_COUNT,
                "TenantBudgetRejectedCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "租户成本预算导致的拒绝数。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.TENANT_FAIRNESS_INDEX,
                "TenantFairnessIndex",
                "ratio",
                RealtimeMetricDirection.HIGHER_IS_BETTER,
                "多租户公平性得分。",
            ),
            metric(
                RealtimeMetricKey.FAIRNESS_VIOLATION_COUNT,
                "FairnessViolationCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "租户公平策略违约次数。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.TENANT_SLA_PENALTY,
                "TenantSlaPenalty",
                "score",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "按租户策略加权后的 SLA 惩罚。",
            ),
            metric(
                RealtimeMetricKey.DOMINANT_RESOURCE_FAIRNESS_INDEX,
                "DominantResourceFairnessIndex",
                "ratio",
                RealtimeMetricDirection.HIGHER_IS_BETTER,
                "基于主导资源份额的公平性得分。",
            ),
            metric(
                RealtimeMetricKey.COST_SLA_TRADEOFF_SCORE,
                "CostSlaTradeoffScore",
                "score",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "成本与 SLA 惩罚组合后的折中得分。",
            ),
            metric(
                RealtimeMetricKey.RETRY_SUCCESS_BY_TENANT,
                "RetrySuccessByTenant",
                "ratio",
                RealtimeMetricDirection.HIGHER_IS_BETTER,
                "按租户视角聚合的重试成功表现。",
            ),
            metric(
                RealtimeMetricKey.CROSS_RACK_ASSIGNMENT_COUNT,
                "CrossRackAssignmentCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "分配到非本地 rack 的次数。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.CROSS_REGION_ASSIGNMENT_COUNT,
                "CrossRegionAssignmentCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "分配到非本地 region 的次数。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.AVERAGE_TOPOLOGY_LATENCY,
                "AverageTopologyLatency",
                "seconds",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "拓扑放置带来的平均网络延迟。",
            ),
            metric(
                RealtimeMetricKey.TOPOLOGY_COST,
                "TopologyCost",
                "cost",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "跨 rack 或跨 region 放置带来的拓扑成本。",
            ),
            metric(
                RealtimeMetricKey.HOST_FAILURE_COUNT,
                "HostFailureCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "Host 故障域事件数。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.RACK_FAILURE_COUNT,
                "RackFailureCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "Rack 故障域事件数。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.REGION_FAILURE_COUNT,
                "RegionFailureCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "Region 故障域事件数。",
                RealtimeMetricValueKind.INT,
            ),
            metric(
                RealtimeMetricKey.FAILURE_DOMAIN_SPREAD_SCORE,
                "FailureDomainSpreadScore",
                "ratio",
                RealtimeMetricDirection.HIGHER_IS_BETTER,
                "工作负载跨故障域分散程度。",
            ),
        )

    val metricHeaders: List<String> = metrics.map { it.csvName }
    val summaryHeaders: List<String> = summaryHeaders()
    val trialHeaders: List<String> = trialMetadataHeaders + metricHeaders
    val cloudletCountSummaryHeaders: List<String> = summaryHeaders(prefixHeaders = listOf("CloudletCount"))

    fun summaryHeaders(prefixHeaders: List<String> = emptyList()): List<String> =
        prefixHeaders + summaryMetadataHeaders + metrics.map { it.meanHeader } + metrics.map { it.stdDevHeader }

    fun trialMetricValues(result: RealtimeAlgorithmResult): List<Any> = metrics.map { it.trialValue(result) }

    fun meanMetricValues(statistics: RealtimeAlgorithmStatistics) = metrics.map { it.meanValue(statistics) }

    fun stdDevMetricValues(statistics: RealtimeAlgorithmStatistics) = metrics.map { it.stdDevValue(statistics) }

    fun blankMetricValues(): List<String> = metrics.map { "" }

    fun trialMetricMap(result: RealtimeAlgorithmResult): Map<String, Any> =
        metrics.associateTo(linkedMapOf()) { it.csvName to it.trialValue(result) }

    fun meanMetricMap(statistics: RealtimeAlgorithmStatistics): Map<String, Any> =
        metrics.associateTo(linkedMapOf()) { it.csvName to it.meanValue(statistics) }

    fun stdDevMetricMap(statistics: RealtimeAlgorithmStatistics): Map<String, Any> =
        metrics.associateTo(linkedMapOf()) { it.csvName to it.stdDevValue(statistics) }

    fun definitionFor(key: RealtimeMetricKey): RealtimeMetricDefinition = metricsByKey.getValue(key)

    @Suppress("LongParameterList")
    private fun metric(
        key: RealtimeMetricKey,
        csvName: String,
        unit: String,
        direction: RealtimeMetricDirection,
        description: String,
        kind: RealtimeMetricValueKind = RealtimeMetricValueKind.DOUBLE,
    ): RealtimeMetricDefinition = RealtimeMetricDefinition(key, csvName, unit, direction, description, kind)

    private val metricsByKey: Map<RealtimeMetricKey, RealtimeMetricDefinition> =
        metrics.associateBy { it.key }
}
