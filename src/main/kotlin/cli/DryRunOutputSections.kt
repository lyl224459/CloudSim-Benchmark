package cli

import config.RealtimeSchedulingConfig
import util.Logger

private val bracketedRealtimeKeys = setOf("tenantQuota", "tenantWeights", "tenantCostBudget")
private val realtimeOverviewFormat =
    listOf(
        "distribution",
        "strategy",
        "maxQueueSize",
        "taskTimeout",
        "resourceReservation",
        "decisionDelay",
        "decisionJitter",
        "failureRate",
        "retryLimit",
        "retryDelay",
        "retryBackoffMultiplier",
        "queuePolicy",
        "priorityLevels",
        "highPriorityRatio",
        "deadlineFactor",
        "vmQueueCapacity",
        "overloadFailureMultiplier",
        "autoscalingEnabled",
        "scaleOutQueueThreshold",
        "maxDynamicVms",
        "vmColdStartDelay",
        "resourceModelEnabled",
        "networkLatency",
        "imagePullDelay",
        "runtimeFailureRate",
        "nodeFailureRate",
        "timeoutAction",
        "preemptionEnabled",
        "preemptionPolicy",
        "preemptionMinPriorityGap",
        "preemptionMaxPerTask",
        "preemptionDelay",
        "preemptionPenalty",
        "multiTenantEnabled",
        "tenantCount",
        "tenantQuota",
        "tenantWeights",
        "tenantFairnessPolicy",
        "tenantSchedulingPolicy",
        "tenantBurstAllowance",
        "tenantSlaPenaltyWeight",
        "tenantCostBudget",
        "topologyEnabled",
        "topologyPolicy",
        "regionCount",
        "racksPerRegion",
        "hostsPerRack",
        "localRegion",
        "crossRackLatency",
        "crossRegionLatency",
        "crossRegionCost",
        "hostFailureRate",
        "rackFailureRate",
        "regionFailureRate",
    ).joinToString(", ", prefix = "实时到达/调度: ") { key ->
        if (key in bracketedRealtimeKeys) "$key=[{}]" else "$key={}"
    }
private val physicalTopologyFormat =
    listOf(
        "physicalTopologyEnabled",
        "dataLocalityEnabled",
        "imageCacheEnabled",
        "hostCountPerRack",
        "hostCpuCapacity",
        "hostRamCapacity",
        "hostBwCapacity",
        "hostIoCapacity",
        "crossRackBandwidth",
        "crossRegionBandwidth",
        "dataLocalityPolicy",
        "imageCacheCapacity",
    ).joinToString(", ", prefix = "物理拓扑/数据本地性: ") { "$it={}" }

internal fun printDryRunHeader(resolved: ResolvedExperimentConfig) {
    Logger.result("Dry run: 不会创建实验目录或结果文件")
    Logger.result("模式: {}", resolved.mode)
    Logger.result("Profile: {}", resolved.profileName ?: "(无)")
    Logger.result("输出目录: {}", resolved.output.resultsDir)
    Logger.result("随机种子: {}", resolved.randomSeed)
    Logger.result("运行次数: {}", resolved.runs)
    if (resolved.taskCounts.isNotEmpty()) {
        Logger.result("任务数列表: {}", resolved.taskCounts.joinToString(", "))
    } else {
        val count = if (resolved.mode == "batch") resolved.batch.cloudletCount else resolved.realtime.cloudletCount
        Logger.result("任务数: {}", count)
    }
}

internal fun printDryRunAlgorithms(resolved: ResolvedExperimentConfig) {
    Logger.result("算法:")
    for (algorithm in resolved.algorithms) {
        Logger.result(
            "  {} population={} maxIter={}",
            algorithm.displayName,
            algorithm.settings.population,
            algorithm.settings.maxIter,
        )
    }
}

@Suppress("SpreadOperator")
internal fun printRealtimeOverview(resolved: ResolvedExperimentConfig) {
    Logger.result(realtimeOverviewFormat, *realtimeOverviewValues(resolved))
}

