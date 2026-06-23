# Configuration Reference

本文档是 TOML 字段查表。配置合并和示例说明见 [configuration.md](configuration.md)。

## Root Fields

| Field | Type | Default | Notes |
| :--- | :--- | :--- | :--- |
| `defaultProfile` | string? | null | 未显式传 `--profile` 时使用。 |
| `mode` | string? | null | 旧 root 级 mode 仍会被解析，但推荐写 profile。 |
| `random.seed` | long | `0` | 默认随机种子。 |
| `optimizer.population` | int | `20` | realtime 优化器默认种群。 |
| `optimizer.maxIter` | int | `20` | realtime 优化器默认迭代次数。 |

## System Sections

### `output`

| Field | Type | Default | Notes |
| :--- | :--- | :--- | :--- |
| `resultsDir` | string | `runs` | 实验输出根目录。 |
| `csv.enabled` | boolean | `true` | false 时不写 trial/summary CSV。 |
| `csv.delimiter` | string | `,` | CSV 分隔符。 |

### `logging`

| Field | Type | Default | Notes |
| :--- | :--- | :--- | :--- |
| `level` | string | `INFO` | 日志级别。 |
| `file` | boolean | `true` | 是否写文件日志。 |
| `console` | boolean | `true` | 是否输出控制台日志。 |

### `experiment`

| Field | Type | Default | Notes |
| :--- | :--- | :--- | :--- |
| `autoCreateDirs` | boolean | `true` | 自动创建输出目录。 |
| `nameFormat` | string | `{mode}_{timestamp}_{algorithms}` | 支持 mode、timestamp、algorithms、preset、tasks token。 |
| `maxConcurrent` | int | CPU core count | 默认最大并发。 |

### `jvm`

| Field | Type | Default | Notes |
| :--- | :--- | :--- | :--- |
| `maxHeapSize` | string | `2g` | JVM heap 建议。 |
| `gcAlgorithm` | string | `G1` | GC 建议。 |

## Algorithm And Preset Sections

### `algorithms.NAME`

| Field | Type | Default | Notes |
| :--- | :--- | :--- | :--- |
| `enabled` | boolean | `true` | `ALL` 展开时只包含 enabled 算法。 |
| `description` | string | empty | 说明文本。 |
| `population` | int? | null | 支持 population 的算法使用。 |
| `maxIter` | int? | null | 支持 maxIter 的算法使用。 |

### `presets.NAME`

| Field | Type | Default | Notes |
| :--- | :--- | :--- | :--- |
| `algorithms` | list<string> | empty | 算法名或别名列表。 |

## Profile Fields

### `profiles.NAME`

| Field | Type | Default | Notes |
| :--- | :--- | :--- | :--- |
| `mode` | string | empty | `batch`、`realtime`、`batch-multi`、`realtime-multi`。 |
| `algorithms` | list<string> | empty | 与 `preset` 互斥。 |
| `preset` | string? | null | 引用 `presets.NAME`。 |
| `runs` | int? | null | trial 次数。 |
| `seed` | long? | null | 覆盖 random seed。 |
| `tasks` | list<int> | empty | multi mode 任务数列表。 |
| `outputDir` | string? | null | 覆盖输出根目录。 |
| `batch` | table? | null | batch 子配置。 |
| `realtime` | table? | null | realtime 子配置。 |

## Batch Fields

`[profiles.NAME.batch]` 或 root `batch`：

| Field | Type | Default | Notes |
| :--- | :--- | :--- | :--- |
| `cloudletCount` | int | `100` | 单任务规模。 |
| `cloudletCounts` | list<int> | empty | multi mode 可用。 |
| `population` | int | `30` | batch metaheuristic 种群。 |
| `maxIter` | int | `50` | batch metaheuristic 迭代。 |
| `runs` | int | `1` | trial 次数。 |
| `generator.type` | string | `LOG_NORMAL` | 新配置形态。 |
| `generatorType` | string | `LOG_NORMAL` | 兼容形态。 |
| `googleTrace` | table? | null | Google trace 参数。 |
| `objective` | table | default weights | 目标函数权重。 |

