# CloudSim-Benchmark

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![JDK](https://img.shields.io/badge/JDK-25+-blue.svg)](https://jdk.java.net/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-purple.svg)](https://kotlinlang.org/)
[![Gradle](https://img.shields.io/badge/Gradle-9.5.1-green.svg)](https://gradle.org/)
[![CI](https://github.com/lyl224459/CloudSim-Benchmark/actions/workflows/ci.yml/badge.svg)](https://github.com/lyl224459/CloudSim-Benchmark/actions/workflows/ci.yml)

CloudSim-Benchmark 是一个基于 CloudSim Plus 和 Kotlin 的云任务调度实验框架。项目用于对比批处理和实时调度算法，生成可复现实验结果、CSV 指标、性能趋势报告和发布包。

## Scope

- 批处理调度：`RANDOM`、`PSO`、`WOA`、`GWO`、`HHO`、`RL`、`IMPROVED_RL`。
- 实时调度：`MIN_LOAD`、`RANDOM`、`PSO_REALTIME`、`WOA_REALTIME` 等。
- 配置来源：CLI、TOML profile、algorithm library、preset 和代码默认值。
- 输出内容：trial CSV、summary CSV、resolved config、实时指标、JMH 性能趋势、release manifest。
- 构建方式：CloudSim Plus 通过 `third_party/cloudsimplus` submodule 按 `gradle/cloudsimplus.lock` 源码构建。

## Quick Start

要求：JDK 25+、Git submodule、PowerShell 或 Bash。Podman 只在容器 smoke 或镜像构建时需要。

```powershell
git submodule update --init --recursive
.\gradlew.bat fullCheck --configuration-cache
.\run.cmd --help
```

运行一个最小批处理实验：

```powershell
.\run.cmd run --mode batch --algorithms RANDOM --runs 1
```

运行 dry-run，查看最终合并配置且不创建结果目录：

```powershell
.\run.cmd run --config configs/examples/batch_test.toml --profile batch_test --dry-run
```

Unix/macOS 使用 `./run`，Windows 发布包根目录使用 `run.cmd`。

## Minimal TOML

README 只保留一个可解析的最小配置示例。完整配置说明见 [docs/configuration.md](docs/configuration.md)。

```toml
defaultProfile = "batch_smoke"

[profiles.batch_smoke]
mode = "batch"
algorithms = ["RANDOM"]
runs = 1

[profiles.batch_smoke.batch]
cloudletCount = 20
```

## Common Commands

```powershell
.\run.cmd list algorithms --mode batch
.\run.cmd list profiles --config configs/examples/single_config_example.toml
.\run.cmd config validate --config configs/examples/realtime_test.toml
.\gradlew.bat benchmarkPerformanceSmoke --no-daemon --stacktrace
.\gradlew.bat verifyReleasePackage --no-daemon --stacktrace
```

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
| [docs/troubleshooting.md](docs/troubleshooting.md) | JDK、CloudSim Plus、代理、Podman、warning audit 和本地 Git 噪音排错。 |
| [docs/glossary.md](docs/glossary.md) | 项目术语和指标名解释。 |
| [docs/wiki-sync.md](docs/wiki-sync.md) | GitHub Wiki 生成、链接重写和 CI 自动同步。 |
| [docs/realtime-metrics.md](docs/realtime-metrics.md) | 实时调度 CSV 指标、单位、趋势和字段定义。 |
| [docs/release-readiness.md](docs/release-readiness.md) | 发布前门禁、手动检查和当前质量基线。 |

## Outputs

默认结果写入 `runs/`。每次真实运行会保存 resolved config、trial CSV、summary CSV 和实验信息。实时调度 CSV 由 `RealtimeMetricSchema` 统一生成表头和指标值，字段说明见 [docs/realtime-metrics.md](docs/realtime-metrics.md)。

性能趋势任务输出：

- `build/reports/performance/jmh-results.json`
- `build/reports/performance/performance-trend.md`

## Container

容器镜像只组装运行时上下文，不在 Podman 构建中编译源码：

```powershell
.\gradlew.bat prepareContainerImageContext verifyContainerBuildContext
podman build -t cloudsim-benchmark -f build/container-context/Containerfile build/container-context
podman run --rm -v "${PWD}\runs:/app/runs" cloudsim-benchmark --help
```

镜像默认使用 UID/GID `10001` 的非 root 用户，唯一持久写目录为 `/app/runs`。

## Quality Gates

常用本地门禁：

```powershell
.\gradlew.bat ktlintCheck detekt --no-daemon --stacktrace
.\gradlew.bat test jacocoTestReport jacocoTestCoverageVerification --no-daemon --stacktrace
.\gradlew.bat buildSrc:check --no-daemon --stacktrace
pwsh -File scripts/run-build-warning-audit.ps1
```

完整门禁、release package、container、supply-chain 和 warning audit 说明见 [docs/development.md](docs/development.md) 与 [docs/release-readiness.md](docs/release-readiness.md)。

## License

项目代码使用 MIT License。运行时依赖包含 CloudSim Plus，CloudSim Plus 为 GPLv3 依赖；发布和许可证策略见 [docs/release-readiness.md](docs/release-readiness.md) 与 `gradle/allowed-licenses.json`。
