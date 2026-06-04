package cli

private val supportedModes = setOf("batch", "realtime", "batch-multi", "realtime-multi")
private val legacyModes =
    mapOf(
        "batch" to "run --mode batch",
        "b" to "run --mode batch",
        "realtime" to "run --mode realtime",
        "r" to "run --mode realtime",
        "batch-multi" to "run --mode batch-multi",
        "bm" to "run --mode batch-multi",
        "batch_multi" to "run --mode batch-multi",
        "realtime-multi" to "run --mode realtime-multi",
        "rm" to "run --mode realtime-multi",
        "realtime_multi" to "run --mode realtime-multi",
    )

private fun cliError(message: String): Nothing = throw IllegalArgumentException(message)

private inline fun ensureCli(
    condition: Boolean,
    lazyMessage: () -> String,
) {
    if (!condition) {
        cliError(lazyMessage())
    }
}

class CliParser(
    private val args: Array<String>,
) {
    sealed interface Command

    data class RunCommand(
        val mode: String? = null,
        val algorithms: List<String> = emptyList(),
        val preset: String? = null,
        val profile: String? = null,
        val randomSeed: Long? = null,
        val taskCounts: List<Int> = emptyList(),
        val runs: Int? = null,
        val configFile: String? = null,
        val verbose: Boolean = false,
        val outputDir: String? = null,
        val useCoroutines: Boolean = true,
        val maxConcurrency: Int = 0,
        val dryRun: Boolean = false,
    ) : Command

    private data class RunCommandDraft(
        var mode: String? = null,
        var algorithms: List<String> = emptyList(),
        var preset: String? = null,
        var profile: String? = null,
        var randomSeed: Long? = null,
        var taskCounts: List<Int> = emptyList(),
        var runs: Int? = null,
        var configFile: String? = null,
        var verbose: Boolean = false,
        var outputDir: String? = null,
        var useCoroutines: Boolean = true,
        var maxConcurrency: Int = 0,
        var dryRun: Boolean = false,
    ) {
        fun toCommand(): RunCommand =
            RunCommand(
                mode = mode,
                algorithms = algorithms,
                preset = preset,
                profile = profile,
                randomSeed = randomSeed,
                taskCounts = taskCounts,
                runs = runs,
                configFile = configFile,
                verbose = verbose,
                outputDir = outputDir,
                useCoroutines = useCoroutines,
                maxConcurrency = maxConcurrency,
                dryRun = dryRun,
            )
    }

    private data class RunOption(
        val names: Set<String>,
        val hasValue: Boolean,
        val apply: (RunCommandDraft, String?, String) -> Unit,
    )

    data class ListAlgorithmsCommand(
        val mode: String,
    ) : Command

    data class ListProfilesCommand(
        val configFile: String,
    ) : Command

    data class ListPresetsCommand(
        val configFile: String,
    ) : Command

    data class ConfigValidateCommand(
        val configFile: String,
    ) : Command

    data class ConfigPrintCommand(
        val configFile: String,
        val profile: String? = null,
    ) : Command

    data object HelpCommand : Command

    fun parse(): Command {
        if (args.isEmpty() || args.any { it == "--help" || it == "-h" }) {
            return HelpCommand
        }

        val command = args[0].lowercase()
        legacyModes[command]?.let { replacement ->
            cliError(
                "旧入口 \"$command\" 已停用。请使用: $replacement ${args.drop(1).joinToString(" ")}".trim(),
            )
        }

        return when (command) {
            "run" -> parseRun(args.drop(1))
            "list" -> parseList(args.drop(1))
            "config" -> parseConfig(args.drop(1))
            else ->
                cliError(
                    "未知命令: $command。可用命令: run, list, config。运行 --help 查看示例。",
                )
        }
    }

    private fun parseRun(tokens: List<String>): RunCommand {
        val draft = RunCommandDraft()
        val options = runOptions().flatMap { option -> option.names.map { it to option } }.toMap()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            val inlineName = inlineOptionName(token)
            val option = options[inlineName ?: token]
            when {
                option != null -> {
                    val value = optionValue(option, tokens, i, token, inlineName)
                    option.apply(draft, value, inlineName ?: token)
                    i += if (option.hasValue && inlineName == null) 2 else 1
                }
                token.startsWith("-") -> cliError("未知参数: $token")
                else -> cliError("意外的位置参数: $token。请使用命名参数，例如 run --mode batch -a RANDOM")
            }
        }

        val command = draft.toCommand()
        val resolvedMode = command.mode?.let { normalizeMode(it) }
        if (resolvedMode != null && resolvedMode !in supportedModes) {
            cliError("无效运行模式: $resolvedMode。可用模式: ${supportedModes.joinToString(", ")}")
        }
        if (command.algorithms.isNotEmpty() && !command.preset.isNullOrBlank()) {
            cliError("--preset 与 --algorithms/-a 互斥，请只指定一种算法选择方式")
        }

        return command.copy(mode = resolvedMode)
    }

    private fun runOptions(): List<RunOption> =
        listOf(
            valueOption("--mode") { draft, value, _ -> draft.mode = normalizeMode(value) },
            valueOption("--algorithms", "-a") { draft, value, _ -> draft.algorithms = parseNameList(value) },
            valueOption("--preset") { draft, value, _ -> draft.preset = value },
            valueOption("--profile", "-p") { draft, value, _ -> draft.profile = value },
            valueOption("--seed", "-s") { draft, value, option -> draft.randomSeed = parseLong(value, option) },
            valueOption("--runs", "-r") { draft, value, option -> draft.runs = parsePositiveInt(value, option) },
            valueOption("--tasks", "-t") { draft, value, _ -> draft.taskCounts = parseTaskCounts(value) },
            valueOption("--config", "-c") { draft, value, _ -> draft.configFile = value },
            valueOption("--output", "-o") { draft, value, _ -> draft.outputDir = value },
            flagOption("--sequential", "-S") { draft -> draft.useCoroutines = false },
            valueOption("--concurrency", "-C") { draft, value, option ->
                draft.maxConcurrency = parsePositiveInt(value, option)
            },
            flagOption("--dry-run") { draft -> draft.dryRun = true },
            flagOption("--verbose", "-v") { draft -> draft.verbose = true },
        )

    private fun valueOption(
        vararg names: String,
        apply: (RunCommandDraft, String, String) -> Unit,
    ): RunOption =
        RunOption(names.toSet(), hasValue = true) { draft, value, option ->
            apply(draft, value ?: cliError("$option 参数需要指定值"), option)
        }

    private fun flagOption(
        vararg names: String,
        apply: (RunCommandDraft) -> Unit,
    ): RunOption = RunOption(names.toSet(), hasValue = false) { draft, _, _ -> apply(draft) }

    private fun inlineOptionName(token: String): String? =
        token
            .takeIf { it.startsWith("--") && it.contains("=") }
            ?.substringBefore("=")

    private fun optionValue(
        option: RunOption,
        tokens: List<String>,
        index: Int,
        token: String,
        inlineName: String?,
    ): String? {
        if (!option.hasValue) {
            ensureCli(inlineName == null) { "未知参数: $token" }
            return null
        }
        return inlineName?.let { token.substringAfter("=") } ?: readValue(tokens, index, token)
    }

    private fun parseList(tokens: List<String>): Command {
        if (tokens.isEmpty()) {
            cliError("list 命令需要子命令: algorithms, profiles 或 presets")
        }
        return when (tokens[0].lowercase()) {
            "algorithms" -> ListAlgorithmsCommand(parseRequiredMode(tokens.drop(1), "list algorithms"))
            "profiles" -> ListProfilesCommand(parseRequiredConfig(tokens.drop(1), "list profiles"))
            "presets" -> ListPresetsCommand(parseRequiredConfig(tokens.drop(1), "list presets"))
            else -> cliError("未知 list 子命令: ${tokens[0]}。可用: algorithms, profiles, presets")
        }
    }

    private fun parseConfig(tokens: List<String>): Command {
        if (tokens.isEmpty()) {
            cliError("config 命令需要子命令: validate 或 print")
        }
        return when (tokens[0].lowercase()) {
            "validate" -> ConfigValidateCommand(parseRequiredConfig(tokens.drop(1), "config validate"))
            "print" -> {
                val (configFile, profile) = parseConfigAndOptionalProfile(tokens.drop(1), "config print")
                ConfigPrintCommand(configFile, profile)
            }
            else -> cliError("未知 config 子命令: ${tokens[0]}。可用: validate, print")
        }
    }

    private fun parseRequiredMode(
        tokens: List<String>,
        commandName: String,
    ): String {
        var mode: String? = null
        var i = 0
        while (i < tokens.size) {
            when {
                tokens[i] == "--mode" || tokens[i] == "-m" -> {
                    mode = normalizeMode(readValue(tokens, i, tokens[i]))
                    i += 2
                }
                tokens[i].startsWith("--mode=") -> {
                    mode = normalizeMode(tokens[i].substringAfter("="))
                    i++
                }
                tokens[i].startsWith("-") -> cliError("未知参数: ${tokens[i]}")
                else -> cliError("意外的位置参数: ${tokens[i]}")
            }
        }
        val resolvedMode = mode ?: cliError("$commandName 需要 --mode batch|realtime")
        if (resolvedMode !in setOf("batch", "realtime")) {
            cliError("$commandName 只接受 --mode batch 或 realtime")
        }
        return resolvedMode
    }

    private fun parseRequiredConfig(
        tokens: List<String>,
        commandName: String,
    ): String = parseConfigAndOptionalProfile(tokens, commandName).first

    private fun parseConfigAndOptionalProfile(
        tokens: List<String>,
        commandName: String,
    ): Pair<String, String?> {
        var configFile: String? = null
        var profile: String? = null
        var i = 0
        while (i < tokens.size) {
            when {
                tokens[i] == "--config" || tokens[i] == "-c" -> {
                    configFile = readValue(tokens, i, tokens[i])
                    i += 2
                }
                tokens[i].startsWith("--config=") -> {
                    configFile = tokens[i].substringAfter("=")
                    i++
                }
                tokens[i] == "--profile" || tokens[i] == "-p" -> {
                    profile = readValue(tokens, i, tokens[i])
                    i += 2
                }
                tokens[i].startsWith("--profile=") -> {
                    profile = tokens[i].substringAfter("=")
                    i++
                }
                tokens[i].startsWith("-") -> cliError("未知参数: ${tokens[i]}")
                else -> cliError("意外的位置参数: ${tokens[i]}")
            }
        }
        return (configFile ?: cliError("$commandName 需要 --config FILE")) to profile
    }

    private fun readValue(
        tokens: List<String>,
        index: Int,
        option: String,
    ): String {
        if (index + 1 >= tokens.size) {
            cliError("$option 参数需要指定值")
        }
        return tokens[index + 1]
    }

    private fun parseNameList(value: String): List<String> =
        value
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun parseTaskCounts(value: String): List<Int> =
        parseNameList(value).map { part ->
            part.toIntOrNull()?.takeIf { it > 0 }
                ?: cliError("无效的任务数: $part。任务数必须是大于 0 的整数")
        }

    private fun parsePositiveInt(
        value: String,
        option: String,
    ): Int =
        value.toIntOrNull()?.takeIf { it > 0 }
            ?: cliError("$option 必须是大于 0 的整数: $value")

    private fun parseLong(
        value: String,
        option: String,
    ): Long =
        value.toLongOrNull()
            ?: cliError("$option 必须是整数: $value")
}

fun normalizeMode(mode: String): String =
    when (mode.lowercase().replace("_", "-")) {
        "b" -> "batch"
        "r" -> "realtime"
        "bm" -> "batch-multi"
        "rm" -> "realtime-multi"
        else -> mode.lowercase().replace("_", "-")
    }

fun supportedModes(): Set<String> = supportedModes
