# Glossary

本文档解释项目中常见术语、缩写和指标名。

## CloudSim Terms

| Term | Meaning |
| :--- | :--- |
| Cloudlet | CloudSim Plus 中的任务单元。 |
| VM | 虚拟机，任务最终绑定到 VM。 |
| VM id | CloudSim 对象 id，不保证从 0 连续。 |
| VM index | `vmList` 中的数组下标，调度器内部必须返回这个值。 |
| MIPS | VM 计算能力。 |
| PEs | Processing elements，任务或 VM 的处理单元数。 |
| DatacenterBroker | CloudSim broker，负责提交 VM/cloudlet 和处理事件。 |

## Scheduling Terms

| Term | Meaning |
| :--- | :--- |
| Batch scheduling | 一次性对全部任务做静态分配。 |
| Realtime scheduling | 每个任务到达时做增量调度。 |
| Makespan | 所有任务完成的最大时间。 |
| Load balance | VM 负载均衡指标。 |
| Cost | 成本估算。 |
| Total time | 全部任务执行时间总和。 |
| Fitness | 综合目标函数值，默认越小越好。 |
| Allocation | cloudlet -> VM index 的分配数组。 |
| Candidate VM | 当前任务可考虑的 VM。 |
| Accepted candidate | 通过资源、容量、拓扑和租户约束的候选 VM。 |
| Fallback | 优化结果不可用时使用的保守选择路径。 |

## Algorithm Terms

| Term | Meaning |
| :--- | :--- |
| PSO | Particle Swarm Optimization，粒子群优化。 |
| WOA | Whale Optimization Algorithm，鲸鱼优化。 |
| GWO | Grey Wolf Optimizer，灰狼优化。 |
| HHO | Harris Hawks Optimization，哈里斯鹰优化。 |
| RL | Q-learning baseline。 |
| Improved-RL | 带任务特征、状态离散和改进奖励的 RL scheduler。 |
| Population | metaheuristic 种群规模。 |
| Max iteration | metaheuristic 最大迭代次数。 |

## Realtime Terms

| Term | Meaning |
| :--- | :--- |
| SLA | Service Level Agreement，通常与 deadline、timeout 和 penalty 相关。 |
| Deadline factor | 根据任务估计运行时间生成 deadline 的系数。 |
| Timeout action | 超时后的 fail/retry/cancel/degrade 策略。 |
| Retry limit | 单任务最大重试次数。 |
| Checkpoint | 失败或抢占时可恢复的已完成进度。 |
| Preemption | 高优先级任务抢占低优先级任务资源。 |
| Autoscaling | 根据队列压力动态创建或回收 VM。 |
| Cold start | 动态 VM 或镜像准备带来的启动延迟。 |

## Tenant Terms

| Term | Meaning |
| :--- | :--- |
| Tenant | 多租户调度中的租户。 |
| Quota | 租户可用配额。 |
| Tenant weight | weighted fair 调度权重。 |
| Jain index | 衡量公平性的指数，越接近 1 越公平。 |
| DRF | Dominant Resource Fairness，主导资源公平。 |
| Tenant SLA penalty | 按租户累计的 SLA 惩罚。 |
| Tenant budget | 租户成本预算。 |

## Topology Terms

| Term | Meaning |
| :--- | :--- |
| Region | 区域，拓扑最高层级。 |
| Rack | 机架，region 内部层级。 |
| Host | 物理宿主机。 |
| Failure domain | 故障域，可以是 host/rack/region。 |
| Data locality | 任务数据位置与 VM 位置的接近程度。 |
| Image cache | VM/host 上已缓存镜像，影响冷启动。 |
| Cross-rack latency | 跨 rack 放置带来的延迟。 |
| Cross-region cost | 跨 region 放置带来的成本。 |

## Output Terms

| Term | Meaning |
| :--- | :--- |
| Trial CSV | 单个算法每次 run 的结果文件。 |
| Summary CSV | 每个算法聚合后的均值和标准差。 |
| `summary_avg.csv` | 当前实验目录内固定名称的 summary 文件。 |
| `resolved_config.json` | CLI/profile/default 合并后的配置快照。 |
| `experiment_info.txt` | 人类可读实验元数据。 |
| Release manifest | 发布资产、CloudSim Plus metadata 和 checksum 清单。 |
| SBOM | Software Bill of Materials，软件物料清单。 |
| Attestation | GitHub/Sigstore 生成的 provenance 或 SBOM 证明。 |

## Build Terms

| Term | Meaning |
| :--- | :--- |
| Locked CloudSim Plus mode | 使用 `gradle/cloudsimplus.lock` 验证本地 submodule，不访问网络。 |
| Mutable CloudSim Plus mode | 显式 ref 或 auto-update，可 fetch/checkout。 |
| Sanitized repo | 去除 manifest `Class-Path` 后的本地 Maven repo。 |
| Configuration cache | Gradle 配置缓存。 |
| TestKit | Gradle 用于测试插件和 task 的功能测试工具。 |
| Warning audit | 分来源检查构建日志，阻断未知 warning。 |
