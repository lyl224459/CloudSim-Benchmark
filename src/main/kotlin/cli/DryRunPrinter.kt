package cli

import config.ExperimentConfig
import config.PresetConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import scheduler.AlgorithmMode
import scheduler.AlgorithmRegistry
import util.Logger
import java.io.File

object DryRunPrinter {
    private val prettyJson = Json { prettyPrint = true }

    fun printAlgorithms(mode: String) {
        val algorithms = when (normalizeMode(mode)) {
            "batch" -> AlgorithmRegistry.forMode(AlgorithmMode.BATCH).map { it.name }
            "realtime" -> AlgorithmRegistry.forMode(AlgorithmMode.REALTIME).map { it.name }
            else -> throw IllegalArgumentException("list algorithms 只接受 batch 或 realtime")
        }
        Logger.result("可用算法 ({}): {}", mode, algorithms.joinToString(", "))
    }

    fun printProfiles(config: ExperimentConfig) {
        if (config.profiles.isEmpty()) {
            Logger.result("未定义 profiles")
            return
        }
        Logger.result("可用 profiles:")
        config.profiles.toSortedMap().forEach { (name, profile) ->
            val selection = when {
                profile.algorithms.isNotEmpty() -> "algorithms=${profile.algorithms.joinToString(", ")}"
                !profile.preset.isNullOrBlank() -> "preset=${profile.preset}"
                else -> "(未指定)"
            }
            Logger.result("  {} -> mode={}, {}", name, profile.mode, selection)
        }
        config.defaultProfile?.let { Logger.result("默认 profile: {}", it) }
    }

    fun printPresets(presets: Map<String, PresetConfig>) {
        if (presets.isEmpty()) {
            Logger.result("未定义预设")
            return
        }
        Logger.result("可用预设:")
        presets.toSortedMap().forEach { (name, preset) ->
            Logger.result("  {} = {}", name, preset.algorithms.joinToString(", "))
        }
    }

