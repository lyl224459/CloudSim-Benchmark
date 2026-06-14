package config

internal object SystemConfigMapper {
    fun fromTomlConfig(tomlConfig: SystemTomlConfig): SystemConfig {
        val config =
            SystemConfig(
                output = mergeOutputConfig(OutputConfig(), tomlConfig.output),
                logging = mergeLoggingConfig(LoggingConfig(), tomlConfig.logging),
                experiment = mergeExperimentConfig(SystemExperimentConfig(), tomlConfig.experiment),
                jvm = mergeJvmConfig(JvmConfig(), tomlConfig.jvm),
            )
        SystemConfigValidator.validate(config)
        return config
    }

    private fun mergeOutputConfig(
        base: OutputConfig,
        toml: OutputTomlConfig?,
    ): OutputConfig =
        toml?.let {
            base.copy(
                resultsDir = it.resultsDir,
                csv = mergeCsvConfig(base.csv, it.csv),
            )
        } ?: base

    private fun mergeCsvConfig(
        base: CsvConfig,
        toml: CsvTomlConfig,
    ): CsvConfig =
        base.copy(
            enabled = toml.enabled,
            delimiter = toml.delimiter,
        )

    private fun mergeLoggingConfig(
        base: LoggingConfig,
        toml: LoggingTomlConfig?,
    ): LoggingConfig =
        toml?.let {
            base.copy(
                level = it.level,
                file = it.file,
                console = it.console,
            )
        } ?: base

    private fun mergeExperimentConfig(
        base: SystemExperimentConfig,
        toml: SystemExperimentTomlConfig?,
    ): SystemExperimentConfig =
        toml?.let {
            base.copy(
                autoCreateDirs = it.autoCreateDirs,
                nameFormat = it.nameFormat,
                maxConcurrent = it.maxConcurrent,
            )
        } ?: base

    private fun mergeJvmConfig(
        base: JvmConfig,
        toml: JvmTomlConfig?,
    ): JvmConfig =
        toml?.let {
            base.copy(
                maxHeapSize = it.maxHeapSize,
                gcAlgorithm = it.gcAlgorithm,
            )
        } ?: base
}
