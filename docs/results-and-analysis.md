# Results And Analysis Guide

本文档说明实验运行后会生成哪些文件、CSV 字段如何解读、失败 trial 如何记录，以及如何比较 batch、realtime 和 multi-count 实验结果。

## Output Directory Layout

默认输出根目录是 `runs/`，可通过以下方式覆盖：

- CLI：`--output DIR`
- TOML：`[output].resultsDir`
- profile：`outputDir`

真实运行会创建模式目录和实验目录，例如：

```text
runs/
  batch/
    batch_20260621_153000_RANDOM/
      experiment_info.txt
      resolved_config.json
      RANDOM.csv
      batch_comparison_20260621_153001.csv
      summary_avg.csv
  realtime/
    realtime_20260621_153500_MIN_LOAD/
      experiment_info.txt
      resolved_config.json
      MIN_LOAD.csv
      realtime_candidate_scores.csv
      realtime_comparison_20260621_153501.csv
      summary_avg.csv
```

如果没有显式实验名，目录名形如 `exp1_yyyyMMdd_HHmmss`。如果 `csv.enabled=false`，CSV 文件不会写入，但控制台结果和实验流程仍会执行。

## Common Files

| File | Written By | Purpose |
| :--- | :--- | :--- |
| `experiment_info.txt` | runner | 保存运行模式、任务数、算法、随机种子、trial 数、生成器等实验元数据。 |
| `resolved_config.json` | CLI / dry-run path | 保存最终合并后的配置快照，便于复现实验。 |
| `<Algorithm>.csv` | `ExperimentOutputContext.saveAlgorithmTrialRow` | 每个算法的 trial 级结果。 |
| `summary_avg.csv` | exporter | 每个算法的 summary 结果。 |
| `batch_comparison_*.csv` | batch exporter | batch 算法对比结果，包含均值和标准差。 |
| `realtime_comparison_*.csv` | realtime exporter | realtime 算法对比结果，包含均值和标准差。 |
| `realtime_candidate_scores.csv` | realtime exporter | 每次实时调度的候选 VM score 明细；仅 realtime 成功 trial 且 CSV 开启时写入。 |

`summary_avg.csv` 是固定文件名，每个实验目录只保留该实验的一份 summary。`*_comparison_*.csv` 使用时间戳，适合从同一个目录多次手动导出时保留历史。

## Batch Trial CSV

Batch trial header：

```text
Trial,Status,ErrorType,ErrorMessage,Makespan,LoadBalance,Cost,TotalTime,Fitness
```

字段含义：

| Field | Meaning |
| :--- | :--- |
| `Trial` | trial 序号，从 1 开始。 |
| `Status` | `SUCCESS` 或 `FAILED`。 |
| `ErrorType` | 失败时的异常类型，例如 `IllegalArgumentException`。 |
| `ErrorMessage` | 失败详情。 |
| `Makespan` | 估计最大完成时间，越小越好。 |
| `LoadBalance` | 负载均衡指标，越小越好。 |
| `Cost` | 成本估算，越小越好。 |
| `TotalTime` | 所有任务估计执行时间总和，越小越好。 |
| `Fitness` | 目标函数综合值，越小越好。 |

失败 trial 的 metric 单元格为空，失败原因放在 `ErrorType` 和 `ErrorMessage`。

## Batch Summary CSV

Batch summary header：

```text
Algorithm,Status,ErrorType,ErrorMessage,Runs,SuccessfulRuns,FailedRuns,
Makespan_Mean,LoadBalance_Mean,Cost_Mean,TotalTime_Mean,Fitness_Mean,
Makespan_StdDev,LoadBalance_StdDev,Cost_StdDev,TotalTime_StdDev,Fitness_StdDev
```

`Status` 取值：

| Status | Meaning |
| :--- | :--- |
| `SUCCESS` | 全部 trial 成功。 |
| `PARTIAL_FAILURE` | 至少一个 trial 成功，至少一个 trial 失败。 |
| `FAILED` | 全部 trial 失败。 |

`*_Mean` 和 `*_StdDev` 只基于成功 trial 计算。全部失败时 metric 单元格为空。

## Realtime Trial And Summary CSV

Realtime CSV 由 `RealtimeMetricSchema` 统一生成。公共前缀字段：

```text
Trial,Status,ErrorType,ErrorMessage,...
Algorithm,Status,ErrorType,ErrorMessage,Runs,SuccessfulRuns,FailedRuns,...
```

后续指标包括：

