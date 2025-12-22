package datacenter

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import org.cloudsimplus.core.CloudSimPlus
import org.cloudsimplus.brokers.DatacenterBrokerSimple
import scheduler.*
import util.Logger
import util.StatisticalValue
import util.ResultsManager
import java.text.DecimalFormat
import java.util.*

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


/**
 * 算法对比运行器
 */
class ComparisonRunner(
    private val cloudletCount: Int = config.DatacenterConfig.DEFAULT_CLOUDLET_N,
    private val population: Int = 30,
    private val maxIter: Int = 100,
    private val randomSeed: Long = 0L,
    private val algorithms: List<config.BatchAlgorithmType> = emptyList(),  // 空列表 = 运行所有算法
    private val runs: Int = 1,  // 运行次数，默认1次
    private val generatorType: config.CloudletGeneratorType = config.CloudletGenConfig.GENERATOR_TYPE
) {
    private val random = Random(randomSeed)
    private val dft = DecimalFormat("###.##")
    
    /**
     * 运行单个算法并返回结果
     */
    private fun runAlgorithm(
        algorithmName: String,
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
        val cloudletGenerator = CloudletGenerator(random)
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
        val cloudletToVm = IntArray(cloudletList.size) { i ->
            finishedCloudlets.find { it.id == cloudletList[i].id }?.vm?.id?.toInt() ?: 0
        }
        val objFunc = SchedulerObjectiveFunction(cloudletList, vmList)
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
     * 运行所有算法并对比
     */
    fun runComparison(): List<AlgorithmResult> {
        Logger.info("\n${"=".repeat(60)}")
        Logger.info("开始算法对比实验")
        Logger.info("任务数量: {}", cloudletCount)
        Logger.info("种群大小: {}", population)
        Logger.info("最大迭代次数: {}", maxIter)
        Logger.info("随机数种子: {}", randomSeed)
        Logger.info("运行次数: {}", runs)
        Logger.info("${"=".repeat(60)}")
        
        val results = mutableListOf<AlgorithmResult>()
        
        // 确定要运行的算法列表（如果配置为空，则运行所有算法）
        val algorithmsToRun = if (algorithms.isEmpty()) {
            config.BatchAlgorithmType.entries
        } else {
            algorithms
        }
        
        Logger.info("将运行 {} 个算法: {}", algorithmsToRun.size, algorithmsToRun.joinToString(", ") { it.name })
        
        // 根据配置运行选定的算法
        for (algorithmType in algorithmsToRun) {
            if (runs > 1) {
                // 多次运行，计算统计值
                val runResults = mutableListOf<AlgorithmResult>()
                for (run in 1..runs) {
                    Logger.info("\n--- 第 {}/{} 次运行 ---", run, runs)
                    val result = when (algorithmType) {
                        config.BatchAlgorithmType.RANDOM -> {
                            runAlgorithm("Random") { cloudlets, vms ->
                                RandomScheduler(cloudlets, vms, Random(randomSeed + run))
                            }
                        }
                        config.BatchAlgorithmType.PSO -> {
                            runAlgorithm("PSO") { cloudlets, vms ->
                                PSOScheduler(cloudlets, vms, population, maxIter, Random(randomSeed + run))
                            }
                        }
                        config.BatchAlgorithmType.WOA -> {
                            runAlgorithm("WOA") { cloudlets, vms ->
                                WOAScheduler(cloudlets, vms, population, maxIter, Random(randomSeed + run))
                            }
                        }
                        config.BatchAlgorithmType.GWO -> {
                            runAlgorithm("GWO") { cloudlets, vms ->
                                GWOScheduler(cloudlets, vms, population, maxIter, Random(randomSeed + run))
                            }
                        }
                        config.BatchAlgorithmType.HHO -> {
                            runAlgorithm("HHO") { cloudlets, vms ->
                                HHOScheduler(cloudlets, vms, population, maxIter, Random(randomSeed + run))
                            }
                        }
                    }
                    runResults.add(result)
                }
                
                // 计算平均值（用于显示）
                val avgResult = AlgorithmResult(
                    algorithmName = runResults[0].algorithmName,
                    makespan = runResults.map { it.makespan }.average(),
                    loadBalance = runResults.map { it.loadBalance }.average(),
                    cost = runResults.map { it.cost }.average(),
                    totalTime = runResults.map { it.totalTime }.average(),
                    fitness = runResults.map { it.fitness }.average()
                )
                results.add(avgResult)
            } else {
                // 单次运行
                when (algorithmType) {
                    config.BatchAlgorithmType.RANDOM -> {
                        results.add(runAlgorithm("Random") { cloudlets, vms ->
                            RandomScheduler(cloudlets, vms, random)
                        })
                    }
                    config.BatchAlgorithmType.PSO -> {
                        results.add(runAlgorithm("PSO") { cloudlets, vms ->
                            PSOScheduler(cloudlets, vms, population, maxIter, random)
                        })
                    }
                    config.BatchAlgorithmType.WOA -> {
                        results.add(runAlgorithm("WOA") { cloudlets, vms ->
                            WOAScheduler(cloudlets, vms, population, maxIter, random)
                        })
                    }
                    config.BatchAlgorithmType.GWO -> {
                        results.add(runAlgorithm("GWO") { cloudlets, vms ->
                            GWOScheduler(cloudlets, vms, population, maxIter, random)
                        })
                    }
                    config.BatchAlgorithmType.HHO -> {
                        results.add(runAlgorithm("HHO") { cloudlets, vms ->
                            HHOScheduler(cloudlets, vms, population, maxIter, random)
                        })
                    }
                }
            }
        }
        
        // 打印对比结果
        printComparisonResults(results)
        
        return results
    }
    
    /**
     * 运行所有算法并返回统计结果（用于批量实验）
     */
    fun runComparisonWithStatistics(): List<AlgorithmStatistics> {
        Logger.info("\n${"=".repeat(60)}")
        Logger.info("开始算法对比实验（统计模式）")
        Logger.info("任务数量: {}", cloudletCount)
        Logger.info("种群大小: {}", population)
        Logger.info("最大迭代次数: {}", maxIter)
        Logger.info("随机数种子: {}", randomSeed)
        Logger.info("运行次数: {}", runs)
        Logger.info("${"=".repeat(60)}")
        
        val statistics = mutableListOf<AlgorithmStatistics>()
        
        // 确定要运行的算法列表（如果配置为空，则运行所有算法）
        val algorithmsToRun = if (algorithms.isEmpty()) {
            config.BatchAlgorithmType.entries
        } else {
            algorithms
        }
        
        Logger.info("将运行 {} 个算法: {}", algorithmsToRun.size, algorithmsToRun.joinToString(", ") { it.name })
        
        // 根据配置运行选定的算法
        for (algorithmType in algorithmsToRun) {
            val runResults = mutableListOf<AlgorithmResult>()
            
            // 运行多次
            for (run in 1..runs) {
                Logger.info("\n--- {} 算法，第 {}/{} 次运行 ---", algorithmType.name, run, runs)
                val result = when (algorithmType) {
                    config.BatchAlgorithmType.RANDOM -> {
                        runAlgorithm("Random") { cloudlets, vms ->
                            RandomScheduler(cloudlets, vms, Random(randomSeed + run))
                        }
                    }
                    config.BatchAlgorithmType.PSO -> {
                        runAlgorithm("PSO") { cloudlets, vms ->
                            PSOScheduler(cloudlets, vms, population, maxIter, Random(randomSeed + run))
                        }
                    }
                    config.BatchAlgorithmType.WOA -> {
                        runAlgorithm("WOA") { cloudlets, vms ->
                            WOAScheduler(cloudlets, vms, population, maxIter, Random(randomSeed + run))
                        }
                    }
                    config.BatchAlgorithmType.GWO -> {
                        runAlgorithm("GWO") { cloudlets, vms ->
                            GWOScheduler(cloudlets, vms, population, maxIter, Random(randomSeed + run))
                        }
                    }
                    config.BatchAlgorithmType.HHO -> {
                        runAlgorithm("HHO") { cloudlets, vms ->
                            HHOScheduler(cloudlets, vms, population, maxIter, Random(randomSeed + run))
                        }
                    }
                }
                runResults.add(result)
            }
            
            // 计算统计值
            val makespanStats = DescriptiveStatistics()
            val loadBalanceStats = DescriptiveStatistics()
            val costStats = DescriptiveStatistics()
            val totalTimeStats = DescriptiveStatistics()
            val fitnessStats = DescriptiveStatistics()
            
            for (result in runResults) {
                makespanStats.addValue(result.makespan)
                loadBalanceStats.addValue(result.loadBalance)
                costStats.addValue(result.cost)
                totalTimeStats.addValue(result.totalTime)
                fitnessStats.addValue(result.fitness)
            }
            
            statistics.add(AlgorithmStatistics(
                algorithmName = runResults[0].algorithmName,
                makespan = StatisticalValue(
                    mean = makespanStats.mean,
                    stdDev = makespanStats.standardDeviation,
                    min = makespanStats.min,
                    max = makespanStats.max
                ),
                loadBalance = StatisticalValue(
                    mean = loadBalanceStats.mean,
                    stdDev = loadBalanceStats.standardDeviation,
                    min = loadBalanceStats.min,
                    max = loadBalanceStats.max
                ),
                cost = StatisticalValue(
                    mean = costStats.mean,
                    stdDev = costStats.standardDeviation,
                    min = costStats.min,
                    max = costStats.max
                ),
                totalTime = StatisticalValue(
                    mean = totalTimeStats.mean,
                    stdDev = totalTimeStats.standardDeviation,
                    min = totalTimeStats.min,
                    max = totalTimeStats.max
                ),
                fitness = StatisticalValue(
                    mean = fitnessStats.mean,
                    stdDev = fitnessStats.standardDeviation,
                    min = fitnessStats.min,
                    max = fitnessStats.max
                )
            ))
        }
        
        return statistics
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
        
        // 导出 CSV 文件
        exportToCSV(results)
    }
    
    /**
     * 导出结果到 CSV 文件
     */
    private fun exportToCSV(results: List<AlgorithmResult>) {
        val csvFile = ResultsManager.generateBatchResultFileName()
        csvFile.bufferedWriter().use { writer ->
            // 写入表头
            if (runs > 1) {
                writer.write("Algorithm,Makespan_Mean,Makespan_StdDev,LoadBalance_Mean,LoadBalance_StdDev," +
                        "Cost_Mean,Cost_StdDev,TotalTime_Mean,TotalTime_StdDev,Fitness_Mean,Fitness_StdDev,Runs\n")
            } else {
                writer.write("Algorithm,Makespan,LoadBalance,Cost,TotalTime,Fitness\n")
            }
            
            // 写入数据
            for (result in results) {
                if (runs > 1) {
                    // 多次运行：导出平均值和标准差（需要从统计结果中获取）
                    writer.write("${result.algorithmName}," +
                            "${result.makespan},0.0," +
                            "${result.loadBalance},0.0," +
                            "${result.cost},0.0," +
                            "${result.totalTime},0.0," +
                            "${result.fitness},0.0," +
                            "${runs}\n")
                } else {
                    // 单次运行：导出原始值
                    writer.write("${result.algorithmName}," +
                            "${result.makespan}," +
                            "${result.loadBalance}," +
                            "${result.cost}," +
                            "${result.totalTime}," +
                            "${result.fitness}\n")
                }
            }
        }
        Logger.info("结果已导出到: {}", csvFile.absolutePath)
        if (runs > 1) {
            Logger.info("注: 导出值为 {} 次运行的平均值", runs)
        }
    }
}

