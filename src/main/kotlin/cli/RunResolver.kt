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

private val defaultMultiTaskCounts = listOf(50, 100, 200, 500)

object RunResolver {
    fun resolve(command: CliParser.RunCommand): ResolvedExperimentConfig {
        val rawConfigs = loadBaseConfigs(command.configFile)
        val loadedConfigs = mergeAlgorithmLibrary(rawConfigs)
        val profileSelection = selectProfile(loadedConfigs.experimentConfig, command.profile, command.configFile)
        val selectedProfile = profileSelection?.profile
        val resolvedMode = resolveRunMode(command, selectedProfile, loadedConfigs.experimentConfig)
        val configsWithProfile = applyProfileAndOverrides(
            configs = loadedConfigs,
            command = command.copy(mode = resolvedMode),
            profile = selectedProfile,
            mode = resolvedMode
        )
        val resolvedAlgorithms = resolveAlgorithmsForRun(
            mode = resolvedMode,
            command = command,
            config = configsWithProfile.experimentConfig,
            profile = selectedProfile
        )
        val taskCounts = resolveTaskCounts(resolvedMode, command, configsWithProfile.experimentConfig, selectedProfile)
        val experimentConfig = attachResolvedAlgorithms(
            configsWithProfile.experimentConfig,
            resolvedMode,
            resolvedAlgorithms,
            taskCounts
        )
        val finalConfigs = configsWithProfile.copy(experimentConfig = experimentConfig)
        val systemConfig = finalConfigs.systemConfig

        return ResolvedExperimentConfig(
            command = command.copy(mode = resolvedMode),
            systemConfig = systemConfig,
            experimentConfig = finalConfigs.experimentConfig,
            mode = resolvedMode,
            profile = ResolvedProfile(
                name = profileSelection?.name,
                presetName = command.preset ?: selectedProfile?.preset
            ),
            algorithms = resolvedAlgorithms,
            taskCounts = taskCounts,
            execution = ResolvedExecutionOptions(
                useCoroutines = command.useCoroutines,
                maxConcurrency = command.maxConcurrency,
                dryRun = command.dryRun
            ),
            output = ResolvedOutputConfig(
                resultsDir = systemConfig.output.resultsDir,
                csvEnabled = systemConfig.output.csv.enabled,
                csvDelimiter = systemConfig.output.csv.delimiter,
                nameFormat = systemConfig.experiment.nameFormat
            )
        )
    }

    fun loadBaseConfigs(configFile: String?): LoadedRunConfigs {
        return if (!configFile.isNullOrBlank()) {
            ConfigurationManager.loadFromSingleFile(configFile).toRunConfigs()
        } else {
            LoadedRunConfigs(
                systemConfig = SystemConfig.createDefault(),
                experimentConfig = ExperimentConfig.createDefault()
            )
        }
    }

    fun mergeAlgorithmLibrary(configs: LoadedRunConfigs): LoadedRunConfigs {
        val libraryFile = File("configs/algorithms.toml")
        if (!libraryFile.exists() || libraryFile.length() == 0L) {
            return configs
        }

        return try {
            val libraryConfig = ExperimentConfig.loadLibrary(libraryFile.absolutePath)
            configs.copy(
                experimentConfig = configs.experimentConfig.copy(
                    algorithmConfigs = libraryConfig.algorithmConfigs + configs.experimentConfig.algorithmConfigs,
                    presets = libraryConfig.presets + configs.experimentConfig.presets
                )
            )
        } catch (e: Exception) {
            Logger.warn("加载算法库配置失败，已跳过 configs/algorithms.toml: {}", e.message)
            configs
        }
    }

    fun renderExperimentName(resolved: ResolvedExperimentConfig, timestamp: String): String {
        val taskToken = when (resolved.mode) {
            "batch-multi", "realtime-multi" -> resolved.taskCounts.joinToString("-")
            "batch" -> resolved.batch.cloudletCount.toString()
            "realtime" -> resolved.realtime.cloudletCount.toString()
            else -> ""
        }
        val algorithmToken = resolved.selectedAlgorithmNames.joinToString("+").ifBlank { "ALL" }
        val rendered = resolved.output.nameFormat
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
        val profile: ProfileConfig
    )

