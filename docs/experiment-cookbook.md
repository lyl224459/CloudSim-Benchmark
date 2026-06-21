# Experiment Cookbook

本文档给出常见实验场景的命令和配置选择。所有命令都可以先加 `--dry-run` 验证合并配置。

## Batch Smoke

用途：确认环境、CloudSim Plus、CLI 和输出目录可用。

```powershell
.\run.cmd run --mode batch --algorithms RANDOM --runs 1
```

关注：

- 是否创建 `runs/batch/...`。
- 是否生成 `resolved_config.json`。
- `summary_avg.csv` 中 `Status=SUCCESS`。

## Realtime Smoke

用途：确认实时 broker、arrival 和 realtime scheduler 可用。

```powershell
.\run.cmd run --mode realtime --algorithms MIN_LOAD --runs 1
```

关注：

- `RejectedCount` 是否符合预期。
- `FailedCount` 是否为 0。
- `AverageWaitingTime` 是否有限。

## Batch Algorithm Comparison

用途：比较 batch metaheuristic 和 baseline。

```powershell
.\run.cmd run --mode batch --algorithms RANDOM,PSO,WOA,GWO,HHO --runs 3 --seed 42
```

建议：

- 先用较小 cloudlet count 验证。
- 再使用 `configs/experiments/performance_test.toml` 或自定义 profile 放大任务数。
- 比较 `Fitness_Mean`、`Makespan_Mean`、`Cost_Mean` 和 `*_StdDev`。

## Realtime SLA Scenario

用途：观察 deadline、timeout 和 retry。

```powershell
.\run.cmd run --config configs/examples/single_config_example.toml --profile realtime_sla
```

关注：

- `TimeoutCount`
- `RetryCount`
- `RetrySuccessRate`
- `SlaPenalty`
- `AverageResponseTime`

如果 timeout 太多，尝试提高 `deadlineFactor`、降低 `arrivalRate` 或增加 VM 容量。

## Realtime Overload Scenario

用途：验证过载、失败和重试指标。

```powershell
.\run.cmd run --config configs/examples/single_config_example.toml --profile realtime_overload
```

关注：

- `RejectedCount`
- `ResourceRejectedCount`
- `FailedCount`
- `RetryCount`
- `MaxQueueDepth`

## Priority Queue Scenario

用途：验证 priority queue 和高优先级任务效果。

```powershell
.\run.cmd run --config configs/examples/single_config_example.toml --profile realtime_priority
```

关注：

- 高优先级任务是否减少等待或 timeout。
- `AverageWaitingTime` 和 SLA penalty 是否下降。

## Capacity And Resource Model Scenario

用途：观察 RAM/BW/IO 需求、队列容量和 resource rejection。

```powershell
.\run.cmd run --config configs/examples/single_config_example.toml --profile realtime_capacity
```

关注：

- `ResourceRejectedCount`
- `AvgQueueDepth`
- `MaxQueueDepth`
- `ColdStartDelayTotal`

## Autoscaling Scenario

用途：验证动态 VM、冷启动和伸缩成本。

```powershell
.\run.cmd run --config configs/examples/single_config_example.toml --profile realtime_autoscaling
```

关注：

- dynamic VM peak；
- cold start delay；
- autoscaling cost；
- queue depth 是否下降；
- rejected/timeout 是否减少。

## Multi-Count Scalability

Batch：

```powershell
.\run.cmd run --mode batch-multi --tasks 50,100,200,500 --algorithms RANDOM,PSO --runs 3
```

Realtime：

```powershell
.\run.cmd run --mode realtime-multi --tasks 50,100,200,500 --algorithms MIN_LOAD,RANDOM --runs 3
```

关注：

- `CloudletCount` 与指标曲线。
- 算法是否在大任务数下失败。
- stddev 是否随规模变大而放大。

## Google Trace Batch

```powershell
.\run.cmd run --config configs/experiments/google_trace_test.toml --profile google_trace_batch
```

先确认 trace 文件存在：

```text
data/google_trace/task_events.csv
```

如果文件不存在，会 fallback 到 mock trace 数据。正式实验不要依赖 fallback。

## Google Trace Realtime

```powershell
.\run.cmd run --config configs/experiments/google_trace_test.toml --profile google_trace_realtime
```

适合验证：

- tenant metadata；
- requested CPU/RAM/BW/IO；
- image id；
- data region；
- retry hint。

## Dry-Run Before Long Runs

```powershell
.\run.cmd run --config configs/experiments/performance_test.toml --profile batch_compare --dry-run
```

检查：

- mode；
- profile；
- selected algorithms；
- task counts；
- random seed；
- output dir；
- CSV 设置。

## Disable CSV For Console-Only Runs

在配置中：

```toml
[output.csv]
enabled = false
```

用途：

- 快速实验；
- 避免写磁盘；
- 只看控制台 summary。

注意：禁用 CSV 后不会生成 trial/summary 文件，不适合正式对比。

## Performance Trend

```powershell
.\gradlew.bat benchmarkPerformanceTrend --no-daemon --stacktrace
```

带历史 baseline：

```powershell
.\gradlew.bat benchmarkPerformanceTrend `
  -PperformanceBaseline=build/reports/performance/previous-jmh-results.json `
  --no-daemon --stacktrace
```

用于观察代码变更对核心调度热点的影响，不作为普通 hosted runner 硬门禁。

## Recommended Experiment Record

正式实验建议记录：

- Git commit。
- CloudSim Plus lock。
- JDK/Gradle/Kotlin 版本。
- CLI 命令。
- `resolved_config.json`。
- `summary_avg.csv` 和每个算法 trial CSV。
- 是否使用 `--sequential` 或特定 `--concurrency`。
- 是否使用真实 Google trace 文件。
