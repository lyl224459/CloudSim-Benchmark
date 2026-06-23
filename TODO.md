# TODO

本文件记录当前项目后续值得完善的方向，优先服务后续发版和 realtime 模式稳定演进。

## 高优先级

- 增加 realtime 长跑压测和 soak tests，覆盖 rescheduling、autoscaling、preemption、deadline admission、event observation 同时开启的场景，重点验证周期事件不会无限续约、任务生命周期能自然收敛。
- 补充实验结果分析工具，提供 SLA miss root-cause、单个 Cloudlet 生命周期追踪、候选 VM score 对比、autoscaling 成本收益分析等脚本。
- 校准资源和拓扑模型参数，基于真实 trace 或小规模实测数据调整 CPU overcommit、网络传输、镜像拉取、noisy-neighbor、deadline penalty 等默认权重。
- 建立 realtime 算法 benchmark 矩阵，系统比较 EDF、LLF、EFT、SRPT、PRIORITY_DEADLINE、PSO_REALTIME、WOA_REALTIME 在不同 workload 和 deadline 压力下的效果。

## 中优先级

- 增加 SQLite 或 Parquet 输出选项，避免大规模事件观测数据全部依赖 CSV。
- 扩展 trace 输入生态，补充 Alibaba、Kubernetes pod/event 或其他生产集群 trace 的映射。
- 治理 realtime 配置复杂度，提供面向场景的 profile 模板，例如 low-latency、cost-aware、failure-heavy、multi-tenant。
- 完善 CI 和 release 链路，加入示例配置全量 validate、smoke run 矩阵、Docker image tag 与项目版本一致性检查。

## 低优先级

- 补充从零运行 realtime 实验、阅读 `realtime_events.csv`、复现实验结果的短教程。
- 对候选评分、事件记录、VM 生命周期查询等热点做性能 profiling，再按数据决定是否优化。
- 丰富结果可视化，提供 p95/p99、SLA violation、成本、队列深度、scale out/in 时间线图表。
