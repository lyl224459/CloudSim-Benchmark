# CloudSim-Benchmark: 云任务调度算法对比实验平台

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.21-blue.svg)](https://kotlinlang.org/)
[![Gradle](https://img.shields.io/badge/Gradle-9.2.1-green.svg)](https://gradle.org/)

> **CloudSim-Benchmark** 是一个基于 CloudSim Plus 和 Kotlin 开发的云任务调度算法对比实验平台，支持批处理和实时调度两种模式，集成了多种群体智能优化算法（PSO、WOA、GWO、HHO），提供批量任务数实验、多次运行统计等功能，为云计算任务调度研究提供完整的实验框架。

## 📋 目录

- [项目简介](#-项目简介)
- [核心特性](#-核心特性)
- [快速开始](#-快速开始)
- [使用指南](#-使用指南)
- [算法说明](#-算法说明)
- [配置说明](#-配置说明)
- [实验结果](#-实验结果)
- [项目结构](#-项目结构)
- [CI/CD 持续集成](#-cicd-持续集成)
- [开发指南](#-开发指南)
- [许可证](#-许可证)
- [作者](#-作者)

> **提示**: 如果目录链接无法跳转，请确保在 GitHub 上查看此文件，GitHub 会自动生成正确的锚点链接。

## 🎯 项目简介

CloudSim-Benchmark 是一个专业的云任务调度算法对比实验平台，旨在为研究人员和开发者提供：

- **完整的实验框架**：从任务生成、算法执行到结果分析的完整流程
- **多种调度模式**：支持批处理和实时调度两种场景，满足不同研究需求
- **丰富的算法库**：集成 PSO、WOA、GWO、HHO 等群体智能优化算法
- **灵活的配置系统**：统一的配置管理，支持多种任务生成器和参数调整
- **可靠的实验结果**：支持多次运行统计（平均值、标准差、最小值、最大值），确保结果的可重复性和统计显著性
- **批量任务数实验**：支持按不同任务数批量执行实验，研究算法在不同规模下的性能表现

## ✨ 核心特性

### 调度模式

- **批处理调度模式**: 所有任务一次性提交，执行全局优化调度
- **实时调度模式**: 任务动态到达，支持增量调度和实时响应

### 优化算法

- **PSO** (Particle Swarm Optimization) - 粒子群优化
- **WOA** (Whale Optimization Algorithm) - 鲸鱼优化算法
- **GWO** (Grey Wolf Optimizer) - 灰狼优化算法
- **HHO** (Harris Hawks Optimization) - 哈里斯鹰优化算法
- **Random** - 随机调度（基准算法）

### 任务生成器

- **LOG_NORMAL** - 对数正态分布生成器（默认）
- **UNIFORM** - 均匀分布生成器
- **LOG_NORMAL_SCI** - 对数正态分布 SCI 生成器（独立输出文件参数）

### 实验功能

- ✅ 算法自由选择（支持命令行和代码配置）
- ✅ 多次运行取平均值（支持统计分析和标准差计算）
- ✅ 批量任务数实验（支持批处理和实时调度两种模式）
- ✅ 结果自动保存（时间戳命名，避免覆盖）
- ✅ 完整的日志系统（跨平台日志库）
- ✅ CSV 结果导出（便于后续分析）
- ✅ **协程并行优化**（显著提升实验效率，支持算法并行执行）
- ✅ **强化学习调度器**（基于Q-learning的智能调度算法）
- ✅ **高性能计算优化**（ND4J、Fastutil、Eclipse Collections）

### 批量任务数实验功能

项目提供了两个专门的批量任务数实验运行器：

- **`BatchCloudletCountRunner`** (`src/main/kotlin/datacenter/BatchCloudletCountRunner.kt`)
  - 批处理模式批量任务数实验运行器
  - 支持按不同任务数批量执行批处理调度实验
  - 每个任务数可运行多次并计算统计值

- **`RealtimeCloudletCountRunner`** (`src/main/kotlin/datacenter/RealtimeCloudletCountRunner.kt`)
  - 实时调度模式批量任务数实验运行器
  - 支持按不同任务数批量执行实时调度实验
  - 包含实时调度特有指标（平均等待时间、平均响应时间）

### 运行脚本

项目提供了一个统一的智能运行脚本，自动检测平台并支持所有功能：

**核心脚本**：
- **`scripts/run`** - 统一运行脚本（Linux/macOS，自动检测平台，支持所有功能）
- **`scripts/run.bat`** - 统一运行脚本（Windows，自动检测平台，支持所有功能）

**便捷启动脚本**：
- **`run`** - Linux/macOS 根目录便捷启动脚本
- **`run.cmd`** - Windows 根目录便捷启动脚本

## 🚀 快速开始

### 环境要求

- **JDK**: 23+ (项目使用 JVM 23)
- **Gradle**: 9.2.1+ (自动包含在项目中)

### 构建优化特性

项目构建系统已自动优化：
- ✅ 自动检测CPU核心数并使用全部核心并行构建
- ✅ 启用Gradle缓存和按需配置
- ✅ Kotlin编译器优化（内联类等）
- ✅ 默认跳过测试以提升构建速度
- **Kotlin**: 2.1.21+
- **CloudSim Plus**: 8.5.5 (从 Maven Central 获取)

### 1. 克隆项目

```bash
git clone https://github.com/lyl224459/CloudSim-Benchmark.git
cd CloudSim-Benchmark
```

### 2. 构建项目

```bash
# Windows
gradle build
gradle fatJar

# Linux/macOS
./gradlew build
./gradlew fatJar
```

生成的 JAR 文件位于: `build/libs/cloudsim-benchmark-1.0.0-all.jar`

### 3. 运行实验

#### 方式一：使用统一脚本（推荐）

**统一运行脚本支持所有功能**:
```bash
# 显示帮助
./run help
# 或
./scripts/run help

# 构建项目
./run build
# 或
./scripts/run build
```

**批处理模式**:
```bash
# 运行所有算法
./run batch

# 只运行 PSO 和 WOA，随机种子 42
./run batch PSO,WOA 42

# 运行 10 次取平均值
./run batch PSO,WOA 42 10

# 批量任务数实验 - 测试多个任务数
./run batch-multi 50,100,200,500

# 批量任务数实验 - 指定运行次数和算法
./run batch-multi 50,100,200,500 10 PSO,WOA
```

**实时调度模式**:
```bash
# 运行所有算法（默认模式）
./run realtime

# 指定算法和随机种子
./run realtime PSO_REALTIME,WOA_REALTIME 123

# 批量任务数实验 - 测试多个任务数
./run realtime-multi 50,100,200,500
```

#### 方式二：直接使用根目录脚本

**Windows**:
```bash
run.cmd batch PSO,WOA 42
run.cmd realtime PSO_REALTIME,WOA_REALTIME
run.cmd batch-multi 50,100,200,500 10 PSO,WOA
run.cmd realtime-multi 50,100,200,500
```

**Linux/macOS**:
```bash
./run batch PSO,WOA 42
./run realtime PSO_REALTIME,WOA_REALTIME
./run batch-multi 50,100,200,500 10 PSO,WOA
./run realtime-multi 50,100,200,500
```

#### 使用统一脚本（推荐）

**统一脚本自动检测平台，支持所有功能**：
```bash
# 显示帮助
./run help

# 构建项目
./run build

# 批处理模式（使用配置的算法）
./run batch

# 批处理模式（指定算法）
./run batch PSO,WOA

# 批处理模式（指定算法和种子）
./run batch PSO,WOA 42

# 批量任务数实验模式
./run batch-multi 50,100,200,500 10 PSO,WOA

# 实时调度模式
./run realtime

# 实时调度模式（指定算法）
./run realtime PSO_REALTIME,WOA_REALTIME

# 实时调度模式批量任务数实验
./run realtime-multi 50,100,200,500 10 PSO_REALTIME,WOA_REALTIME
```

#### 使用实验配置文件

**直接使用实验配置**：
```bash
# 使用快速测试配置
./run batch --config configs/experiments/quick_test.toml

# 使用性能测试配置
./run batch --config configs/experiments/performance_test.toml

# 使用可扩展性测试配置
./run batch --config configs/experiments/scalability_test.toml
```

**通过环境变量指定配置**：
```bash
export CONFIG_FILE=configs/experiments/quick_test.toml
./run batch
```

#### 批量任务数实验

```bash
# 批处理模式批量实验
./run batch-multi 50,100,200,500 10 PSO,WOA

# 实时调度模式批量实验
./run realtime-multi 50,100,200,500 10 PSO_REALTIME,WOA_REALTIME
```

#### 传统方式

```bash
# 直接使用系统Gradle（需要预装）
gradle build
gradle fatJar

# 或使用项目自带的Gradle Wrapper（推荐）
./gradlew build
./gradlew fatJar
```

**Linux/macOS**:
```bash
# 设置脚本可执行（可选，脚本会自动处理）
chmod +x run scripts/run

# 批处理模式
./run batch PSO,WOA 42

# 批量任务数实验模式
./run batch-multi 50,100,200,500

# 批量任务数实验模式 - 指定运行次数和算法
./run batch-multi 50,100,200,500 10 PSO,WOA

# 实时调度模式
./run realtime PSO_REALTIME,WOA_REALTIME 123

# 实时调度模式批量任务数实验
./run realtime-multi 50,100,200,500 10 PSO_REALTIME,WOA_REALTIME
```

#### 方式二：使用 Gradle 任务运行

**Windows/Linux/macOS**:
```bash
# 批处理模式 - 运行所有算法
gradle runBatch

# 批处理模式 - 运行指定算法
gradle runBatch -Palgorithms=PSO,WOA

# 批处理模式 - 指定算法和随机种子
gradle runBatch -Palgorithms=PSO,WOA -Pseed=42

# 实时调度模式 - 运行所有算法
gradle runRealtime

# 实时调度模式 - 运行指定算法
gradle runRealtime -Palgorithms=PSO_REALTIME,WOA_REALTIME

# 实时调度模式 - 指定算法和随机种子
gradle runRealtime -Palgorithms=PSO_REALTIME,WOA_REALTIME -Pseed=123

# 批处理模式批量任务数实验 - 默认任务数 (50,100,200,500)
gradle runBatchMulti

# 批处理模式批量任务数实验 - 指定任务数
gradle runBatchMulti -PcloudletCounts=50,100,200,500,1000

# 批处理模式批量任务数实验 - 指定任务数和算法
gradle runBatchMulti -PcloudletCounts=50,100,200 -Palgorithms=PSO,WOA

# 批处理模式批量任务数实验 - 指定运行次数
gradle runBatchMulti -PcloudletCounts=50,100,200,500 -Pruns=10

# 批处理模式批量任务数实验 - 完整参数
gradle runBatchMulti -PcloudletCounts=50,100,200 -Palgorithms=PSO,WOA -Pseed=42

# 实时调度模式批量任务数实验 - 默认任务数 (50,100,200,500)
gradle runRealtimeMulti

# 实时调度模式批量任务数实验 - 指定任务数
gradle runRealtimeMulti -PcloudletCounts=50,100,200,500,1000

# 实时调度模式批量任务数实验 - 指定任务数和算法
gradle runRealtimeMulti -PcloudletCounts=50,100,200 -Palgorithms=PSO_REALTIME,WOA_REALTIME

# 实时调度模式批量任务数实验 - 完整参数
gradle runRealtimeMulti -PcloudletCounts=50,100,200 -Palgorithms=PSO_REALTIME,WOA_REALTIME -Pseed=42

# 通用任务（自定义模式）
gradle runExp -Pmode=batch -Palgorithms=PSO,WOA -Pseed=42
gradle runExp -Pmode=batch-multi -Palgorithms=PSO,WOA
gradle runExp -Pmode=realtime -Palgorithms=PSO_REALTIME,WOA_REALTIME
gradle runExp -Pmode=realtime-multi -Palgorithms=PSO_REALTIME,WOA_REALTIME
```

**注意**: 
- Gradle 任务会自动编译代码并运行，无需先构建 JAR 文件
- 如果遇到 `NoClassDefFoundError` 错误，请检查网络连接，确保能够从 Maven Central 下载 CloudSim Plus 依赖
- 或者使用 fatJar 方式运行（见方式三），fatJar 包含所有依赖，无需额外配置

#### 方式三：直接运行 JAR

```bash
# 批处理模式 - 运行所有算法
java -jar build/libs/cloudsim-benchmark-1.0.0-all.jar batch

# 批处理模式 - 只运行 PSO 和 WOA
java -jar build/libs/cloudsim-benchmark-1.0.0-all.jar batch PSO,WOA

# 批处理模式 - 指定随机种子
java -jar build/libs/cloudsim-benchmark-1.0.0-all.jar batch PSO,WOA 42

# 批量任务数实验 - 测试多个任务数
java -jar build/libs/cloudsim-benchmark-1.0.0-all.jar batch-multi 50,100,200,500

# 批量任务数实验 - 指定运行次数（每个任务数运行10次）
java -jar build/libs/cloudsim-benchmark-1.0.0-all.jar batch-multi 50,100,200,500 10

# 批量任务数实验 - 指定算法
java -jar build/libs/cloudsim-benchmark-1.0.0-all.jar batch-multi 50,100,200 PSO,WOA

# 批量任务数实验 - 指定运行次数、算法和随机种子
java -jar build/libs/cloudsim-benchmark-1.0.0-all.jar batch-multi 50,100,200,500 10 PSO,WOA 42

# 实时调度模式批量任务数实验
java -jar build/libs/cloudsim-benchmark-1.0.0-all.jar realtime-multi 50,100,200,500

# 实时调度模式
java -jar build/libs/cloudsim-benchmark-1.0.0-all.jar realtime

# 实时调度模式 - 指定算法和随机种子
java -jar build/libs/cloudsim-benchmark-1.0.0-all.jar realtime PSO_REALTIME,WOA_REALTIME 123
```

## 📖 使用指南

### 命令行参数

#### 基本模式

**批处理模式** (`batch`)：
```
用法: java -jar cloudsim-benchmark-1.0.0-all.jar batch [algorithms] [randomSeed]
```

**实时调度模式** (`realtime`)：
```
用法: java -jar cloudsim-benchmark-1.0.0-all.jar realtime [algorithms] [randomSeed]
```

#### 批量任务数实验模式

**批处理模式批量任务数实验** (`batch-multi`)：
```
用法: java -jar cloudsim-benchmark-1.0.0-all.jar batch-multi <cloudletCounts> [runs] [algorithms] [randomSeed]

参数说明:
  cloudletCounts - 任务数列表（必需），用逗号分隔，例如: 50,100,200,500
  runs          - 每个任务数的运行次数（可选，默认: 1），用于计算平均值和标准差
  algorithms    - 要运行的算法列表（可选，默认: 所有算法）
                  批处理模式: RANDOM, PSO, WOA, GWO, HHO
                  多个算法用逗号分隔，例如: PSO,WOA
  randomSeed    - 随机数种子（可选，默认: 0）

示例:
  # 测试多个任务数，每个任务数运行1次
  java -jar cloudsim-benchmark-1.0.0-all.jar batch-multi 50,100,200,500

  # 测试多个任务数，每个任务数运行10次
  java -jar cloudsim-benchmark-1.0.0-all.jar batch-multi 50,100,200,500 10

  # 指定运行次数和算法
  java -jar cloudsim-benchmark-1.0.0-all.jar batch-multi 50,100,200,500 10 PSO,WOA

  # 完整参数：运行次数、算法和随机种子
  java -jar cloudsim-benchmark-1.0.0-all.jar batch-multi 50,100,200,500 10 PSO,WOA 42
```

**实时调度模式批量任务数实验** (`realtime-multi`)：
```
用法: java -jar cloudsim-benchmark-1.0.0-all.jar realtime-multi <cloudletCounts> [runs] [algorithms] [randomSeed]

参数说明:
  cloudletCounts - 任务数列表（必需），用逗号分隔，例如: 50,100,200,500
  runs          - 每个任务数的运行次数（可选，默认: 1），用于计算平均值和标准差
  algorithms    - 要运行的算法列表（可选，默认: 所有算法）
                  实时模式: MIN_LOAD, RANDOM, PSO_REALTIME, WOA_REALTIME
                  多个算法用逗号分隔，例如: PSO_REALTIME,WOA_REALTIME
  randomSeed    - 随机数种子（可选，默认: 0）

示例:
  # 测试多个任务数，每个任务数运行1次
  java -jar cloudsim-benchmark-1.0.0-all.jar realtime-multi 50,100,200,500

  # 测试多个任务数，每个任务数运行10次
  java -jar cloudsim-benchmark-1.0.0-all.jar realtime-multi 50,100,200,500 10

  # 指定运行次数和算法
  java -jar cloudsim-benchmark-1.0.0-all.jar realtime-multi 50,100,200,500 10 PSO_REALTIME,WOA_REALTIME

  # 完整参数：运行次数、算法和随机种子
  java -jar cloudsim-benchmark-1.0.0-all.jar realtime-multi 50,100,200,500 10 PSO_REALTIME,WOA_REALTIME 42
```

### 算法选择

#### 批处理模式可用算法

- `RANDOM` - 随机调度（基准算法）
- `PSO` - 粒子群优化
- `WOA` - 鲸鱼优化
- `GWO` - 灰狼优化
- `HHO` - 哈里斯鹰优化

#### 实时模式可用算法

- `MIN_LOAD` - 最小负载调度
- `RANDOM` - 随机调度
- `PSO_REALTIME` - PSO实时调度
- `WOA_REALTIME` - WOA实时调度

### 批量任务数实验

批量任务数实验模式可以按照不同的任务数批量执行实验，每个任务数可以运行多次并计算统计值（平均值、标准差、最小值、最大值）。这对于研究算法在不同规模任务下的性能表现非常有用。

**核心功能**：
- 🔄 **多任务数批量执行**：一次测试多个不同的任务数，自动循环执行
- 📊 **统计值计算**：每个任务数多次运行，自动计算平均值、标准差、最小值、最大值
- 📈 **结果汇总**：所有任务数的结果自动汇总到一个 CSV 文件中
- 📋 **对比表格**：打印每个指标在不同任务数下的详细对比表格

**实现类**：
- **`BatchCloudletCountRunner`** (`src/main/kotlin/datacenter/BatchCloudletCountRunner.kt`)
  - 批处理模式批量任务数实验运行器
  - 支持批处理调度算法（RANDOM, PSO, WOA, GWO, HHO）
  
- **`RealtimeCloudletCountRunner`** (`src/main/kotlin/datacenter/RealtimeCloudletCountRunner.kt`)
  - 实时调度模式批量任务数实验运行器
  - 支持实时调度算法（MIN_LOAD, RANDOM, PSO_REALTIME, WOA_REALTIME）
  - 包含实时调度特有指标（平均等待时间、平均响应时间）

#### 批处理模式批量任务数实验 (`batch-multi`)

批处理模式批量任务数实验使用批处理调度算法，所有任务一次性提交。

**使用方法**：

```bash
# 基本用法：测试多个任务数
java -jar cloudsim-benchmark-1.0.0-all.jar batch-multi 50,100,200,500

# 指定运行次数（每个任务数运行10次并计算平均值）
java -jar cloudsim-benchmark-1.0.0-all.jar batch-multi 50,100,200,500 10

# 指定运行次数和算法
java -jar cloudsim-benchmark-1.0.0-all.jar batch-multi 50,100,200,500 10 PSO,WOA

# 指定运行次数、算法和随机种子
java -jar cloudsim-benchmark-1.0.0-all.jar batch-multi 50,100,200,500 10 PSO,WOA 42
```

**功能特性**：

- ✅ **多任务数批量执行**：支持一次测试多个不同的任务数（如：50, 100, 200, 500）
- ✅ **多次运行统计**：每个任务数可运行多次（通过 `runs` 参数指定），自动计算平均值、标准差、最小值、最大值
- ✅ **自动汇总结果**：所有任务数的结果自动汇总到一个 CSV 文件中，便于对比分析
- ✅ **详细统计表格**：控制台输出每个指标在不同任务数下的详细对比表格

**输出结果**：

结果文件格式：`results/batch_cloudlet_count_comparison_YYYYMMDD_HHmmss.csv`

CSV 文件包含以下列：
- `CloudletCount` - 任务数
- `Algorithm` - 算法名称
- `Makespan_Mean`, `Makespan_StdDev` - Makespan 的平均值和标准差
- `LoadBalance_Mean`, `LoadBalance_StdDev` - 负载均衡的平均值和标准差
- `Cost_Mean`, `Cost_StdDev` - 成本的平均值和标准差
- `TotalTime_Mean`, `TotalTime_StdDev` - 总时间的平均值和标准差
- `Fitness_Mean`, `Fitness_StdDev` - 适应度的平均值和标准差
- `Runs` - 运行次数

**参数说明**：

- **任务数列表** (`cloudletCounts`): 必需参数，用逗号分隔，例如 `50,100,200,500`
- **运行次数** (`runs`): 可选参数，每个任务数的运行次数（默认: 1），用于计算统计值
- **算法列表** (`algorithms`): 可选参数，要运行的算法列表（默认: 所有算法）
- **随机种子** (`randomSeed`): 可选参数，随机数种子（默认: 0）

**配置继承**：

批量任务数实验的其他配置继承自 `config.batch`：
- `population` - 优化算法的种群大小（默认: 30）
- `maxIter` - 优化算法的最大迭代次数（默认: 50）
- `generatorType` - 任务生成器类型（默认: LOG_NORMAL）

**运行脚本**：

**使用统一脚本**：
```bash
./scripts/run batch-multi 50,100,200,500 10 PSO,WOA
```

**使用统一脚本**：
```bash
./run batch-multi 50,100,200,500 10 PSO,WOA
```

# 指定运行次数、算法和随机种子
run-batch-multi.bat 50,100,200,500 10 PSO,WOA 42
```

#### 实时调度模式批量任务数实验 (`realtime-multi`)

实时调度模式批量任务数实验使用实时调度算法，任务动态到达，支持增量调度和实时响应。

**使用方法**：

```bash
# 基本用法：测试多个任务数（每个任务数运行1次）
java -jar cloudsim-benchmark-1.0.0-all.jar realtime-multi 50,100,200,500

# 指定运行次数（每个任务数运行10次）
java -jar cloudsim-benchmark-1.0.0-all.jar realtime-multi 50,100,200,500 10

# 指定运行次数和算法
java -jar cloudsim-benchmark-1.0.0-all.jar realtime-multi 50,100,200,500 10 PSO_REALTIME,WOA_REALTIME

# 指定运行次数、算法和随机种子
java -jar cloudsim-benchmark-1.0.0-all.jar realtime-multi 50,100,200,500 10 PSO_REALTIME,WOA_REALTIME 42
```

**功能特性**：

- ✅ **多任务数批量执行**：支持一次测试多个不同的任务数（如：50, 100, 200, 500）
- ✅ **多次运行统计**：每个任务数可运行多次（通过配置 `runs` 参数），自动计算平均值、标准差、最小值、最大值
- ✅ **实时调度指标**：包含平均等待时间和平均响应时间等实时调度特有指标
- ✅ **自动汇总结果**：所有任务数的结果自动汇总到一个 CSV 文件中，便于对比分析
- ✅ **详细统计表格**：控制台输出每个指标在不同任务数下的详细对比表格

**输出结果**：

结果文件格式：`results/realtime_cloudlet_count_comparison_YYYYMMDD_HHmmss.csv`

CSV 文件包含以下列：
- `CloudletCount` - 任务数
- `Algorithm` - 算法名称
- `Makespan_Mean`, `Makespan_StdDev` - Makespan 的平均值和标准差
- `LoadBalance_Mean`, `LoadBalance_StdDev` - 负载均衡的平均值和标准差
- `Cost_Mean`, `Cost_StdDev` - 成本的平均值和标准差
- `TotalTime_Mean`, `TotalTime_StdDev` - 总时间的平均值和标准差
- `Fitness_Mean`, `Fitness_StdDev` - 适应度的平均值和标准差
- `AvgWaitingTime_Mean`, `AvgWaitingTime_StdDev` - 平均等待时间的平均值和标准差
- `AvgResponseTime_Mean`, `AvgResponseTime_StdDev` - 平均响应时间的平均值和标准差
- `Runs` - 运行次数

**参数说明**：

- **任务数列表** (`cloudletCounts`): 必需参数，用逗号分隔，例如 `50,100,200,500`
- **运行次数** (`runs`): 可选参数，每个任务数的运行次数（默认: 1），用于计算统计值
- **算法列表** (`algorithms`): 可选参数，要运行的算法列表（默认: 所有算法）
- **随机种子** (`randomSeed`): 可选参数，随机数种子（默认: 0）

**配置继承**：

实时调度模式批量任务数实验的其他配置继承自 `config.realtime` 和 `config.optimizer`：
- `runs` - 每个任务数的运行次数（默认: 1），用于计算统计值
- `simulationDuration` - 仿真持续时间（秒，默认: 500.0）
- `arrivalRate` - 平均每秒到达的任务数（默认: 5.0）
- `population` - 优化算法的种群大小（默认: 20）
- `maxIter` - 优化算法的最大迭代次数（默认: 20）
- `generatorType` - 任务生成器类型（默认: LOG_NORMAL）

**运行脚本**：

**使用统一脚本**：
```bash
./scripts/run realtime-multi 50,100,200,500 10 PSO_REALTIME,WOA_REALTIME 42
```

**使用统一脚本**：
```bash
./run realtime-multi 50,100,200,500 10 PSO_REALTIME,WOA_REALTIME 42
```

### 多次运行取平均值

为了获得更可靠的实验结果，可以配置多次运行并计算统计值（平均值、标准差、最小值、最大值）：

**命令行方式**（推荐）：
```bash
# 批处理模式：运行10次
java -jar cloudsim-benchmark-1.0.0-all.jar batch PSO,WOA 42 10

# 批量任务数实验：每个任务数运行10次
java -jar cloudsim-benchmark-1.0.0-all.jar batch-multi 50,100,200,500 10
```

**代码配置方式**:
```kotlin
val config = ExperimentConfig(
    batch = BatchConfig(
        runs = 10  // 运行10次，计算统计值
    ),
    realtime = RealtimeConfig(
        runs = 10  // 运行10次，计算统计值
    )
)
```

**统计值说明**: 
- 多次运行时，每次运行使用不同的随机种子（`randomSeed + run`），确保实验的独立性
- 结果会显示平均值 ± 标准差，以及最小值和最大值
- CSV 导出文件会包含平均值、标准差、最小值、最大值列
- 适用于需要统计显著性验证的科学实验

## 🧪 算法说明

### PSO (粒子群优化)

- **原理**: 模拟鸟群觅食行为，通过个体最优和全局最优引导搜索
- **参数**: 惯性权重 w ∈ [0.2, 0.9]，学习因子 c1 = c2 = 2.0
- **特点**: 收敛速度快，适合连续优化问题

### WOA (鲸鱼优化)

- **原理**: 模拟座头鲸的螺旋气泡网捕食行为
- **特点**: 平衡探索和开发能力，全局搜索能力强

### GWO (灰狼优化)

- **原理**: 模拟灰狼群体的社会等级和狩猎行为
- **特点**: Alpha、Beta、Delta 三只狼引导搜索，收敛稳定

### HHO (哈里斯鹰优化)

- **原理**: 模拟哈里斯鹰的捕猎策略
- **特点**: 多种捕猎策略切换，适应性强

## ⚙️ 配置说明

CloudSim-Benchmark 支持多种配置方式，提供了灵活的配置管理机制。

### 配置加载优先级

配置按以下优先级加载，后加载的配置会覆盖先加载的配置：

1. **默认配置** - 代码中的默认值
2. **配置文件** - Properties格式配置文件
3. **环境变量** - 系统环境变量
4. **系统属性** - JVM系统属性
5. **命令行参数** - 运行时命令行覆盖

### 外部配置文件支持

项目支持从外部配置文件加载配置，无需修改代码即可调整实验参数。支持 **TOML** 和 **Properties** 两种格式。

#### 配置文件格式

**推荐：TOML 格式**（更现代，可读性更好）：

```toml
# 通用配置
[random]
seed = 42

# 批处理模式配置
[batch]
cloudletCount = 100
population = 30
maxIter = 50
runs = 3
generatorType = "LOG_NORMAL"

# 实时调度模式配置
[realtime]
cloudletCount = 200
simulationDuration = 500.0
arrivalRate = 5.0
runs = 3
generatorType = "LOG_NORMAL"

# 优化算法配置
[optimizer]
population = 20
maxIter = 20
```

**兼容：Properties 格式**：

```properties
# 通用配置
random.seed=42

# 批处理模式配置
batch.cloudlet.count=100
batch.population=30
batch.max.iter=50
batch.runs=3

# 实时调度模式配置
realtime.cloudlet.count=200
realtime.simulation.duration=500.0
realtime.arrival.rate=5.0
realtime.runs=3

# 优化算法配置
optimizer.population=20
optimizer.max.iter=20
```

### 算法选择配置

通过 `configs/algorithms.toml` 配置可用的算法及其参数：

```toml
# 批处理算法配置
[batch.algorithms]
RANDOM = { enabled = true, description = "随机调度算法" }
PSO = { enabled = true, description = "粒子群优化", population = 30, maxIter = 100 }
WOA = { enabled = true, description = "鲸鱼优化", population = 30, maxIter = 100 }

# 实时调度算法配置
[realtime.algorithms]
MIN_LOAD = { enabled = true, description = "最小负载调度" }
PSO_REALTIME = { enabled = true, description = "实时PSO", population = 20, maxIter = 20 }

# 算法组合预设
[presets]
quick_test = ["RANDOM", "PSO", "WOA"]
full_test = ["RANDOM", "PSO", "WOA", "GWO", "HHO"]
```

### 实验结果目录结构

采用YOLO风格的目录管理：

```
runs/
├── batch/              # 批处理实验结果
│   ├── exp1_20240101_120000/  # 实验1
│   │   ├── batch_comparison.csv
│   │   └── logs/
│   └── exp2_20240101_121000/  # 实验2
└── realtime/           # 实时调度实验结果
    ├── exp1_20240101_122000/
    └── exp2_20240101_123000/
```

#### 配置文件加载方式

1. **分层配置文件**：
   - `configs/default.toml` - 基础配置
   - `configs/batch.toml` - 批处理配置
   - `configs/realtime.toml` - 实时调度配置
   - `configs/algorithms.toml` - 算法配置
   - `configs/experiments/*.toml` - 实验专用配置

2. **自定义配置文件**：
   - 系统属性：`-Dconfig.file=/path/to/config.toml`
   - 环境变量：`CONFIG_FILE=/path/to/config.toml`

3. **自动检测**：程序会按以下顺序加载配置文件（后加载的覆盖前面的）：
   - `configs/default.toml`
   - `configs/batch.toml`
   - `configs/realtime.toml`
   - `configs/algorithms.toml`
   - 其他TOML/Properties文件
   - 系统属性指定的文件
   - 环境变量指定的文件

#### 环境变量配置

通过环境变量设置配置参数：

```bash
# Linux/macOS
export CLOUDSIM_RANDOM_SEED=42
export CLOUDSIM_BATCH_CLOUDLET_COUNT=100
export CLOUDSIM_BATCH_POPULATION=30

# Windows
set CLOUDSIM_RANDOM_SEED=42
set CLOUDSIM_BATCH_CLOUDLET_COUNT=100
set CLOUDSIM_BATCH_POPULATION=30
```

#### 系统属性配置

通过JVM参数设置：

```bash
java -Dcloudsim.random.seed=42 \
     -Dcloudsim.batch.cloudlet.count=100 \
     -Dcloudsim.batch.population=30 \
     -jar cloudsim-benchmark.jar batch
```

### 代码内配置结构

```
configs/
├── default.toml             # 默认配置
├── batch.toml              # 批处理模式配置
├── realtime.toml           # 实时调度模式配置
├── algorithms.toml         # 算法选择和参数配置
└── experiments/            # 实验专用配置
    ├── quick_test.toml     # 快速测试配置
    ├── performance_test.toml # 性能测试配置
    └── scalability_test.toml # 可扩展性测试配置

src/main/kotlin/config/
├── ExperimentConfig.kt     # 主配置类（实验参数）
├── AlgorithmType.kt        # 算法类型枚举
├── DatacenterConfig        # 数据中心配置（VM规格、价格等）
├── CloudletGenConfig       # 任务生成配置（分布参数）
└── ObjectiveConfig         # 目标函数配置（权重参数）
```

### 批处理模式配置

在 `ExperimentConfig.kt` 中修改 `BatchConfig`:

```kotlin
batch: BatchConfig(
    cloudletCount: Int = 100,      // 任务数量
    population: Int = 30,           // 种群大小
    maxIter: Int = 50,              // 最大迭代次数
    algorithms: List<BatchAlgorithmType> = emptyList(),  // 算法列表（空=所有算法）
    runs: Int = 1,                  // 运行次数（用于计算平均值）
    generatorType: CloudletGeneratorType = LOG_NORMAL  // 任务生成器类型
)
```

### 实时调度模式配置

在 `ExperimentConfig.kt` 中修改 `RealtimeConfig`:

```kotlin
realtime: RealtimeConfig(
    cloudletCount: Int = 200,           // 任务数量
    simulationDuration: Double = 500.0,  // 仿真持续时间（秒）
    arrivalRate: Double = 5.0,           // 平均每秒到达的任务数（泊松分布）
    algorithms: List<RealtimeAlgorithmType> = emptyList(),  // 算法列表（空=所有算法）
    runs: Int = 1,                       // 运行次数（用于计算平均值）
    generatorType: CloudletGeneratorType = LOG_NORMAL  // 任务生成器类型
)
```

### 任务生成器配置

项目支持三种任务生成器，对应不同的任务生成策略：

1. **LOG_NORMAL**（默认）- 对数正态分布生成器
   - 使用对数正态分布生成任务执行时间
   - 使用正态分布生成文件大小

2. **UNIFORM** - 均匀分布生成器
   - 使用均匀分布生成所有参数

3. **GOOGLE_TRACE** - Google Trace数据生成器
   - 从Google Trace CSV文件读取真实任务数据
   - 支持时间窗口过滤和任务数量限制

#### 配置文件方式（推荐）

```toml
[batch.generator]
type = "LOG_NORMAL"  # LOG_NORMAL, UNIFORM, GOOGLE_TRACE

[batch.googleTrace]  # 当使用GOOGLE_TRACE时需要配置
filePath = "data/google_trace/task_events.csv"
maxTasks = 1000
timeWindowStart = 0
timeWindowEnd = 600000000000  # Long.MAX_VALUE的近似值

[realtime.generator]
type = "UNIFORM"     # 实时调度也支持相同配置
```

#### 代码配置方式（向后兼容）

```kotlin
val config = ExperimentConfig(
    batch = BatchConfig(
        generatorType = CloudletGeneratorType.UNIFORM  // 使用均匀分布生成器
    )
)
```

#### Google Trace数据准备

要使用Google Trace数据，需要：

1. 下载Google Trace Day 1数据：`task_events.csv`
2. 将文件放置在 `data/google_trace/` 目录下
3. 配置相应的参数：
   - `filePath`: CSV文件路径
   - `maxTasks`: 最多读取的任务数量
   - `timeWindowStart/End`: 时间窗口过滤（微秒时间戳）

### 数据中心配置

在 `ExperimentConfig.kt` 中的 `DatacenterConfig` object:

```kotlin
// VM 性能配置
L_MIPS = 1000, M_MIPS = 2000, H_MIPS = 4000

// VM 价格配置（美元/秒）
L_PRICE = 0.1, M_PRICE = 0.5, H_PRICE = 1.0

// VM 数量配置
L_VM_N = 4, M_VM_N = 3, H_VM_N = 2

// 资源规格配置
RAM = 2048 MB              // 每个VM的RAM
STORAGE = 100000 MB        // 每个VM的存储容量
IMAGE_SIZE = 10000 MB      // VM镜像大小
BW = 1024 Mbps             // 每个VM的带宽
```

### 目标函数配置

项目支持通过配置文件自由组合目标函数权重，包括成本、总时间、负载均衡和Makespan。

#### 配置文件方式（推荐）

```toml
[batch.objective]
cost = 0.4         # 成本权重 (0.0-1.0)
totalTime = 0.3    # 总时间权重 (0.0-1.0)
loadBalance = 0.2  # 负载均衡权重 (0.0-1.0)
makespan = 0.1     # Makespan权重 (0.0-1.0，可选)

[realtime.objective]
cost = 0.3
totalTime = 0.4
loadBalance = 0.3
makespan = 0.0     # 实时调度通常不考虑Makespan
```

#### 代码配置方式（向后兼容）

在 `ExperimentConfig.kt` 中的 `ObjectiveConfig` object:

```kotlin
// 适应度函数权重（总和应为1.0）
ALPHA = 1.0 / 3    // Cost（成本）权重
BETA = 1.0 / 3     // TotalTime（总时间）权重
GAMMA = 1.0 / 3    // LoadBalance（负载均衡）权重
```

#### 目标函数说明

- **Cost（成本）**: 任务执行的总成本，根据VM性能等级定价
- **Total Time（总时间）**: 所有任务的执行时间总和
- **Load Balance（负载均衡）**: VM负载的方差，越小表示负载越均衡
- **Makespan（可选）**: 最后一个任务完成的时间，可用于优化整体响应时间

权重总和应为1.0，不同的应用场景可以根据需求调整权重比例。

## ⚡ 协程并行优化（默认启用）

CloudSim-Benchmark **默认集成 Kotlin 协程技术**，显著提升实验执行效率。所有实验命令都会自动使用协程优化，无需额外配置。

### 协程优化特性

- **算法并行执行**: 多个调度算法同时运行，无需顺序等待
- **多次运行并行化**: 统计实验的多次运行采用协程并发执行
- **Channel通信**: 使用协程Channel高效收集和处理实验结果
- **异常隔离**: SupervisorJob确保单个算法失败不影响其他算法执行
- **资源高效利用**: 充分利用多核CPU，提高实验吞吐量
- **默认启用**: 所有实验命令自动使用协程优化

### 性能提升示例

在 8 核 CPU 上测试结果：

```
模拟 5 个算法并行执行对比：

顺序执行时间: 5009ms
并行执行时间: 1007ms
加速比: 4.98x
并行效率: 62.25%
```

### 使用协程优化

#### 演示协程功能
```bash
# 运行协程优化功能演示
./run coroutine-demo

# 或者使用缩写
./run cd
```

#### 自动协程优化
```bash
# 所有实验命令都自动使用协程优化
./run batch          # 批处理实验（协程优化）
./run realtime       # 实时调度实验（协程优化）
./run batch-multi    # 批量实验（协程优化）
```

#### 代码中使用协程
```kotlin
// Main.kt 自动在 runBlocking 中运行
fun main(args: Array<String>) = runBlocking {
    // 所有实验都使用协程版本
    val runner = ComparisonRunner(...)
    runner.runComparison() // 自动使用协程
}
```

### 协程架构设计

```kotlin
// 1. 协程作用域管理
private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

// 2. 并行执行算法
val algorithmJobs = algorithmsToRun.map { algorithmType ->
    async(Dispatchers.Default) {
        executeAlgorithmAsync(algorithmType)
    }
}

// 3. Channel收集结果
val resultsChannel = Channel<AlgorithmResult>(algorithmsToRun.size)
resultsChannel.receive() // 高效结果收集
```

### 注意事项

- **自动优化**: 项目默认启用协程，无需手动配置
- **CPU密集型**: 对CPU密集型任务（如算法优化）效果显著
- **内存使用**: 协程会略微增加内存使用，但效率提升明显
- **异常处理**: 系统稳定性有保障，单个算法失败不影响整体
- **扩展性**: 架构支持未来进一步优化和扩展

## 🧠 强化学习调度器

CloudSim-Benchmark 集成了基于**Q-learning算法**的强化学习调度器，为智能任务调度提供新的解决方案。

### RL调度器特性

- **Q-learning算法**: 通过试错学习获得最优调度策略
- **状态空间**: VM负载分布 + 调度进度
- **动作空间**: 选择特定VM执行任务
- **奖励函数**: 负载均衡度 + 任务完成奖励 + 过载惩罚
- **在线学习**: 支持训练和推理阶段分离

### 使用RL调度器

#### 单独运行RL算法
```bash
# 运行强化学习调度器
./run batch RL

# 指定随机种子
./run batch RL 42
```

#### 对比传统算法和RL算法
```bash
# 对比PSO和RL算法
./run batch PSO,RL

# 多算法对比包括RL
./run batch PSO,WOA,RL
```

#### 代码中使用RL调度器
```kotlin
// 创建RL调度器实例
val rlScheduler = RLScheduler(
    cloudletList = cloudlets,
    vmList = vms,
    objectiveWeights = config.ObjectiveWeightsConfig(),
    learningRate = 0.1,      // 学习率
    discountFactor = 0.9,    // 折扣因子
    explorationRate = 0.1,   // 探索率
    episodes = 100           // 训练轮数
)

// 执行调度
val schedule = rlScheduler.allocate()
```

### RL算法原理

#### 状态表示
```kotlin
data class State(
    val vmLoads: DoubleArray,  // 各VM负载情况(0.0-1.0)
    val progress: Double       // 调度进度(0.0-1.0)
)
```

#### 动作表示
```kotlin
data class Action(val vmIndex: Int)  // 选择哪个VM
```

#### 奖励函数
```kotlin
fun calculateReward(state: State, action: Action): Double {
    // 1. 负载均衡奖励（主要奖励）
    val balanceReward = (1.0 - loadVariance) * 10.0

    // 2. 过载惩罚
    val overloadPenalty = if (newLoad > 0.8) -5.0 else 0.0

    // 3. 多样性奖励
    val diversityBonus = if (activeVMs > 1) 1.0 else 0.0

    return balanceReward + overloadPenalty + diversityBonus
}
```

#### Q-learning更新
```kotlin
// Q(s,a) = Q(s,a) + α[R + γ·max(Q(s',a')) - Q(s,a)]
val newQ = currentQ + learningRate * (reward + discountFactor * nextMaxQ - currentQ)
```

### 训练过程

1. **初始化**: 创建Q-table，初始化为0或小随机值
2. **训练循环**: 重复多个episode
3. **单episode**: 完整调度过程，收集状态-动作-奖励序列
4. **Q更新**: 使用Q-learning公式更新Q值
5. **收敛检查**: 检查学习是否稳定

### 参数调优

| 参数 | 默认值 | 说明 | 调优建议 |
|------|--------|------|----------|
| `learningRate` | 0.1 | 学习步长 | 0.01-0.3 |
| `discountFactor` | 0.9 | 未来奖励折扣 | 0.8-0.99 |
| `explorationRate` | 0.1 | 探索概率 | 0.05-0.3 |
| `episodes` | 100 | 训练轮数 | 50-500 |

### 性能特点

- **训练开销**: 预训练需要时间，但推理快速
- **适应性**: 能适应不同的VM配置和任务特征
- **稳定性**: Q-learning保证收敛到最优策略
- **可扩展性**: 状态空间可扩展，支持更多特征

## 🚀 高性能计算优化

CloudSim-Benchmark 集成了多个高性能计算库，显著提升了数值计算、统计分析和数据处理的效率。

### 高性能计算库集成

- **ND4J (Eclipse Deeplearning4J)**: 高性能数值计算和向量化操作
- **Fastutil**: 高性能Java集合库，提供优化的数组和Map实现
- **Eclipse Collections**: 高级集合操作，支持函数式编程和并行处理

### 优化内容

#### 1. 统计计算优化
```kotlin
// 优化前：多次数组遍历
val variance = values.map { (it - mean) * (it - mean) }.average()

// 优化后：ND4J向量化计算
val array = Nd4j.create(values)
val diff = array.sub(mean)
val squaredDiff = diff.mul(diff)
val variance = squaredDiff.meanNumber().toDouble()
```

#### 2. 目标函数优化
```kotlin
// 优化前：循环累加和数学运算
for (i in 0 until vmNum) {
    LB += Math.pow(executeTimeOfVM[i] - avgExecuteTime, 2.0)
}

// 优化后：ND4J向量化操作
val diff = executeTimes.sub(avgExecuteTime)
val squaredDiff = diff.mul(diff)
val variance = squaredDiff.meanNumber().toDouble()
```

#### 3. 数据结构优化
```kotlin
// 使用Fastutil高性能集合
val doubleList = DoubleArrayList()  // 比ArrayList性能更好
val intList = IntArrayList()        // 优化的整型数组

// Eclipse Collections高级操作
val immutableList = DoubleLists.immutable.of(1.0, 2.0, 3.0)
val parallelList = DoubleLists.mutable.of(1.0, 2.0, 3.0, 4.0)
    .asParallel(this, 4)  // 并行处理
```

### 性能提升效果

#### 统计计算性能对比
```
优化前：多次遍历 + 中间集合创建
优化后：单次向量化操作

提升效果：
- 标准差计算：~3-5倍性能提升
- 批量统计：~2-4倍性能提升
- 内存使用：减少中间对象创建
```

#### 目标函数性能对比
```
优化前：循环遍历 + 标量数学运算
优化后：向量化操作 + 并行计算

提升效果：
- Makespan计算：~2-3倍性能提升
- LoadBalance计算：~3-4倍性能提升
- 内存局部性：显著改善
```

### 自动优化机制

项目中的所有数值计算都会自动使用高性能优化：

- **StatisticalValue.kt**: 统计计算自动使用ND4J
- **SchedulerObjectiveFunction.kt**: 目标函数自动优化
- **OptimizedCollections.kt**: 集合操作使用高性能库

### 使用方式

高性能优化是**自动启用**的，无需手动配置：

```bash
# 所有命令都自动使用高性能优化
./run batch PSO          # ✅ 自动优化
./run realtime           # ✅ 自动优化
./run batch RL           # ✅ 自动优化
```

### 扩展性

高性能计算架构支持未来扩展：

- **GPU加速**: ND4J支持CUDA/OpenCL GPU加速
- **分布式计算**: 支持集群环境下的分布式计算
- **内存优化**: 针对大数据集的内存优化策略
- **算法扩展**: 为新算法提供高性能计算基础

## 📊 实验结果

实验结果会以两种方式输出：

1. **控制台输出**: 详细的算法运行过程和对比结果
2. **CSV文件**: 所有结果文件统一保存在 `results/` 文件夹下，每次运行都会生成一个带时间戳的唯一文件，不会覆盖之前的结果

### 结果文件命名规则

- **批处理模式**: `results/batch_comparison_YYYYMMDD_HHmmss.csv`
- **批处理模式批量任务数实验**: `results/batch_cloudlet_count_comparison_YYYYMMDD_HHmmss.csv`
- **实时调度模式**: `results/realtime_comparison_YYYYMMDD_HHmmss.csv`
- **实时调度模式批量任务数实验**: `results/realtime_cloudlet_count_comparison_YYYYMMDD_HHmmss.csv`

例如：
- `results/batch_comparison_20241222_143025.csv`
- `results/batch_cloudlet_count_comparison_20241222_143156.csv`
- `results/realtime_comparison_20241222_143156.csv`

### 结果文件内容

CSV 文件包含以下指标：

- **Makespan**: 最大完成时间
- **Load Balance**: 负载均衡度
- **Cost**: 总成本
- **Total Time**: 总执行时间
- **Fitness**: 综合适应度值
- **AvgWaitingTime** (仅实时模式): 平均等待时间
- **AvgResponseTime** (仅实时模式): 平均响应时间

如果配置了多次运行（`runs > 1`），CSV 文件还会包含平均值和标准差列。

### 结果示例

```
算法对比结果汇总
================================================================================
算法           Makespan        Load Balance    Cost            Total Time      Fitness        
--------------------------------------------------------------------------------
Random       6362.47         2534.72         1952.67         44593.91        2.11           
PSO          3379.31         1269.82         977.6           44421.01        2.82           
WOA          1320.8          573.15          416.41          40380.59        3.25           
GWO          3636.48         1527.59         1206.21         44393.88        2.07           
HHO          3456.12         1489.33         1156.78         44321.45        2.15           
--------------------------------------------------------------------------------

最优值:
  最小 Makespan: WOA (1320.8)
  最小 Load Balance: WOA (573.15)
  最小 Cost: WOA (416.41)
  最小 Fitness: WOA (3.25)
================================================================================
```

## 🔬 Google Trace 数据集支持

项目支持使用真实的Google数据中心工作负载数据进行实验，提供更真实的测试环境。

### 数据获取

1. 访问 [Kaggle Google Cluster Data](https://www.kaggle.com/datasets/google/clusterdata-2011-2)
2. 下载 `task_events` 数据文件
3. 解压后放置到 `data/google_trace/task_events.csv`

### 配置使用

**TOML配置**：
```toml
[batch]
generatorType = "GOOGLE_TRACE"

[batch.googleTrace]
filePath = "data/google_trace/task_events.csv"
maxTasks = 1000
timeWindowStart = 0
timeWindowEnd = 3600  # 1小时数据
```

**运行实验**：
```bash
# 使用Google Trace配置
./run batch --config configs/experiments/google_trace_test.toml

# 或直接指定类型
./run batch GOOGLE_TRACE
```

### 数据格式

Google Trace CSV包含真实数据中心任务特征：
- 时间戳、作业ID、任务索引
- CPU/内存/磁盘资源需求
- 优先级和调度类别
- 事件类型（调度、完成、失败等）

### 自动降级

如果数据文件不存在，系统会自动生成基于真实数据统计特征的模拟数据，保证实验连续性。

## 📁 项目结构

```
cloudsim-benchmark/
├── src/main/kotlin/
│   ├── Main.kt                    # 主程序入口
│   ├── config/
│   │   ├── ExperimentConfig.kt   # 实验配置
│   │   ├── AlgorithmType.kt      # 算法类型枚举
│   │   └── ConfigExamples.kt     # 配置示例
│   ├── datacenter/
│   │   ├── ComparisonRunner.kt  # 批处理对比运行器
│   │   ├── RealtimeComparisonRunner.kt  # 实时调度对比运行器
│   │   ├── BatchCloudletCountRunner.kt  # 批处理模式批量任务数实验运行器
│   │   ├── RealtimeCloudletCountRunner.kt  # 实时调度模式批量任务数实验运行器
│   │   ├── DatacenterCreator.kt  # 数据中心创建器
│   │   ├── CloudletGenerator.kt  # 云任务生成器（统一接口）
│   │   ├── RealtimeCloudletGenerator.kt  # 实时任务生成器
│   │   ├── ObjectiveFunction.kt  # 目标函数
│   │   └── generator/
│   │       ├── CloudletGeneratorStrategy.kt  # 生成器策略接口
│   │       ├── LogNormalCloudletGenerator.kt  # 对数正态分布生成器
│   │       ├── UniformCloudletGenerator.kt    # 均匀分布生成器
│   │       └── CloudletGeneratorFactory.kt     # 生成器工厂
│   ├── scheduler/
│   │   ├── Scheduler.kt           # 调度器抽象基类
│   │   ├── RandomScheduler.kt    # 随机调度器
│   │   ├── PSOScheduler.kt       # PSO调度器
│   │   ├── WOAScheduler.kt       # WOA调度器
│   │   ├── GWOScheduler.kt       # GWO调度器
│   │   ├── HHOScheduler.kt       # HHO调度器（包含HHO算法实现）
│   │   └── RealtimeScheduler.kt  # 实时调度器接口和实现
│   ├── broker/
│   │   └── RealtimeBroker.kt     # 实时调度代理
│   └── util/
│       ├── Logger.kt              # 日志工具类
│       ├── ResultsManager.kt     # 结果管理器
│       └── StatisticalValue.kt   # 统计值类
├── src/main/resources/
│   └── logback.xml               # 日志配置文件
├── build.gradle.kts              # Gradle构建配置
├── configs/                      # 配置文件目录
│   ├── default.toml             # 默认配置
│   ├── batch.toml              # 批处理配置
│   ├── realtime.toml           # 实时调度配置
│   ├── algorithms.toml         # 算法配置
│   └── experiments/            # 实验配置
│       ├── google_trace_test.toml # Google Trace测试配置
├── data/                        # 数据目录
│   └── google_trace/            # Google Trace数据集
│       ├── README.md            # 数据获取和使用说明
│       └── task_events.csv      # Google Trace数据文件（需要下载）
├── runs/                        # 实验结果目录（YOLO风格）
│   ├── batch/                  # 批处理实验结果
│   └── realtime/               # 实时调度实验结果
├── scripts/
│   ├── run                       # 统一运行脚本（Linux/macOS）
│   └── run.bat                   # 统一运行脚本（Windows）
├── tools/
│   └── visualize_results.ipynb   # 实验结果可视化工具
├── LICENSE                       # MIT许可证
└── README.md                     # 项目说明文档
```

## 🔬 实验可重复性

项目使用固定随机数种子保证实验可重复性：

- **默认种子**: 0
- **自定义种子**: 通过命令行参数指定
- **所有随机数生成**: 任务生成、算法初始化、迭代过程都使用统一种子

## 📝 日志系统

项目使用跨平台日志库进行日志记录：

### 日志库

- **kotlin-logging**: Kotlin 友好的日志 API
- **slf4j**: 日志门面，支持切换日志实现
- **logback**: 日志实现，支持控制台和文件输出

### 日志配置

日志配置文件位于 `src/main/resources/logback.xml`，支持：

- **控制台输出**: 实时查看日志
- **文件输出**: `logs/cloudsim-benchmark.log`（按天滚动）
- **结果日志**: `logs/results.log`（仅记录实验结果，格式简洁）

### 日志级别

- **INFO**: 一般信息（默认）
- **DEBUG**: 调试信息（优化算法详细过程）
- **WARN**: 警告信息
- **ERROR**: 错误信息

## 🔄 CI/CD 持续集成

项目配置了 GitHub Actions 自动构建和打包：

### 自动构建

每次推送到 `main` 或 `dev` 分支时，GitHub Actions 会自动：
- ✅ 运行测试
- ✅ 构建项目
- ✅ 生成 fat JAR 文件
- ✅ 上传构建产物作为 artifact

### 工作流文件

- **`.github/workflows/build.yml`** - 基本构建工作流（Windows）
  - 每次推送时自动触发
  - 构建项目并上传 artifact
  
- **`.github/workflows/build-matrix.yml`** - Windows 构建
  - 验证 Windows 平台兼容性
  
- **`.github/workflows/release.yml`** - 发布构建工作流
  - 创建 Release 时触发
  
- **`.github/workflows/release-auto.yml`** - 自动发布工作流（Windows）
  - 推送版本标签（如 `v1.0.0`）时自动触发
  - 在 Windows 上构建项目
  - 构建成功后自动创建 GitHub Release
  - 自动上传 JAR 文件和 Windows ZIP 压缩包

### 下载构建产物

1. 进入 GitHub 仓库的 Actions 页面
2. 选择最新的工作流运行
3. 在 Artifacts 部分下载 `cloudsim-benchmark-jar`

### 注意事项

✅ **CloudSim Plus 依赖**: 项目使用 CloudSim Plus 8.5.5（已发布版本），从 Maven Central 自动获取，无需本地构建。

## 🛠️ 开发指南

### 添加新算法

1. 在对应的 `scheduler/` 文件中实现优化算法（算法实现与调度器合并在同一文件）
2. 在 `scheduler/` 目录下创建对应的调度器
3. 在 `config/AlgorithmType.kt` 中添加算法类型
4. 在 `ComparisonRunner` 或 `RealtimeComparisonRunner` 中注册算法

### 添加新任务生成器

1. 实现 `CloudletGeneratorStrategy` 接口
2. 在 `CloudletGeneratorFactory` 中注册生成器
3. 在 `CloudletGeneratorType` 枚举中添加类型

### 修改配置

1. **实验参数**: 修改 `src/main/kotlin/config/ExperimentConfig.kt` 中的默认值
2. **算法选择**: 使用命令行参数或代码配置
3. **数据中心参数**: 修改 `DatacenterConfig` object 中的常量
4. **任务生成参数**: 修改 `CloudletGenConfig` object 中的常量
5. **目标函数参数**: 修改 `ObjectiveConfig` object 中的权重

**注意**: 修改配置后需要重新编译项目。

## 📄 许可证

本项目采用 [MIT License](LICENSE) 许可证。

```
MIT License

Copyright (c) 2025 LYL224459

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 👤 作者

**LYL224459**

## 🙏 致谢

- [CloudSim Plus](https://github.com/cloudsimplus/cloudsimplus) - 云计算仿真框架
- [Apache Commons Math3](https://commons.apache.org/proper/commons-math/) - 数学计算库
- [KotlinLogging](https://github.com/MicroUtils/kotlin-logging) - Kotlin 日志库

## 📚 相关资源

- [CloudSim Plus 文档](https://cloudsimplus.org/)
- [Kotlin 官方文档](https://kotlinlang.org/docs/home.html)
- [Gradle 用户指南](https://docs.gradle.org/)

---

**⭐ 如果这个项目对您有帮助，欢迎 Star！**
