package datacenter

import config.CloudletGeneratorType
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import util.ExperimentConcurrency
import util.ExperimentOutputContext
import util.Logger
import util.StatisticalValue
import java.text.DecimalFormat
import scheduler.ResolvedAlgorithm
import kotlinx.coroutines.runBlocking

/**
 * 批量任务数实验运行器
 * 支持按照不同的任务数批量执行实验，每个任务数可以运行多次并取平均值
 */
class BatchCloudletCountRunner(
    /**
     * 要测试的任务数列表
     * 例如: listOf(50, 100, 200, 500, 1000)
     */
    private val cloudletCounts: List<Int>,
    
    /**
     * 种群大小
     */
    private val population: Int = 30,
    
    /**
     * 最大迭代次数
     */
    private val maxIter: Int = 50,
    
    /**
     * 随机数种子
     */
    private val randomSeed: Long = 0L,
    
    /**
     * 已解析的算法定义
     */
    private val resolvedAlgorithms: List<ResolvedAlgorithm>,
    
    /**
     * 每个任务数的运行次数（用于计算平均值）
     */
    private val runs: Int = 1,
    
    /**
     * 任务生成器类型
     */
    private val generatorType: CloudletGeneratorType = config.CloudletGenConfig.GENERATOR_TYPE,

    /**
     * 实验目录
     */
    private val experimentDir: java.io.File? = null,
    private val outputContext: ExperimentOutputContext = ExperimentOutputContext(experimentDir),
    private val useCoroutines: Boolean = true,
    private val maxConcurrency: Int = 0,
    private val concurrency: ExperimentConcurrency = ExperimentConcurrency(useCoroutines, maxConcurrency)
) {
    private val dft = DecimalFormat("###.##")
    
    /**
     * 运行批量任务数实验（批处理模式）
     */
    suspend fun runExperiment() {
        Logger.info("\n${"=".repeat(80)}")
        Logger.info("开始批量任务数实验")
        Logger.info("${"=".repeat(80)}")
        Logger.info("任务数列表: {}", cloudletCounts.joinToString(", "))
        Logger.info("每个任务数运行次数: {}", runs)
        Logger.info("种群大小: {}, 最大迭代: {}", population, maxIter)
        Logger.info("随机数种子: {}", randomSeed)
        Logger.info("${"=".repeat(80)}\n")

        outputContext.saveExperimentInfo(mapOf(
            "运行模式" to "批处理批量任务数 (Batch Multi)",
            "任务数列表" to cloudletCounts.joinToString(", "),
            "每个任务数运行次数" to runs,
            "种群大小" to population,
            "最大迭代次数" to maxIter,
            "随机数种子" to randomSeed,
            "任务生成器" to generatorType.name
        ))

        val allResults = concurrency.map(cloudletCounts.withIndex().toList()) { indexed ->
            runCloudletCount(indexed.index, indexed.value)
        }.toMap()
        
        // 导出汇总结果
        exportBatchResults(allResults)
        
        Logger.info("\n${"=".repeat(80)}")
        Logger.info("批量任务数实验完成！")
        Logger.info("${"=".repeat(80)}")
    }

    fun runExperimentSync() = runBlocking {
        runExperiment()
    }

    private suspend fun runCloudletCount(index: Int, cloudletCount: Int): Pair<Int, List<AlgorithmStatistics>> {
        Logger.info("\n${"#".repeat(80)}")
        Logger.info("任务数: {} ({}/{})", cloudletCount, index + 1, cloudletCounts.size)
        Logger.info("${"#".repeat(80)}")

        val childOutputContext = outputContext.child("cloudlets_$cloudletCount")
        val runner = ComparisonRunner(
            cloudletCount = cloudletCount,
            population = population,
            maxIter = maxIter,
            randomSeed = randomSeed,
            resolvedAlgorithms = resolvedAlgorithms,
            runs = runs,
            generatorType = generatorType,
            experimentDir = childOutputContext.experimentDir,
            outputContext = childOutputContext,
            concurrency = concurrency
        )

        val statistics = runner.runComparisonWithStatistics()

        Logger.info("\n任务数 {} 的统计结果:", cloudletCount)
        for (stat in statistics) {
            Logger.info(
                "  {}: Makespan={}±{}, Fitness={}±{}",
                stat.algorithmName,
                dft.format(stat.makespan.mean),
                dft.format(stat.makespan.stdDev),
                dft.format(stat.fitness.mean),
                dft.format(stat.fitness.stdDev)
            )
        }

        Logger.info("\n任务数 {} 的实验完成", cloudletCount)
        return cloudletCount to statistics
    }
    
    /**
     * 导出批量实验结果到 CSV
     */
    private fun exportBatchResults(results: Map<Int, List<AlgorithmStatistics>>) {
        if (!outputContext.csvEnabled) {
            Logger.info("CSV 输出已禁用，跳过批处理批量任务数结果导出")
            printSummaryTable(results)
            return
        }
        val csvFile = outputContext.generateResultFileName("batch_cloudlet_count_summary")
        
        csvFile.bufferedWriter().use { writer ->
            // 写入表头
            writer.write(
                outputContext.csvLine(
                    listOf(
                        "CloudletCount", "Algorithm",
                        "Makespan_Mean", "Makespan_StdDev",
                        "LoadBalance_Mean", "LoadBalance_StdDev",
                        "Cost_Mean", "Cost_StdDev",
                        "TotalTime_Mean", "TotalTime_StdDev",
                        "Fitness_Mean", "Fitness_StdDev", "Runs"
                    )
                ) + "\n"
            )
            
            // 写入数据（按任务数排序）
            val sortedCounts = results.keys.sorted()
            for (cloudletCount in sortedCounts) {
                val algorithmStats = results[cloudletCount] ?: continue
                
                for (stat in algorithmStats) {
                    writer.write(
                        outputContext.csvLine(
                            listOf(
                                cloudletCount, stat.algorithmName,
                                stat.makespan.mean, stat.makespan.stdDev,
                                stat.loadBalance.mean, stat.loadBalance.stdDev,
                                stat.cost.mean, stat.cost.stdDev,
                                stat.totalTime.mean, stat.totalTime.stdDev,
                                stat.fitness.mean, stat.fitness.stdDev,
                                runs
                            )
                        ) + "\n"
                    )
                }
            }
        }
        
        Logger.info("\n批量实验结果已导出到: {}", csvFile.absolutePath)
        
        val summaryHeaders = listOf("CloudletCount", "Algorithm", "AvgMakespan", "AvgLoadBalance", "AvgCost", "AvgTotalTime", "AvgFitness")
        val summaryData = results.flatMap { (count, stats) ->
            stats.map { stat ->
                mapOf(
                    "CloudletCount" to count,
                    "Algorithm" to stat.algorithmName,
                    "AvgMakespan" to stat.makespan.mean,
                    "AvgLoadBalance" to stat.loadBalance.mean,
                    "AvgCost" to stat.cost.mean,
                    "AvgTotalTime" to stat.totalTime.mean,
                    "AvgFitness" to stat.fitness.mean
                )
            }
        }
        outputContext.saveSummaryResults(summaryData, summaryHeaders)

        // 打印汇总表格
        printSummaryTable(results)
    }
    
    /**
     * 打印汇总表格
     */
    private fun printSummaryTable(results: Map<Int, List<AlgorithmStatistics>>) {
        Logger.info("\n${"=".repeat(100)}")
        Logger.info("批量任务数实验汇总")
        Logger.info("${"=".repeat(100)}")
        
        val sortedCounts = results.keys.sorted()
        
        // 获取所有算法名称
        val allAlgorithms = results.values.flatMap { it.map { stat -> stat.algorithmName } }.distinct().sorted()
        
        // 打印表头
        Logger.info(String.format("%-12s", "任务数"))
        for (alg in allAlgorithms) {
            Logger.info(String.format("%-20s", alg))
        }
        Logger.info("")
        Logger.info("-".repeat(100))
        
        // 打印每个指标
        val metrics = listOf(
            "Makespan" to { stat: AlgorithmStatistics -> stat.makespan },
            "LoadBalance" to { stat: AlgorithmStatistics -> stat.loadBalance },
            "Cost" to { stat: AlgorithmStatistics -> stat.cost },
            "Fitness" to { stat: AlgorithmStatistics -> stat.fitness }
        )
        
        for ((metricName, metricGetter) in metrics) {
            Logger.info("\n{} (平均值):", metricName)
            Logger.info(String.format("%-12s", "任务数"))
            for (alg in allAlgorithms) {
                Logger.info(String.format("%-20s", alg))
            }
            Logger.info("")
            Logger.info("-".repeat(100))
            
            for (cloudletCount in sortedCounts) {
                Logger.info(String.format("%-12d", cloudletCount))
                val algorithmStats = results[cloudletCount] ?: emptyList()
                val statsMap = algorithmStats.associateBy { it.algorithmName }
                
                for (alg in allAlgorithms) {
                    val stat = statsMap[alg]
                    if (stat != null) {
                        val value = metricGetter(stat)
                        Logger.info(String.format("%-20s", "${dft.format(value.mean)} ± ${dft.format(value.stdDev)}"))
                    } else {
                        Logger.info(String.format("%-20s", "-"))
                    }
                }
                Logger.info("")
            }
        }
        
        Logger.info("${"=".repeat(100)}")
    }
}

