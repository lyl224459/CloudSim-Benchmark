package cli

import config.BatchConfig
import config.ExperimentConfig
import config.ProfileConfig
import config.RealtimeConfig
import config.SystemConfig
import scheduler.ResolvedAlgorithm

private data class ProfileOverrides(
    val runs: Int,
    val taskCounts: List<Int>,
    val systemConfig: SystemConfig,
    val experimentBase: ExperimentConfig,
)

internal object RunConfigOverrideResolver {
    fun apply(
        configs: LoadedRunConfigs,
        command: CliParser.RunCommand,
        profile: ProfileConfig?,
        mode: String,
    ): LoadedRunConfigs {
        val overrides = resolveProfileOverrides(configs, command, profile, mode)
        val experimentConfig =
            when (mode) {
                "batch", "batch-multi" -> overrides.experimentBase.withBatchOverrides(profile, overrides)
                "realtime", "realtime-multi" -> overrides.experimentBase.withRealtimeOverrides(profile, overrides)
                else -> overrides.experimentBase
            }

        ExperimentConfig.validate(experimentConfig)
        SystemConfig.validate(overrides.systemConfig)
        return LoadedRunConfigs(overrides.systemConfig, experimentConfig)
    }

    fun attachAlgorithms(
        config: ExperimentConfig,
        mode: String,
        algorithms: List<ResolvedAlgorithm>,
        taskCounts: List<Int>,
    ): ExperimentConfig {
        val batchAlgorithms = algorithms.mapNotNull { it.definition.legacyBatchType }
        val realtimeAlgorithms = algorithms.mapNotNull { it.definition.legacyRealtimeType }
        return when {
            RunModeResolver.isBatch(mode) ->
                config.copy(
                    batch =
                        config.batch.copy(
                            algorithms = batchAlgorithms,
                            cloudletCounts = taskCounts.ifEmpty { config.batch.cloudletCounts },
                        ),
                )
            mode.startsWith("realtime") ->
                config.copy(
                    realtime =
                        config.realtime.copy(
                            algorithms = realtimeAlgorithms,
                            cloudletCounts = taskCounts.ifEmpty { config.realtime.cloudletCounts },
                        ),
                )
            else -> config
        }
    }

    private fun resolveProfileOverrides(
        configs: LoadedRunConfigs,
        command: CliParser.RunCommand,
        profile: ProfileConfig?,
        mode: String,
    ): ProfileOverrides {
        val experiment = configs.experimentConfig
        return ProfileOverrides(
            runs =
                command.runs ?: profile?.runs
                    ?: if (RunModeResolver.isBatch(mode)) experiment.batch.runs else experiment.realtime.runs,
            taskCounts = RunTaskCountResolver.resolveOverrides(command, profile, mode, experiment),
            systemConfig = resolveSystemConfig(configs.systemConfig, command, profile),
            experimentBase =
                experiment.copy(
                    mode = RunModeResolver.toExperimentMode(mode),
                    randomSeed = command.randomSeed ?: profile?.seed ?: experiment.randomSeed,
                ),
        )
    }

    private fun resolveSystemConfig(
        systemConfig: SystemConfig,
        command: CliParser.RunCommand,
        profile: ProfileConfig?,
    ): SystemConfig {
        val outputDir = command.outputDir ?: profile?.outputDir ?: return systemConfig
        return systemConfig.copy(output = systemConfig.output.copy(resultsDir = outputDir))
    }

    private fun ExperimentConfig.withBatchOverrides(
        profile: ProfileConfig?,
        overrides: ProfileOverrides,
    ): ExperimentConfig =
        copy(
            batch =
                batchConfigFor(profile).copy(
                    runs = overrides.runs,
                    cloudletCounts = overrides.taskCounts.ifEmpty { batch.cloudletCounts },
                ),
            optimizer = optimizer,
        )

    private fun ExperimentConfig.withRealtimeOverrides(
        profile: ProfileConfig?,
        overrides: ProfileOverrides,
    ): ExperimentConfig =
        copy(
            realtime =
                realtimeConfigFor(profile).copy(
                    runs = overrides.runs,
                    cloudletCounts = overrides.taskCounts.ifEmpty { realtime.cloudletCounts },
                ),
            optimizer = optimizer,
        )

    private fun ExperimentConfig.batchConfigFor(profile: ProfileConfig?): BatchConfig =
        profile?.batch?.let { ExperimentConfig.mergeBatchConfig(BatchConfig(), it) } ?: batch

    private fun ExperimentConfig.realtimeConfigFor(profile: ProfileConfig?): RealtimeConfig =
        profile?.realtime?.let { ExperimentConfig.mergeRealtimeConfig(RealtimeConfig(), it) } ?: realtime
}
