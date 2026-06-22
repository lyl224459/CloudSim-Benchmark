# Algorithms Guide

本文档面向使用者解释已注册算法、别名、模式支持、关键参数和结果解读。开发扩展点见 [api.md](api.md)。

## Modes

| Mode | Algorithm Family | Input | Output |
| :--- | :--- | :--- | :--- |
| `batch` | Batch scheduler | 一组 cloudlets 和 VMs | cloudlet -> VM 下标数组 |
| `realtime` | Realtime scheduler | 每次新任务到达的调度上下文 | 单个 VM 下标 |
| `batch-multi` | Batch scheduler repeated by task count | 多个 cloudlet count | 每个任务规模的 summary |
| `realtime-multi` | Realtime scheduler repeated by task count | 多个 cloudlet count | 每个任务规模的 summary |

batch 算法和 realtime 算法不能跨 mode 使用。

源码位置：

- batch 调度器位于 `src/main/kotlin/scheduler/batch/`。
- realtime 调度器和实时资源/拓扑候选模型位于 `src/main/kotlin/scheduler/realtime/`。
- `src/main/kotlin/scheduler/AlgorithmRegistry.kt` 仍是统一注册入口，负责名称、别名、mode capability 和 `ALL` 展开。

## Batch Algorithms

| Name | Aliases | Type | Parameters | Notes |
| :--- | :--- | :--- | :--- | :--- |
| `RANDOM` | `RAND` | Baseline | seed | 随机分配任务到 VM，适合作为基准。 |
| `PSO` | | Metaheuristic | `population`, `maxIter` | 粒子群优化，通常收敛快。 |
| `WOA` | | Metaheuristic | `population`, `maxIter` | 鲸鱼优化，强调探索和包围更新。 |
| `GWO` | | Metaheuristic | `population`, `maxIter` | 灰狼优化，使用 alpha/beta/delta 引导。 |
| `HHO` | | Metaheuristic | `population`, `maxIter` | 哈里斯鹰优化，包含不同追捕策略。 |
| `RL` | | Reinforcement learning | seed | Q-learning 基线。 |
| `IMPROVED_RL` | `Improved-RL`, `Improved RL` | Reinforcement learning | seed | 加入任务特征、负载状态离散和改进奖励。 |

Batch scheduler 统一约束：

- cloudlet 列表不能为空。
- VM 列表不能为空。
- 返回值必须是 VM 下标，不是 VM id。
- 分配数组长度必须等于 cloudlet 数。
- 所有下标必须在 `0 until vmList.size`。

## Realtime Algorithms

| Name | Aliases | Type | Parameters | Notes |
| :--- | :--- | :--- | :--- | :--- |
| `MIN_LOAD` | `MINLOAD`, `MinLoad`, `MIN-LOAD` | Baseline | none | 按候选 VM 当前负载、队列和策略排序选择。 |
| `RANDOM` | `RAND` | Baseline | seed | 从候选 VM 中随机选择。 |
| `EDF_REALTIME` | `EDF`, `EDF-Realtime`, `EDF Realtime` | Baseline | none | 优先最小 deadline miss，再选 projected finish time 最早的 accepted candidate；无 deadline 时退化为 EFT。 |
| `LLF_REALTIME` | `LLF`, `LLF-Realtime`, `LLF Realtime` | Baseline | none | 在可按期候选中选择最小非负 slack；全部 miss 时选择 lateness 最小；无 deadline 时退化为 EFT。 |
| `EFT_REALTIME` | `EFT`, `EFT-Realtime`, `EFT Realtime` | Baseline | none | 选择 projected finish time 最早的 accepted candidate。 |
| `SRPT_REALTIME` | `SRPT`, `SRPT-Realtime`, `SRPT Realtime` | Baseline | none | 选择 incoming task 在候选 VM 上 estimated runtime 最短的 accepted candidate。 |
| `PRIORITY_DEADLINE_REALTIME` | `PRIORITY_DEADLINE`, `PRIORITY-DEADLINE`, `PRIORITY DEADLINE` | Baseline | none | 优先可抢占候选，再按 deadline miss、finish time、队列和资源压力排序。 |
| `PSO_REALTIME` | `PSO`, `PSO-Realtime`, `PSO Realtime` | Metaheuristic | `population`, `maxIter` | 达到阈值后对 accepted candidates 优化。 |
| `WOA_REALTIME` | `WOA`, `WOA-Realtime`, `WOA Realtime` | Metaheuristic | `population`, `maxIter` | 达到阈值后对 accepted candidates 优化。 |

