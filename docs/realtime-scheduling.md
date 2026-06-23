# Realtime Scheduling Guide

本文档解释实时调度模型：任务如何到达、如何进入 broker、候选 VM 如何过滤、超时和失败如何处理，以及租户、拓扑和资源模型如何影响指标。

## High-Level Flow

```text
RealtimeCloudletGenerator
  -> CloudSim arrival event
  -> RealtimeBroker.processEvent
  -> dependency gate
  -> admission / metadata / candidate snapshot
  -> RealtimeScheduler.scheduleOnArrival
  -> accepted VM index or rejection
  -> optional reschedule tick for pending / waiting / running tasks
  -> runtime event planning
  -> metrics and read model
```

`RealtimeBroker` 是 CloudSim broker facade；内部服务负责准入、VM 选择、提交、运行时事件、租户/拓扑 accounting 和 read views。

## Arrival Model

核心字段：

| Field | Meaning |
| :--- | :--- |
| `cloudletCount` | 总任务数。 |
| `simulationDuration` | 实时仿真持续时间。 |
| `arrivalRate` | 平均到达率。 |
| `arrival.distribution` | 到达分布：`poisson`、`uniform`、`burst`、`periodic`、`sporadic`、`diurnal_burst`。 |
| `arrival.burstIntensity` | burst 到达强度。 |
| `arrival.burstDuration` | burst 持续时间。 |
| `arrival.workloadPattern` | 负载模式：`standard`、`mixed_short_long`、`dag_chain`、`dag_layered`。 |

到达模型决定任务何时进入 broker。默认情况下调度器只在任务到达时做选择，不会像 batch 一样一次性看到全部未来任务；启用周期性重调度后，broker 会在后续 tick 中重新评估未终止任务。

`periodic` 使用 `periodSeconds` 和可选 `arrivalJitter` 生成固定周期任务；`sporadic` 在 `sporadicMinInterArrival` 与 `sporadicMaxInterArrival` 之间采样；`diurnal_burst` 用仿真时长作为一个周期，并通过 `diurnalPeakMultiplier`、`diurnalOffPeakMultiplier` 调制到达率。

`mixed_short_long` 会按 `shortTaskRatio` 调整 cloudlet length，并把 `expectedDuration`、`workloadClass` 写入 realtime metadata。`dag_chain` 和 `dag_layered` 会写入 `workflowId`、`stageIndex`、`dependencyIds`，供 broker 的依赖控制器使用。

## Queue Policy

字段：`queuePolicy`

可选值：

| Value | Behavior |
| :--- | :--- |
| `fifo` | 优先可用时间更早、负载更低、队列更短的 VM。 |
| `priority` | 优先剩余队列槽更多，再考虑可用时间和负载。 |
| `deadline` | 使用 projected finish time 排序，更关注 deadline 相关行为。 |

`priorityLevels` 和 `highPriorityRatio` 控制任务优先级分布。`deadlineFactor` 控制 deadline 相对任务估计运行时间的松紧程度。

## Deadline Admission

deadline admission 在 scheduler 选择 VM 前使用 realtime score 中的 projected finish time 计算 slack。默认 `deadlineAdmissionEnabled = true`，但默认 `deadlineType = "soft"`、`deadlineMissAction = "accept"`，因此旧配置仍会继续提交 miss 风险任务并保留 SLA penalty 统计。

字段：

- `deadlineAdmissionEnabled`
- `deadlineType`
- `deadlineMissAction`
- `retryLimit`
- `retryDelay`
- `retryBackoffMultiplier`

`deadlineType` 可选值：

| Value | Behavior |
| :--- | :--- |
| `soft` | 不过滤 late candidates；如果所有候选都会 miss，则按 `deadlineMissAction` 处理。 |
| `firm` | 如果存在可按期完成候选，只允许这些候选进入 scheduler；全部 miss 时按 `deadlineMissAction` 处理。 |
| `hard` | 当前实现与 firm 一样执行候选过滤和 all-miss action，用于表达更严格的实验语义。 |

