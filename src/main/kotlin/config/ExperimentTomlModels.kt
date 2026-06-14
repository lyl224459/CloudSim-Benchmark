package config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExperimentTomlConfig(
    val defaultProfile: String? = null,
    val mode: String? = null,
    val random: RandomConfig? = null,
    val batch: TomlBatchConfig? = null,
    @SerialName("batch_multi")
    val batchMulti: TomlBatchConfig? = null,
    val realtime: TomlRealtimeConfig? = null,
    @SerialName("realtime_multi")
    val realtimeMulti: TomlRealtimeConfig? = null,
    val optimizer: TomlOptimizerConfig? = null,
    val algorithms: Map<String, AlgorithmConfig> = emptyMap(),
    val presets: Map<String, PresetConfig> = emptyMap(),
    val profiles: Map<String, ProfileConfig> = emptyMap(),
)

@Serializable
data class CloudSimBenchmarkTomlConfig(
    val output: OutputTomlConfig? = null,
    val logging: LoggingTomlConfig? = null,
    val experiment: SystemExperimentTomlConfig? = null,
    val jvm: JvmTomlConfig? = null,
    val defaultProfile: String? = null,
    val mode: String? = null,
    val random: RandomConfig? = null,
    val batch: TomlBatchConfig? = null,
    @SerialName("batch_multi")
    val batchMulti: TomlBatchConfig? = null,
    val realtime: TomlRealtimeConfig? = null,
    @SerialName("realtime_multi")
    val realtimeMulti: TomlRealtimeConfig? = null,
    val optimizer: TomlOptimizerConfig? = null,
    val algorithms: Map<String, AlgorithmConfig> = emptyMap(),
    val presets: Map<String, PresetConfig> = emptyMap(),
    val profiles: Map<String, ProfileConfig> = emptyMap(),
) {
    fun toSystemTomlConfig(): SystemTomlConfig =
        SystemTomlConfig(
            output = output,
            logging = logging,
            experiment = experiment,
            jvm = jvm,
        )

    fun toExperimentTomlConfig(): ExperimentTomlConfig =
        ExperimentTomlConfig(
            defaultProfile = defaultProfile,
            mode = mode,
            random = random,
            batch = batch,
            batchMulti = batchMulti,
            realtime = realtime,
            realtimeMulti = realtimeMulti,
            optimizer = optimizer,
            algorithms = algorithms,
            presets = presets,
            profiles = profiles,
        )
}

@Serializable
data class AlgorithmConfig(
    val enabled: Boolean = true,
    val description: String = "",
    val population: Int? = null,
    val maxIter: Int? = null,
)

@Serializable
data class PresetConfig(
    val algorithms: List<String> = emptyList(),
)

@Serializable
data class ProfileConfig(
    val mode: String = "",
    val algorithms: List<String> = emptyList(),
    val preset: String? = null,
    val runs: Int? = null,
    val seed: Long? = null,
    val tasks: List<Int> = emptyList(),
    val outputDir: String? = null,
    val batch: TomlBatchConfig? = null,
    val realtime: TomlRealtimeConfig? = null,
)

@Serializable
data class RandomConfig(
    val seed: Long = 0L,
)

@Serializable
data class TomlBatchConfig(
    val cloudletCount: Int = 100,
    val cloudletCounts: List<Int> = emptyList(),
    val population: Int = 30,
    val maxIter: Int = 50,
    val runs: Int = 1,
    val generator: GeneratorConfig = GeneratorConfig.LOG_NORMAL,
    val objective: ObjectiveWeightsConfig = ObjectiveWeightsConfig(),
    val generatorType: String = "LOG_NORMAL",
    val googleTrace: GoogleTraceConfig? = null,
)

@Serializable
data class TomlRealtimeConfig(
    val cloudletCount: Int = 200,
    val cloudletCounts: List<Int> = emptyList(),
    val simulationDuration: Double = 500.0,
    val arrivalRate: Double = 5.0,
    val runs: Int = 1,
    val generator: GeneratorConfig = GeneratorConfig.LOG_NORMAL,
    val objective: ObjectiveWeightsConfig = ObjectiveWeightsConfig(),
    val arrival: RealtimeArrivalConfig = RealtimeArrivalConfig(),
    val scheduling: RealtimeSchedulingConfig = RealtimeSchedulingConfig(),
    val generatorType: String = "LOG_NORMAL",
    val googleTrace: GoogleTraceConfig? = null,
)

@Serializable
data class RealtimeArrivalConfig(
    val distribution: String = "poisson",
    val burstIntensity: Double = 2.0,
    val burstDuration: Double = 50.0,
)

