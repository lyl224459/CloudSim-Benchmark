# API And Extension Guide

本文档说明当前项目的稳定接口、主要 Kotlin API 和常见扩展点。项目不是独立发布给第三方复用的库；真正需要保持兼容的是 CLI、TOML 配置字段、CSV 表头、算法注册名、release 包语义和可复现实验行为。其余 `internal` 类型可以在小 PR 中重构，但必须保留行为测试。

## Stability Levels

| Level | Examples | Compatibility Rule |
| :--- | :--- | :--- |
| Stable user surface | CLI、TOML schema、CSV headers、release package、container entrypoint | 默认不破坏。需要变更时必须有 migration 说明和快照测试。 |
| Stable code facade | `CommandExecutor`、`RunResolver`、`AlgorithmRegistry`、runner constructors、`RealtimeMetricSchema` | 可内部重构，外观函数和语义保持。 |
| Internal extension point | runner services、broker services、metric catalog sections、buildSrc helpers | 可以重排文件和类型，但要用测试锁住输出和边界。 |
| Test fixture | `src/test` 和 `buildSrc/src/test` fixture/helper | 可自由整理，不能降低测试发现和覆盖门禁。 |

## CLI API

入口文件：

- `src/main/kotlin/Main.kt`
- `src/main/kotlin/cli/CliParser.kt`
- `src/main/kotlin/cli/CommandExecutor.kt`
- `src/main/kotlin/cli/RunResolver.kt`
- `src/main/kotlin/cli/DryRunPrinter.kt`

主要调用链：

```text
main(args)
  -> CommandExecutor.execute(args)
  -> CliParser(args).parse()
  -> CommandExecutionCoordinator
  -> RunResolver.resolve(command)
  -> ProductionExperimentLauncher
```

Stable command model：

- `CliParser.RunCommand`
- `CliParser.ListAlgorithmsCommand`
- `CliParser.ListProfilesCommand`
- `CliParser.ListPresetsCommand`
- `CliParser.ConfigValidateCommand`
- `CliParser.ConfigPrintCommand`
- `CliParser.HelpCommand`

新增 CLI 参数时必须同步：

1. `CliParser.runOptions()` 或对应子命令 parser。
2. `ResolvedExperimentConfig` / `RunResolver` / override resolver。
3. `DryRunPrinter` 和 dry-run JSON。
4. `docs/cli.md`。
5. CLI parser tests、dry-run snapshot tests、JUnit inventory。

约束：

- `--key value` 与 `--key=value` 应等价。
- boolean flag 不能接受 inline value。
- `--preset` 与 `--algorithms` 互斥。
- legacy top-level command 必须继续给出迁移提示。

## Configuration API

入口文件：

- `src/main/kotlin/config/ConfigurationManager.kt`
- `src/main/kotlin/config/ExperimentConfig.kt`
- `src/main/kotlin/config/ExperimentTomlModels.kt`
- `src/main/kotlin/config/ExperimentDomainConfig.kt`
- `src/main/kotlin/config/SystemConfig.kt`
- `src/main/kotlin/config/RealtimeConfigValidator.kt`
- `src/main/kotlin/config/ExperimentConfigValidator.kt`

常用入口：

```kotlin
ConfigurationManager.loadFromSingleFile(path)
ExperimentConfig.load(path)
ExperimentConfig.loadLibrary(path)
ExperimentConfig.createDefault()
ExperimentConfig.validate(config)
SystemConfig.createDefault()
SystemConfig.validate(config)
```

配置分层：

```text
CLI
  overrides profile
    overrides root experiment/system config
      overrides code defaults
```

新增 TOML 字段时必须同步：

1. TOML DTO，例如 `ExperimentTomlModels.kt` 或 `SystemTomlModels.kt`。
2. domain model，例如 `ExperimentDomainConfig.kt` 或 `SystemDomainConfig.kt`。
3. mapper/parser，例如 `ExperimentConfigParsers.kt`、`ExperimentConfigLoader.kt` 或 `SystemConfigMapper.kt`。
4. validator rule 和错误路径。
5. 示例配置和 `docs/configuration.md`。
6. config parsing tests、validation snapshot tests、README TOML drift test（如 README 新增 TOML）。

