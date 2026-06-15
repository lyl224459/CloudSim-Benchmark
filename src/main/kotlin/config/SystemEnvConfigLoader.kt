package config

internal fun interface EnvReader {
    fun get(name: String): String?
}

private object SystemEnvReader : EnvReader {
    override fun get(name: String): String? = System.getenv(name)
}

internal object SystemEnvConfigLoader {
    fun load(env: EnvReader = SystemEnvReader): SystemConfig? {
        if (!envBoolean(env, "USE_ENV_CONFIG", false)) {
            return null
        }
        return SystemConfig(
            output = outputConfig(env),
            logging = loggingConfig(env),
            experiment = experimentConfig(env),
            jvm = jvmConfig(env),
        )
    }

    private fun outputConfig(env: EnvReader): OutputConfig {
        val defaults = OutputConfig()
        return OutputConfig(
            resultsDir = env.get("OUTPUT_RESULTS_DIR") ?: defaults.resultsDir,
            csv = csvConfig(env, defaults.csv),
        )
    }

    private fun csvConfig(
        env: EnvReader,
        defaults: CsvConfig,
    ): CsvConfig =
        CsvConfig(
            enabled = envBoolean(env, "CSV_ENABLED", defaults.enabled),
            delimiter = env.get("CSV_DELIMITER") ?: defaults.delimiter,
        )

    private fun loggingConfig(env: EnvReader): LoggingConfig {
        val defaults = LoggingConfig()
        return LoggingConfig(
            level = env.get("LOGGING_LEVEL") ?: defaults.level,
            file = envBoolean(env, "LOGGING_FILE", defaults.file),
            console = envBoolean(env, "LOGGING_CONSOLE", defaults.console),
        )
    }

    private fun experimentConfig(env: EnvReader): SystemExperimentConfig {
        val defaults = SystemExperimentConfig()
        return SystemExperimentConfig(
            autoCreateDirs = envBoolean(env, "EXPERIMENT_AUTO_CREATE_DIRS", defaults.autoCreateDirs),
            nameFormat = env.get("EXPERIMENT_NAME_FORMAT") ?: defaults.nameFormat,
            maxConcurrent = env.get("EXPERIMENT_MAX_CONCURRENT")?.toIntOrNull() ?: defaults.maxConcurrent,
        )
    }

    private fun jvmConfig(env: EnvReader): JvmConfig {
        val defaults = JvmConfig()
        return JvmConfig(
            maxHeapSize = env.get("JVM_MAX_HEAP_SIZE") ?: defaults.maxHeapSize,
            gcAlgorithm = env.get("JVM_GC_ALGORITHM") ?: defaults.gcAlgorithm,
        )
    }

    private fun envBoolean(
        env: EnvReader,
        name: String,
        default: Boolean,
    ): Boolean {
        val value = env.get(name)
        return value?.toBooleanStrictOrNull() ?: default
    }
}