    private fun selectProfile(
        config: ExperimentConfig,
        requestedProfile: String?,
        configFile: String?
    ): SelectedProfile? {
        if (config.profiles.isEmpty()) {
            if (requestedProfile != null) {
                throw IllegalArgumentException("配置文件未定义 profiles，不能使用 --profile $requestedProfile")
            }
            return null
        }

        val profileName = when {
            !requestedProfile.isNullOrBlank() -> requestedProfile
            !config.defaultProfile.isNullOrBlank() -> config.defaultProfile
            else -> throw IllegalArgumentException(
                "配置文件 ${configFile ?: "(未指定)"} 包含 profiles，但未指定 --profile，也没有 defaultProfile。可用 profiles: ${config.profiles.keys.sorted().joinToString(", ")}"
            )
        }

        val profileEntry = config.profiles.entries.firstOrNull { (name, _) ->
            name.equals(profileName, ignoreCase = true) ||
                ExperimentConfig.normalizeAlgorithmName(name) == ExperimentConfig.normalizeAlgorithmName(profileName)
        } ?: throw IllegalArgumentException(
            "未知 profile: $profileName。可用 profiles: ${config.profiles.keys.sorted().joinToString(", ")}"
        )

        return SelectedProfile(profileEntry.key, profileEntry.value)
    }

    private fun resolveRunMode(
        command: CliParser.RunCommand,
        profile: ProfileConfig?,
        config: ExperimentConfig
    ): String {
        val commandMode = command.mode?.let { normalizeMode(it) }
        if (commandMode != null) {
            if (commandMode !in supportedModes()) {
                throw IllegalArgumentException("无效运行模式: $commandMode。可用模式: ${supportedModes().joinToString(", ")}")
            }
            return commandMode
        }

        val profileMode = profile?.mode?.takeIf { it.isNotBlank() }?.let { normalizeMode(it) }
        if (profileMode != null) {
            if (profileMode !in supportedModes()) {
                throw IllegalArgumentException("无效运行模式: $profileMode。可用模式: ${supportedModes().joinToString(", ")}")
            }
            return profileMode
        }

        if (config.profiles.isNotEmpty()) {
            throw IllegalArgumentException("run 命令缺少 --mode，且所选 profile 未定义 mode")
        }

        throw IllegalArgumentException("run 命令需要 --mode batch|realtime|batch-multi|realtime-multi，或使用包含 profiles 的配置文件")
    }

    private fun applyProfileAndOverrides(
        configs: LoadedRunConfigs,
        command: CliParser.RunCommand,
        profile: ProfileConfig?,
        mode: String
    ): LoadedRunConfigs {
        val resolvedRuns = command.runs ?: profile?.runs ?:
            if (mode.startsWith("batch")) configs.experimentConfig.batch.runs else configs.experimentConfig.realtime.runs
        val resolvedTaskCounts = if (mode.endsWith("multi")) {
            command.taskCounts.ifEmpty {
                profile?.tasks ?: if (mode.startsWith("batch")) configs.experimentConfig.batch.cloudletCounts else configs.experimentConfig.realtime.cloudletCounts
            }
        } else {
            emptyList()
        }
        val systemConfig = command.outputDir?.let { outputDir ->
            configs.systemConfig.copy(output = configs.systemConfig.output.copy(resultsDir = outputDir))
        } ?: profile?.outputDir?.let { outputDir ->
            configs.systemConfig.copy(output = configs.systemConfig.output.copy(resultsDir = outputDir))
        } ?: configs.systemConfig

        val experimentBase = configs.experimentConfig.copy(
            mode = mode.toExperimentMode(),
            randomSeed = command.randomSeed ?: profile?.seed ?: configs.experimentConfig.randomSeed
        )

        val experimentConfig = when (mode) {
            "batch", "batch-multi" -> experimentBase.copy(
                batch = (profile?.batch?.let { ExperimentConfig.mergeBatchConfig(BatchConfig(), it) } ?: experimentBase.batch).copy(
                    runs = resolvedRuns,
                    cloudletCounts = resolvedTaskCounts.ifEmpty { experimentBase.batch.cloudletCounts }
                ),
                optimizer = experimentBase.optimizer
            )
            "realtime", "realtime-multi" -> experimentBase.copy(
                realtime = (profile?.realtime?.let { ExperimentConfig.mergeRealtimeConfig(RealtimeConfig(), it) } ?: experimentBase.realtime).copy(
                    runs = resolvedRuns,
                    cloudletCounts = resolvedTaskCounts.ifEmpty { experimentBase.realtime.cloudletCounts }
                ),
                optimizer = experimentBase.optimizer
            )
            else -> experimentBase
        }

        ExperimentConfig.validate(experimentConfig)
        SystemConfig.validate(systemConfig)
        return LoadedRunConfigs(systemConfig, experimentConfig)
    }