## Realtime Fields

`[profiles.NAME.realtime]` 或 root `realtime`：

| Field | Type | Default | Notes |
| :--- | :--- | :--- | :--- |
| `cloudletCount` | int | `200` | 单任务规模。 |
| `cloudletCounts` | list<int> | empty | multi mode 可用。 |
| `simulationDuration` | double | `500.0` | 仿真持续时间。 |
| `arrivalRate` | double | `5.0` | 平均到达率。 |
| `runs` | int | `1` | trial 次数。 |
| `generator.type` | string | `LOG_NORMAL` | 新配置形态。 |
| `generatorType` | string | `LOG_NORMAL` | 兼容形态。 |
| `googleTrace` | table? | null | Google trace 参数。 |
| `objective` | table | default weights | 目标函数权重。 |
| `arrival` | table | defaults | 到达模型参数。 |
| `scheduling` | table | defaults | 实时调度参数。 |

## Objective Weights

| Field | Default | Constraint |
| :--- | :--- | :--- |
| `cost` | `1/3` | `0.0..1.0` |
| `totalTime` | `1/3` | `0.0..1.0` |
| `loadBalance` | `1/3` | `0.0..1.0` |
| `makespan` | `0.0` | `0.0..1.0` |

权重总和必须大于 0。权重不会自动归一化；目标函数按配置值加权。

## Realtime Arrival

| Field | Default | Notes |
| :--- | :--- | :--- |
| `distribution` | `poisson` | 到达分布名称。 |
| `burstIntensity` | `2.0` | burst 强度。 |
| `burstDuration` | `50.0` | burst 持续时间。 |
| `workloadPattern` | `standard` | `standard`、`mixed_short_long`、`dag_chain`、`dag_layered`。 |
| `periodSeconds` | `1.0` | `periodic` 到达的固定周期，单位秒。 |
| `arrivalJitter` | `0.0` | 周期到达的对称 jitter，单位秒。 |
| `sporadicMinInterArrival` | `0.1` | `sporadic` 最小到达间隔，单位秒。 |
| `sporadicMaxInterArrival` | `1.0` | `sporadic` 最大到达间隔，必须不小于最小间隔。 |
| `diurnalPeakMultiplier` | `2.0` | `diurnal_burst` 峰值到达率倍率。 |
| `diurnalOffPeakMultiplier` | `0.5` | `diurnal_burst` 低谷到达率倍率。 |
| `shortTaskRatio` | `0.8` | `mixed_short_long` 中短任务比例，范围 `0.0..1.0`。 |
| `shortTaskLengthMultiplier` | `0.5` | 短任务长度倍率，必须大于 0。 |
| `longTaskLengthMultiplier` | `2.0` | 长任务长度倍率，必须大于 0。 |
| `runtimeReferenceMips` | `1000.0` | 将 expected duration 映射为 cloudlet length 的参考 MIPS，必须大于 0。 |
| `dagDepth` | `3` | DAG 生成深度，必须大于 0。 |
| `dagWidth` | `2` | DAG 分层宽度，必须大于 0。 |
| `dagFanOut` | `1` | 每个 DAG 节点最多依赖的前驱数，必须大于 0。 |

`distribution` 支持 `poisson`、`uniform`、`burst`、`periodic`、`sporadic`、`diurnal_burst`。旧配置默认仍是 `poisson` + `standard`。

## Realtime Scheduling

