package cli

import config.BatchConfig
import config.ExperimentConfig
import config.RealtimeConfig
import config.SystemConfig
import scheduler.ResolvedAlgorithm

data class ResolvedProfile(
    val name: String?,
    val presetName: String?
)

data class ResolvedExecutionOptions(
    val useCoroutines: Boolean,
    val maxConcurrency: Int,
    val dryRun: Boolean
)

data class ResolvedOutputConfig(
    val resultsDir: String,
    val csvEnabled: Boolean,
    val csvDelimiter: String,
    val nameFormat: String
)

data class ResolvedExperimentConfig(
    val command: CliParser.RunCommand,
    val systemConfig: SystemConfig,
    val experimentConfig: ExperimentConfig,
    val mode: String,
    val profile: ResolvedProfile,
    val algorithms: List<ResolvedAlgorithm>,
    val taskCounts: List<Int>,
    val execution: ResolvedExecutionOptions,
    val output: ResolvedOutputConfig
) {
    val selectedAlgorithmNames: List<String> = algorithms.map { it.name }
    val profileName: String? = profile.name
    val presetName: String? = profile.presetName
    val batch: BatchConfig get() = experimentConfig.batch
    val realtime: RealtimeConfig get() = experimentConfig.realtime
    val randomSeed: Long get() = experimentConfig.randomSeed
    val runs: Int get() = if (mode.startsWith("batch")) batch.runs else realtime.runs
}

data class LoadedRunConfigs(
    val systemConfig: SystemConfig,
    val experimentConfig: ExperimentConfig
)
