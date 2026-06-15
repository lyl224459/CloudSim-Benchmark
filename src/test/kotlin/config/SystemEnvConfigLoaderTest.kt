package config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SystemEnvConfigLoaderTest {
    @Test
    fun `disabled environment config returns null`() {
        assertThat(SystemEnvConfigLoader.load(mapEnv())).isNull()
    }

    @Test
    fun `environment config maps all supported values`() {
        val config =
            requireNotNull(
                SystemEnvConfigLoader.load(
                    mapEnv(
                        "USE_ENV_CONFIG" to "true",
                        "OUTPUT_RESULTS_DIR" to "custom-runs",
                        "CSV_ENABLED" to "false",
                        "CSV_DELIMITER" to ";",
                        "LOGGING_LEVEL" to "DEBUG",
                        "LOGGING_FILE" to "true",
                        "LOGGING_CONSOLE" to "false",
                        "EXPERIMENT_AUTO_CREATE_DIRS" to "false",
                        "EXPERIMENT_NAME_FORMAT" to "custom",
                        "EXPERIMENT_MAX_CONCURRENT" to "7",
                        "JVM_MAX_HEAP_SIZE" to "2g",
                        "JVM_GC_ALGORITHM" to "ZGC",
                    ),
                ),
            )

        assertThat(config.output.resultsDir).isEqualTo("custom-runs")
        assertThat(config.output.csv.enabled).isFalse()
        assertThat(config.output.csv.delimiter).isEqualTo(";")
        assertThat(config.logging).isEqualTo(LoggingConfig(level = "DEBUG", file = true, console = false))
        assertThat(config.experiment).isEqualTo(SystemExperimentConfig(false, "custom", 7))
        assertThat(config.jvm).isEqualTo(JvmConfig("2g", "ZGC"))
    }

    @Test
    fun `missing and invalid environment values use defaults`() {
        val defaults = SystemConfig()
        val config =
            requireNotNull(
                SystemEnvConfigLoader.load(
                    mapEnv(
                        "USE_ENV_CONFIG" to "true",
                        "CSV_ENABLED" to "invalid",
                        "LOGGING_FILE" to "invalid",
                        "EXPERIMENT_MAX_CONCURRENT" to "invalid",
                    ),
                ),
            )

        assertThat(config.output).isEqualTo(defaults.output)
        assertThat(config.logging.file).isEqualTo(defaults.logging.file)
        assertThat(config.experiment.maxConcurrent).isEqualTo(defaults.experiment.maxConcurrent)
        assertThat(config.jvm).isEqualTo(defaults.jvm)
    }

    private fun mapEnv(vararg values: Pair<String, String>): EnvReader {
        val env = mapOf(*values)
        return EnvReader(env::get)
    }
}
