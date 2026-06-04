# Release Readiness

本清单用于稳定发布当前重构，避免继续混入新功能。

## Release gates

- `.\gradlew.bat ktlintCheck detekt --no-daemon --stacktrace`
- `.\gradlew.bat fullCheck --no-daemon --stacktrace --rerun-tasks`
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
- Ubuntu CI 需运行 `containerImageSmoke` 验证 Containerfile 构建出的镜像可以执行 `--help`。

## Current quality baseline

- `detekt-baseline.xml` 是首次接入 detekt 的过渡基线，当前 169 项，后续迭代逐步削减。
- JaCoCo 已设置保守门禁：line >= 60%、branch >= 35%。
- `benchmarkPerformanceTrend` 只生成性能趋势报告，不设置性能失败阈值。
