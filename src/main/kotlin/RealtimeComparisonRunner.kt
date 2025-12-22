package datacenter

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import broker.RealtimeBroker
import org.cloudsimplus.core.CloudSimPlus
import scheduler.*
import util.Logger
import util.StatisticalValue
import util.ResultsManager
import java.text.DecimalFormat
import java.util.*

/**
 * 实时调度算法对比结果
 */
data class RealtimeAlgorithmResult(
    val algorithmName: String,
    val makespan: Double,
    val loadBalance: Double,
    val cost: Double,
    val totalTime: Double,
    val fitness: Double,
    val averageWaitingTime: Double,
    val averageResponseTime: Double
)

/**
 * 实时调度算法统计结果（多次运行的平均值和标准差）
 */
data class RealtimeAlgorithmStatistics(
    val algorithmName: String,
    val makespan: StatisticalValue,
    val loadBalance: StatisticalValue,
    val cost: StatisticalValue,
    val totalTime: StatisticalValue,
    val fitness: StatisticalValue,
    val averageWaitingTime: StatisticalValue,
    val averageResponseTime: StatisticalValue
)


/**
 * 实时调度对比运行器
 */
class RealtimeComparisonRunner(
    private val cloudletCount: Int = 100,
    private val simulationDuration: Double = 1000.0,
    private val arrivalRate: Double = 10.0,  // 每秒到达的任务数
    private val population: Int = 20,
    private val maxIter: Int = 20,
    private val randomSeed: Long = Constants.DEFAULT_RANDOM_SEED,
    private val algorithms: List<config.RealtimeAlgorithmType> = emptyList(),  // 空列表 = 运行所有算法
    private val runs: Int = 1,  // 运行次数，默认1次
    private val generatorType: config.CloudletGeneratorType = config.CloudletGenConfig.GENERATOR_TYPE
) {
    private val random = Random(randomSeed)
    private val dft = DecimalFormat("###.##")
    
    /**
     * 运行单个实时调度算法
     */
    private fun runRealtimeAlgorithm(
        algorithmName: String,
        schedulerFactory: (List<org.cloudsimplus.vms.Vm>) -> RealtimeScheduler
    ): RealtimeAlgorithmResult {
        Logger.info("\n${"=".repeat(60)}")
        Logger.info("运行实时调度算法: {}", algorithmName)
        Logger.info("${"=".repeat(60)}")
        
        // 创建仿真环境
        val simulation = CloudSimPlus()
        
        // 创建数据中心
        val datacenter0 = DatacenterCreator.createDatacenter(simulation, "Datacenter0", DatacenterType.LOW)
        val datacenter1 = DatacenterCreator.createDatacenter(simulation, "Datacenter1", DatacenterType.MEDIUM)
        val datacenter2 = DatacenterCreator.createDatacenter(simulation, "Datacenter2", DatacenterType.HIGH)
        
        // 创建虚拟机列表
        val vmList = DatacenterCreator.createVms()
        
        // 创建实时调度器
        val scheduler = schedulerFactory(vmList)
        
        // 创建实时代理
        val broker = RealtimeBroker(simulation, scheduler, vmList)
        broker.submitVmList(vmList)
        
        // 生成实时任务（带到达时间）
        val cloudletGenerator = RealtimeCloudletGenerator(random, arrivalRate, generatorType)
        val cloudletList = cloudletGenerator.createRealtimeCloudlets(0, cloudletCount, simulationDuration)
        
        // 提交任务（按到达时间）
        broker.submitCloudletListRealtime(cloudletList)
        
        Logger.info("已生成 {} 个实时任务", cloudletList.size)
        Logger.info("仿真持续时间: {} 秒", simulationDuration)
        
        // 开始仿真
        simulation.start()
        
        // 获取完成的云任务
        val finishedCloudlets = broker.getCloudletFinishedList<org.cloudsimplus.cloudlets.Cloudlet>()
        
        // 计算指标
        val (makespan, loadBalance, cost, avgWaitingTime, avgResponseTime) = 
            calculateRealtimeMetrics(finishedCloudlets, vmList.size)
        
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
        Logger.info("  平均等待时间: {}", dft.format(avgWaitingTime))
        Logger.info("  平均响应时间: {}", dft.format(avgResponseTime))
        Logger.info("  适应度 (Fitness): {}", dft.format(fitness))
        
        return RealtimeAlgorithmResult(
            algorithmName, makespan, loadBalance, cost, totalTime, fitness,
            avgWaitingTime, avgResponseTime
        )
    }
    
    /**
     * 计算实时调度指标
     */
    private fun calculateRealtimeMetrics(
        cloudletList: List<org.cloudsimplus.cloudlets.Cloudlet>,
        vmNum: Int
    ): Quintuple<Double, Double, Double, Double, Double> {
        var makespan = 0.0
        val executeTimeOfVM = DoubleArray(vmNum)
        var cost = 0.0
        var totalWaitingTime = 0.0
        var totalResponseTime = 0.0
        var completedCount = 0
        
        for (cloudlet in cloudletList) {
            if (cloudlet.status == org.cloudsimplus.cloudlets.Cloudlet.Status.SUCCESS) {
                val finishTime = cloudlet.finishTime
                if (finishTime > makespan) {
                    makespan = finishTime
                }
                
                val vmId = cloudlet.vm.id.toInt()
                val actualCPUTime = cloudlet.totalExecutionTime
                executeTimeOfVM[vmId] += actualCPUTime
                
                // 计算成本
                val vm = cloudlet.vm
                val costPerSec = when {
                    vm.mips == Constants.L_MIPS.toDouble() -> Constants.L_PRICE
                    vm.mips == Constants.M_MIPS.toDouble() -> Constants.M_PRICE
                    vm.mips == Constants.H_MIPS.toDouble() -> Constants.H_PRICE
                    else -> Constants.L_PRICE
                }
                cost += actualCPUTime * costPerSec
                
                // 计算等待时间和响应时间
                val arrivalTime = cloudlet.submissionDelay
                val startTime = cloudlet.startTime
                val waitingTime = if (startTime > 0) startTime - arrivalTime else 0.0
                val responseTime = finishTime - arrivalTime
                
                totalWaitingTime += waitingTime
                totalResponseTime += responseTime
                completedCount++
            }
        }
        
        // 计算负载均衡度
        val avgExecuteTime = executeTimeOfVM.average()
        var LB = 0.0
        for (i in 0 until vmNum) {
            LB += Math.pow(executeTimeOfVM[i] - avgExecuteTime, 2.0)
        }
        LB = Math.sqrt(LB / vmNum)
        
        val avgWaitingTime = if (completedCount > 0) totalWaitingTime / completedCount else 0.0
        val avgResponseTime = if (completedCount > 0) totalResponseTime / completedCount else 0.0
        
        return Quintuple(makespan, LB, cost, avgWaitingTime, avgResponseTime)
    }
    
    /**
     * 运行所有实时调度算法并对比
     */
    fun runComparison(): List<RealtimeAlgorithmResult> {
        Logger.info("\n${"=".repeat(60)}")
        Logger.info("开始实时调度算法对比实验")
        Logger.info("任务数量: {}", cloudletCount)
        Logger.info("仿真持续时间: {} 秒", simulationDuration)
        Logger.info("到达率: {} 任务/秒", arrivalRate)
        Logger.info("运行次数: {}", runs)
        Logger.info("随机数种子: {}", randomSeed)
        Logger.info("${"=".repeat(60)}")
        
        val results = mutableListOf<RealtimeAlgorithmResult>()
        
        // 确定要运行的算法列表（如果配置为空，则运行所有算法）
        val algorithmsToRun = if (algorithms.isEmpty()) {
            config.RealtimeAlgorithmType.entries
        } else {
            algorithms
        }
        
        Logger.info("将运行 {} 个算法: {}", algorithmsToRun.size, algorithmsToRun.joinToString(", ") { it.name })
        
        // 如果运行次数大于1，进行多次运行并计算统计值
        if (runs > 1) {
            val statistics = mutableListOf<RealtimeAlgorithmStatistics>()
            
            // 对每个算法进行多次运行
            for (algorithmType in algorithmsToRun) {
                val algorithmName = when (algorithmType) {
                    config.RealtimeAlgorithmType.MIN_LOAD -> "MinLoad"
                    config.RealtimeAlgorithmType.RANDOM -> "Random"
                    config.RealtimeAlgorithmType.PSO_REALTIME -> "PSO-Realtime"
                    config.RealtimeAlgorithmType.WOA_REALTIME -> "WOA-Realtime"
                }
                
                Logger.info("\n运行算法 {} ({} 次)", algorithmName, runs)
                
                // 收集多次运行的结果
                val runResults = mutableListOf<RealtimeAlgorithmResult>()
                for (run in 1..runs) {
                    Logger.info("  第 {}/{} 次运行...", run, runs)
                    val result = when (algorithmType) {
                        config.RealtimeAlgorithmType.MIN_LOAD -> {
                            runRealtimeAlgorithm("MinLoad") { vms ->
                                RealtimeMinLoadScheduler(vms)
                            }
                        }
                        config.RealtimeAlgorithmType.RANDOM -> {
                            runRealtimeAlgorithm("Random") { vms ->
                                RealtimeRandomScheduler(vms, Random(randomSeed + run))
                            }
                        }
                        config.RealtimeAlgorithmType.PSO_REALTIME -> {
                            runRealtimeAlgorithm("PSO-Realtime") { vms ->
                                RealtimePSOScheduler(vms, population, maxIter, Random(randomSeed + run))
                            }
                        }
                        config.RealtimeAlgorithmType.WOA_REALTIME -> {
                            runRealtimeAlgorithm("WOA-Realtime") { vms ->
                                RealtimeWOAScheduler(vms, population, maxIter, Random(randomSeed + run))
                            }
                        }
                    }
                    runResults.add(result)
                }
                
                // 计算统计值
                val stats = calculateRealtimeStatistics(algorithmName, runResults)
                statistics.add(stats)
                
                // 使用平均值作为最终结果
                results.add(RealtimeAlgorithmResult(
                    algorithmName = algorithmName,
                    makespan = stats.makespan.mean,
                    loadBalance = stats.loadBalance.mean,
                    cost = stats.cost.mean,
                    totalTime = stats.totalTime.mean,
                    fitness = stats.fitness.mean,
                    averageWaitingTime = stats.averageWaitingTime.mean,
                    averageResponseTime = stats.averageResponseTime.mean
                ))
            }
            
            // 打印统计结果
            printRealtimeStatisticsResults(statistics)
        } else {
            // 单次运行（原有逻辑）
            for (algorithmType in algorithmsToRun) {
                when (algorithmType) {
                    config.RealtimeAlgorithmType.MIN_LOAD -> {
                        results.add(runRealtimeAlgorithm("MinLoad") { vms ->
                            RealtimeMinLoadScheduler(vms)
                        })
                    }
                    config.RealtimeAlgorithmType.RANDOM -> {
                        results.add(runRealtimeAlgorithm("Random") { vms ->
                            RealtimeRandomScheduler(vms, random)
                        })
                    }
                    config.RealtimeAlgorithmType.PSO_REALTIME -> {
                        results.add(runRealtimeAlgorithm("PSO-Realtime") { vms ->
                            RealtimePSOScheduler(vms, population, maxIter, random)
                        })
                    }
                    config.RealtimeAlgorithmType.WOA_REALTIME -> {
                        results.add(runRealtimeAlgorithm("WOA-Realtime") { vms ->
                            RealtimeWOAScheduler(vms, population, maxIter, random)
                        })
                    }
                }
            }
        }
        
        // 打印对比结果
        printRealtimeComparisonResults(results)
        
        return results
    }
    
    /**
     * 运行所有实时调度算法并对比，返回统计结果（平均值和标准差）
     */
    fun runComparisonWithStatistics(): List<RealtimeAlgorithmStatistics> {
        Logger.info("\n${"=".repeat(60)}")
        Logger.info("开始实时调度算法对比实验 ({} 次运行)", runs)
        Logger.info("任务数量: {}", cloudletCount)
        Logger.info("仿真持续时间: {} 秒", simulationDuration)
        Logger.info("到达率: {} 任务/秒", arrivalRate)
        Logger.info("初始随机数种子: {}", randomSeed)
        Logger.info("${"=".repeat(60)}")

        val algorithmsToRun = if (algorithms.isEmpty()) {
            config.RealtimeAlgorithmType.entries
        } else {
            algorithms
        }

        Logger.info("将运行 {} 个算法: {}", algorithmsToRun.size, algorithmsToRun.joinToString(", ") { it.name })

        val statistics = mutableListOf<RealtimeAlgorithmStatistics>()

        // 对每个算法进行多次运行
        for (algorithmType in algorithmsToRun) {
            val algorithmName = when (algorithmType) {
                config.RealtimeAlgorithmType.MIN_LOAD -> "MinLoad"
                config.RealtimeAlgorithmType.RANDOM -> "Random"
                config.RealtimeAlgorithmType.PSO_REALTIME -> "PSO-Realtime"
                config.RealtimeAlgorithmType.WOA_REALTIME -> "WOA-Realtime"
            }

            Logger.info("\n--- 算法: {} ---", algorithmName)
            val runResults = mutableListOf<RealtimeAlgorithmResult>()

            for (i in 0 until runs) {
                Logger.info("  运行第 {}/{} 次...", i + 1, runs)
                // 每次运行使用不同的随机种子
                val currentRandomSeed = randomSeed + i
                val currentRandom = Random(currentRandomSeed)

                val result = when (algorithmType) {
                    config.RealtimeAlgorithmType.MIN_LOAD -> {
                        runRealtimeAlgorithm("MinLoad") { vms ->
                            RealtimeMinLoadScheduler(vms)
                        }
                    }
                    config.RealtimeAlgorithmType.RANDOM -> {
                        runRealtimeAlgorithm("Random") { vms ->
                            RealtimeRandomScheduler(vms, currentRandom)
                        }
                    }
                    config.RealtimeAlgorithmType.PSO_REALTIME -> {
                        runRealtimeAlgorithm("PSO-Realtime") { vms ->
                            RealtimePSOScheduler(vms, population, maxIter, currentRandom)
                        }
                    }
                    config.RealtimeAlgorithmType.WOA_REALTIME -> {
                        runRealtimeAlgorithm("WOA-Realtime") { vms ->
                            RealtimeWOAScheduler(vms, population, maxIter, currentRandom)
                        }
                    }
                }
                runResults.add(result)
            }

            // 计算统计值
            val stats = calculateRealtimeStatistics(algorithmName, runResults)
            statistics.add(stats)
        }

        printRealtimeStatisticsResults(statistics)
        exportStatisticsToCSV(statistics)

        return statistics
    }
    
    /**
     * 导出统计结果到 CSV 文件
     */
    private fun exportStatisticsToCSV(statistics: List<RealtimeAlgorithmStatistics>) {
        val csvFile = ResultsManager.generateRealtimeResultFileName()
        csvFile.bufferedWriter().use { writer ->
            // 写入表头
            writer.write("Algorithm,Makespan_Mean,Makespan_StdDev,LoadBalance_Mean,LoadBalance_StdDev," +
                    "Cost_Mean,Cost_StdDev,TotalTime_Mean,TotalTime_StdDev,Fitness_Mean,Fitness_StdDev," +
                    "AvgWaitingTime_Mean,AvgWaitingTime_StdDev,AvgResponseTime_Mean,AvgResponseTime_StdDev,Runs\n")

            // 写入数据
            for (stat in statistics) {
                writer.write("${stat.algorithmName}," +
                        "${stat.makespan.mean},${stat.makespan.stdDev}," +
                        "${stat.loadBalance.mean},${stat.loadBalance.stdDev}," +
                        "${stat.cost.mean},${stat.cost.stdDev}," +
                        "${stat.totalTime.mean},${stat.totalTime.stdDev}," +
                        "${stat.fitness.mean},${stat.fitness.stdDev}," +
                        "${stat.averageWaitingTime.mean},${stat.averageWaitingTime.stdDev}," +
                        "${stat.averageResponseTime.mean},${stat.averageResponseTime.stdDev}," +
                        "${runs}\n")
            }
        }
        Logger.info("结果已导出到: {}", csvFile.absolutePath)
        Logger.info("注: 导出值为 {} 次运行的平均值和标准差", runs)
    }

    /**
     * 计算多次运行的统计值
     */
    private fun calculateRealtimeStatistics(
        algorithmName: String,
        results: List<RealtimeAlgorithmResult>
    ): RealtimeAlgorithmStatistics {
        val makespanStats = DescriptiveStatistics()
        val loadBalanceStats = DescriptiveStatistics()
        val costStats = DescriptiveStatistics()
        val totalTimeStats = DescriptiveStatistics()
        val fitnessStats = DescriptiveStatistics()
        val avgWaitingTimeStats = DescriptiveStatistics()
        val avgResponseTimeStats = DescriptiveStatistics()
        
        for (result in results) {
            makespanStats.addValue(result.makespan)
            loadBalanceStats.addValue(result.loadBalance)
            costStats.addValue(result.cost)
            totalTimeStats.addValue(result.totalTime)
            fitnessStats.addValue(result.fitness)
            avgWaitingTimeStats.addValue(result.averageWaitingTime)
            avgResponseTimeStats.addValue(result.averageResponseTime)
        }
        
        return RealtimeAlgorithmStatistics(
            algorithmName = algorithmName,
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
            ),
            averageWaitingTime = StatisticalValue(
                mean = avgWaitingTimeStats.mean,
                stdDev = avgWaitingTimeStats.standardDeviation,
                min = avgWaitingTimeStats.min,
                max = avgWaitingTimeStats.max
            ),
            averageResponseTime = StatisticalValue(
                mean = avgResponseTimeStats.mean,
                stdDev = avgResponseTimeStats.standardDeviation,
                min = avgResponseTimeStats.min,
                max = avgResponseTimeStats.max
            )
        )
    }
    
    /**
     * 打印统计结果（多次运行的平均值和标准差）
     */
    private fun printRealtimeStatisticsResults(statistics: List<RealtimeAlgorithmStatistics>) {
        Logger.result("\n${"=".repeat(120)}")
        Logger.result("实时调度算法统计结果（{} 次运行的平均值 ± 标准差）", runs)
        Logger.result("${"=".repeat(120)}")
        Logger.result(String.format("%-15s %-18s %-18s %-18s %-18s %-18s %-18s %-18s",
            "算法", "Makespan", "Load Balance", "Cost", "Total Time", "Fitness", "Avg Wait Time", "Avg Resp Time"))
        Logger.result("-".repeat(120))
        
        for (stat in statistics) {
            Logger.result(String.format("%-15s %-18s %-18s %-18s %-18s %-18s %-18s %-18s",
                stat.algorithmName,
                stat.makespan.toString(),
                stat.loadBalance.toString(),
                stat.cost.toString(),
                stat.totalTime.toString(),
                stat.fitness.toString(),
                stat.averageWaitingTime.toString(),
                stat.averageResponseTime.toString()))
        }
        
        Logger.result("-".repeat(120))
        
        // 找出最优值（基于平均值）
        val bestMakespan = statistics.minByOrNull { it.makespan.mean }
        val bestLB = statistics.minByOrNull { it.loadBalance.mean }
        val bestCost = statistics.minByOrNull { it.cost.mean }
        val bestFitness = statistics.minByOrNull { it.fitness.mean }
        val bestWaitTime = statistics.minByOrNull { it.averageWaitingTime.mean }
        val bestRespTime = statistics.minByOrNull { it.averageResponseTime.mean }
        
        Logger.result("\n最优值（基于平均值）:")
        bestMakespan?.let { 
            Logger.result("  最小 Makespan: {} ({})", it.algorithmName, it.makespan.toString())
        }
        bestLB?.let { 
            Logger.result("  最小 Load Balance: {} ({})", it.algorithmName, it.loadBalance.toString())
        }
        bestCost?.let { 
            Logger.result("  最小 Cost: {} ({})", it.algorithmName, it.cost.toString())
        }
        bestFitness?.let { 
            Logger.result("  最小 Fitness: {} ({})", it.algorithmName, it.fitness.toString())
        }
        bestWaitTime?.let { 
            Logger.result("  最小平均等待时间: {} ({})", it.algorithmName, it.averageWaitingTime.toString())
        }
        bestRespTime?.let { 
            Logger.result("  最小平均响应时间: {} ({})", it.algorithmName, it.averageResponseTime.toString())
        }
        Logger.result("${"=".repeat(120)}\n")
    }
    
    /**
     * 打印实时调度对比结果
     */
    private fun printRealtimeComparisonResults(results: List<RealtimeAlgorithmResult>) {
        Logger.result("\n${"=".repeat(90)}")
        Logger.result("实时调度算法对比结果汇总")
        Logger.result("${"=".repeat(90)}")
        Logger.result(String.format("%-15s %-12s %-15s %-12s %-12s %-15s %-15s",
            "算法", "Makespan", "Load Balance", "Cost", "Avg Wait", "Avg Response", "Fitness"))
        Logger.result("-".repeat(90))
        
        for (result in results) {
            Logger.result(String.format("%-15s %-12s %-15s %-12s %-12s %-15s %-15s",
                result.algorithmName,
                dft.format(result.makespan),
                dft.format(result.loadBalance),
                dft.format(result.cost),
                dft.format(result.averageWaitingTime),
                dft.format(result.averageResponseTime),
                dft.format(result.fitness)))
        }
        
        Logger.result("-".repeat(90))
        
        // 找出最优值
        val bestMakespan = results.minByOrNull { it.makespan }
        val bestLB = results.minByOrNull { it.loadBalance }
        val bestCost = results.minByOrNull { it.cost }
        val bestWaitTime = results.minByOrNull { it.averageWaitingTime }
        val bestResponseTime = results.minByOrNull { it.averageResponseTime }
        val bestFitness = results.minByOrNull { it.fitness }
        
        Logger.result("\n最优值:")
        Logger.result("  最小 Makespan: {} ({})", bestMakespan?.algorithmName, dft.format(bestMakespan?.makespan))
        Logger.result("  最小 Load Balance: {} ({})", bestLB?.algorithmName, dft.format(bestLB?.loadBalance))
        Logger.result("  最小 Cost: {} ({})", bestCost?.algorithmName, dft.format(bestCost?.cost))
        Logger.result("  最小平均等待时间: {} ({})", bestWaitTime?.algorithmName, dft.format(bestWaitTime?.averageWaitingTime))
        Logger.result("  最小平均响应时间: {} ({})", bestResponseTime?.algorithmName, dft.format(bestResponseTime?.averageResponseTime))
        Logger.result("  最小 Fitness: {} ({})", bestFitness?.algorithmName, dft.format(bestFitness?.fitness))
        Logger.result("${"=".repeat(90)}\n")
        
        // 导出 CSV 文件
        exportRealtimeToCSV(results)
    }
    
    /**
     * 导出实时调度结果到 CSV 文件
     */
    private fun exportRealtimeToCSV(results: List<RealtimeAlgorithmResult>) {
        val csvFile = ResultsManager.generateRealtimeResultFileName()
        csvFile.bufferedWriter().use { writer ->
            // 写入表头
            if (runs > 1) {
                writer.write("Algorithm,Makespan_Mean,Makespan_StdDev,LoadBalance_Mean,LoadBalance_StdDev," +
                        "Cost_Mean,Cost_StdDev,TotalTime_Mean,TotalTime_StdDev,Fitness_Mean,Fitness_StdDev," +
                        "AvgWaitingTime_Mean,AvgWaitingTime_StdDev,AvgResponseTime_Mean,AvgResponseTime_StdDev,Runs\n")
            } else {
                writer.write("Algorithm,Makespan,LoadBalance,Cost,TotalTime,Fitness,AvgWaitingTime,AvgResponseTime\n")
            }
            
            // 写入数据
            for (result in results) {
                if (runs > 1) {
                    // 多次运行：导出平均值和标准差
                    writer.write("${result.algorithmName}," +
                            "${result.makespan},0.0," +
                            "${result.loadBalance},0.0," +
                            "${result.cost},0.0," +
                            "${result.totalTime},0.0," +
                            "${result.fitness},0.0," +
                            "${result.averageWaitingTime},0.0," +
                            "${result.averageResponseTime},0.0," +
                            "${runs}\n")
                } else {
                    // 单次运行：导出原始值
                    writer.write("${result.algorithmName}," +
                            "${result.makespan}," +
                            "${result.loadBalance}," +
                            "${result.cost}," +
                            "${result.totalTime}," +
                            "${result.fitness}," +
                            "${result.averageWaitingTime}," +
                            "${result.averageResponseTime}\n")
                }
            }
        }
        Logger.info("实时调度结果已导出到: {}", csvFile.absolutePath)
        if (runs > 1) {
            Logger.info("注: 导出值为 {} 次运行的平均值", runs)
        }
    }
    
}

/**
 * 五元组类
 */
private data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

