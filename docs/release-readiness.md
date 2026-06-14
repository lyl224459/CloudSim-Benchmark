# Release Readiness

本清单用于稳定发布当前重构，避免继续混入新功能。

## Release gates

- `.\gradlew.bat ktlintCheck detekt --no-daemon --stacktrace`
- `.\gradlew.bat buildSrc:test --no-daemon --stacktrace`
- `pwsh -File scripts/run-build-warning-audit.ps1`
- `.\gradlew.bat fullCheck --no-daemon --stacktrace --rerun-tasks`
- `.\gradlew.bat verifyCloudSimPlusSourceBuild --no-daemon --stacktrace --configuration-cache`
- `.\gradlew.bat verifyCloudSimPlusLock --no-daemon --stacktrace --configuration-cache`
- `.\gradlew.bat benchmarkPerformanceSmoke --no-daemon --stacktrace --rerun-tasks`
- `.\gradlew.bat benchmarkPerformanceTrend --no-daemon --stacktrace`
- `.\gradlew.bat verifyReleasePackage --no-daemon --stacktrace`
- `.\gradlew.bat verifyReleaseManifest --no-daemon --stacktrace`
- `git diff --check`

## Manual checks

- `fullCheck` 和 `benchmarkPerformanceSmoke` 日志不得出现 `Resource [logback.xml] occurs multiple times`。
- `run.cmd`、`scripts/run`、`scripts/run.bat` 需同时支持源码构建 JAR 和发布包根目录的 `cloudsim-benchmark-all.jar`。
- Windows release zip 需包含 `cloudsim-benchmark-all.jar`、`run.cmd`、`configs/`、`data/`、`README.md`、`LICENSE` 和 `cloudsim-benchmark-logback.xml`。
- Unix release tar.gz 需包含 `cloudsim-benchmark-all.jar`、`scripts/run`、`configs/`、`data/`、`README.md`、`LICENSE` 和 `cloudsim-benchmark-logback.xml`。
- Ubuntu CI 需运行 `containerImageSmoke` 验证 Containerfile 构建出的镜像可以执行 `--help`；CI 缺少 Docker 必须失败，本地缺少 Docker 时允许跳过并打印诊断。

## Current quality baseline

- `detekt-baseline.xml` 是首次接入 detekt 的过渡基线，当前 10 项，后续迭代逐步削减。
- JaCoCo 已设置保守门禁：line >= 68%、branch >= 50%；`buildSrc` 门禁为 line >= 50%、branch >= 40%。
- `benchmarkPerformanceTrend` 只生成性能趋势报告，不设置性能失败阈值。
- Windows、Ubuntu、macOS CI 和 release 构建均执行 build warning audit，并始终上传 `build/reports/build-warnings/`。
- 普通构建严格使用 `gradle/cloudsimplus.lock`；每周 latest compatibility workflow 单独测试上游最新 release。发布清单必须记录实际 CloudSim Plus ref、commit 和 version。
- `buildSrc` JaCoCo 生成 XML/HTML 报告并执行保守覆盖率门禁。
- 当前精确允许两个外部工具 warning：detekt `1.23.8` 的 Gradle 10 deprecation，以及 ktlint 内嵌 Kotlin compiler 的 JDK 25 Unsafe warning。detekt warning 发生在全局配置阶段，可能出现在每个被审计任务日志中；ktlint Unsafe warning 只允许出现在 `ktlintCheck` 日志中。其余 deprecation、native access、Unsafe、JVM target fallback 和未知 warning 均阻断构建。
- detekt 白名单在稳定版插件不再调用 `ReportingExtension.file(String)` 后删除；ktlint 白名单在其内嵌 Kotlin compiler 不再调用该 Unsafe API 后删除。
