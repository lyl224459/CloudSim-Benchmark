package config

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.TomlOutputConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlinx.serialization.modules.EmptySerializersModule
import util.Logger
import java.io.File

/**
 * 配置验证异常
 * 包含详细的验证错误信息
 */
class ConfigValidationException(
    message: String,
    val errors: List<ValidationError>,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause) {

    override fun toString(): String {
        val errorDetails = if (errors.isNotEmpty()) {
            "\n详细错误信息:\n" + errors.joinToString("\n") { "  - ${it.field}: ${it.message} (当前值: ${it.value})" }
        } else {
            ""
        }
        return "${super.toString()}$errorDetails"
    }
}

/**
 * 验证错误信息
 */
data class ValidationError(
    val field: String,      // 字段名
    val value: String,      // 当前值
    val message: String     // 错误消息
)

/**
 * 实验模式枚举
 */
enum class ExperimentMode {
    BATCH,
    REALTIME,
    BATCH_MULTI,
    REALTIME_MULTI
}

/**
 * TOML格式的实验配置
 */
@Serializable
data class ExperimentTomlConfig(
    val defaultProfile: String? = null,
    val mode: String? = null,  // 实验模式
    val random: RandomConfig? = null,
    val batch: TomlBatchConfig? = null,
    val batch_multi: TomlBatchConfig? = null,
    val realtime: TomlRealtimeConfig? = null,
    val realtime_multi: TomlRealtimeConfig? = null,
    val optimizer: TomlOptimizerConfig? = null,
    // 算法配置
    @kotlinx.serialization.Transient
    val algorithms: Map<String, AlgorithmConfig> = emptyMap(),
    // 预设配置
    @kotlinx.serialization.Transient
    val presets: Map<String, PresetConfig> = emptyMap(),
    @kotlinx.serialization.Transient
    val profiles: Map<String, ProfileConfig> = emptyMap()
)

@Serializable
data class AlgorithmConfig(
    val enabled: Boolean = true,
    val description: String = "",
    val population: Int? = null,
    val maxIter: Int? = null
)

@Serializable
data class PresetConfig(
    val algorithms: List<String> = emptyList()
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
    val realtime: TomlRealtimeConfig? = null
)

@Serializable
data class RandomConfig(
    val seed: Long = 0L
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
    // 向后兼容
    val generatorType: String = "LOG_NORMAL",
    val googleTrace: GoogleTraceConfig? = null
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
    // 向后兼容
    val generatorType: String = "LOG_NORMAL",
    val googleTrace: GoogleTraceConfig? = null
)

@Serializable
data class RealtimeArrivalConfig(
    val distribution: String = "poisson",
    val burstIntensity: Double = 2.0,
    val burstDuration: Double = 50.0
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
    val tenantFairnessPolicy: String = "quota_first"
) {
    fun normalizedQueuePolicy(): RealtimeQueuePolicy = RealtimeQueuePolicy.parse(queuePolicy)

    fun normalizedTimeoutAction(): RealtimeTimeoutAction = RealtimeTimeoutAction.parse(timeoutAction)

    fun normalizedPreemptionPolicy(): RealtimePreemptionPolicy = RealtimePreemptionPolicy.parse(preemptionPolicy)

    fun normalizedTenantFairnessPolicy(): RealtimeTenantFairnessPolicy =
        RealtimeTenantFairnessPolicy.parse(tenantFairnessPolicy)
}

enum class RealtimeQueuePolicy(val configValue: String) {
    FIFO("fifo"),
    PRIORITY("priority"),
    DEADLINE("deadline");

    companion object {
        fun parse(value: String): RealtimeQueuePolicy =
            entries.firstOrNull { it.configValue.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("未知实时队列策略: $value")

        fun valuesForConfig(): Set<String> = entries.map { it.configValue }.toSet()
    }
}

enum class RealtimeTimeoutAction(val configValue: String) {
    FAIL("fail"),
    RETRY("retry"),
    CANCEL("cancel"),
    DEGRADE("degrade");

    companion object {
        fun parse(value: String): RealtimeTimeoutAction =
            entries.firstOrNull { it.configValue.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("未知实时超时动作: $value")

        fun valuesForConfig(): Set<String> = entries.map { it.configValue }.toSet()
    }
}

enum class RealtimePreemptionPolicy(val configValue: String) {
    PRIORITY_THEN_DEADLINE("priority_then_deadline"),
    DEADLINE_THEN_PRIORITY("deadline_then_priority");

    companion object {
        fun parse(value: String): RealtimePreemptionPolicy =
            entries.firstOrNull { it.configValue.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("未知实时抢占策略: $value")

        fun valuesForConfig(): Set<String> = entries.map { it.configValue }.toSet()
    }
}

enum class RealtimeTenantFairnessPolicy(val configValue: String) {
    QUOTA_FIRST("quota_first"),
    WEIGHTED_FAIR("weighted_fair");

