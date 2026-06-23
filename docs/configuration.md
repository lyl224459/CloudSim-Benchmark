# Configuration Guide

项目使用严格 TOML 解析。推荐把实验写成 profile，通过 CLI 选择 profile 并只覆盖少量运行参数。字段查表见 [config-reference.md](config-reference.md)，常见实验组合见 [experiment-cookbook.md](experiment-cookbook.md)。

## Resolution Order

最终配置按以下优先级合并：

1. CLI 参数。
2. 选中的 profile。
3. 配置文件全局项、algorithm library 和 preset。
4. 代码默认值。

`--dry-run` 会打印合并后的配置快照，适合在正式运行前确认解析结果。

## File Types

项目中有三类 TOML：

| Type | Files | Purpose |
| :--- | :--- | :--- |
| System/experiment defaults | `configs/default.toml`、`configs/batch.toml`、`configs/realtime.toml` | 本地默认值和模式级默认值。 |
| Algorithm library | `configs/algorithms.toml` | 算法默认启用状态、参数和 preset。 |
| Runnable experiment | `configs/examples/*.toml`、`configs/experiments/*.toml` | 带 `defaultProfile` 和 `[profiles.NAME]` 的可执行配置。 |

`RunResolver` 会在读取指定配置后尝试合并 `configs/algorithms.toml`。算法库加载失败时会记录 warning 并跳过，不阻断用户配置运行。

## Root Sections

常见 root section：

| Section | Purpose |
| :--- | :--- |
| `output` | 结果目录和 CSV 设置。 |
| `logging` | 控制台和文件日志设置。 |
| `experiment` | 运行名称、并发度和输出目录创建策略。 |
| `jvm` | JVM heap、GC 等运行建议。 |
| `random` | 默认随机种子。 |
| `optimizer` | 通用优化器参数。 |
| `algorithms.NAME` | 算法启用状态和算法参数。 |
| `presets.NAME` | 算法集合。 |
| `profiles.NAME` | 可执行实验 profile。 |

## Profiles

一个文件可以包含多个 profile。`defaultProfile` 是没有显式 `--profile` 时的默认选择。

```toml
defaultProfile = "batch_smoke"

[profiles.batch_smoke]
mode = "batch"
algorithms = ["RANDOM"]
runs = 1

[profiles.batch_smoke.batch]
cloudletCount = 20
```

Profile 支持的 mode：

- `batch`
- `realtime`
- `batch-multi`
- `realtime-multi`

multi 模式没有显式 `tasks` 时默认使用 `50,100,200,500`。

## Algorithms And Presets

算法可以直接写在 profile 中：

```toml
[profiles.realtime_smoke]
mode = "realtime"
algorithms = ["MIN_LOAD", "RANDOM"]
runs = 1
```

也可以通过 preset 复用：

```toml
[presets.small_scale]
algorithms = ["PSO", "WOA", "GWO"]

[profiles.batch_small]
mode = "batch"
preset = "small_scale"
runs = 5
```

`algorithms` 与 `preset` 在同一个 profile 中互斥。

## Override Examples

使用 profile 中的 mode 和任务设置，但覆盖算法与运行次数：

```powershell
.\run.cmd run --config configs/examples/batch_test.toml --profile batch_test --algorithms RANDOM --runs 1
```

使用 profile 中的算法，但覆盖输出目录：

```powershell
.\run.cmd run --config configs/examples/single_config_example.toml --profile batch_small --output tmp-runs
```

使用 preset：

```powershell
.\run.cmd run --config configs/examples/single_config_example.toml --profile batch_small --preset small_scale
```

如果同时指定 `--preset` 和 `--algorithms`，CLI 会失败。

## Batch Section

批处理 profile 的 batch 子段控制任务数和优化器参数：

```toml
[profiles.batch_small.batch]
cloudletCount = 100
population = 30
maxIter = 50
```

## Realtime Section

实时 profile 的 realtime 子段控制到达、调度、资源、租户和拓扑策略。示例文件：

- `configs/examples/realtime_test.toml`
- `configs/examples/realtime_workloads.toml`
- `configs/examples/single_config_example.toml`
- `configs/experiments/google_trace_test.toml`

实时指标字段见 [realtime-metrics.md](realtime-metrics.md)。实时调度模型、策略和指标使用建议见 [realtime-scheduling.md](realtime-scheduling.md)。

### Realtime Arrival And Workload

到达分布写在 `[profiles.NAME.realtime.arrival]`。默认 `distribution = "poisson"`、`workloadPattern = "standard"`，旧配置无需修改。

```toml
[profiles.realtime_dag.realtime.arrival]
distribution = "periodic"
periodSeconds = 0.8
arrivalJitter = 0.1
workloadPattern = "dag_layered"
runtimeReferenceMips = 1000.0
dagDepth = 4
dagWidth = 3
dagFanOut = 2
```

常见 workload：