@Serializable
data class RealtimeSchedulingConfig(
    val strategy: String = "dynamic",
    val maxQueueSize: Int = Int.MAX_VALUE,
    val taskTimeout: Double = 0.0,
    val resourceReservation: String = "none",
    val decisionDelay: Double = 0.0,
    val decisionJitter: Double = 0.0,
    val failureRate: Double = 0.0,
    val retryLimit: Int = 0,
    val retryDelay: Double = 0.0,
    val retryBackoffMultiplier: Double = 1.0,
    val queuePolicy: String = "fifo",
    val priorityLevels: Int = 1,
    val highPriorityRatio: Double = 0.0,
    val deadlineFactor: Double = 0.0,
    val vmQueueCapacity: Int = 0,
    val overloadFailureMultiplier: Double = 0.0,
    val autoscalingEnabled: Boolean = false,
    val scaleOutQueueThreshold: Int = 0,
    val scaleInIdleTime: Double = 0.0,
    val maxDynamicVms: Int = 0,
    val vmColdStartDelay: Double = 0.0,
    val scaleOutCost: Double = 0.0,
    val scaleInProtectionTime: Double = 0.0,
    val resourceModelEnabled: Boolean = false,
    val networkLatency: Double = 0.0,
    val imagePullDelay: Double = 0.0,
    val ioWeight: Double = 0.0,
    val ramWeight: Double = 0.0,
    val bwWeight: Double = 0.0,
    val runtimeFailureRate: Double = 0.0,
    val nodeFailureRate: Double = 0.0,
    val checkpointInterval: Double = 0.0,
    val migrationDelay: Double = 0.0,
    val timeoutAction: String = "fail",
    val preemptionEnabled: Boolean = false,
    val preemptionPolicy: String = "priority_then_deadline",
    val preemptionMinPriorityGap: Int = 1,
    val preemptionMaxPerTask: Int = 1,
    val preemptionDelay: Double = 0.0,
    val preemptionPenalty: Double = 0.0,
    val multiTenantEnabled: Boolean = false,
    val tenantCount: Int = 1,
    val tenantQuota: List<Int> = emptyList(),
    val tenantWeights: List<Double> = emptyList(),
    val tenantFairnessPolicy: String = "quota_first",
    val tenantSchedulingPolicy: String = "quota_first",
    val tenantBurstAllowance: Int = 0,
    val tenantSlaPenaltyWeight: Double = 1.0,
    val tenantCostBudget: List<Double> = emptyList(),
    val topologyEnabled: Boolean = false,
    val topologyPolicy: String = "latency_aware",
    val regionCount: Int = 3,
    val racksPerRegion: Int = 2,
    val hostsPerRack: Int = 2,
    val localRegion: Int = 0,
    val crossRackLatency: Double = 0.1,
    val crossRegionLatency: Double = 1.0,
    val crossRegionCost: Double = 0.0,
    val hostFailureRate: Double = 0.0,
    val rackFailureRate: Double = 0.0,
    val regionFailureRate: Double = 0.0,
    val physicalTopologyEnabled: Boolean = false,
    val dataLocalityEnabled: Boolean = false,
    val imageCacheEnabled: Boolean = false,
    val hostCountPerRack: Int = 2,
    val hostCpuCapacity: Double = 0.0,
    val hostRamCapacity: Double = 0.0,
    val hostBwCapacity: Double = 0.0,
    val hostIoCapacity: Double = 0.0,
    val crossRackBandwidth: Double = 0.0,
    val crossRegionBandwidth: Double = 0.0,
    val dataLocalityPolicy: String = "prefer_local",
    val imageCacheCapacity: Int = 0,
) {
    fun normalizedQueuePolicy(): RealtimeQueuePolicy = RealtimeQueuePolicy.parse(queuePolicy)

    fun normalizedTimeoutAction(): RealtimeTimeoutAction = RealtimeTimeoutAction.parse(timeoutAction)

    fun normalizedPreemptionPolicy(): RealtimePreemptionPolicy = RealtimePreemptionPolicy.parse(preemptionPolicy)

    fun normalizedTenantFairnessPolicy() = RealtimeTenantFairnessPolicy.parse(tenantFairnessPolicy)

    fun normalizedTenantSchedulingPolicy() = TenantSchedulingPolicy.parse(tenantSchedulingPolicy)

    fun normalizedTopologyPolicy(): RealtimeTopologyPolicy = RealtimeTopologyPolicy.parse(topologyPolicy)

    fun normalizedDataLocalityPolicy(): DataLocalityPolicy = DataLocalityPolicy.parse(dataLocalityPolicy)
}

@Serializable
data class GoogleTraceConfig(
    val filePath: String = "data/google_trace/task_events.csv",
    val maxTasks: Int = 1000,
    val timeWindowStart: Long = 0L,
    val timeWindowEnd: Long = Long.MAX_VALUE,
)

@Serializable
data class GeneratorConfig(
    val type: String = "LOG_NORMAL",
) {
    companion object {
        val LOG_NORMAL = GeneratorConfig(type = "LOG_NORMAL")
        val UNIFORM = GeneratorConfig(type = "UNIFORM")
        val GOOGLE_TRACE = GeneratorConfig(type = "GOOGLE_TRACE")
    }
}

@Serializable
data class TomlOptimizerConfig(
    val population: Int = 20,
    val maxIter: Int = 20,
)
