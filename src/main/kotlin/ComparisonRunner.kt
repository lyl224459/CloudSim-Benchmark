package datacenter

import org.cloudsimplus.core.CloudSimPlus
import org.cloudsimplus.brokers.DatacenterBrokerSimple
import scheduler.*
import util.ExperimentConcurrency
import util.ExperimentOutputContext
import util.Logger
import util.StatisticalValue
import util.mapCloudletsToVmIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import java.text.DecimalFormat
import java.util.*
import kotlin.system.measureTimeMillis

/**
 * 算法对比结果
 */
data class AlgorithmResult(
    val algorithmName: String,
    val makespan: Double,
    val loadBalance: Double,
    val cost: Double,
    val totalTime: Double,
    val fitness: Double
)

/**
 * 算法统计结果（多次运行的平均值和标准差）
 */
data class AlgorithmStatistics(
    val algorithmName: String,
    val makespan: StatisticalValue,
    val loadBalance: StatisticalValue,
    val cost: StatisticalValue,
    val totalTime: StatisticalValue,
    val fitness: StatisticalValue
)

private data class AlgorithmRunSummary(
    val average: AlgorithmResult,
    val statistics: AlgorithmStatistics?,
    val runResults: List<AlgorithmResult>
)

/**
 * 算法对比运行器
 */