错误语义：

- 未知字段必须失败。
- 旧顶层实验 schema 必须继续拒绝。
- validator 错误顺序、field path 和 message keyword 由测试锁定。

## Algorithm Registry API

入口文件：

- `src/main/kotlin/scheduler/AlgorithmRegistry.kt`
- `src/main/kotlin/scheduler/Scheduler.kt`
- `src/main/kotlin/scheduler/RealtimeScheduler.kt`

核心类型：

```kotlin
enum class AlgorithmMode { BATCH, REALTIME }

data class ResolvedAlgorithmSettings(
    val population: Int,
    val maxIter: Int,
)

sealed class AlgorithmDefinition
class BatchAlgorithmDefinition : AlgorithmDefinition
class RealtimeAlgorithmDefinition : AlgorithmDefinition

data class ResolvedAlgorithm(
    val definition: AlgorithmDefinition,
    val settings: ResolvedAlgorithmSettings,
)
```

Batch scheduler factory shape：

```kotlin
(cloudlets, vms, objectiveWeights, settings, seed) -> Scheduler
```

Realtime scheduler factory shape：

```kotlin
(vms, objectiveWeights, settings, seed) -> RealtimeScheduler
```

Registry responsibilities：

- canonical algorithm name normalization；
- alias matching；
- `ALL` expansion；
- batch/realtime capability separation；
- default enabled handling；
- algorithm-specific `population` / `maxIter` resolution。

新增 batch 算法时必须同步：

1. 新增 `Scheduler` subclass，`allocate()` 返回 cloudlet -> VM index 的 `IntArray`。
2. 在 `AlgorithmRegistry` 注册 `BatchAlgorithmDefinition`。
3. 如需 legacy enum，更新 `BatchAlgorithmType`。
4. 如支持参数，设置 `supportsPopulation` / `supportsMaxIter`。
5. 补固定 seed、单 VM、同质 VM、空任务拒绝、VM 下标范围和 finite fitness 测试。
6. 更新 `docs/api.md`、`docs/cli.md` 或 `docs/configuration.md` 中的算法说明。
7. 如算法面向用户可选，更新 [algorithms.md](algorithms.md)。

新增 realtime 算法时必须同步：

1. 实现 `RealtimeScheduler.scheduleOnArrival(context)`。
2. 如复用候选过滤，继承 `RealtimeSchedulerBase`。
3. 在 `AlgorithmRegistry` 注册 `RealtimeAlgorithmDefinition`。
4. 补 accepted candidates、fallback、非连续 VM id、空候选和 invalid optimized index 测试。
5. 确认不会绕过 broker resource/topology/admission 约束。
6. 如算法面向用户可选，更新 [algorithms.md](algorithms.md) 和 [realtime-scheduling.md](realtime-scheduling.md)。

## Batch Scheduler API

基类：

```kotlin
abstract class Scheduler(
    protected val cloudletList: List<Cloudlet>,
    protected val vmList: List<Vm>,
    protected val objectiveWeights: ObjectiveWeightsConfig = ObjectiveWeightsConfig(),
) {
    abstract fun allocate(): IntArray
    fun schedule()
}
```

重要约束：

- `cloudletList` 不能为空。
- `vmList` 不能为空。
- `allocate()` 长度必须等于 cloudlet 数。
- 每个值必须是 VM 下标，不是 VM id。
- `schedule()` 会把 `cloudlet.setVm(vmList[index])` 应用到 CloudSim 对象。

目标函数：

- `SchedulerObjectiveFunction.calculate(allocation)` 返回有限 fitness。
- 单 VM、同质 VM 或归一化分母为 0 时对应 ratio 返回 `0.0`。
- 成本、makespan、total time、load balance 在一次 metrics pass 中计算。

## Realtime Scheduler API

核心接口：

```kotlin
interface RealtimeScheduler {
    fun scheduleOnArrival(context: RealtimeSchedulingContext): Int
}
```

兼容入口：

```kotlin
fun scheduleOnArrival(
    newCloudlet: Cloudlet,
    waitingCloudlets: List<Cloudlet>,
    vmList: List<Vm>,
): Int
```

