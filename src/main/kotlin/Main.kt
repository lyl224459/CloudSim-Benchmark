import config.ExperimentConfig
import datacenter.ComparisonRunner
import datacenter.RealtimeComparisonRunner
import datacenter.BatchCloudletCountRunner
import datacenter.RealtimeCloudletCountRunner
import util.Logger

/**
 * 主程序入口
 * 支持批处理和实时调度两种模式
 */
fun main(args: Array<String>) {
    // 加载配置（可以从配置文件读取，这里使用默认配置）
    val config = loadConfig(args)
    
    // 解析命令行参数
    val mode = if (args.isNotEmpty()) args[0] else "realtime"
    
    when (mode.lowercase()) {
        "batch", "b" -> {
            Logger.info("开始批处理调度算法对比实验...")
            Logger.info("配置: 任务数={}, 种群={}, 迭代={}, 运行次数={}", 
                config.batch.cloudletCount, config.batch.population, config.batch.maxIter, config.batch.runs)
            Logger.info("随机数种子: {}", config.randomSeed)
            val runner = ComparisonRunner(
                cloudletCount = config.batch.cloudletCount,
                population = config.batch.population,
                maxIter = config.batch.maxIter,
                randomSeed = config.randomSeed,
                algorithms = config.batch.algorithms,
                runs = config.batch.runs,
                generatorType = config.batch.generatorType
            )
            runner.runComparison()
            Logger.info("批处理实验完成！")
        }
        "batch-multi", "bm" -> {
            // 批处理模式批量任务数实验
            Logger.info("开始批处理模式批量任务数实验...")
            val cloudletCounts = parseCloudletCounts(args)
            if (cloudletCounts.isEmpty()) {
                Logger.error("未指定任务数列表！使用默认值: 50, 100, 200, 500")
                val defaultCounts = listOf(50, 100, 200, 500)
                runBatchCloudletCountExperiment(defaultCounts, config)
            } else {
                runBatchCloudletCountExperiment(cloudletCounts, config)
            }
        }
        "realtime-multi", "rm" -> {
            // 实时调度模式批量任务数实验
            Logger.info("开始实时调度模式批量任务数实验...")
            val cloudletCounts = parseCloudletCounts(args)
            if (cloudletCounts.isEmpty()) {
                Logger.error("未指定任务数列表！使用默认值: 50, 100, 200, 500")
                val defaultCounts = listOf(50, 100, 200, 500)
                runRealtimeCloudletCountExperiment(defaultCounts, config)
            } else {
                runRealtimeCloudletCountExperiment(cloudletCounts, config)
            }
        }
        "realtime", "r", "" -> {
            Logger.info("开始实时调度算法对比实验...")
            Logger.info("配置: 任务数={}, 持续时间={}s, 到达率={}/s, 运行次数={}", 
                config.realtime.cloudletCount, config.realtime.simulationDuration, config.realtime.arrivalRate, config.realtime.runs)
            Logger.info("优化算法: 种群={}, 迭代={}", 
                config.optimizer.population, config.optimizer.maxIter)
            Logger.info("随机数种子: {}", config.randomSeed)
            if (config.realtime.algorithms.isNotEmpty()) {
                Logger.info("选定算法: {}", config.realtime.algorithms.joinToString(", ") { it.name })
            } else {
                Logger.info("运行所有算法")
            }
            val runner = RealtimeComparisonRunner(
                cloudletCount = config.realtime.cloudletCount,
                simulationDuration = config.realtime.simulationDuration,
                arrivalRate = config.realtime.arrivalRate,
                population = config.optimizer.population,
                maxIter = config.optimizer.maxIter,
                randomSeed = config.randomSeed,
                algorithms = config.realtime.algorithms,
                runs = config.realtime.runs,
                generatorType = config.realtime.generatorType
            )
            runner.runComparison()
            Logger.info("实时调度实验完成！")
        }
        else -> {
            printUsage()
        }
    }
}

/**
 * 加载配置
 */
