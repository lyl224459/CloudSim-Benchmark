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
                RealtimeMetricKey.AVERAGE_REALTIME_SCORE,
                "AvgRealtimeScore",
                "score",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "被选中候选 VM 的 realtime 专用评分平均值。",
            ),
            realtimeMetric(
                RealtimeMetricKey.AVERAGE_SELECTED_LATENESS_PENALTY,
                "AvgSelectedLatenessPenalty",
                "score",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "被选中候选 VM 的 deadline lateness penalty 平均值。",
            ),
            realtimeMetric(
                RealtimeMetricKey.AVERAGE_SELECTED_DEADLINE_SLACK,
                "AvgSelectedDeadlineSlack",
                "seconds",
                RealtimeMetricDirection.HIGHER_IS_BETTER,
                "被选中候选 VM 的 deadline slack 平均值；无 deadline 时为 0。",
            ),
            realtimeMetric(
                RealtimeMetricKey.AVERAGE_CANDIDATE_SCORE_SPREAD,
                "AvgCandidateScoreSpread",
                "score",
                RealtimeMetricDirection.LOWER_IS_BETTER,
                "每次调度 accepted candidate 最高与最低 realtime score 差值的平均值。",
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