    fun printDryRun(resolved: ResolvedExperimentConfig) {
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
        Logger.result("算法:")
        for (algorithm in resolved.algorithms) {
            Logger.result("  {} population={} maxIter={}", algorithm.displayName, algorithm.settings.population, algorithm.settings.maxIter)
        }
        if (resolved.mode.startsWith("realtime")) {
            Logger.result(
                "实时到达/调度: distribution={}, strategy={}, maxQueueSize={}, taskTimeout={}, resourceReservation={}, decisionDelay={}, decisionJitter={}, failureRate={}, retryLimit={}, retryDelay={}, retryBackoffMultiplier={}, queuePolicy={}, priorityLevels={}, highPriorityRatio={}, deadlineFactor={}, vmQueueCapacity={}, overloadFailureMultiplier={}, autoscalingEnabled={}, scaleOutQueueThreshold={}, maxDynamicVms={}, vmColdStartDelay={}, resourceModelEnabled={}, networkLatency={}, imagePullDelay={}, runtimeFailureRate={}, nodeFailureRate={}, timeoutAction={}, preemptionEnabled={}, preemptionPolicy={}, preemptionMinPriorityGap={}, preemptionMaxPerTask={}, preemptionDelay={}, preemptionPenalty={}, multiTenantEnabled={}, tenantCount={}, tenantQuota=[{}], tenantWeights=[{}], tenantFairnessPolicy={}, tenantSchedulingPolicy={}, tenantBurstAllowance={}, tenantSlaPenaltyWeight={}, tenantCostBudget=[{}], topologyEnabled={}, topologyPolicy={}, regionCount={}, racksPerRegion={}, hostsPerRack={}, localRegion={}, crossRackLatency={}, crossRegionLatency={}, crossRegionCost={}, hostFailureRate={}, rackFailureRate={}, regionFailureRate={}",
                resolved.realtime.arrival.distribution,
                resolved.realtime.scheduling.strategy,
                resolved.realtime.scheduling.maxQueueSize,
                resolved.realtime.scheduling.taskTimeout,
                resolved.realtime.scheduling.resourceReservation,
                resolved.realtime.scheduling.decisionDelay,
                resolved.realtime.scheduling.decisionJitter,
                resolved.realtime.scheduling.failureRate,
                resolved.realtime.scheduling.retryLimit,
                resolved.realtime.scheduling.retryDelay,
                resolved.realtime.scheduling.retryBackoffMultiplier,
                resolved.realtime.scheduling.queuePolicy,
                resolved.realtime.scheduling.priorityLevels,
                resolved.realtime.scheduling.highPriorityRatio,
                resolved.realtime.scheduling.deadlineFactor,
                resolved.realtime.scheduling.vmQueueCapacity,
                resolved.realtime.scheduling.overloadFailureMultiplier,
                resolved.realtime.scheduling.autoscalingEnabled,
                resolved.realtime.scheduling.scaleOutQueueThreshold,
                resolved.realtime.scheduling.maxDynamicVms,
                resolved.realtime.scheduling.vmColdStartDelay,
                resolved.realtime.scheduling.resourceModelEnabled,
                resolved.realtime.scheduling.networkLatency,
                resolved.realtime.scheduling.imagePullDelay,
                resolved.realtime.scheduling.runtimeFailureRate,
                resolved.realtime.scheduling.nodeFailureRate,
                resolved.realtime.scheduling.timeoutAction,
                resolved.realtime.scheduling.preemptionEnabled,
                resolved.realtime.scheduling.preemptionPolicy,
                resolved.realtime.scheduling.preemptionMinPriorityGap,
                resolved.realtime.scheduling.preemptionMaxPerTask,
                resolved.realtime.scheduling.preemptionDelay,
                resolved.realtime.scheduling.preemptionPenalty,
                resolved.realtime.scheduling.multiTenantEnabled,
                resolved.realtime.scheduling.tenantCount,
                resolved.realtime.scheduling.tenantQuota.joinToString(","),
                resolved.realtime.scheduling.tenantWeights.joinToString(","),
                resolved.realtime.scheduling.tenantFairnessPolicy,
                resolved.realtime.scheduling.tenantSchedulingPolicy,
                resolved.realtime.scheduling.tenantBurstAllowance,
                resolved.realtime.scheduling.tenantSlaPenaltyWeight,
                resolved.realtime.scheduling.tenantCostBudget.joinToString(","),
                resolved.realtime.scheduling.topologyEnabled,
                resolved.realtime.scheduling.topologyPolicy,
                resolved.realtime.scheduling.regionCount,
                resolved.realtime.scheduling.racksPerRegion,
                resolved.realtime.scheduling.hostsPerRack,
                resolved.realtime.scheduling.localRegion,
                resolved.realtime.scheduling.crossRackLatency,
                resolved.realtime.scheduling.crossRegionLatency,
                resolved.realtime.scheduling.crossRegionCost,
                resolved.realtime.scheduling.hostFailureRate,
                resolved.realtime.scheduling.rackFailureRate,
                resolved.realtime.scheduling.regionFailureRate
            )
            Logger.result(
                "物理拓扑/数据本地性: physicalTopologyEnabled={}, dataLocalityEnabled={}, imageCacheEnabled={}, hostCountPerRack={}, hostCpuCapacity={}, hostRamCapacity={}, hostBwCapacity={}, hostIoCapacity={}, crossRackBandwidth={}, crossRegionBandwidth={}, dataLocalityPolicy={}, imageCacheCapacity={}",
                resolved.realtime.scheduling.physicalTopologyEnabled,
                resolved.realtime.scheduling.dataLocalityEnabled,
                resolved.realtime.scheduling.imageCacheEnabled,
                resolved.realtime.scheduling.hostCountPerRack,
                resolved.realtime.scheduling.hostCpuCapacity,
                resolved.realtime.scheduling.hostRamCapacity,
                resolved.realtime.scheduling.hostBwCapacity,
                resolved.realtime.scheduling.hostIoCapacity,
                resolved.realtime.scheduling.crossRackBandwidth,
                resolved.realtime.scheduling.crossRegionBandwidth,
                resolved.realtime.scheduling.dataLocalityPolicy,
                resolved.realtime.scheduling.imageCacheCapacity
            )
        }
        Logger.result("CSV 输出: enabled={}, delimiter='{}'", resolved.output.csvEnabled, resolved.output.csvDelimiter)
    }