private fun loadConfig(args: Array<String>): ExperimentConfig {
    val defaultConfig = ExperimentConfig()
    
    // 解析命令行参数
    val mode = if (args.isNotEmpty()) args[0] else "realtime"
    
    // 从命令行参数解析算法列表和随机种子
    val (algorithms, randomSeed) = parseCommandLineArgs(args, mode)
    
    // 根据模式设置算法配置
    return when (mode.lowercase()) {
        "batch", "b", "batch-multi", "bm" -> {
            val batchAlgorithms = algorithms.mapNotNull { 
                try { config.BatchAlgorithmType.valueOf(it.uppercase()) } 
                catch (e: IllegalArgumentException) { 
                    Logger.warn("未知的批处理算法: {}，已忽略", it)
                    null
                }
            }
            defaultConfig.copy(
                randomSeed = randomSeed,
                batch = defaultConfig.batch.copy(
                    algorithms = if (batchAlgorithms.isEmpty()) emptyList() else batchAlgorithms
                )
            )
        }
        "realtime", "r", "realtime-multi", "rm", "" -> {
            val realtimeAlgorithms = algorithms.mapNotNull { 
                try { config.RealtimeAlgorithmType.valueOf(it.uppercase()) } 
                catch (e: IllegalArgumentException) { 
                    Logger.warn("未知的实时算法: {}，已忽略", it)
                    null
                }
            }
            defaultConfig.copy(
                randomSeed = randomSeed,
                realtime = defaultConfig.realtime.copy(
                    algorithms = if (realtimeAlgorithms.isEmpty()) emptyList() else realtimeAlgorithms
                )
            )
        }
        else -> defaultConfig.copy(randomSeed = randomSeed)
    }
}

/**
 * 解析命令行参数，返回算法列表和随机种子
 */
private fun parseCommandLineArgs(args: Array<String>, mode: String): Pair<List<String>, Long> {
    if (args.size < 2) return Pair(emptyList(), 0L)
    
    val algorithms = mutableListOf<String>()
    var randomSeed = 0L
    
    // 从第二个参数开始解析
    for (i in 1 until args.size) {
        val arg = args[i]
        
        // 尝试解析为数字（随机种子）
        val seed = arg.toLongOrNull()
        if (seed != null && seed > 1000) {  // 随机种子通常较大，避免误判任务数
            randomSeed = seed
            break  // 找到随机种子后停止解析
        }
        
        // 如果是逗号分隔的列表
        if (arg.contains(",")) {
            // 检查是否是任务数列表（全为数字）
            val parts = arg.split(",").map { it.trim() }
            if (parts.all { it.toIntOrNull() != null && it.toIntOrNull()!! < 10000 }) {
                // 可能是任务数列表，跳过（在 parseCloudletCounts 中处理）
                continue
            } else {
                // 算法列表
                algorithms.addAll(parts)
            }
        } else {
            // 单个算法名称（如果不是数字）
            if (arg.toIntOrNull() == null || arg.toIntOrNull()!! >= 10000) {
                algorithms.add(arg)
            }
        }
    }
    
    return Pair(algorithms, randomSeed)
}

/**
 * 运行批处理模式批量任务数实验
 */
private fun runBatchCloudletCountExperiment(cloudletCounts: List<Int>, config: ExperimentConfig) {
    val runner = BatchCloudletCountRunner(
        cloudletCounts = cloudletCounts,
        population = config.batch.population,
        maxIter = config.batch.maxIter,
        randomSeed = config.randomSeed,
        algorithms = config.batch.algorithms,
        runs = config.batch.runs,
        generatorType = config.batch.generatorType
    )
    runner.runExperiment()
}

/**
 * 运行实时调度模式批量任务数实验
 */
private fun runRealtimeCloudletCountExperiment(cloudletCounts: List<Int>, config: ExperimentConfig) {
    val runner = RealtimeCloudletCountRunner(
        cloudletCounts = cloudletCounts,
        simulationDuration = config.realtime.simulationDuration,
        arrivalRate = config.realtime.arrivalRate,
        population = config.optimizer.population,
        maxIter = config.optimizer.maxIter,
        randomSeed = config.randomSeed,
        algorithms = config.realtime.algorithms,
        runs = config.realtime.runs,
        generatorType = config.realtime.generatorType
    )
    runner.runBatchExperiment()
}

/**
 * 解析任务数列表
 * 格式: batch-multi/realtime-multi 50,100,200,500 [algorithms] [randomSeed]
 */
