package datacenter

internal object RealtimeTopologyMetricDefinitions {
    val metrics: List<RealtimeMetricDefinition> =
        listOf(
            realtimeMetric(
                RealtimeMetricKey.CROSS_RACK_ASSIGNMENT_COUNT,
                "CrossRackAssignmentCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "分配到非本地 rack 的次数。",
                RealtimeMetricValueKind.INT,
            ),
            realtimeMetric(
                RealtimeMetricKey.CROSS_REGION_ASSIGNMENT_COUNT,
                "CrossRegionAssignmentCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "分配到非本地 region 的次数。",
                RealtimeMetricValueKind.INT,
            ),
            realtimeMetric(
                RealtimeMetricKey.AVERAGE_TOPOLOGY_LATENCY,
                "AverageTopologyLatency",
                "seconds",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "拓扑放置带来的平均网络延迟。",
            ),
            realtimeMetric(
                RealtimeMetricKey.TOPOLOGY_COST,
                "TopologyCost",
                "cost",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "跨 rack 或跨 region 放置带来的拓扑成本。",
            ),
            realtimeMetric(
                RealtimeMetricKey.HOST_FAILURE_COUNT,
                "HostFailureCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "Host 故障域事件数。",
                RealtimeMetricValueKind.INT,
            ),
            realtimeMetric(
                RealtimeMetricKey.RACK_FAILURE_COUNT,
                "RackFailureCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "Rack 故障域事件数。",
                RealtimeMetricValueKind.INT,
            ),
            realtimeMetric(
                RealtimeMetricKey.REGION_FAILURE_COUNT,
                "RegionFailureCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "Region 故障域事件数。",
                RealtimeMetricValueKind.INT,
            ),
            realtimeMetric(
                RealtimeMetricKey.FAILURE_DOMAIN_SPREAD_SCORE,
                "FailureDomainSpreadScore",
                "ratio",
                RealtimeMetricDirection.HIGHER_IS_BETTER,
                "工作负载跨故障域分散程度。",
            ),
        )
}
