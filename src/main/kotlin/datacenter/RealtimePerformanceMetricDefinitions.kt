package datacenter

internal object RealtimePerformanceMetricDefinitions {
    val metrics: List<RealtimeMetricDefinition> =
        listOf(
            realtimeMetric(
                RealtimeMetricKey.MAKESPAN,
                "Makespan",
                "seconds",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "所有完成任务中的最大完成时间。",
            ),
            realtimeMetric(
                RealtimeMetricKey.LOAD_BALANCE,
                "LoadBalance",
                "ratio",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "VM 负载不均衡程度。",
            ),
            realtimeMetric(
                RealtimeMetricKey.COST,
                "Cost",
                "cost",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "根据 VM 价格估算的总执行成本。",
            ),
            realtimeMetric(
                RealtimeMetricKey.TOTAL_TIME,
                "TotalTime",
                "seconds",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "按调度结果估算的总执行时间。",
            ),
            realtimeMetric(
                RealtimeMetricKey.FITNESS,
                "Fitness",
                "score",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "多目标权重计算出的调度适应度。",
            ),
            realtimeMetric(
                RealtimeMetricKey.AVERAGE_WAITING_TIME,
                "AvgWaitingTime",
                "seconds",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "任务从到达到开始执行的平均等待时间。",
            ),
            realtimeMetric(
                RealtimeMetricKey.AVERAGE_RESPONSE_TIME,
                "AvgResponseTime",
                "seconds",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "任务从到达到完成的平均响应时间。",
            ),
        )
}
