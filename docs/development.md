# Development Guide

本文档是维护者手册，覆盖本地开发、质量门禁、常见变更流程和 CI/供应链约束。API 和扩展点地图见 [api.md](api.md)，发布前完整门禁见 [release-readiness.md](release-readiness.md)。

## Working Principles

- 小 PR、单主题、全 CI。
- 不把依赖更新、业务重构、文档、release 准备混在一个 PR。
- 不为行数拆分生产代码；优先拆有新增需求、覆盖困难或真实职责混杂的类。
- CLI、TOML、CSV 表头、算法注册名、随机种子和 release 包语义默认稳定。
- `internal` seam 可以为测试增加，但不要把测试专用设计暴露成 public API。

## Branch Flow

- 普通维护分支使用 `codex/` 前缀。
- `dev` 和 `main` 保持同步；普通维护 PR 目标分支为 `main`。
- 本地有未提交改动时先收敛，再开始下一轮开发。
- CI 未全绿前不继续叠加新重构。
- Dependabot bulk PR 不直接合并；按生态拆小 PR。

推荐流程：

```powershell
git fetch origin
git switch dev
git merge --ff-only origin/dev
git switch -c codex/my-change
```

完成后：

```powershell
git status -sb
git diff --check
```

## Local Setup

第一次 checkout：

```powershell
git submodule update --init --recursive
.\gradlew.bat fullCheck --configuration-cache
```

网络受限时：

```powershell
.\gradlew.bat verifyCloudSimPlusLock '-Dorg.gradle.project.cloudsimplus.gitProxy=http://host:port'
```

离线模式：

```powershell
.\gradlew.bat verifyCloudSimPlusLock -Pcloudsimplus.offline=true
```

## Fast Feedback Commands

按变更类型选择最小验证，再跑完整门禁。

| Change | First Command |
| :--- | :--- |
| CLI parser/resolver | `.\gradlew.bat test --tests "cli.*" --no-daemon --stacktrace` |
| Config parser/validator | `.\gradlew.bat test --tests "config.*" --no-daemon --stacktrace` |
| Batch scheduler | `.\gradlew.bat test --tests "scheduler.*" --no-daemon --stacktrace` |
| Realtime broker | `.\gradlew.bat test --tests "broker.*" --no-daemon --stacktrace` |
| Metrics/export | `.\gradlew.bat test --tests "datacenter.*Metric*" --no-daemon --stacktrace` |
| buildSrc | `.\gradlew.bat buildSrc:check buildSrc:jacocoTestReport --no-daemon --stacktrace` |
| Docs | `.\gradlew.bat test --tests "config.DocumentationDriftTest" --no-daemon --stacktrace` |
| Wiki | `python scripts/build-wiki.py` |

## Standard Gates

常用门禁：

```powershell
.\gradlew.bat ktlintCheck detekt --no-daemon --stacktrace
.\gradlew.bat test jacocoTestReport jacocoTestCoverageVerification --no-daemon --stacktrace
.\gradlew.bat buildSrc:check --no-daemon --stacktrace
pwsh -File scripts/run-build-warning-audit.ps1
```

完整门禁：

```powershell
.\gradlew.bat fullCheck verifyReleasePackage benchmarkPerformanceSmoke --no-daemon --stacktrace --configuration-cache
.\gradlew.bat generateSupplyChainReports checkLicense --no-daemon --stacktrace --no-configuration-cache
```

测试入口变化后：

```powershell
.\gradlew.bat updateJUnitTestInventory verifyJUnitTestInventory verifyJUnitTestSignatures --no-daemon --stacktrace
```

Container 相关变化：

```powershell
.\gradlew.bat prepareContainerImageContext verifyContainerBuildContext containerImageSmoke --no-daemon --stacktrace
```

## Documentation Rules

以下内容由测试锁定：

- README 中的 `toml` 代码块必须是可独立解析配置。
- `docs/realtime-metrics.md` 必须与 realtime metric schema 渲染结果一致。
- `docs/release-readiness.md` 必须记录真实存在的 Gradle 验证任务。
- Detekt baseline 必须保持不存在。
- JUnit 测试入口必须与 `gradle/junit-test-inventory.lock` 一致。

新增文档时：

1. 优先把 README 作为入口页，不把长篇细节塞回 README。
2. 详细说明放到 `docs/`。
3. 命令必须使用真实 Gradle task 或真实 CLI command。
4. 示例配置优先引用 `configs/` 文件；如果写 `toml` code fence，确保可解析。
5. API 变更同步 [api.md](api.md)。

## Adding A CLI Option

Checklist：

