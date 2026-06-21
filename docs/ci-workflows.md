# CI Workflows Guide

本文档说明 GitHub Actions workflow 的职责、触发条件、关键 job 和 artifact。

## Workflow Overview

| Workflow | File | Trigger | Purpose |
| :--- | :--- | :--- | :--- |
| CI - Build and Test | `.github/workflows/ci.yml` | PR to `main`, push to `main` | 主阻断矩阵。 |
| CloudSim Plus Latest Compatibility | `.github/workflows/cloudsimplus-latest.yml` | weekly, manual | 测试上游最新 CloudSim Plus release。 |
| Supply Chain - OSV | `.github/workflows/osv.yml` | PR, weekly, manual | 漏洞扫描。 |
| Weekly Performance History | `.github/workflows/performance-history.yml` | weekly, manual | 记录 hosted runner JMH 趋势。 |
| Wiki Sync | `.github/workflows/wiki-sync.yml` | docs push to `main`, manual | 生成并同步 GitHub Wiki。 |
| Release - Build and Publish | `.github/workflows/release.yml` | tag, manual | 构建 release assets、容器和 attestation。 |

## Main CI Jobs

### `workflow-static-analysis`

运行 actionlint：

```text
rhysd/actionlint
```

阻断：

- YAML 语法错误；
- GitHub expression 错误；
- 权限和 action input 错误。

### `supply-chain-policy`

运行：

```powershell
./gradlew generateSupplyChainReports checkLicense --no-configuration-cache
```

上传：

```text
build/reports/supply-chain/
```

阻断：

- 未知 runtime 许可证；
- 许可证策略失败；
- SBOM/license report 生成失败。

### `build-test-package`

三平台矩阵：

- Windows
- Ubuntu
- macOS

职责：

- checkout recursive submodules；
- setup JDK 25；
- cache Gradle 和 Maven；
- warning audit；
- `fullCheck`；
- `verifyReleasePackage`；
- 上传 warning audit artifact。

### `containerImageSmoke`

Ubuntu 执行，要求 Docker 可用。验证：

- 最小 container context；
- Docker image build；
- `--help`；
- UID 10001；
- `/app/runs` 可写；
- 镜像无 Git/Gradle/source。

### Performance Artifact Job

非阻断或独立趋势用途。运行 `benchmarkPerformanceTrend` 并上传：

- `jmh-results.json`
- `performance-trend.md`
- optional baseline JSON。

## CloudSim Plus Latest Compatibility

目的：不改变仓库 lock 的情况下测试最新上游 release。

运行：

```text
-Pcloudsimplus.autoUpdate=true
verifyCloudSimPlusSourceBuild
fullCheck
benchmarkPerformanceSmoke
```

失败处理：

- 不直接改 main。
- 下载 artifact 查看实际 ref/version。
- 如果需要升级，单独开 CloudSim Plus lock PR。

## OSV Workflow

PR：

- 使用 reusable PR scanner。
- 阻断新增漏洞。

Scheduled/manual：

- `fail-on-vuln: false`。
- 上传报告/SARIF。
- 用于存量风险观察。

## Performance History Workflow

写入分支：

```text
performance-history
```

包含：

- 最新 `jmh-results.json`；
- `performance-trend.md`；
- 与上一份 baseline 的 delta。

该 workflow `continue-on-error: true`，避免 hosted runner 抖动阻断普通开发。

## Wiki Sync Workflow

目的：把仓库 README 和 `docs/` 自动发布到 GitHub Wiki。

运行：

```text
python scripts/build-wiki.py
clone CloudSim-Benchmark.wiki.git
rsync build/wiki/ to wiki repo
commit and push changed pages
```

要求：

- 仓库启用 GitHub Wiki。
- workflow 使用 `contents: write`。
- Wiki 页面不要手工编辑；手工修改会被下一次同步覆盖。

详细说明见 [wiki-sync.md](wiki-sync.md)。

## Release Workflow

Windows job：

- warning audit；
- buildSrc check；
- package release assets；
- generate SBOM/license；
- verify release manifest；
- upload container context；
- attest release assets；
- create GitHub Release。

Ubuntu image job：

- download container context；
- build/push GHCR image；
- generate image attestation。

## Permissions

默认 CI：

```text
contents: read
```

OSV：

```text
security-events: write
```

Release：

```text
contents: write
id-token: write
attestations: write
packages: write for image job
```

权限应按 job 最小化，不要全 workflow 过度授权。

## Artifact Triage

常用 artifact：

| Artifact | Use |
| :--- | :--- |
| `build-warning-audit-*` | 定位 warning 来源。 |
| `supply-chain-policy` | 查看 SBOM/license policy 输出。 |
| `cloudsimplus-latest-*` | 查看最新 CloudSim Plus compatibility 结果。 |
| `weekly-performance-history` | 查看 JMH trend 和 baseline delta。 |
| release artifacts | 验证 zip/tar/jar/SBOM/license/manifest。 |

## Failure Policy

- CI 红时先修 CI，不继续叠加新开发。
- 依赖更新失败时不要混入业务修复。
- CloudSim Plus latest 失败不代表 main 失败，单独分析。
- Performance history 失败先看 artifact，确认是否真实回归。
- Container smoke 本地可跳过 Docker 缺失，CI 缺 Docker 必须失败。
