# Build Logic Guide

本文档说明 Gradle/buildSrc 任务、CloudSim Plus 源码构建、configuration cache、warning audit 和容器/release 构建逻辑。

## buildSrc Responsibilities

buildSrc 放置自定义 Gradle task class 和纯 helper。原则：

- task 声明 inputs/outputs；
- 进程执行在 task action；
- 默认 locked path 不访问网络；
- TestKit 覆盖 task action；
- 错误诊断必须明确。

## CloudSim Plus Tasks

| Task | Type | Purpose |
| :--- | :--- | :--- |
| `verifyLockedCloudSimPlusSource` | cacheable | 验证 submodule checkout、gitlink、POM version 和 lock。 |
| `prepareMutableCloudSimPlusSource` | always-run | 显式 ref/auto-update 模式下 fetch/checkout。 |
| `prepareCloudSimPlusSource` | lifecycle | 根据模式选择 locked 或 mutable path。 |
| `verifyCloudSimPlusLock` | lifecycle | 验证 CloudSim Plus lock 一致性。 |
| `updateCloudSimPlusLock` | explicit maintenance | 写入当前实际 ref/commit/version。 |
| `buildCloudSimPlusFromSource` | task class | Maven package 并 stage 锁定版本 JAR/POM。 |
| `sanitizeCloudSimPlusJarManifest` | task class | 去除 JAR manifest `Class-Path` 并复制到 sanitized repo。 |
| `verifyCloudSimPlusSourceBuild` | task class | 确认 classpath 只使用源码构建 artifact。 |

## CloudSim Plus Properties

| Property | Default | Meaning |
| :--- | :--- | :--- |
| `cloudsimplus.autoUpdate` | `false` | true 时解析最新 semver release。 |
| `cloudsimplus.ref` | unset | 显式 tag/commit/ref。 |
| `cloudsimplus.offline` | `false` | 跳过网络，使用当前 checkout。 |
| `cloudsimplus.gitProxy` | unset | Git fetch/checkout 代理。 |
| `cloudsimplus.gitTimeoutSeconds` | configured default | Git 命令超时。 |
| `cloudsimplus.mavenCacheDir` | `~/.m2/repository` | Maven dependency cache。 |

优先级：

1. 显式 `cloudsimplus.ref`。
2. `cloudsimplus.autoUpdate=true`。
3. 默认 `gradle/cloudsimplus.lock`。

## Maven Staging

Maven dependency cache 与项目 staging repo 分离：

```text
~/.m2/repository                       # dependencies
build/cloudsimplus-raw-m2              # only locked CloudSim Plus JAR/POM
build/cloudsimplus-m2                  # sanitized CloudSim Plus JAR/POM
```

Gradle dependency resolution 只允许 `org.cloudsimplus:cloudsimplus` 从 sanitized repo 解析，避免静默回退 Maven Central。

## Warning Audit

Script：

```text
scripts/run-build-warning-audit.ps1
```

Task：

```text
verifyBuildWarnings
```

审计来源：

- compileKotlin；
- detekt；
- ktlintCheck；
- CloudSim Plus Maven build。

只允许精确白名单 warning。审计报告写入：

```text
build/reports/build-warnings/audit.md
```

## Test And Coverage Tasks

| Task | Purpose |
| :--- | :--- |
| `verifyJUnitTestSignatures` | 阻断非 void/Unit JUnit test/template。 |
| `updateJUnitTestInventory` | 更新测试入口 lock。 |
| `verifyJUnitTestInventory` | 检查测试入口没有意外变化。 |
| `verifyNoDetektBaseline` | 禁止重新引入 detekt baseline。 |
| `jacocoTestCoverageVerification` | 根项目覆盖率门禁。 |
| `buildSrc:jacocoTestCoverageVerification` | buildSrc 覆盖率门禁。 |

## Documentation And Policy Tasks

