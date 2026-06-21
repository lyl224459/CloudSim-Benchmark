# Getting Started

本文档覆盖第一次 checkout 到本地可运行的最短路径。CLI 细节见 [cli.md](cli.md)，配置细节见 [configuration.md](configuration.md)。

## Requirements

- JDK 25 或更高版本。
- Git submodule 支持。
- PowerShell 7+ 或 Bash。
- Podman 可选，仅用于 `containerImageSmoke` 或手动镜像验证。

## Checkout

```powershell
git clone https://github.com/lyl224459/CloudSim-Benchmark.git
cd CloudSim-Benchmark
git submodule update --init --recursive
```

CloudSim Plus 由 `third_party/cloudsimplus` submodule 提供源码。普通构建默认使用 `gradle/cloudsimplus.lock` 中锁定的 ref、commit 和 version；默认锁定模式只验证 checkout 状态，不执行 fetch 或 checkout。

如果 submodule 缺失，先执行：

```powershell
git submodule update --init --recursive
```

## Build

Windows:

```powershell
.\gradlew.bat fullCheck --configuration-cache
.\run.cmd --help
```

Unix/macOS:

```bash
./gradlew fullCheck --configuration-cache
./run --help
```

`run.cmd` 和 `./run` 会在 JAR 缺失时构建 fatJar。需要只构建 JAR 时可运行：

```powershell
.\run.cmd build
```

## Proxy And Offline Mode

Windows `run.cmd` 会读取系统代理并传给 Gradle 和 Java。受限网络下也可以显式传入 CloudSim Plus Git 代理：

```powershell
.\gradlew.bat verifyCloudSimPlusLock '-Dorg.gradle.project.cloudsimplus.gitProxy=http://host:port'
```

离线构建使用当前 submodule checkout：

```powershell
.\gradlew.bat verifyCloudSimPlusLock -Pcloudsimplus.offline=true
```

显式测试其他 CloudSim Plus ref：

```powershell
.\gradlew.bat fullCheck -Pcloudsimplus.ref=v8.5.7
```

动态最新 release 测试只用于兼容性验证：

```powershell
.\gradlew.bat fullCheck -Pcloudsimplus.autoUpdate=true
```

## First Runs

批处理 smoke：

```powershell
.\run.cmd run --mode batch --algorithms RANDOM --runs 1
```

实时 smoke：

```powershell
.\run.cmd run --mode realtime --algorithms MIN_LOAD --runs 1
```

配置 dry-run：

```powershell
.\run.cmd run --config configs/examples/single_config_example.toml --profile batch_small --dry-run
```

验证配置文件：

```powershell
.\run.cmd config validate --config configs/examples/realtime_test.toml
```

## Output Locations

- `runs/`: 默认实验输出目录。
- `build/reports/tests/`: 测试报告。
- `build/reports/jacoco/`: 覆盖率报告。
- `build/reports/performance/`: JMH JSON 和趋势 Markdown。
- `build/container-context/`: 最小容器构建上下文。

实验输出结构和 CSV 解读见 [results-and-analysis.md](results-and-analysis.md)。性能报告说明见 [performance.md](performance.md)。

## Container Smoke

先由 Gradle 生成最小运行上下文：

```powershell
.\gradlew.bat prepareContainerImageContext verifyContainerBuildContext
```

再构建镜像：

```powershell
podman build -t cloudsim-benchmark -f build/container-context/Containerfile build/container-context
```

CI 中 `containerImageSmoke` 要求 Podman 可用；本地没有 Podman 时会打印诊断并跳过。

容器运行、挂载和非 root 用户说明见 [container.md](container.md)。常见构建问题见 [troubleshooting.md](troubleshooting.md)。
