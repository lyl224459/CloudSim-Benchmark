package config

import kotlinx.serialization.Serializable

@Serializable
data class SystemTomlConfig(
    val output: OutputTomlConfig? = null,
    val logging: LoggingTomlConfig? = null,
    val experiment: SystemExperimentTomlConfig? = null,
    val jvm: JvmTomlConfig? = null,
)

@Serializable
data class OutputTomlConfig(
    val resultsDir: String = "runs",
    val csv: CsvTomlConfig = CsvTomlConfig(),
)

@Serializable
data class CsvTomlConfig(
    val enabled: Boolean = true,
    val delimiter: String = ",",
)

@Serializable
data class LoggingTomlConfig(
    val level: String = "INFO",
    val file: Boolean = true,
    val console: Boolean = true,
)

@Serializable
data class SystemExperimentTomlConfig(
    val autoCreateDirs: Boolean = true,
    val nameFormat: String = "{mode}_{timestamp}_{algorithms}",
    val maxConcurrent: Int = Runtime.getRuntime().availableProcessors(),
)

@Serializable
data class JvmTomlConfig(
    val maxHeapSize: String = "2g",
    val gcAlgorithm: String = "G1",
)
