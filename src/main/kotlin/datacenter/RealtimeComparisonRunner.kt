package datacenter

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import scheduler.ResolvedAlgorithm
import util.Logger
import kotlin.system.measureTimeMillis

class RealtimeComparisonRunner(
    private val request: RealtimeExperimentRequest,
) {
    private val cloudletCount = request.realtime.cloudletCount
    private val simulationDuration = request.realtime.simulationDuration
    private val arrivalRate = request.realtime.arrivalRate
    private val population = request.optimizer.population
    private val maxIter = request.optimizer.maxIter
    private val randomSeed = request.execution.randomSeed
    private val runs = request.realtime.runs
    private val generatorType = request.realtime.generatorType
    private val googleTraceConfig = request.realtime.googleTraceConfig
    private val objectiveWeights = request.realtime.objectiveWeights
    private val arrival = request.realtime.arrival
    private val scheduling = request.realtime.scheduling
    private val resolvedAlgorithms = request.execution.resolvedAlgorithms
    private val outputContext = request.execution.outputContext
    private val concurrency = request.execution.concurrency
    private val metricsCollector = RealtimeMetricsCollector(scheduling, objectiveWeights)
    private val experimentConfig =
        RealtimeExperimentConfigSnapshot(
            cloudletCount = cloudletCount,
            simulationDuration = simulationDuration,
            arrivalRate = arrivalRate,
            generatorType = generatorType,
            googleTraceConfig = googleTraceConfig,
            arrival = arrival,
            scheduling = scheduling,
        )
    private val experimentRunner =
        RealtimeExperimentRunner(
            config = experimentConfig,
            metricsCollector = metricsCollector,
        )
    private val resultExporter = RealtimeResultExporter(outputContext)
    private val algorithmSelection = RealtimeAlgorithmSelection(resolvedAlgorithms)

    suspend fun runComparison(): List<RealtimeAlgorithmResult> =
        coroutineScope {
            logComparisonHeader()
            outputContext.saveExperimentInfo(
                RealtimeExperimentInfoFactory.create(
                    config = experimentConfig,
                    randomSeed = randomSeed,
                    runs = runs,
                    population = population,
                    maxIter = maxIter,
                ),
            )

            val selectedAlgorithms = algorithmSelection.required()
            Logger.info(
                "将运行 {} 个算法: {}",
                selectedAlgorithms.size,
                selectedAlgorithms.joinToString(", ") { it.name },
            )

            var results = emptyList<RealtimeAlgorithmResult>()
            val executionTime =
                measureTimeMillis {
                    val summaries = runAlgorithmSummaries(selectedAlgorithms).sortedBy { it.algorithmName }
                    results = summaries.mapNotNull { it.average }
                    Logger.info("所有实时算法执行完成")
                    resultExporter.printComparisonResults(summaries)
                    resultExporter.exportRealtimeToCSV(summaries)
                    resultExporter.saveSummaryResults(summaries)
                }

            Logger.info("实时调度算法对比实验完成，总耗时: {}ms", executionTime)
            results
        }

    suspend fun runComparisonWithStatistics(): List<RealtimeAlgorithmStatistics> {
        logStatisticsHeader()
        return runComparisonSummaries()
            .sortedBy { it.algorithmName }
            .mapNotNull { it.statistics }
    }

    internal suspend fun runComparisonSummaries(): List<RealtimeRunSummary> {
        val selectedAlgorithms = algorithmSelection.required()
        Logger.info(
            "将运行 {} 个算法: {}",
            selectedAlgorithms.size,
            selectedAlgorithms.joinToString(", ") { it.name },
        )
        return runAlgorithmSummaries(selectedAlgorithms)
    }

    private suspend fun runAlgorithmSummaries(algorithmsToRun: List<ResolvedAlgorithm>): List<RealtimeRunSummary> =
        concurrency.map(algorithmsToRun) { executeRealtimeAlgorithmSummarySafely(it) }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun executeRealtimeAlgorithmSummarySafely(algorithm: ResolvedAlgorithm): RealtimeRunSummary =
        try {
            Logger.debug("开始执行实时算法: {}", algorithm.name)
            val summary =
                buildRealtimeSummary(
                    algorithmName = algorithm.displayName,
                    outcomes = executeRealtimeAlgorithmRuns(algorithm),
                )
            Logger.debug("实时算法 {} 执行完成", algorithm.name)
            summary
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            Logger.error("实时算法 {} 执行失败: {}", exception, algorithm.name, exception.message)
            buildRealtimeSummary(
                algorithmName = algorithm.displayName,
                outcomes = listOf(RealtimeFailureResultFactory.from(algorithm.displayName, run = 0, exception)),
            )
        }

    internal fun buildRealtimeSummary(
        algorithmName: String,
        outcomes: List<RealtimeRunOutcome>,
    ): RealtimeRunSummary =
        RealtimeRunAggregator.buildSummary(
            algorithmName = algorithmName,
            outcomes = outcomes,
        )

    private suspend fun executeRealtimeAlgorithmRuns(algorithm: ResolvedAlgorithm): List<RealtimeRunOutcome> =
        concurrency.map(1..runs) { run -> executeRealtimeSingleRunAsync(algorithm, run) }

    private suspend fun executeRealtimeSingleRunAsync(
        algorithm: ResolvedAlgorithm,
        run: Int,
    ): RealtimeRunOutcome =
        concurrency.run {
            val runSeed = randomSeed + run
            runCatching {
                val result =
                    experimentRunner.run(
                        RealtimeExperimentRunRequest(
                            algorithmName = algorithm.displayName,
                            runSeed = runSeed,
                            schedulerFactory = { vms ->
                                algorithm.createRealtimeScheduler(vms, objectiveWeights, runSeed)
                            },
                        ),
                    )
                RealtimeRunOutcome.Success(result, run)
            }.fold(
                onSuccess = { outcome ->
                    resultExporter.saveTrialOutcome(outcome)
                    outcome
                },
                onFailure = { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Logger.error("实时算法 {} 第 {} 次运行失败: {}", throwable, algorithm.name, run, throwable.message)
                    val failed = RealtimeFailureResultFactory.from(algorithm.displayName, run, throwable)
                    resultExporter.saveTrialOutcome(failed)
                    failed
                },
            )
        }

    private fun logComparisonHeader() {
        Logger.info("\n${"=".repeat(LOG_SEPARATOR_LENGTH)}")
        Logger.info("开始实时调度算法对比实验")
        Logger.info("任务数量: {}", cloudletCount)
        Logger.info("仿真持续时间: {} 秒", simulationDuration)
        Logger.info("到达率: {} 任务/秒", arrivalRate)
        Logger.info("到达分布: {}", arrival.distribution)
        Logger.info("调度策略: {}", scheduling.strategy)
        Logger.info("运行次数: {}", runs)
        Logger.info("随机数种子: {}", randomSeed)
        Logger.info("执行模式: {}", concurrency.description)
        Logger.info("${"=".repeat(LOG_SEPARATOR_LENGTH)}")
    }

    private fun logStatisticsHeader() {
        Logger.info("\n${"=".repeat(LOG_SEPARATOR_LENGTH)}")
        Logger.info("开始实时调度算法对比实验 ({} 次运行)", runs)
        Logger.info("任务数量: {}", cloudletCount)
        Logger.info("仿真持续时间: {} 秒", simulationDuration)
        Logger.info("到达率: {} 任务/秒", arrivalRate)
        Logger.info("到达分布: {}", arrival.distribution)
        Logger.info("调度策略: {}", scheduling.strategy)
        Logger.info("初始随机数种子: {}", randomSeed)
        Logger.info("执行模式: {}", concurrency.description)
        Logger.info("${"=".repeat(LOG_SEPARATOR_LENGTH)}")
    }

    private companion object {
        const val LOG_SEPARATOR_LENGTH = 60
    }
}