`RealtimeSchedulingContext` 提供：

- new cloudlet；
- active cloudlets；
- VM list；
- current time；
- node states；
- node candidates；
- queue policy；
- topology policy；
- tenant fairness pressure；
- preemption candidates。

返回值必须是真实 VM 下标。候选过滤建议使用：

- `selectableNodeStates(context)`
- `acceptingOptimizationCandidates(context)`
- `orderedCandidateStates(context)`
- `fallbackCandidateVm(context)`
- `optimizedCandidateVmIndex(context, candidateStates, optimizedCandidateIndex)`

## Runner API

入口文件：

- `src/main/kotlin/datacenter/ExperimentRequests.kt`
- `src/main/kotlin/datacenter/ComparisonRunner.kt`
- `src/main/kotlin/datacenter/RealtimeComparisonRunner.kt`
- `src/main/kotlin/datacenter/BatchCloudletCountRunner.kt`
- `src/main/kotlin/datacenter/RealtimeCloudletCountRunner.kt`

请求模型：

```kotlin
data class ExperimentExecutionRequest(
    val randomSeed: Long,
    val resolvedAlgorithms: List<ResolvedAlgorithm>,
    val outputContext: ExperimentOutputContext,
    val concurrency: ExperimentConcurrency,
)

data class BatchExperimentRequest(
    val batch: BatchConfig,
    val execution: ExperimentExecutionRequest,
)

data class RealtimeExperimentRequest(
    val realtime: RealtimeConfig,
    val optimizer: OptimizerConfig,
    val execution: ExperimentExecutionRequest,
)
```

Batch runner：

```kotlin
ComparisonRunner(request).runComparison()
ComparisonRunner(request).runComparisonWithStatistics()
ComparisonRunner(request).runComparisonSummaries()
```

Realtime runner：

```kotlin
RealtimeComparisonRunner(request).runComparison()
RealtimeComparisonRunner(request).runComparisonWithStatistics()
RealtimeComparisonRunner(request).runComparisonSummaries()
```

Runner behavior to preserve：

- algorithm summaries sorted by algorithm name；
- `CancellationException` propagates；
- algorithm exceptions become failed trial summaries；
- CSV disabled path does not write CSV；
- child output context in multi-count runners remains deterministic；
- seed increment semantics remain stable。

## Broker API

入口文件：

- `src/main/kotlin/broker/RealtimeBroker.kt`
- `src/main/kotlin/broker/RealtimeBrokerReadModel.kt`
- `src/main/kotlin/broker/RealtimeBrokerReadViews.kt`
- `src/main/kotlin/broker/RealtimeVmSelectionFacade.kt`
- `src/main/kotlin/broker/RealtimeSubmissionService.kt`
- `src/main/kotlin/broker/RealtimeRuntimeEventPlanner.kt`

`RealtimeBroker` 是 CloudSim broker facade。外部调用方应通过 facade 提交任务和读取指标，不直接依赖内部服务。

主要职责：

- realtime cloudlet submission；
- CloudSim event routing；
- admission / rejection；
- VM selection；
- retry / permanent failure；
- timeout / runtime failure；
- autoscaling；
- topology and tenant accounting；
- read model / metrics snapshot。

内部服务拆分是维护边界，不是稳定 API。修改 broker 时必须优先跑 broker/event/controller 测试，并覆盖：

- resource/capacity/tenant rejection；
- preemption success/failure；
- timeout fail/cancel/retry/degrade；
- runtime failure retry/permanent failure；
- topology failure accounting；
- active VM index accounting。

## Metrics API

入口文件：

- `src/main/kotlin/datacenter/RealtimeMetricSchema.kt`
- `src/main/kotlin/datacenter/RealtimeMetricCatalog.kt`
- `src/main/kotlin/datacenter/RealtimeMetricProjection.kt`
- `src/main/kotlin/datacenter/RealtimeMetricTypes.kt`
- `src/main/kotlin/datacenter/RealtimeMetricsCollector.kt`
- `src/main/kotlin/datacenter/RealtimeMetricDocumentationGenerator.kt`

