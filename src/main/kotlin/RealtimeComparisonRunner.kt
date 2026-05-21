package datacenter

import broker.RealtimeBroker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import org.cloudsimplus.core.CloudSimPlus
import scheduler.RealtimeScheduler
import scheduler.ResolvedAlgorithm
import util.ExperimentConcurrency
import util.ExperimentOutputContext
import util.Logger
import util.StatisticalValue
import util.mapCloudletsToVmIds
import java.text.DecimalFormat
import java.util.Random
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

data class RealtimeAlgorithmResult(
    val algorithmName: String,
    val makespan: Double,
    val loadBalance: Double,
    val cost: Double,
    val totalTime: Double,
    val fitness: Double,
    val averageWaitingTime: Double,
    val averageResponseTime: Double,
    val rejectedCount: Int,
    val timeoutCount: Int,
    val failedCount: Int,
    val retryCount: Int,
    val permanentFailedCount: Int,
    val averageDecisionDelay: Double,
    val completedCount: Int,
    val submittedCount: Int,
    val slaViolationCount: Int,
    val slaViolationRate: Double,
    val capacityRejectedCount: Int,
    val averageQueueDepth: Double,
    val maxQueueDepth: Int,
    val p95ResponseTime: Double
)

data class RealtimeAlgorithmStatistics(
    val algorithmName: String,
    val makespan: StatisticalValue,
    val loadBalance: StatisticalValue,
    val cost: StatisticalValue,
    val totalTime: StatisticalValue,
    val fitness: StatisticalValue,
    val averageWaitingTime: StatisticalValue,
    val averageResponseTime: StatisticalValue,
    val rejectedCount: StatisticalValue,
    val timeoutCount: StatisticalValue,
    val failedCount: StatisticalValue,
    val retryCount: StatisticalValue,
    val permanentFailedCount: StatisticalValue,
    val averageDecisionDelay: StatisticalValue,
    val completedCount: StatisticalValue,
    val submittedCount: StatisticalValue,
    val slaViolationCount: StatisticalValue,
    val slaViolationRate: StatisticalValue,
    val capacityRejectedCount: StatisticalValue,
    val averageQueueDepth: StatisticalValue,
    val maxQueueDepth: StatisticalValue,
    val p95ResponseTime: StatisticalValue
)

private data class RealtimeRunSummary(
    val average: RealtimeAlgorithmResult,
    val statistics: RealtimeAlgorithmStatistics?,
    val runResults: List<RealtimeAlgorithmResult>
)

