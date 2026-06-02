package cli

import config.AlgorithmConfig
import config.BatchAlgorithmType
import config.BatchConfig
import config.ConfigurationManager
import config.ExperimentConfig
import config.ExperimentMode
import config.PresetConfig
import config.ProfileConfig
import config.RealtimeAlgorithmType
import config.RealtimeConfig
import config.SystemConfig
import scheduler.AlgorithmDefinition
import scheduler.AlgorithmMode
import scheduler.AlgorithmRegistry
import scheduler.ResolvedAlgorithm
import scheduler.ResolvedAlgorithmSettings
import util.Logger
import java.io.File
import java.io.IOException

private const val DEFAULT_MULTI_TASK_COUNT_50 = 50
private const val DEFAULT_MULTI_TASK_COUNT_100 = 100
private const val DEFAULT_MULTI_TASK_COUNT_200 = 200
private const val DEFAULT_MULTI_TASK_COUNT_500 = 500

private val defaultMultiTaskCounts =
    listOf(
        DEFAULT_MULTI_TASK_COUNT_50,
        DEFAULT_MULTI_TASK_COUNT_100,
        DEFAULT_MULTI_TASK_COUNT_200,
        DEFAULT_MULTI_TASK_COUNT_500,
    )

object RunResolver {
    fun resolve(command: CliParser.RunCommand): ResolvedExperimentConfig {
        val rawConfigs = loadBaseConfigs(command.configFile)
        val loadedConfigs = mergeAlgorithmLibrary(rawConfigs)
        val profileSelection = selectProfile(loadedConfigs.experimentConfig, command.profile, command.configFile)
        val selectedProfile = profileSelection?.profile
        val resolvedMode = resolveRunMode(command, selectedProfile, loadedConfigs.experimentConfig)
        val configsWithProfile =
            applyProfileAndOverrides(
                configs = loadedConfigs,
                command = command.copy(mode = resolvedMode),
                profile = selectedProfile,
                mode = resolvedMode,
            )
        val resolvedAlgorithms =
            resolveAlgorithmsForRun(
                mode = resolvedMode,
                command = command,
                config = configsWithProfile.experimentConfig,
                profile = selectedProfile,
            )
        val taskCounts = resolveTaskCounts(resolvedMode, command, configsWithProfile.experimentConfig, selectedProfile)
        val experimentConfig =
            attachResolvedAlgorithms(
                configsWithProfile.experimentConfig,
                resolvedMode,
                resolvedAlgorithms,
                taskCounts,
            )
        val finalConfigs = configsWithProfile.copy(experimentConfig = experimentConfig)
        val systemConfig = finalConfigs.systemConfig

        return ResolvedExperimentConfig(
            command = command.copy(mode = resolvedMode),
            systemConfig = systemConfig,
            experimentConfig = finalConfigs.experimentConfig,
            mode = resolvedMode,
            profile =
                ResolvedProfile(
                    name = profileSelection?.name,
                    presetName = command.preset ?: selectedProfile?.preset,
                ),
            algorithms = resolvedAlgorithms,
            taskCounts = taskCounts,
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
        val libraryFile = File("configs/algorithms.toml")
        if (!libraryFile.exists() || libraryFile.length() == 0L) {
            return configs
        }

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

    private data class SelectedProfile(
        val name: String,
        val profile: ProfileConfig,
    )

    private data class ProfileOverrides(
        val runs: Int,
        val taskCounts: List<Int>,
        val systemConfig: SystemConfig,
        val experimentBase: ExperimentConfig,
    )

    private fun selectProfile(
        config: ExperimentConfig,
        requestedProfile: String?,
        configFile: String?,
    ): SelectedProfile? {
        if (config.profiles.isEmpty()) {
            require(requestedProfile == null) {
                "配置文件未定义 profiles，不能使用 --profile $requestedProfile"
            }
            return null
        }

        val availableProfiles =
            config.profiles.keys
                .sorted()
                .joinToString(", ")
        val profileName =
            when {
                !requestedProfile.isNullOrBlank() -> requestedProfile
                !config.defaultProfile.isNullOrBlank() -> config.defaultProfile
                else -> throw IllegalArgumentException(
                    missingProfileSelectionMessage(configFile, availableProfiles),
                )
            }

        val profileEntry =
            config.profiles.entries.firstOrNull { (name, _) ->
                name.equals(profileName, ignoreCase = true) ||
                    hasSameNormalizedName(name, profileName)
            } ?: throw IllegalArgumentException("未知 profile: $profileName。可用 profiles: $availableProfiles")

        return SelectedProfile(profileEntry.key, profileEntry.value)
    }

    private fun resolveRunMode(
        command: CliParser.RunCommand,
        profile: ProfileConfig?,
        config: ExperimentConfig,
    ): String {
        val commandMode = command.mode?.let { normalizeMode(it) }
        if (commandMode != null) {
            require(commandMode in supportedModes()) {
                invalidRunModeMessage(commandMode)
            }
            return commandMode
        }

        val profileMode = profile?.mode?.takeIf { it.isNotBlank() }?.let { normalizeMode(it) }
        if (profileMode != null) {
            require(profileMode in supportedModes()) {
                invalidRunModeMessage(profileMode)
            }
            return profileMode
        }

        require(config.profiles.isEmpty()) {
            "run 命令缺少 --mode，且所选 profile 未定义 mode"
        }

        throw IllegalArgumentException("run 命令需要 --mode batch|realtime|batch-multi|realtime-multi，或使用包含 profiles 的配置文件")
    }

    private fun applyProfileAndOverrides(
        configs: LoadedRunConfigs,
        command: CliParser.RunCommand,
        profile: ProfileConfig?,
        mode: String,
    ): LoadedRunConfigs {
        val overrides = resolveProfileOverrides(configs, command, profile, mode)
        val experimentConfig =
            when (mode) {
                "batch", "batch-multi" ->
                    overrides.experimentBase.copy(
                        batch =
                            batchConfigFor(profile, overrides.experimentBase).copy(
                                runs = overrides.runs,
                                cloudletCounts =
                                    overrides.taskCounts.ifEmpty {
                                        overrides.experimentBase.batch.cloudletCounts
                                    },
                            ),
                        optimizer = overrides.experimentBase.optimizer,
                    )
                "realtime", "realtime-multi" ->
                    overrides.experimentBase.copy(
                        realtime =
                            realtimeConfigFor(profile, overrides.experimentBase).copy(
                                runs = overrides.runs,
                                cloudletCounts =
                                    overrides.taskCounts.ifEmpty {
                                        overrides.experimentBase.realtime.cloudletCounts
                                    },
                            ),
                        optimizer = overrides.experimentBase.optimizer,
                    )
                else -> overrides.experimentBase
            }

        ExperimentConfig.validate(experimentConfig)
        SystemConfig.validate(overrides.systemConfig)
        return LoadedRunConfigs(overrides.systemConfig, experimentConfig)
    }

    private fun resolveProfileOverrides(
        configs: LoadedRunConfigs,
        command: CliParser.RunCommand,
        profile: ProfileConfig?,
        mode: String,
    ): ProfileOverrides {
        val experiment = configs.experimentConfig
        val experimentBase =
            experiment.copy(
                mode = mode.toExperimentMode(),
                randomSeed = command.randomSeed ?: profile?.seed ?: experiment.randomSeed,
            )
        return ProfileOverrides(
            runs = resolveRuns(command, profile, mode, experiment),
            taskCounts = resolveOverrideTaskCounts(command, profile, mode, experiment),
            systemConfig = resolveSystemConfig(configs.systemConfig, command, profile),
            experimentBase = experimentBase,
        )
    }

    private fun resolveRuns(
        command: CliParser.RunCommand,
        profile: ProfileConfig?,
        mode: String,
        experiment: ExperimentConfig,
    ): Int =
        command.runs ?: profile?.runs
            ?: if (mode.isBatchMode()) experiment.batch.runs else experiment.realtime.runs

    private fun resolveOverrideTaskCounts(
        command: CliParser.RunCommand,
        profile: ProfileConfig?,
        mode: String,
        experiment: ExperimentConfig,
    ): List<Int> {
        if (!mode.isMultiMode()) return emptyList()
        return command.taskCounts.ifEmpty {
            profile?.tasks ?: if (mode.isBatchMode()) {
                experiment.batch.cloudletCounts
            } else {
                experiment.realtime.cloudletCounts
            }
        }
    }

    private fun resolveSystemConfig(
        systemConfig: SystemConfig,
        command: CliParser.RunCommand,
        profile: ProfileConfig?,
    ): SystemConfig {
        val outputDir = command.outputDir ?: profile?.outputDir ?: return systemConfig
        return systemConfig.copy(output = systemConfig.output.copy(resultsDir = outputDir))
    }

    private fun batchConfigFor(
        profile: ProfileConfig?,
        experimentBase: ExperimentConfig,
    ): BatchConfig =
        profile?.batch?.let { ExperimentConfig.mergeBatchConfig(BatchConfig(), it) }
            ?: experimentBase.batch

    private fun realtimeConfigFor(
        profile: ProfileConfig?,
        experimentBase: ExperimentConfig,
    ): RealtimeConfig =
        profile?.realtime?.let { ExperimentConfig.mergeRealtimeConfig(RealtimeConfig(), it) }
            ?: experimentBase.realtime

    private fun resolveAlgorithmsForRun(
        mode: String,
        command: CliParser.RunCommand,
        config: ExperimentConfig,
        profile: ProfileConfig?,
    ): List<ResolvedAlgorithm> {
        val algorithmMode = if (mode.startsWith("batch")) AlgorithmMode.BATCH else AlgorithmMode.REALTIME
        val names =
            when {
                command.algorithms.isNotEmpty() -> command.algorithms
                !command.preset.isNullOrBlank() -> findPreset(config.presets, command.preset).algorithms
                !profile?.algorithms.isNullOrEmpty() -> profile.algorithms
                !profile?.preset.isNullOrBlank() -> findPreset(config.presets, profile.preset).algorithms
                else -> enabledAlgorithmNames(config, algorithmMode)
            }

        val definitions = AlgorithmRegistry.resolveAll(algorithmMode, names)
        return definitions.map { definition ->
            ResolvedAlgorithm(definition, resolveAlgorithmSettings(definition, config, mode))
        }
    }

    private fun enabledAlgorithmNames(
        config: ExperimentConfig,
        mode: AlgorithmMode,
    ): List<String> {
        val algorithms = AlgorithmRegistry.forMode(mode)
        return algorithms
            .filter { definition ->
                val override = config.algorithmConfigs[definition.name]
                override?.enabled ?: definition.defaultEnabled
            }.map { it.name }
    }

    private fun resolveAlgorithmSettings(
        definition: AlgorithmDefinition,
        config: ExperimentConfig,
        mode: String,
    ): ResolvedAlgorithmSettings {
        val override = config.algorithmConfigs[definition.name]
        val defaultPopulation = if (mode.startsWith("batch")) config.batch.population else config.optimizer.population
        val defaultMaxIter = if (mode.startsWith("batch")) config.batch.maxIter else config.optimizer.maxIter
        return ResolvedAlgorithmSettings(
            population = override?.population ?: defaultPopulation,
            maxIter = override?.maxIter ?: defaultMaxIter,
        )
    }

    private fun resolveTaskCounts(
        mode: String,
        command: CliParser.RunCommand,
        config: ExperimentConfig,
        profile: ProfileConfig?,
    ): List<Int> {
        if (!mode.endsWith("multi")) {
            return emptyList()
        }
        val baseCounts =
            when (mode) {
                "batch-multi" -> profile?.tasks ?: config.batch.cloudletCounts
                "realtime-multi" -> profile?.tasks ?: config.realtime.cloudletCounts
                else -> emptyList()
            }
        return command.taskCounts.ifEmpty { baseCounts }.ifEmpty { defaultMultiTaskCounts }
    }

    private fun attachResolvedAlgorithms(
        config: ExperimentConfig,
        mode: String,
        algorithms: List<ResolvedAlgorithm>,
        taskCounts: List<Int>,
    ): ExperimentConfig {
        val batchAlgorithms = algorithms.mapNotNull { it.definition.legacyBatchType }
        val realtimeAlgorithms = algorithms.mapNotNull { it.definition.legacyRealtimeType }
        return when {
            mode.startsWith("batch") ->
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

    private fun findPreset(
        presets: Map<String, PresetConfig>,
        presetName: String,
    ): PresetConfig {
        val preset =
            presets.entries
                .firstOrNull { (name, _) ->
                    name.equals(presetName, ignoreCase = true) ||
                        hasSameNormalizedName(name, presetName)
                }?.value

        return preset ?: throw IllegalArgumentException(
            "未知预设: $presetName。可用预设: ${presets.keys.sorted().joinToString(", ").ifBlank { "(无)" }}",
        )
    }

    private fun String.toExperimentMode(): ExperimentMode = ExperimentMode.valueOf(uppercase().replace("-", "_"))

    private fun String.isBatchMode(): Boolean = startsWith("batch")

    private fun String.isMultiMode(): Boolean = endsWith("multi")

    private fun ConfigurationManager.LoadedConfigs.toRunConfigs() = LoadedRunConfigs(systemConfig, experimentConfig)

    private fun logSkippedAlgorithmLibrary(exception: Exception) {
        Logger.warn("加载算法库配置失败，已跳过 configs/algorithms.toml: {}", exception.message)
    }

    private fun hasSameNormalizedName(
        left: String,
        right: String,
    ): Boolean = ExperimentConfig.normalizeAlgorithmName(left) == ExperimentConfig.normalizeAlgorithmName(right)

    private fun invalidRunModeMessage(mode: String): String {
        val modes = supportedModes().joinToString(", ")
        return "无效运行模式: $mode。可用模式: $modes"
    }

    private fun missingProfileSelectionMessage(
        configFile: String?,
        availableProfiles: String,
    ): String =
        "配置文件 ${configFile ?: "(未指定)"} 包含 profiles，" +
            "但未指定 --profile，也没有 defaultProfile。可用 profiles: $availableProfiles"
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