| Task | Purpose |
| :--- | :--- |
| `generateRealtimeMetricDocs` | 从 metric schema 生成 realtime metrics 文档。 |
| `verifyGitHubActionsPolicy` | 检查 workflow action 版本策略。 |
| `validateExampleConfigs` | 验证示例配置。 |
| `fullCheck` | 聚合主要本地门禁。 |

Wiki 生成不接入 Gradle task；它由 `scripts/build-wiki.py` 和 `.github/workflows/wiki-sync.yml` 处理，避免让普通 `check` 依赖 GitHub Wiki 发布逻辑。

## Performance Tasks

| Task | Purpose |
| :--- | :--- |
| `benchmarkPerformance` | legacy lightweight benchmark。 |
| `benchmarkPerformanceSmoke` | 性能链路 smoke。 |
| `generatePerformanceTrendReport` | 从 JMH JSON 生成 Markdown。 |
| `benchmarkPerformanceTrend` | 执行 JMH + trend report。 |

## Release Tasks

| Task | Purpose |
| :--- | :--- |
| `fatJar` | 可执行 all jar。 |
| `fatJarHelpSmoke` | fatJar `--help` smoke。 |
| `verifyReleaseAssets` | release 资产集合检查。 |
| `packageWindowsRelease` | Windows zip。 |
| `packageUnixRelease` | Unix tar.gz。 |
| `packageSourceRelease` | source zip。 |
| `generateReleaseManifest` | release manifest。 |
| `verifyReleaseManifest` | manifest 校验。 |
| `verifyReleasePackage` | release package smoke。 |

## Supply Chain Tasks

| Task | Purpose |
| :--- | :--- |
| `cyclonedxDirectBom` | CycloneDX SBOM。 |
| `generateLicenseReport` | 许可证报告。 |
| `verifyRuntimeLicensePolicy` | runtime 许可证策略。 |
| `generateSupplyChainReports` | 汇总 SBOM/license report。 |
| `checkLicense` | license policy gate。 |

## Container Tasks

| Task | Purpose |
| :--- | :--- |
| `prepareContainerImageContext` | 生成最小运行时 Podman/OCI context。 |
| `verifyContainerBuildContext` | 校验 context 大小、禁止文件、checksum。 |
| `containerImageSmoke` | 使用 Podman 构建并运行容器 smoke。 |
| `cliEndToEndSmoke` | CLI end-to-end smoke。 |

## Configuration Cache

默认目标：

- locked CloudSim Plus path cache-safe；
- source build/sanitize/verify 支持 configuration cache；
- mutable CloudSim Plus task 可 always-run；
- warning audit 作为独立脚本清理 staging 后重建。

验证：

```powershell
.\gradlew.bat fullCheck verifyReleasePackage --configuration-cache
.\gradlew.bat fullCheck verifyReleasePackage --configuration-cache
```

第二次应复用 configuration cache。

## Adding A Build Task

Checklist：

1. 新建 task class。
2. 标注 `@Input`、`@InputFile`、`@OutputFile`、`@OutputDirectory`。
3. 避免 configuration phase 读取动态文件或执行进程。
4. 写纯 helper 单测。
5. 写 TestKit 功能测试。
6. 覆盖 configuration cache 场景。
7. 如果接入 `check` 或 `fullCheck`，更新 [development.md](development.md) 或 [release-readiness.md](release-readiness.md)。

## Common Failures

| Failure | Cause | Fix |
| :--- | :--- | :--- |
| classpath uses Maven Central CloudSim Plus | sanitized repo 未生成或 exclusive content 失效 | 跑 `verifyCloudSimPlusSourceBuild`。 |
| configuration cache discarded | task 在 configuration phase 做动态工作 | 移到 task action 并声明输入输出。 |
| warning audit unknown warning | 工具链新增 warning | 定位来源，不要全局 suppress。 |
| release manifest mismatch | asset 列表或 metadata 漂移 | 重新生成并验证 release manifest。 |
| container context too large | build/cache/source 进入 context | 检查 `verifyContainerBuildContext` 报告。 |
