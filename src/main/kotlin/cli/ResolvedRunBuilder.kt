package cli

import scheduler.ResolvedAlgorithm

internal data class ResolvedRunSelection(
    val algorithms: List<ResolvedAlgorithm>,
    val taskCounts: List<Int>,
)

internal object ResolvedRunBuilder {
    fun build(
        command: CliParser.RunCommand,
        configs: LoadedRunConfigs,
        mode: String,
        profileSelection: SelectedProfile?,
        selection: ResolvedRunSelection,
    ): ResolvedExperimentConfig {
        val selectedProfile = profileSelection?.profile
        val experimentConfig =
            RunConfigOverrideResolver.attachAlgorithms(
                configs.experimentConfig,
                mode,
                selection.algorithms,
                selection.taskCounts,
            )
        val systemConfig = configs.systemConfig
        return ResolvedExperimentConfig(
            command = command.copy(mode = mode),
            systemConfig = systemConfig,
            experimentConfig = experimentConfig,
            mode = mode,
            profile =
                ResolvedProfile(
                    name = profileSelection?.name,
                    presetName = command.preset ?: selectedProfile?.preset,
                ),
            algorithms = selection.algorithms,
            taskCounts = selection.taskCounts,
            execution =
                ResolvedExecutionOptions(
                    useCoroutines = command.useCoroutines,
                    maxConcurrency = command.maxConcurrency,
                    dryRun = command.dryRun,
                ),
            output =
                ResolvedOutputConfig(
                    resultsDir = systemConfig.output.resultsDir,
                    csvEnabled = systemConfig.output.csv.enabled,
                    csvDelimiter = systemConfig.output.csv.delimiter,
                    nameFormat = systemConfig.experiment.nameFormat,
                ),
        )
    }
}
