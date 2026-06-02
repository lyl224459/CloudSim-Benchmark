package config

/**
 * 配置验证异常
 * 包含详细的验证错误信息
 */
class ConfigValidationException(
    message: String,
    val errors: List<ValidationError>,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause) {
    override fun toString(): String {
        val errorDetails =
            if (errors.isNotEmpty()) {
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
    val field: String, // 字段名
    val value: String, // 当前值
    val message: String, // 错误消息
)

/**
 * 实验模式枚举
 */
enum class ExperimentMode {
    BATCH,
    REALTIME,
    BATCH_MULTI,
    REALTIME_MULTI,
}

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
    val presets: Map<String, PresetConfig> = emptyMap(),
) {
    companion object {
        fun load(configPath: String): ExperimentConfig = ExperimentConfigLoader.load(configPath, requireProfiles = true)

        fun loadLibrary(configPath: String): ExperimentConfig {
            val requireProfiles = false
            return ExperimentConfigLoader.load(configPath, requireProfiles)
        }

        fun createDefault(): ExperimentConfig = ExperimentConfig()

        fun validate(config: ExperimentConfig) = ExperimentConfigValidator.validate(config)

        fun parseGeneratorType(type: String): CloudletGeneratorType = ExperimentConfigParsers.parseGeneratorType(type)

        internal fun fromTomlConfig(
            tomlConfig: ExperimentTomlConfig,
            requireProfiles: Boolean = true,
        ): ExperimentConfig = ExperimentConfigLoader.fromTomlConfig(tomlConfig, requireProfiles)

        internal fun validateDynamicTomlSections(
            content: String,
            tomlConfig: ExperimentTomlConfig,
        ) = ExperimentConfigLoader.validateDynamicTomlSections(content, tomlConfig)

        internal fun mergeBatchConfig(
            base: BatchConfig,
            toml: TomlBatchConfig?,
        ): BatchConfig = ExperimentConfigParsers.mergeBatchConfig(base, toml)

        internal fun mergeRealtimeConfig(
            base: RealtimeConfig,
            toml: TomlRealtimeConfig?,
        ): RealtimeConfig = ExperimentConfigParsers.mergeRealtimeConfig(base, toml)

        fun normalizeAlgorithmName(name: String): String = ExperimentConfigParsers.normalizeAlgorithmName(name)
    }
}