Stable facade：

```kotlin
RealtimeMetricSchema.trialHeaders()
RealtimeMetricSchema.summaryHeaders()
RealtimeMetricSchema.trialMetricMap(result)
RealtimeMetricSchema.meanMetricMap(statistics)
RealtimeMetricSchema.stdDevMetricMap(statistics)
```

新增 realtime metric 时必须同步：

1. 在 `RealtimeMetricKey` 新增 key。
2. 在对应 metric definition 文件增加 `RealtimeMetricDefinition`。
3. 在 projection/value extraction 中提供值。
4. 更新 collector 或 broker metric source。
5. 重新生成 `docs/realtime-metrics.md`。
6. 更新 metric schema snapshot、CSV header snapshot 和 docs snapshot。

CSV 表头和顺序默认稳定，不应随意重排。

## Cloudlet Generator API

入口文件：

- `src/main/kotlin/datacenter/generator/CloudletGeneratorStrategy.kt`
- `src/main/kotlin/datacenter/generator/CloudletGeneratorFactory.kt`
- `src/main/kotlin/datacenter/generator/GoogleTraceCloudletGenerator.kt`
- `src/main/kotlin/datacenter/RealtimeTraceMetadataRegistry.kt`

Strategy contract：

```kotlin
interface CloudletGeneratorStrategy {
    fun generateCloudlets(count: Int): List<Cloudlet>
}
```

新增 generator 时必须同步：

1. `CloudletGeneratorType`。
2. factory 分支。
3. config DTO/domain/validator。
4. trace metadata provider（如果 realtime metrics 需要 tenant/topology/resource metadata）。
5. 非法输入、空输入、fallback 和 determinism 测试。

## Output API

入口文件：

- `src/main/kotlin/util/ExperimentOutputContext.kt`
- `src/main/kotlin/util/CsvRowWriter.kt`
- `src/main/kotlin/datacenter/BatchResultExporter.kt`
- `src/main/kotlin/datacenter/RealtimeResultExporter.kt`
- `src/main/kotlin/datacenter/CloudletCountResultExporters.kt`

Output rules：

- dry-run 不创建结果目录。
- CSV disabled 时不写 CSV。
- 写入失败必须显式抛出，不吞异常。
- trial CSV 和 summary CSV 使用 schema/order，不手写散落字段。
- release package 和 container context 不应包含运行结果。

## buildSrc API

入口文件：

- `buildSrc/src/main/kotlin/buildlogic/CloudSimPlusTasks.kt`
- `buildSrc/src/main/kotlin/buildlogic/CloudSimPlusGitSupport.kt`
- `buildSrc/src/main/kotlin/buildlogic/CloudSimPlusJarSanitizer.kt`
- `buildSrc/src/main/kotlin/buildlogic/BuildWarningAuditTask.kt`
- `buildSrc/src/main/kotlin/buildlogic/ContainerImageSmokeTask.kt`
- `buildSrc/src/main/kotlin/buildlogic/LicensePolicySupport.kt`

Build logic rules：

- Gradle task class must declare inputs/outputs.
- Network or process execution belongs in task action, not configuration phase.
- Locked CloudSim Plus mode must be cacheable and no-network.
- Mutable CloudSim Plus mode may fetch/checkout and is intentionally out-of-date.
- TestKit coverage must exercise task action behavior.

When adding a build task：

1. Create a task class in buildSrc, not a long build script closure.
2. Add pure helper tests.
3. Add TestKit functional tests for success, failure diagnostics and configuration cache.
4. Wire the task into `check` or `fullCheck` only if it is deterministic and reasonably fast.
5. Document it in `docs/development.md` or `docs/release-readiness.md` if it becomes a gate.

## Compatibility Checklist

Before merging an API-facing change, verify:

- CLI behavior and error keywords are unchanged or documented.
- TOML schema changes have examples and validator tests.
- CSV headers and metric order snapshots pass.
- Algorithm names and aliases remain stable.
- Random seed semantics remain stable.
- `verifyJUnitTestInventory` passes after adding/removing tests.
- `DocumentationDriftTest` passes if README/docs/release-readiness changed.