    companion object {
        fun parse(value: String): RealtimeTenantFairnessPolicy =
            entries.firstOrNull { it.configValue.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("未知实时租户公平策略: $value")

        fun valuesForConfig(): Set<String> = entries.map { it.configValue }.toSet()
    }
}

@Serializable
data class GoogleTraceConfig(
    val filePath: String = "data/google_trace/task_events.csv",
    val maxTasks: Int = 1000,
    val timeWindowStart: Long = 0L,
    val timeWindowEnd: Long = Long.MAX_VALUE
)

@Serializable
data class GeneratorConfig(
    val type: String = "LOG_NORMAL"  // 生成器类型
) {
    // 预定义参数配置
    companion object {
        val LOG_NORMAL = GeneratorConfig(type = "LOG_NORMAL")
        val UNIFORM = GeneratorConfig(type = "UNIFORM")
        val GOOGLE_TRACE = GeneratorConfig(type = "GOOGLE_TRACE")
    }
}

@Serializable
data class TomlOptimizerConfig(
    val population: Int = 20,
    val maxIter: Int = 20
)

/**
 * 实验配置类
 * 专门管理实验参数，如任务数量、算法参数、目标函数等
 */
data class ExperimentConfig(
    val defaultProfile: String? = null,

    val profiles: Map<String, ProfileConfig> = emptyMap(),

    // ========== 实验模式 ==========
    val mode: ExperimentMode = ExperimentMode.BATCH,
    
    // ========== 批处理模式配置 ==========
    val batch: BatchConfig = BatchConfig(),

    // ========== 实时调度模式配置 ==========
    val realtime: RealtimeConfig = RealtimeConfig(),

    // ========== 通用配置 ==========
    val randomSeed: Long = 0L,

    // ========== 优化算法配置 ==========
    val optimizer: OptimizerConfig = OptimizerConfig(),

    // ========== 算法与预设配置 ==========
    val algorithmConfigs: Map<String, AlgorithmConfig> = emptyMap(),
    val presets: Map<String, PresetConfig> = emptyMap()
) {
    companion object {
        /**
         * 从配置文件加载实验配置
         * 优先级：指定配置文件 > 默认配置
         */
        fun load(configPath: String): ExperimentConfig {
            val config = loadInternal(configPath, requireProfiles = true)
            validateConfig(config, requireProfiles = true)
            return config
        }

        /**
         * 加载算法库或仅包含算法/预设的配置片段。
         */
        fun loadLibrary(configPath: String): ExperimentConfig {
            val config = loadInternal(configPath, requireProfiles = false)
            validateConfig(config)
            return config
        }

        /**
         * 提供默认配置（供测试/外部调用）
         */
        fun createDefault(): ExperimentConfig = ExperimentConfig()

        /**
         * 对外公开的配置验证入口（供测试/外部调用）
         * @throws ConfigValidationException 当配置无效时
         */
        fun validate(config: ExperimentConfig) = validateConfig(config)

        /**
         * 解析生成器类型字符串（对外公开，方便测试/调用）
         */
        fun parseGeneratorType(type: String): CloudletGeneratorType = try {
            CloudletGeneratorType.valueOf(type.uppercase())
        } catch (e: IllegalArgumentException) {
            Logger.warn("未知的生成器类型: {}, 使用默认值 LOG_NORMAL", type)
            CloudletGeneratorType.LOG_NORMAL
        }

        /**
         * 解析实验模式
         */
        fun parseExperimentMode(mode: String): ExperimentMode = try {
            ExperimentMode.valueOf(mode.uppercase().replace("-", "_"))
        } catch (e: IllegalArgumentException) {
            Logger.warn("未知的实验模式: {}, 使用默认值 BATCH", mode)
            ExperimentMode.BATCH
        }

        /**
         * 内部加载方法（不验证）
         */
        private fun loadInternal(configPath: String, requireProfiles: Boolean): ExperimentConfig {
            try {
                val file = File(configPath)
                if (!file.exists()) {
                    throw IllegalArgumentException("配置文件不存在: $configPath")
                }

                val tomlContent = file.readText()
                val tomlConfig = Toml(
                    TomlInputConfig(ignoreUnknownNames = true),
                    TomlOutputConfig(),
                    EmptySerializersModule()
                ).decodeFromString(serializer<ExperimentTomlConfig>(), tomlContent)

                val profiles = parseProfileConfigs(tomlContent)
                if (profiles.isEmpty()) {
                    if (tomlConfig.mode != null || tomlConfig.batch != null || tomlConfig.realtime != null) {
                        throw IllegalArgumentException("旧顶层实验 schema 已废弃，请迁移到 [profiles.NAME]")
                    }
                    if (requireProfiles) {
                        throw IllegalArgumentException("未找到 profiles 配置，请至少定义一个 [profiles.NAME]")
                    }
                }

                return ExperimentConfig(
                    defaultProfile = tomlConfig.defaultProfile,
                    profiles = profiles,
                    randomSeed = tomlConfig.random?.seed ?: 0L,
                    optimizer = mergeOptimizerConfig(OptimizerConfig(), tomlConfig.optimizer),
                    algorithmConfigs = parseAlgorithmConfigs(tomlContent),
                    presets = parsePresetConfigs(tomlContent)
                )
            } catch (e: Exception) {
                Logger.error("加载实验配置时发生错误: ${e.message}", e)
                throw e
            }
        }

        /**
         * 合并批处理配置
         */
        internal fun mergeBatchConfig(base: BatchConfig, toml: TomlBatchConfig?): BatchConfig {
            if (toml == null) return base

            // 解析生成器配置（新格式优先）
            val generatorType = if (toml.generator.type != "LOG_NORMAL") {
                parseGeneratorType(toml.generator.type)
            } else {
                parseGeneratorType(toml.generatorType) // 向后兼容
            }

            // 解析目标函数权重
            val objectiveWeights = toml.objective

            return base.copy(
                cloudletCount = toml.cloudletCount,
                cloudletCounts = toml.cloudletCounts,
                population = toml.population,
                maxIter = toml.maxIter,
                runs = toml.runs,
                generatorType = generatorType,
                googleTraceConfig = toml.googleTrace ?: base.googleTraceConfig,
                objectiveWeights = objectiveWeights
            )
        }

        /**
         * 合并实时调度配置
         */
        internal fun mergeRealtimeConfig(base: RealtimeConfig, toml: TomlRealtimeConfig?): RealtimeConfig {
            if (toml == null) return base

            // 解析生成器配置（新格式优先）
            val generatorType = if (toml.generator.type != "LOG_NORMAL") {
                parseGeneratorType(toml.generator.type)
            } else {
                parseGeneratorType(toml.generatorType) // 向后兼容
            }

            // 解析目标函数权重
            val objectiveWeights = toml.objective

            return base.copy(
                cloudletCount = toml.cloudletCount,
                simulationDuration = toml.simulationDuration,
                arrivalRate = toml.arrivalRate,
                cloudletCounts = toml.cloudletCounts,
                runs = toml.runs,
                generatorType = generatorType,
                googleTraceConfig = toml.googleTrace ?: base.googleTraceConfig,
                objectiveWeights = objectiveWeights,
                arrival = toml.arrival,
                scheduling = toml.scheduling
            )
        }

        private fun buildBatchToml(properties: Map<String, String>, base: TomlBatchConfig?): TomlBatchConfig {
            if (properties.isEmpty()) return base ?: TomlBatchConfig()
            val current = base ?: TomlBatchConfig()
            return TomlBatchConfig(
                cloudletCount = properties["cloudletCount"]?.let { parseRequiredInt("batch.cloudletCount", it) } ?: current.cloudletCount,
                cloudletCounts = properties["cloudletCounts"]?.let { parseIntArrayValue(it) } ?: current.cloudletCounts,
                population = properties["population"]?.let { parseRequiredInt("batch.population", it) } ?: current.population,
                maxIter = properties["maxIter"]?.let { parseRequiredInt("batch.maxIter", it) } ?: current.maxIter,
                runs = properties["runs"]?.let { parseRequiredInt("batch.runs", it) } ?: current.runs,
                generator = current.generator,
                objective = current.objective,
                generatorType = properties["generatorType"]?.let { TomlSectionParser.unquote(it) } ?: current.generatorType,
                googleTrace = current.googleTrace
            )
        }

        private fun buildRealtimeToml(properties: Map<String, String>, base: TomlRealtimeConfig?): TomlRealtimeConfig {
            if (properties.isEmpty()) return base ?: TomlRealtimeConfig()
            val current = base ?: TomlRealtimeConfig()
            return TomlRealtimeConfig(
                cloudletCount = properties["cloudletCount"]?.let { parseRequiredInt("realtime.cloudletCount", it) } ?: current.cloudletCount,
                cloudletCounts = properties["cloudletCounts"]?.let { parseIntArrayValue(it) } ?: current.cloudletCounts,
                simulationDuration = properties["simulationDuration"]?.let { parseRequiredDouble("realtime.simulationDuration", it) } ?: current.simulationDuration,
                arrivalRate = properties["arrivalRate"]?.let { parseRequiredDouble("realtime.arrivalRate", it) } ?: current.arrivalRate,
                runs = properties["runs"]?.let { parseRequiredInt("realtime.runs", it) } ?: current.runs,
                generator = current.generator,
                objective = current.objective,
                arrival = current.arrival,
                scheduling = current.scheduling,
                generatorType = properties["generatorType"]?.let { TomlSectionParser.unquote(it) } ?: current.generatorType,
                googleTrace = current.googleTrace
            )
        }

        fun normalizeAlgorithmName(name: String): String =
            name.trim().replace("-", "_").replace(" ", "_").uppercase()

        private fun parseProfileConfigs(content: String): Map<String, ProfileConfig> {
            val profiles = linkedMapOf<String, ProfileConfig>()
            val sections = TomlSectionParser.parse(content).filter { it.path.firstOrNull() == "profiles" }

            for (section in sections) {
                if (section.path.size !in 2..4) {
                    throw IllegalArgumentException("未知 profile 配置段: [${section.name}]")
                }

                val profileName = section.path[1]
                val profile = profiles[profileName] ?: ProfileConfig()
                val updated = when (section.path.size) {
                    2 -> applyProfileRoot(profileName, profile, section.values)
                    3 -> when (section.path[2]) {
                        "batch" -> profile.copy(batch = buildBatchToml(section.values, profile.batch))
                        "realtime" -> profile.copy(realtime = buildRealtimeToml(section.values, profile.realtime))
                        else -> throw IllegalArgumentException("未知 profile 配置段: [${section.name}]")
                    }
                    4 -> when (section.path[2] to section.path[3]) {
                        "batch" to "objective" -> profile.copy(batch = applyBatchObjective(section.values, profile.batch))
                        "realtime" to "objective" -> profile.copy(realtime = applyRealtimeObjective(section.values, profile.realtime))
                        "realtime" to "arrival" -> profile.copy(realtime = applyRealtimeArrival(section.values, profile.realtime))
                        "realtime" to "scheduling" -> profile.copy(realtime = applyRealtimeScheduling(section.values, profile.realtime))
                        else -> throw IllegalArgumentException("未知 profile 配置段: [${section.name}]")
                    }
                    else -> profile
                }
                profiles[profileName] = updated
            }

            return profiles
        }

        private fun applyProfileRoot(
            profileName: String,
            profile: ProfileConfig,
            properties: Map<String, String>
        ): ProfileConfig {
            validateKeys("profiles.$profileName", properties, setOf("mode", "algorithms", "preset", "runs", "seed", "tasks", "outputDir"))
            return profile.copy(
                mode = properties["mode"]?.let { TomlSectionParser.unquote(it) } ?: profile.mode,
                preset = properties["preset"]?.let { TomlSectionParser.unquote(it) } ?: profile.preset,
                runs = properties["runs"]?.let { parseRequiredInt("profiles.$profileName.runs", it) } ?: profile.runs,
                seed = properties["seed"]?.let { parseRequiredLong("profiles.$profileName.seed", it) } ?: profile.seed,
                tasks = properties["tasks"]?.let { parseIntArrayValue(it) } ?: profile.tasks,
                outputDir = properties["outputDir"]?.let { TomlSectionParser.unquote(it) } ?: profile.outputDir,
                algorithms = properties["algorithms"]?.let { parseArrayValue(it) } ?: profile.algorithms
            )
        }

        private fun applyBatchObjective(properties: Map<String, String>, base: TomlBatchConfig?): TomlBatchConfig {
            validateKeys("profiles.*.batch.objective", properties, setOf("cost", "totalTime", "loadBalance", "makespan"))
            val current = base ?: TomlBatchConfig()
            return current.copy(objective = buildObjectiveWeights(properties, current.objective, "batch.objective"))
        }

        private fun applyRealtimeObjective(properties: Map<String, String>, base: TomlRealtimeConfig?): TomlRealtimeConfig {
            validateKeys("profiles.*.realtime.objective", properties, setOf("cost", "totalTime", "loadBalance", "makespan"))
            val current = base ?: TomlRealtimeConfig()
            return current.copy(objective = buildObjectiveWeights(properties, current.objective, "realtime.objective"))
        }

        private fun applyRealtimeArrival(properties: Map<String, String>, base: TomlRealtimeConfig?): TomlRealtimeConfig {
            validateKeys("profiles.*.realtime.arrival", properties, setOf("distribution", "burstIntensity", "burstDuration"))
            val current = base ?: TomlRealtimeConfig()
            val arrival = current.arrival.copy(
                distribution = properties["distribution"]?.let { TomlSectionParser.unquote(it) } ?: current.arrival.distribution,
                burstIntensity = properties["burstIntensity"]?.let { parseRequiredDouble("realtime.arrival.burstIntensity", it) } ?: current.arrival.burstIntensity,
                burstDuration = properties["burstDuration"]?.let { parseRequiredDouble("realtime.arrival.burstDuration", it) } ?: current.arrival.burstDuration
            )
            return current.copy(arrival = arrival)
        }

        private fun applyRealtimeScheduling(properties: Map<String, String>, base: TomlRealtimeConfig?): TomlRealtimeConfig {
            validateKeys(
                "profiles.*.realtime.scheduling",
                properties,
                setOf(
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
                    "scaleInIdleTime",
                    "maxDynamicVms",
                    "vmColdStartDelay",
                    "scaleOutCost",
                    "scaleInProtectionTime",
                    "resourceModelEnabled",
                    "networkLatency",
                    "imagePullDelay",
                    "ioWeight",
                    "ramWeight",
                    "bwWeight",
                    "runtimeFailureRate",
                    "nodeFailureRate",
                    "checkpointInterval",
                    "migrationDelay",
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
                    "tenantFairnessPolicy"
                )
            )
            val current = base ?: TomlRealtimeConfig()
            val scheduling = current.scheduling.copy(
                strategy = properties["strategy"]?.let { TomlSectionParser.unquote(it) } ?: current.scheduling.strategy,
                maxQueueSize = properties["maxQueueSize"]?.let { parseRequiredInt("realtime.scheduling.maxQueueSize", it) } ?: current.scheduling.maxQueueSize,
                taskTimeout = properties["taskTimeout"]?.let { parseRequiredDouble("realtime.scheduling.taskTimeout", it) } ?: current.scheduling.taskTimeout,
                resourceReservation = properties["resourceReservation"]?.let { TomlSectionParser.unquote(it) } ?: current.scheduling.resourceReservation,
                decisionDelay = properties["decisionDelay"]?.let { parseRequiredDouble("realtime.scheduling.decisionDelay", it) } ?: current.scheduling.decisionDelay,
                decisionJitter = properties["decisionJitter"]?.let { parseRequiredDouble("realtime.scheduling.decisionJitter", it) } ?: current.scheduling.decisionJitter,
                failureRate = properties["failureRate"]?.let { parseRequiredDouble("realtime.scheduling.failureRate", it) } ?: current.scheduling.failureRate,
                retryLimit = properties["retryLimit"]?.let { parseRequiredInt("realtime.scheduling.retryLimit", it) } ?: current.scheduling.retryLimit,
                retryDelay = properties["retryDelay"]?.let { parseRequiredDouble("realtime.scheduling.retryDelay", it) } ?: current.scheduling.retryDelay,
                retryBackoffMultiplier = properties["retryBackoffMultiplier"]?.let {
                    parseRequiredDouble("realtime.scheduling.retryBackoffMultiplier", it)
                } ?: current.scheduling.retryBackoffMultiplier,
                queuePolicy = properties["queuePolicy"]?.let { TomlSectionParser.unquote(it) } ?: current.scheduling.queuePolicy,
                priorityLevels = properties["priorityLevels"]?.let { parseRequiredInt("realtime.scheduling.priorityLevels", it) } ?: current.scheduling.priorityLevels,
                highPriorityRatio = properties["highPriorityRatio"]?.let {
                    parseRequiredDouble("realtime.scheduling.highPriorityRatio", it)
                } ?: current.scheduling.highPriorityRatio,
                deadlineFactor = properties["deadlineFactor"]?.let { parseRequiredDouble("realtime.scheduling.deadlineFactor", it) } ?: current.scheduling.deadlineFactor,
                vmQueueCapacity = properties["vmQueueCapacity"]?.let { parseRequiredInt("realtime.scheduling.vmQueueCapacity", it) } ?: current.scheduling.vmQueueCapacity,
                overloadFailureMultiplier = properties["overloadFailureMultiplier"]?.let {
                    parseRequiredDouble("realtime.scheduling.overloadFailureMultiplier", it)
                } ?: current.scheduling.overloadFailureMultiplier,
                autoscalingEnabled = properties["autoscalingEnabled"]?.let { parseRequiredBoolean("realtime.scheduling.autoscalingEnabled", it) } ?: current.scheduling.autoscalingEnabled,
                scaleOutQueueThreshold = properties["scaleOutQueueThreshold"]?.let {
                    parseRequiredInt("realtime.scheduling.scaleOutQueueThreshold", it)
                } ?: current.scheduling.scaleOutQueueThreshold,
                scaleInIdleTime = properties["scaleInIdleTime"]?.let { parseRequiredDouble("realtime.scheduling.scaleInIdleTime", it) } ?: current.scheduling.scaleInIdleTime,
                maxDynamicVms = properties["maxDynamicVms"]?.let { parseRequiredInt("realtime.scheduling.maxDynamicVms", it) } ?: current.scheduling.maxDynamicVms,
                vmColdStartDelay = properties["vmColdStartDelay"]?.let {
                    parseRequiredDouble("realtime.scheduling.vmColdStartDelay", it)
                } ?: current.scheduling.vmColdStartDelay,
                scaleOutCost = properties["scaleOutCost"]?.let { parseRequiredDouble("realtime.scheduling.scaleOutCost", it) } ?: current.scheduling.scaleOutCost,
                scaleInProtectionTime = properties["scaleInProtectionTime"]?.let {
                    parseRequiredDouble("realtime.scheduling.scaleInProtectionTime", it)
                } ?: current.scheduling.scaleInProtectionTime,
                resourceModelEnabled = properties["resourceModelEnabled"]?.let {
                    parseRequiredBoolean("realtime.scheduling.resourceModelEnabled", it)
                } ?: current.scheduling.resourceModelEnabled,
                networkLatency = properties["networkLatency"]?.let { parseRequiredDouble("realtime.scheduling.networkLatency", it) } ?: current.scheduling.networkLatency,
                imagePullDelay = properties["imagePullDelay"]?.let { parseRequiredDouble("realtime.scheduling.imagePullDelay", it) } ?: current.scheduling.imagePullDelay,
                ioWeight = properties["ioWeight"]?.let { parseRequiredDouble("realtime.scheduling.ioWeight", it) } ?: current.scheduling.ioWeight,
                ramWeight = properties["ramWeight"]?.let { parseRequiredDouble("realtime.scheduling.ramWeight", it) } ?: current.scheduling.ramWeight,
                bwWeight = properties["bwWeight"]?.let { parseRequiredDouble("realtime.scheduling.bwWeight", it) } ?: current.scheduling.bwWeight,
                runtimeFailureRate = properties["runtimeFailureRate"]?.let {
                    parseRequiredDouble("realtime.scheduling.runtimeFailureRate", it)
                } ?: current.scheduling.runtimeFailureRate,
                nodeFailureRate = properties["nodeFailureRate"]?.let { parseRequiredDouble("realtime.scheduling.nodeFailureRate", it) } ?: current.scheduling.nodeFailureRate,
                checkpointInterval = properties["checkpointInterval"]?.let {
                    parseRequiredDouble("realtime.scheduling.checkpointInterval", it)
                } ?: current.scheduling.checkpointInterval,
                migrationDelay = properties["migrationDelay"]?.let { parseRequiredDouble("realtime.scheduling.migrationDelay", it) } ?: current.scheduling.migrationDelay,
                timeoutAction = properties["timeoutAction"]?.let { TomlSectionParser.unquote(it) } ?: current.scheduling.timeoutAction,
                preemptionEnabled = properties["preemptionEnabled"]?.let {
                    parseRequiredBoolean("realtime.scheduling.preemptionEnabled", it)
                } ?: current.scheduling.preemptionEnabled,
                preemptionPolicy = properties["preemptionPolicy"]?.let { TomlSectionParser.unquote(it) } ?: current.scheduling.preemptionPolicy,
                preemptionMinPriorityGap = properties["preemptionMinPriorityGap"]?.let {
                    parseRequiredInt("realtime.scheduling.preemptionMinPriorityGap", it)
                } ?: current.scheduling.preemptionMinPriorityGap,
                preemptionMaxPerTask = properties["preemptionMaxPerTask"]?.let {
                    parseRequiredInt("realtime.scheduling.preemptionMaxPerTask", it)
                } ?: current.scheduling.preemptionMaxPerTask,
                preemptionDelay = properties["preemptionDelay"]?.let {
                    parseRequiredDouble("realtime.scheduling.preemptionDelay", it)
                } ?: current.scheduling.preemptionDelay,
                preemptionPenalty = properties["preemptionPenalty"]?.let {
                    parseRequiredDouble("realtime.scheduling.preemptionPenalty", it)
                } ?: current.scheduling.preemptionPenalty,
                multiTenantEnabled = properties["multiTenantEnabled"]?.let {
                    parseRequiredBoolean("realtime.scheduling.multiTenantEnabled", it)
                } ?: current.scheduling.multiTenantEnabled,
                tenantCount = properties["tenantCount"]?.let {
                    parseRequiredInt("realtime.scheduling.tenantCount", it)
                } ?: current.scheduling.tenantCount,
                tenantQuota = properties["tenantQuota"]?.let {
                    parseIntArrayValue(it)
                } ?: current.scheduling.tenantQuota,
                tenantWeights = properties["tenantWeights"]?.let {
                    parseDoubleArrayValue(it)
                } ?: current.scheduling.tenantWeights,
                tenantFairnessPolicy = properties["tenantFairnessPolicy"]?.let {
                    TomlSectionParser.unquote(it)
                } ?: current.scheduling.tenantFairnessPolicy
            )
            return current.copy(scheduling = scheduling)
        }

        private fun buildObjectiveWeights(
            properties: Map<String, String>,
            current: ObjectiveWeightsConfig,
            context: String
        ): ObjectiveWeightsConfig =
            ObjectiveWeightsConfig(
                cost = properties["cost"]?.let { parseRequiredDouble("$context.cost", it) } ?: current.cost,
                totalTime = properties["totalTime"]?.let { parseRequiredDouble("$context.totalTime", it) } ?: current.totalTime,
                loadBalance = properties["loadBalance"]?.let { parseRequiredDouble("$context.loadBalance", it) } ?: current.loadBalance,
                makespan = properties["makespan"]?.let { parseRequiredDouble("$context.makespan", it) } ?: current.makespan
            )

        private fun parseAlgorithmConfigs(content: String): Map<String, AlgorithmConfig> {
            val configs = linkedMapOf<String, AlgorithmConfig>()
            var currentAlgorithm: String? = null
            val properties = linkedMapOf<String, String>()

            fun flush() {
                val algorithm = currentAlgorithm ?: return
                configs[normalizeAlgorithmName(algorithm)] = AlgorithmConfig(
                    enabled = properties["enabled"]?.toBooleanStrictOrNull() ?: true,
                    description = properties["description"]?.trim('"', '\'') ?: "",
                    population = properties["population"]?.let { parseRequiredInt("algorithms.$algorithm.population", it) },
                    maxIter = properties["maxIter"]?.let { parseRequiredInt("algorithms.$algorithm.maxIter", it) }
                )
                properties.clear()
            }

            for (line in content.lineSequence()) {
                val trimmed = line.trim()
                when {
                    trimmed.matches(Regex("""\[algorithms\.[^\]]+]""")) -> {
                        flush()
                        currentAlgorithm = trimmed.removePrefix("[algorithms.").removeSuffix("]")
                    }
                    currentAlgorithm != null && trimmed.startsWith("[") -> {
                        flush()
                        currentAlgorithm = null
                    }
                    currentAlgorithm != null && "=" in trimmed && !trimmed.startsWith("#") -> {
                        val key = trimmed.substringBefore("=").trim()
                        val value = trimmed.substringAfter("=").trim()
                        properties[key] = value
                    }
                }
            }
            flush()
            return configs
        }

        private fun parsePresetConfigs(content: String): Map<String, PresetConfig> {
            val presets = linkedMapOf<String, PresetConfig>()
            var currentPreset: String? = null
            val algorithms = mutableListOf<String>()

            fun flush() {
                val preset = currentPreset ?: return
                presets[preset] = PresetConfig(algorithms = algorithms.toList())
                algorithms.clear()
            }

            for (line in content.lineSequence()) {
                val trimmed = line.trim()
                when {
                    trimmed.matches(Regex("""\[presets\.[^\]]+]""")) -> {
                        flush()
                        currentPreset = trimmed.removePrefix("[presets.").removeSuffix("]")
                    }
                    currentPreset != null && trimmed.startsWith("[") -> {
                        flush()
                        currentPreset = null
                    }
                    currentPreset != null && trimmed.startsWith("algorithms") && "=" in trimmed -> {
                        algorithms.clear()
                        algorithms.addAll(parseArrayValue(trimmed.substringAfter("=")))
                    }
                    currentPreset != null && trimmed.startsWith("-") -> {
                        algorithms.add(trimmed.removePrefix("-").trim().trim('"', '\''))
                    }
                }
            }
            flush()
            return presets
        }

        private fun parseArrayValue(raw: String): List<String> =
            TomlSectionParser.stringList(raw)

        private fun parseIntArrayValue(raw: String): List<Int> =
            TomlSectionParser.intList(raw)

        private fun parseDoubleArrayValue(raw: String): List<Double> =
            TomlSectionParser.doubleList(raw)

        private fun parseBoolArrayValue(raw: String): List<Boolean> =
            parseArrayValue(raw).mapNotNull { it.toBooleanStrictOrNull() }

        private fun parseRequiredInt(field: String, raw: String): Int =
            TomlSectionParser.unquote(raw).toIntOrNull()
                ?: throw IllegalArgumentException("$field 必须是整数: $raw")

        private fun parseRequiredLong(field: String, raw: String): Long =
            TomlSectionParser.unquote(raw).toLongOrNull()
                ?: throw IllegalArgumentException("$field 必须是整数: $raw")

        private fun parseRequiredDouble(field: String, raw: String): Double =
            TomlSectionParser.unquote(raw).toDoubleOrNull()
                ?: throw IllegalArgumentException("$field 必须是数字: $raw")

        private fun parseRequiredBoolean(field: String, raw: String): Boolean =
            TomlSectionParser.unquote(raw).toBooleanStrictOrNull()
                ?: throw IllegalArgumentException("$field 必须是布尔值: $raw")

        private fun validateKeys(context: String, values: Map<String, String>, allowed: Set<String>) {
            val unknown = values.keys.filter { it !in allowed }
            if (unknown.isNotEmpty()) {
                throw IllegalArgumentException("$context 包含未知字段: ${unknown.joinToString(", ")}")
            }
        }

        /**
         * 合并优化器配置
         */
        private fun mergeOptimizerConfig(base: OptimizerConfig, toml: TomlOptimizerConfig?): OptimizerConfig {
            if (toml == null) return base

            return base.copy(
                population = toml.population,
                maxIter = toml.maxIter
            )
        }

        /**
         * 验证配置参数的合理性
         * @throws ConfigValidationException 当配置无效时，包含详细的验证错误信息
         */
        private fun validateConfig(config: ExperimentConfig, requireProfiles: Boolean = false) {
            val errors = mutableListOf<ValidationError>()

            try {
                // 验证批处理配置
                validateBatchConfig(config.batch, errors)

                // 验证实时调度配置
                validateRealtimeConfig(config.realtime, errors)

                // 验证优化算法配置
                validateOptimizerConfig(config.optimizer, errors)

                // 验证算法覆盖配置
                validateAlgorithmConfigs(config.algorithmConfigs, errors)

                // 验证预设配置
                validatePresetConfigs(config.presets, errors)

                // 验证 profiles 配置
                validateProfileConfigs(config, errors, requireProfiles)

                // 验证随机种子
                validateRandomConfig(config.randomSeed, errors)

                // 验证目标函数权重
                validateObjectiveWeights(config.batch.objectiveWeights, "batch", errors)
                validateObjectiveWeights(config.realtime.objectiveWeights, "realtime", errors)

                if (errors.isNotEmpty()) {
                    throw ConfigValidationException("配置验证失败，共发现 ${errors.size} 个错误", errors)
                }

                Logger.debug("配置验证通过")
            } catch (e: ConfigValidationException) {
                Logger.error("配置验证失败: ${e.message}")
                throw e
            } catch (e: Exception) {
                Logger.error("配置验证过程中发生意外错误", e)
                throw ConfigValidationException("配置验证过程中发生意外错误: ${e.message}", emptyList(), e)
            }
        }

        /**
         * 验证批处理配置
         */
        private fun validateBatchConfig(batch: BatchConfig, errors: MutableList<ValidationError>) {
            if (batch.cloudletCount <= 0) {
                errors.add(ValidationError("batch.cloudletCount", batch.cloudletCount.toString(),
                    "批处理任务数必须大于0"))
            }
            if (batch.cloudletCount > 10000) {
                errors.add(ValidationError("batch.cloudletCount", batch.cloudletCount.toString(),
                    "批处理任务数过大，可能影响性能，建议不超过10000"))
            }

            if (batch.population <= 0) {
                errors.add(ValidationError("batch.population", batch.population.toString(),
                    "批处理种群大小必须大于0"))
            }
            if (batch.population > 1000) {
                errors.add(ValidationError("batch.population", batch.population.toString(),
                    "批处理种群大小过大，可能影响性能，建议不超过1000"))
            }

            if (batch.maxIter <= 0) {
                errors.add(ValidationError("batch.maxIter", batch.maxIter.toString(),
                    "批处理最大迭代次数必须大于0"))
            }
            if (batch.maxIter > 10000) {
                errors.add(ValidationError("batch.maxIter", batch.maxIter.toString(),
                    "批处理最大迭代次数过大，可能影响性能，建议不超过10000"))
            }

            if (batch.runs <= 0) {
                errors.add(ValidationError("batch.runs", batch.runs.toString(),
                    "批处理运行次数必须大于0"))
            }
            if (batch.runs > 100) {
                errors.add(ValidationError("batch.runs", batch.runs.toString(),
                    "批处理运行次数过多，可能耗时过长，建议不超过100"))
            }

            for ((index, count) in batch.cloudletCounts.withIndex()) {
                if (count <= 0) {
                    errors.add(ValidationError("batch.cloudletCounts[$index]", count.toString(),
                        "批量任务数必须大于0"))
                }
            }
        }

        /**
         * 验证实时调度配置
         */
        private fun validateRealtimeConfig(realtime: RealtimeConfig, errors: MutableList<ValidationError>) {
            if (realtime.cloudletCount <= 0) {
                errors.add(ValidationError("realtime.cloudletCount", realtime.cloudletCount.toString(),
                    "实时调度任务数必须大于0"))
            }
            if (realtime.cloudletCount > 10000) {
                errors.add(ValidationError("realtime.cloudletCount", realtime.cloudletCount.toString(),
                    "实时调度任务数过大，可能影响性能，建议不超过10000"))
            }

            if (realtime.simulationDuration <= 0) {
                errors.add(ValidationError("realtime.simulationDuration", realtime.simulationDuration.toString(),
                    "仿真持续时间必须大于0"))
            }
            if (realtime.simulationDuration > 10000.0) {
                errors.add(ValidationError("realtime.simulationDuration", realtime.simulationDuration.toString(),
                    "仿真持续时间过长，可能影响性能，建议不超过10000秒"))
            }

            if (realtime.arrivalRate <= 0) {
                errors.add(ValidationError("realtime.arrivalRate", realtime.arrivalRate.toString(),
                    "到达率必须大于0"))
            }
            if (realtime.arrivalRate > 1000.0) {
                errors.add(ValidationError("realtime.arrivalRate", realtime.arrivalRate.toString(),
                    "到达率过高，可能导致系统过载，建议不超过1000个/秒"))
            }

            if (realtime.runs <= 0) {
                errors.add(ValidationError("realtime.runs", realtime.runs.toString(),
                    "实时调度运行次数必须大于0"))
            }
            if (realtime.runs > 50) {
                errors.add(ValidationError("realtime.runs", realtime.runs.toString(),
                    "实时调度运行次数过多，可能耗时过长，建议不超过50"))
            }

            val distributions = setOf("poisson", "uniform", "burst")
            if (realtime.arrival.distribution.lowercase() !in distributions) {
                errors.add(ValidationError("realtime.arrival.distribution", realtime.arrival.distribution,
                    "到达分布必须是以下值之一: ${distributions.joinToString(", ")}"))
            }
            if (realtime.arrival.burstIntensity <= 0.0) {
                errors.add(ValidationError("realtime.arrival.burstIntensity", realtime.arrival.burstIntensity.toString(),
                    "突发强度必须大于0"))
            }
            if (realtime.arrival.burstDuration <= 0.0) {
                errors.add(ValidationError("realtime.arrival.burstDuration", realtime.arrival.burstDuration.toString(),
                    "突发持续时间必须大于0"))
            }

            val strategies = setOf("dynamic", "static")
            if (realtime.scheduling.strategy.lowercase() !in strategies) {
                errors.add(ValidationError("realtime.scheduling.strategy", realtime.scheduling.strategy,
                    "实时调度策略必须是以下值之一: ${strategies.joinToString(", ")}"))
            }
            if (realtime.scheduling.maxQueueSize <= 0) {
                errors.add(ValidationError("realtime.scheduling.maxQueueSize", realtime.scheduling.maxQueueSize.toString(),
                    "最大队列长度必须大于0"))
            }
            if (realtime.scheduling.taskTimeout < 0.0) {
                errors.add(ValidationError("realtime.scheduling.taskTimeout", realtime.scheduling.taskTimeout.toString(),
                    "任务超时时间不能为负数"))
            }
            if (realtime.scheduling.decisionDelay < 0.0) {
                errors.add(ValidationError("realtime.scheduling.decisionDelay", realtime.scheduling.decisionDelay.toString(),
                    "调度决策延迟不能为负数"))
            }
            if (realtime.scheduling.decisionJitter < 0.0) {
                errors.add(ValidationError("realtime.scheduling.decisionJitter", realtime.scheduling.decisionJitter.toString(),
                    "调度决策抖动不能为负数"))
            }
            if (realtime.scheduling.failureRate < 0.0 || realtime.scheduling.failureRate > 1.0) {
                errors.add(ValidationError("realtime.scheduling.failureRate", realtime.scheduling.failureRate.toString(),
                    "任务失败率必须在 [0,1] 范围内"))
            }
            if (realtime.scheduling.retryLimit < 0) {
                errors.add(ValidationError("realtime.scheduling.retryLimit", realtime.scheduling.retryLimit.toString(),
                    "重试次数不能为负数"))
            }
            if (realtime.scheduling.retryDelay < 0.0) {
                errors.add(ValidationError("realtime.scheduling.retryDelay", realtime.scheduling.retryDelay.toString(),
                    "重试延迟不能为负数"))
            }
            if (realtime.scheduling.retryBackoffMultiplier < 1.0) {
                errors.add(ValidationError("realtime.scheduling.retryBackoffMultiplier", realtime.scheduling.retryBackoffMultiplier.toString(),
                    "重试退避倍数必须大于等于 1"))
            }
            val queuePolicies = RealtimeQueuePolicy.valuesForConfig()
            if (realtime.scheduling.queuePolicy.lowercase() !in queuePolicies) {
                errors.add(ValidationError("realtime.scheduling.queuePolicy", realtime.scheduling.queuePolicy,
                    "实时队列策略必须是以下值之一: ${queuePolicies.joinToString(", ")}"))
            }
            if (realtime.scheduling.priorityLevels < 1) {
                errors.add(ValidationError("realtime.scheduling.priorityLevels", realtime.scheduling.priorityLevels.toString(),
                    "优先级层级必须大于等于 1"))
            }
            if (realtime.scheduling.highPriorityRatio < 0.0 || realtime.scheduling.highPriorityRatio > 1.0) {
                errors.add(ValidationError("realtime.scheduling.highPriorityRatio", realtime.scheduling.highPriorityRatio.toString(),
                    "高优先级任务比例必须在 [0,1] 范围内"))
            }
            if (realtime.scheduling.deadlineFactor < 0.0) {
                errors.add(ValidationError("realtime.scheduling.deadlineFactor", realtime.scheduling.deadlineFactor.toString(),
                    "SLA deadline 系数不能为负数"))
            }
            if (realtime.scheduling.vmQueueCapacity < 0) {
                errors.add(ValidationError("realtime.scheduling.vmQueueCapacity", realtime.scheduling.vmQueueCapacity.toString(),
                    "单 VM 队列容量不能为负数"))
            }
            if (realtime.scheduling.overloadFailureMultiplier < 0.0) {
                errors.add(ValidationError("realtime.scheduling.overloadFailureMultiplier", realtime.scheduling.overloadFailureMultiplier.toString(),
                    "过载失败倍率不能为负数"))
            }
            if (realtime.scheduling.scaleOutQueueThreshold < 0) {
                errors.add(ValidationError("realtime.scheduling.scaleOutQueueThreshold", realtime.scheduling.scaleOutQueueThreshold.toString(),
                    "扩容队列阈值不能为负数"))
            }
            if (realtime.scheduling.scaleInIdleTime < 0.0) {
                errors.add(ValidationError("realtime.scheduling.scaleInIdleTime", realtime.scheduling.scaleInIdleTime.toString(),
                    "缩容空闲时间不能为负数"))
            }
            if (realtime.scheduling.maxDynamicVms < 0) {
                errors.add(ValidationError("realtime.scheduling.maxDynamicVms", realtime.scheduling.maxDynamicVms.toString(),
                    "最大动态 VM 数不能为负数"))
            }
            if (realtime.scheduling.vmColdStartDelay < 0.0) {
                errors.add(ValidationError("realtime.scheduling.vmColdStartDelay", realtime.scheduling.vmColdStartDelay.toString(),
                    "VM 冷启动延迟不能为负数"))
            }
            if (realtime.scheduling.scaleOutCost < 0.0) {
                errors.add(ValidationError("realtime.scheduling.scaleOutCost", realtime.scheduling.scaleOutCost.toString(),
                    "扩容成本不能为负数"))
            }
            if (realtime.scheduling.scaleInProtectionTime < 0.0) {
                errors.add(ValidationError("realtime.scheduling.scaleInProtectionTime", realtime.scheduling.scaleInProtectionTime.toString(),
                    "缩容保护时间不能为负数"))
            }
            if (realtime.scheduling.networkLatency < 0.0) {
                errors.add(ValidationError("realtime.scheduling.networkLatency", realtime.scheduling.networkLatency.toString(),
                    "网络延迟不能为负数"))
            }
            if (realtime.scheduling.imagePullDelay < 0.0) {
                errors.add(ValidationError("realtime.scheduling.imagePullDelay", realtime.scheduling.imagePullDelay.toString(),
                    "镜像拉取延迟不能为负数"))
            }
            if (realtime.scheduling.ioWeight < 0.0) {
                errors.add(ValidationError("realtime.scheduling.ioWeight", realtime.scheduling.ioWeight.toString(),
                    "I/O 权重不能为负数"))
            }
            if (realtime.scheduling.ramWeight < 0.0) {
                errors.add(ValidationError("realtime.scheduling.ramWeight", realtime.scheduling.ramWeight.toString(),
                    "RAM 权重不能为负数"))
            }
            if (realtime.scheduling.bwWeight < 0.0) {
                errors.add(ValidationError("realtime.scheduling.bwWeight", realtime.scheduling.bwWeight.toString(),
                    "带宽权重不能为负数"))
            }
            if (realtime.scheduling.runtimeFailureRate < 0.0 || realtime.scheduling.runtimeFailureRate > 1.0) {
                errors.add(ValidationError("realtime.scheduling.runtimeFailureRate", realtime.scheduling.runtimeFailureRate.toString(),
                    "运行中失败率必须在 [0,1] 范围内"))
            }
            if (realtime.scheduling.nodeFailureRate < 0.0 || realtime.scheduling.nodeFailureRate > 1.0) {
                errors.add(ValidationError("realtime.scheduling.nodeFailureRate", realtime.scheduling.nodeFailureRate.toString(),
                    "节点失败率必须在 [0,1] 范围内"))
            }
            if (realtime.scheduling.checkpointInterval < 0.0) {
                errors.add(ValidationError("realtime.scheduling.checkpointInterval", realtime.scheduling.checkpointInterval.toString(),
                    "checkpoint 间隔不能为负数"))
            }
            if (realtime.scheduling.migrationDelay < 0.0) {
                errors.add(ValidationError("realtime.scheduling.migrationDelay", realtime.scheduling.migrationDelay.toString(),
                    "迁移延迟不能为负数"))
            }
            val timeoutActions = RealtimeTimeoutAction.valuesForConfig()
            if (realtime.scheduling.timeoutAction.lowercase() !in timeoutActions) {
                errors.add(ValidationError("realtime.scheduling.timeoutAction", realtime.scheduling.timeoutAction,
                    "超时动作必须是以下值之一: ${timeoutActions.joinToString(", ")}"))
            }
            val preemptionPolicies = RealtimePreemptionPolicy.valuesForConfig()
            if (realtime.scheduling.preemptionPolicy.lowercase() !in preemptionPolicies) {
                errors.add(ValidationError("realtime.scheduling.preemptionPolicy", realtime.scheduling.preemptionPolicy,
                    "抢占策略必须是以下值之一: ${preemptionPolicies.joinToString(", ")}"))
            }
            if (realtime.scheduling.preemptionMinPriorityGap < 0) {
                errors.add(ValidationError("realtime.scheduling.preemptionMinPriorityGap", realtime.scheduling.preemptionMinPriorityGap.toString(),
                    "抢占优先级差不能为负数"))
            }
            if (realtime.scheduling.preemptionMaxPerTask < 0) {
                errors.add(ValidationError("realtime.scheduling.preemptionMaxPerTask", realtime.scheduling.preemptionMaxPerTask.toString(),
                    "单任务最大抢占次数不能为负数"))
            }
            if (realtime.scheduling.preemptionDelay < 0.0) {
                errors.add(ValidationError("realtime.scheduling.preemptionDelay", realtime.scheduling.preemptionDelay.toString(),
                    "抢占延迟不能为负数"))
            }
            if (realtime.scheduling.preemptionPenalty < 0.0) {
                errors.add(ValidationError("realtime.scheduling.preemptionPenalty", realtime.scheduling.preemptionPenalty.toString(),
                    "抢占惩罚不能为负数"))
            }
            if (realtime.scheduling.tenantCount < 1) {
                errors.add(ValidationError("realtime.scheduling.tenantCount", realtime.scheduling.tenantCount.toString(),
                    "租户数量必须大于等于 1"))
            }
            if (realtime.scheduling.tenantQuota.isNotEmpty() &&
                realtime.scheduling.tenantQuota.size != realtime.scheduling.tenantCount
            ) {
                errors.add(ValidationError("realtime.scheduling.tenantQuota", realtime.scheduling.tenantQuota.joinToString(","),
                    "租户配额数量必须等于 tenantCount"))
            }
            realtime.scheduling.tenantQuota.forEachIndexed { index, quota ->
                if (quota < 0) {
                    errors.add(ValidationError("realtime.scheduling.tenantQuota[$index]", quota.toString(),
                        "租户配额不能为负数"))
                }
            }
            if (realtime.scheduling.tenantWeights.isNotEmpty() &&
                realtime.scheduling.tenantWeights.size != realtime.scheduling.tenantCount
            ) {
                errors.add(ValidationError("realtime.scheduling.tenantWeights", realtime.scheduling.tenantWeights.joinToString(","),
                    "租户权重数量必须等于 tenantCount"))
            }
            realtime.scheduling.tenantWeights.forEachIndexed { index, weight ->
                if (weight <= 0.0) {
                    errors.add(ValidationError("realtime.scheduling.tenantWeights[$index]", weight.toString(),
                        "租户权重必须大于 0"))
                }
            }
            val tenantFairnessPolicies = RealtimeTenantFairnessPolicy.valuesForConfig()
            if (realtime.scheduling.tenantFairnessPolicy.lowercase() !in tenantFairnessPolicies) {
                errors.add(ValidationError("realtime.scheduling.tenantFairnessPolicy", realtime.scheduling.tenantFairnessPolicy,
                    "租户公平策略必须是以下值之一: ${tenantFairnessPolicies.joinToString(", ")}"))
            }
            val reservations = setOf("none", "partial", "full")
            if (realtime.scheduling.resourceReservation.lowercase() !in reservations) {
                errors.add(ValidationError("realtime.scheduling.resourceReservation", realtime.scheduling.resourceReservation,
                    "资源预留策略必须是以下值之一: ${reservations.joinToString(", ")}"))
            }

            for ((index, count) in realtime.cloudletCounts.withIndex()) {
                if (count <= 0) {
                    errors.add(ValidationError("realtime.cloudletCounts[$index]", count.toString(),
                        "批量任务数必须大于0"))
                }
            }
        }

        private fun validateAlgorithmConfigs(
            algorithmConfigs: Map<String, AlgorithmConfig>,
            errors: MutableList<ValidationError>
        ) {
            for ((name, config) in algorithmConfigs) {
                config.population?.let { population ->
                    if (population <= 0) {
                        errors.add(ValidationError("algorithms.$name.population", population.toString(),
                            "算法级种群大小必须大于0"))
                    }
                }
                config.maxIter?.let { maxIter ->
                    if (maxIter <= 0) {
                        errors.add(ValidationError("algorithms.$name.maxIter", maxIter.toString(),
                            "算法级最大迭代次数必须大于0"))
                    }
                }
            }
        }

        private fun validatePresetConfigs(
            presets: Map<String, PresetConfig>,
            errors: MutableList<ValidationError>
        ) {
            for ((name, preset) in presets) {
                if (preset.algorithms.isEmpty()) {
                    errors.add(ValidationError("presets.$name.algorithms", "[]",
                        "预设算法列表不能为空"))
                }
            }
        }

        private fun validateProfileConfigs(
            config: ExperimentConfig,
            errors: MutableList<ValidationError>,
            requireProfiles: Boolean
        ) {
            if (requireProfiles && config.profiles.isEmpty()) {
                errors.add(ValidationError("profiles", "[]", "profiles 配置不能为空"))
            }

            config.defaultProfile?.let { defaultProfile ->
                if (config.profiles.isNotEmpty() && defaultProfile !in config.profiles) {
                    errors.add(ValidationError("defaultProfile", defaultProfile,
                        "defaultProfile 必须引用已定义的 profile"))
                }
            }

            for ((name, profile) in config.profiles) {
                if (profile.mode.isBlank()) {
                    errors.add(ValidationError("profiles.$name.mode", profile.mode, "profile 必须指定 mode"))
                } else {
                    val normalizedMode = profile.mode.lowercase().replace("_", "-")
                    if (normalizedMode !in setOf("batch", "realtime", "batch-multi", "realtime-multi")) {
                        errors.add(ValidationError("profiles.$name.mode", profile.mode,
                            "profile.mode 必须是 batch, realtime, batch-multi, realtime-multi 之一"))
                    }
                }

                if (profile.algorithms.isNotEmpty() && !profile.preset.isNullOrBlank()) {
                    errors.add(ValidationError("profiles.$name", profile.algorithms.joinToString(","),
                        "profile 的 algorithms 与 preset 互斥"))
                }

                if (profile.runs != null && profile.runs <= 0) {
                    errors.add(ValidationError("profiles.$name.runs", profile.runs.toString(), "runs 必须大于 0"))
                }

                if (profile.tasks.any { it <= 0 }) {
                    errors.add(ValidationError("profiles.$name.tasks", profile.tasks.joinToString(","), "tasks 必须全部大于 0"))
                }
            }
        }

        /**
         * 验证优化算法配置
         */
        private fun validateOptimizerConfig(optimizer: OptimizerConfig, errors: MutableList<ValidationError>) {
            if (optimizer.population <= 0) {
                errors.add(ValidationError("optimizer.population", optimizer.population.toString(),
                    "优化算法种群大小必须大于0"))
            }
            if (optimizer.population > 500) {
                errors.add(ValidationError("optimizer.population", optimizer.population.toString(),
                    "优化算法种群大小过大，可能影响性能，建议不超过500"))
            }

            if (optimizer.maxIter <= 0) {
                errors.add(ValidationError("optimizer.maxIter", optimizer.maxIter.toString(),
                    "优化算法最大迭代次数必须大于0"))
            }
            if (optimizer.maxIter > 5000) {
                errors.add(ValidationError("optimizer.maxIter", optimizer.maxIter.toString(),
                    "优化算法最大迭代次数过大，可能影响性能，建议不超过5000"))
            }
        }

        /**
         * 验证随机种子配置
         */
        private fun validateRandomConfig(randomSeed: Long, errors: MutableList<ValidationError>) {
            // 随机种子通常不需要特殊验证，但可以检查是否是合理的长整型值
            if (randomSeed == Long.MIN_VALUE) {
                errors.add(ValidationError("randomSeed", randomSeed.toString(),
                    "随机种子值无效，请使用其他值"))
            }
        }

        /**
         * 验证目标函数权重
         */
        private fun validateObjectiveWeights(weights: ObjectiveWeightsConfig, context: String, errors: MutableList<ValidationError>) {
            val weightsList = listOf(
                "cost" to weights.cost,
                "totalTime" to weights.totalTime,
                "loadBalance" to weights.loadBalance,
                "makespan" to weights.makespan
            )

            for ((name, value) in weightsList) {
                if (value < 0.0 || value > 1.0) {
                    errors.add(ValidationError("$context.objective.$name", value.toString(),
                        "$context 模式中 $name 权重必须在 [0,1] 范围内"))
                }
            }

            val totalWeight = weights.cost + weights.totalTime + weights.loadBalance + weights.makespan
            if (totalWeight <= 0.0) {
                errors.add(ValidationError("$context.objective", totalWeight.toString(),
                    "$context 模式中目标函数权重总和必须大于 0"))
            }

            // 检查权重总和是否接近1.0（允许小幅偏差）
            if (Math.abs(totalWeight - 1.0) > 0.01) {
                errors.add(ValidationError("$context.objective", totalWeight.toString(),
                    "$context 模式中目标函数权重总和应为 1.0（当前: ${String.format("%.3f", totalWeight)})"))
            }
        }
    }
}

/**
 * 批处理模式配置
 */
data class BatchConfig(
    val cloudletCount: Int = 100,
    val cloudletCounts: List<Int> = emptyList(),
    val population: Int = 30,
    val maxIter: Int = 50,
    /**
     * 要运行的算法列表（空列表表示运行所有算法）
     * 示例: listOf(BatchAlgorithmType.PSO, BatchAlgorithmType.WOA)
     */
    val algorithms: List<BatchAlgorithmType> = emptyList(),  // 空列表 = 运行所有算法
    /**
     * 实验运行次数（用于计算平均值）
     * 默认: 1（单次运行）
     */
    val runs: Int = 1,
    /**
     * 任务生成器类型
     * 默认: LOG_NORMAL（对数正态分布）
     */
    val generatorType: CloudletGeneratorType = CloudletGenConfig.GENERATOR_TYPE,
    /**
     * Google Trace 配置（当generatorType为GOOGLE_TRACE时使用）
     */
    val googleTraceConfig: GoogleTraceConfig? = null,
    /**
     * 目标函数权重配置
     * 允许自由组合成本、时间、负载均衡等目标
     */
    val objectiveWeights: ObjectiveWeightsConfig = ObjectiveWeightsConfig()
)

/**
 * 实时调度模式配置
 */
data class RealtimeConfig(
    val cloudletCount: Int = 200,
    val cloudletCounts: List<Int> = emptyList(),
    val simulationDuration: Double = 500.0,  // 仿真持续时间（秒）
    val arrivalRate: Double = 5.0,            // 平均每秒到达的任务数
    /**
     * 要运行的算法列表（空列表表示运行所有算法）
     * 示例: listOf(RealtimeAlgorithmType.PSO_REALTIME, RealtimeAlgorithmType.WOA_REALTIME)
     */
    val algorithms: List<RealtimeAlgorithmType> = emptyList(),  // 空列表 = 运行所有算法
    /**
     * 实验运行次数（用于计算平均值）
     * 默认: 1（单次运行）
     */
    val runs: Int = 1,
    /**
     * 任务生成器类型
     * 默认: LOG_NORMAL（对数正态分布）
     */
    val generatorType: CloudletGeneratorType = CloudletGenConfig.GENERATOR_TYPE,
    /**
     * Google Trace 配置（当generatorType为GOOGLE_TRACE时使用）
     */
    val googleTraceConfig: GoogleTraceConfig? = null,
    /**
     * 目标函数权重配置
     * 允许自由组合成本、时间、负载均衡等目标
     */
    val objectiveWeights: ObjectiveWeightsConfig = ObjectiveWeightsConfig(),
    val arrival: RealtimeArrivalConfig = RealtimeArrivalConfig(),
    val scheduling: RealtimeSchedulingConfig = RealtimeSchedulingConfig()
)

/**
 * 优化算法配置
 */
data class OptimizerConfig(
    val population: Int = 20,      // 实时调度使用的种群大小
    val maxIter: Int = 20           // 实时调度使用的最大迭代次数
)

/**
 * 数据中心配置
 */
object DatacenterConfig {
    // 虚拟机配置
    const val L_MIPS = 1000
    const val M_MIPS = 2000
    const val H_MIPS = 4000
    