1. 在 `CliParser` 中增加 option descriptor 或子命令解析。
2. 在 resolver/override 阶段把值映射到 `ResolvedExperimentConfig`。
3. 更新 dry-run 文本和 JSON 输出。
4. 更新 [cli.md](cli.md)。
5. 增加测试：
   - `--key value`；
   - `--key=value`；
   - 缺值；
   - inline flag 错误；
   - 与已有参数的互斥关系；
   - dry-run snapshot。
6. 更新 JUnit inventory。

不要在 `CommandExecutor` 中直接解析新参数；解析逻辑必须留在 `CliParser` 和 resolver 层。

## Adding A Config Field

Checklist：

1. 更新 TOML DTO。
2. 更新 domain config。
3. 更新 mapper/parser 和默认值。
4. 更新 validator rule 和错误路径。
5. 更新示例配置与 [configuration.md](configuration.md)。
6. 增加解析、merge、validation snapshot 和 dry-run 测试。
7. 如果字段影响 CSV 或调度语义，补对应 runner/broker/scheduler 测试。

保持错误消息关键词稳定。字段路径变化会破坏用户脚本和 snapshot。

## Adding A Batch Algorithm

Checklist：

1. 新增 `Scheduler` subclass。
2. `allocate()` 返回 VM 下标数组，不返回 VM id。
3. 在 `AlgorithmRegistry` 注册 `BatchAlgorithmDefinition`。
4. 如支持优化参数，设置 `supportsPopulation` / `supportsMaxIter`。
5. 更新配置枚举和文档。
6. 测试固定 seed、单 VM、同质 VM、空任务拒绝、VM 下标范围、finite fitness。
7. 确认 `ComparisonRunner` 成功、失败和 CSV 路径覆盖。

随机调用顺序是行为的一部分，除非明确迁移，不要无意改变。

## Adding A Realtime Algorithm

Checklist：

1. 实现 `RealtimeScheduler`，优先继承 `RealtimeSchedulerBase`。
2. 只从 accepted candidates 中选择 VM。
3. 优化器返回候选列表下标时，必须映射回真实 VM 下标。
4. 候选为空或结果异常时走 fallback。
5. 在 `AlgorithmRegistry` 注册 `RealtimeAlgorithmDefinition`。
6. 补测试：
   - accepted candidate 过滤；
   - non-accepting VM；
   - 非连续 VM id；
   - 空候选；
   - invalid optimized index；
   - queue policy 和 topology policy 分支。

Realtime scheduler 不应绕过 broker 的资源、拓扑、租户和容量约束。

## Adding A Realtime Metric

Checklist：

1. 在 `RealtimeMetricKey` 加 key。
2. 在对应 metric definition 文件加定义。
3. 在 `RealtimeMetricProjection` 或 collector 中提供值。
4. 更新 `RealtimeMetricsCollector`、broker snapshot 或 metric source。
5. 重新生成 `docs/realtime-metrics.md`。
6. 更新 schema/header/docs snapshot。
7. 验证失败 trial、空指标和 summary 统计。

CSV 表头顺序是稳定接口；新增字段默认追加到对应分类的末尾，除非明确做 migration。

## Broker Changes

Broker 是高风险区域。修改前先判断影响哪个层：

| Area | Files |
| :--- | :--- |
| Event routing | `RealtimeBrokerEventRouting.kt`、`RealtimeBroker.kt` |
| Admission/rejection | `RealtimeControllers.kt`、`RealtimeVmSelectionFacade.kt` |
| Submission/retry | `RealtimeSubmissionService.kt`、`RealtimeTaskInterruptionController.kt` |
| Runtime failure/timeout | `RealtimeRuntimeEventPlanner.kt`、`RealtimeRuntimeEventController.kt` |
| Metrics/read model | `RealtimeBrokerReadModel.kt`、`RealtimeBrokerReadViews.kt`、`RealtimeBrokerMetrics.kt` |

必须覆盖：

- resource/capacity/tenant rejection；
- retry success/failure；
- permanent failure；
- timeout fail/cancel/retry/degrade；
- runtime failure；
- preemption；
- topology accounting；
- active VM index accounting。

## Runner And Export Changes

Runner 变更要保持：

- selected algorithms 排序稳定；
- seed 递增稳定；
- partial failure 仍输出失败 summary；
- `CancellationException` 原样传播；
- CSV disabled 不写 CSV；
- writer/exporter 异常不吞掉；
- multi-count child output context 稳定。

推荐先增加 fake service 测试，再用小规模 CloudSim 集成测试验证真实路径。

## buildSrc Changes

buildSrc 规则：

- 新 Gradle 逻辑优先写 task class，不写大型 script closure。
- task inputs/outputs 必须声明。
- 进程执行、网络访问和文件写入只放在 task action。
- 默认 locked CloudSim Plus path 必须 no-network、cache-safe。
- TestKit 要覆盖 task action、错误诊断和 configuration cache。