    fun resolvedJson(resolved: ResolvedExperimentConfig, experimentDir: File?, timestamp: String): String {
        val config = resolved.experimentConfig
        val json = buildJsonObject {
            put("mode", resolved.mode)
            put("profile", resolved.profileName ?: "")
            put("timestamp", timestamp)
            experimentDir?.let { put("experimentDir", it.absolutePath) }
            put("outputDir", resolved.output.resultsDir)
            put("randomSeed", resolved.randomSeed)
            put("runs", resolved.runs)
            put("preset", resolved.presetName ?: "")
            putJsonArray("algorithms") {
                resolved.selectedAlgorithmNames.forEach { add(JsonPrimitive(it)) }
            }
            putJsonObject("algorithmSettings") {
                resolved.algorithms.forEach { algorithm ->
                    putJsonObject(algorithm.name) {
                        put("population", algorithm.settings.population)
                        put("maxIter", algorithm.settings.maxIter)
                    }
                }
            }
            putJsonObject("batch") {
                put("cloudletCount", config.batch.cloudletCount)
                putJsonArray("cloudletCounts") { config.batch.cloudletCounts.forEach { add(JsonPrimitive(it)) } }
                put("generatorType", config.batch.generatorType.name)
                putJsonObject("objective") {
                    put("cost", config.batch.objectiveWeights.cost)
                    put("totalTime", config.batch.objectiveWeights.totalTime)
                    put("loadBalance", config.batch.objectiveWeights.loadBalance)
                    put("makespan", config.batch.objectiveWeights.makespan)
                }
            }
            putJsonObject("realtime") {
                put("cloudletCount", config.realtime.cloudletCount)
                putJsonArray("cloudletCounts") { config.realtime.cloudletCounts.forEach { add(JsonPrimitive(it)) } }
                put("simulationDuration", config.realtime.simulationDuration)
                put("arrivalRate", config.realtime.arrivalRate)
                put("generatorType", config.realtime.generatorType.name)
                putJsonObject("objective") {
                    put("cost", config.realtime.objectiveWeights.cost)
                    put("totalTime", config.realtime.objectiveWeights.totalTime)
                    put("loadBalance", config.realtime.objectiveWeights.loadBalance)
                    put("makespan", config.realtime.objectiveWeights.makespan)
                }
                putJsonObject("arrival") {
                    put("distribution", config.realtime.arrival.distribution)
                    put("burstIntensity", config.realtime.arrival.burstIntensity)
                    put("burstDuration", config.realtime.arrival.burstDuration)
                }
                putJsonObject("scheduling") {
                    put("strategy", config.realtime.scheduling.strategy)
                    put("maxQueueSize", config.realtime.scheduling.maxQueueSize)
                    put("taskTimeout", config.realtime.scheduling.taskTimeout)
                    put("resourceReservation", config.realtime.scheduling.resourceReservation)
                    put("decisionDelay", config.realtime.scheduling.decisionDelay)
                    put("decisionJitter", config.realtime.scheduling.decisionJitter)
                    put("failureRate", config.realtime.scheduling.failureRate)
                    put("retryLimit", config.realtime.scheduling.retryLimit)
                    put("retryDelay", config.realtime.scheduling.retryDelay)
                    put("retryBackoffMultiplier", config.realtime.scheduling.retryBackoffMultiplier)
                    put("queuePolicy", config.realtime.scheduling.queuePolicy)
                    put("priorityLevels", config.realtime.scheduling.priorityLevels)
                    put("highPriorityRatio", config.realtime.scheduling.highPriorityRatio)
                    put("deadlineFactor", config.realtime.scheduling.deadlineFactor)
                    put("vmQueueCapacity", config.realtime.scheduling.vmQueueCapacity)
                    put("overloadFailureMultiplier", config.realtime.scheduling.overloadFailureMultiplier)
                    put("autoscalingEnabled", config.realtime.scheduling.autoscalingEnabled)
                    put("scaleOutQueueThreshold", config.realtime.scheduling.scaleOutQueueThreshold)
                    put("scaleInIdleTime", config.realtime.scheduling.scaleInIdleTime)
                    put("maxDynamicVms", config.realtime.scheduling.maxDynamicVms)
                    put("vmColdStartDelay", config.realtime.scheduling.vmColdStartDelay)
                    put("scaleOutCost", config.realtime.scheduling.scaleOutCost)
                    put("scaleInProtectionTime", config.realtime.scheduling.scaleInProtectionTime)
                    put("resourceModelEnabled", config.realtime.scheduling.resourceModelEnabled)
                    put("networkLatency", config.realtime.scheduling.networkLatency)
                    put("imagePullDelay", config.realtime.scheduling.imagePullDelay)
                    put("ioWeight", config.realtime.scheduling.ioWeight)
                    put("ramWeight", config.realtime.scheduling.ramWeight)
                    put("bwWeight", config.realtime.scheduling.bwWeight)
                    put("runtimeFailureRate", config.realtime.scheduling.runtimeFailureRate)
                    put("nodeFailureRate", config.realtime.scheduling.nodeFailureRate)
                    put("checkpointInterval", config.realtime.scheduling.checkpointInterval)
                    put("migrationDelay", config.realtime.scheduling.migrationDelay)
                    put("timeoutAction", config.realtime.scheduling.timeoutAction)
                    put("preemptionEnabled", config.realtime.scheduling.preemptionEnabled)
                    put("preemptionPolicy", config.realtime.scheduling.preemptionPolicy)
                    put("preemptionMinPriorityGap", config.realtime.scheduling.preemptionMinPriorityGap)
                    put("preemptionMaxPerTask", config.realtime.scheduling.preemptionMaxPerTask)
                    put("preemptionDelay", config.realtime.scheduling.preemptionDelay)
                    put("preemptionPenalty", config.realtime.scheduling.preemptionPenalty)
                    put("multiTenantEnabled", config.realtime.scheduling.multiTenantEnabled)
                    put("tenantCount", config.realtime.scheduling.tenantCount)
                    putJsonArray("tenantQuota") {
                        config.realtime.scheduling.tenantQuota.forEach { add(JsonPrimitive(it)) }
                    }
                    putJsonArray("tenantWeights") {
                        config.realtime.scheduling.tenantWeights.forEach { add(JsonPrimitive(it)) }
                    }
                    put("tenantFairnessPolicy", config.realtime.scheduling.tenantFairnessPolicy)
                    put("tenantSchedulingPolicy", config.realtime.scheduling.tenantSchedulingPolicy)
                    put("tenantBurstAllowance", config.realtime.scheduling.tenantBurstAllowance)
                    put("tenantSlaPenaltyWeight", config.realtime.scheduling.tenantSlaPenaltyWeight)
                    putJsonArray("tenantCostBudget") {
                        config.realtime.scheduling.tenantCostBudget.forEach { add(JsonPrimitive(it)) }
                    }
                    put("topologyEnabled", config.realtime.scheduling.topologyEnabled)
                    put("topologyPolicy", config.realtime.scheduling.topologyPolicy)
                    put("regionCount", config.realtime.scheduling.regionCount)
                    put("racksPerRegion", config.realtime.scheduling.racksPerRegion)
                    put("hostsPerRack", config.realtime.scheduling.hostsPerRack)
                    put("localRegion", config.realtime.scheduling.localRegion)
                    put("crossRackLatency", config.realtime.scheduling.crossRackLatency)
                    put("crossRegionLatency", config.realtime.scheduling.crossRegionLatency)
                    put("crossRegionCost", config.realtime.scheduling.crossRegionCost)
                    put("hostFailureRate", config.realtime.scheduling.hostFailureRate)
                    put("rackFailureRate", config.realtime.scheduling.rackFailureRate)
                    put("regionFailureRate", config.realtime.scheduling.regionFailureRate)
                    put("physicalTopologyEnabled", config.realtime.scheduling.physicalTopologyEnabled)
                    put("dataLocalityEnabled", config.realtime.scheduling.dataLocalityEnabled)
                    put("imageCacheEnabled", config.realtime.scheduling.imageCacheEnabled)
                    put("hostCountPerRack", config.realtime.scheduling.hostCountPerRack)
                    put("hostCpuCapacity", config.realtime.scheduling.hostCpuCapacity)
                    put("hostRamCapacity", config.realtime.scheduling.hostRamCapacity)
                    put("hostBwCapacity", config.realtime.scheduling.hostBwCapacity)
                    put("hostIoCapacity", config.realtime.scheduling.hostIoCapacity)
                    put("crossRackBandwidth", config.realtime.scheduling.crossRackBandwidth)
                    put("crossRegionBandwidth", config.realtime.scheduling.crossRegionBandwidth)
                    put("dataLocalityPolicy", config.realtime.scheduling.dataLocalityPolicy)
                    put("imageCacheCapacity", config.realtime.scheduling.imageCacheCapacity)
                }
            }
            putJsonObject("csv") {
                put("enabled", resolved.output.csvEnabled)
                put("delimiter", resolved.output.csvDelimiter)
            }
            putJsonArray("taskCounts") {
                resolved.taskCounts.forEach { add(JsonPrimitive(it)) }
            }
        }
        return prettyJson.encodeToString(JsonObject.serializer(), json)
    }

