# Release Package Guide

本文档说明 release assets、manifest、包内容、容器和 attestation。当前不要求发版时也可用于检查发布任务。

## Release Tasks

| Task | Purpose |
| :--- | :--- |
| `fatJar` | 构建可执行 all jar。 |
| `copyReleaseJar` | 复制 release jar。 |
| `packageWindowsRelease` | 生成 Windows zip。 |
| `packageUnixRelease` | 生成 Unix tar.gz。 |
| `packageSourceRelease` | 生成 source zip。 |
| `generateSupplyChainReports` | 生成 SBOM 和许可证报告。 |
| `generateReleaseManifest` | 生成 release manifest。 |
| `packageReleaseAssets` | 汇总 release assets。 |
| `verifyReleaseManifest` | 校验 manifest 与实际资产。 |
| `verifyReleasePackage` | release package smoke。 |

## Expected Assets

Release artifacts 通常包括：

- executable jar；
- Windows zip；
- Unix tar.gz；
- source zip；
- CycloneDX SBOM；
- license report；
- release manifest；
- container context artifact；
- GHCR image digest。

## Windows Package

应包含：

```text
cloudsim-benchmark-all.jar
run.cmd
configs/
data/
README.md
LICENSE
cloudsim-benchmark-logback.xml
```

Smoke：

```powershell
run.cmd --help
```

## Unix Package

应包含：

```text
cloudsim-benchmark-all.jar
scripts/run
configs/
data/
README.md
LICENSE
cloudsim-benchmark-logback.xml
```

Smoke：

```bash
scripts/run --help
```

## Manifest

Release manifest 使用稳定 key-value 格式，记录：

- manifest format version；
- CloudSim Plus ref；
- CloudSim Plus commit；
- CloudSim Plus version；
- release asset 列表；
- checksum 或资产元数据。

`verifyReleaseManifest` 会检查：

- metadata 与 `gradle/cloudsimplus.lock` 一致；
- asset 列表与实际文件一致；
- required assets 存在；
- checksum/provenance 不漂移。

## SBOM And License

Release 只基于 runtime classpath 生成 SBOM 和许可证报告，不包含测试依赖。

检查：

```powershell
.\gradlew.bat generateSupplyChainReports checkLicense --no-daemon --stacktrace --no-configuration-cache
```

CloudSim Plus GPLv3 是已接受 runtime 许可证。新增许可证必须更新 policy 或替换依赖。

## Container Release

Release 容器使用 `build/container-context`，不是源码目录。

要求：

- 非 root UID/GID 10001；
- 只写 `/app/runs`；
- no Git/Gradle/source；
- `--help` smoke；
- GHCR image provenance；
- image SBOM attestation。

## Attestation

Release workflow 使用 `actions/attest@v4`：

- release assets provenance；
- fatJar + SBOM attestation；
- GHCR image digest provenance；
- GHCR image SBOM attestation。

需要 GitHub 权限：

- `id-token: write`
- `attestations: write`
- `contents: write`
- `packages: write` for image job

## Manual Release Checklist

发版前：

1. `main` CI 全绿。
2. `docs/release-readiness.md` 中门禁仍真实存在。
3. `detekt-baseline.xml` 不存在。
4. `verifyJUnitTestInventory` 通过。
5. `generateSupplyChainReports checkLicense` 通过。
6. `verifyReleasePackage` 通过。
7. `containerImageSmoke` 在 CI 通过。
8. CloudSim Plus lock 与 submodule gitlink 一致。

发版后：

1. 下载 Windows zip，运行 `run.cmd --help`。
2. 下载 Unix tar.gz，运行 `scripts/run --help`。
3. 验证 release manifest。
4. 验证 SBOM/license assets。
5. 检查 GHCR image digest 和 attestation。

## Non-Release Development

普通 PR 不需要创建 tag，也不需要手动运行 release workflow。涉及 package、manifest、container、supply-chain 的 PR 应至少运行：

```powershell
.\gradlew.bat verifyReleasePackage generateSupplyChainReports checkLicense --no-daemon --stacktrace --no-configuration-cache
```