`deadlineMissAction` 可选值：

| Value | Behavior |
| :--- | :--- |
| `accept` | 继续提交最优候选，计入 `DeadlineMissAcceptedCount`，后续 SLA violation 正常统计。 |
| `reject` | 拒绝任务，原因计为 `DEADLINE`，指标计入 `DeadlineRejectedCount`。 |
| `degrade` | 继续提交最优候选，计入 `DeadlineDegradedCount`，不放宽 deadline。 |
| `retry_later` | 使用 `retryDelay`、`retryBackoffMultiplier`、`retryLimit` 重新排队；耗尽后按 deadline 拒绝。 |

## Periodic Rescheduling

周期性重调度默认关闭。启用后，broker 会按 CloudSim tick 定期刷新 VM lifecycle，并检查 pending、waiting、running 的非终态任务；重调度仍复用现有资源、拓扑、租户、deadline admission 和 realtime score，不绕过候选过滤链路。

字段：

- `reschedulingEnabled`
- `reschedulingInterval`
- `reschedulingPolicy`
- `maxReschedulesPerTask`
- `migrationDelay`
- `checkpointInterval`

`reschedulingPolicy` 可选值：

| Value | Behavior |
| :--- | :--- |
| `deadline_score` | deadline slack 为负且新候选改善 slack，或 realtime total score 更低时重调度。 |
| `score_only` | 仅当新候选 realtime total score 更低时重调度。 |
| `deadline_only` | 仅当任务已 miss deadline 且新候选改善 slack 时重调度。 |

pending 任务会废弃旧 pending reservation 和旧 submit token，再按新的 `decisionDelay + decisionJitter` 安排提交。waiting/running 任务会先中断当前执行片段，按 checkpoint 估算剩余长度；如果 `migrationDelay > 0`，该延迟会叠加到新的 pending submission，并计入 migration 指标。重调度不增加 `RetryCount`，而是使用 `RescheduleAttemptCount`、`RescheduleSuccessCount`、`RescheduleFailureCount` 和 `AvgRescheduleDelay` 单独观测。

## DAG Dependency Enforcement

`dependencyEnforcementEnabled` 默认开启，但只有任务 metadata 中存在 `dependencyIds` 时才生效。没有依赖 metadata 的旧 workload 仍按 arrival-only 流程进入准入和 VM 选择。

DAG 强约束语义：

- 任务到达后，如果任一前驱未成功完成，broker 将任务标记为 `DEPENDENCY_BLOCKED`，不进入租户、容量、deadline admission、VM 选择、retry 或 reschedule。
- 前驱成功完成后，所有依赖已满足的 blocked 后继会以 delay `0.0` 重新进入 arrival workflow；原始 arrival time 不重写，因此响应时间和 deadline 仍包含依赖等待。
- 任一前驱失败、拒绝、取消或超时，后继会按 `DEPENDENCY` 原因拒绝，并级联处理其下游，避免 blocked 任务无限等待。
- `dependencyEnforcementEnabled = false` 时，broker 忽略 dependency metadata，任务按普通实时任务处理。

相关指标：

- `DependencyBlockedCount`
- `DependencyReleasedCount`
- `DependencyRejectedCount`

## Admission And Rejection

broker 可以因为多种原因拒绝任务或候选 VM：

| Reason | Typical Cause |
| :--- | :--- |
| resource | RAM/BW/IO/CPU 需求超过候选容量。 |
| capacity | VM 队列容量满。 |
| deadline | deadline admission 判定所有候选都会 miss，且 miss action 要求拒绝。 |
| dependency | DAG 前驱失败、拒绝、取消或超时，导致后继级联拒绝。 |
| tenant | 租户 quota、budget 或 fairness 约束。 |
| topology | 拓扑策略、故障域或数据本地性不满足。 |

拒绝任务不会让算法 trial 失败，而是计入 realtime metrics，例如 `RejectedCount`、`ResourceRejectedCount`、`DeadlineRejectedCount`、`TenantRejectedCount`。

## Timeout

字段：