    fun printUsage() {
        Logger.info("CloudSim-Benchmark CLI")
        Logger.info("")
        Logger.info("用法:")
        Logger.info("  cloudsim run --mode batch|realtime|batch-multi|realtime-multi [options]")
        Logger.info("  cloudsim list algorithms --mode batch|realtime")
        Logger.info("  cloudsim list profiles --config FILE")
        Logger.info("  cloudsim list presets --config FILE")
        Logger.info("  cloudsim config validate --config FILE")
        Logger.info("  cloudsim config print --config FILE [--profile NAME]")
        Logger.info("")
        Logger.info("run 选项:")
        Logger.info("  --mode MODE                      运行模式（可省略，交给 profile 决定）")
        Logger.info("  --algorithms, -a ALGO1,ALGO2     算法列表，或 ALL")
        Logger.info("  --preset NAME                    使用配置文件中的预设；与 --algorithms 互斥")
        Logger.info("  --profile, -p NAME               选择配置文件中的 profile")
        Logger.info("  --seed, -s SEED                  随机数种子")
        Logger.info("  --runs, -r COUNT                 运行次数")
        Logger.info("  --tasks, -t COUNT1,COUNT2        multi 模式任务数列表")
        Logger.info("  --config, -c FILE                配置文件")
        Logger.info("  --output, -o DIR                 输出目录")
        Logger.info("  --sequential, -S                 顺序执行")
        Logger.info("  --concurrency, -C NUM            最大协程并发数")
        Logger.info("  --dry-run                        只打印解析后的配置，不创建结果")
        Logger.info("")
        Logger.info("示例:")
        Logger.info("  cloudsim run --config configs/examples/single_config_example.toml --profile batch_small --dry-run")
        Logger.info("  cloudsim run --mode batch -a RANDOM,PSO -r 3 -s 42 -o runs/demo")
        Logger.info("  cloudsim run --mode batch-multi --tasks 50,100,200 -a ALL --concurrency 4")
        Logger.info("  cloudsim list profiles --config configs/examples/single_config_example.toml")
    }
}
