package cli

import config.ExperimentConfig
import config.PresetConfig
import config.ProfileConfig
import scheduler.AlgorithmDefinition
import scheduler.AlgorithmMode
import scheduler.AlgorithmRegistry
import scheduler.ResolvedAlgorithm
import scheduler.ResolvedAlgorithmSettings

internal object RunAlgorithmResolver {
    fun resolve(
        mode: String,
        command: CliParser.RunCommand,
        config: ExperimentConfig,
        profile: ProfileConfig?,
    ): List<ResolvedAlgorithm> {
        val algorithmMode = if (RunModeResolver.isBatch(mode)) AlgorithmMode.BATCH else AlgorithmMode.REALTIME
        val names =
            when {
                command.algorithms.isNotEmpty() -> command.algorithms
                !command.preset.isNullOrBlank() -> findPreset(config.presets, command.preset).algorithms
                !profile?.algorithms.isNullOrEmpty() -> profile.algorithms
                !profile?.preset.isNullOrBlank() -> findPreset(config.presets, profile.preset).algorithms
                else -> enabledAlgorithmNames(config, algorithmMode)
            }

        return AlgorithmRegistry.resolveAll(algorithmMode, names).map { definition ->
            ResolvedAlgorithm(definition, resolveSettings(definition, config, mode))
        }
    }

    private fun enabledAlgorithmNames(
        config: ExperimentConfig,
        mode: AlgorithmMode,
    ): List<String> {
        val definitions = AlgorithmRegistry.forMode(mode)
        return definitions
            .filter { definition ->
                config.algorithmConfigs[definition.name]?.enabled ?: definition.defaultEnabled
            }.map { it.name }
    }

    private fun resolveSettings(
        definition: AlgorithmDefinition,
        config: ExperimentConfig,
        mode: String,
    ): ResolvedAlgorithmSettings {
        val override = config.algorithmConfigs[definition.name]
        val batchMode = RunModeResolver.isBatch(mode)
        val defaultPopulation = if (batchMode) config.batch.population else config.optimizer.population
        val defaultMaxIter = if (batchMode) config.batch.maxIter else config.optimizer.maxIter
        return ResolvedAlgorithmSettings(
            population = override?.population ?: defaultPopulation,
            maxIter = override?.maxIter ?: defaultMaxIter,
        )
    }

    private fun findPreset(
        presets: Map<String, PresetConfig>,
        presetName: String,
    ): PresetConfig {
        val normalizedPresetName = ExperimentConfig.normalizeAlgorithmName(presetName)
        val preset =
            presets.entries
                .firstOrNull { (name, _) ->
                    name.equals(presetName, ignoreCase = true) ||
                        ExperimentConfig.normalizeAlgorithmName(name) == normalizedPresetName
                }?.value
        return preset ?: throw IllegalArgumentException(
            "未知预设: $presetName。可用预设: ${presets.keys.sorted().joinToString(", ").ifBlank { "(无)" }}",
        )
    }
}
