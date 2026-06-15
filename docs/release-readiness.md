# Release Readiness

本清单用于稳定发布当前重构，避免继续混入新功能。

## Release gates

- `.\gradlew.bat ktlintCheck detekt --no-daemon --stacktrace`
- `.\gradlew.bat buildSrc:test --no-daemon --stacktrace`
- `pwsh -File scripts/run-build-warning-audit.ps1`
- `.\gradlew.bat fullCheck --no-daemon --stacktrace --rerun-tasks`
- `.\gradlew.bat verifyCloudSimPlusSourceBuild --no-daemon --stacktrace --configuration-cache`
- `.\gradlew.bat verifyCloudSimPlusLock --no-daemon --stacktrace --configuration-cache`
- `.\gradlew.bat verifyGitHubActionsPolicy --no-daemon --stacktrace --configuration-cache`
- `.\gradlew.bat benchmarkPerformanceSmoke --no-daemon --stacktrace --rerun-tasks`
- `.\gradlew.bat benchmarkPerformanceTrend --no-daemon --stacktrace`
- `.\gradlew.bat verifyReleasePackage --no-daemon --stacktrace`
- `.\gradlew.bat prepareContainerImageContext verifyContainerBuildContext --no-daemon --stacktrace --configuration-cache`
- `.\gradlew.bat generateSupplyChainReports --no-daemon --stacktrace --no-configuration-cache`
- `.\gradlew.bat packageReleaseAssets verifyReleaseManifest --no-daemon --stacktrace --no-configuration-cache`
- `git diff --check`

## Manual checks

- `fullCheck` 和 `benchmarkPerformanceSmoke` 日志不得出现 `Resource [logback.xml] occurs multiple times`。
- `run.cmd`、`scripts/run`、`scripts/run.bat` 需同时支持源码构建 JAR 和发布包根目录的 `cloudsim-benchmark-all.jar`。
- Windows release zip 需包含 `cloudsim-benchmark-all.jar`、`run.cmd`、`configs/`、`data/`、`README.md`、`LICENSE` 和 `cloudsim-benchmark-logback.xml`。
- Unix release tar.gz 需包含 `cloudsim-benchmark-all.jar`、`scripts/run`、`configs/`、`data/`、`README.md`、`LICENSE` 和 `cloudsim-benchmark-logback.xml`。
- Ubuntu CI 需运行 `containerImageSmoke` 验证最小容器上下文构建出的镜像可以执行 `--help`，且镜像内不包含 Git、Gradle 或源码；CI 缺少 Docker 必须失败，本地缺少 Docker 时允许跳过并打印诊断。

## Current quality baseline

- Detekt baseline 已清零并删除；`verifyNoDetektBaseline` 禁止重新引入 baseline 文件或配置。
- JaCoCo 已设置门禁：line >= 75%、branch >= 55%、`datacenter`/`cli`/`broker`/`scheduler` branch 分别 >= 55%/50%/65%/55%，并对关键 CLI、runner、autoscaling 与输出上下文类设置独立门禁；`buildSrc` 合并普通单测与 TestKit task action 覆盖，门禁为 line >= 65%、branch >= 50%，并对 CloudSim Plus Git/source-build 关键类设置独立门禁。
- `benchmarkPerformanceTrend` 不设置性能失败阈值；`Weekly Performance History` 每周将 hosted runner 的 JMH JSON 和 delta
  报告写入 `performance-history` 分支，仅用于趋势观察。
- Windows、Ubuntu、macOS CI 和 release 构建均执行 build warning audit，并始终上传 `build/reports/build-warnings/`。
- 普通构建严格使用 `gradle/cloudsimplus.lock`；每周 latest compatibility workflow 单独测试上游最新 release。发布清单必须记录实际 CloudSim Plus ref、commit 和 version。
- 默认锁定模式下 `prepareCloudSimPlusSource` 仅增量验证 checkout/lock，不执行网络或 checkout；缺失 submodule 时先运行 `git submodule update --init --recursive`。
- GitHub workflow JavaScript Action 必须使用 `verifyGitHubActionsPolicy` 批准的 Node.js 24 主版本。
- `verifyContainerBuildContext` 必须确认 `build/container-context` 小于 50 MiB、provenance 与 CloudSim Plus lock/fatJar checksum 一致，且上下文不包含 Git、Gradle 或源码。
- `generateSupplyChainReports` 必须生成 runtime CycloneDX SBOM 和许可证报告；Dependabot 与 OSV workflow 负责持续依赖更新和漏洞扫描。
- CloudSim Plus Maven 依赖缓存与 raw/sanitized staging repo 必须分离；两个 staging repo 各只允许包含锁定版本的 JAR/POM。
- `verifyJUnitTestSignatures` 与 `verifyJUnitTestInventory` 必须通过；有意调整测试入口时使用 `updateJUnitTestInventory` 更新精确清单。
- `buildSrc` JaCoCo 生成 XML/HTML 报告并执行保守覆盖率门禁。
- 当前精确允许三个外部工具 warning 签名：detekt `1.23.8` 的 Gradle 10 deprecation、ktlint 内嵌 Kotlin compiler 的 JDK 25 Unsafe warning，以及锁定 CloudSim Plus 源码可能产生的 javac legacy diagnostics。detekt warning 发生在全局配置阶段，可能出现在每个被审计任务日志中；ktlint Unsafe warning 只允许出现在 `ktlintCheck` 日志中。其余 deprecation、native access、Unsafe、JVM target fallback 和未知 warning 均阻断构建。
- detekt 白名单在稳定版插件不再调用 `ReportingExtension.file(String)` 后删除；ktlint 白名单在其内嵌 Kotlin compiler 不再调用该 Unsafe API 后删除。
