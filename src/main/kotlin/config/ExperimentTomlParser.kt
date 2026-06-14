package config

internal object ExperimentTomlParser {
    fun normalizeAlgorithmName(name: String): String =
        name
            .trim()
            .replace("-", "_")
            .replace(" ", "_")
            .uppercase()

    fun mergeBatchConfig(
        base: BatchConfig,
        toml: TomlBatchConfig?,
        parseGeneratorType: (String) -> CloudletGeneratorType,
    ): BatchConfig {
        if (toml == null) return base

        val generatorType =
            if (toml.generator.type != "LOG_NORMAL") {
                parseGeneratorType(toml.generator.type)
            } else {
                parseGeneratorType(toml.generatorType)
            }

        return base.copy(
            cloudletCount = toml.cloudletCount,
            cloudletCounts = toml.cloudletCounts,
            population = toml.population,
            maxIter = toml.maxIter,
            runs = toml.runs,
            generatorType = generatorType,
            googleTraceConfig = toml.googleTrace ?: base.googleTraceConfig,
            objectiveWeights = toml.objective,
        )
    }

    fun mergeRealtimeConfig(
        base: RealtimeConfig,
        toml: TomlRealtimeConfig?,
        parseGeneratorType: (String) -> CloudletGeneratorType,
    ): RealtimeConfig {
        if (toml == null) return base

        val generatorType =
            if (toml.generator.type != "LOG_NORMAL") {
                parseGeneratorType(toml.generator.type)
            } else {
                parseGeneratorType(toml.generatorType)
            }

        return base.copy(
            cloudletCount = toml.cloudletCount,
            simulationDuration = toml.simulationDuration,
            arrivalRate = toml.arrivalRate,
            cloudletCounts = toml.cloudletCounts,
            runs = toml.runs,
            generatorType = generatorType,
            googleTraceConfig = toml.googleTrace ?: base.googleTraceConfig,
            objectiveWeights = toml.objective,
            arrival = toml.arrival,
            scheduling = toml.scheduling,
        )
    }
}
