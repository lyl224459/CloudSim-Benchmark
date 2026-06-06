package datacenter

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import scheduler.ResolvedAlgorithm
import util.Logger
import util.StatisticalValue
import kotlin.system.measureTimeMillis

data class AlgorithmResult(
    val algorithmName: String,
    val makespan: Double,
    val loadBalance: Double,
    val cost: Double,
    val totalTime: Double,
    val fitness: Double,
)

data class AlgorithmStatistics(
    val algorithmName: String,
    val makespan: StatisticalValue,
    val loadBalance: StatisticalValue,
    val cost: StatisticalValue,
    val totalTime: StatisticalValue,
    val fitness: StatisticalValue,
)

class ComparisonRunner(
    private val request: BatchExperimentRequest,
) {
    private val batch = request.batch
    private val execution = request.execution
    private val algorithmExecutor = BatchAlgorithmExecutor(batch)
    private val resultExporter = BatchResultExporter(execution.outputContext, batch.runs)

    suspend fun runComparison(): List<AlgorithmResult> =
        coroutineScope {
            BatchExperimentLogger.logHeader("开始算法对比实验", request)
            execution.outputContext.saveExperimentInfo(
                mapOf(
                    "运行模式" to "批处理 (Batch)",
                    "任务数量" to batch.cloudletCount,
                    "种群大小" to batch.population,
                    "最大迭代次数" to batch.maxIter,
                    "随机数种子" to execution.randomSeed,
                    "运行次数" to batch.runs,
                    "任务生成器" to batch.generatorType.name,
                ),
            )

            val selectedAlgorithms = requiredAlgorithms()
            BatchExperimentLogger.logSelectedAlgorithms(selectedAlgorithms)
            var results = emptyList<AlgorithmResult>()
            val executionTime =
                measureTimeMillis {
                    val summaries = executeAlgorithmSummaries(selectedAlgorithms).sortedBy { it.algorithmName }
                    results = summaries.mapNotNull { it.average }
                    Logger.info("所有算法执行完成")
                    resultExporter.printComparisonResults(summaries)
                    resultExporter.exportToCsv(summaries)
                    resultExporter.saveSummary(summaries)
                }
            Logger.info("算法对比实验完成，总耗时: {}ms", executionTime)
            results
        }

    suspend fun runComparisonWithStatistics(): List<AlgorithmStatistics> {
        BatchExperimentLogger.logHeader("开始算法对比实验（统计模式）", request)
        return runComparisonSummaries()
            .sortedBy { it.algorithmName }
            .mapNotNull { it.statistics }
    }

    internal suspend fun runComparisonSummaries(): List<BatchRunSummary> {
        val selectedAlgorithms = requiredAlgorithms()
        BatchExperimentLogger.logSelectedAlgorithms(selectedAlgorithms)
        return executeAlgorithmSummaries(selectedAlgorithms)
    }

    internal fun buildAlgorithmSummary(
        algorithmName: String,
        outcomes: List<BatchRunOutcome>,
    ): BatchRunSummary = BatchRunAggregator.buildSummary(algorithmName, outcomes)

    private fun requiredAlgorithms(): List<ResolvedAlgorithm> {
        require(execution.resolvedAlgorithms.isNotEmpty()) { "ComparisonRunner 需要已解析的算法列表" }
        return execution.resolvedAlgorithms
    }

    private suspend fun executeAlgorithmSummaries(algorithms: List<ResolvedAlgorithm>): List<BatchRunSummary> =
        execution.concurrency.map(algorithms) { executeAlgorithmSummarySafely(it) }

    private suspend fun executeAlgorithmSummarySafely(algorithm: ResolvedAlgorithm): BatchRunSummary =
        try {
            Logger.debug("开始执行算法: {}", algorithm.name)
            val summary = BatchRunAggregator.buildSummary(algorithm.displayName, executeAlgorithmRuns(algorithm))
            Logger.debug("算法 {} 执行完成", algorithm.name)
            summary
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: IllegalArgumentException) {
            failedAlgorithmSummary(algorithm, exception)
        } catch (exception: IllegalStateException) {
            failedAlgorithmSummary(algorithm, exception)
        }

    private fun failedAlgorithmSummary(
        algorithm: ResolvedAlgorithm,
        error: RuntimeException,
    ): BatchRunSummary {
        Logger.error("算法 {} 执行失败: {}", error, algorithm.name, error.message)
        return BatchRunAggregator.buildSummary(
            algorithm.displayName,
            listOf(
                BatchRunOutcome.Failed(
                    algorithmName = algorithm.displayName,
                    run = 0,
                    errorType = error::class.simpleName ?: error::class.java.simpleName,
                    errorMessage = error.message.orEmpty(),
                ),
            ),
        )
    }

    private suspend fun executeAlgorithmRuns(algorithm: ResolvedAlgorithm): List<BatchRunOutcome> =
        execution.concurrency.map(1..batch.runs) { run ->
            Logger.debug("算法 {} 开始第 {}/{} 次运行", algorithm.name, run, batch.runs)
            executeSingleRun(algorithm, run).also {
                Logger.debug("算法 {} 第 {} 次运行完成", algorithm.name, run)
            }
        }

    private suspend fun executeSingleRun(
        algorithm: ResolvedAlgorithm,
        run: Int,
    ): BatchRunOutcome =
        execution.concurrency.run {
            val runSeed = execution.randomSeed + run
            runCatching {
                BatchRunOutcome.Success(
                    result =
                        algorithmExecutor.run(algorithm.displayName, runSeed) { cloudlets, vms ->
                            algorithm.createBatchScheduler(cloudlets, vms, batch.objectiveWeights, runSeed)
                        },
                    run = run,
                )
            }.fold(
                onSuccess = { outcome ->
                    resultExporter.saveTrial(outcome)
                    outcome
                },
                onFailure = { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Logger.error("算法 {} 第 {} 次运行失败: {}", throwable, algorithm.name, run, throwable.message)
                    val failed =
                        BatchRunOutcome.Failed(
                            algorithmName = algorithm.displayName,
                            run = run,
                            errorType = throwable::class.simpleName ?: throwable::class.java.simpleName,
                            errorMessage = throwable.message.orEmpty(),
                        )
                    resultExporter.saveTrial(failed)
                    failed
                },
            )
        }
}

private object BatchExperimentLogger {
    fun logHeader(
        title: String,
        request: BatchExperimentRequest,
    ) {
        Logger.info("\n${"=".repeat(SECTION_SEPARATOR_WIDTH)}")
        Logger.info(title)
        Logger.info("任务数量: {}", request.batch.cloudletCount)
        Logger.info("种群大小: {}", request.batch.population)
        Logger.info("最大迭代次数: {}", request.batch.maxIter)
        Logger.info("随机数种子: {}", request.execution.randomSeed)
        Logger.info("运行次数: {}", request.batch.runs)
        Logger.info("执行模式: {}", request.execution.concurrency.description)
        Logger.info("${"=".repeat(SECTION_SEPARATOR_WIDTH)}")
    }

    fun logSelectedAlgorithms(algorithms: List<ResolvedAlgorithm>) {
        Logger.info("将运行 {} 个算法: {}", algorithms.size, algorithms.joinToString(", ") { it.name })
    }

    private const val SECTION_SEPARATOR_WIDTH = 60
}
