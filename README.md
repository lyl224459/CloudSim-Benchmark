# CloudSim-Benchmark

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![JDK](https://img.shields.io/badge/JDK-25+-blue.svg)](https://jdk.java.net/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-purple.svg)](https://kotlinlang.org/)
[![Gradle](https://img.shields.io/badge/Gradle-9.5.1-green.svg)](https://gradle.org/)
[![CI](https://github.com/lyl224459/CloudSim-Benchmark/actions/workflows/ci.yml/badge.svg)](https://github.com/lyl224459/CloudSim-Benchmark/actions/workflows/ci.yml)

**CloudSim-Benchmark** 是一个基于 [CloudSim Plus](https://cloudsimplus.org/) 和 Kotlin 开发的高性能云任务调度算法评估框架。它集成了多种启发式群体智能算法与强化学习模型，支持批处理和实时调度两种实验模式，旨在为云计算调度研究提供一个**极致快速、易于扩展、结果可靠**的实验平台。

---

## 📋 目录

- [🎯 项目简介](#-项目简介)
- [✨ 核心特性](#-核心特性)
- [🛠️ 系统要求](#-系统要求)
- [🚀 快速开始](#-快速开始)
- [🧠 调度算法库](#-调度算法库)
- [⚙️ 配置说明](#-配置说明)
- [📈 实验结果与可视化](#-实验结果与可视化)
- [⚡ 性能优化深度解析](#-性能优化深度解析)
- [🐳 容器化支持](#-容器化支持-podmandocker)
- [🛠️ 开发指南](#-开发指南)
- [🔄 CI/CD 持续集成](#-cicd)
- [📄 许可证](#-许可证)

---

## 🎯 项目简介

CloudSim-Benchmark 旨在解决云调度研究中实验流程繁琐、算法对比困难、统计结果不科学等痛点：

- **完整实验框架**：涵盖任务生成、资源建模、算法执行、统计分析到图表生成的全流程。
- **多种调度场景**：原生支持**静态批处理 (Batch)** 和 **动态实时到达 (Real-time)** 调度。
- **高性能执行**：针对大规模仿真（数万任务）进行了 JVM 级优化，充分利用多核性能。
- **科研级统计**：支持多次运行自动计算平均值、标准差、置信区间，支持 Google Trace 真实数据集。

---

## ✨ 核心特性

- ✅ **丰富的算法库**：集成 PSO, WOA, GWO, HHO, 以及自研的 **Improved-RL** (改进版强化学习)。
- ✅ **极致性能优化**：采用 **ZGC** 低延迟回收、目标函数缓存和协程并行执行。
- ✅ **协程并行加速**：基于 Kotlin 协程实现算法与试验的并行执行，加速比可达 5x-8x。
- ✅ **统一 CLI 接口**：全新的命名参数格式，支持 `--algorithms ALL` 一键运行，支持协程开关。
- ✅ **结构化结果管理**：自动生成带时间戳的实验快照与分算法原始 CSV 数据。
- ✅ **一键可视化**：内置 Jupyter Notebook，自动读取最新实验数据并绘图。
- ✅ **现代化构建**：自动检测 CPU 核心数，本地开发极速构建（无压缩），发布版本自动压缩。

---

## 🛠️ 系统要求

- **操作系统**: Windows (推荐), Linux, macOS
- **JDK**: 25 或更高版本
- **内存**: 建议分配 2GB+ 堆内存 (通过 Gradle 自动配置)

---

## 🚀 快速开始

### 1. 克隆与构建

```bash
git clone https://github.com/lyl224459/CloudSim-Benchmark.git
cd CloudSim-Benchmark
git submodule update --init --recursive
./run.cmd build    # Windows
./run build        # Linux/WSL
```

Windows 脚本会自动读取系统代理（Internet Settings 中的 `ProxyEnable`/`ProxyServer`）并传递给 Gradle，首次下载 Gradle Wrapper 时无需在仓库中写死代理地址。若 JAR 不存在，`run.cmd` 会自动执行 `gradlew.bat fatJar --no-daemon`。

CloudSim Plus 通过 `third_party/cloudsimplus` submodule 源码构建。普通本地、PR、release 和 container 构建默认严格使用 `gradle/cloudsimplus.lock` 中的固定 ref、commit 与版本；Maven 依赖默认缓存到 `~/.m2/repository`，构建后仅将锁定版本的 JAR/POM 写入 `build/cloudsimplus-raw-m2`，清理 manifest 后的可解析 JAR/POM 写入 `build/cloudsimplus-m2`。可用 `-Pcloudsimplus.mavenCacheDir=<dir>` 覆盖依赖缓存位置。兼容性测试可用 `-Pcloudsimplus.ref=<tag-or-sha>` 指定 ref，或用 `-Pcloudsimplus.autoUpdate=true` 测试最新 semver release；确认兼容后通过 `updateCloudSimPlusLock` 更新 lock 并提交对应 submodule gitlink。`-Pcloudsimplus.offline=true` 使用当前 checkout，受限网络可用 `-Dorg.gradle.project.cloudsimplus.gitProxy=http://host:port` 指定代理。PowerShell 下 dotted Gradle 参数需要加引号，例如 `'-Dorg.gradle.project.cloudsimplus.gitProxy=http://host:port'`。源码构建任务支持 Gradle configuration cache；如遇网络失败，优先指定代理而不是关闭 configuration cache。Maven 源码构建会自动追加 JDK 25 兼容 JVM 参数，并保留用户已有 `MAVEN_OPTS`。

### 2. 运行实验 (子命令 CLI)

项目现在使用明确的子命令接口，通过 `./run.cmd` (Windows) 或 `./run` (Linux) 启动。旧入口 `batch` / `realtime` / `batch-multi` / `realtime-multi` 已停用，会直接给出迁移提示。

#### 🔹 基础对比实验 (Batch)

运行所有批处理算法，试验 3 次：

```bash
./run.cmd run --mode batch --algorithms ALL --runs 3
```

#### 🔹 实时调度实验 (Realtime)

指定特定算法并使用自定义随机种子：

```bash
./run.cmd run --mode realtime -a PSO_REALTIME,WOA_REALTIME -s 42 -r 5
```

#### 🔹 批量任务数扩展性实验 (Multi-Count)

研究算法在 50, 100, 200, 500 任务规模下的性能趋势：

```bash
./run.cmd run --mode batch-multi --tasks 50,100,200,500 -a ALL -r 3
```

#### 🔹 协程控制

显式指定最大并发数或使用顺序执行：

```bash
./run.cmd run --mode batch -a ALL -C 4         # 限制并发数为 4
./run.cmd run --mode batch -a ALL --sequential # 使用顺序执行模式
```

#### 🔹 Dry Run / 管理命令

```bash
./run.cmd run --mode batch --dry-run --config configs/examples/batch_test.toml
./run.cmd list algorithms --mode batch
./run.cmd list profiles --config configs/examples/single_config_example.toml
./run.cmd list presets --config configs/examples/single_config_example.toml
./run.cmd config validate --config configs/examples/realtime_test.toml
./run.cmd config print --config configs/examples/batch_test.toml --profile batch_test
```

#### 🔹 参数速查表

| 参数                  | 短参数 | 描述                                 | 必需     |
| :-------------------- | :----- | :----------------------------------- | :------- |
| `--mode`            | -     | `batch` / `realtime` / `batch-multi` / `realtime-multi` | 可选 |
| `--algorithms`      | `-a` | 算法列表 (如 `PSO,WOA` 或 `ALL`) | 与 `--preset` 二选一 |
| `--preset`          | -     | 使用配置文件中的预设算法集合         | 与 `--algorithms` 二选一 |
| `--profile`         | `-p` | 选择配置文件中的 profile            | 配置文件存在时推荐 |
| `--tasks`           | `-t` | 任务数列表 (仅限 multi 模式)         | 仅 multi |
| `--seed`            | `-s` | 随机数种子 (默认 0)                  | 否       |
| `--runs`            | `-r` | 每个配置的试验次数 (默认 1)          | 否       |
| `--sequential`      | `-S` | 禁用并行，切换到顺序执行模式         | 否       |
| `--concurrency`     | `-C` | 限制并发协程数量 (默认 CPU 核心数)   | 否       |
| `--config`          | `-c` | TOML 配置文件路径                    | 否       |
| `--output`          | `-o` | 结果输出目录                         | 否       |
| `--dry-run`         | -     | 打印最终合并后的配置，不创建结果目录 | 否       |
| `--help`            | `-h` | 显示详细帮助信息                     | 否       |

---

## 🧠 调度算法库

### 1. 群体智能 (Heuristic)

- **PSO (粒子群优化)**: 模拟鸟群觅食，收敛快，适合连续空间。
- **WOA (鲸鱼优化)**: 模拟座头鲸螺旋捕食，全局搜索能力强。
- **GWO (灰狼优化)**: 模拟狼群等级狩猎，收敛极其稳定。
- **HHO (哈里斯鹰优化)**: 模拟鹰群围捕，多策略自适应切换（已修复位置更新逻辑）。

### 2. 强化学习 (Reinforcement Learning)

- **RL (Q-Learning)**: 基于 Q-Learning 的基础调度器。
- **Improved-RL**: **核心改进版**。
  - **状态离散化**: 解决连续负载导致的状态爆炸问题。
  - **任务感知**: 引入任务长度特征，提升决策精度。
  - **奖励重构**: 基于方差增量的负载均衡奖励函数。

### 3. 基准算法

- **Random**: 随机分配。
- **Min-Load**: 实时模式下的最小负载优先策略。

---

## ⚙️ 配置说明

项目支持**分层配置系统**，运行时优先级为：命令行参数 > 配置文件 > 代码默认值。系统配置仍支持环境变量加载；命令行参数会覆盖配置文件中的同名实验设置。

### 外部配置文件 (TOML)

推荐通过 `configs/experiments/` 下的配置文件进行大规模实验：

```bash
./run.cmd run --config configs/experiments/performance_test.toml --profile batch_compare
```

也可以通过配置文件提供默认值，再用 CLI 覆盖少量参数：

```bash
./run.cmd run --config configs/examples/batch_test.toml --profile batch_test -a RANDOM -r 1 -o tmp-runs
```

上例会使用 profile 中定义的模式、任务数和默认算法，但 CLI 会覆盖算法、运行次数和输出目录。实际优先级为：**CLI > profile > 配置文件全局项 > 代码默认值**。

### 配置分离

项目现在支持系统配置和实验配置的分离：

- **系统配置**: 见 `src/main/kotlin/config/SystemConfig.kt`，管理输出目录、日志级别、JVM 参数等
- **实验配置**: 见 `src/main/kotlin/config/ExperimentConfig.kt`，管理 profile、任务数、算法参数、目标函数权重等

### 实验模式配置

单个 TOML 文件现在通过 `defaultProfile` + `[profiles.NAME]` 定义实验：

```toml
defaultProfile = "batch_test"

[profiles.batch_test]
mode = "batch"
algorithms = ["PSO", "WOA"]
runs = 3

[profiles.batch_test.batch]
cloudletCount = 100
population = 30
maxIter = 50
```

这种方式允许用户在一个文件里定义多个实验档案，并通过 `--profile NAME` 选择。每次真实运行会在实验目录中保存 `resolved_config.json`。

### 测试配置文件示例

项目提供了四种模式的测试配置文件示例，位于 `configs/examples/` 目录下：

1. **批处理模式测试配置** (`configs/examples/batch_test.toml`):

```toml
defaultProfile = "batch_test"

[profiles.batch_test]
mode = "batch"
runs = 3
```

2. **实时调度模式测试配置** (`configs/examples/realtime_test.toml`):

```toml
defaultProfile = "realtime_test"

[profiles.realtime_test]
mode = "realtime"
runs = 3
```

3. **批处理多任务数模式测试配置** (`configs/examples/batch_multi_test.toml`):

```toml
defaultProfile = "batch_multi_test"

[profiles.batch_multi_test]
mode = "batch-multi"
tasks = [50, 100, 200]
```

4. **实时多任务数模式测试配置** (`configs/examples/realtime_multi_test.toml`):

```toml
defaultProfile = "realtime_multi_test"

[profiles.realtime_multi_test]
mode = "realtime-multi"
tasks = [50, 100, 200]
```

这些配置文件可以作为用户自定义实验配置的参考模板。

---

## 📈 实验结果与可视化

实时调度 CSV 已统一使用结构化 `Status` / `ErrorType` / `ErrorMessage` 字段，并由单一指标 schema 生成列名。完整指标说明见 [Realtime 指标定义](docs/realtime-metrics.md)。

### 性能基准

项目提供两层性能任务：`benchmarkPerformanceSmoke` 只验证链路能跑通；`benchmarkPerformanceTrend` 使用 JMH 生成非阻断趋势报告，适合在本机或专用性能环境里观察调度算法耗时与分配趋势：

```bash
./gradlew.bat benchmarkPerformanceTrend --no-daemon --stacktrace
```

JMH 结果写入 `build/reports/performance/jmh-results.json`，Markdown 趋势报告写入 `build/reports/performance/performance-trend.md`。报告包含 objective function 调用、PSO/WOA/GWO/HHO 固定输入、realtime 调度不同 cloudlet count 的耗时、GC 分配指标和固定 JVM/GC 参数。可用历史 JSON 生成 delta：

```bash
./gradlew.bat benchmarkPerformanceTrend ^
  -PperformanceBaseline=build/reports/performance/previous-jmh-results.json ^
  --no-daemon --stacktrace
```

开发期可运行 smoke 任务验证 JSON 链路：

```bash
./gradlew.bat benchmarkPerformanceSmoke --no-daemon --stacktrace
```

旧的 `benchmarkPerformance` 任务仍保留为轻量 JSON 基准，默认覆盖 `100,1000,10000` cloudlets，并运行 `PSO,WOA,GWO,HHO,REALTIME_MIN_LOAD`。

### Realtime 指标定义

| 指标主题 | 关键 CSV 指标 | 含义 |
| :--- | :--- | :--- |
| 租户公平 | `TenantFairnessIndex`, `FairnessViolationCount` | 衡量多租户完成量与策略约束的公平程度。 |
| DRF | `DominantResourceFairnessIndex` | 基于主导资源份额的租户公平性得分。 |
| SLA penalty | `SlaPenalty`, `TenantSlaPenalty` | 将 deadline 延迟按全局或租户策略累计成惩罚值。 |
| 拓扑成本 | `TopologyCost`, `AverageTopologyLatency` | 衡量跨 rack/region 放置带来的成本与延迟。 |
| 镜像缓存/冷启动 | `ColdStartDelayTotal`, `AutoscalingCost` | 反映镜像拉取、VM 冷启动和伸缩引入的代价。 |
| 队列深度 | `AvgQueueDepth`, `MaxQueueDepth` | 记录实时调度选择 VM 时观察到的队列压力。 |
| Retry/Rejection | `RetryCount`, `RetrySuccessRate`, `RejectedCount`, `ResourceRejectedCount` | 统计重试成功率以及准入、容量、资源策略造成的拒绝。 |

完整字段、单位、趋势和定义见 [docs/realtime-metrics.md](docs/realtime-metrics.md)。

## ⚡ 性能优化深度解析

### 1. JVM 级优化

- **ZGC 垃圾回收器**: 使用 `-XX:+UseZGC` 实现毫秒级低延迟回收，避免大对象创建时的长时间停顿。
- **内存配置**: 默认分配 2GB 堆内存，支持大规模仿真（数万任务）。
- **模块系统优化**: 通过 `--add-opens` 参数绕过模块系统限制，提升反射性能。

### 2. 计算性能优化

- **目标函数缓存**: 调度目标函数预缓存任务长度、VM 性能与归一化边界，降低 PSO/WOA/GWO/HHO 高频 fitness 计算成本。
- **指标聚合优化**: 实时指标由统一 schema 驱动 CSV、统计与文档，减少重复字段维护成本。
- **协程并行**: 基于 Kotlin 协程实现算法并行执行，充分利用多核 CPU 性能。

### 3. 算法层面优化

- **批处理模式**: 一次性调度全部任务，适合静态场景，支持负载均衡和能耗优化。
- **实时模式**: 动态响应任务到达，适合动态场景，支持优先级调度。
- **多任务数模式**: 自动测试不同任务规模下的性能表现，便于扩展性分析。

---

## 🐳 容器化支持 (Podman/Docker)

项目支持容器化部署，使用 Podman 或 Docker 运行：

```bash
# 构建镜像
podman build -t cloudsim-benchmark .

# 运行批处理实验
./run.cmd podman batch --algorithms ALL --runs 3

# 运行实时调度实验
./run podman realtime -a PSO_REALTIME,WOA_REALTIME -r 5
```

容器化运行的优势：

- **环境隔离**: 避免本地环境差异影响实验结果
- **可重现性**: 确保实验在不同机器上的结果一致

`containerImageSmoke` 在 CI 中要求 Docker 可用，Docker 缺失或镜像 smoke 失败都会阻断构建；本地开发机缺少 Docker 时会给出明确诊断并跳过该 smoke。
- **资源管控**: 限制容器资源使用，避免过度消耗

Containerfile 配置了完整的运行时环境，包括 JDK 25、ZGC 优化等。

---

## 🛠️ 开发指南

### 项目结构

```
CloudSim-Benchmark/
├── src/main/kotlin/
│   ├── ComparisonRunner.kt         # 主比较执行器 (批处理)
│   ├── RealtimeComparisonRunner.kt # 主比较执行器 (实时)
│   ├── BatchCloudletCountRunner.kt # 多任务数执行器 (批处理)
│   ├── RealtimeCloudletCountRunner.kt # 多任务数执行器 (实时)
│   ├── config/                    # 配置管理模块
│   │   ├── SystemConfig.kt        # 系统配置
│   │   ├── ExperimentConfig.kt    # 实验配置
│   │   └── ConfigurationManager.kt # 配置管理器
│   ├── scheduler/                 # 调度算法模块
│   │   ├── algorithms/            # 群体智能算法
│   │   ├── realtime/              # 实时调度算法
│   │   └── base/                  # 调度器基类
│   ├── broker/                    # 云代理模块
│   ├── datacenter/                # 数据中心模块
│   └── util/                      # 工具类模块
├── docs/examples/                 # 文档示例与演示代码
│   └── CoroutineDemo.kt           # 协程性能演示
├── configs/                       # 配置文件目录
├── runs/                          # 实验结果目录
├── data/                          # 数据集目录
├── draw/                          # 可视化脚本目录
├── scripts/                       # 脚本目录
└── build.gradle.kts               # 构建脚本
```

### 添加新算法

1. 在 `scheduler/algorithms` 或 `scheduler/realtime` 中创建新算法类
2. 继承 `BaseScheduler` 或 `BaseRealtimeScheduler`
3. 实现抽象方法（如 `optimize`）
4. 在 `src/main/kotlin/scheduler/AlgorithmRegistry.kt` 中注册算法定义与别名
5. 在 `configs/algorithms.toml` 中添加算法配置

### Kotlin 维护约束

- 涉及 Kotlin 代码迁移、Gradle/Android/KMP 工具链迁移、Java 转 Kotlin、JPA/ORM 实体建模等专题改动时，必须先遵循本机 Codex 已安装的对应 Kotlin skill 指南。
- 若当前专题没有匹配的本地 Kotlin skill，则按本仓库既有 Kotlin 风格执行：保持 CLI、配置字段、CSV 表头兼容，优先小步重构，并确保 `ktlintCheck`、`detekt`、`test` 和覆盖率门禁通过。
- 新增 Kotlin 代码应优先复用现有模块边界和 helper，不引入与当前 CloudSim/调度实验无关的框架或运行时依赖。

### 扩展实验模式

1. 在 `src/main/kotlin/config/ExperimentConfig.kt` 中添加 `ExperimentMode`
2. 实现对应的 Runner 类
3. 在 `src/main/kotlin/cli/CliParser.kt` 与 `src/main/kotlin/cli/CommandExecutor.kt` 中注册命令行参数与执行逻辑
4. 添加相应的配置验证逻辑

---

## 🔄 CI/CD

项目配置了 GitHub Actions CI/CD，自动化构建、测试和发布流程。当前主分支在 Windows、Ubuntu、macOS 上执行 `fullCheck`、发布包 smoke 和阻断型 build warning audit，包含示例配置校验、格式检查、静态检查、测试、覆盖率报告和 fatJar 构建：

```yaml
# .github/workflows/ci.yml
- Windows / Ubuntu / macOS + JDK 25
- fullCheck / verifyReleasePackage / containerImageSmoke
- pwsh -File scripts/run-build-warning-audit.ps1
```

发布流程由 `.github/workflows/release.yml` 在 `v*.*.*` 标签或手动触发时执行，复用 Gradle release package tasks 生成可执行 JAR、Windows zip、Unix tar.gz、源码包和清单，并推送容器镜像到 GHCR。发布前验收清单见 `docs/release-readiness.md`。

warning audit 分别捕获 `compileKotlin`、`detekt`、`ktlintCheck` 和 CloudSim Plus Maven 源码构建日志，仅精确允许 detekt `1.23.8` 的 Gradle 10 deprecation 和 ktlint 内嵌 Kotlin compiler 的 JDK 25 Unsafe warning；任何新增 warning 都会阻断 CI。审计报告和原始日志保存在 `build/reports/build-warnings/` 并由 CI 始终上传。

每周 CloudSim Plus latest compatibility workflow 会在 Windows、Ubuntu、macOS 上测试最新 release，但不会自动修改 lock。发布清单记录实际 CloudSim Plus ref、commit、version 与全部资产；`buildSrc` JaCoCo 报告位于 `buildSrc/build/reports/jacoco/`，并执行 line 50% / branch 40% 门禁。根项目使用 `verifyJUnitTestSignatures` 阻止非 `Unit` 测试入口被静默忽略，并使用 `gradle/junit-test-inventory.lock` 精确跟踪测试入口；有意新增、删除或重命名测试后运行 `updateJUnitTestInventory` 更新清单。

### 迁移说明

旧兼容包装 `util.ResultsManager` 已删除，结果目录和 CSV 输出请使用 `util.ExperimentOutputContext`。旧常量包装 `datacenter.Constants` 已删除，数据中心参数请使用 `config.DatacenterConfig`；`datacenter.DatacenterType` 保持可用。

---

## 📄 许可证

MIT License - 可自由用于商业和个人项目。

--------

## 🤝 贡献

欢迎提交 Issue 和 Pull Request 来改进项目。对于学术合作，请联系作者。
