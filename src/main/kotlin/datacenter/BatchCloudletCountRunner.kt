package datacenter

import util.Logger
import java.text.DecimalFormat

private const val LOG_SEPARATOR_WIDTH = 80

/**
 * 批量任务数实验运行器
 * 支持按照不同的任务数批量执行实验，每个任务数可以运行多次并取平均值
 */
class BatchCloudletCountRunner internal constructor(
    private val request: BatchExperimentRequest,
    private val summaryRunner: BatchSummaryRunnerFactory,
    private val exporter: BatchCloudletCountExportService,
) {
    private val cloudletCounts = request.batch.cloudletCounts
    private val population = request.batch.population
    private val maxIter = request.batch.maxIter
    private val randomSeed = request.execution.randomSeed
    private val runs = request.batch.runs
    private val generatorType = request.batch.generatorType
    private val outputContext = request.execution.outputContext
    private val concurrency = request.execution.concurrency
    private val dft = DecimalFormat("###.##")

    constructor(request: BatchExperimentRequest) : this(
        request,
        BatchSummaryRunnerFactory { child -> ComparisonRunner(child).runComparisonSummaries() },
        BatchCloudletCountResultExporter(request.execution.outputContext),
    )

    /**
     * 运行批量任务数实验（批处理模式）
     */
    suspend fun runExperiment() {
        Logger.info("\n${"=".repeat(LOG_SEPARATOR_WIDTH)}")
        Logger.info("开始批量任务数实验")
        Logger.info("${"=".repeat(LOG_SEPARATOR_WIDTH)}")
        Logger.info("任务数列表: {}", cloudletCounts.joinToString(", "))
        Logger.info("每个任务数运行次数: {}", runs)
        Logger.info("种群大小: {}, 最大迭代: {}", population, maxIter)
        Logger.info("随机数种子: {}", randomSeed)
        Logger.info("${"=".repeat(LOG_SEPARATOR_WIDTH)}\n")

        outputContext.saveExperimentInfo(
            mapOf(
                "运行模式" to "批处理批量任务数 (Batch Multi)",
                "任务数列表" to cloudletCounts.joinToString(", "),
                "每个任务数运行次数" to runs,
                "种群大小" to population,
                "最大迭代次数" to maxIter,
                "随机数种子" to randomSeed,
                "任务生成器" to generatorType.name,
            ),
        )

        val allResults =
            concurrency
                .map(cloudletCounts.withIndex().toList()) { indexed ->
                    runCloudletCount(indexed.index, indexed.value)
                }.toMap()

        // 导出汇总结果
        exporter.export(allResults)

        Logger.info("\n${"=".repeat(LOG_SEPARATOR_WIDTH)}")
        Logger.info("批量任务数实验完成！")
        Logger.info("${"=".repeat(LOG_SEPARATOR_WIDTH)}")
    }

    private suspend fun runCloudletCount(
        index: Int,
        cloudletCount: Int,
    ): Pair<Int, List<BatchRunSummary>> {
        Logger.info("\n${"#".repeat(LOG_SEPARATOR_WIDTH)}")
        Logger.info("任务数: {} ({}/{})", cloudletCount, index + 1, cloudletCounts.size)
        Logger.info("${"#".repeat(LOG_SEPARATOR_WIDTH)}")

        val childOutputContext = outputContext.child("cloudlets_$cloudletCount")
        val childRequest =
            request.copy(
                batch = request.batch.copy(cloudletCount = cloudletCount),
                execution = request.execution.copy(outputContext = childOutputContext),
            )
        val summaries = summaryRunner.run(childRequest).sortedBy { it.algorithmName }
        val statistics = summaries.mapNotNull { it.statistics }

        Logger.info("\n任务数 {} 的统计结果:", cloudletCount)
        for (stat in statistics) {
            Logger.info(
                "  {}: Makespan={}±{}, Fitness={}±{}",
                stat.algorithmName,
                dft.format(stat.makespan.mean),
                dft.format(stat.makespan.stdDev),
                dft.format(stat.fitness.mean),
                dft.format(stat.fitness.stdDev),
            )
        }

        Logger.info("\n任务数 {} 的实验完成", cloudletCount)
        return cloudletCount to summaries
    }
}
