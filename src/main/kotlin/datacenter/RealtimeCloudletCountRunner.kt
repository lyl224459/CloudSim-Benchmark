package datacenter

import util.Logger
import java.text.DecimalFormat

private const val REALTIME_LOG_SEPARATOR_WIDTH = 80

/**
 * 实时调度批量任务数实验运行器
 * 支持按照不同的任务数批量执行实时调度实验，每个任务数可以运行多次并取平均值
 */
class RealtimeCloudletCountRunner internal constructor(
    private val request: RealtimeExperimentRequest,
    private val summaryRunner: RealtimeSummaryRunnerFactory,
    private val exporter: RealtimeCloudletCountExportService,
) {
    private val cloudletCounts = request.realtime.cloudletCounts
    private val simulationDuration = request.realtime.simulationDuration
    private val arrivalRate = request.realtime.arrivalRate
    private val population = request.optimizer.population
    private val maxIter = request.optimizer.maxIter
    private val randomSeed = request.execution.randomSeed
    private val runs = request.realtime.runs
    private val generatorType = request.realtime.generatorType
    private val scheduling = request.realtime.scheduling
    private val outputContext = request.execution.outputContext
    private val concurrency = request.execution.concurrency
    private val dft = DecimalFormat("###.##")

    constructor(request: RealtimeExperimentRequest) : this(
        request,
        RealtimeSummaryRunnerFactory { child -> RealtimeComparisonRunner(child).runComparisonSummaries() },
        RealtimeCloudletCountResultExporter(request.execution.outputContext),
    )

    /**
     * 运行批量任务数实验
     */
    suspend fun runBatchExperiment() {
        Logger.info("\n${"=".repeat(REALTIME_LOG_SEPARATOR_WIDTH)}")
        Logger.info("开始实时调度批量任务数实验")
        Logger.info("${"=".repeat(REALTIME_LOG_SEPARATOR_WIDTH)}")
        Logger.info("任务数列表: {}", cloudletCounts.joinToString(", "))
        Logger.info("每个任务数运行次数: {}", runs)
        Logger.info("仿真持续时间: {}s, 到达率: {}/s", simulationDuration, arrivalRate)
        Logger.info("种群大小: {}, 最大迭代: {}", population, maxIter)
        Logger.info("随机数种子: {}", randomSeed)
        Logger.info("${"=".repeat(REALTIME_LOG_SEPARATOR_WIDTH)}\n")

        outputContext.saveExperimentInfo(realtimeCloudletCountExperimentInfo())

        val allResults =
            concurrency
                .map(cloudletCounts.withIndex().toList()) { indexed ->
                    runCloudletCount(indexed.index, indexed.value)
                }.toMap()

        exporter.export(allResults)

        Logger.info("\n${"=".repeat(REALTIME_LOG_SEPARATOR_WIDTH)}")
        Logger.info("实时调度批量任务数实验完成！")
        Logger.info("${"=".repeat(REALTIME_LOG_SEPARATOR_WIDTH)}")
    }

    private fun realtimeCloudletCountExperimentInfo(): Map<String, Any> =
        coreExperimentInfo() + schedulingExperimentInfo() + topologyExperimentInfo()

    private fun coreExperimentInfo(): Map<String, Any> =
        mapOf(
            "运行模式" to "实时调度批量任务数 (Realtime Multi)",
            "任务数列表" to cloudletCounts.joinToString(", "),
            "每个任务数运行次数" to runs,
            "仿真持续时间" to simulationDuration,
            "到达率" to arrivalRate,
            "种群大小" to population,
            "最大迭代次数" to maxIter,
            "随机数种子" to randomSeed,
            "任务生成器" to generatorType.name,
        )

    private fun schedulingExperimentInfo(): Map<String, Any> =
        mapOf(
            "队列策略" to scheduling.queuePolicy,
            "优先级层级" to scheduling.priorityLevels,
            "高优先级比例" to scheduling.highPriorityRatio,
            "SLA deadline 系数" to scheduling.deadlineFactor,
            "单 VM 队列容量" to scheduling.vmQueueCapacity,
            "过载失败倍率" to scheduling.overloadFailureMultiplier,
            "弹性伸缩" to scheduling.autoscalingEnabled,
            "Autoscaling 策略" to scheduling.autoscalingPolicy,
            "Autoscaling 评估间隔" to scheduling.autoscalingEvaluationInterval,
            "扩容队列阈值" to scheduling.scaleOutQueueThreshold,
            "最大动态 VM 数" to scheduling.maxDynamicVms,
            "VM 冷启动延迟" to scheduling.vmColdStartDelay,
            "扩容冷却时间" to scheduling.scaleCooldown,
            "批量扩容大小" to scheduling.scaleOutBatchSize,
            "Warm pool 大小" to scheduling.warmPoolSize,
            "最小活跃 VM 数" to scheduling.minActiveVms,
            "缩容 Drain" to scheduling.scaleInDrainEnabled,
            "到达率窗口" to scheduling.arrivalRateWindow,
            "预测前瞻窗口" to scheduling.predictiveLookahead,
            "扩容压力阈值" to scheduling.scalePressureThreshold,
            "动态 VM 秒级成本" to scheduling.dynamicVmCostPerSecond,
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
        )

    private fun topologyExperimentInfo(): Map<String, Any> =
        mapOf(
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
            "CPU Overcommit 比例" to scheduling.cpuOvercommitRatio,
            "Host RAM 容量" to scheduling.hostRamCapacity,
            "Host 带宽容量" to scheduling.hostBwCapacity,
            "Host I/O 容量" to scheduling.hostIoCapacity,
            "网络带宽共享" to scheduling.networkBandwidthSharingEnabled,
            "Storage IOPS 共享" to scheduling.storageIopsSharingEnabled,
            "镜像拉取队列" to scheduling.imagePullQueueEnabled,
            "Noisy-neighbor 惩罚权重" to scheduling.noisyNeighborPenaltyWeight,
            "跨 Rack 带宽" to scheduling.crossRackBandwidth,
            "跨 Region 带宽" to scheduling.crossRegionBandwidth,
            "数据本地性策略" to scheduling.dataLocalityPolicy,
            "镜像缓存容量" to scheduling.imageCacheCapacity,
        )

    private suspend fun runCloudletCount(
        index: Int,
        cloudletCount: Int,
    ): Pair<Int, List<RealtimeRunSummary>> {
        Logger.info("\n${"#".repeat(REALTIME_LOG_SEPARATOR_WIDTH)}")
        Logger.info("任务数: {} ({}/{})", cloudletCount, index + 1, cloudletCounts.size)
        Logger.info("${"#".repeat(REALTIME_LOG_SEPARATOR_WIDTH)}")

        val childOutputContext = outputContext.child("cloudlets_$cloudletCount")
        val childRequest =
            request.copy(
                realtime = request.realtime.copy(cloudletCount = cloudletCount),
                execution = request.execution.copy(outputContext = childOutputContext),
            )
        val summaries = summaryRunner.run(childRequest).sortedBy { it.algorithmName }
        val statistics = summaries.mapNotNull { it.statistics }

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
                dft.format(stat.averageWaitingTime.stdDev),
            )
        }

        Logger.info("\n任务数 {} 的实验完成", cloudletCount)
        return cloudletCount to summaries
    }
}
