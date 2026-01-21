package config

import com.akuleshov7.ktoml.Toml
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
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
    val mode: String? = null,  // 实验模式
    val random: RandomConfig? = null,
    val batch: TomlBatchConfig? = null,
    val realtime: TomlRealtimeConfig? = null,
    val optimizer: TomlOptimizerConfig? = null,
    // 算法配置
    val algorithms: Map<String, Map<String, AlgorithmConfig>>? = null,
    // 预设配置
    val presets: Map<String, List<String>>? = null
)

@Serializable
data class AlgorithmConfig(
    val enabled: Boolean = true,
    val description: String = "",
    val population: Int? = null,
    val maxIter: Int? = null
)

@Serializable
data class RandomConfig(
    val seed: Long = 0L
)

@Serializable
data class TomlBatchConfig(
    val cloudletCount: Int = 100,
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
    val simulationDuration: Double = 500.0,
    val arrivalRate: Double = 5.0,
    val runs: Int = 1,
    val generator: GeneratorConfig = GeneratorConfig.LOG_NORMAL,
    val objective: ObjectiveWeightsConfig = ObjectiveWeightsConfig(),
    // 向后兼容
    val generatorType: String = "LOG_NORMAL",
    val googleTrace: GoogleTraceConfig? = null
)

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
    // ========== 实验模式 ==========
    val mode: ExperimentMode = ExperimentMode.BATCH,
    
    // ========== 批处理模式配置 ==========
    val batch: BatchConfig = BatchConfig(),

    // ========== 实时调度模式配置 ==========
    val realtime: RealtimeConfig = RealtimeConfig(),

    // ========== 通用配置 ==========
    val randomSeed: Long = 0L,

    // ========== 优化算法配置 ==========
    val optimizer: OptimizerConfig = OptimizerConfig()
) {
    companion object {
        /**
         * 从配置文件加载实验配置
         * 优先级：指定配置文件 > 默认配置
         */
        fun load(configPath: String): ExperimentConfig {
            val config = loadInternal(configPath)
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
            ExperimentMode.valueOf(mode.uppercase())
        } catch (e: IllegalArgumentException) {
            Logger.warn("未知的实验模式: {}, 使用默认值 BATCH", mode)
            ExperimentMode.BATCH
        }

        /**
         * 内部加载方法（不验证）
         */
        private fun loadInternal(configPath: String): ExperimentConfig {
            try {
                val file = File(configPath)
                if (!file.exists()) {
                    throw IllegalArgumentException("配置文件不存在: $configPath")
                }

                val tomlContent = file.readText()
                val tomlConfig = Toml.decodeFromString(serializer<ExperimentTomlConfig>(), tomlContent)

                val mode = if (tomlConfig.mode != null) {
                    parseExperimentMode(tomlConfig.mode)
                } else {
                    ExperimentMode.BATCH  // 默认为批处理模式
                }

                return ExperimentConfig(
                    mode = mode,
                    randomSeed = tomlConfig.random?.seed ?: 0L,
                    batch = mergeBatchConfig(BatchConfig(), tomlConfig.batch),
                    realtime = mergeRealtimeConfig(RealtimeConfig(), tomlConfig.realtime),
                    optimizer = mergeOptimizerConfig(OptimizerConfig(), tomlConfig.optimizer)
                )
            } catch (e: Exception) {
                Logger.error("加载实验配置时发生错误: ${e.message}", e)
                throw e
            }
        }

        /**
         * 合并批处理配置
         */
        private fun mergeBatchConfig(base: BatchConfig, toml: TomlBatchConfig?): BatchConfig {
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
                population = toml.population ?: base.population,
                maxIter = toml.maxIter ?: base.maxIter,
                runs = toml.runs,
                generatorType = generatorType,
                googleTraceConfig = toml.googleTrace ?: base.googleTraceConfig,
                objectiveWeights = objectiveWeights
            )
        }

        /**
         * 合并实时调度配置
         */
        private fun mergeRealtimeConfig(base: RealtimeConfig, toml: TomlRealtimeConfig?): RealtimeConfig {
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
                runs = toml.runs,
                generatorType = generatorType,
                googleTraceConfig = toml.googleTrace ?: base.googleTraceConfig,
                objectiveWeights = objectiveWeights
            )
        }

        /**
         * 合并优化器配置
         */
        private fun mergeOptimizerConfig(base: OptimizerConfig, toml: TomlOptimizerConfig?): OptimizerConfig {
            if (toml == null) return base

            return base.copy(
                population = toml.population ?: base.population,
                maxIter = toml.maxIter ?: base.maxIter
            )
        }

        /**
         * 验证配置参数的合理性
         * @throws ConfigValidationException 当配置无效时，包含详细的验证错误信息
         */
        private fun validateConfig(config: ExperimentConfig) {
            val errors = mutableListOf<ValidationError>()

            try {
                // 验证批处理配置
                validateBatchConfig(config.batch, errors)

                // 验证实时调度配置
                validateRealtimeConfig(config.realtime, errors)

                // 验证优化算法配置
                validateOptimizerConfig(config.optimizer, errors)

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
    val objectiveWeights: ObjectiveWeightsConfig = ObjectiveWeightsConfig()
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