private fun realtimeOverviewValues(resolved: ResolvedExperimentConfig): Array<Any?> {
    val scheduling = resolved.realtime.scheduling
    val values =
        listOf(resolved.realtime.arrival.distribution) +
            realtimeCoreSchedulingValues(scheduling) +
            realtimeFailureSchedulingValues(scheduling) +
            realtimeTenantSchedulingValues(scheduling) +
            realtimeTopologySchedulingValues(scheduling)
    return values.toTypedArray()
}

private fun realtimeCoreSchedulingValues(scheduling: RealtimeSchedulingConfig): List<Any?> =
    listOf(
        scheduling.strategy,
        scheduling.maxQueueSize,
        scheduling.taskTimeout,
        scheduling.resourceReservation,
        scheduling.decisionDelay,
        scheduling.decisionJitter,
        scheduling.failureRate,
        scheduling.retryLimit,
        scheduling.retryDelay,
        scheduling.retryBackoffMultiplier,
        scheduling.queuePolicy,
        scheduling.priorityLevels,
        scheduling.highPriorityRatio,
        scheduling.deadlineFactor,
        scheduling.vmQueueCapacity,
        scheduling.overloadFailureMultiplier,
        scheduling.autoscalingEnabled,
        scheduling.scaleOutQueueThreshold,
        scheduling.maxDynamicVms,
        scheduling.vmColdStartDelay,
        scheduling.resourceModelEnabled,
        scheduling.networkLatency,
        scheduling.imagePullDelay,
    )

private fun realtimeFailureSchedulingValues(scheduling: RealtimeSchedulingConfig): List<Any?> =
    listOf(
        scheduling.runtimeFailureRate,
        scheduling.nodeFailureRate,
        scheduling.timeoutAction,
        scheduling.preemptionEnabled,
        scheduling.preemptionPolicy,
        scheduling.preemptionMinPriorityGap,
        scheduling.preemptionMaxPerTask,
        scheduling.preemptionDelay,
        scheduling.preemptionPenalty,
    )

private fun realtimeTenantSchedulingValues(scheduling: RealtimeSchedulingConfig): List<Any?> =
    listOf(
        scheduling.multiTenantEnabled,
        scheduling.tenantCount,
        scheduling.tenantQuota.joinToString(","),
        scheduling.tenantWeights.joinToString(","),
        scheduling.tenantFairnessPolicy,
        scheduling.tenantSchedulingPolicy,
        scheduling.tenantBurstAllowance,
        scheduling.tenantSlaPenaltyWeight,
        scheduling.tenantCostBudget.joinToString(","),
    )

private fun realtimeTopologySchedulingValues(scheduling: RealtimeSchedulingConfig): List<Any?> =
    listOf(
        scheduling.topologyEnabled,
        scheduling.topologyPolicy,
        scheduling.regionCount,
        scheduling.racksPerRegion,
        scheduling.hostsPerRack,
        scheduling.localRegion,
        scheduling.crossRackLatency,
        scheduling.crossRegionLatency,
        scheduling.crossRegionCost,
        scheduling.hostFailureRate,
        scheduling.rackFailureRate,
        scheduling.regionFailureRate,
    )

internal fun printPhysicalTopology(resolved: ResolvedExperimentConfig) {
    val scheduling = resolved.realtime.scheduling
    Logger.result(
        physicalTopologyFormat,
        scheduling.physicalTopologyEnabled,
        scheduling.dataLocalityEnabled,
        scheduling.imageCacheEnabled,
        scheduling.hostCountPerRack,
        scheduling.hostCpuCapacity,
        scheduling.hostRamCapacity,
        scheduling.hostBwCapacity,
        scheduling.hostIoCapacity,
        scheduling.crossRackBandwidth,
        scheduling.crossRegionBandwidth,
        scheduling.dataLocalityPolicy,
        scheduling.imageCacheCapacity,
    )
}
