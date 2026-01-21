package config

import com.akuleshov7.ktoml.Toml
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import util.Logger
import java.io.File
import java.nio.file.Paths
import java.nio.file.InvalidPathException

/**
 * TOML格式的系统配置
 * 仅用于反序列化TOML文件
 */
@Serializable
data class SystemTomlConfig(
    val output: OutputTomlConfig? = null,
    val logging: LoggingTomlConfig? = null,
    val experiment: SystemExperimentTomlConfig? = null,
    val jvm: JvmTomlConfig? = null
)

@Serializable
data class OutputTomlConfig(
    val resultsDir: String = "runs",
    val csv: CsvTomlConfig = CsvTomlConfig()
)

@Serializable
data class CsvTomlConfig(
    val enabled: Boolean = true,
    val delimiter: String = ","
)

@Serializable
data class LoggingTomlConfig(
    val level: String = "INFO",
    val file: Boolean = true,
    val console: Boolean = true
)

@Serializable
data class SystemExperimentTomlConfig(
    val autoCreateDirs: Boolean = true,
    val nameFormat: String = "{mode}_{timestamp}_{algorithms}",
    val maxConcurrent: Int = Runtime.getRuntime().availableProcessors()
)

@Serializable
data class JvmTomlConfig(
    val maxHeapSize: String = "2g",
    val gcAlgorithm: String = "G1"
)

/**
 * 系统配置类
 * 专门管理系统的基础设施配置，如输出目录、日志级别等
 */