Realtime scheduler 返回单个 VM 下标。候选为空、优化结果非法或候选被资源/拓扑/租户策略拒绝时，会走 fallback。

## Algorithm Selection

选择来源优先级：

1. CLI `--algorithms`。
2. CLI `--preset`。
3. profile `algorithms`。
4. profile `preset`。
5. algorithm library 中默认启用算法。

`--algorithms ALL` 展开当前 mode 下所有启用算法。当前 realtime 默认展开为 9 个算法：`MIN_LOAD`、`RANDOM`、5 个 deadline/finish/runtime 基线，以及 `PSO_REALTIME`、`WOA_REALTIME`。算法启用状态和默认参数来自：

- `configs/algorithms.toml`
- profile 内算法参数
- CLI 运行参数
- 代码默认值

## Parameters

| Parameter | Applies To | Meaning |
| :--- | :--- | :--- |
| `population` | PSO/WOA/GWO/HHO/Realtime PSO/WOA | 搜索种群规模。越大通常搜索更充分，耗时也更高。 |
| `maxIter` | PSO/WOA/GWO/HHO/Realtime PSO/WOA | 最大迭代次数。越大通常结果更稳定，耗时也更高。 |
| `seed` | All stochastic algorithms | 控制随机序列，保证可复现。 |
| `objectiveWeights` | Batch and optimized realtime | 控制 makespan、load balance、cost、total time 的综合权重。 |

`population` 和 `maxIter` 可以在全局 optimizer、algorithm config、batch/realtime profile 中出现。实际合并顺序见 [configuration.md](configuration.md)。

## Objective Function

Batch 和优化型 realtime 算法使用 `SchedulerObjectiveFunction`。它综合：

- makespan；
- load balance；
- cost；
- total time。

目标函数内部缓存 cloudlet length、VM MIPS、成本和归一化边界。单 VM、同质 VM 或归一化分母为 0 时返回有限值。

## Result Interpretation

不要只看单一指标：

- 纯性能对比可优先看 `Fitness` 和 `Makespan`。
- 成本敏感场景看 `Cost`。
- 多租户场景看 tenant fairness 与 SLA。
- 实时场景必须同时看 rejected、timeout、failed、retry 和 queue depth。
- 拓扑场景必须看 topology cost、latency 和 failure-domain 指标。

## Choosing Algorithms

建议：

- smoke：`RANDOM` 或 `MIN_LOAD`。
- batch 快速对比：`RANDOM,PSO,WOA`。
- batch 全量对比：`ALL`。
- realtime 快速对比：`MIN_LOAD,RANDOM`。
- realtime deadline 基线对比：`EDF_REALTIME,LLF_REALTIME,EFT_REALTIME,SRPT_REALTIME,PRIORITY_DEADLINE_REALTIME`。
- realtime 优化型对比：`MIN_LOAD,EDF_REALTIME,PSO_REALTIME,WOA_REALTIME`。
- 大任务数性能趋势：先用 `RANDOM`、`MIN_LOAD` 或 `EFT_REALTIME` 验证链路，再加入 metaheuristic。

## Adding Or Modifying Algorithms

修改算法时必须确认：

- 默认参数不变，除非明确迁移。
- 随机调用顺序不变，除非测试和文档同步。
- VM id 不被当成 VM 下标。
- 空 cloudlet 行为一致。
- realtime 算法不绕过 accepted candidate 过滤。
- `AlgorithmRegistry` capability 测试通过。

详细开发 checklist 见 [api.md](api.md) 和 [development.md](development.md)。
