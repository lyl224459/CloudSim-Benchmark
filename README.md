# CloudSim-Benchmark

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![JDK](https://img.shields.io/badge/JDK-23+-blue.svg)](https://jdk.java.net/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.21-purple.svg)](https://kotlinlang.org/)
[![Gradle](https://img.shields.io/badge/Gradle-9.2.1-green.svg)](https://gradle.org/)
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
- ✅ **极致性能优化**：采用 **ZGC** 低延迟回收、**ND4J** 向量化计算、**Fastutil** 高性能集合。
- ✅ **协程并行加速**：基于 Kotlin 协程实现算法与试验的并行执行，加速比可达 5x-8x。
- ✅ **统一 CLI 接口**：全新的命名参数格式，支持 `--algorithms ALL` 一键运行，支持协程开关。
- ✅ **结构化结果管理**：自动生成带时间戳的实验快照与分算法原始 CSV 数据。
- ✅ **一键可视化**：内置 Jupyter Notebook，自动读取最新实验数据并绘图。
- ✅ **现代化构建**：自动检测 CPU 核心数，本地开发极速构建（无压缩），发布版本自动压缩。

---

## 🛠️ 系统要求
- **操作系统**: Windows (推荐), Linux, macOS
- **JDK**: 23 或更高版本 (全面兼容 JDK 24)
- **内存**: 建议分配 2GB+ 堆内存 (通过 Gradle 自动配置)

---

## 🚀 快速开始

### 1. 克隆与构建
```bash
git clone https://github.com/lyl224459/CloudSim-Benchmark.git
cd CloudSim-Benchmark
./run.cmd build    # Windows
./run build        # Linux/WSL
```

### 2. 运行实验 (统一命名参数格式)
项目采用现代化的命名参数接口，通过 `./run.cmd` (Windows) 或 `./run` (Linux) 启动。注意：不再支持旧的位置参数格式。

#### 🔹 基础对比实验 (Batch)
运行所有批处理算法，试验 3 次：
```bash
./run.cmd batch --algorithms ALL --runs 3
```

#### 🔹 实时调度实验 (Realtime)
指定特定算法并使用自定义随机种子：
```bash
./run.cmd realtime -a PSO_REALTIME,WOA_REALTIME -s 42 -r 5
```

#### 🔹 批量任务数扩展性实验 (Multi-Count)
研究算法在 50, 100, 200, 500 任务规模下的性能趋势：
```bash
./run.cmd batch-multi --tasks 50,100,200,500 -a ALL -r 3
```

#### 🔹 协程控制
显式指定最大并发数或使用顺序执行：
```bash
./run.cmd batch -a ALL -C 4        # 限制并发数为 4
./run.cmd batch -a ALL --sequential # 使用顺序执行模式
```

#### 🔹 参数速查表
| 参数 | 短参数 | 描述 | 必需 |
| :--- | :--- | :--- | :--- |
| `--algorithms` | `-a` | 算法列表 (如 `PSO,WOA` 或 `ALL`) | 是 |
| `--tasks` | `-t` | 任务数列表 (仅限 multi 模式) | 仅 multi |
| `--seed` | `-s` | 随机数种子 (默认 0) | 否 |
| `--runs` | `-r` | 每个配置的试验次数 (默认 1) | 否 |
| `--sequential` | `-S` | 禁用并行，切换到顺序执行模式 | 否 |
| `--concurrency`| `-C` | 限制并发协程数量 (默认 CPU 核心数) | 否 |
| `--help` | `-h` | 显示详细帮助信息 | 否 |

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

项目支持**分层配置系统**，加载优先级：命令行参数 > 环境变量 > `configs/*.toml` > 代码默认值。

### 外部配置文件 (TOML)
推荐通过 `configs/experiments/` 下的配置文件进行大规模实验：
```bash
./run.cmd batch --config configs/experiments/performance_test.toml
```

---

## ⚡ 性能优化深度解析

本项目针对仿真实验的“高频、大量、重复”特点进行了极致优化：

1.  **ZGC (低延迟回收)**：默认配置为 ZGC，消除大规模对象创建导致的 GC 长停顿，仿真耗时减少约 15%。
2.  **ND4J 向量化计算**：所有统计计算（标准差、均值等）和目标函数计算均采用 ND4J 调用 CPU SIMD 指令集，计算效率提升 3-5 倍。
3.  **协程并行架构**：
    - 算法之间异步执行。
    - 多次试验 (Trials) 异步并行。
    - 使用协程 Channel 实现非阻塞结果收集。
4.  **JVM 原生优化**：自动启用 `--enable-native-access` 和 `--add-opens`，消除 Unsafe 警告，提升原生库调用性能。

---

## 📈 实验结果与可视化

### 1. 结果存储 (YOLO 结构)
每次运行都会在 `runs/` 目录下创建独立文件夹：
```text
runs/batch/batch_20260111_092415/
├── experiment_info.txt    # 本次实验的完整参数快照
├── summary_avg.csv        # 所有算法的平均性能对比汇总
├── PSO.csv                # PSO 算法每轮试验的原始数据
├── Improved-RL.csv        # 改进 RL 算法每轮试验的原始数据
└── ...
```

### 2. 可视化分析
内置专业绘图工具 `draw/visualize_results.ipynb`：
- **多指标对比**: Makespan, Cost, LoadBalance, TotalTime。
- **稳定性分析**: 自动绘制箱线图 (Box Plots) 和散点图。
- **扩展性分析**: 绘制性能随任务数增长的折线图。

---

## 🐳 容器化支持 (Podman/Docker)

项目提供完整的容器化支持，确保在不同计算环境下实验结果的高度一致性。

### 1. 构建镜像
```bash
./gradlew podmanBuild
# 或直接使用 podman 命令
podman build -t cloudsim-benchmark .
```

### 2. 运行实验
脚本会自动创建 `benchmark_workspace` 目录并将 `src`, `data`, `configs`, `runs` 挂载到容器中：
```bash
# Windows (使用脚本)
./run.cmd podman batch -a ALL -s 42

# Linux/WSL (使用脚本)
./run podman batch -a ALL -s 42
```

容器镜像已同步发布至 **GitHub Container Registry (GHCR)**：
`ghcr.io/lyl224459/cloudsim-benchmark:latest`

---

## 🛠️ 开发指南

### 添加新调度算法
1.  在 `src/main/kotlin/scheduler/` 目录下创建新调度器类。
2.  在 `config/AlgorithmType.kt` 的枚举类中注册。
3.  在 `ComparisonRunner.kt` 或 `RealtimeComparisonRunner.kt` 的 `when` 分支中添加工厂逻辑。

### 贡献
欢迎提交 PR。请确保在提交前运行 `./gradlew test` 以通过所有单元测试。

---

## 🔄 CI/CD 持续集成

项目采用分层流水线设计：
- **CI 流水线** (`ci.yml`): 每次 Push 到 `main/dev` 分支时自动触发，执行 JDK 23 环境下的编译与测试验证。
- **Release 流水线** (`release.yml`): 推送版本标签（如 `v1.0.0`）时触发：
    - 自动构建**高压缩比**的发布版 JAR。
    - 打包 Windows 完整运行包。
    - 构建并推送 OCI 标准镜像至 **GHCR**。
    - 自动生成 GitHub Release。

---

## 📄 许可证
本项目采用 **MIT License**。

---
**⭐ 如果 CloudSim-Benchmark 帮助到了您的研究，请点一个 Star！**
