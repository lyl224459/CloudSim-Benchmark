package cli

import config.AlgorithmConfig
import config.BatchAlgorithmType
import config.ConfigurationManager
import config.ExperimentConfig
import config.ExperimentMode
import config.RealtimeAlgorithmType
import config.SystemConfig
import scheduler.AlgorithmMode
import scheduler.AlgorithmRegistry
import util.Logger
import java.io.File
import java.io.IOException

object RunResolver {
    fun resolve(command: CliParser.RunCommand): ResolvedExperimentConfig {
        val loadedConfigs = mergeAlgorithmLibrary(loadBaseConfigs(command.configFile))
        val profileSelection =
            RunProfileResolver.select(
                loadedConfigs.experimentConfig,
                command.profile,
                command.configFile,
            )
        val selectedProfile = profileSelection?.profile
        val resolvedMode = RunModeResolver.resolve(command, selectedProfile, loadedConfigs.experimentConfig)
        val configsWithProfile =
            RunConfigOverrideResolver.apply(
                configs = loadedConfigs,
                command = command.copy(mode = resolvedMode),
                profile = selectedProfile,
                mode = resolvedMode,
            )
        val resolvedAlgorithms =
            RunAlgorithmResolver.resolve(
                mode = resolvedMode,
                command = command,
                config = configsWithProfile.experimentConfig,
                profile = selectedProfile,
            )
        val taskCounts =
            RunTaskCountResolver.resolveFinal(
                resolvedMode,
                command,
                configsWithProfile.experimentConfig,
                selectedProfile,
            )
        return ResolvedRunBuilder.build(
            command = command,
            configs = configsWithProfile,
            mode = resolvedMode,
            profileSelection = profileSelection,
            selection = ResolvedRunSelection(resolvedAlgorithms, taskCounts),
        )
    }

    fun loadBaseConfigs(configFile: String?): LoadedRunConfigs =
        if (!configFile.isNullOrBlank()) {
            ConfigurationManager.loadFromSingleFile(configFile).toRunConfigs()
        } else {
            LoadedRunConfigs(
                systemConfig = SystemConfig.createDefault(),
                experimentConfig = ExperimentConfig.createDefault(),
            )
        }

    fun mergeAlgorithmLibrary(configs: LoadedRunConfigs): LoadedRunConfigs {
        val libraryFile = defaultAlgorithmLibraryFile()
        return mergeAlgorithmLibrary(configs, libraryFile)
    }

    internal fun mergeAlgorithmLibrary(
        configs: LoadedRunConfigs,
        libraryFile: File,
    ): LoadedRunConfigs {
        if (!libraryFile.exists() || libraryFile.length() == 0L) return configs

        return try {
            val libraryConfig = ExperimentConfig.loadLibrary(libraryFile.absolutePath)
            configs.copy(
                experimentConfig =
                    configs.experimentConfig.copy(
                        algorithmConfigs = libraryConfig.algorithmConfigs + configs.experimentConfig.algorithmConfigs,
                        presets = libraryConfig.presets + configs.experimentConfig.presets,
                    ),
            )
        } catch (exception: IllegalArgumentException) {
            logSkippedAlgorithmLibrary(exception)
            configs
        } catch (exception: IllegalStateException) {
            logSkippedAlgorithmLibrary(exception)
            configs
        } catch (exception: SecurityException) {
            logSkippedAlgorithmLibrary(exception)
            configs
        } catch (exception: IOException) {
            logSkippedAlgorithmLibrary(exception)
            configs
        }
    }

    private fun defaultAlgorithmLibraryFile(): File = File("configs/algorithms.toml")

    fun renderExperimentName(
        resolved: ResolvedExperimentConfig,
        timestamp: String,
    ): String {
        val taskToken =
            when (resolved.mode) {
                "batch-multi", "realtime-multi" -> resolved.taskCounts.joinToString("-")
                "batch" -> resolved.batch.cloudletCount.toString()
                "realtime" -> resolved.realtime.cloudletCount.toString()
                else -> ""
            }
        val algorithmToken = resolved.selectedAlgorithmNames.joinToString("+").ifBlank { "ALL" }
        val rendered =
            resolved.output.nameFormat
                .replace("{mode}", resolved.mode)
                .replace("{timestamp}", timestamp)
                .replace("{algorithms}", algorithmToken)
                .replace("{preset}", resolved.presetName ?: "none")
                .replace("{tasks}", taskToken)

        return rendered.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_').ifBlank {
            "${resolved.mode}_$timestamp"
        }
    }

    fun parseBatchAlgorithms(algorithmNames: List<String>): List<BatchAlgorithmType> =
        AlgorithmRegistry.resolveAll(AlgorithmMode.BATCH, algorithmNames).mapNotNull { it.legacyBatchType }

    fun parseRealtimeAlgorithms(algorithmNames: List<String>): List<RealtimeAlgorithmType> =
        AlgorithmRegistry.resolveAll(AlgorithmMode.REALTIME, algorithmNames).mapNotNull { it.legacyRealtimeType }

    private fun ConfigurationManager.LoadedConfigs.toRunConfigs() = LoadedRunConfigs(systemConfig, experimentConfig)

    private fun logSkippedAlgorithmLibrary(exception: Exception) {
        Logger.warn("加载算法库配置失败，已跳过 configs/algorithms.toml: {}", exception.message)
    }
}

fun resolveRun(command: CliParser.RunCommand): ResolvedExperimentConfig = RunResolver.resolve(command)

internal fun applyRunOverrides(
    configs: ConfigurationManager.LoadedConfigs,
    command: CliParser.RunCommand,
    selectionAlgorithmConfigs: Map<String, AlgorithmConfig> = configs.experimentConfig.algorithmConfigs,
): ConfigurationManager.LoadedConfigs {
    val mode =
        command.mode?.let { normalizeMode(it) } ?: configs.experimentConfig.mode.name
            .lowercase()
            .replace("_", "-")
    val systemConfig =
        command.outputDir?.let { outputDir ->
            configs.systemConfig.copy(output = configs.systemConfig.output.copy(resultsDir = outputDir))
        } ?: configs.systemConfig

    val experimentConfig =
        configs.experimentConfig.copy(
            mode = ExperimentMode.valueOf(mode.uppercase().replace("-", "_")),
            randomSeed = command.randomSeed ?: configs.experimentConfig.randomSeed,
            algorithmConfigs = selectionAlgorithmConfigs,
        )

    ExperimentConfig.validate(experimentConfig)
    SystemConfig.validate(systemConfig)
    return ConfigurationManager.LoadedConfigs(systemConfig, experimentConfig)
}

internal fun parseBatchAlgorithms(algorithmNames: List<String>) = RunResolver.parseBatchAlgorithms(algorithmNames)

internal fun parseRealtimeAlgorithms(algorithmNames: List<String>): List<RealtimeAlgorithmType> =
    RunResolver.parseRealtimeAlgorithms(algorithmNames)