| Workload | Key Fields | Meaning |
| :--- | :--- | :--- |
| periodic | `periodSeconds`、`arrivalJitter` | 固定周期到达，可加 seed 可复现 jitter。 |
| sporadic | `sporadicMinInterArrival`、`sporadicMaxInterArrival` | 在最小/最大间隔内采样，保持到达时间有序。 |
| diurnal burst | `diurnalPeakMultiplier`、`diurnalOffPeakMultiplier` | 用仿真时长作为一个昼夜周期调制到达率。 |
| mixed short/long | `shortTaskRatio`、`shortTaskLengthMultiplier`、`longTaskLengthMultiplier` | 按比例生成长短任务，并写入 expected duration metadata。 |
| DAG | `dagDepth`、`dagWidth`、`dagFanOut` | 生成 chain 或 layered workflow dependency metadata。 |

## Realtime Scheduling Subsections

实时配置通常通过 `[profiles.NAME.realtime.scheduling]` 承载调度策略：

```toml
[profiles.realtime_sla.realtime.scheduling]
deadlineFactor = 1.2
deadlineAdmissionEnabled = true
deadlineType = "soft"
deadlineMissAction = "accept"
reschedulingEnabled = false
reschedulingInterval = 0.0
reschedulingPolicy = "deadline_score"
maxReschedulesPerTask = 1
dependencyEnforcementEnabled = true
taskTimeout = 20.0
timeoutAction = "retry"
retryLimit = 1
retryDelay = 1.0
checkpointInterval = 5.0
```

常见策略字段：

| Field Group | Examples | Meaning |
| :--- | :--- | :--- |
| Queue/deadline | `queuePolicy`、`deadlineFactor`、`deadlineAdmissionEnabled`、`deadlineType`、`deadlineMissAction`、`taskTimeout` | 控制 FIFO、priority、deadline admission 和 timeout。 |
| Rescheduling | `reschedulingEnabled`、`reschedulingInterval`、`reschedulingPolicy`、`maxReschedulesPerTask` | 控制周期性检查 pending、waiting、running 任务并按内置策略重调度。 |
| Dependency | `dependencyEnforcementEnabled` | 控制 DAG metadata 存在时是否强制等待前驱成功完成。 |
| Resource/topology model | `resourceModelEnabled`、`vmQueueCapacity`、`physicalTopologyEnabled`、`cpuOvercommitRatio`、`networkBandwidthSharingEnabled`、`storageIopsSharingEnabled`、`imagePullQueueEnabled`、`noisyNeighborPenaltyWeight` | 控制资源需求、host 级容量拒绝、共享传输/存储延迟、镜像拉取队列和 noisy-neighbor 候选压力。 |
| Failure/retry | `runtimeFailureRate`、`nodeFailureRate`、`retryLimit`、`retryDelay` | 控制运行时失败、节点失败和重试。 |
| Autoscaling | `autoscalingEnabled`、`autoscalingPolicy`、`autoscalingEvaluationInterval`、`scaleOutQueueThreshold`、`scaleCooldown`、`scaleOutBatchSize`、`warmPoolSize`、`minActiveVms`、`scaleInDrainEnabled`、`arrivalRateWindow`、`predictiveLookahead`、`scalePressureThreshold`、`dynamicVmCostPerSecond` | 控制队列阈值扩缩容、deadline/到达率预测扩容、warm pool、drain 和动态 VM 秒级成本。 |
| Tenant/topology | `tenantFairnessPolicy`、`topologyPolicy`、`dataLocalityPolicy` | 控制多租户公平、故障域和数据本地性。 |

字段具体默认值以 domain config 和示例配置为准；新增字段必须同时更新 validator 和文档。

## Google Trace

Google trace 配置通过 generator 设置启用。示例见 `configs/experiments/google_trace_test.toml`。解析逻辑会跳过字段不足、非法数字和时间窗口外记录，并在 trace 不可用时使用可运行 fallback。

realtime 模式会把 trace timestamp 转成仿真秒。默认 `normalizeTimestamps = true`，到达时间按 `(timestamp - firstTimestamp) / timestampDivisor` 计算；如果关闭归一化，则按 `timestamp / timestampDivisor` 计算。

新增 trace 字段时需要同步：

- `GoogleTraceConfig`。
- trace parser/spec factory。
- realtime metadata registry。
- Google trace 异常输入测试。

完整字段映射和排错见 [google-trace.md](google-trace.md)。

## Config Files

基础配置：

- `configs/default.toml`
- `configs/batch.toml`
- `configs/realtime.toml`
- `configs/algorithms.toml`

示例配置：

- `configs/examples/batch_test.toml`
- `configs/examples/realtime_test.toml`
- `configs/examples/batch_multi_test.toml`
- `configs/examples/realtime_multi_test.toml`
- `configs/examples/single_config_example.toml`
- `configs/examples/realtime_workloads.toml`
- `configs/examples/realtime_resource_topology.toml`

实验配置：

- `configs/experiments/quick_test.toml`
- `configs/experiments/performance_test.toml`
- `configs/experiments/scalability_test.toml`
- `configs/experiments/custom_objective.toml`
- `configs/experiments/google_trace_test.toml`

所有 `configs/**/*.toml` 都由测试解析；README 中的 TOML 代码块也必须是可独立解析的配置。

## Strict Parsing

配置解析会拒绝未知字段和旧顶层实验 schema。多行数组、quoted profile name、inline array 等标准 TOML 形态受支持。新增配置字段时应同步测试、示例配置和文档。
