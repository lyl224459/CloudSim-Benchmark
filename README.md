# CloudSim-Benchmark

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![JDK](https://img.shields.io/badge/JDK-25+-blue.svg)](https://jdk.java.net/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-purple.svg)](https://kotlinlang.org/)
[![Gradle](https://img.shields.io/badge/Gradle-9.6.0-green.svg)](https://gradle.org/)
[![CI](https://github.com/lyl224459/CloudSim-Benchmark/actions/workflows/ci.yml/badge.svg)](https://github.com/lyl224459/CloudSim-Benchmark/actions/workflows/ci.yml)

CloudSim-Benchmark 是一个基于 CloudSim Plus 和 Kotlin 的云任务调度实验平台。它面向批处理调度、实时调度、算法对比、配置复现和结果分析，提供 CLI、TOML profile、CSV 指标、JMH 性能趋势、发布包和容器镜像。

这个仓库适合用来：

- 对比 `RANDOM`、`PSO`、`WOA`、`GWO`、`HHO`、`RL`、`IMPROVED_RL` 等批处理算法。
- 验证 `MIN_LOAD`、`RANDOM`、`EDF_REALTIME`、`LLF_REALTIME`、`EFT_REALTIME`、`SRPT_REALTIME`、`PRIORITY_DEADLINE_REALTIME`、`PSO_REALTIME`、`WOA_REALTIME` 等实时调度算法。
- 通过 TOML profile 固化实验参数，并用 `--dry-run` 保存可复现实验配置。
- 生成 trial CSV、summary CSV、resolved config、实时指标、性能趋势和 release manifest。
- 在 CI 中验证 CloudSim Plus 锁定版本、构建逻辑、许可证、SBOM、attestation 和发布包。

## What Is Inside

| Area | Description |
| :--- | :--- |
| Simulation core | 基于 CloudSim Plus 的 batch/realtime runner、broker、scheduler 和指标采集。 |
| Algorithms | batch/realtime 算法注册、别名解析、参数配置和 preset 选择。 |
| Configuration | CLI 参数、TOML profile、algorithm library、preset 和默认值合并。 |
| Outputs | `runs/` 下的实验元数据、resolved config、trial CSV、summary CSV 和 comparison CSV。 |
| Build and release | Gradle 任务、CloudSim Plus 源码锁定构建、release package、container context、SBOM。 |
| Documentation | CLI、配置、算法、实时调度、结果分析、性能、供应链和排错文档。 |

## Requirements

- JDK 25 或更高版本。
- Apache Maven 3.9+（构建时若缺失 CloudSim Plus JAR 会自动触发子构建，首次需能访问 `mvn`）。
- Git submodule 支持。
- PowerShell 7+ 或 Bash。
- Podman 可选，仅用于容器构建、镜像 smoke 和本地容器运行。

CloudSim Plus 由 `third_party/cloudsimplus` submodule 提供源码。普通构建默认使用 `gradle/cloudsimplus.lock` 中锁定的 ref、commit 和 version；默认锁定模式只验证 checkout 状态，不主动更新上游。

## Quick Start

首次 checkout 后初始化 submodule：

```powershell
git submodule update --init --recursive
```

> **国内用户建议**：配置 Gradle 中国镜像源可大幅提升首次构建速度。参见 [docs/troubleshooting.md](docs/troubleshooting.md) 中「首次构建与环境准备」章节的步骤 2。

构建项目（首次会自动编译 CloudSim Plus 源码，需 Maven）：

```powershell
.\gradlew.bat build --no-configuration-cache
```

运行完整本地门禁并查看 CLI 帮助：

```powershell
.\gradlew.bat fullCheck --configuration-cache
.\run.cmd --help
```

Unix/macOS 使用：

```bash
./gradlew fullCheck --configuration-cache
./run --help
```

`run.cmd` 和 `./run` 会在 JAR 缺失时自动构建 fatJar。只构建运行包时可执行：

```powershell
.\run.cmd build
```

## First Experiments

批处理 smoke：

```powershell
.\run.cmd run --mode batch --algorithms RANDOM --runs 1
```

实时 smoke：

```powershell
.\run.cmd run --mode realtime --algorithms MIN_LOAD --runs 1
```

使用配置 profile 运行：

```powershell
.\run.cmd run --config configs/examples/single_config_example.toml --profile batch_small
```

正式运行前建议先 dry-run，确认最终合并配置且不创建结果目录：

```powershell
.\run.cmd run --config configs/examples/batch_test.toml --profile batch_test --dry-run
```

查看可用算法、profile 和配置合法性：

```powershell
.\run.cmd list algorithms --mode batch
.\run.cmd list algorithms --mode realtime
.\run.cmd list profiles --config configs/examples/single_config_example.toml
.\run.cmd config validate --config configs/examples/realtime_test.toml
```

## Minimal TOML

README 只保留一个可解析的最小配置示例。完整字段、默认值和覆盖顺序见 [docs/configuration.md](docs/configuration.md) 与 [docs/config-reference.md](docs/config-reference.md)。

```toml
defaultProfile = "batch_smoke"

[profiles.batch_smoke]
mode = "batch"
algorithms = ["RANDOM"]
runs = 1

[profiles.batch_smoke.batch]
cloudletCount = 20
```

## Repository Layout

```text
configs/                 默认配置、示例 profile 和实验配置
data/                    示例数据和 Google trace 说明
docs/                    项目手册和维护文档
scripts/                 跨平台运行脚本和构建 warning audit
src/main/kotlin/         CLI、配置、runner、scheduler、metrics 和工具代码
src/test/kotlin/         单元测试、文档漂移测试、CLI 和调度链路测试
buildSrc/                自定义 Gradle 任务、CloudSim Plus 构建和 release 校验
third_party/cloudsimplus CloudSim Plus 锁定源码 submodule
Containerfile            最小运行镜像定义
```

## Outputs

默认结果写入 `runs/`，可通过 `--output` 或 TOML 输出配置覆盖。一次真实运行通常会生成：

- `experiment_info.txt`: 运行模式、任务数、算法、seed、trial 数等元数据。
- `resolved_config.json`: 最终合并配置快照。
- `<Algorithm>.csv`: 单算法 trial 级结果。
- `summary_avg.csv`: 算法 summary 均值和标准差。
- `batch_comparison_*.csv` 或 `realtime_comparison_*.csv`: 对比导出结果。

实时调度 CSV 由 `RealtimeMetricSchema` 统一生成表头和指标值，字段说明见 [docs/realtime-metrics.md](docs/realtime-metrics.md)。结果目录结构和 CSV 解读见 [docs/results-and-analysis.md](docs/results-and-analysis.md)。

性能趋势任务输出：

- `build/reports/performance/jmh-results.json`
- `build/reports/performance/performance-trend.md`

## Container

本地容器镜像只组装 Gradle 已生成的运行时上下文，不在 Podman build 中编译源码：

```powershell
.\gradlew.bat prepareContainerImageContext verifyContainerBuildContext
podman build -t cloudsim-benchmark -f build/container-context/Containerfile build/container-context
podman run --rm -v "${PWD}\runs:/app/runs" cloudsim-benchmark --help
```

发布镜像位于 GHCR：

```powershell
docker pull ghcr.io/lyl224459/cloudsim-benchmark:1.2.1
docker pull ghcr.io/lyl224459/cloudsim-benchmark:latest
```

如果需要固定不可变镜像摘要，使用 digest 引用：

```powershell
docker pull ghcr.io/lyl224459/cloudsim-benchmark@sha256:<image-digest>
```

不要把 GHCR 页面上可能出现的 `sha256-<digest>` attestation fallback tag 当作运行镜像版本。容器挂载、非 root 用户和 attestation 说明见 [docs/container.md](docs/container.md)。

## Common Gradle Tasks

| Task | Purpose |
| :--- | :--- |
| `fullCheck` | 执行主线本地校验：check、test、fatJar、示例配置和 CLI smoke。 |
| `benchmarkPerformanceSmoke` | 快速验证性能报告链路。 |
| `benchmarkPerformanceTrend` | 运行 JMH 并生成性能趋势 Markdown。 |
| `verifyReleasePackage` | 校验 release 包内容、脚本和运行时文件。 |
| `prepareContainerImageContext` | 生成最小容器构建上下文。 |
| `verifyContainerBuildContext` | 校验容器上下文大小、provenance 和禁止文件。 |
| `containerImageSmoke` | 构建容器镜像并运行 `--help` smoke。 |
| `generateSupplyChainReports` | 生成 runtime SBOM 和许可证报告。 |

常用本地质量门禁：

```powershell
.\gradlew.bat ktlintCheck detekt --no-daemon --stacktrace
.\gradlew.bat test jacocoTestReport jacocoTestCoverageVerification --no-daemon --stacktrace
.\gradlew.bat buildSrc:check --no-daemon --stacktrace
pwsh -File scripts/run-build-warning-audit.ps1
```

完整门禁、release package、container、supply-chain 和 warning audit 说明见 [docs/development.md](docs/development.md) 与 [docs/release-readiness.md](docs/release-readiness.md)。

## Documentation

| Document | Purpose |
| :--- | :--- |
| [docs/getting-started.md](docs/getting-started.md) | 本地构建、代理、CloudSim Plus lock、常用验证命令。 |
| [docs/cli.md](docs/cli.md) | `run`、`list`、`config` 子命令和常用参数。 |
| [docs/configuration.md](docs/configuration.md) | TOML profile、algorithm、preset、覆盖顺序和示例文件。 |
| [docs/config-reference.md](docs/config-reference.md) | TOML 字段、默认值、合法值和维护注意事项。 |
| [docs/experiment-cookbook.md](docs/experiment-cookbook.md) | 常见实验场景的可执行命令和配置选择。 |
| [docs/architecture.md](docs/architecture.md) | CLI、config、runner、scheduler、broker、metrics 和构建链路。 |
| [docs/api.md](docs/api.md) | 稳定接口、主要 internal 扩展点和新增算法/指标/配置的改动清单。 |
| [docs/development.md](docs/development.md) | 本地质量门禁、文档漂移、供应链、CI 和 PR 约束。 |
| [docs/ci-workflows.md](docs/ci-workflows.md) | GitHub Actions workflow、job、artifact 和失败处理。 |
| [docs/build-logic.md](docs/build-logic.md) | buildSrc task、CloudSim Plus 源码构建、configuration cache 和 warning audit。 |
| [docs/release-package.md](docs/release-package.md) | release assets、manifest、脚本、SBOM、许可证和 attestation。 |
| [docs/results-and-analysis.md](docs/results-and-analysis.md) | 输出目录、trial/summary CSV、失败行和结果解读。 |
| [docs/algorithms.md](docs/algorithms.md) | batch/realtime 算法、别名、参数和选择规则。 |
| [docs/realtime-scheduling.md](docs/realtime-scheduling.md) | 实时到达、队列、SLA、重试、抢占、租户、拓扑和资源模型。 |
| [docs/google-trace.md](docs/google-trace.md) | Google trace 输入格式、字段映射、fallback 和排错。 |
| [docs/container.md](docs/container.md) | 最小容器上下文、非 root 镜像、挂载和 smoke 验证。 |
| [docs/supply-chain.md](docs/supply-chain.md) | dependency verification、OSV、许可证、SBOM、attestation 和 Dependabot。 |
| [docs/performance.md](docs/performance.md) | smoke、JMH 趋势、baseline delta 和性能结果使用边界。 |
| [docs/testing.md](docs/testing.md) | JUnit inventory、JaCoCo、TestKit、文档漂移和测试新增规则。 |
| [docs/troubleshooting.md](docs/troubleshooting.md) | JDK、Maven 安装、CloudSim Plus、代理、Podman、warning audit、首次构建流程和本地 Git 噪音排错。 |
| [docs/glossary.md](docs/glossary.md) | 项目术语和指标名解释。 |
| [docs/wiki-sync.md](docs/wiki-sync.md) | GitHub Wiki 生成、链接重写和 CI 自动同步。 |
| [docs/realtime-metrics.md](docs/realtime-metrics.md) | 实时调度 CSV 指标、单位、趋势和字段定义。 |
| [docs/release-readiness.md](docs/release-readiness.md) | 发布前门禁、手动检查和当前质量基线。 |

## Troubleshooting

- JDK 或 Gradle 版本问题：先看 [docs/getting-started.md](docs/getting-started.md) 和 [docs/troubleshooting.md](docs/troubleshooting.md)。
- 配置无法解析：运行 `.\run.cmd config validate --config <file>`，再对照 [docs/config-reference.md](docs/config-reference.md)。
- 算法名不被接受：运行 `.\run.cmd list algorithms --mode batch` 或 `--mode realtime`。
- 实验结果异常：优先检查 `resolved_config.json`、`summary_avg.csv` 和失败 trial 的 `ErrorType`/`ErrorMessage`。
- 容器镜像拉取版本不对：使用 `:1.2.1`、`:latest` 或 `@sha256:<digest>`，不要使用 `:sha256-...`。

## License

项目代码使用 MIT License。运行时依赖包含 CloudSim Plus，CloudSim Plus 为 GPLv3 依赖；发布和许可证策略见 [docs/release-readiness.md](docs/release-readiness.md) 与 `gradle/allowed-licenses.json`。