class RealtimeComparisonRunner(
    private val cloudletCount: Int = 100,
    private val simulationDuration: Double = 1000.0,
    private val arrivalRate: Double = 10.0,
    private val population: Int = 20,
    private val maxIter: Int = 20,
    private val randomSeed: Long = 0L,
    private val runs: Int = 1,
    private val generatorType: config.CloudletGeneratorType = config.CloudletGenConfig.GENERATOR_TYPE,
    private val googleTraceConfig: config.GoogleTraceConfig? = null,
    private val objectiveWeights: config.ObjectiveWeightsConfig = config.ObjectiveWeightsConfig(),
    private val arrival: config.RealtimeArrivalConfig = config.RealtimeArrivalConfig(),
    private val scheduling: config.RealtimeSchedulingConfig = config.RealtimeSchedulingConfig(),
    private val resolvedAlgorithms: List<ResolvedAlgorithm>,
    private val experimentDir: java.io.File? = null,
    private val outputContext: ExperimentOutputContext = ExperimentOutputContext(experimentDir),
    private val useCoroutines: Boolean = true,
    private val maxConcurrency: Int = 0,
    private val concurrency: ExperimentConcurrency = ExperimentConcurrency(useCoroutines, maxConcurrency)
) {
    private val dft = DecimalFormat("###.##")

    private data class RealtimeMetrics(
        val makespan: Double,
        val loadBalance: Double,
        val cost: Double,
        val averageWaitingTime: Double,
        val averageResponseTime: Double,
        val rejectedCount: Int,
        val timeoutCount: Int,
        val failedCount: Int,
        val retryCount: Int,
        val permanentFailedCount: Int,
        val averageDecisionDelay: Double,
        val completedCount: Int,
        val submittedCount: Int,
        val slaViolationCount: Int,
        val slaViolationRate: Double,
        val capacityRejectedCount: Int,
        val averageQueueDepth: Double,
        val maxQueueDepth: Int,
        val p95ResponseTime: Double
    )

    private fun executionModeDescription(): String {
        return concurrency.description
    }

    private fun runRealtimeAlgorithm(
        algorithmName: String,
        runSeed: Long,
        schedulerFactory: (List<org.cloudsimplus.vms.Vm>) -> RealtimeScheduler
    ): RealtimeAlgorithmResult {
        Logger.info("\n${"=".repeat(60)}")
        Logger.info("运行实时调度算法: {}", algorithmName)
        Logger.info("${"=".repeat(60)}")

        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "Datacenter0", DatacenterType.LOW)
        DatacenterCreator.createDatacenter(simulation, "Datacenter1", DatacenterType.MEDIUM)
        DatacenterCreator.createDatacenter(simulation, "Datacenter2", DatacenterType.HIGH)

        val vmList = DatacenterCreator.createVms()
        val scheduler = schedulerFactory(vmList)
        val broker = RealtimeBroker(simulation, scheduler, vmList, scheduling)
        broker.submitVmList(vmList)

        val random = Random(runSeed)
        val cloudletGenerator = RealtimeCloudletGenerator(random, arrivalRate, generatorType, arrival, googleTraceConfig)
        val cloudletList = cloudletGenerator.createRealtimeCloudlets(0, cloudletCount, simulationDuration)
        broker.submitCloudletListRealtime(cloudletList)

        Logger.info("已生成 {} 个实时任务", cloudletList.size)
        Logger.info("仿真持续时间: {} 秒", simulationDuration)
        simulation.start()

        val finishedCloudlets = broker.getCloudletFinishedList<org.cloudsimplus.cloudlets.Cloudlet>()
        val metrics = calculateRealtimeMetrics(finishedCloudlets, vmList.size, broker)

        val cloudletToVm = mapCloudletsToVmIds(cloudletList, finishedCloudlets)
        val objFunc = SchedulerObjectiveFunction(cloudletList, vmList, objectiveWeights)
        val totalTime = objFunc.estimateTotalTime(cloudletToVm)
        val fitness = objFunc.calculate(cloudletToVm)

        Logger.info("\n结果:")
        Logger.info("  最大完成时间 (Makespan): {}", dft.format(metrics.makespan))
        Logger.info("  负载均衡度 (LB): {}", dft.format(metrics.loadBalance))
        Logger.info("  总成本 (Cost): {}", dft.format(metrics.cost))
        Logger.info("  平均等待时间: {}", dft.format(metrics.averageWaitingTime))
        Logger.info("  平均响应时间: {}", dft.format(metrics.averageResponseTime))
        Logger.info(
            "  Reject/Timeout/Failed/Retry/PermanentFailed: {}/{}/{}/{}/{}",
            metrics.rejectedCount,
            metrics.timeoutCount,
            metrics.failedCount,
            metrics.retryCount,
            metrics.permanentFailedCount
        )
        Logger.info("  平均调度决策延迟: {}", dft.format(metrics.averageDecisionDelay))
        Logger.info(
            "  SLA/容量/队列: violation={} ({}), capacityRejected={}, avgQueueDepth={}, maxQueueDepth={}, p95Response={}",
            metrics.slaViolationCount,
            dft.format(metrics.slaViolationRate),
            metrics.capacityRejectedCount,
            dft.format(metrics.averageQueueDepth),
            metrics.maxQueueDepth,
            dft.format(metrics.p95ResponseTime)
        )
        Logger.info("  适应度 (Fitness): {}", dft.format(fitness))

        return RealtimeAlgorithmResult(
            algorithmName = algorithmName,
            makespan = metrics.makespan,
            loadBalance = metrics.loadBalance,
            cost = metrics.cost,
            totalTime = totalTime,
            fitness = fitness,
            averageWaitingTime = metrics.averageWaitingTime,
            averageResponseTime = metrics.averageResponseTime,
            rejectedCount = metrics.rejectedCount,
            timeoutCount = metrics.timeoutCount,
            failedCount = metrics.failedCount,
            retryCount = metrics.retryCount,
            permanentFailedCount = metrics.permanentFailedCount,
            averageDecisionDelay = metrics.averageDecisionDelay,
            completedCount = metrics.completedCount,
            submittedCount = metrics.submittedCount,
            slaViolationCount = metrics.slaViolationCount,
            slaViolationRate = metrics.slaViolationRate,
            capacityRejectedCount = metrics.capacityRejectedCount,
            averageQueueDepth = metrics.averageQueueDepth,
            maxQueueDepth = metrics.maxQueueDepth,
            p95ResponseTime = metrics.p95ResponseTime
        )
    }

    private fun calculateRealtimeMetrics(
        cloudletList: List<org.cloudsimplus.cloudlets.Cloudlet>,
        vmNum: Int,
        broker: RealtimeBroker
    ): RealtimeMetrics {
        var makespan = 0.0
        val executeTimeOfVM = DoubleArray(vmNum)
        var cost = 0.0
        var totalWaitingTime = 0.0
        var totalResponseTime = 0.0
        val responseTimes = mutableListOf<Double>()
        var completedCount = 0
        var failedCount = 0

        for (cloudlet in cloudletList) {
            when (cloudlet.status) {
                org.cloudsimplus.cloudlets.Cloudlet.Status.SUCCESS -> {
                    val finishTime = cloudlet.finishTime
                    if (finishTime > makespan) {
                        makespan = finishTime
                    }

                    val vmId = cloudlet.vm.id.toInt()
                    val actualCPUTime = cloudlet.getTotalExecutionTime()
                    executeTimeOfVM[vmId] += actualCPUTime

                    val costPerSec = when {
                        cloudlet.vm.mips == config.DatacenterConfig.L_MIPS.toDouble() -> config.DatacenterConfig.L_PRICE
                        cloudlet.vm.mips == config.DatacenterConfig.M_MIPS.toDouble() -> config.DatacenterConfig.M_PRICE
                        cloudlet.vm.mips == config.DatacenterConfig.H_MIPS.toDouble() -> config.DatacenterConfig.H_PRICE
                        else -> config.DatacenterConfig.L_PRICE
                    }
                    cost += actualCPUTime * costPerSec

                    val arrivalTime = broker.getArrivalTime(cloudlet)
                    val startTime = cloudlet.getStartTime()
                    val waitingTime = if (startTime > 0) startTime - arrivalTime else 0.0
                    val responseTime = finishTime - arrivalTime

                    totalWaitingTime += waitingTime
                    totalResponseTime += responseTime
                    responseTimes.add(responseTime)
                    completedCount++
                }
                org.cloudsimplus.cloudlets.Cloudlet.Status.FAILED -> failedCount++
                else -> Unit
            }
        }

        val avgExecuteTime = executeTimeOfVM.average()
        var lb = 0.0
        for (i in 0 until vmNum) {
            lb += Math.pow(executeTimeOfVM[i] - avgExecuteTime, 2.0)
        }
        lb = Math.sqrt(lb / vmNum)

        val avgWaitingTime = if (completedCount > 0) totalWaitingTime / completedCount else 0.0
        val avgResponseTime = if (completedCount > 0) totalResponseTime / completedCount else 0.0
        val timeoutCount = broker.getTimeoutCount(scheduling.taskTimeout)
        val slaViolationCount = broker.getSlaViolationCount(cloudletList)
        val slaViolationRate = if (completedCount > 0) {
            slaViolationCount.toDouble() / completedCount.toDouble()
        } else {
            0.0
        }

        return RealtimeMetrics(
            makespan = makespan,
            loadBalance = lb,
            cost = cost,
            averageWaitingTime = avgWaitingTime,
            averageResponseTime = avgResponseTime,
            rejectedCount = broker.getRejectedCount(),
            timeoutCount = timeoutCount,
            failedCount = failedCount,
            retryCount = broker.getRetryCount(),
            permanentFailedCount = broker.getPermanentFailedCount(),
            averageDecisionDelay = broker.getAverageDecisionDelay(),
            completedCount = completedCount,
            submittedCount = broker.getSubmittedCount(),
            slaViolationCount = slaViolationCount,
            slaViolationRate = slaViolationRate,
            capacityRejectedCount = broker.getCapacityRejectedCount(),
            averageQueueDepth = broker.getAverageQueueDepth(),
            maxQueueDepth = broker.getMaxQueueDepth(),
            p95ResponseTime = responseTimes.percentile95()
        )
    }

    suspend fun runComparison(): List<RealtimeAlgorithmResult> = coroutineScope {
        Logger.info("\n${"=".repeat(60)}")
        Logger.info("开始实时调度算法对比实验")
        Logger.info("任务数量: {}", cloudletCount)
        Logger.info("仿真持续时间: {} 秒", simulationDuration)
        Logger.info("到达率: {} 任务/秒", arrivalRate)
        Logger.info("到达分布: {}", arrival.distribution)
        Logger.info("调度策略: {}", scheduling.strategy)
        Logger.info("运行次数: {}", runs)
        Logger.info("随机数种子: {}", randomSeed)
        Logger.info("执行模式: {}", executionModeDescription())
        Logger.info("${"=".repeat(60)}")

        outputContext.saveExperimentInfo(mapOf(
            "运行模式" to "实时调度 (Realtime)",
            "任务数量" to cloudletCount,
            "仿真持续时间" to simulationDuration,
            "到达率" to arrivalRate,
            "到达分布" to arrival.distribution,
            "调度策略" to scheduling.strategy,
            "最大队列" to scheduling.maxQueueSize,
            "任务超时" to scheduling.taskTimeout,
            "资源预留" to scheduling.resourceReservation,
            "调度决策延迟" to scheduling.decisionDelay,
            "调度决策抖动" to scheduling.decisionJitter,
            "任务失败率" to scheduling.failureRate,
            "重试次数上限" to scheduling.retryLimit,
            "重试延迟" to scheduling.retryDelay,
            "重试退避倍数" to scheduling.retryBackoffMultiplier,
            "队列策略" to scheduling.queuePolicy,
            "优先级层级" to scheduling.priorityLevels,
            "高优先级比例" to scheduling.highPriorityRatio,
            "SLA deadline 系数" to scheduling.deadlineFactor,
            "单 VM 队列容量" to scheduling.vmQueueCapacity,
            "过载失败倍率" to scheduling.overloadFailureMultiplier,
            "随机数种子" to randomSeed,
            "运行次数" to runs,
            "任务生成器" to generatorType.name
        ))

        val algorithmsToRun = algorithmsToRun()
        Logger.info("将运行 {} 个算法: {}", algorithmsToRun.size, algorithmsToRun.joinToString(", ") { it.name })

        val results = mutableListOf<RealtimeAlgorithmResult>()
        val executionTime = measureTimeMillis {
            val summaries = executeRealtimeAlgorithmSummaries(algorithmsToRun).sortedBy { it.average.algorithmName }
            results.addAll(summaries.map { it.average })
            Logger.info("所有实时算法执行完成")
            printRealtimeComparisonResults(results)
            exportRealtimeToCSV(summaries)

            val summaryData = results.map { r ->
                mapOf(
                    "Algorithm" to r.algorithmName,
                    "AvgMakespan" to r.makespan,
                    "AvgLoadBalance" to r.loadBalance,
                    "AvgCost" to r.cost,
                    "AvgTotalTime" to r.totalTime,
                    "AvgFitness" to r.fitness,
                    "AvgWaitingTime" to r.averageWaitingTime,
                    "AvgResponseTime" to r.averageResponseTime,
                    "RejectedCount" to r.rejectedCount,
                    "TimeoutCount" to r.timeoutCount,
                    "FailedCount" to r.failedCount,
                    "RetryCount" to r.retryCount,
                    "PermanentFailedCount" to r.permanentFailedCount,
                    "AvgDecisionDelay" to r.averageDecisionDelay,
                    "CompletedCount" to r.completedCount,
                    "SubmittedCount" to r.submittedCount,
                    "SlaViolationCount" to r.slaViolationCount,
                    "SlaViolationRate" to r.slaViolationRate,
                    "CapacityRejectedCount" to r.capacityRejectedCount,
                    "AvgQueueDepth" to r.averageQueueDepth,
                    "MaxQueueDepth" to r.maxQueueDepth,
                    "P95ResponseTime" to r.p95ResponseTime
                )
            }
            outputContext.saveSummaryResults(
                summaryData,
                listOf(
                    "Algorithm", "AvgMakespan", "AvgLoadBalance", "AvgCost", "AvgTotalTime", "AvgFitness",
                    "AvgWaitingTime", "AvgResponseTime", "RejectedCount", "TimeoutCount", "FailedCount",
                    "RetryCount", "PermanentFailedCount", "AvgDecisionDelay", "CompletedCount", "SubmittedCount",
                    "SlaViolationCount", "SlaViolationRate", "CapacityRejectedCount", "AvgQueueDepth",
                    "MaxQueueDepth", "P95ResponseTime"
                )
            )
        }

        Logger.info("实时调度算法对比实验完成，总耗时: {}ms", executionTime)
        results
    }

    private fun algorithmsToRun(): List<ResolvedAlgorithm> {
        if (resolvedAlgorithms.isEmpty()) {
            throw IllegalArgumentException("RealtimeComparisonRunner 需要已解析的算法列表")
        }
        return resolvedAlgorithms
    }

    private suspend fun executeRealtimeAlgorithmSummaries(
        algorithmsToRun: List<ResolvedAlgorithm>
    ): List<RealtimeRunSummary> {
        return concurrency.map(algorithmsToRun) { executeRealtimeAlgorithmSummarySafely(it) }
    }

    private suspend fun executeRealtimeAlgorithmSummarySafely(
        algorithm: ResolvedAlgorithm
    ): RealtimeRunSummary {
        return try {
            Logger.debug("开始执行实时算法: {}", algorithm.name)
            val result = buildRealtimeSummary(executeRealtimeAlgorithmRuns(algorithm))
            Logger.debug("实时算法 {} 执行完成", algorithm.name)
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.error("实时算法 {} 执行失败: {}", e, algorithm.name, e.message)
            failedRealtimeSummary(algorithm.name)
        }
    }

    private fun buildRealtimeSummary(runResults: List<RealtimeAlgorithmResult>): RealtimeRunSummary {
        return RealtimeRunSummary(
            average = averageRealtimeResults(runResults),
            statistics = calculateRealtimeStatistics(runResults),
            runResults = runResults
        )
    }

    private fun failedRealtimeSummary(algorithmName: String): RealtimeRunSummary {
        val failedResult = RealtimeAlgorithmResult(
            algorithmName = algorithmName,
            makespan = Double.NaN,
            loadBalance = Double.NaN,
            cost = Double.NaN,
            totalTime = Double.NaN,
            fitness = Double.NaN,
            averageWaitingTime = Double.NaN,
            averageResponseTime = Double.NaN,
            rejectedCount = Int.MAX_VALUE,
            timeoutCount = Int.MAX_VALUE,
            failedCount = Int.MAX_VALUE,
            retryCount = Int.MAX_VALUE,
            permanentFailedCount = Int.MAX_VALUE,
            averageDecisionDelay = Double.NaN,
            completedCount = 0,
            submittedCount = 0,
            slaViolationCount = Int.MAX_VALUE,
            slaViolationRate = Double.NaN,
            capacityRejectedCount = Int.MAX_VALUE,
            averageQueueDepth = Double.NaN,
            maxQueueDepth = Int.MAX_VALUE,
            p95ResponseTime = Double.NaN
        )
        return RealtimeRunSummary(
            average = failedResult,
            statistics = null,
            runResults = emptyList()
        )
    }

    private suspend fun executeRealtimeAlgorithmRuns(algorithm: ResolvedAlgorithm): List<RealtimeAlgorithmResult> {
        return concurrency.map(1..runs) { run ->
            executeRealtimeSingleRunAsync(algorithm, run)
        }
    }

    private fun averageRealtimeResults(runResults: List<RealtimeAlgorithmResult>): RealtimeAlgorithmResult {
        return RealtimeAlgorithmResult(
            algorithmName = runResults[0].algorithmName,
            makespan = runResults.map { it.makespan }.average(),
            loadBalance = runResults.map { it.loadBalance }.average(),
            cost = runResults.map { it.cost }.average(),
            totalTime = runResults.map { it.totalTime }.average(),
            fitness = runResults.map { it.fitness }.average(),
            averageWaitingTime = runResults.map { it.averageWaitingTime }.average(),
            averageResponseTime = runResults.map { it.averageResponseTime }.average(),
            rejectedCount = runResults.map { it.rejectedCount }.average().roundToInt(),
            timeoutCount = runResults.map { it.timeoutCount }.average().roundToInt(),
            failedCount = runResults.map { it.failedCount }.average().roundToInt(),
            retryCount = runResults.map { it.retryCount }.average().roundToInt(),
            permanentFailedCount = runResults.map { it.permanentFailedCount }.average().roundToInt(),
            averageDecisionDelay = runResults.map { it.averageDecisionDelay }.average(),
            completedCount = runResults.map { it.completedCount }.average().roundToInt(),
            submittedCount = runResults.map { it.submittedCount }.average().roundToInt(),
            slaViolationCount = runResults.map { it.slaViolationCount }.average().roundToInt(),
            slaViolationRate = runResults.map { it.slaViolationRate }.average(),
            capacityRejectedCount = runResults.map { it.capacityRejectedCount }.average().roundToInt(),
            averageQueueDepth = runResults.map { it.averageQueueDepth }.average(),
            maxQueueDepth = runResults.map { it.maxQueueDepth }.average().roundToInt(),
            p95ResponseTime = runResults.map { it.p95ResponseTime }.average()
        )
    }

    private suspend fun executeRealtimeSingleRunAsync(
        algorithm: ResolvedAlgorithm,
        run: Int
    ): RealtimeAlgorithmResult = concurrency.run {
        val runSeed = randomSeed + run
        val result = runRealtimeAlgorithm(algorithm.displayName, runSeed) { vms ->
            algorithm.definition.realtimeFactory!!.invoke(vms, objectiveWeights, algorithm.settings, runSeed)
        }

        outputContext.saveAlgorithmTrialResult(
            result.algorithmName,
            run,
            mapOf(
                "Makespan" to result.makespan,
                "LoadBalance" to result.loadBalance,
                "Cost" to result.cost,
                "TotalTime" to result.totalTime,
                "Fitness" to result.fitness,
                "WaitingTime" to result.averageWaitingTime,
                "ResponseTime" to result.averageResponseTime,
                "RejectedCount" to result.rejectedCount.toDouble(),
                "TimeoutCount" to result.timeoutCount.toDouble(),
                "FailedCount" to result.failedCount.toDouble(),
                "RetryCount" to result.retryCount.toDouble(),
                "PermanentFailedCount" to result.permanentFailedCount.toDouble(),
                "AvgDecisionDelay" to result.averageDecisionDelay,
                "CompletedCount" to result.completedCount.toDouble(),
                "SubmittedCount" to result.submittedCount.toDouble(),
                "SlaViolationCount" to result.slaViolationCount.toDouble(),
                "SlaViolationRate" to result.slaViolationRate,
                "CapacityRejectedCount" to result.capacityRejectedCount.toDouble(),
                "AvgQueueDepth" to result.averageQueueDepth,
                "MaxQueueDepth" to result.maxQueueDepth.toDouble(),
                "P95ResponseTime" to result.p95ResponseTime
            )
        )
        result
    }

    suspend fun runComparisonWithStatistics(): List<RealtimeAlgorithmStatistics> {
        Logger.info("\n${"=".repeat(60)}")
        Logger.info("开始实时调度算法对比实验 ({} 次运行)", runs)
        Logger.info("任务数量: {}", cloudletCount)
        Logger.info("仿真持续时间: {} 秒", simulationDuration)
        Logger.info("到达率: {} 任务/秒", arrivalRate)
        Logger.info("到达分布: {}", arrival.distribution)
        Logger.info("调度策略: {}", scheduling.strategy)
        Logger.info("初始随机数种子: {}", randomSeed)
        Logger.info("执行模式: {}", executionModeDescription())
        Logger.info("${"=".repeat(60)}")

        val algorithmsToRun = algorithmsToRun()
        Logger.info("将运行 {} 个算法: {}", algorithmsToRun.size, algorithmsToRun.joinToString(", ") { it.name })

        return executeRealtimeAlgorithmSummaries(algorithmsToRun)
            .sortedBy { it.average.algorithmName }
            .mapNotNull { it.statistics }
    }

    private fun calculateRealtimeStatistics(
        results: List<RealtimeAlgorithmResult>
    ): RealtimeAlgorithmStatistics {
        fun stats(values: List<Double>): StatisticalValue {
            return StatisticalValue.fromArray(values.toDoubleArray())
        }

        return RealtimeAlgorithmStatistics(
            algorithmName = results[0].algorithmName,
            makespan = stats(results.map { it.makespan }),
            loadBalance = stats(results.map { it.loadBalance }),
            cost = stats(results.map { it.cost }),
            totalTime = stats(results.map { it.totalTime }),
            fitness = stats(results.map { it.fitness }),
            averageWaitingTime = stats(results.map { it.averageWaitingTime }),
            averageResponseTime = stats(results.map { it.averageResponseTime }),
            rejectedCount = stats(results.map { it.rejectedCount.toDouble() }),
            timeoutCount = stats(results.map { it.timeoutCount.toDouble() }),
            failedCount = stats(results.map { it.failedCount.toDouble() }),
            retryCount = stats(results.map { it.retryCount.toDouble() }),
            permanentFailedCount = stats(results.map { it.permanentFailedCount.toDouble() }),
            averageDecisionDelay = stats(results.map { it.averageDecisionDelay }),
            completedCount = stats(results.map { it.completedCount.toDouble() }),
            submittedCount = stats(results.map { it.submittedCount.toDouble() }),
            slaViolationCount = stats(results.map { it.slaViolationCount.toDouble() }),
            slaViolationRate = stats(results.map { it.slaViolationRate }),
            capacityRejectedCount = stats(results.map { it.capacityRejectedCount.toDouble() }),
            averageQueueDepth = stats(results.map { it.averageQueueDepth }),
            maxQueueDepth = stats(results.map { it.maxQueueDepth.toDouble() }),
            p95ResponseTime = stats(results.map { it.p95ResponseTime })
        )
    }

    private fun printRealtimeComparisonResults(results: List<RealtimeAlgorithmResult>) {
        Logger.result("\n${"=".repeat(100)}")
        Logger.result("实时调度算法对比结果汇总")
        Logger.result("${"=".repeat(100)}")
        Logger.result(String.format("%-15s %-12s %-12s %-12s %-12s %-12s %-8s %-8s %-8s",
            "算法", "Makespan", "LB", "Cost", "AvgWait", "Fitness", "Reject", "Timeout", "Failed"))
        Logger.result("-".repeat(100))

        for (result in results) {
            Logger.result(String.format("%-15s %-12s %-12s %-12s %-12s %-12s %-8d %-8d %-8d",
                result.algorithmName,
                dft.format(result.makespan),
                dft.format(result.loadBalance),
                dft.format(result.cost),
                dft.format(result.averageWaitingTime),
                dft.format(result.fitness),
                result.rejectedCount,
                result.timeoutCount,
                result.failedCount))
        }
        Logger.result("${"=".repeat(100)}")
    }

    private fun exportRealtimeToCSV(summaries: List<RealtimeRunSummary>) {
        if (!outputContext.csvEnabled) {
            Logger.info("CSV 输出已禁用，跳过实时结果导出")
            return
        }
        val csvFile = outputContext.generateResultFileName("realtime_comparison")
        csvFile.bufferedWriter().use { writer ->
            val headers = if (runs > 1) {
                listOf(
                    "Algorithm", "Makespan_Mean", "Makespan_StdDev", "LoadBalance_Mean", "LoadBalance_StdDev",
                    "Cost_Mean", "Cost_StdDev", "TotalTime_Mean", "TotalTime_StdDev", "Fitness_Mean", "Fitness_StdDev",
                    "AvgWaitingTime_Mean", "AvgWaitingTime_StdDev", "AvgResponseTime_Mean", "AvgResponseTime_StdDev",
                    "RejectedCount_Mean", "RejectedCount_StdDev", "TimeoutCount_Mean", "TimeoutCount_StdDev",
                    "FailedCount_Mean", "FailedCount_StdDev", "RetryCount_Mean", "RetryCount_StdDev",
                    "PermanentFailedCount_Mean", "PermanentFailedCount_StdDev",
                    "AvgDecisionDelay_Mean", "AvgDecisionDelay_StdDev",
                    "CompletedCount_Mean", "CompletedCount_StdDev",
                    "SubmittedCount_Mean", "SubmittedCount_StdDev",
                    "SlaViolationCount_Mean", "SlaViolationCount_StdDev",
                    "SlaViolationRate_Mean", "SlaViolationRate_StdDev",
                    "CapacityRejectedCount_Mean", "CapacityRejectedCount_StdDev",
                    "AvgQueueDepth_Mean", "AvgQueueDepth_StdDev",
                    "MaxQueueDepth_Mean", "MaxQueueDepth_StdDev",
                    "P95ResponseTime_Mean", "P95ResponseTime_StdDev",
                    "Runs"
                )
            } else {
                listOf(
                    "Algorithm", "Makespan", "LoadBalance", "Cost", "TotalTime", "Fitness",
                    "AvgWaitingTime", "AvgResponseTime", "RejectedCount", "TimeoutCount", "FailedCount",
                    "RetryCount", "PermanentFailedCount", "AvgDecisionDelay", "CompletedCount", "SubmittedCount",
                    "SlaViolationCount", "SlaViolationRate", "CapacityRejectedCount", "AvgQueueDepth",
                    "MaxQueueDepth", "P95ResponseTime"
                )
            }
            writer.write(outputContext.csvLine(headers) + "\n")

            for (summary in summaries) {
                val result = summary.average
                val stat = summary.statistics
                val row = if (runs > 1) {
                    if (stat != null) {
                        listOf(
                            stat.algorithmName,
                            stat.makespan.mean, stat.makespan.stdDev,
                            stat.loadBalance.mean, stat.loadBalance.stdDev,
                            stat.cost.mean, stat.cost.stdDev,
                            stat.totalTime.mean, stat.totalTime.stdDev,
                            stat.fitness.mean, stat.fitness.stdDev,
                            stat.averageWaitingTime.mean, stat.averageWaitingTime.stdDev,
                            stat.averageResponseTime.mean, stat.averageResponseTime.stdDev,
                            stat.rejectedCount.mean, stat.rejectedCount.stdDev,
                            stat.timeoutCount.mean, stat.timeoutCount.stdDev,
                            stat.failedCount.mean, stat.failedCount.stdDev,
                            stat.retryCount.mean, stat.retryCount.stdDev,
                            stat.permanentFailedCount.mean, stat.permanentFailedCount.stdDev,
                            stat.averageDecisionDelay.mean, stat.averageDecisionDelay.stdDev,
                            stat.completedCount.mean, stat.completedCount.stdDev,
                            stat.submittedCount.mean, stat.submittedCount.stdDev,
                            stat.slaViolationCount.mean, stat.slaViolationCount.stdDev,
                            stat.slaViolationRate.mean, stat.slaViolationRate.stdDev,
                            stat.capacityRejectedCount.mean, stat.capacityRejectedCount.stdDev,
                            stat.averageQueueDepth.mean, stat.averageQueueDepth.stdDev,
                            stat.maxQueueDepth.mean, stat.maxQueueDepth.stdDev,
                            stat.p95ResponseTime.mean, stat.p95ResponseTime.stdDev,
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
                            result.averageWaitingTime, Double.NaN,
                            result.averageResponseTime, Double.NaN,
                            result.rejectedCount, Double.NaN,
                            result.timeoutCount, Double.NaN,
                            result.failedCount, Double.NaN,
                            result.retryCount, Double.NaN,
                            result.permanentFailedCount, Double.NaN,
                            result.averageDecisionDelay, Double.NaN,
                            result.completedCount, Double.NaN,
                            result.submittedCount, Double.NaN,
                            result.slaViolationCount, Double.NaN,
                            result.slaViolationRate, Double.NaN,
                            result.capacityRejectedCount, Double.NaN,
                            result.averageQueueDepth, Double.NaN,
                            result.maxQueueDepth, Double.NaN,
                            result.p95ResponseTime, Double.NaN,
                            0
                        )
                    }
                } else {
                    listOf(
                        result.algorithmName,
                        result.makespan,
                        result.loadBalance,
                        result.cost,
                        result.totalTime,
                        result.fitness,
                        result.averageWaitingTime,
                        result.averageResponseTime,
                        result.rejectedCount,
                        result.timeoutCount,
                        result.failedCount,
                        result.retryCount,
                        result.permanentFailedCount,
                        result.averageDecisionDelay,
                        result.completedCount,
                        result.submittedCount,
                        result.slaViolationCount,
                        result.slaViolationRate,
                        result.capacityRejectedCount,
                        result.averageQueueDepth,
                        result.maxQueueDepth,
                        result.p95ResponseTime
                    )
                }
                writer.write(outputContext.csvLine(row) + "\n")
            }
        }
        Logger.info("实时调度结果已导出到: {}", csvFile.absolutePath)
    }

    private fun List<Double>.percentile95(): Double {
        if (isEmpty()) return 0.0
        val sorted = sorted()
        val index = ((sorted.size - 1) * 0.95).roundToInt().coerceIn(sorted.indices)
        return sorted[index]
    }
}
