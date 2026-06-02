package config

import kotlinx.serialization.Serializable

data class OutputConfig(
    val resultsDir: String = "runs",
    val csv: CsvConfig = CsvConfig(),
)

@Serializable
data class CsvConfig(
    val enabled: Boolean = true,
    val delimiter: String = ",",
)

data class LoggingConfig(
    val level: String = "INFO",
    val file: Boolean = true,
    val console: Boolean = true,
)

data class SystemExperimentConfig(
    val autoCreateDirs: Boolean = true,
    val nameFormat: String = "{mode}_{timestamp}_{algorithms}",
    val maxConcurrent: Int = Runtime.getRuntime().availableProcessors(),
)

data class JvmConfig(
    val maxHeapSize: String = "2g",
    val gcAlgorithm: String = "G1",
)
