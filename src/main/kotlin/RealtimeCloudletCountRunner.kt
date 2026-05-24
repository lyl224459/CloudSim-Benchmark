package datacenter

import config.CloudletGeneratorType
import util.ExperimentConcurrency
import util.ExperimentOutputContext
import util.Logger
import java.text.DecimalFormat
import scheduler.ResolvedAlgorithm

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
     * 实时到达配置
     */
    private val arrival: config.RealtimeArrivalConfig = config.RealtimeArrivalConfig(),

    /**
     * 实时调度配置
     */
    private val scheduling: config.RealtimeSchedulingConfig = config.RealtimeSchedulingConfig(),

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
     * 运行批量任务数实验
     */
    suspend fun runBatchExperiment() {
        Logger.info("\n${"=".repeat(80)}")
        Logger.info("开始实时调度批量任务数实验")
        Logger.info("${"=".repeat(80)}")
        Logger.info("任务数列表: {}", cloudletCounts.joinToString(", "))
        Logger.info("每个任务数运行次数: {}", runs)
        Logger.info("仿真持续时间: {}s, 到达率: {}/s", simulationDuration, arrivalRate)
        Logger.info("种群大小: {}, 最大迭代: {}", population, maxIter)
        Logger.info("随机数种子: {}", randomSeed)
        Logger.info("${"=".repeat(80)}\n")

        outputContext.saveExperimentInfo(mapOf(
            "运行模式" to "实时调度批量任务数 (Realtime Multi)",
            "任务数列表" to cloudletCounts.joinToString(", "),
            "每个任务数运行次数" to runs,
            "仿真持续时间" to simulationDuration,
            "到达率" to arrivalRate,
            "种群大小" to population,
            "最大迭代次数" to maxIter,
            "队列策略" to scheduling.queuePolicy,
            "优先级层级" to scheduling.priorityLevels,
            "高优先级比例" to scheduling.highPriorityRatio,
            "SLA deadline 系数" to scheduling.deadlineFactor,
            "单 VM 队列容量" to scheduling.vmQueueCapacity,
            "过载失败倍率" to scheduling.overloadFailureMultiplier,
            "弹性伸缩" to scheduling.autoscalingEnabled,
            "扩容队列阈值" to scheduling.scaleOutQueueThreshold,
            "最大动态 VM 数" to scheduling.maxDynamicVms,
            "VM 冷启动延迟" to scheduling.vmColdStartDelay,
            "资源模型" to scheduling.resourceModelEnabled,
            "网络延迟" to scheduling.networkLatency,
            "镜像拉取延迟" to scheduling.imagePullDelay,
            "运行中失败率" to scheduling.runtimeFailureRate,
            "节点失败率" to scheduling.nodeFailureRate,
            "超时动作" to scheduling.timeoutAction,
            "抢占启用" to scheduling.preemptionEnabled,
            "抢占策略" to scheduling.preemptionPolicy,
            "抢占最小优先级差" to scheduling.preemptionMinPriorityGap,
            "单任务最大抢占次数" to scheduling.preemptionMaxPerTask,
            "抢占延迟" to scheduling.preemptionDelay,
            "抢占惩罚" to scheduling.preemptionPenalty,
            "多租户隔离" to scheduling.multiTenantEnabled,
            "租户数量" to scheduling.tenantCount,
            "租户配额" to scheduling.tenantQuota.joinToString(", "),
            "租户权重" to scheduling.tenantWeights.joinToString(", "),
            "租户公平策略" to scheduling.tenantFairnessPolicy,
            "租户调度策略" to scheduling.tenantSchedulingPolicy,
            "租户突发额度" to scheduling.tenantBurstAllowance,
            "租户 SLA 惩罚权重" to scheduling.tenantSlaPenaltyWeight,
            "租户成本预算" to scheduling.tenantCostBudget.joinToString(", "),
            "拓扑模型" to scheduling.topologyEnabled,
            "拓扑策略" to scheduling.topologyPolicy,
            "Region 数量" to scheduling.regionCount,
            "每 Region Rack 数" to scheduling.racksPerRegion,
            "每 Rack Host 数" to scheduling.hostsPerRack,
            "本地 Region" to scheduling.localRegion,
            "跨 Rack 延迟" to scheduling.crossRackLatency,
            "跨 Region 延迟" to scheduling.crossRegionLatency,
            "跨 Region 成本" to scheduling.crossRegionCost,
            "Host 失败率" to scheduling.hostFailureRate,
            "Rack 失败率" to scheduling.rackFailureRate,
            "Region 失败率" to scheduling.regionFailureRate,
            "物理拓扑模型" to scheduling.physicalTopologyEnabled,
            "数据本地性" to scheduling.dataLocalityEnabled,
            "镜像缓存" to scheduling.imageCacheEnabled,
            "物理每 Rack Host 数" to scheduling.hostCountPerRack,
            "Host CPU 容量" to scheduling.hostCpuCapacity,
            "Host RAM 容量" to scheduling.hostRamCapacity,
            "Host 带宽容量" to scheduling.hostBwCapacity,
            "Host I/O 容量" to scheduling.hostIoCapacity,
            "跨 Rack 带宽" to scheduling.crossRackBandwidth,
            "跨 Region 带宽" to scheduling.crossRegionBandwidth,
            "数据本地性策略" to scheduling.dataLocalityPolicy,
            "镜像缓存容量" to scheduling.imageCacheCapacity,
            "随机数种子" to randomSeed,
            "任务生成器" to generatorType.name
        ))

        val allResults = concurrency.map(cloudletCounts.withIndex().toList()) { indexed ->
            runCloudletCount(indexed.index, indexed.value)
        }.toMap()
        
        // 导出汇总结果
        exportBatchResults(allResults)
        
        Logger.info("\n${"=".repeat(80)}")
        Logger.info("实时调度批量任务数实验完成！")
        Logger.info("${"=".repeat(80)}")
    }

    private suspend fun runCloudletCount(index: Int, cloudletCount: Int): Pair<Int, List<RealtimeAlgorithmStatistics>> {
        Logger.info("\n${"#".repeat(80)}")
        Logger.info("任务数: {} ({}/{})", cloudletCount, index + 1, cloudletCounts.size)
        Logger.info("${"#".repeat(80)}")

        val childOutputContext = outputContext.child("cloudlets_$cloudletCount")
        val runner = RealtimeComparisonRunner(
            cloudletCount = cloudletCount,
            simulationDuration = simulationDuration,
            arrivalRate = arrivalRate,
            population = population,
            maxIter = maxIter,
            randomSeed = randomSeed,
            resolvedAlgorithms = resolvedAlgorithms,
            runs = runs,
            generatorType = generatorType,
            arrival = arrival,
            scheduling = scheduling,
            experimentDir = childOutputContext.experimentDir,
            outputContext = childOutputContext,
            concurrency = concurrency
        )

        val statistics = runner.runComparisonWithStatistics()

        Logger.info("\n任务数 {} 的统计结果:", cloudletCount)
        for (stat in statistics) {
            Logger.info(
                "  {}: Makespan={}±{}, Fitness={}±{}, AvgWaitingTime={}±{}",
                stat.algorithmName,
                dft.format(stat.makespan.mean),
                dft.format(stat.makespan.stdDev),
                dft.format(stat.fitness.mean),
                dft.format(stat.fitness.stdDev),
                dft.format(stat.averageWaitingTime.mean),
                dft.format(stat.averageWaitingTime.stdDev)
            )
        }

        Logger.info("\n任务数 {} 的实验完成", cloudletCount)
        return cloudletCount to statistics
    }
    
    /**
     * 导出批量实验结果到 CSV
     */
    private fun exportBatchResults(results: Map<Int, List<RealtimeAlgorithmStatistics>>) {
        if (!outputContext.csvEnabled) {
            Logger.info("CSV 输出已禁用，跳过实时批量任务数结果导出")
            printSummaryTable(results)
            return
        }
        val csvFile = outputContext.generateResultFileName("realtime_cloudlet_count_summary")
        
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
                        "Fitness_Mean", "Fitness_StdDev",
                        "AvgWaitingTime_Mean", "AvgWaitingTime_StdDev",
                        "AvgResponseTime_Mean", "AvgResponseTime_StdDev",
                        "RejectedCount_Mean", "RejectedCount_StdDev",
                        "TimeoutCount_Mean", "TimeoutCount_StdDev",
                        "FailedCount_Mean", "FailedCount_StdDev",
                        "RetryCount_Mean", "RetryCount_StdDev",
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
                        "P99ResponseTime_Mean", "P99ResponseTime_StdDev",
                        "ScaleOutCount_Mean", "ScaleOutCount_StdDev",
                        "ScaleInCount_Mean", "ScaleInCount_StdDev",
                        "ActiveVmPeak_Mean", "ActiveVmPeak_StdDev",
                        "AutoscalingCost_Mean", "AutoscalingCost_StdDev",
                        "ColdStartDelayTotal_Mean", "ColdStartDelayTotal_StdDev",
                        "ResourceRejectedCount_Mean", "ResourceRejectedCount_StdDev",
                        "RuntimeFailureCount_Mean", "RuntimeFailureCount_StdDev",
                        "TimeoutCancelledCount_Mean", "TimeoutCancelledCount_StdDev",
                        "MigrationCount_Mean", "MigrationCount_StdDev",
                        "CheckpointRecoveryCount_Mean", "CheckpointRecoveryCount_StdDev",
                        "RetrySuccessRate_Mean", "RetrySuccessRate_StdDev",
                        "SlaPenalty_Mean", "SlaPenalty_StdDev",
                        "PreemptedCount_Mean", "PreemptedCount_StdDev",
                        "PreemptionSuccessCount_Mean", "PreemptionSuccessCount_StdDev",
                        "PreemptionFailedCount_Mean", "PreemptionFailedCount_StdDev",
                        "AvgPreemptionDelay_Mean", "AvgPreemptionDelay_StdDev",
                        "PreemptionPenalty_Mean", "PreemptionPenalty_StdDev",
                        "CheckpointLossTotal_Mean", "CheckpointLossTotal_StdDev",
                        "TenantQuotaRejectedCount_Mean", "TenantQuotaRejectedCount_StdDev",
                        "TenantBudgetRejectedCount_Mean", "TenantBudgetRejectedCount_StdDev",
                        "TenantFairnessIndex_Mean", "TenantFairnessIndex_StdDev",
                        "FairnessViolationCount_Mean", "FairnessViolationCount_StdDev",
                        "TenantSlaPenalty_Mean", "TenantSlaPenalty_StdDev",
                        "DominantResourceFairnessIndex_Mean", "DominantResourceFairnessIndex_StdDev",
                        "CostSlaTradeoffScore_Mean", "CostSlaTradeoffScore_StdDev",
                        "RetrySuccessByTenant_Mean", "RetrySuccessByTenant_StdDev",
                        "CrossRackAssignmentCount_Mean", "CrossRackAssignmentCount_StdDev",
                        "CrossRegionAssignmentCount_Mean", "CrossRegionAssignmentCount_StdDev",
                        "AverageTopologyLatency_Mean", "AverageTopologyLatency_StdDev",
                        "TopologyCost_Mean", "TopologyCost_StdDev",
                        "HostFailureCount_Mean", "HostFailureCount_StdDev",
                        "RackFailureCount_Mean", "RackFailureCount_StdDev",
                        "RegionFailureCount_Mean", "RegionFailureCount_StdDev",
                        "FailureDomainSpreadScore_Mean", "FailureDomainSpreadScore_StdDev",
                        "Runs"
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
                                stat.p99ResponseTime.mean, stat.p99ResponseTime.stdDev,
                                stat.scaleOutCount.mean, stat.scaleOutCount.stdDev,
                                stat.scaleInCount.mean, stat.scaleInCount.stdDev,
                                stat.activeVmPeak.mean, stat.activeVmPeak.stdDev,
                                stat.autoscalingCost.mean, stat.autoscalingCost.stdDev,
                                stat.coldStartDelayTotal.mean, stat.coldStartDelayTotal.stdDev,
                                stat.resourceRejectedCount.mean, stat.resourceRejectedCount.stdDev,
                                stat.runtimeFailureCount.mean, stat.runtimeFailureCount.stdDev,
                                stat.timeoutCancelledCount.mean, stat.timeoutCancelledCount.stdDev,
                                stat.migrationCount.mean, stat.migrationCount.stdDev,
                                stat.checkpointRecoveryCount.mean, stat.checkpointRecoveryCount.stdDev,
                                stat.retrySuccessRate.mean, stat.retrySuccessRate.stdDev,
                                stat.slaPenalty.mean, stat.slaPenalty.stdDev,
                                stat.preemptedCount.mean, stat.preemptedCount.stdDev,
                                stat.preemptionSuccessCount.mean, stat.preemptionSuccessCount.stdDev,
                                stat.preemptionFailedCount.mean, stat.preemptionFailedCount.stdDev,
                                stat.averagePreemptionDelay.mean, stat.averagePreemptionDelay.stdDev,
                                stat.preemptionPenalty.mean, stat.preemptionPenalty.stdDev,
                                stat.checkpointLossTotal.mean, stat.checkpointLossTotal.stdDev,
                                stat.tenantQuotaRejectedCount.mean, stat.tenantQuotaRejectedCount.stdDev,
                                stat.tenantBudgetRejectedCount.mean, stat.tenantBudgetRejectedCount.stdDev,
                                stat.tenantFairnessIndex.mean, stat.tenantFairnessIndex.stdDev,
                                stat.fairnessViolationCount.mean, stat.fairnessViolationCount.stdDev,
                                stat.tenantSlaPenalty.mean, stat.tenantSlaPenalty.stdDev,
                                stat.dominantResourceFairnessIndex.mean, stat.dominantResourceFairnessIndex.stdDev,
                                stat.costSlaTradeoffScore.mean, stat.costSlaTradeoffScore.stdDev,
                                stat.retrySuccessByTenant.mean, stat.retrySuccessByTenant.stdDev,
                                stat.crossRackAssignmentCount.mean, stat.crossRackAssignmentCount.stdDev,
                                stat.crossRegionAssignmentCount.mean, stat.crossRegionAssignmentCount.stdDev,
                                stat.averageTopologyLatency.mean, stat.averageTopologyLatency.stdDev,
                                stat.topologyCost.mean, stat.topologyCost.stdDev,
                                stat.hostFailureCount.mean, stat.hostFailureCount.stdDev,
                                stat.rackFailureCount.mean, stat.rackFailureCount.stdDev,
                                stat.regionFailureCount.mean, stat.regionFailureCount.stdDev,
                                stat.failureDomainSpreadScore.mean, stat.failureDomainSpreadScore.stdDev,
                                runs
                            )
                        ) + "\n"
                    )
                }
            }
        }
        
        Logger.info("\n批量实验结果已导出到: {}", csvFile.absolutePath)
        
        val summaryHeaders = listOf(
            "CloudletCount",
            "Algorithm",
            "AvgMakespan",
            "AvgLoadBalance",
            "AvgCost",
            "AvgTotalTime",
            "AvgFitness",
            "AvgWaitingTime",
            "AvgResponseTime",
            "RetryCount",
            "PermanentFailedCount",
            "AvgDecisionDelay",
            "CompletedCount",
            "SubmittedCount",
            "SlaViolationCount",
            "SlaViolationRate",
            "CapacityRejectedCount",
            "AvgQueueDepth",
            "MaxQueueDepth",
            "P95ResponseTime",
            "P99ResponseTime",
            "ScaleOutCount",
            "ScaleInCount",
            "ActiveVmPeak",
            "AutoscalingCost",
            "ColdStartDelayTotal",
            "ResourceRejectedCount",
            "RuntimeFailureCount",
            "TimeoutCancelledCount",
            "MigrationCount",
            "CheckpointRecoveryCount",
            "RetrySuccessRate",
            "SlaPenalty",
            "PreemptedCount",
            "PreemptionSuccessCount",
            "PreemptionFailedCount",
            "AvgPreemptionDelay",
            "PreemptionPenalty",
            "CheckpointLossTotal",
            "TenantQuotaRejectedCount",
            "TenantBudgetRejectedCount",
            "TenantFairnessIndex",
            "FairnessViolationCount",
            "TenantSlaPenalty",
            "DominantResourceFairnessIndex",
            "CostSlaTradeoffScore",
            "RetrySuccessByTenant",
            "CrossRackAssignmentCount",
            "CrossRegionAssignmentCount",
            "AverageTopologyLatency",
            "TopologyCost",
            "HostFailureCount",
            "RackFailureCount",
            "RegionFailureCount",
            "FailureDomainSpreadScore"
        )
        val summaryData = results.flatMap { (count, stats) ->
            stats.map { stat ->
                mapOf(
                    "CloudletCount" to count,
                    "Algorithm" to stat.algorithmName,
                    "AvgMakespan" to stat.makespan.mean,
                    "AvgLoadBalance" to stat.loadBalance.mean,
                    "AvgCost" to stat.cost.mean,
                    "AvgTotalTime" to stat.totalTime.mean,
                    "AvgFitness" to stat.fitness.mean,
                    "AvgWaitingTime" to stat.averageWaitingTime.mean,
                    "AvgResponseTime" to stat.averageResponseTime.mean,
                    "RetryCount" to stat.retryCount.mean,
                    "PermanentFailedCount" to stat.permanentFailedCount.mean,
                    "AvgDecisionDelay" to stat.averageDecisionDelay.mean,
                    "CompletedCount" to stat.completedCount.mean,
                    "SubmittedCount" to stat.submittedCount.mean,
                    "SlaViolationCount" to stat.slaViolationCount.mean,
                    "SlaViolationRate" to stat.slaViolationRate.mean,
                    "CapacityRejectedCount" to stat.capacityRejectedCount.mean,
                    "AvgQueueDepth" to stat.averageQueueDepth.mean,
                    "MaxQueueDepth" to stat.maxQueueDepth.mean,
                    "P95ResponseTime" to stat.p95ResponseTime.mean,
                    "P99ResponseTime" to stat.p99ResponseTime.mean,
                    "ScaleOutCount" to stat.scaleOutCount.mean,
                    "ScaleInCount" to stat.scaleInCount.mean,
                    "ActiveVmPeak" to stat.activeVmPeak.mean,
                    "AutoscalingCost" to stat.autoscalingCost.mean,
                    "ColdStartDelayTotal" to stat.coldStartDelayTotal.mean,
                    "ResourceRejectedCount" to stat.resourceRejectedCount.mean,
                    "RuntimeFailureCount" to stat.runtimeFailureCount.mean,
                    "TimeoutCancelledCount" to stat.timeoutCancelledCount.mean,
                    "MigrationCount" to stat.migrationCount.mean,
                    "CheckpointRecoveryCount" to stat.checkpointRecoveryCount.mean,
                    "RetrySuccessRate" to stat.retrySuccessRate.mean,
                    "SlaPenalty" to stat.slaPenalty.mean,
                    "PreemptedCount" to stat.preemptedCount.mean,
                    "PreemptionSuccessCount" to stat.preemptionSuccessCount.mean,
                    "PreemptionFailedCount" to stat.preemptionFailedCount.mean,
                    "AvgPreemptionDelay" to stat.averagePreemptionDelay.mean,
                    "PreemptionPenalty" to stat.preemptionPenalty.mean,
                    "CheckpointLossTotal" to stat.checkpointLossTotal.mean,
                    "TenantQuotaRejectedCount" to stat.tenantQuotaRejectedCount.mean,
                    "TenantBudgetRejectedCount" to stat.tenantBudgetRejectedCount.mean,
                    "TenantFairnessIndex" to stat.tenantFairnessIndex.mean,
                    "FairnessViolationCount" to stat.fairnessViolationCount.mean,
                    "TenantSlaPenalty" to stat.tenantSlaPenalty.mean,
                    "DominantResourceFairnessIndex" to stat.dominantResourceFairnessIndex.mean,
                    "CostSlaTradeoffScore" to stat.costSlaTradeoffScore.mean,
                    "RetrySuccessByTenant" to stat.retrySuccessByTenant.mean,
                    "CrossRackAssignmentCount" to stat.crossRackAssignmentCount.mean,
                    "CrossRegionAssignmentCount" to stat.crossRegionAssignmentCount.mean,
                    "AverageTopologyLatency" to stat.averageTopologyLatency.mean,
                    "TopologyCost" to stat.topologyCost.mean,
                    "HostFailureCount" to stat.hostFailureCount.mean,
                    "RackFailureCount" to stat.rackFailureCount.mean,
                    "RegionFailureCount" to stat.regionFailureCount.mean,
                    "FailureDomainSpreadScore" to stat.failureDomainSpreadScore.mean
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

