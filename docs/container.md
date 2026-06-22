# Container Guide

容器构建采用“Gradle 构建，Podman 只组装运行镜像”的模型。Podman build 不编译源码，也不需要 `.git`、Gradle cache 或 CloudSim Plus checkout。

## Build Context Flow

```text
fatJar
  -> prepareContainerImageContext
  -> build/container-context
  -> verifyContainerBuildContext
  -> podman build -f build/container-context/Containerfile build/container-context
```

`build/container-context` 只允许包含运行时需要的文件：

- `app.jar`
- `configs/`
- `data/`
- runtime `Containerfile`
- provenance 文件

禁止包含：

- `.git`
- `.gradle`
- Gradle wrapper
- Kotlin/Java source
- build logs
- `runs/`
- IDE 文件

## Commands

生成上下文：

```powershell
.\gradlew.bat prepareContainerImageContext verifyContainerBuildContext --configuration-cache
```

构建镜像：

```powershell
podman build -t cloudsim-benchmark -f build/container-context/Containerfile build/container-context
```

运行 help：

```powershell
podman run --rm cloudsim-benchmark --help
```

运行实验并挂载结果目录：

```powershell
podman run --rm `
  --read-only `
  --tmpfs /tmp `
  -v "${PWD}\runs:/app/runs" `
  cloudsim-benchmark run --mode batch --algorithms RANDOM --runs 1
```

## Runtime User

镜像默认使用固定 UID/GID：

```text
10001:10001
```

唯一需要持久写入的目录：

```text
/app/runs
```

CI smoke 会验证：

- `--help` 可以执行；
- 运行 UID 是 `10001`；
- `/app/runs` 可写；
- root filesystem 可只读；
- 镜像中没有 Git、Gradle 或源码。

## Provenance

container context provenance 记录：

- CloudSim Plus ref、commit、version；
- fatJar SHA-256；
- 上下文文件清单；
- 资产和 checksum。

`verifyContainerBuildContext` 会校验 provenance 与实际文件一致，并限制上下文大小不超过 50 MiB。

## Local vs CI Behavior

| Environment | Podman Missing |
| :--- | :--- |
| Local | `containerImageSmoke` 打印诊断并跳过。 |
| CI | `containerImageSmoke` 失败，阻断 PR。 |

CI 使用 Podman 构建最小上下文镜像。普通开发机可以直接使用本地 Podman。

## Release Container

Release workflow 使用 Podman 从同一最小上下文构建并推送 GHCR 镜像：

```text
ghcr.io/lyl224459/cloudsim-benchmark
```

可运行镜像使用语义化版本 tag，并在正式 tag 发布时同步 `latest`：

```powershell
docker pull ghcr.io/lyl224459/cloudsim-benchmark:1.2.0
docker pull ghcr.io/lyl224459/cloudsim-benchmark:latest
```

如果需要固定到不可变镜像摘要，Docker/Podman digest 引用使用 `@sha256:...`，不是 `:sha256-...`：

```powershell
docker pull ghcr.io/lyl224459/cloudsim-benchmark@sha256:<image-digest>
```

Release 会为镜像 digest 生成 provenance 和 SBOM attestation。启用 registry attestation 后，GHCR 可能显示 `sha256-<digest>` 形式的 attestation fallback tag；这类 tag 是证明材料索引，不是应用运行镜像。不要使用 `docker pull ghcr.io/lyl224459/cloudsim-benchmark:sha256-...` 作为运行版本。

## Troubleshooting

| Symptom | Cause | Action |
| :--- | :--- | :--- |
| Context too large | 多余文件进入 `build/container-context` | 检查 `verifyContainerBuildContext` 报告和 `.dockerignore`。 |
| Permission denied writing results | 未挂载 `/app/runs` 或目录权限不足 | 挂载宿主 `runs` 并确保当前用户可写。 |
| Podman build cannot find JAR | 未运行 `prepareContainerImageContext` | 先运行 Gradle context 任务。 |
| CI passes build but smoke fails | 镜像入口或用户权限问题 | 查看 container smoke 日志中的 UID、文件检查和 help 输出。 |
