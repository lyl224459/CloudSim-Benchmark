package config

import util.Logger

internal object ExperimentConfigParsers {
    fun parseGeneratorType(type: String): CloudletGeneratorType =
        enumValueOrDefault(type, CloudletGeneratorType.LOG_NORMAL) {
            Logger.warn("未知的生成器类型: {}, 使用默认值 LOG_NORMAL", type)
        }

    fun parseExperimentMode(mode: String): ExperimentMode =
        enumValueOrDefault(mode.replace("-", "_"), ExperimentMode.BATCH) {
            Logger.warn("未知的实验模式: {}, 使用默认值 BATCH", mode)
        }

    fun mergeBatchConfig(
        base: BatchConfig,
        toml: TomlBatchConfig?,
    ): BatchConfig = ExperimentTomlParser.mergeBatchConfig(base, toml, ::parseGeneratorType)

    fun mergeRealtimeConfig(
        base: RealtimeConfig,
        toml: TomlRealtimeConfig?,
    ): RealtimeConfig = ExperimentTomlParser.mergeRealtimeConfig(base, toml, ::parseGeneratorType)

    fun normalizeAlgorithmName(name: String): String = ExperimentTomlParser.normalizeAlgorithmName(name)

    private inline fun <reified T : Enum<T>> enumValueOrDefault(
        rawValue: String,
        default: T,
        onFallback: () -> Unit,
    ): T = enumValues<T>().firstOrNull { it.name == rawValue.uppercase() } ?: default.also { onFallback() }
}
