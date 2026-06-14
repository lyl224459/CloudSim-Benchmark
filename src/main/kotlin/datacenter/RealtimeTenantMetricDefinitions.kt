package datacenter

internal object RealtimeTenantMetricDefinitions {
    val metrics: List<RealtimeMetricDefinition> =
        listOf(
            realtimeMetric(
                RealtimeMetricKey.TENANT_QUOTA_REJECTED_COUNT,
                "TenantQuotaRejectedCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "租户配额导致的拒绝数。",
                RealtimeMetricValueKind.INT,
            ),
            realtimeMetric(
                RealtimeMetricKey.TENANT_BUDGET_REJECTED_COUNT,
                "TenantBudgetRejectedCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "租户成本预算导致的拒绝数。",
                RealtimeMetricValueKind.INT,
            ),
            realtimeMetric(
                RealtimeMetricKey.TENANT_FAIRNESS_INDEX,
                "TenantFairnessIndex",
                "ratio",
                RealtimeMetricDirection.HIGHER_IS_BETTER,
                "多租户公平性得分。",
            ),
            realtimeMetric(
                RealtimeMetricKey.FAIRNESS_VIOLATION_COUNT,
                "FairnessViolationCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "租户公平策略违约次数。",
                RealtimeMetricValueKind.INT,
            ),
            realtimeMetric(
                RealtimeMetricKey.TENANT_SLA_PENALTY,
                "TenantSlaPenalty",
                "score",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "按租户策略加权后的 SLA 惩罚。",
            ),
            realtimeMetric(
                RealtimeMetricKey.DOMINANT_RESOURCE_FAIRNESS_INDEX,
                "DominantResourceFairnessIndex",
                "ratio",
                RealtimeMetricDirection.HIGHER_IS_BETTER,
                "基于主导资源份额的公平性得分。",
            ),
            realtimeMetric(
                RealtimeMetricKey.COST_SLA_TRADEOFF_SCORE,
                "CostSlaTradeoffScore",
                "score",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "成本与 SLA 惩罚组合后的折中得分。",
            ),
            realtimeMetric(
                RealtimeMetricKey.RETRY_SUCCESS_BY_TENANT,
                "RetrySuccessByTenant",
                "ratio",
                RealtimeMetricDirection.HIGHER_IS_BETTER,
                "按租户视角聚合的重试成功表现。",
            ),
        )
}
