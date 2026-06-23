package cli

import config.ExperimentConfig
import config.PresetConfig
import scheduler.AlgorithmMode
import scheduler.AlgorithmRegistry
import util.Logger
import java.io.File

private const val BATCH_SMALL_EXAMPLE =
    "  cloudsim run --config configs/examples/single_config_example.toml " +
        "--profile batch_small --dry-run"

internal interface DryRunOutput {
    fun info(
        message: String,
        vararg args: Any?,
    )

    fun result(
        message: String,
        vararg args: Any?,
    )
}

private object LoggerDryRunOutput : DryRunOutput {
    override fun info(
        message: String,
        vararg args: Any?,
    ) = Logger.info(message, *args)

    override fun result(
        message: String,
        vararg args: Any?,
    ) = Logger.result(message, *args)
}

internal fun printUsage(output: DryRunOutput) {
    usageLines.forEach(output::info)
}

private val usageLines =
    listOf(
        "CloudSim-Benchmark CLI",
        "",
        "用法:",
        "  cloudsim run --mode batch|realtime|batch-multi|realtime-multi [options]",
        "  cloudsim list algorithms --mode batch|realtime",
        "  cloudsim list profiles --config FILE",
        "  cloudsim list presets --config FILE",
        "  cloudsim config validate --config FILE",
        "  cloudsim config print --config FILE [--profile NAME]",
        "",
        "run 选项:",
        "  --mode MODE                      运行模式（可省略，交给 profile 决定）",
        "  --algorithms, -a ALGO1,ALGO2     算法列表，或 ALL",
        "  --preset NAME                    使用配置文件中的预设；与 --algorithms 互斥",
        "  --profile, -p NAME               选择配置文件中的 profile",
        "  --seed, -s SEED                  随机数种子",
        "  --runs, -r COUNT                 运行次数",
        "  --tasks, -t COUNT1,COUNT2        multi 模式任务数列表",
        "  --config, -c FILE                配置文件",
        "  --output, -o DIR                 输出目录",
        "  --sequential, -S                 顺序执行",
        "  --concurrency, -C NUM            最大协程并发数",
        "  --dry-run                        只打印解析后的配置，不创建结果",
        "",
        "示例:",
        BATCH_SMALL_EXAMPLE,
        "  cloudsim run --mode batch -a RANDOM,PSO -r 3 -s 42 -o runs/demo",
        "  cloudsim run --mode batch-multi --tasks 50,100,200 -a ALL --concurrency 4",
        "  cloudsim list profiles --config configs/examples/single_config_example.toml",
    )

object DryRunPrinter {
    fun printAlgorithms(mode: String) = printAlgorithms(mode, LoggerDryRunOutput)

    internal fun printAlgorithms(
        mode: String,
        output: DryRunOutput,
    ) {
        val algorithms =
            when (normalizeMode(mode)) {
                "batch" -> AlgorithmRegistry.forMode(AlgorithmMode.BATCH).map { it.name }
                "realtime" -> AlgorithmRegistry.forMode(AlgorithmMode.REALTIME).map { it.name }
                else -> throw IllegalArgumentException("list algorithms 只接受 batch 或 realtime")
            }
        output.result("可用算法 ({}): {}", mode, algorithms.joinToString(", "))
    }

    fun printProfiles(config: ExperimentConfig) = printProfiles(config, LoggerDryRunOutput)

    internal fun printProfiles(
        config: ExperimentConfig,
        output: DryRunOutput,
    ) {
        if (config.profiles.isEmpty()) {
            output.result("未定义 profiles")
            return
        }
        output.result("可用 profiles:")
        config.profiles.toSortedMap().forEach { (name, profile) ->
            val selection =
                when {
                    profile.algorithms.isNotEmpty() -> "algorithms=${profile.algorithms.joinToString(", ")}"
                    !profile.preset.isNullOrBlank() -> "preset=${profile.preset}"
                    else -> "(未指定)"
                }
            output.result("  {} -> mode={}, {}", name, profile.mode, selection)
        }
        config.defaultProfile?.let { output.result("默认 profile: {}", it) }
    }

    fun printPresets(presets: Map<String, PresetConfig>) = printPresets(presets, LoggerDryRunOutput)

    internal fun printPresets(
        presets: Map<String, PresetConfig>,
        output: DryRunOutput,
    ) {
        if (presets.isEmpty()) {
            output.result("未定义预设")
            return
        }
        output.result("可用预设:")
        presets.toSortedMap().forEach { (name, preset) ->
            output.result("  {} = {}", name, preset.algorithms.joinToString(", "))
        }
    }

    fun printDryRun(resolved: ResolvedExperimentConfig) = printDryRun(resolved, LoggerDryRunOutput)

    internal fun printDryRun(
        resolved: ResolvedExperimentConfig,
        output: DryRunOutput,
    ) {
        printDryRunHeader(resolved, output)
        printDryRunAlgorithms(resolved, output)
        if (resolved.mode.startsWith("realtime")) {
            printRealtimeOverview(resolved, output)
            printPhysicalTopology(resolved, output)
        }
        output.result("CSV 输出: enabled={}, delimiter='{}'", resolved.output.csvEnabled, resolved.output.csvDelimiter)
        if (resolved.mode.startsWith("realtime")) {
            output.result(
                "事件观测 CSV: enabled={}, file={}",
                resolved.output.csvEnabled && resolved.realtime.scheduling.eventObservationEnabled,
                "realtime_events.csv",
            )
        }
    }

    fun resolvedJson(
        resolved: ResolvedExperimentConfig,
        experimentDir: File?,
        timestamp: String,
    ): String = renderResolvedJson(resolved, experimentDir, timestamp)

    fun printUsage() = printUsage(LoggerDryRunOutput)
}