CloudSim Plus 相关变化必须覆盖：

- `.git` 目录和 `gitdir:` 文件；
- detached HEAD；
- missing submodule；
- broken checkout；
- tag 不存在；
- proxy fallback；
- timeout；
- lock drift；
- POM version drift；
- classpath artifact 漂移。

## Dependency Update Flow

Dependabot 或手工依赖升级按类别拆 PR：

- Kotlin toolchain；
- kotlinx runtime；
- logging；
- JUnit；
- Mockito；
- AssertJ；
- GitHub Actions；
- Docker actions/base image。

每个 PR 只改对应依赖和 `gradle/verification-metadata.xml`。必须运行：

```powershell
.\gradlew.bat generateSupplyChainReports checkLicense --no-daemon --stacktrace --no-configuration-cache
.\gradlew.bat fullCheck verifyReleasePackage benchmarkPerformanceSmoke --no-daemon --stacktrace --configuration-cache
```

不要在依赖 PR 中修改 CloudSim Plus lock、CLI、CSV、算法行为或版本 tag。

供应链细节、许可证和 attestation 见 [supply-chain.md](supply-chain.md)。

## Coverage And Static Analysis

项目使用 JaCoCo、detekt、ktlint 和类级覆盖率门禁。补覆盖时优先真实边界输入，不堆 mock：

- CLI 解析错误和组合约束。
- config merge 和 validator 错误路径。
- scheduler 空候选、fallback 和非连续 VM id。
- broker timeout/retry/failure/topology。
- exporter 输出失败和 CSV disabled。
- buildSrc task action。

Detekt baseline 已清零。新增 `@Suppress` 必须带理由：

- 兼容 facade：说明稳定 API 或兼容边界。
- 测试长方法：优先拆 fixture/helper。
- 真实设计债：后续有功能改动时拆，不为行数硬拆。

## Build Warning Audit

`scripts/run-build-warning-audit.ps1` 会隔离审计：

- `compileKotlin`
- `detekt`
- `ktlintCheck`
- CloudSim Plus 源码构建

只允许精确白名单 warning。未知 deprecation、native access、Unsafe、JVM target fallback 或 Maven/Jansi/Guava warning 都应失败。不要用全局 suppress 隐藏日志。

## Supply Chain

供应链门禁包含：

- Gradle dependency verification；
- OSV scan；
- runtime CycloneDX SBOM；
- license policy；
- release manifest；
- GitHub Actions Node 24 policy；
- actionlint；
- release asset 和 GHCR image attestation。

CloudSim Plus GPLv3 是已接受的 runtime 许可证。新增 runtime 依赖时必须确认许可证策略和 SBOM 输出。

## Performance Work

`benchmarkPerformanceSmoke` 只验证链路能跑通。`benchmarkPerformanceTrend` 使用 JMH 输出：

- `build/reports/performance/jmh-results.json`
- `build/reports/performance/performance-trend.md`

Hosted runner 数据只用于观察。性能门禁应放在固定硬件 runner，不应和普通 PR 阻断混在一起。

性能报告格式和 baseline delta 见 [performance.md](performance.md)。

## Container Work

容器镜像不编译源码。流程：

1. Gradle 构建 fatJar。
2. `prepareContainerImageContext` 生成最小上下文。
3. `verifyContainerBuildContext` 验证大小、provenance、checksum 和禁止文件。
4. Docker 只组装 JRE 运行镜像。

镜像必须：

- 非 root 运行；
- 只写 `/app/runs`；
- 不包含 Git、Gradle、源码或构建缓存；
- `--help` smoke 通过。

容器上下文、非 root 运行和 smoke 排错见 [container.md](container.md)。

## More References

- 测试体系： [testing.md](testing.md)
- 结果解读： [results-and-analysis.md](results-and-analysis.md)
- CI workflow： [ci-workflows.md](ci-workflows.md)
- build logic： [build-logic.md](build-logic.md)
- release package： [release-package.md](release-package.md)
- wiki sync： [wiki-sync.md](wiki-sync.md)
- 术语表： [glossary.md](glossary.md)
- 常见问题： [troubleshooting.md](troubleshooting.md)

## Troubleshooting

常见问题：

- CloudSim Plus submodule 缺失：运行 `git submodule update --init --recursive`。
- 受限网络：设置 `-Dorg.gradle.project.cloudsimplus.gitProxy=http://host:port`。
- JUnit inventory 失败：确认是否有意新增/删除测试，再运行 `updateJUnitTestInventory`。
- README TOML drift 失败：README 的 `toml` code fence 必须是完整可解析配置。
- 本地 Git 报 global excludesfile 权限错误：修复本机 `core.excludesfile` 指向文件的权限。
