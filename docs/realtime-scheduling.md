# Realtime Scheduling Guide

本文档解释实时调度模型：任务如何到达、如何进入 broker、候选 VM 如何过滤、超时和失败如何处理，以及租户、拓扑和资源模型如何影响指标。

## High-Level Flow

```text
RealtimeCloudletGenerator
  -> CloudSim arrival event
  -> RealtimeBroker.processEvent
  -> admission / metadata / candidate snapshot
  -> RealtimeScheduler.scheduleOnArrival
  -> accepted VM index or rejection
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
| `arrival.distribution` | 到达分布，目前配置层保留 `poisson` 等值。 |
| `arrival.burstIntensity` | burst 到达强度。 |
| `arrival.burstDuration` | burst 持续时间。 |

到达模型决定任务何时进入 broker。调度器只在任务到达时做单次选择，不会像 batch 一样一次性看到全部未来任务。

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

## Admission And Rejection

broker 可以因为多种原因拒绝任务或候选 VM：

| Reason | Typical Cause |
| :--- | :--- |
| resource | RAM/BW/IO/CPU 需求超过候选容量。 |
| capacity | VM 队列容量满。 |
| deadline | deadline admission 判定所有候选都会 miss，且 miss action 要求拒绝。 |
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
- `scaleOutQueueThreshold`
- `scaleInIdleTime`
- `maxDynamicVms`
- `vmColdStartDelay`
- `scaleOutCost`
- `scaleInProtectionTime`

Autoscaling 会创建动态 VM。动态 VM 有冷启动延迟和成本，相关指标包括：

- active/dynamic VM count；
- cold start delay；
- autoscaling cost；
- queue depth。

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

策略：

| Field | Values |
| :--- | :--- |
| `topologyPolicy` | `latency_aware`, `spread_fault_domains` |
| `dataLocalityPolicy` | `prefer_local`, `balanced`, `ignore` |

Data locality 会影响候选排序和成本。Image cache 会影响冷启动和镜像拉取。

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
| Tenant fairness | Jain index、DRF、tenant SLA penalty、fairness violation。 |
| Topology | topology cost、latency、cross-region/rack placement。 |
| Autoscaling | dynamic VM peak、cold start delay、autoscaling cost。 |
| Reliability | runtime failure、retry count、retry success rate、checkpoint loss。 |

完整字段见 [realtime-metrics.md](realtime-metrics.md)。
