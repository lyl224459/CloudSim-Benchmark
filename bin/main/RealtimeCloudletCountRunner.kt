package datacenter

import config.RealtimeAlgorithmType
import config.CloudletGeneratorType
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import util.Logger
import util.StatisticalValue
import util.ResultsManager
import java.text.DecimalFormat

/**
 * 实时调度批量任务数实验运行器
 * 支持按照不同的任务数批量执行实时调度实验，每个任务数可以运行多次并取平均值
 */
class RealtimeCloudletCountRunner(
    /**
     * 要测试的任务数列表
     * 例如: listOf(50, 100, 200, 500, 1000)
     */
    private val cloudletCounts: List<Int>,
    
    /**
     * 仿真持续时间（秒）
     */
    private val simulationDuration: Double = 500.0,
    
    /**
     * 平均每秒到达的任务数
     */
    private val arrivalRate: Double = 5.0,
    
    /**
     * 种群大小
     */
    private val population: Int = 20,
    
    /**
     * 最大迭代次数
     */
    private val maxIter: Int = 20,
    
    /**
     * 随机数种子
     */
    private val randomSeed: Long = 0L,
    
    /**
     * 要运行的算法列表（空列表表示运行所有算法）
     */
    private val algorithms: List<RealtimeAlgorithmType> = emptyList(),
    
    /**
     * 每个任务数的运行次数（用于计算平均值）
     */
    private val runs: Int = 1,
    
    /**
     * 任务生成器类型
     */
    private val generatorType: CloudletGeneratorType = config.CloudletGenConfig.GENERATOR_TYPE
) {
    private val dft = DecimalFormat("###.##")
    
    /**
     * 运行批量任务数实验
     */
    fun runBatchExperiment() {
        Logger.info("\n${"=".repeat(80)}")
        Logger.info("开始实时调度批量任务数实验")
        Logger.info("${"=".repeat(80)}")
        Logger.info("任务数列表: {}", cloudletCounts.joinToString(", "))
        Logger.info("每个任务数运行次数: {}", runs)
        Logger.info("仿真持续时间: {}s, 到达率: {}/s", simulationDuration, arrivalRate)
        Logger.info("种群大小: {}, 最大迭代: {}", population, maxIter)
        Logger.info("随机数种子: {}", randomSeed)
        Logger.info("${"=".repeat(80)}\n")
        
        // 存储所有任务数的结果
        val allResults = mutableMapOf<Int, List<RealtimeAlgorithmStatistics>>()
        
        // 对每个任务数进行实验
        for ((index, cloudletCount) in cloudletCounts.withIndex()) {
            Logger.info("\n${"#".repeat(80)}")
            Logger.info("任务数: {} ({}/{})", cloudletCount, index + 1, cloudletCounts.size)
            Logger.info("${"#".repeat(80)}")
            
            // 运行对比实验（使用统计模式）
            val runner = RealtimeComparisonRunner(
                cloudletCount = cloudletCount,
                simulationDuration = simulationDuration,
                arrivalRate = arrivalRate,
                population = population,
                maxIter = maxIter,
                randomSeed = randomSeed,
                algorithms = algorithms,
                runs = runs,
                generatorType = generatorType
            )
            
            // 获取统计结果
            val statistics = runner.runComparisonWithStatistics()
            allResults[cloudletCount] = statistics
            
            Logger.info("\n任务数 {} 的统计结果:", cloudletCount)
            for (stat in statistics) {
                Logger.info("  {}: Makespan={}±{}, Fitness={}±{}, AvgWaitingTime={}±{}", 
                    stat.algorithmName, 
                    dft.format(stat.makespan.mean), 
                    dft.format(stat.makespan.stdDev),
                    dft.format(stat.fitness.mean), 
                    dft.format(stat.fitness.stdDev),
                    dft.format(stat.averageWaitingTime.mean),
                    dft.format(stat.averageWaitingTime.stdDev))
            }
            
            Logger.info("\n任务数 {} 的实验完成", cloudletCount)
        }
        
        // 导出汇总结果
        exportBatchResults(allResults)
        
        Logger.info("\n${"=".repeat(80)}")
        Logger.info("实时调度批量任务数实验完成！")
        Logger.info("${"=".repeat(80)}")
    }
    
    /**
     * 导出批量实验结果到 CSV
     */
    private fun exportBatchResults(results: Map<Int, List<RealtimeAlgorithmStatistics>>) {
        val csvFile = ResultsManager.generateFileName("realtime_cloudlet_count_comparison")
        
        csvFile.bufferedWriter().use { writer ->
            // 写入表头
            writer.write("CloudletCount,Algorithm,")
            writer.write("Makespan_Mean,Makespan_StdDev,")
            writer.write("LoadBalance_Mean,LoadBalance_StdDev,")
            writer.write("Cost_Mean,Cost_StdDev,")
            writer.write("TotalTime_Mean,TotalTime_StdDev,")
            writer.write("Fitness_Mean,Fitness_StdDev,")
            writer.write("AvgWaitingTime_Mean,AvgWaitingTime_StdDev,")
            writer.write("AvgResponseTime_Mean,AvgResponseTime_StdDev,Runs\n")
            
            // 写入数据（按任务数排序）
            val sortedCounts = results.keys.sorted()
            for (cloudletCount in sortedCounts) {
                val algorithmStats = results[cloudletCount] ?: continue
                
                for (stat in algorithmStats) {
                    writer.write("$cloudletCount,${stat.algorithmName},")
                    writer.write("${stat.makespan.mean},${stat.makespan.stdDev},")
                    writer.write("${stat.loadBalance.mean},${stat.loadBalance.stdDev},")
                    writer.write("${stat.cost.mean},${stat.cost.stdDev},")
                    writer.write("${stat.totalTime.mean},${stat.totalTime.stdDev},")
                    writer.write("${stat.fitness.mean},${stat.fitness.stdDev},")
                    writer.write("${stat.averageWaitingTime.mean},${stat.averageWaitingTime.stdDev},")
                    writer.write("${stat.averageResponseTime.mean},${stat.averageResponseTime.stdDev},$runs\n")
                }
            }
        }
        
        Logger.info("\n批量实验结果已导出到: {}", csvFile.absolutePath)
        
        // 打印汇总表格
        printSummaryTable(results)
    }
    
    /**
     * 打印汇总表格
     */
    private fun printSummaryTable(results: Map<Int, List<RealtimeAlgorithmStatistics>>) {
        Logger.info("\n${"=".repeat(100)}")
        Logger.info("实时调度批量任务数实验汇总")
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
            "Makespan" to { stat: RealtimeAlgorithmStatistics -> stat.makespan },
            "LoadBalance" to { stat: RealtimeAlgorithmStatistics -> stat.loadBalance },
            "Cost" to { stat: RealtimeAlgorithmStatistics -> stat.cost },
            "Fitness" to { stat: RealtimeAlgorithmStatistics -> stat.fitness },
            "AvgWaitingTime" to { stat: RealtimeAlgorithmStatistics -> stat.averageWaitingTime },
            "AvgResponseTime" to { stat: RealtimeAlgorithmStatistics -> stat.averageResponseTime }
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