| Field | Default | Notes |
| :--- | :--- | :--- |
| `strategy` | `dynamic` | 调度策略名。 |
| `maxQueueSize` | `Int.MAX_VALUE` | 全局队列上限。 |
| `decisionDelay` | `0.0` | 决策延迟。 |
| `decisionJitter` | `0.0` | 决策抖动。 |
| `queuePolicy` | `fifo` | `fifo`、`priority`、`deadline`。 |
| `priorityLevels` | `1` | 优先级数量。 |
| `highPriorityRatio` | `0.0` | 高优先级任务比例。 |
| `deadlineFactor` | `0.0` | deadline 生成系数。 |
| `deadlineAdmissionEnabled` | `true` | 是否在调度前按 projected finish time 做 deadline 可调度性判断。 |
| `deadlineType` | `soft` | `soft`、`firm`、`hard`；firm/hard 会在可按期候选存在时过滤 late 候选。 |
| `deadlineMissAction` | `accept` | `accept`、`reject`、`degrade`、`retry_later`；`retry_later` 复用 retry 参数。 |
| `reschedulingEnabled` | `false` | 是否启用 CloudSim 周期性重调度；默认关闭，保持 arrival-only 行为。 |
| `reschedulingInterval` | `0.0` | 重调度 tick 间隔，单位秒；启用时必须大于 0。 |
| `reschedulingPolicy` | `deadline_score` | `deadline_score`、`score_only`、`deadline_only`。 |
| `maxReschedulesPerTask` | `1` | 单任务最多执行的周期性重调度次数；启用时必须大于 0。 |
| `dependencyEnforcementEnabled` | `true` | 存在 DAG dependency metadata 时是否强制等待前驱成功完成。 |
| `vmQueueCapacity` | `0` | 单 VM 队列容量，0 表示不启用。 |
| `taskTimeout` | `0.0` | 0 表示不启用 timeout。 |
| `timeoutAction` | `fail` | `fail`、`retry`、`cancel`、`degrade`。 |
| `retryLimit` | `0` | 最大重试次数。 |
| `retryDelay` | `0.0` | 重试延迟。 |
| `retryBackoffMultiplier` | `1.0` | 重试退避倍率。 |
| `runtimeFailureRate` | `0.0` | 运行时失败率。 |
| `nodeFailureRate` | `0.0` | 节点失败率。 |
| `checkpointInterval` | `0.0` | checkpoint 间隔。 |
| `migrationDelay` | `0.0` | migration 延迟。 |
| `preemptionEnabled` | `false` | 是否启用抢占。 |
| `preemptionPolicy` | `priority_then_deadline` | `priority_then_deadline`、`deadline_then_priority`。 |
| `preemptionMinPriorityGap` | `1` | 触发抢占的最小优先级差。 |
| `preemptionMaxPerTask` | `1` | 单任务最大抢占次数。 |
| `preemptionDelay` | `0.0` | 抢占延迟。 |
| `preemptionPenalty` | `0.0` | 抢占惩罚。 |
| `autoscalingEnabled` | `false` | 是否启用 autoscaling。 |
| `scaleOutQueueThreshold` | `0` | 扩容队列阈值。 |
| `scaleInIdleTime` | `0.0` | 缩容空闲时间。 |
| `maxDynamicVms` | `0` | 最大动态 VM 数。 |
| `vmColdStartDelay` | `0.0` | VM 冷启动延迟。 |
| `scaleOutCost` | `0.0` | 扩容成本。 |
| `scaleInProtectionTime` | `0.0` | 缩容保护时间。 |

## Realtime Resource Fields

| Field | Default | Notes |
| :--- | :--- | :--- |
| `resourceModelEnabled` | `false` | 是否启用资源模型。 |
| `networkLatency` | `0.0` | 网络延迟基线。 |
| `imagePullDelay` | `0.0` | 镜像拉取延迟。 |
| `ioWeight` | `0.0` | IO 权重。 |
| `ramWeight` | `0.0` | RAM 权重。 |
| `bwWeight` | `0.0` | 带宽权重。 |

## Tenant Fields

| Field | Default | Notes |
| :--- | :--- | :--- |
| `multiTenantEnabled` | `false` | 是否启用多租户。 |
| `tenantCount` | `1` | 租户数量。 |
| `tenantQuota` | empty | 每租户 quota。 |
| `tenantWeights` | empty | weighted fair 权重。 |
| `tenantFairnessPolicy` | `quota_first` | `quota_first`、`weighted_fair`。 |
| `tenantSchedulingPolicy` | `quota_first` | `quota_first`、`weighted_fair`、`dominant_resource_fairness`。 |
| `tenantBurstAllowance` | `0` | 租户 burst 容忍。 |
| `tenantSlaPenaltyWeight` | `1.0` | 租户 SLA penalty 权重。 |
| `tenantCostBudget` | empty | 租户成本预算。 |