- `taskTimeout`
- `timeoutAction`
- `retryLimit`
- `retryDelay`
- `retryBackoffMultiplier`

`timeoutAction` 可选值：

| Value | Meaning |
| :--- | :--- |
| `fail` | 超时后标记失败。 |
| `retry` | 超时后按 retry 策略重新提交。 |
| `cancel` | 超时后取消。 |
| `degrade` | 降级处理，记录 timeout degradation。 |

相关指标：

- `TimeoutCount`
- `FailedCount`
- `RetryCount`
- `RetrySuccessRate`
- checkpoint 和 migration 指标。

## Runtime Failure

字段：

- `runtimeFailureRate`
- `nodeFailureRate`
- `hostFailureRate`
- `rackFailureRate`
- `regionFailureRate`
- `checkpointInterval`
- `migrationDelay`

runtime failure 可能触发 retry、permanent failure、checkpoint recovery 或 migration。失败域会计入 topology/failure-domain metrics。

## Preemption

字段：

- `preemptionEnabled`
- `preemptionPolicy`
- `preemptionMinPriorityGap`
- `preemptionMaxPerTask`
- `preemptionDelay`
- `preemptionPenalty`

策略：

| Value | Meaning |
| :--- | :--- |
| `priority_then_deadline` | 先看优先级差距，再看 deadline。 |
| `deadline_then_priority` | 先看 deadline，再看优先级差距。 |

抢占会影响等待时间、重试/迁移统计、checkpoint loss 和 SLA。

## Autoscaling

字段：

- `autoscalingEnabled`
- `autoscalingPolicy`
- `autoscalingEvaluationInterval`
- `scaleOutQueueThreshold`
- `scaleInIdleTime`
- `maxDynamicVms`
- `vmColdStartDelay`
- `scaleOutCost`
- `scaleInProtectionTime`
- `scaleCooldown`
- `scaleOutBatchSize`
- `warmPoolSize`
- `minActiveVms`
- `scaleInDrainEnabled`
- `arrivalRateWindow`
- `predictiveLookahead`
- `scalePressureThreshold`
- `dynamicVmCostPerSecond`

Autoscaling 会创建动态 VM。动态 VM 有冷启动延迟和成本，相关指标包括：

- active/dynamic VM count；
- cold start delay；
- autoscaling cost；
- queue depth；
- autoscaling pressure、deadline slack pressure、arrival-rate pressure；
- warm pool hit rate、scale-in drain count、dynamic VM seconds。

默认 `autoscalingPolicy = "queue_threshold"`，保持旧行为：任务到达时按 `scaleOutQueueThreshold` 和 `maxDynamicVms` 判断是否创建动态 VM，缩容仍按 `scaleInIdleTime` 与 `scaleInProtectionTime` 回收空闲动态 VM。

`autoscalingPolicy = "deadline_predictive"` 会在队列压力之外加入两个信号：

- deadline slack pressure：复用 realtime candidate score 中的 deadline slack/lateness，不维护第二套 finish/slack 公式；
- arrival-rate pressure：按最近 `arrivalRateWindow` 秒到达数预测 `predictiveLookahead` 秒内需求。

高级策略触发后，单次扩容受 `scaleOutBatchSize`、`maxDynamicVms` 和 `scaleCooldown` 限制。`warmPoolSize` 会维持目标空闲动态 VM 数，`minActiveVms` 会尽量保证 active/starting VM 不低于下限。启用 `scaleInDrainEnabled` 后，动态 VM 缩容先进入 `DRAINING`，不再接受新任务，等 pending/waiting/running/reservation 清空后再终止，不主动中断任务。

`dynamicVmCostPerSecond` 大于 0 时，动态 VM 存活时间按 VM seconds 累加，并叠加到 `AutoscalingCost`；`scaleOutCost` 仍保留一次性扩容成本。

## Resource Model

字段：

- `resourceModelEnabled`
- `vmQueueCapacity`
- `networkLatency`
- `imagePullDelay`
- `ioWeight`
- `ramWeight`
- `bwWeight`

