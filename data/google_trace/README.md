# Google Trace 数据集

本目录用于存放Google数据中心工作负载跟踪数据。

## 数据获取

### 从Kaggle下载

1. 访问 [Google Cluster Data](https://www.kaggle.com/datasets/google/clusterdata-2011-2) 数据集
2. 下载 `task_events` 文件（通常是 `part-00000-of-00500.csv.gz` 或类似）
3. 解压并重命名为 `task_events.csv`
4. 放置到本目录下

### 数据格式

CSV文件包含以下字段：
```
timestamp,job_id,task_index,machine_id,event_type,user,priority,cpu_request,ram_request,disk_request,different_machine_restriction
```

字段说明：
- `timestamp`: 时间戳（Unix时间）
- `job_id`: 作业ID
- `task_index`: 任务索引
- `machine_id`: 机器ID
- `event_type`: 事件类型 (0=SCHEDULE, 1=EVICT, 2=FAIL, 3=FINISH, 4=KILL, 5=LOST, 6=UPDATE_PENDING, 7=UPDATE_RUNNING)
- `user`: 用户名
- `priority`: 优先级 (0-11)
- `cpu_request`: CPU请求量
- `ram_request`: 内存请求量
- `disk_request`: 磁盘请求量
- `different_machine_restriction`: 是否限制不同机器

## 使用方法

### 1. 配置TOML文件

```toml
[batch]
generatorType = "GOOGLE_TRACE"

[batch.googleTrace]
filePath = "data/google_trace/task_events.csv"
maxTasks = 1000
timeWindowStart = 0
timeWindowEnd = 3600  # 限制1小时数据
```

### 2. 运行实验

```bash
# 使用Google Trace配置运行批处理实验
./run batch --config configs/experiments/google_trace_test.toml

# 或直接指定生成器类型
./run batch GOOGLE_TRACE
```

## 注意事项

1. **数据大小**: Google Trace数据集很大，建议先用小数据集测试
2. **内存使用**: 大数据集可能消耗大量内存，可通过 `maxTasks` 限制
3. **时间窗口**: 可通过 `timeWindowStart` 和 `timeWindowEnd` 限制时间范围
4. **数据可用性**: 如果数据文件不存在，系统会自动使用模拟数据

## 模拟数据

当Google Trace数据不可用时，系统会生成基于真实数据统计特征的模拟数据，保证实验的连续性。