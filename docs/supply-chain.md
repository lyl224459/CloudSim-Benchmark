# Supply Chain Guide

本文档说明依赖、许可证、漏洞、SBOM、release manifest 和 attestation 的维护方式。发布检查清单见 [release-readiness.md](release-readiness.md)。
Release asset 结构和 attestation 操作见 [release-package.md](release-package.md)。

## Components

| Control | Purpose |
| :--- | :--- |
| Gradle dependency verification | 锁定外部依赖 checksum。 |
| Dependabot | 每周检查 Gradle、GitHub Actions 和容器相关更新。 |
| OSV | PR 漏洞扫描和每周全量扫描。 |
| Gitleaks | PR、`main` push 和每周敏感信息扫描。 |
| License policy | 阻断未知或新增 runtime 许可证。 |
| CycloneDX SBOM | 生成 runtime dependency SBOM。 |
| Release manifest | 校验 release assets 与 CloudSim Plus metadata。 |
| GitHub attestation | 为 release assets、SBOM 和 GHCR image 生成 provenance。 |

## Dependency Verification

Gradle verification metadata：

```text
gradle/verification-metadata.xml
```

依赖升级时更新 checksum：

```powershell
.\gradlew.bat testClasses buildSrc:testClasses compileJmhKotlin generateSupplyChainReports `
  --write-verification-metadata sha256 `
  --no-daemon --stacktrace --no-configuration-cache
```

只提交与本次依赖变化相关的 metadata diff。不要在依赖 PR 中修改 CloudSim Plus lock、CLI、CSV 或算法行为。

## Dependabot Flow

Dependabot 配置：

```text
.github/dependabot.yml
```

Gradle 更新按类别分组：

- Kotlin toolchain；
- kotlinx runtime；
- logging；
- JUnit；
- Mockito；
- AssertJ。

GitHub Actions 和容器相关更新单独处理。bulk PR 不直接合并，应拆成小 PR。

每个依赖 PR 必跑：

```powershell
.\gradlew.bat generateSupplyChainReports checkLicense --no-daemon --stacktrace --no-configuration-cache
.\gradlew.bat fullCheck verifyReleasePackage benchmarkPerformanceSmoke --no-daemon --stacktrace --configuration-cache
```

远程还必须通过三平台 CI、container smoke、warning audit、OSV 和 supply-chain policy。

## License Policy

允许列表：

```text
gradle/allowed-licenses.json
```

当前允许的 runtime 许可证包括：

- Apache-2.0
- EPL-1.0
- EPL-2.0
- GPL-3.0 / GPLv3
- LGPL-2.1 / LGPL-2.1-only
- MIT

CloudSim Plus 是 GPLv3 runtime 依赖，已经明确接受。未知许可证或新增不在允许列表中的许可证会被 `checkLicense` 阻断。

生成报告：

```powershell
.\gradlew.bat generateSupplyChainReports checkLicense --no-daemon --stacktrace --no-configuration-cache
```

## SBOM

SBOM 基于发布 runtime classpath 生成，避免把测试依赖混入 release 资产。

输出通常在：

```text
build/reports/
build/reports/dependency-license/
build/reports/cyclonedx/
```

Release manifest 会记录 SBOM 和许可证报告资产。

## OSV

Workflow：

```text
.github/workflows/osv.yml
```

策略：

- PR 扫描阻断新增漏洞。
- 每周全量扫描上传报告和 SARIF。
- 存量问题不应和无关业务 PR 混在一起修。

## Secret Scanning

Workflow：

```text
.github/workflows/secrets.yml
```

策略：

- 使用 `actions/checkout@v6` 和 `fetch-depth: 0` 扫描完整历史。
- 使用 `gitleaks/gitleaks-action@v3` 与 `.gitleaks.toml` 默认规则。
- 初始不维护宽泛 allowlist；真实 secret 需要先轮换并移除，示例假值只允许精确路径或 regex 例外。
- `verifyGitHubActionsPolicy` 会检查 workflow 存在、Gitleaks 主版本和 full-history checkout。

## GitHub Actions Policy

`verifyGitHubActionsPolicy` 扫描 workflow：

- 禁止已知 Node 20 action 主版本；
- 要求使用批准的 Node 24 兼容 action；
- release workflow 允许并要求 `actions/attest@v4`；
- 禁止重新引入 Docker build/push action，镜像发布主路径使用 Podman。

actionlint 在 Ubuntu job 中校验 workflow YAML、表达式、权限和 action inputs。

## CloudSim Plus Lock

默认构建严格使用：

```text
gradle/cloudsimplus.lock
third_party/cloudsimplus
```

普通依赖 PR 不修改 CloudSim Plus lock。测试最新 CloudSim Plus release 使用 weekly compatibility workflow 或显式：

```powershell
.\gradlew.bat fullCheck -Pcloudsimplus.autoUpdate=true
```

确认兼容后才运行 `updateCloudSimPlusLock` 并提交 lock 与 submodule gitlink。

## Release Attestations

Release workflow 使用 GitHub 原生 attestation：

- release assets provenance；
- fatJar + SBOM attestation；
- Podman 推送的 GHCR image digest provenance；
- GHCR image SBOM attestation。

需要权限：

- `contents: write`
- `id-token: write`
- `attestations: write`
- image job 需要 `packages: write`

## Review Checklist

供应链 PR 审查：

- 只更新目标依赖和 verification metadata。
- SBOM 和 license report 可生成。
- OSV 无新增阻断漏洞。
- `checkLicense` 通过。
- release manifest 包含新增/变更资产。
- CI 三平台通过。
- warning audit 未新增未知 warning。
