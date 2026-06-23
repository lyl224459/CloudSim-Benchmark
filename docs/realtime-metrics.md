# Realtime 指标定义

实时调度 CSV 的指标列由 `RealtimeMetricSchema` 统一维护。`*_Mean` 表示成功 run 的平均值，`*_StdDev` 表示成功 run 的标准差；失败 run 的指标单元格为空，并通过 `Status`、`ErrorType`、`ErrorMessage` 记录失败信息。

| CSV 指标 | 单位 | 趋势 | 定义 |
| :--- | :--- | :--- | :--- |
| Makespan | seconds | 越低越好 | 所有完成任务中的最大完成时间。 |
| LoadBalance | ratio | 越低越好 | VM 负载不均衡程度。 |
| Cost | cost | 越低越好 | 根据 VM 价格估算的总执行成本。 |
| TotalTime | seconds | 越低越好 | 按调度结果估算的总执行时间。 |
| Fitness | score | 越低越好 | 多目标权重计算出的调度适应度。 |
| AvgRealtimeScore | score | 越低越好 | 被选中候选 VM 的 realtime 专用评分平均值。 |
| AvgSelectedLatenessPenalty | score | 越低越好 | 被选中候选 VM 的 deadline lateness penalty 平均值。 |
| AvgSelectedDeadlineSlack | seconds | 越高越好 | 被选中候选 VM 的 deadline slack 平均值；无 deadline 时为 0。 |
| AvgCandidateScoreSpread | score | 越低越好 | 每次调度 accepted candidate 最高与最低 realtime score 差值的平均值。 |
| AvgWaitingTime | seconds | 越低越好 | 任务从到达到开始执行的平均等待时间。 |
| AvgResponseTime | seconds | 越低越好 | 任务从到达到完成的平均响应时间。 |
| RejectedCount | count | 越低越好 | 被实时准入或资源策略拒绝的任务数。 |
| TimeoutCount | count | 越低越好 | 达到 SLA 超时时间的任务数。 |
| FailedCount | count | 越低越好 | 最终进入失败状态的任务数。 |
| RetryCount | count | 越低越好 | Broker 发起的重试次数。 |
| PermanentFailedCount | count | 越低越好 | 重试耗尽或不可恢复失败的任务数。 |
| AvgDecisionDelay | seconds | 越低越好 | 调度决策延迟的平均值。 |
| CompletedCount | count | 越高越好 | 成功完成的任务数。 |
| SubmittedCount | count | 越高越好 | 已提交给 CloudSim 的任务数。 |
| SlaViolationCount | count | 越低越好 | 完成但超过 deadline 的任务数。 |
| SlaViolationRate | ratio | 越低越好 | SLA 违约数除以成功完成任务数。 |
| CapacityRejectedCount | count | 越低越好 | VM 队列容量导致的拒绝数。 |
| DeadlineRejectedCount | count | 越低越好 | deadline admission 判定无法按期完成并拒绝的任务数。 |
| DeadlineDegradedCount | count | 越低越好 | deadline miss 后按降级策略继续提交的任务数。 |
| DeadlineRetryLaterCount | count | 越低越好 | deadline miss 后被重新排队等待再次 admission 的次数。 |
| DeadlineMissAcceptedCount | count | 越低越好 | deadline miss 但按 accept 策略继续提交的任务数。 |
| DependencyBlockedCount | count | 越低越好 | DAG 依赖未满足而暂缓进入调度链路的任务数。 |
| DependencyReleasedCount | count | 越高越好 | 前驱全部成功后被释放回实时调度链路的任务数。 |
| DependencyRejectedCount | count | 越低越好 | 因前驱失败、拒绝、取消或超时而被级联拒绝的任务数。 |
| RescheduleAttemptCount | count | 越低越好 | 周期性重调度检查中尝试重新安排任务的次数。 |
| RescheduleSuccessCount | count | 越高越好 | 周期性重调度成功迁移或重排到新 VM 的次数。 |
| RescheduleFailureCount | count | 越低越好 | 周期性重调度尝试后未找到更优 VM 或未执行迁移的次数。 |
| AvgRescheduleDelay | seconds | 越低越好 | 成功重调度产生的平均决策/迁移延迟。 |
| AvgQueueDepth | count | 越低越好 | 被选中 VM 的队列深度采样平均值。 |
| MaxQueueDepth | count | 越低越好 | 队列深度采样最大值。 |
| P95ResponseTime | seconds | 越低越好 | 响应时间第 95 百分位。 |
| P99ResponseTime | seconds | 越低越好 | 响应时间第 99 百分位。 |
| ScaleOutCount | count | 中性 | 自动扩容次数。 |
| ScaleInCount | count | 中性 | 自动缩容次数。 |
| ActiveVmPeak | count | 中性 | 运行过程中活跃 VM 峰值。 |
| AutoscalingCost | cost | 越低越好 | 自动伸缩带来的额外成本。 |
| ColdStartDelayTotal | seconds | 越低越好 | VM 冷启动延迟总量。 |
| AvgAutoscalingPressure | score | 越低越好 | 高级 autoscaling 评估中的扩容总压力平均值。 |
| AvgDeadlineSlackPressure | score | 越低越好 | 由 deadline slack 不足产生的扩容压力平均值。 |
| AvgArrivalRatePressure | score | 越低越好 | 由近期到达率预测产生的扩容压力平均值。 |
| ScaleCooldownSkippedCount | count | 中性 | 扩容压力达到阈值但被 cooldown 跳过的次数。 |
| WarmPoolHitRate | ratio | 越高越好 | warm pool 评估时可用空闲动态 VM 满足目标的比例。 |
| ScaleInDrainCount | count | 中性 | 启用 drain 后动态 VM 进入 DRAINING 的次数。 |
| AutoscalingVmSeconds | vm-seconds | 越低越好 | 动态 VM 存活时间按 VM 数累加后的总秒数。 |
| ResourceRejectedCount | count | 越低越好 | RAM、带宽或 I/O 资源约束导致的拒绝数。 |
| AvgPhysicalHostUtilization | ratio | 越低越好 | 被选中 placement 的物理 host 资源利用率平均值。 |
| AvgHostResourceFragmentation | ratio | 越低越好 | 被选中 placement 的物理 host 资源碎片度平均值。 |
| AvgNetworkTransferDelay | seconds | 越低越好 | 被选中 placement 的数据传输与拓扑网络延迟平均值。 |
| ImageCacheHitRate | ratio | 越高越好 | 启用 image cache 后被选中 placement 的镜像缓存命中率。 |
| AvgNoisyNeighborPressure | score | 越低越好 | 被选中 placement 的 noisy-neighbor 压力平均值。 |
| RuntimeFailureCount | count | 越低越好 | 运行期失败事件数。 |
| TimeoutCancelledCount | count | 越低越好 | 超时处理中被取消的任务数。 |
| MigrationCount | count | 越低越好 | 任务迁移事件数。 |
| CheckpointRecoveryCount | count | 越高越好 | 重试或迁移中成功复用 checkpoint 的次数。 |
| RetrySuccessRate | ratio | 越高越好 | 重试成功数除以重试总数。 |
| SlaPenalty | score | 越低越好 | SLA 延迟按权重累计的惩罚值。 |
| PreemptedCount | count | 越低越好 | 被高优先级任务抢占的任务数。 |
| PreemptionSuccessCount | count | 越高越好 | 成功执行的抢占决策数。 |
| PreemptionFailedCount | count | 越低越好 | 未能执行的抢占决策数。 |
| AvgPreemptionDelay | seconds | 越低越好 | 抢占引入的平均延迟。 |
| PreemptionPenalty | score | 越低越好 | 抢占策略累计的惩罚值。 |
| CheckpointLossTotal | MI | 越低越好 | 重试或迁移中未恢复的工作量。 |
| TenantQuotaRejectedCount | count | 越低越好 | 租户配额导致的拒绝数。 |
| TenantBudgetRejectedCount | count | 越低越好 | 租户成本预算导致的拒绝数。 |
| TenantFairnessIndex | ratio | 越高越好 | 多租户公平性得分。 |
| FairnessViolationCount | count | 越低越好 | 租户公平策略违约次数。 |
| TenantSlaPenalty | score | 越低越好 | 按租户策略加权后的 SLA 惩罚。 |
| DominantResourceFairnessIndex | ratio | 越高越好 | 基于主导资源份额的公平性得分。 |
| CostSlaTradeoffScore | score | 越低越好 | 成本与 SLA 惩罚组合后的折中得分。 |
| RetrySuccessByTenant | ratio | 越高越好 | 按租户视角聚合的重试成功表现。 |
| CrossRackAssignmentCount | count | 越低越好 | 分配到非本地 rack 的次数。 |
| CrossRegionAssignmentCount | count | 越低越好 | 分配到非本地 region 的次数。 |
| AverageTopologyLatency | seconds | 越低越好 | 拓扑放置带来的平均网络延迟。 |
| TopologyCost | cost | 越低越好 | 跨 rack 或跨 region 放置带来的拓扑成本。 |
| HostFailureCount | count | 越低越好 | Host 故障域事件数。 |
| RackFailureCount | count | 越低越好 | Rack 故障域事件数。 |
| RegionFailureCount | count | 越低越好 | Region 故障域事件数。 |
| FailureDomainSpreadScore | ratio | 越高越好 | 工作负载跨故障域分散程度。 |
