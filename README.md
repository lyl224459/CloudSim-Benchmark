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

对应的运行脚本：
- **`run-batch-multi.bat`** - Windows 批处理模式批量任务数实验脚本
- **`run-realtime-multi.bat`** - Windows 实时调度模式批量任务数实验脚本

## 🚀 快速开始

### 环境要求

- **JDK**: 23+ (项目使用 JVM 23)
- **Gradle**: 9.2.1+
- **Kotlin**: 2.1.21+
- **CloudSim Plus**: 8.5.5 (从 Maven Central 获取)

### 1. 克隆项目

```bash
git clone <repository-url>
cd cloudsim-b
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

#### 方式一：使用运行脚本（推荐）

**Windows**:
```bash
# 批处理模式 - 运行所有算法
run-batch.bat

# 批处理模式 - 只运行 PSO 和 WOA，随机种子 42
run-batch.bat PSO,WOA 42

# 批处理模式 - 运行 10 次取平均值
run-batch.bat PSO,WOA 42 10

# 批量任务数实验模式 - 测试多个任务数
run-batch-multi.bat 50,100,200,500

# 批量任务数实验模式 - 指定运行次数（每个任务数运行10次）
run-batch-multi.bat 50,100,200,500 10

# 批量任务数实验模式 - 指定算法
run-batch-multi.bat 50,100,200 PSO,WOA

# 批量任务数实验模式 - 指定运行次数和算法
run-batch-multi.bat 50,100,200,500 10 PSO,WOA

# 实时调度模式
run-realtime.bat

# 实时调度模式 - 指定算法和随机种子
run-realtime.bat PSO_REALTIME,WOA_REALTIME 123

# 实时调度模式批量任务数实验 - 测试多个任务数
run-realtime-multi.bat 50,100,200,500

# 实时调度模式批量任务数实验 - 指定算法
run-realtime-multi.bat 50,100,200 PSO_REALTIME,WOA_REALTIME

# 通用脚本
run.bat batch PSO,WOA 42
run.bat batch-multi 50,100,200,500
run.bat batch-multi 50,100,200 PSO,WOA
run.bat realtime PSO_REALTIME,WOA_REALTIME 123
run.bat realtime-multi 50,100,200,500
run.bat realtime-multi 50,100,200 PSO_REALTIME,WOA_REALTIME

# 构建并运行（自动构建 JAR）
build-and-run.bat batch PSO,WOA 42
```

**Linux/macOS**:
```bash
chmod +x run.sh

# 批处理模式
./run.sh batch PSO,WOA 42

# 批量任务数实验模式
./run.sh batch-multi 50,100,200,500

# 批量任务数实验模式 - 指定运行次数
./run.sh batch-multi 50,100,200,500 10

# 批量任务数实验模式 - 指定算法
./run.sh batch-multi 50,100,200 PSO,WOA

# 批量任务数实验模式 - 指定运行次数和算法
./run.sh batch-multi 50,100,200,500 10 PSO,WOA

# 实时调度模式
./run.sh realtime PSO_REALTIME,WOA_REALTIME 123

# 实时调度模式批量任务数实验
./run.sh realtime-multi 50,100,200,500
./run.sh realtime-multi 50,100,200 PSO_REALTIME,WOA_REALTIME
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
- 如果遇到 `NoClassDefFoundError` 错误，请确保 CloudSim Plus 已正确安装到本地 Maven 仓库
- 或者使用 fatJar 方式运行（见方式三），fatJar 包含所有依赖，无需本地 Maven 仓库

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
用法: java -jar cloudsim-benchmark-1.0.0-all.jar realtime-multi <cloudletCounts> [algorithms] [randomSeed]

参数说明:
  cloudletCounts - 任务数列表（必需），用逗号分隔，例如: 50,100,200,500
  algorithms    - 要运行的算法列表（可选，默认: 所有算法）
                  实时模式: MIN_LOAD, RANDOM, PSO_REALTIME, WOA_REALTIME
                  多个算法用逗号分隔，例如: PSO_REALTIME,WOA_REALTIME
  randomSeed    - 随机数种子（可选，默认: 0）

