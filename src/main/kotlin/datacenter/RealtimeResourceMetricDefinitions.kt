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
            realtimeMetric(
                RealtimeMetricKey.AVERAGE_PHYSICAL_HOST_UTILIZATION,
                "AvgPhysicalHostUtilization",
                "ratio",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "被选中 placement 的物理 host 资源利用率平均值。",
            ),
            realtimeMetric(
                RealtimeMetricKey.AVERAGE_HOST_RESOURCE_FRAGMENTATION,
                "AvgHostResourceFragmentation",
                "ratio",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "被选中 placement 的物理 host 资源碎片度平均值。",
            ),
            realtimeMetric(
                RealtimeMetricKey.AVERAGE_NETWORK_TRANSFER_DELAY,
                "AvgNetworkTransferDelay",
                "seconds",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "被选中 placement 的数据传输与拓扑网络延迟平均值。",
            ),
            realtimeMetric(
                RealtimeMetricKey.IMAGE_CACHE_HIT_RATE,
                "ImageCacheHitRate",
                "ratio",
                RealtimeMetricDirection.HIGHER_IS_BETTER,
                "启用 image cache 后被选中 placement 的镜像缓存命中率。",
            ),
            realtimeMetric(
                RealtimeMetricKey.AVERAGE_NOISY_NEIGHBOR_PRESSURE,
                "AvgNoisyNeighborPressure",
                "score",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "被选中 placement 的 noisy-neighbor 压力平均值。",
            ),
        )
}
