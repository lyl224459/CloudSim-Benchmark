# Performance Guide

本文档说明性能 smoke、JMH 趋势报告、baseline delta 和性能数据使用边界。
常见实验组合见 [experiment-cookbook.md](experiment-cookbook.md)，输出解读见 [results-and-analysis.md](results-and-analysis.md)。

## Performance Tasks

| Task | Purpose | Blocking |
| :--- | :--- | :--- |
| `benchmarkPerformanceSmoke` | 快速验证性能报告链路能跑通。 | 是，本地/CI 门禁可运行。 |
| `benchmarkPerformanceTrend` | 运行 JMH 并生成趋势 Markdown。 | CI 中非阻断或独立 artifact。 |
| `benchmarkPerformance` | 旧轻量 JSON benchmark。 | 兼容保留。 |

Smoke 不代表真实性能趋势，只证明代码路径可执行。

## Commands

Smoke：

```powershell
.\gradlew.bat benchmarkPerformanceSmoke --no-daemon --stacktrace
```

JMH 趋势：

```powershell
.\gradlew.bat benchmarkPerformanceTrend --no-daemon --stacktrace
```

带 baseline：

```powershell
.\gradlew.bat benchmarkPerformanceTrend `
  -PperformanceBaseline=build/reports/performance/previous-jmh-results.json `
  --no-daemon --stacktrace
```

## Outputs

```text
build/reports/performance/jmh-results.json
build/reports/performance/performance-trend.md
build/reports/realtime-performance/benchmark-smoke-results.json
build/reports/realtime-performance/benchmark-results.json
```

JMH JSON 用于机器读取，Markdown 用于 PR artifact 或人工查看。

## Covered Workloads

趋势报告覆盖：

- objective function calculate；
- PSO/WOA/GWO/HHO 固定输入调度；
- realtime `MIN_LOAD`、deadline/finish/runtime 基线、`PSO_REALTIME`、`WOA_REALTIME` 在不同 cloudlet count 下调度；
- GC allocation profiler；
- 固定 JVM/GC 参数。

## JVM And GC

性能任务固定关键参数：

```text
-Xms1g
-Xmx1g
-XX:+UseG1GC
-Dfile.encoding=UTF-8
```

普通应用运行可能使用不同 JVM 参数，因此不要把 JMH 数据与普通 CLI wall-clock 直接混比。

## CI Performance History

Workflow：

```text
.github/workflows/performance-history.yml
```

它会把 hosted runner 的 JMH JSON 和趋势报告写到 `performance-history` 分支，用于观察变化。

Hosted runner 数据的限制：

- 硬件不固定；
- CPU 争用不可控；
- 温度和频率不可控；
- OS 和虚拟化环境可能变化。

因此当前不设置性能失败阈值。真正性能门禁应放在固定硬件 runner。

## Reading The Report

关注：

- 平均耗时；
- 分配量；
- GC 指标；
- 相比 baseline 的 delta；
- 单个 benchmark 是否异常抖动。

不要只看一次运行的绝对值。趋势需要多次数据点。

## When To Run

建议运行 `benchmarkPerformanceTrend` 的变更：

- objective function；
- scheduler 内部循环；
- realtime broker 热路径；
- metrics collector；
- CloudSim Plus lock 更新；
- JVM/Gradle/Kotlin toolchain 升级。

普通文档、测试 fixture、CI workflow 变更不需要运行 JMH。

## Troubleshooting

| Symptom | Cause | Action |
| :--- | :--- | :--- |
| JMH 结果缺失 | `jmh` task 未执行或失败 | 查看 `build/reports/jmh/` 和 Gradle 日志。 |
| trend report 无 delta | 未传 `performanceBaseline` 或文件为空 | 提供历史 `jmh-results.json`。 |
| CI performance job 失败 | artifact 或 JMH 环境问题 | 下载日志和 JSON，先确认是否真实 build regression。 |
| 本地耗时明显高 | 机器负载、杀毒、Podman、后台任务 | 关闭干扰或使用固定 runner。 |
