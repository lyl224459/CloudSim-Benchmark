package config

internal object SystemEnvConfigLoader {
    fun load(): SystemConfig? {
        if (!envBoolean("USE_ENV_CONFIG", false)) {
            return null
        }
        return SystemConfig(
            output = outputConfig(),
            logging = loggingConfig(),
            experiment = experimentConfig(),
            jvm = jvmConfig(),
        )
    }

    private fun outputConfig(): OutputConfig {
        val defaults = OutputConfig()
        return OutputConfig(
            resultsDir = System.getenv("OUTPUT_RESULTS_DIR") ?: defaults.resultsDir,
            csv = csvConfig(defaults.csv),
        )
    }

    private fun csvConfig(defaults: CsvConfig): CsvConfig =
        CsvConfig(
            enabled = envBoolean("CSV_ENABLED", defaults.enabled),
            delimiter = System.getenv("CSV_DELIMITER") ?: defaults.delimiter,
        )

    private fun loggingConfig(): LoggingConfig {
        val defaults = LoggingConfig()
        return LoggingConfig(
            level = System.getenv("LOGGING_LEVEL") ?: defaults.level,
            file = envBoolean("LOGGING_FILE", defaults.file),
            console = envBoolean("LOGGING_CONSOLE", defaults.console),
        )
    }

    private fun experimentConfig(): SystemExperimentConfig {
        val defaults = SystemExperimentConfig()
        return SystemExperimentConfig(
            autoCreateDirs = envBoolean("EXPERIMENT_AUTO_CREATE_DIRS", defaults.autoCreateDirs),
            nameFormat = System.getenv("EXPERIMENT_NAME_FORMAT") ?: defaults.nameFormat,
            maxConcurrent = System.getenv("EXPERIMENT_MAX_CONCURRENT")?.toIntOrNull() ?: defaults.maxConcurrent,
        )
    }

    private fun jvmConfig(): JvmConfig {
        val defaults = JvmConfig()
        return JvmConfig(
            maxHeapSize = System.getenv("JVM_MAX_HEAP_SIZE") ?: defaults.maxHeapSize,
            gcAlgorithm = System.getenv("JVM_GC_ALGORITHM") ?: defaults.gcAlgorithm,
        )
    }

    private fun envBoolean(
        name: String,
        default: Boolean,
    ): Boolean {
        val value = System.getenv(name)
        return value?.toBooleanStrictOrNull() ?: value?.toBoolean() ?: default
    }
}
