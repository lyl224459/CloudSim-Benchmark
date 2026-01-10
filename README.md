# CloudSim-Benchmark

基于CloudSim Plus的云调度算法性能评估框架

## 🚀 快速开始

### 系统要求
- **JDK**: 23+
- **构建工具**: Gradle 9.2.1+
- **构建优化**: 自动检测CPU核心数并优化构建速度

### 运行批处理实验
```bash
./run batch
```

### 运行实时调度实验
```bash
./run realtime
```

### 构建项目（自动优化）
```bash
./run build    # 自动使用最佳构建配置（无需额外设置）
```

**构建优化特性**:
- ✅ 自动检测CPU核心数并使用全部核心
- ✅ 并行构建和缓存优化
- ✅ Kotlin编译器优化

## 🧠 强化学习调度器

项目集成**强化学习调度器**，使用Q-learning算法进行智能任务调度：

```bash
# 运行强化学习调度器
./run batch RL

# 对比传统算法和RL算法
./run batch PSO,RL
```

**RL调度器特性**:
- 🧠 **Q-learning算法**: 学习最优调度策略
- 🎯 **智能决策**: 基于负载均衡的奖励函数
- 📈 **自适应学习**: 通过训练提高调度质量
- ⚡ **协程优化**: 并行训练和推理

## 🚀 高性能计算优化（默认启用）

项目集成了**高性能计算库**，显著提升数值计算和数据处理效率：

```bash
# 自动使用高性能优化
./run batch PSO,WOA,RL

# 所有数值计算都经过优化
# - 统计计算使用ND4J向量化
# - 目标函数使用高性能数学运算
# - 数据结构使用Fastutil优化
```

**高性能计算特性**:
- 🔢 **ND4J向量化计算**: 统计计算和矩阵运算加速
- ⚡ **Fastutil集合优化**: 高性能数组和集合操作
- 🧮 **Eclipse Collections**: 高级集合处理和并行操作
- 📊 **数学运算优化**: 使用优化的数学库提升计算速度

## ⚡ 协程并行优化（默认启用）

项目**默认使用协程并行执行**，显著提升实验效率：

```bash
# 查看协程优化演示
./run coroutine-demo

# 运行实验（自动使用协程优化）
./run batch
./run realtime
```

**协程优化特性**:
- 🚀 **算法并行执行**: 多个调度算法同时运行，无需顺序等待
- 📊 **多次运行并行**: 统计实验的多次运行并发执行
- 📡 **Channel通信**: 高效的结果收集和处理机制
- 🛡️ **异常隔离**: SupervisorJob确保单个算法失败不影响整体执行
- ⚡ **性能提升**: 在多核CPU上可实现**5倍以上**的加速效果

**性能提升示例**:
- 顺序执行 5 个算法: ~5000ms
- 协程并行执行: ~1000ms
- **加速比: 5x** (在 8 核 CPU 上)

## ⚙️ 配置功能

### 任务生成器配置

项目支持通过配置文件更改任务生成器：

```toml
[batch.generator]
type = "LOG_NORMAL"  # LOG_NORMAL, UNIFORM, GOOGLE_TRACE

[realtime.generator]
type = "UNIFORM"     # 实时调度也支持相同配置
```

### 目标函数权重配置

可以自由组合成本、时间、负载均衡和Makespan目标：

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
- ✅ 默认跳过测试以提升速度

## 📖 详细文档

请查看 [`docs/README.md`](docs/README.md) 获取完整的使用说明和API文档。

## 📁 项目结构

- `configs/` - 配置文件目录
- `src/` - 源代码
- `scripts/` - 运行脚本
- `docs/` - 详细文档
- `tools/` - 工具和可视化脚本
- `data/` - 数据文件目录

## 📊 实验结果

实验结果保存在 `runs/` 目录下，采用YOLO风格的目录结构。

## 🤝 贡献

欢迎提交Issue和Pull Request！

## 📄 许可证

MIT License