    const val L_PRICE = 0.1
    const val M_PRICE = 0.5
    const val H_PRICE = 1.0
    
    const val L_VM_N = 4
    const val M_VM_N = 3
    const val H_VM_N = 2
    
    // 资源配置
    const val RAM = 2048              // MB
    const val STORAGE = 100000L       // MB
    const val IMAGE_SIZE = 10000L     // MB
    const val BW = 1024               // Mbps
    
    // 默认任务数量
    const val DEFAULT_CLOUDLET_N = 1000
    
    // 默认随机数种子
    const val DEFAULT_RANDOM_SEED = 0L
}

/**
 * 任务生成器类型
 */
enum class CloudletGeneratorType {
    LOG_NORMAL,      // 对数正态分布（默认，对应 createCloudlets）
    UNIFORM,         // 均匀分布（对应 createCloudlets1）
    LOG_NORMAL_SCI,  // 对数正态分布 SCI（对应 createCloudletsSCI，输出文件大小独立参数）
    GOOGLE_TRACE     // Google Trace 数据集（CSV格式）
}

/**
 * 任务生成配置
 */
object CloudletGenConfig {
    // 任务生成器类型
    val GENERATOR_TYPE: CloudletGeneratorType = CloudletGeneratorType.LOG_NORMAL
    