    private fun resolveAlgorithmsForRun(
        mode: String,
        command: CliParser.RunCommand,
        config: ExperimentConfig,
        profile: ProfileConfig?
    ): List<ResolvedAlgorithm> {
        val algorithmMode = if (mode.startsWith("batch")) AlgorithmMode.BATCH else AlgorithmMode.REALTIME
        val names = when {
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

    private fun enabledAlgorithmNames(config: ExperimentConfig, mode: AlgorithmMode): List<String> {
        val algorithms = AlgorithmRegistry.forMode(mode)
        return algorithms
            .filter { definition ->
                val override = config.algorithmConfigs[definition.name]
                override?.enabled ?: definition.defaultEnabled
            }
            .map { it.name }
    }

    private fun resolveAlgorithmSettings(
        definition: AlgorithmDefinition,
        config: ExperimentConfig,
        mode: String
    ): ResolvedAlgorithmSettings {
        val override = config.algorithmConfigs[definition.name]
        val defaultPopulation = if (mode.startsWith("batch")) config.batch.population else config.optimizer.population
        val defaultMaxIter = if (mode.startsWith("batch")) config.batch.maxIter else config.optimizer.maxIter
        return ResolvedAlgorithmSettings(
            population = override?.population ?: defaultPopulation,
            maxIter = override?.maxIter ?: defaultMaxIter
        )
    }

    private fun resolveTaskCounts(
        mode: String,
        command: CliParser.RunCommand,
        config: ExperimentConfig,
        profile: ProfileConfig?
    ): List<Int> {
        if (!mode.endsWith("multi")) {
            return emptyList()
        }
        val baseCounts = when (mode) {
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
        taskCounts: List<Int>
    ): ExperimentConfig {
        val batchAlgorithms = algorithms.mapNotNull { it.definition.legacyBatchType }
        val realtimeAlgorithms = algorithms.mapNotNull { it.definition.legacyRealtimeType }
        return when {
            mode.startsWith("batch") -> config.copy(
                batch = config.batch.copy(
                    algorithms = batchAlgorithms,
                    cloudletCounts = taskCounts.ifEmpty { config.batch.cloudletCounts }
                )
            )
            mode.startsWith("realtime") -> config.copy(
                realtime = config.realtime.copy(
                    algorithms = realtimeAlgorithms,
                    cloudletCounts = taskCounts.ifEmpty { config.realtime.cloudletCounts }
                )
            )
            else -> config
        }
    }

    private fun findPreset(presets: Map<String, PresetConfig>, presetName: String): PresetConfig {
        val preset = presets.entries.firstOrNull { (name, _) ->
            name.equals(presetName, ignoreCase = true) ||
                ExperimentConfig.normalizeAlgorithmName(name) == ExperimentConfig.normalizeAlgorithmName(presetName)
        }?.value

        return preset ?: throw IllegalArgumentException(
            "未知预设: $presetName。可用预设: ${presets.keys.sorted().joinToString(", ").ifBlank { "(无)" }}"
        )
    }

    private fun String.toExperimentMode(): ExperimentMode =
        ExperimentMode.valueOf(uppercase().replace("-", "_"))

    private fun ConfigurationManager.LoadedConfigs.toRunConfigs(): LoadedRunConfigs =
        LoadedRunConfigs(systemConfig, experimentConfig)
}

fun resolveRun(command: CliParser.RunCommand): ResolvedExperimentConfig =
    RunResolver.resolve(command)

internal fun applyRunOverrides(
    configs: ConfigurationManager.LoadedConfigs,
    command: CliParser.RunCommand,
    selectionAlgorithmConfigs: Map<String, AlgorithmConfig> = configs.experimentConfig.algorithmConfigs
): ConfigurationManager.LoadedConfigs {
    val mode = command.mode?.let { normalizeMode(it) } ?: configs.experimentConfig.mode.name.lowercase().replace("_", "-")
    val systemConfig = command.outputDir?.let { outputDir ->
        configs.systemConfig.copy(output = configs.systemConfig.output.copy(resultsDir = outputDir))
    } ?: configs.systemConfig

    val experimentConfig = configs.experimentConfig.copy(
        mode = ExperimentMode.valueOf(mode.uppercase().replace("-", "_")),
        randomSeed = command.randomSeed ?: configs.experimentConfig.randomSeed,
        algorithmConfigs = selectionAlgorithmConfigs
    )

    ExperimentConfig.validate(experimentConfig)
    SystemConfig.validate(systemConfig)
    return ConfigurationManager.LoadedConfigs(systemConfig, experimentConfig)
}

internal fun parseBatchAlgorithms(algorithmNames: List<String>): List<BatchAlgorithmType> =
    RunResolver.parseBatchAlgorithms(algorithmNames)

internal fun parseRealtimeAlgorithms(algorithmNames: List<String>): List<RealtimeAlgorithmType> =
    RunResolver.parseRealtimeAlgorithms(algorithmNames)