data class SystemConfig(
    val output: OutputConfig = OutputConfig(),
    val logging: LoggingConfig = LoggingConfig(),
    val experiment: SystemExperimentConfig = SystemExperimentConfig(),
    val jvm: JvmConfig = JvmConfig()
) {
    companion object {
        /**
         * 从配置文件加载系统配置
         * 优先级：指定配置文件 > 环境变量 > 默认配置
         *
         * @param configPath 配置文件路径，可为null
         * @return 加载的系统配置实例
         * @throws IllegalArgumentException 当配置文件不存在或格式错误时
         * @throws ConfigValidationException 当配置验证失败时
         */
        fun load(configPath: String?): SystemConfig {
            val config = loadInternal(configPath)
            validateConfig(config)
            return config
        }

        /**
         * 提供默认配置（供测试/外部调用）
         *
         * @return 默认系统配置实例
         */
        fun createDefault(): SystemConfig = SystemConfig()

        /**
         * 对外公开的配置验证入口（供测试/外部调用）
         * @param config 要验证的配置
         * @throws ConfigValidationException 当配置无效时
         */
        fun validate(config: SystemConfig) = validateConfig(config)

        /**
         * 内部加载方法（不验证）
         *
         * @param configPath 配置文件路径，可为null
         * @return 加载的配置，不经过验证
         */
        private fun loadInternal(configPath: String?): SystemConfig {
            try {
                // 1. 尝试从指定配置文件加载
                if (!configPath.isNullOrEmpty()) {
                    val file = File(configPath)
                    
                    // 验证文件路径安全性
                    validateFilePath(configPath)
                    
                    if (!file.exists()) {
                        throw IllegalArgumentException("配置文件不存在: $configPath")
                    }

                    if (!file.canRead()) {
                        throw IllegalArgumentException("配置文件无法读取: $configPath")
                    }

                    val tomlContent = try {
                        file.readText()
                    } catch (e: Exception) {
                        throw IllegalArgumentException("无法读取配置文件内容: ${e.message}", e)
                    }
                    
                    val tomlConfig = try {
                        Toml.decodeFromString(serializer<SystemTomlConfig>(), tomlContent)
                    } catch (e: Exception) {
                        throw IllegalArgumentException("配置文件格式错误，无法解析TOML: ${e.message}", e)
                    }

                    return SystemConfig(
                        output = mergeOutputConfig(OutputConfig(), tomlConfig.output),
                        logging = mergeLoggingConfig(LoggingConfig(), tomlConfig.logging),
                        experiment = mergeExperimentConfig(SystemExperimentConfig(), tomlConfig.experiment),
                        jvm = mergeJvmConfig(JvmConfig(), tomlConfig.jvm)
                    )
                }

                // 2. 尝试从环境变量加载
                val envConfig = loadFromEnv()
                if (envConfig != null) {
                    return envConfig
                }

                // 3. 返回默认配置
                return SystemConfig()
            } catch (e: Exception) {
                Logger.error("加载系统配置时发生错误: ${e.message}", e)
                throw e
            }
        }

        /**
         * 验证配置文件路径的安全性，防止路径遍历攻击
         *
         * @param configPath 配置文件路径
         * @throws IllegalArgumentException 如果路径不安全
         */
        private fun validateFilePath(configPath: String) {
            try {
                val path = Paths.get(configPath).normalize()
                val canonicalPath = path.toAbsolutePath().toString()
                
                // 检查路径是否包含非法字符或模式
                if (canonicalPath.contains("..") || canonicalPath.contains("./") || canonicalPath.contains("../")) {
                    throw IllegalArgumentException("配置文件路径包含非法字符，可能存在路径遍历风险: $configPath")
                }
                
                // 确保路径在项目目录内（如果适用）
                val projectRoot = System.getProperty("user.dir")?.let { Paths.get(it) }
                if (projectRoot != null) {
                    try {
                        path.toAbsolutePath().startsWith(projectRoot) // 这会抛出异常如果不在项目目录下
                    } catch (e: Exception) {
                        Logger.warn("无法验证配置文件路径是否在项目目录内: $configPath")
                    }
                }
            } catch (e: InvalidPathException) {
                throw IllegalArgumentException("配置文件路径格式无效: $configPath", e)
            }
        }

        /**
         * 从环境变量加载配置
         *
         * @return 从环境变量加载的配置，如果未设置USE_ENV_CONFIG则返回null
         */
        private fun loadFromEnv(): SystemConfig? {
            // 检查是否存在环境变量配置标识
            val useEnv = System.getenv("USE_ENV_CONFIG")?.toBoolean() ?: false
            if (!useEnv) return null

            return SystemConfig(
                output = OutputConfig(
                    resultsDir = System.getenv("OUTPUT_RESULTS_DIR") ?: OutputConfig().resultsDir,
                    csv = CsvConfig(
                        enabled = System.getenv("CSV_ENABLED")?.toBooleanStrictOrNull() 
                            ?: System.getenv("CSV_ENABLED")?.toBoolean() 
                            ?: CsvConfig().enabled,
                        delimiter = System.getenv("CSV_DELIMITER") ?: CsvConfig().delimiter
                    )
                ),
                logging = LoggingConfig(
                    level = System.getenv("LOGGING_LEVEL") ?: LoggingConfig().level,
                    file = System.getenv("LOGGING_FILE")?.toBooleanStrictOrNull() 
                        ?: System.getenv("LOGGING_FILE")?.toBoolean() 
                        ?: LoggingConfig().file,
                    console = System.getenv("LOGGING_CONSOLE")?.toBooleanStrictOrNull() 
                        ?: System.getenv("LOGGING_CONSOLE")?.toBoolean() 
                        ?: LoggingConfig().console
                ),
                experiment = SystemExperimentConfig(
                    autoCreateDirs = System.getenv("EXPERIMENT_AUTO_CREATE_DIRS")?.toBooleanStrictOrNull() 
                        ?: System.getenv("EXPERIMENT_AUTO_CREATE_DIRS")?.toBoolean() 
                        ?: SystemExperimentConfig().autoCreateDirs,
                    nameFormat = System.getenv("EXPERIMENT_NAME_FORMAT") ?: SystemExperimentConfig().nameFormat,
                    maxConcurrent = System.getenv("EXPERIMENT_MAX_CONCURRENT")?.toIntOrNull() 
                        ?: SystemExperimentConfig().maxConcurrent
                ),
                jvm = JvmConfig(
                    maxHeapSize = System.getenv("JVM_MAX_HEAP_SIZE") ?: JvmConfig().maxHeapSize,
                    gcAlgorithm = System.getenv("JVM_GC_ALGORITHM") ?: JvmConfig().gcAlgorithm
                )
            )
        }

        /**
         * 合并输出配置
         *
         * @param base 基础配置
         * @param toml TOML配置，可能为null
         * @return 合并后的配置
         */
        private fun mergeOutputConfig(base: OutputConfig, toml: OutputTomlConfig?): OutputConfig {
            if (toml == null) return base

            return base.copy(
                resultsDir = toml.resultsDir,
                csv = mergeCsvConfig(base.csv, toml.csv)
            )
        }

        /**
         * 合并CSV配置
         *
         * @param base 基础配置
         * @param toml TOML配置
         * @return 合并后的配置
         */
        private fun mergeCsvConfig(base: CsvConfig, toml: CsvTomlConfig): CsvConfig {
            return base.copy(
                enabled = toml.enabled,
                delimiter = toml.delimiter
            )
        }

        /**
         * 合并日志配置
         *
         * @param base 基础配置
         * @param toml TOML配置，可能为null
         * @return 合并后的配置
         */
        private fun mergeLoggingConfig(base: LoggingConfig, toml: LoggingTomlConfig?): LoggingConfig {
            if (toml == null) return base

            return base.copy(
                level = toml.level,
                file = toml.file,
                console = toml.console
            )
        }

        /**
         * 合并实验配置
         *
         * @param base 基础配置
         * @param toml TOML配置，可能为null
         * @return 合并后的配置
         */
        private fun mergeExperimentConfig(base: SystemExperimentConfig, toml: SystemExperimentTomlConfig?): SystemExperimentConfig {
            if (toml == null) return base

            return base.copy(
                autoCreateDirs = toml.autoCreateDirs,
                nameFormat = toml.nameFormat,
                maxConcurrent = toml.maxConcurrent
            )
        }

        /**
         * 合并JVM配置
         *
         * @param base 基础配置
         * @param toml TOML配置，可能为null
         * @return 合并后的配置
         */
        private fun mergeJvmConfig(base: JvmConfig, toml: JvmTomlConfig?): JvmConfig {
            if (toml == null) return base

            return base.copy(
                maxHeapSize = toml.maxHeapSize,
                gcAlgorithm = toml.gcAlgorithm
            )
        }

        /**
         * 验证配置参数的合理性
         * @param config 要验证的配置
         * @throws ConfigValidationException 当配置无效时，包含详细的验证错误信息
         */
        private fun validateConfig(config: SystemConfig) {
            val errors = mutableListOf<ValidationError>()

            try {
                // 验证输出配置
                validateOutputConfig(config.output, errors)

                // 验证日志配置
                validateLoggingConfig(config.logging, errors)

                // 验证实验配置
                validateExperimentConfig(config.experiment, errors)

                // 验证JVM配置
                validateJvmConfig(config.jvm, errors)

                if (errors.isNotEmpty()) {
                    throw ConfigValidationException("系统配置验证失败，共发现 ${errors.size} 个错误", errors)
                }

                Logger.debug("系统配置验证通过")
            } catch (e: ConfigValidationException) {
                Logger.error("系统配置验证失败: ${e.message}")
                throw e
            } catch (e: Exception) {
                Logger.error("系统配置验证过程中发生意外错误", e)
                throw ConfigValidationException("系统配置验证过程中发生意外错误: ${e.message}", emptyList(), e)
            }
        }

        /**
         * 验证输出配置
         *
         * @param output 输出配置
         * @param errors 错误收集列表
         */
        private fun validateOutputConfig(output: OutputConfig, errors: MutableList<ValidationError>) {
            if (output.resultsDir.isBlank()) {
                errors.add(ValidationError("output.resultsDir", output.resultsDir,
                    "输出目录不能为空"))
            }

            if (output.resultsDir.contains("..")) {
                errors.add(ValidationError("output.resultsDir", output.resultsDir,
                    "输出目录不能包含 '..' 以防止路径遍历"))
            }
            
            try {
                // 尝试创建路径以验证其有效性
                Paths.get(output.resultsDir)
            } catch (e: InvalidPathException) {
                errors.add(ValidationError("output.resultsDir", output.resultsDir,
                    "输出目录路径格式无效: ${e.message}"))
            }
        }

        /**
         * 验证日志配置
         *
         * @param logging 日志配置
         * @param errors 错误收集列表
         */
        private fun validateLoggingConfig(logging: LoggingConfig, errors: MutableList<ValidationError>) {
            val validLevels = listOf("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF")
            if (logging.level.uppercase() !in validLevels) {
                errors.add(ValidationError("logging.level", logging.level,
                    "日志级别必须是以下值之一: ${validLevels.joinToString(", ")}"))
            }
        }

        /**
         * 验证实验配置
         *
         * @param experiment 实验配置
         * @param errors 错误收集列表
         */
        private fun validateExperimentConfig(experiment: SystemExperimentConfig, errors: MutableList<ValidationError>) {
            if (experiment.nameFormat.isBlank()) {
                errors.add(ValidationError("experiment.nameFormat", experiment.nameFormat,
                    "实验名称格式不能为空"))
            }

            if (experiment.maxConcurrent <= 0) {
                errors.add(ValidationError("experiment.maxConcurrent", experiment.maxConcurrent.toString(),
                    "最大并发数必须大于0"))
            }

            val cpuCores = Runtime.getRuntime().availableProcessors()
            if (experiment.maxConcurrent > cpuCores * 4) {
                errors.add(ValidationError("experiment.maxConcurrent", experiment.maxConcurrent.toString(),
                    "最大并发数(${experiment.maxConcurrent})远超CPU核心数(${cpuCores})的4倍，可能导致性能问题"))
            }
        }

        /**
         * 验证JVM配置
         *
         * @param jvm JVM配置
         * @param errors 错误收集列表
         */
        private fun validateJvmConfig(jvm: JvmConfig, errors: MutableList<ValidationError>) {
            val heapSizeRegex = Regex("\\d+[kmg]", RegexOption.IGNORE_CASE)
            if (!heapSizeRegex.matches(jvm.maxHeapSize)) {
                errors.add(ValidationError("jvm.maxHeapSize", jvm.maxHeapSize,
                    "JVM最大堆大小格式无效，应为数字+单位(k/m/g)，如: 2g, 512m, 1024k"))
            }

            val validGcAlgorithms = listOf("G1", "ZGC", "Shenandoah", "CMS", "Serial", "Parallel")
            if (jvm.gcAlgorithm !in validGcAlgorithms) {
                errors.add(ValidationError("jvm.gcAlgorithm", jvm.gcAlgorithm,
                    "JVM垃圾收集算法必须是以下值之一: ${validGcAlgorithms.joinToString(", ")}"))
            }
        }
    }
}

/**
 * 输出配置
 */
data class OutputConfig(
    val resultsDir: String = "runs",
    val csv: CsvConfig = CsvConfig()
)

/**
 * CSV配置
 */
@Serializable
data class CsvConfig(
    val enabled: Boolean = true,
    val delimiter: String = ","
)

/**
 * 日志配置
 */
data class LoggingConfig(
    val level: String = "INFO",
    val file: Boolean = true,
    val console: Boolean = true
)

/**
 * 实验配置（系统层面的实验配置）
 */
data class SystemExperimentConfig(
    val autoCreateDirs: Boolean = true,
    val nameFormat: String = "{mode}_{timestamp}_{algorithms}",
    val maxConcurrent: Int = Runtime.getRuntime().availableProcessors()
)

/**
 * JVM配置
 */
data class JvmConfig(
    val maxHeapSize: String = "2g",
    val gcAlgorithm: String = "G1"
)