示例:
  # 测试多个任务数
  java -jar cloudsim-benchmark-1.0.0-all.jar realtime-multi 50,100,200,500

  # 指定算法
  java -jar cloudsim-benchmark-1.0.0-all.jar realtime-multi 50,100,200 PSO_REALTIME,WOA_REALTIME

  # 指定算法和随机种子
  java -jar cloudsim-benchmark-1.0.0-all.jar realtime-multi 50,100,200 PSO_REALTIME,WOA_REALTIME 42
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

Windows 用户可以使用专门的脚本 `run-batch-multi.bat`：

```bash
# 基本用法
run-batch-multi.bat 50,100,200,500

# 指定运行次数（每个任务数运行10次）
run-batch-multi.bat 50,100,200,500 10

# 指定运行次数和算法
run-batch-multi.bat 50,100,200,500 10 PSO,WOA

# 指定运行次数、算法和随机种子
run-batch-multi.bat 50,100,200,500 10 PSO,WOA 42
```

#### 实时调度模式批量任务数实验 (`realtime-multi`)

实时调度模式批量任务数实验使用实时调度算法，任务动态到达，支持增量调度和实时响应。

**使用方法**：

```bash
# 基本用法：测试多个任务数
java -jar cloudsim-benchmark-1.0.0-all.jar realtime-multi 50,100,200,500

# 指定算法
java -jar cloudsim-benchmark-1.0.0-all.jar realtime-multi 50,100,200 PSO_REALTIME,WOA_REALTIME

# 指定算法和随机种子
java -jar cloudsim-benchmark-1.0.0-all.jar realtime-multi 50,100,200 PSO_REALTIME,WOA_REALTIME 42
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

Windows 用户可以使用专门的脚本 `run-realtime-multi.bat`：

```bash
# 基本用法
run-realtime-multi.bat 50,100,200,500

# 指定算法
run-realtime-multi.bat 50,100,200 PSO_REALTIME,WOA_REALTIME

# 指定算法和随机种子
run-realtime-multi.bat 50,100,200 PSO_REALTIME,WOA_REALTIME 42
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

所有配置参数统一在 `config` 包中管理，便于统一修改和维护。

### 配置文件结构

```
config/
├── ExperimentConfig.kt      # 主配置类（实验参数）
├── AlgorithmType.kt         # 算法类型枚举
├── DatacenterConfig         # 数据中心配置（VM规格、价格等）
├── CloudletGenConfig        # 任务生成配置（分布参数）
└── ObjectiveConfig          # 目标函数配置（权重参数）
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

3. **LOG_NORMAL_SCI** - 对数正态分布 SCI 生成器
   - 使用对数正态分布生成任务执行时间
   - 输出文件大小有独立的均值和方差参数

**配置方式**:
```kotlin
val config = ExperimentConfig(
    batch = BatchConfig(
        generatorType = CloudletGeneratorType.UNIFORM  // 使用均匀分布生成器
    )
)
```

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

在 `ExperimentConfig.kt` 中的 `ObjectiveConfig` object:

```kotlin
// 适应度函数权重（总和应为1.0）
ALPHA = 1.0 / 3    // Cost（成本）权重
BETA = 1.0 / 3     // TotalTime（总时间）权重
GAMMA = 1.0 / 3    // LoadBalance（负载均衡）权重
```

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
├── run.bat                       # Windows通用运行脚本
├── run-batch.bat                 # Windows批处理模式脚本
├── run-batch-multi.bat           # Windows批处理模式批量任务数实验脚本
├── run-realtime.bat              # Windows实时调度模式脚本
├── run-realtime-multi.bat       # Windows实时调度模式批量任务数实验脚本
├── build-and-run.bat             # Windows构建并运行脚本
├── run.sh                        # Linux/macOS运行脚本
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

- **`.github/workflows/build.yml`** - 基本构建工作流（Ubuntu）
- **`.github/workflows/build-matrix.yml`** - 多操作系统构建（Ubuntu、Windows、macOS）
- **`.github/workflows/release.yml`** - 发布构建工作流

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
