# Google Trace Guide

项目支持使用 Google cluster trace 风格的 task events 作为 cloudlet 输入。本文档说明文件位置、字段、解析规则、fallback 行为和 metadata 映射。

## Configuration

示例文件：

- `configs/experiments/google_trace_test.toml`

最小配置形态：

```toml
[profiles.google_trace_batch.batch]
generatorType = "GOOGLE_TRACE"
cloudletCount = 500

[profiles.google_trace_realtime.realtime]
generatorType = "GOOGLE_TRACE"
cloudletCount = 1000
```

可选 trace 配置：

```toml
[profiles.google_trace_realtime.realtime.googleTrace]
filePath = "data/google_trace/task_events.csv"
maxTasks = 1000
timeWindowStart = 0
timeWindowEnd = 9223372036854775807
normalizeTimestamps = true
timestampDivisor = 1000000.0
```

默认值：

| Field | Default |
| :--- | :--- |
| `filePath` | `data/google_trace/task_events.csv` |
| `maxTasks` | `1000` |
| `timeWindowStart` | `0` |
| `timeWindowEnd` | `Long.MAX_VALUE` |
| `normalizeTimestamps` | `true` |
| `timestampDivisor` | `1000000.0` |

在 realtime 模式中，trace timestamp 会映射为 cloudlet 原始 `submissionDelay`。默认计算方式：

```text
arrivalTimestamp = (timestamp - firstTimestamp) / timestampDivisor
```

如果 `normalizeTimestamps = false`，则使用 `timestamp / timestampDivisor`。batch 模式仍只使用 trace-derived cloudlets 做静态调度。

## Expected Input

解析器按逗号分隔读取 task events。当前至少需要 13 个字段，并使用以下字段下标：

| Column | Meaning |
| :--- | :--- |
| 0 | timestamp |
| 1 | job id |
| 2 | task index |
| 3 | machine id |
| 4 | event type |
| 5 | user name |
| 6 | scheduling class |
| 7 | priority |
| 8 | CPU request |
| 9 | memory request |
| 10 | disk space request |
| 11 | different machines restriction |

字段不足或数值非法的行会跳过。空 `machine id`、CPU、memory、disk 字段允许作为 optional 值。

## Event Types

项目识别以下事件常量：

| Event | Value | Meaning |
| :--- | :--- | :--- |
| schedule | `0` | 正常调度事件。 |
| evict | `1` | 可能触发 retry hint。 |
| fail | `2` | 可能触发 retry hint。 |
| kill | `4` | 可能触发 retry hint。 |
| lost | `5` | 可能触发 retry hint。 |

非 schedule 事件仍可生成 cloudlet spec，但 evict/fail/kill/lost 会设置 `retryHint=1`。

## Fallback Behavior

以下情况会 fallback 到 mock trace 数据：

- trace 文件不存在；
- trace 文件为空或没有可用记录；
- 记录全部在时间窗口外；
- 记录全部格式非法。

Fallback 的目的不是伪造真实 workload，而是保证配置和 CI smoke 可以运行。正式实验应提供真实 trace 文件并记录数据来源。

## Mapping To Cloudlets

Cloudlet length：

```text
length = 100000 * cpuRequest * (1.0 + priority * 0.1)
```

如果 `cpuRequest` 缺失，使用 `0.5`。priority 越高，生成任务越长。

Cloudlet 固定属性：

| Attribute | Value |
| :--- | :--- |
| PEs | `1` |
| file size | `1000` |
| output size | `1000` |
| utilization model | dynamic |

## Mapping To Realtime Metadata

Trace 字段会映射为 realtime metadata：

| Metadata | Source |
| :--- | :--- |
| `tenantKey` | `userName` |
| `tenantId` | stable hash of `userName` |
| `priority` | trace priority |
| `requestedCpu` | CPU request |
| `requestedRam` | memory request |
| `requestedBw` | CPU request * 1000 |
| `requestedIo` | disk space request |
| `dataRegion` | stable tenant id modulo 3 |
| `inputDataSize` | disk request, fallback memory request, fallback 1.0 |
| `imageId` | `trace-image-${jobId modulo 16}` |
| `imageSize` | memory request * 1024, fallback 1.0 |
| `retryHint` | 1 for evict/fail/kill/lost, otherwise 0 |
| `arrivalTimestamp` | normalized trace timestamp in simulation seconds |
| `expectedDuration` | cloudlet length divided by `1000.0` |

Negative memory/disk values are coerced where used for size fields. Missing resource requests can still produce a runnable cloudlet.

## Batch vs Realtime

Batch mode uses trace-derived cloudlets for static scheduling. Realtime mode additionally uses trace metadata for:

- tenant fairness；
- resource demand；
- topology/data locality；
- image cache；
- retry hint。

If those realtime features are disabled, trace metadata still exists but may not affect scheduling.

## Validation And Tests

Trace parsing tests should cover:

- missing file；
- empty file；
- bad line；
- insufficient fields；
- invalid numbers；
- time window filtering；
- missing CPU/memory/disk fallback；
- retry hint event mapping；
- tenant/image/data region stability。

## Troubleshooting

| Symptom | Likely Cause | Action |
| :--- | :--- | :--- |
| All generated tasks look synthetic | Trace file missing or empty | Check `filePath` and logs for fallback message. |
| Fewer tasks than expected | `maxTasks` or time window filtering | Increase `maxTasks` or widen window. |
| Many malformed lines | CSV format differs from expected task events | Verify delimiter and column order. |
| Resource metrics remain zero | Resource model disabled or trace fields missing | Enable `resourceModelEnabled` and verify request fields. |
| Tenant metrics are empty | `userName` missing or multi-tenant disabled | Enable multi-tenant settings and check trace user field. |