## Topology Fields

| Field | Default | Notes |
| :--- | :--- | :--- |
| `topologyEnabled` | `false` | 是否启用拓扑模型。 |
| `topologyPolicy` | `latency_aware` | `latency_aware`、`spread_fault_domains`。 |
| `regionCount` | `3` | region 数。 |
| `racksPerRegion` | `2` | 每 region rack 数。 |
| `hostsPerRack` | `2` | 每 rack host 数。 |
| `localRegion` | `0` | 本地区域。 |
| `crossRackLatency` | `0.1` | 跨 rack 延迟。 |
| `crossRegionLatency` | `1.0` | 跨 region 延迟。 |
| `crossRegionCost` | `0.0` | 跨 region 成本。 |
| `hostFailureRate` | `0.0` | host 故障率。 |
| `rackFailureRate` | `0.0` | rack 故障率。 |
| `regionFailureRate` | `0.0` | region 故障率。 |
| `physicalTopologyEnabled` | `false` | 是否启用物理拓扑容量。 |
| `dataLocalityEnabled` | `false` | 是否启用数据本地性。 |
| `dataLocalityPolicy` | `prefer_local` | `prefer_local`、`balanced`、`ignore`。 |
| `imageCacheEnabled` | `false` | 是否启用 image cache。 |
| `imageCacheCapacity` | `0` | cache 容量。 |
| `networkBandwidthSharingEnabled` | `false` | 是否按 route 活跃传输数共享跨 rack/region 带宽。 |
| `storageIopsSharingEnabled` | `false` | 是否按 host I/O 容量增加 storage delay。 |
| `imagePullQueueEnabled` | `false` | 是否让同 host image miss 进入共享拉取队列。 |
| `noisyNeighborPenaltyWeight` | `0.0` | noisy-neighbor 压力权重，必须非负。 |

## Physical Host Capacity

| Field | Default |
| :--- | :--- |
| `hostCountPerRack` | `2` |
| `hostCpuCapacity` | `0.0` |
| `cpuOvercommitRatio` | `1.0` |
| `hostRamCapacity` | `0.0` |
| `hostBwCapacity` | `0.0` |
| `hostIoCapacity` | `0.0` |
| `crossRackBandwidth` | `0.0` |
| `crossRegionBandwidth` | `0.0` |

## Generator Types

| Value | Meaning |
| :--- | :--- |
| `LOG_NORMAL` | 默认 log-normal 任务生成器。 |
| `UNIFORM` | 均匀分布任务生成器。 |
| `LOG_NORMAL_SCI` | 科研参数 log-normal 任务生成器。 |
| `GOOGLE_TRACE` | Google trace 风格输入。 |

## Google Trace Fields

| Field | Default | Notes |
| :--- | :--- | :--- |
| `filePath` | `data/google_trace/task_events.csv` | trace task events 路径。 |
| `maxTasks` | `1000` | 最多读取的 trace 记录数。 |
| `timeWindowStart` | `0` | 原始 trace timestamp 下界。 |
| `timeWindowEnd` | `Long.MAX_VALUE` | 原始 trace timestamp 上界。 |
| `normalizeTimestamps` | `true` | realtime 模式下是否把首条记录作为 0 秒基准。 |
| `timestampDivisor` | `1000000.0` | 原始 timestamp 转仿真秒的除数，必须大于 0。 |

## Validation Notes

- 未知字段失败。
- mode 必须是支持值。
- policy 字段必须能被对应 enum 解析。
- 权重必须在 `0.0..1.0` 且总和大于 0。
- `algorithms` 和 `preset` 在同一 profile 中互斥。
- multi mode 默认任务数是 `50,100,200,500`。
