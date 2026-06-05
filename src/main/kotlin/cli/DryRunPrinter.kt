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

object DryRunPrinter {
    fun printAlgorithms(mode: String) {
        val algorithms =
            when (normalizeMode(mode)) {
                "batch" -> AlgorithmRegistry.forMode(AlgorithmMode.BATCH).map { it.name }
                "realtime" -> AlgorithmRegistry.forMode(AlgorithmMode.REALTIME).map { it.name }
                else -> throw IllegalArgumentException("list algorithms 只接受 batch 或 realtime")
            }
        Logger.result("可用算法 ({}): {}", mode, algorithms.joinToString(", "))
    }

    fun printProfiles(config: ExperimentConfig) {
        if (config.profiles.isEmpty()) {
            Logger.result("未定义 profiles")
            return
        }
        Logger.result("可用 profiles:")
        config.profiles.toSortedMap().forEach { (name, profile) ->
            val selection =
                when {
                    profile.algorithms.isNotEmpty() -> "algorithms=${profile.algorithms.joinToString(", ")}"
                    !profile.preset.isNullOrBlank() -> "preset=${profile.preset}"
                    else -> "(未指定)"
                }
            Logger.result("  {} -> mode={}, {}", name, profile.mode, selection)
        }
        config.defaultProfile?.let { Logger.result("默认 profile: {}", it) }
    }

    fun printPresets(presets: Map<String, PresetConfig>) {
        if (presets.isEmpty()) {
            Logger.result("未定义预设")
            return
        }
        Logger.result("可用预设:")
        presets.toSortedMap().forEach { (name, preset) ->
            Logger.result("  {} = {}", name, preset.algorithms.joinToString(", "))
        }
    }

    fun printDryRun(resolved: ResolvedExperimentConfig) {
        printDryRunHeader(resolved)
        printDryRunAlgorithms(resolved)
        if (resolved.mode.startsWith("realtime")) {
            printRealtimeOverview(resolved)
            printPhysicalTopology(resolved)
        }
        Logger.result("CSV 输出: enabled={}, delimiter='{}'", resolved.output.csvEnabled, resolved.output.csvDelimiter)
    }

    fun resolvedJson(
        resolved: ResolvedExperimentConfig,
        experimentDir: File?,
        timestamp: String,
    ): String = renderResolvedJson(resolved, experimentDir, timestamp)

    fun printUsage() {
        Logger.info("CloudSim-Benchmark CLI")
        Logger.info("")
        Logger.info("用法:")
        Logger.info("  cloudsim run --mode batch|realtime|batch-multi|realtime-multi [options]")
        Logger.info("  cloudsim list algorithms --mode batch|realtime")
        Logger.info("  cloudsim list profiles --config FILE")
        Logger.info("  cloudsim list presets --config FILE")
        Logger.info("  cloudsim config validate --config FILE")
        Logger.info("  cloudsim config print --config FILE [--profile NAME]")
        Logger.info("")
        Logger.info("run 选项:")
        Logger.info("  --mode MODE                      运行模式（可省略，交给 profile 决定）")
        Logger.info("  --algorithms, -a ALGO1,ALGO2     算法列表，或 ALL")
        Logger.info("  --preset NAME                    使用配置文件中的预设；与 --algorithms 互斥")
        Logger.info("  --profile, -p NAME               选择配置文件中的 profile")
        Logger.info("  --seed, -s SEED                  随机数种子")
        Logger.info("  --runs, -r COUNT                 运行次数")
        Logger.info("  --tasks, -t COUNT1,COUNT2        multi 模式任务数列表")
        Logger.info("  --config, -c FILE                配置文件")
        Logger.info("  --output, -o DIR                 输出目录")
        Logger.info("  --sequential, -S                 顺序执行")
        Logger.info("  --concurrency, -C NUM            最大协程并发数")
        Logger.info("  --dry-run                        只打印解析后的配置，不创建结果")
        Logger.info("")
        Logger.info("示例:")
        Logger.info(BATCH_SMALL_EXAMPLE)
        Logger.info("  cloudsim run --mode batch -a RANDOM,PSO -r 3 -s 42 -o runs/demo")
        Logger.info("  cloudsim run --mode batch-multi --tasks 50,100,200 -a ALL --concurrency 4")
        Logger.info("  cloudsim list profiles --config configs/examples/single_config_example.toml")
    }
}