    // 任务执行时间分布参数（对数正态分布）
    const val MEAN_EXEC_TIME = 30000.0
    const val VARIANCE_EXEC_TIME = 1.5
    
    // 文件大小分布参数（正态分布）
    const val MEAN_FILE_SIZE = 100.0
    const val VARIANCE_FILE_SIZE = 100.0
    
    // 输出文件大小分布参数（正态分布，用于 LOG_NORMAL_SCI）
    const val MEAN_OUTPUT_SIZE = 100.0
    const val VARIANCE_OUTPUT_SIZE = 20.0
    
    // 均匀分布参数（用于 UNIFORM）
    const val MIN_LENGTH = 10000L
    const val MAX_LENGTH = 50000L
    const val MIN_FILE_SIZE = 10L
    const val MAX_FILE_SIZE = 200L
    const val MIN_OUTPUT_SIZE = 10L
    const val MAX_OUTPUT_SIZE = 200L
}

/**
 * 目标函数配置
 */
@Serializable
data class ObjectiveWeightsConfig(
    val cost: Double = 1.0 / 3,        // 成本权重
    val totalTime: Double = 1.0 / 3,   // 总时间权重
    val loadBalance: Double = 1.0 / 3, // 负载均衡权重
    val makespan: Double = 0.0         // Makespan权重（可选）
) {
    init {
        require(cost >= 0.0 && cost <= 1.0) { "成本权重必须在[0,1]范围内" }
        require(totalTime >= 0.0 && totalTime <= 1.0) { "总时间权重必须在[0,1]范围内" }
        require(loadBalance >= 0.0 && loadBalance <= 1.0) { "负载均衡权重必须在[0,1]范围内" }
        require(makespan >= 0.0 && makespan <= 1.0) { "Makespan权重必须在[0,1]范围内" }

        val sum = cost + totalTime + loadBalance + makespan
        require(sum > 0.0) { "权重总和必须大于0" }
    }
}

object ObjectiveConfig {
    // 默认适应度函数权重（向后兼容）
    const val ALPHA = 1.0 / 3  // Cost权重
    const val BETA = 1.0 / 3    // TotalTime权重
    const val GAMMA = 1.0 / 3   // LoadBalance权重
}
