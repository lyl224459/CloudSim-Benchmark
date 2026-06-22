# CLI Reference

CLI 入口是 `run.cmd`、`scripts/run.bat` 或 `./run`。核心命令分为 `run`、`list` 和 `config`。

## Command Shape

```text
run [options]
list algorithms --mode batch|realtime
list profiles --config FILE
list presets --config FILE
config validate --config FILE
config print --config FILE [--profile NAME]
```

旧入口 `batch`、`realtime`、`batch-multi`、`realtime-multi` 不再作为顶层命令使用；请改用 `run --mode ...`。

## Commands

### `run`

`run` 是唯一执行实验的命令。它可以完全由 CLI 参数驱动，也可以通过 `--config` 和 `--profile` 读取 TOML 后再用 CLI 覆盖部分字段。

最小形式：

```powershell
.\run.cmd run --mode batch --algorithms RANDOM
```

配置形式：

```powershell
.\run.cmd run --config configs/examples/single_config_example.toml --profile batch_small
```

推荐在正式运行前加 `--dry-run`：

```powershell
.\run.cmd run --config configs/examples/single_config_example.toml --profile batch_small --dry-run
```

`--dry-run` 会解析配置、选择 profile、展开算法和任务数，但不会创建结果目录。

### `list`

`list` 用于查看可选算法和配置文件中的 profile/preset：

```powershell
.\run.cmd list algorithms --mode batch
.\run.cmd list algorithms --mode realtime
.\run.cmd list profiles --config configs/examples/single_config_example.toml
.\run.cmd list presets --config configs/examples/single_config_example.toml
```

`list algorithms` 不读取 profile，只按 batch/realtime mode 展示 registry 中可用算法。

### `config`

`config validate` 只做解析和校验：

```powershell
.\run.cmd config validate --config configs/examples/realtime_test.toml
```

`config print` 输出选中 profile 合并后的配置：

```powershell
.\run.cmd config print --config configs/examples/single_config_example.toml --profile realtime_smoke
```

## Run Modes

| Mode | Purpose |
| :--- | :--- |
| `batch` | 单一任务规模的批处理调度对比。 |
| `realtime` | 动态到达的实时调度对比。 |
| `batch-multi` | 多任务规模批处理扩展性实验。 |
| `realtime-multi` | 多任务规模实时调度扩展性实验。 |

## Common Options

| Option | Alias | Meaning |
| :--- | :--- | :--- |
| `--mode` | | `batch`、`realtime`、`batch-multi` 或 `realtime-multi`。 |
| `--algorithms` | `-a` | 算法列表，例如 `PSO,WOA` 或 `ALL`。 |
| `--preset` | | 使用配置文件中的算法预设；与 `--algorithms` 互斥。 |
| `--profile` | `-p` | 选择配置文件中的 profile。 |
| `--tasks` | `-t` | multi 模式任务数列表。 |
| `--seed` | `-s` | 随机种子。 |
| `--runs` | `-r` | 每个配置的 trial 次数。 |
| `--config` | `-c` | TOML 配置文件路径。 |
| `--output` | `-o` | 输出目录。 |
| `--dry-run` | | 打印最终配置，不创建结果目录。 |
| `--sequential` | `-S` | 禁用并行执行。 |
| `--concurrency` | `-C` | 限制并发度。 |
| `--help` | `-h` | 显示帮助。 |

`--key value` 和 `--key=value` 都受支持。布尔 flag 不能带 inline value，例如 `--dry-run=true` 会失败。

## Algorithm Selection

算法选择优先级：

1. CLI `--algorithms`。
2. CLI `--preset`。
3. profile `algorithms`。
4. profile `preset`。
5. algorithm library 中默认启用算法。

`--algorithms ALL` 会展开当前 mode 下所有启用算法。batch 与 realtime 算法能力是强类型分离的：

- batch mode 不接受 `MIN_LOAD`、`EDF_REALTIME`、`LLF_REALTIME`、`EFT_REALTIME`、`SRPT_REALTIME`、`PRIORITY_DEADLINE_REALTIME`、`PSO_REALTIME`、`WOA_REALTIME`。
- realtime mode 不接受 `PSO`、`WOA`、`GWO`、`HHO`、`RL`、`IMPROVED_RL`。

常用 batch 算法：

```text
RANDOM, PSO, WOA, GWO, HHO, RL, IMPROVED_RL
```

常用 realtime 算法：

```text
MIN_LOAD, RANDOM, EDF_REALTIME, LLF_REALTIME, EFT_REALTIME, SRPT_REALTIME, PRIORITY_DEADLINE_REALTIME, PSO_REALTIME, WOA_REALTIME
```

算法别名由 `AlgorithmRegistry` 统一解析，例如 `RAND`、`MINLOAD`、`MIN-LOAD`、`EDF`、`LLF`、`EFT`、`SRPT`、`PRIORITY_DEADLINE`。

## Task Counts

`batch` 和 `realtime` 使用单个 cloudlet count。`batch-multi` 和 `realtime-multi` 使用任务数列表：

```powershell
.\run.cmd run --mode batch-multi --tasks 50,100,200,500 --algorithms RANDOM
```

multi mode 未显式提供 `--tasks` 或 profile `tasks` 时，默认使用：

```text
50,100,200,500
```

## Concurrency

默认启用协程并行。需要排查非确定性日志或简化调试时可使用：

```powershell
.\run.cmd run --mode batch --algorithms RANDOM --sequential
```

限制并发度：

```powershell
.\run.cmd run --mode batch --algorithms ALL --concurrency 4
```

`--concurrency` 必须为正整数。`--sequential` 会禁用并行执行。

## Examples

批处理：

```powershell
.\run.cmd run --mode batch --algorithms PSO,WOA --runs 3
```

实时：

```powershell
.\run.cmd run --mode realtime --algorithms MIN_LOAD,RANDOM --seed 42 --runs 5
```

多任务数：

```powershell
.\run.cmd run --mode batch-multi --tasks 50,100,200,500 --algorithms ALL --runs 3
```

配置 profile：

```powershell
.\run.cmd run --config configs/examples/single_config_example.toml --profile realtime_smoke
```

dry-run：

```powershell
.\run.cmd run --config configs/examples/batch_test.toml --profile batch_test --dry-run
```

## Error Handling

CLI 会在以下情况返回明确错误：

- 未知命令、未知参数或位置参数。
- 缺少 `--config`、`--mode`、`--profile` 等必需值。
- `--preset` 与 `--algorithms` 同时出现。
- batch 算法用于 realtime，或 realtime 算法用于 batch。
- 配置文件不存在、profile 不存在或 TOML 无法解析。

错误消息会保留关键字，测试会锁定这些关键路径，便于脚本判断失败原因。

## Wrapper Scripts

Windows：

```powershell
.\run.cmd run --mode batch --algorithms RANDOM
scripts\run.bat run --mode batch --algorithms RANDOM
```

Unix/macOS：

```bash
./run run --mode batch --algorithms RANDOM
scripts/run run --mode batch --algorithms RANDOM
```

`run.cmd` 会在 JAR 缺失时执行 `fatJar`。发布包根目录优先使用 `cloudsim-benchmark-all.jar`，源码目录会自动选择 `build/libs/cloudsim-benchmark-*-all.jar`。