class ComparisonRunner(
    private val cloudletCount: Int = config.DatacenterConfig.DEFAULT_CLOUDLET_N,
    private val population: Int = 30,
    private val maxIter: Int = 100,
    private val randomSeed: Long = 0L,
    private val runs: Int = 1,  // 运行次数，默认1次
    private val generatorType: config.CloudletGeneratorType = config.CloudletGenConfig.GENERATOR_TYPE,
    private val googleTraceConfig: config.GoogleTraceConfig? = null,
    private val objectiveWeights: config.ObjectiveWeightsConfig = config.ObjectiveWeightsConfig(),
    private val resolvedAlgorithms: List<ResolvedAlgorithm>,
    private val experimentDir: java.io.File? = null,
    private val outputContext: ExperimentOutputContext = ExperimentOutputContext(experimentDir),
    private val useCoroutines: Boolean = true,
    private val maxConcurrency: Int = 0,
    private val concurrency: ExperimentConcurrency = ExperimentConcurrency(useCoroutines, maxConcurrency)
) {
    private val dft = DecimalFormat("###.##")

    /**
     * 运行单个算法并返回结果
     */
    private fun runAlgorithm(
        algorithmName: String,
        runSeed: Long,
        schedulerFactory: (List<org.cloudsimplus.cloudlets.Cloudlet>, List<org.cloudsimplus.vms.Vm>) -> Scheduler
    ): AlgorithmResult {
        Logger.info("\n${"=".repeat(60)}")
        Logger.info("运行算法: {}", algorithmName)
        Logger.info("${"=".repeat(60)}")
        
        // 创建仿真环境
        val simulation = CloudSimPlus()
        
        // 创建数据中心
        val datacenter0 = DatacenterCreator.createDatacenter(simulation, "Datacenter0", DatacenterType.LOW)
        val datacenter1 = DatacenterCreator.createDatacenter(simulation, "Datacenter1", DatacenterType.MEDIUM)
        val datacenter2 = DatacenterCreator.createDatacenter(simulation, "Datacenter2", DatacenterType.HIGH)
        
        // 创建代理
        val broker = DatacenterBrokerSimple(simulation)
        
        // 创建虚拟机列表
        val vmList = DatacenterCreator.createVms()
        broker.submitVmList(vmList)
        
        // 创建云任务列表
        val random = Random(runSeed)
        val cloudletGenerator = CloudletGenerator(random, generatorType, googleTraceConfig)
        val cloudletList = cloudletGenerator.createCloudlets(0, cloudletCount)
        broker.submitCloudletList(cloudletList)
        
        // 创建调度器
        val scheduler = schedulerFactory(cloudletList, vmList)
        
        // 执行调度
        scheduler.schedule()
        
        // 开始仿真
        simulation.start()
        
        // 获取完成的云任务
        val finishedCloudlets = broker.getCloudletFinishedList<org.cloudsimplus.cloudlets.Cloudlet>()
        
        // 计算指标
        val (makespan, loadBalance, cost) = calculateMetrics(finishedCloudlets, vmList.size)
        
        // 计算总时间和适应度
        val cloudletToVm = mapCloudletsToVmIds(cloudletList, finishedCloudlets)
        val objFunc = SchedulerObjectiveFunction(cloudletList, vmList, objectiveWeights)
        val totalTime = objFunc.estimateTotalTime(cloudletToVm)
        val fitness = objFunc.calculate(cloudletToVm)
        
        Logger.info("\n结果:")
        Logger.info("  最大完成时间 (Makespan): {}", dft.format(makespan))
        Logger.info("  负载均衡度 (LB): {}", dft.format(loadBalance))
        Logger.info("  总成本 (Cost): {}", dft.format(cost))
        Logger.info("  总时间 (TotalTime): {}", dft.format(totalTime))
        Logger.info("  适应度 (Fitness): {}", dft.format(fitness))
        
        return AlgorithmResult(algorithmName, makespan, loadBalance, cost, totalTime, fitness)
    }
    
    /**
     * 计算性能指标
     */
    private fun calculateMetrics(
        cloudletList: List<org.cloudsimplus.cloudlets.Cloudlet>,
        vmNum: Int
    ): Triple<Double, Double, Double> {
        var makespan = 0.0
        val executeTimeOfVM = DoubleArray(vmNum)
        var cost = 0.0
        
        for (cloudlet in cloudletList) {
            if (cloudlet.status == org.cloudsimplus.cloudlets.Cloudlet.Status.SUCCESS) {
                val finishTime = cloudlet.finishTime
                if (finishTime > makespan) {
                    makespan = finishTime
                }
                
                val vmId = cloudlet.vm.id.toInt()
                // CloudSim Plus 8.5.5 API: 使用 getTotalExecutionTime() 方法
                val actualCPUTime = cloudlet.getTotalExecutionTime()
                executeTimeOfVM[vmId] += actualCPUTime
                
                val vm = cloudlet.vm
                val costPerSec = when {
                    vm.mips == config.DatacenterConfig.L_MIPS.toDouble() -> config.DatacenterConfig.L_PRICE
                    vm.mips == config.DatacenterConfig.M_MIPS.toDouble() -> config.DatacenterConfig.M_PRICE
                    vm.mips == config.DatacenterConfig.H_MIPS.toDouble() -> config.DatacenterConfig.H_PRICE
                    else -> config.DatacenterConfig.L_PRICE
                }
                cost += actualCPUTime * costPerSec
            }
        }
        
        // 计算负载均衡度
        val avgExecuteTime = executeTimeOfVM.average()
        var LB = 0.0
        for (i in 0 until vmNum) {
            LB += Math.pow(executeTimeOfVM[i] - avgExecuteTime, 2.0)
        }
        LB = Math.sqrt(LB / vmNum)
        
        return Triple(makespan, LB, cost)
    }
    

    /**
     * 运行所有算法并对比（协程优化版本 - 默认方法）
     * 支持并行执行多个算法和多次运行
     */
    suspend fun runComparison(): List<AlgorithmResult> = coroutineScope {
        Logger.info("\n${"=".repeat(60)}")
        Logger.info("开始算法对比实验")
        Logger.info("任务数量: {}", cloudletCount)
        Logger.info("种群大小: {}", population)
        Logger.info("最大迭代次数: {}", maxIter)
        Logger.info("随机数种子: {}", randomSeed)
        Logger.info("运行次数: {}", runs)
        Logger.info("执行模式: {}", executionModeDescription())
        Logger.info("${"=".repeat(60)}")

        outputContext.saveExperimentInfo(mapOf(
            "运行模式" to "批处理 (Batch)",
            "任务数量" to cloudletCount,
            "种群大小" to population,
            "最大迭代次数" to maxIter,
            "随机数种子" to randomSeed,
            "运行次数" to runs,
            "任务生成器" to generatorType.name
        ))

        val algorithmsToRun = algorithmsToRun()
        Logger.info("将运行 {} 个算法: {}", algorithmsToRun.size, algorithmsToRun.joinToString(", ") { it.name })

        val results = mutableListOf<AlgorithmResult>()
        val executionTime = measureTimeMillis {
            val summaries = executeAlgorithmSummaries(algorithmsToRun).sortedBy { it.average.algorithmName }
            results.addAll(summaries.map { it.average })

            Logger.info("所有算法执行完成")
            printComparisonResults(results)
            exportToCSV(summaries)

            val summaryData = results.map { r ->
                mapOf(
                    "Algorithm" to r.algorithmName,
                    "AvgMakespan" to r.makespan,
                    "AvgLoadBalance" to r.loadBalance,
                    "AvgCost" to r.cost,
                    "AvgTotalTime" to r.totalTime,
                    "AvgFitness" to r.fitness
                )
            }
            outputContext.saveSummaryResults(
                summaryData,
                listOf("Algorithm", "AvgMakespan", "AvgLoadBalance", "AvgCost", "AvgTotalTime", "AvgFitness")
            )
        }

        Logger.info("算法对比实验完成，总耗时: {}ms", executionTime)

        results
    }

    private fun algorithmsToRun(): List<ResolvedAlgorithm> {
        if (resolvedAlgorithms.isEmpty()) {
            throw IllegalArgumentException("ComparisonRunner 需要已解析的算法列表")
        }
        return resolvedAlgorithms
    }

    private suspend fun executeAlgorithmSummaries(algorithmsToRun: List<ResolvedAlgorithm>): List<AlgorithmRunSummary> {
        return concurrency.map(algorithmsToRun) { executeAlgorithmSummarySafely(it) }
    }

    private suspend fun executeAlgorithmSummarySafely(algorithm: ResolvedAlgorithm): AlgorithmRunSummary {
        return try {
            Logger.debug("开始执行算法: {}", algorithm.name)
            val result = buildAlgorithmSummary(executeAlgorithmRuns(algorithm))
            Logger.debug("算法 {} 执行完成", algorithm.name)
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.error("算法 {} 执行失败: {}", e, algorithm.name, e.message)
            failedAlgorithmSummary(algorithm.name)
        }
    }

    private fun failedAlgorithmSummary(algorithmName: String): AlgorithmRunSummary {
        val failedResult = AlgorithmResult(
            algorithmName = algorithmName,
            makespan = Double.NaN,
            loadBalance = Double.NaN,
            cost = Double.NaN,
            totalTime = Double.NaN,
            fitness = Double.NaN
        )
        return AlgorithmRunSummary(
            average = failedResult,
            statistics = null,
            runResults = emptyList()
        )
    }

    private fun buildAlgorithmSummary(runResults: List<AlgorithmResult>): AlgorithmRunSummary {
        return AlgorithmRunSummary(
            average = averageResults(runResults),
            statistics = calculateStatistics(runResults),
            runResults = runResults
        )
    }

    private fun executionModeDescription(): String {
        return concurrency.description
    }

    /**
     * 运行所有算法并返回统计结果（用于批量实验）
     */
    suspend fun runComparisonWithStatistics(): List<AlgorithmStatistics> {
        Logger.info("\n${"=".repeat(60)}")
        Logger.info("开始算法对比实验（统计模式）")
        Logger.info("任务数量: {}", cloudletCount)
        Logger.info("种群大小: {}", population)
        Logger.info("最大迭代次数: {}", maxIter)
        Logger.info("随机数种子: {}", randomSeed)
        Logger.info("运行次数: {}", runs)
        Logger.info("执行模式: {}", executionModeDescription())
        Logger.info("${"=".repeat(60)}")

        val algorithmsToRun = algorithmsToRun()
        Logger.info("将运行 {} 个算法: {}", algorithmsToRun.size, algorithmsToRun.joinToString(", ") { it.name })

        return executeAlgorithmSummaries(algorithmsToRun)
            .sortedBy { it.average.algorithmName }
            .mapNotNull { it.statistics }
    }

    /**
     * 打印对比结果表格
     */
    private fun printComparisonResults(results: List<AlgorithmResult>) {
        Logger.result("\n${"=".repeat(80)}")
        Logger.result("算法对比结果汇总")
        Logger.result("${"=".repeat(80)}")
        Logger.result(String.format("%-12s %-15s %-15s %-15s %-15s %-15s",
            "算法", "Makespan", "Load Balance", "Cost", "Total Time", "Fitness"))
        Logger.result("-".repeat(80))
        
        for (result in results) {
            Logger.result(String.format("%-12s %-15s %-15s %-15s %-15s %-15s",
                result.algorithmName,
                dft.format(result.makespan),
                dft.format(result.loadBalance),
                dft.format(result.cost),
                dft.format(result.totalTime),
                dft.format(result.fitness)))
        }
        
        Logger.result("-".repeat(80))
        
        // 找出最优值
        val bestMakespan = results.minByOrNull { it.makespan }
        val bestLB = results.minByOrNull { it.loadBalance }
        val bestCost = results.minByOrNull { it.cost }
        val bestFitness = results.minByOrNull { it.fitness }
        
        Logger.result("\n最优值:")
        Logger.result("  最小 Makespan: {} ({})", bestMakespan?.algorithmName, dft.format(bestMakespan?.makespan))
        Logger.result("  最小 Load Balance: {} ({})", bestLB?.algorithmName, dft.format(bestLB?.loadBalance))
        Logger.result("  最小 Cost: {} ({})", bestCost?.algorithmName, dft.format(bestCost?.cost))
        Logger.result("  最小 Fitness: {} ({})", bestFitness?.algorithmName, dft.format(bestFitness?.fitness))
        Logger.result("${"=".repeat(80)}\n")
        
    }
    
    /**
     * 导出结果到 CSV 文件
     */
    private fun exportToCSV(summaries: List<AlgorithmRunSummary>) {
        if (!outputContext.csvEnabled) {
            Logger.info("CSV 输出已禁用，跳过批处理结果导出")
            return
        }
        val csvFile = outputContext.generateResultFileName("batch_comparison")
        csvFile.bufferedWriter().use { writer ->
            // 写入表头
            if (runs > 1) {
                writer.write(
                    outputContext.csvLine(
                        listOf(
                            "Algorithm", "Makespan_Mean", "Makespan_StdDev", "LoadBalance_Mean", "LoadBalance_StdDev",
                            "Cost_Mean", "Cost_StdDev", "TotalTime_Mean", "TotalTime_StdDev", "Fitness_Mean", "Fitness_StdDev", "Runs"
                        )
                    ) + "\n"
                )
            } else {
                writer.write(outputContext.csvLine(listOf("Algorithm", "Makespan", "LoadBalance", "Cost", "TotalTime", "Fitness")) + "\n")
            }
            
            // 写入数据
            for (summary in summaries) {
                val result = summary.average
                val stat = summary.statistics
                if (runs > 1) {
                    writer.write(
                        outputContext.csvLine(
                            if (stat != null) {
                                listOf(
                                    stat.algorithmName,
                                    stat.makespan.mean, stat.makespan.stdDev,
                                    stat.loadBalance.mean, stat.loadBalance.stdDev,
                                    stat.cost.mean, stat.cost.stdDev,
                                    stat.totalTime.mean, stat.totalTime.stdDev,
                                    stat.fitness.mean, stat.fitness.stdDev,
                                    summary.runResults.size
                                )
                            } else {
                                listOf(
                                    result.algorithmName,
                                    result.makespan, Double.NaN,
                                    result.loadBalance, Double.NaN,
                                    result.cost, Double.NaN,
                                    result.totalTime, Double.NaN,
                                    result.fitness, Double.NaN,
                                    0
                                )
                            }
                        ) + "\n"
                    )
                } else {
                    // 单次运行：导出原始值
                    writer.write(
                        outputContext.csvLine(
                            listOf(
                                result.algorithmName,
                                result.makespan,
                                result.loadBalance,
                                result.cost,
                                result.totalTime,
                                result.fitness
                            )
                        ) + "\n"
                    )
                }
            }
        }
        Logger.info("结果已导出到: {}", csvFile.absolutePath)
        if (runs > 1) {
            Logger.info("注: 导出值为 {} 次运行的平均值与标准差", runs)
        }
    }

    private suspend fun executeAlgorithmRuns(algorithm: ResolvedAlgorithm): List<AlgorithmResult> {
        return concurrency.map(1..runs) { run ->
            Logger.debug("算法 {} 开始第 {}/{} 次运行", algorithm.name, run, runs)
            val result = executeSingleRunAsync(algorithm, run)
            Logger.debug("算法 {} 第 {} 次运行完成", algorithm.name, run)
            result
        }
    }

    private fun averageResults(runResults: List<AlgorithmResult>): AlgorithmResult {
        return AlgorithmResult(
            algorithmName = runResults[0].algorithmName,
            makespan = runResults.map { it.makespan }.average(),
            loadBalance = runResults.map { it.loadBalance }.average(),
            cost = runResults.map { it.cost }.average(),
            totalTime = runResults.map { it.totalTime }.average(),
            fitness = runResults.map { it.fitness }.average()
        )
    }

    private fun calculateStatistics(runResults: List<AlgorithmResult>): AlgorithmStatistics {
        fun stats(values: List<Double>): StatisticalValue {
            return StatisticalValue.fromArray(values.toDoubleArray())
        }

        return AlgorithmStatistics(
            algorithmName = runResults[0].algorithmName,
            makespan = stats(runResults.map { it.makespan }),
            loadBalance = stats(runResults.map { it.loadBalance }),
            cost = stats(runResults.map { it.cost }),
            totalTime = stats(runResults.map { it.totalTime }),
            fitness = stats(runResults.map { it.fitness })
        )
    }

    /**
     * 异步执行单次运行
     */
    private suspend fun executeSingleRunAsync(algorithm: ResolvedAlgorithm, run: Int): AlgorithmResult =
        concurrency.run {
            val runSeed = randomSeed + run
            val result = runAlgorithm(algorithm.displayName, runSeed) { cloudlets, vms ->
                algorithm.definition.batchFactory!!.invoke(
                    cloudlets,
                    vms,
                    objectiveWeights,
                    algorithm.settings,
                    runSeed
                )
            }

            outputContext.saveAlgorithmTrialResult(
                result.algorithmName,
                run,
                mapOf(
                    "Makespan" to result.makespan,
                    "LoadBalance" to result.loadBalance,
                    "Cost" to result.cost,
                    "TotalTime" to result.totalTime,
                    "Fitness" to result.fitness
                )
            )
            result
        }
}