- performance：`Makespan`、`LoadBalance`、`Cost`、`TotalTime`。
- admission/SLA：`RejectedCount`、`DeadlineRejectedCount`、`DeadlineDegradedCount`、`DeadlineRetryLaterCount`、`DeadlineMissAcceptedCount`、`TimeoutCount`、`SlaViolationCount`。
- rescheduling：`RescheduleAttemptCount`、`RescheduleSuccessCount`、`RescheduleFailureCount`、`AvgRescheduleDelay`。
- resource/autoscaling：队列深度、动态 VM、冷启动、伸缩成本。
- reliability：失败、重试、迁移和 checkpoint。
- tenant/fairness：Jain index、quota、tenant SLA penalty。
- topology/failure-domain：拓扑成本、延迟、跨域放置、故障域。

完整字段见 [realtime-metrics.md](realtime-metrics.md)。

## Realtime Candidate Score CSV

`realtime_candidate_scores.csv` 是窄表，不把候选 VM 的逐项分量塞进 trial/summary 宽表。每行对应一次 cloudlet 到达时的一个候选 VM，适合排查为什么某个 VM 被选中或被拒绝。

字段：

| Field | Meaning |
| :--- | :--- |
| `Algorithm` | 算法名。 |
| `Run` | trial 序号。 |
| `CloudletId` | 到达任务 ID。 |
| `ArrivalTime` | 任务原始到达时间。 |
| `SelectedVmIndex` | 本次调度最终选择的 VM 下标。 |
| `CandidateVmIndex` | 当前候选 VM 下标。 |
| `Accepted` | 候选是否通过容量、资源、拓扑和租户过滤。 |
| `Selected` | 当前候选是否为最终选择。 |
| `TotalScore` | realtime score 总分，越低越好。 |
| `ProjectedFinishTime` | 估算完成时间。 |
| `EstimatedRuntime` | 当前任务在该 VM 上的估算运行时间。 |
| `DeadlineSlack` | deadline 减 projected finish time；无 deadline 时为 0。 |
| `LatenessPenalty` | deadline miss 惩罚；无 deadline 时为 0。 |
| `PriorityPressure` | 优先级压力分量。 |
| `PreemptionCost` | 未命中可抢占候选时的额外成本。 |
| `ResourcePressure` | RAM/BW/IO 等资源压力分量。 |
| `TopologyLatency` | 拓扑延迟分量。 |
| `TopologyCost` | 拓扑成本分量。 |
| `TenantFairnessPressure` | 多租户公平压力分量。 |
| `QueuePressure` | 队列深度压力分量。 |

## Multi-Count Output

`batch-multi` 和 `realtime-multi` 会为每个 task count 创建子请求。输出按 task count 排序，summary header 会在普通 summary 前增加 `CloudletCount`：

```text
CloudletCount,Algorithm,Status,...
```

用途：

- 看算法在不同规模下的 makespan、fitness 或 realtime SLA 曲线。
- 检查某个算法是否只在大规模任务下失败。
- 对比 batch 与 realtime 的扩展性差异。

## Reading Failures

失败可能来自：

- CLI/config 错误：通常在创建输出目录前失败。
- scheduler 错误：trial CSV 中 `FAILED`，summary 为 `PARTIAL_FAILURE` 或 `FAILED`。
- exporter 错误：写文件失败会抛出，不会静默吞掉。
- realtime broker 约束：任务级拒绝不会让算法失败，而是计入 `RejectedCount`、`ResourceRejectedCount`、`TenantRejectedCount` 等指标。

排查顺序：

1. 查看控制台错误。
2. 查看 `experiment_info.txt` 确认运行参数。
3. 查看 `resolved_config.json` 确认 CLI/profile 覆盖结果。
4. 查看算法 trial CSV 的 `ErrorType` 和 `ErrorMessage`。
5. realtime 场景继续查看 SLA、reject、retry、topology 指标。

## Comparing Algorithms

常用比较原则：

- `Fitness` 是综合目标，适合快速排序。
- `Makespan` 适合看完成时间。
- `LoadBalance` 适合看 VM 利用均衡。
- `Cost` 适合看成本敏感场景。
- realtime 不能只看 makespan，还要同时看 `RejectedCount`、`TimeoutCount`、`FailedCount`、`SlaPenalty`。
- 多租户场景必须看 `TenantFairnessIndex` 和 tenant 相关 penalty。
- 拓扑场景必须看 `TopologyCost`、`AverageTopologyLatency` 和 failure-domain 指标。

不要跨不同配置、不同随机种子、不同 CloudSim Plus lock 或不同 JVM/GC 参数直接比较性能结果。

## Reproducibility Notes

为了复现实验：

1. 保存 Git commit。
2. 保存 `gradle/cloudsimplus.lock`。
3. 保存 `resolved_config.json`。
4. 保存 JDK/Gradle/Kotlin 版本。
5. 使用相同 `random.seed` 或 CLI `--seed`。
6. 使用相同 `--concurrency` 或 `--sequential` 设置。

Release manifest 会记录 CloudSim Plus ref、commit、version 和资产信息；本地实验建议同样保留 `resolved_config.json`。