资源模型会影响 candidate scoring 和 rejection。任务需求来自 synthetic generator 或 Google trace metadata。

资源相关指标：

- queue depth；
- resource rejection；
- cold start；
- image cache；
- autoscaling cost。

## Tenant Fairness

字段：

- `multiTenantEnabled`
- `tenantCount`
- `tenantQuota`
- `tenantWeights`
- `tenantFairnessPolicy`
- `tenantSchedulingPolicy`
- `tenantBurstAllowance`
- `tenantSlaPenaltyWeight`
- `tenantCostBudget`

策略：

| Field | Values |
| :--- | :--- |
| `tenantFairnessPolicy` | `quota_first`, `weighted_fair` |
| `tenantSchedulingPolicy` | `quota_first`, `weighted_fair`, `dominant_resource_fairness` |

相关指标：

- `TenantFairnessIndex`
- `DominantResourceFairnessIndex`
- `FairnessViolationCount`
- tenant SLA penalty
- retry success by tenant。

## Topology And Data Locality

字段：

- `topologyEnabled`
- `topologyPolicy`
- `regionCount`
- `racksPerRegion`
- `hostsPerRack`
- `localRegion`
- `crossRackLatency`
- `crossRegionLatency`
- `crossRegionCost`
- `physicalTopologyEnabled`
- `dataLocalityEnabled`
- `dataLocalityPolicy`
- `imageCacheEnabled`
- `imageCacheCapacity`
- `cpuOvercommitRatio`
- `networkBandwidthSharingEnabled`
- `storageIopsSharingEnabled`
- `imagePullQueueEnabled`
- `noisyNeighborPenaltyWeight`

策略：

| Field | Values |
| :--- | :--- |
| `topologyPolicy` | `latency_aware`, `spread_fault_domains` |
| `dataLocalityPolicy` | `prefer_local`, `balanced`, `ignore` |

Data locality 会影响候选排序和成本。Image cache 会影响冷启动和镜像拉取。

物理拓扑默认不改变旧行为。启用 `physicalTopologyEnabled` 后，RAM/BW/IO 是 host 级 hard limit；CPU 默认按 `hostCpuCapacity` 限制，`cpuOvercommitRatio > 1.0` 时允许超过原始 CPU 容量但不超过 overcommit 上限，并增加 throttling delay。启用 `networkBandwidthSharingEnabled` 后，同 route 的跨 rack/region 传输按活跃传输数共享带宽，带宽为 0 的远端传输会被拒绝。启用 `storageIopsSharingEnabled` 后，host I/O 容量会增加 storage delay。启用 `imagePullQueueEnabled` 后，同 host image miss 会叠加拉取队列延迟。`noisyNeighborPenaltyWeight` 大于 0 时，host utilization 和同 host 活跃任务数会进入 resource pressure 和 realtime score。

## Candidate Selection

调度器看到的是 `RealtimeSchedulingContext`。候选 VM 来自 broker 预过滤后的 node states：

- `nodeStates`：所有 VM 状态。
- `nodeCandidates`：候选列表。
- `candidateNodeStates`：候选映射后的状态。
- `acceptingWork`：是否可接受工作。

Realtime PSO/WOA 只在 accepted candidates 上优化。返回值最终必须是真实 VM 下标。

## Metrics To Watch

| Scenario | Metrics |
| :--- | :--- |
| Overload | queue depth、rejected、timeout、failed。 |
| SLA | SLA violation、SLA penalty、average waiting/response time。 |
| DAG dependency | dependency blocked/released/rejected、response time、deadline slack。 |
| Tenant fairness | Jain index、DRF、tenant SLA penalty、fairness violation。 |
| Topology/resource | topology cost、latency、cross-region/rack placement、physical host utilization、host fragmentation、network transfer delay、image cache hit rate、noisy-neighbor pressure。 |
| Autoscaling | dynamic VM peak、cold start delay、autoscaling cost。 |
| Reliability | runtime failure、retry count、retry success rate、checkpoint loss。 |

完整字段见 [realtime-metrics.md](realtime-metrics.md)。
