package config

import com.akuleshov7.ktoml.Toml
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import util.Logger
import java.io.File
import java.io.FileInputStream
import java.util.Properties

/**
 * TOML配置文件数据类
 */
@Serializable
data class TomlConfig(
    val random: RandomConfig? = null,
    val batch: TomlBatchConfig? = null,
    val realtime: TomlRealtimeConfig? = null,
    val optimizer: TomlOptimizerConfig? = null,
    val output: TomlOutputConfig? = null,
    val experiment: TomlExperimentConfig? = null,
    // 算法配置
    val algorithms: Map<String, Map<String, AlgorithmConfig>>? = null,
    // 预设配置
    val presets: Map<String, List<String>>? = null
)

@Serializable
data class TomlOutputConfig(
    val resultsDir: String = "runs",
    val csv: CsvConfig = CsvConfig(),
    val logging: LoggingConfig = LoggingConfig()
)

@Serializable
data class CsvConfig(
    val enabled: Boolean = true,
    val delimiter: String = ","
)

@Serializable
data class LoggingConfig(
    val level: String = "INFO",
    val file: Boolean = true,
    val console: Boolean = true
)

@Serializable
data class TomlExperimentConfig(
    val autoCreateDirs: Boolean = true,
    val nameFormat: String = "{mode}_{timestamp}_{algorithms}",
    val maxConcurrent: Int = 2
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
 * 统一管理所有实验参数
 */
data class ExperimentConfig(
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
         * 从多种来源加载配置并验证
         * 优先级：命令行参数 > 环境变量 > 配置文件 > 默认配置
         */
        fun load(args: Array<String> = emptyArray()): ExperimentConfig {
            val config = loadInternal(args)
            validateConfig(config)
            return config
        }

        /**
         * 内部加载方法（不验证）
         */
        private fun loadInternal(args: Array<String> = emptyArray()): ExperimentConfig {
            try {
                var config = ExperimentConfig()

                // 1. 加载默认配置
                Logger.debug("加载默认配置")

                // 2. 加载配置文件
                config = loadFromConfigFile(config)

                // 3. 加载环境变量配置
                config = loadFromEnvironment(config)

                // 4. 加载系统属性配置
                config = loadFromSystemProperties(config)

                // 5. 应用命令行参数覆盖（这里不再处理命令行参数）
                // 命令行参数现在由Main.kt处理

                Logger.debug("配置加载完成")
                return config

            } catch (e: Exception) {
                Logger.error("加载配置时发生错误，使用默认配置: " + e.message, e)
                return ExperimentConfig()
            }
        }

        /**
         * 验证配置参数的合理性
         */
        private fun validateConfig(config: ExperimentConfig) {
            val errors = mutableListOf<String>()

            // 验证随机种子
            if (config.randomSeed < 0) {
                errors.add("随机种子不能为负数: ${config.randomSeed}")
            }

            // 验证批处理配置
            with(config.batch) {
                if (cloudletCount <= 0) {
                    errors.add("批处理任务数必须大于0: $cloudletCount")
                }
                if (population <= 0) {
                    errors.add("批处理种群大小必须大于0: $population")
                }
                if (maxIter <= 0) {
                    errors.add("批处理最大迭代次数必须大于0: $maxIter")
                }
                if (runs <= 0) {
                    errors.add("批处理运行次数必须大于0: $runs")
                }
            }

            // 验证实时调度配置
            with(config.realtime) {
                if (cloudletCount <= 0) {
                    errors.add("实时调度任务数必须大于0: $cloudletCount")
                }
                if (simulationDuration <= 0) {
                    errors.add("仿真持续时间必须大于0: $simulationDuration")
                }
                if (arrivalRate <= 0) {
                    errors.add("到达率必须大于0: $arrivalRate")
                }
                if (runs <= 0) {
                    errors.add("实时调度运行次数必须大于0: $runs")
                }
            }

            // 验证优化算法配置
            with(config.optimizer) {
                if (population <= 0) {
                    errors.add("优化算法种群大小必须大于0: $population")
                }
                if (maxIter <= 0) {
                    errors.add("优化算法最大迭代次数必须大于0: $maxIter")
                }
            }

            if (errors.isNotEmpty()) {
                val errorMsg = "配置验证失败:\n" + errors.joinToString("\n") { "  - $it" }
                Logger.error(errorMsg)
                throw IllegalArgumentException(errorMsg)
            }

            Logger.debug("配置验证通过")
        }

        /**
         * 从配置文件加载配置
         */
        private fun loadFromConfigFile(baseConfig: ExperimentConfig): ExperimentConfig {
            val configFiles = listOf(
                "configs/default.toml",
                "configs/batch.toml",
                "configs/realtime.toml",
                "configs/algorithms.toml",
                "cloudsim-benchmark.toml",
                "config.toml",
                "cloudsim-benchmark.properties",
                "config.properties",
                System.getProperty("config.file"),
                System.getenv("CONFIG_FILE")
            ).filterNotNull()

            var resultConfig = baseConfig

            for (configFile in configFiles) {
                val file = File(configFile)
                if (file.exists() && file.isFile) {
                    try {
                        Logger.debug("从配置文件加载: {}", configFile)
                        resultConfig = when (file.extension.lowercase()) {
                            "toml" -> loadAndMergeTomlConfig(resultConfig, file)
                            "properties", "props" -> loadPropertiesConfig(resultConfig, file)
                            else -> {
                                Logger.warn("不支持的配置文件格式: {}", file.extension)
                                resultConfig
                            }
                        }
                    } catch (e: Exception) {
                        Logger.warn("加载配置文件失败 $configFile: ${e.message}")
                    }
                }
            }

            return resultConfig
        }

        /**
         * 加载并合并TOML配置文件
         */
        private fun loadAndMergeTomlConfig(baseConfig: ExperimentConfig, file: File): ExperimentConfig {
            try {
                val tomlContent = file.readText()
                val tomlConfig = Toml.decodeFromString(serializer<TomlConfig>(), tomlContent)

                return baseConfig.copy(
                    randomSeed = tomlConfig.random?.seed ?: baseConfig.randomSeed,
                    batch = mergeBatchConfig(baseConfig.batch, tomlConfig.batch),
                    realtime = mergeRealtimeConfig(baseConfig.realtime, tomlConfig.realtime),
                    optimizer = mergeOptimizerConfig(baseConfig.optimizer, tomlConfig.optimizer)
                )
            } catch (e: Exception) {
                Logger.warn("TOML配置文件解析失败: ${e.message}")
                return baseConfig
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
                population = toml.population,
                maxIter = toml.maxIter
            )
        }

        /**
         * 加载Properties配置文件
         */
        private fun loadPropertiesConfig(baseConfig: ExperimentConfig, file: File): ExperimentConfig {
            val properties = Properties()
            FileInputStream(file).use { properties.load(it) }
            return applyProperties(baseConfig, properties)
        }

        /**
         * 解析生成器类型字符串
         */
        private fun parseGeneratorType(type: String): CloudletGeneratorType {
            return try {
                CloudletGeneratorType.valueOf(type.uppercase())
            } catch (e: IllegalArgumentException) {
                Logger.warn("未知的生成器类型: {}, 使用默认值 LOG_NORMAL", type)
                CloudletGeneratorType.LOG_NORMAL
            }
        }

        /**
         * 从环境变量加载配置
         */
        private fun loadFromEnvironment(baseConfig: ExperimentConfig): ExperimentConfig {
            val envMap = System.getenv()
            if (envMap.isEmpty()) return baseConfig

            Logger.debug("从环境变量加载配置")
            val properties = Properties()

            // 映射环境变量到配置属性
            envMap.forEach { (key, value) ->
                when (key.uppercase()) {
                    "CLOUDSIM_RANDOM_SEED" -> properties.setProperty("random.seed", value)
                    "CLOUDSIM_BATCH_CLOUDLET_COUNT" -> properties.setProperty("batch.cloudlet.count", value)
                    "CLOUDSIM_BATCH_POPULATION" -> properties.setProperty("batch.population", value)
                    "CLOUDSIM_BATCH_MAX_ITER" -> properties.setProperty("batch.max.iter", value)
                    "CLOUDSIM_BATCH_RUNS" -> properties.setProperty("batch.runs", value)
                    "CLOUDSIM_REALTIME_CLOUDLET_COUNT" -> properties.setProperty("realtime.cloudlet.count", value)
                    "CLOUDSIM_REALTIME_DURATION" -> properties.setProperty("realtime.simulation.duration", value)
                    "CLOUDSIM_REALTIME_ARRIVAL_RATE" -> properties.setProperty("realtime.arrival.rate", value)
                    "CLOUDSIM_REALTIME_RUNS" -> properties.setProperty("realtime.runs", value)
                    "CLOUDSIM_OPTIMIZER_POPULATION" -> properties.setProperty("optimizer.population", value)
                    "CLOUDSIM_OPTIMIZER_MAX_ITER" -> properties.setProperty("optimizer.max.iter", value)
                }
            }

            return applyProperties(baseConfig, properties)
        }

        /**
         * 从系统属性加载配置
         */
        private fun loadFromSystemProperties(baseConfig: ExperimentConfig): ExperimentConfig {
            val properties = Properties()
            System.getProperties().forEach { key, value ->
                val keyStr = key.toString()
                if (keyStr.startsWith("cloudsim.")) {
                    properties.setProperty(keyStr.substring("cloudsim.".length), value.toString())
                }
            }

            if (properties.isEmpty) return baseConfig

            Logger.debug("从系统属性加载配置")
            return applyProperties(baseConfig, properties)
        }


        /**
         * 应用Properties到配置对象
         */
        private fun applyProperties(baseConfig: ExperimentConfig, properties: Properties): ExperimentConfig {
            return baseConfig.copy(
                randomSeed = properties.getProperty("random.seed")?.toLongOrNull() ?: baseConfig.randomSeed,
                batch = baseConfig.batch.copy(
                    cloudletCount = properties.getProperty("batch.cloudlet.count")?.toIntOrNull() ?: baseConfig.batch.cloudletCount,
                    population = properties.getProperty("batch.population")?.toIntOrNull() ?: baseConfig.batch.population,
                    maxIter = properties.getProperty("batch.max.iter")?.toIntOrNull() ?: baseConfig.batch.maxIter,
                    runs = properties.getProperty("batch.runs")?.toIntOrNull() ?: baseConfig.batch.runs
                ),
                realtime = baseConfig.realtime.copy(
                    cloudletCount = properties.getProperty("realtime.cloudlet.count")?.toIntOrNull() ?: baseConfig.realtime.cloudletCount,
                    simulationDuration = properties.getProperty("realtime.simulation.duration")?.toDoubleOrNull() ?: baseConfig.realtime.simulationDuration,
                    arrivalRate = properties.getProperty("realtime.arrival.rate")?.toDoubleOrNull() ?: baseConfig.realtime.arrivalRate,
                    runs = properties.getProperty("realtime.runs")?.toIntOrNull() ?: baseConfig.realtime.runs
                ),
                optimizer = baseConfig.optimizer.copy(
                    population = properties.getProperty("optimizer.population")?.toIntOrNull() ?: baseConfig.optimizer.population,
                    maxIter = properties.getProperty("optimizer.max.iter")?.toIntOrNull() ?: baseConfig.optimizer.maxIter
                )
            )
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

