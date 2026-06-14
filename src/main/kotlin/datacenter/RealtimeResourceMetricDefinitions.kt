package datacenter

internal object RealtimeResourceMetricDefinitions {
    val metrics: List<RealtimeMetricDefinition> =
        listOf(
            realtimeMetric(
                RealtimeMetricKey.SCALE_OUT_COUNT,
                "ScaleOutCount",
                "count",
                RealtimeMetricDirection.NEUTRAL,
                "自动扩容次数。",
                RealtimeMetricValueKind.INT,
            ),
            realtimeMetric(
                RealtimeMetricKey.SCALE_IN_COUNT,
                "ScaleInCount",
                "count",
                RealtimeMetricDirection.NEUTRAL,
                "自动缩容次数。",
                RealtimeMetricValueKind.INT,
            ),
            realtimeMetric(
                RealtimeMetricKey.ACTIVE_VM_PEAK,
                "ActiveVmPeak",
                "count",
                RealtimeMetricDirection.NEUTRAL,
                "运行过程中活跃 VM 峰值。",
                RealtimeMetricValueKind.INT,
            ),
            realtimeMetric(
                RealtimeMetricKey.AUTOSCALING_COST,
                "AutoscalingCost",
                "cost",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "自动伸缩带来的额外成本。",
            ),
            realtimeMetric(
                RealtimeMetricKey.COLD_START_DELAY_TOTAL,
                "ColdStartDelayTotal",
                "seconds",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "VM 冷启动延迟总量。",
            ),
            realtimeMetric(
                RealtimeMetricKey.RESOURCE_REJECTED_COUNT,
                "ResourceRejectedCount",
                "count",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "RAM、带宽或 I/O 资源约束导致的拒绝数。",
                RealtimeMetricValueKind.INT,
            ),
        )
}
