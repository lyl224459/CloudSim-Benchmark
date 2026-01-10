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