private fun parseCloudletCounts(args: Array<String>): List<Int> {
    if (args.size < 2) return emptyList()
    
    // 尝试从第二个参数解析任务数列表
    val secondArg = args[1]
    if (secondArg.contains(",") && secondArg.split(",").all { it.trim().toIntOrNull() != null }) {
        return secondArg.split(",").map { it.trim().toInt() }
    }
    
    return emptyList()
}

/**
 * 打印使用说明
 */
private fun printUsage() {
    Logger.info("用法: java -jar cloudsim-benchmark-1.0.0-all.jar [batch|realtime|batch-multi|realtime-multi] [algorithms] [randomSeed]")
    Logger.info("")
    Logger.info("模式:")
    Logger.info("  batch         - 批处理调度模式（所有任务一次性提交）")
    Logger.info("  batch-multi   - 批处理模式批量任务数实验（按不同任务数批量执行）")
    Logger.info("  realtime      - 实时调度模式（任务动态到达，默认）")
    Logger.info("  realtime-multi - 实时调度模式批量任务数实验（按不同任务数批量执行）")
    Logger.info("")
    Logger.info("参数:")
    Logger.info("  algorithms   - 要运行的算法列表（可选，默认: 所有算法）")
    Logger.info("                批处理模式: RANDOM, PSO, WOA, GWO, HHO")
    Logger.info("                实时模式: MIN_LOAD, RANDOM, PSO_REALTIME, WOA_REALTIME")
    Logger.info("                多个算法用逗号分隔，例如: PSO,WOA")
    Logger.info("  randomSeed   - 随机数种子（可选，默认: 0）")
    Logger.info("")
    Logger.info("示例:")
    Logger.info("  # 运行所有算法")
    Logger.info("  java -jar cloudsim-benchmark-1.0.0-all.jar batch")
    Logger.info("")
    Logger.info("  # 只运行 PSO 和 WOA")
    Logger.info("  java -jar cloudsim-benchmark-1.0.0-all.jar batch PSO,WOA")
    Logger.info("")
    Logger.info("  # 运行指定算法并使用自定义随机种子")
    Logger.info("  java -jar cloudsim-benchmark-1.0.0-all.jar batch PSO,WOA 42")
    Logger.info("")
    Logger.info("  # 批处理模式批量任务数实验（测试 50, 100, 200, 500 个任务）")
    Logger.info("  java -jar cloudsim-benchmark-1.0.0-all.jar batch-multi 50,100,200,500")
    Logger.info("")
    Logger.info("  # 批处理模式批量任务数实验，指定算法")
    Logger.info("  java -jar cloudsim-benchmark-1.0.0-all.jar batch-multi 50,100,200 PSO,WOA")
    Logger.info("")
    Logger.info("  # 批处理模式批量任务数实验，指定算法和随机种子")
    Logger.info("  java -jar cloudsim-benchmark-1.0.0-all.jar batch-multi 50,100,200 PSO,WOA 42")
    Logger.info("")
    Logger.info("  # 实时调度模式批量任务数实验（测试 50, 100, 200, 500 个任务）")
    Logger.info("  java -jar cloudsim-benchmark-1.0.0-all.jar realtime-multi 50,100,200,500")
    Logger.info("")
    Logger.info("  # 实时调度模式批量任务数实验，指定算法")
    Logger.info("  java -jar cloudsim-benchmark-1.0.0-all.jar realtime-multi 50,100,200 PSO_REALTIME,WOA_REALTIME")
    Logger.info("")
    Logger.info("  # 实时调度模式批量任务数实验，指定算法和随机种子")
    Logger.info("  java -jar cloudsim-benchmark-1.0.0-all.jar realtime-multi 50,100,200 PSO_REALTIME,WOA_REALTIME 42")
    Logger.info("")
    Logger.info("  # 实时模式，只运行 PSO 和 WOA")
    Logger.info("  java -jar cloudsim-benchmark-1.0.0-all.jar realtime PSO_REALTIME,WOA_REALTIME")
    Logger.info("")
    Logger.info("配置:")
    Logger.info("  所有配置参数在 config.ExperimentConfig 中统一管理")
    Logger.info("  可以通过修改 ExperimentConfig 类来调整实验参数")
    Logger.info("  或在代码中直接设置 algorithms 列表来选择算法")
    Logger.info("  批量任务数实验的每个任务数会运行 config.batch.runs 次并取平均值")
}
