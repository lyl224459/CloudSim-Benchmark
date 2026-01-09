import config.ExperimentConfig
import datacenter.ComparisonRunner
import datacenter.RealtimeComparisonRunner
import datacenter.BatchCloudletCountRunner
import datacenter.RealtimeCloudletCountRunner
import util.Logger
import util.ResultsManager
import java.io.File
import java.time.LocalDateTime
import kotlin.system.exitProcess

/**
 * 命令行参数解析器
 * 提供结构化的参数解析和验证
 */
class CommandLineParser(private val args: Array<String>) {

    data class ParsedArgs(
        val mode: String,
        val algorithms: List<String> = emptyList(),
        val randomSeed: Long = 0L,
        val taskCounts: List<Int> = emptyList(),
        val runs: Int = 1
    )

    fun parse(): ParsedArgs {
        if (args.isEmpty()) {
            return ParsedArgs("realtime")
        }

        val mode = args[0].lowercase()
        val remainingArgs = args.drop(1)

        return when (mode) {
            "batch", "b" -> parseBasicArgs("batch", remainingArgs)
            "realtime", "r", "" -> parseBasicArgs("realtime", remainingArgs)
            "batch-multi", "bm" -> parseMultiArgs("batch-multi", remainingArgs)
            "realtime-multi", "rm" -> parseMultiArgs("realtime-multi", remainingArgs)
            else -> throw IllegalArgumentException("未知的运行模式: $mode")
        }
    }

    private fun parseBasicArgs(mode: String, args: List<String>): ParsedArgs {
        val algorithms = mutableListOf<String>()
        var randomSeed = 0L

        for (arg in args) {
            when {
                // 检查是否是随机种子（大数字）
                arg.toLongOrNull()?.let { it > 1000 } == true -> {
                    if (randomSeed != 0L) {
                        throw IllegalArgumentException("只能指定一个随机种子")
                    }
                    randomSeed = arg.toLong()
                }
                // 检查是否是算法名称
                else -> algorithms.add(arg)
            }
        }

        return ParsedArgs(mode, algorithms, randomSeed)
    }

    private fun parseMultiArgs(mode: String, args: List<String>): ParsedArgs {
        if (args.isEmpty()) {
            throw IllegalArgumentException("$mode 模式需要指定任务数列表")
        }

        val taskCounts = parseTaskCounts(args[0])
        val remainingArgs = args.drop(1)

        val algorithms = mutableListOf<String>()
        var randomSeed = 0L
        var runs = 1

        for (arg in remainingArgs) {
            when {
                // 检查是否是运行次数（小数字）
                arg.toIntOrNull()?.let { it in 1..100 } == true -> {
                    runs = arg.toInt()
                }
                // 检查是否是随机种子（大数字）
                arg.toLongOrNull()?.let { it > 1000 } == true -> {
                    if (randomSeed != 0L) {
                        throw IllegalArgumentException("只能指定一个随机种子")
                    }
                    randomSeed = arg.toLong()
                }
                // 其他参数当作算法名称
                else -> algorithms.add(arg)
            }
        }

        return ParsedArgs(mode, algorithms, randomSeed, taskCounts, runs)
    }

    private fun parseTaskCounts(arg: String): List<Int> {
        return arg.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { part ->
                part.toIntOrNull()
                    ?.takeIf { it > 0 }
                    ?: throw IllegalArgumentException("无效的任务数: $part")
            }
    }
}

/**
 * 主程序入口
 * 支持批处理和实时调度两种模式
 */
fun main(args: Array<String>) {
    try {
        // 设置全局异常处理器
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Logger.error("未捕获的异常在线程 " + thread.name + " 中发生", throwable)
            exitProcess(1)
        }

        Logger.info("CloudSim-Benchmark 启动")
        Logger.debug("Java版本: {}", System.getProperty("java.version"))
        Logger.debug("Kotlin版本: {}", kotlin.KotlinVersion.CURRENT)
        Logger.debug("操作系统: {}", System.getProperty("os.name"))
        Logger.debug("命令行参数: {}", args.joinToString(" "))

        // 验证基本环境
        validateEnvironment()

        // 解析命令行参数
        val parser = CommandLineParser(args)
        val parsedArgs = parser.parse()

        Logger.info("运行模式: {}", parsedArgs.mode)

        // 加载配置
        val config = loadConfig(parsedArgs)

        // 配置结果管理器
        ResultsManager.configure(config)

        // 创建实验目录
        val experimentDir = ResultsManager.createExperimentDirectory(
            parsedArgs.mode,
            experimentName = "${parsedArgs.mode}_${LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}"
        )

        Logger.info("实验目录: {}", experimentDir.absolutePath)

        when (parsedArgs.mode) {
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
                generatorType = config.batch.generatorType,
                googleTraceConfig = config.batch.googleTraceConfig
            )
            runner.runComparison()
            Logger.info("批处理实验完成！")
        }
        "batch-multi" -> {
            // 批处理模式批量任务数实验
            Logger.info("开始批处理模式批量任务数实验...")
            val cloudletCounts = parsedArgs.taskCounts.ifEmpty {
                Logger.warn("未指定任务数列表，使用默认值: 50, 100, 200, 500")
                listOf(50, 100, 200, 500)
            }
            runBatchCloudletCountExperiment(cloudletCounts, config, experimentDir)
        }
        "realtime-multi" -> {
            // 实时调度模式批量任务数实验
            Logger.info("开始实时调度模式批量任务数实验...")
            val cloudletCounts = parsedArgs.taskCounts.ifEmpty {
                Logger.warn("未指定任务数列表，使用默认值: 50, 100, 200, 500")
                listOf(50, 100, 200, 500)
            }
            runRealtimeCloudletCountExperiment(cloudletCounts, config, experimentDir)
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
                generatorType = config.realtime.generatorType,
                googleTraceConfig = config.realtime.googleTraceConfig
            )
            runner.runComparison()
            Logger.info("实时调度实验完成！")
        }
        else -> {
            Logger.error("未知的运行模式: ${parsedArgs.mode}")
            printUsage()
            throw IllegalArgumentException("未知的运行模式: ${parsedArgs.mode}")
        }
    }

        Logger.info("CloudSim-Benchmark 执行完成")

    } catch (e: IllegalArgumentException) {
        Logger.error("参数错误: " + e.message, e)
        if (Logger.getLogger("Main").isDebugEnabled) {
            e.printStackTrace()
        }
        System.exit(1)
    } catch (e: IllegalStateException) {
        Logger.error("环境错误: " + e.message, e)
        if (Logger.getLogger("Main").isDebugEnabled) {
            e.printStackTrace()
        }
        System.exit(1)
    } catch (e: OutOfMemoryError) {
        Logger.error("内存不足错误，请增加JVM内存参数 (-Xmx)")
        if (Logger.getLogger("Main").isDebugEnabled) {
            e.printStackTrace()
        }
        System.exit(1)
    } catch (e: Exception) {
        Logger.error("程序执行时发生未预期的错误: " + e.message, e)
        System.exit(1)
    }
}

/**
 * 加载配置
 * 支持从多种来源加载配置并应用命令行覆盖
 */
private fun loadConfig(parsedArgs: CommandLineParser.ParsedArgs): ExperimentConfig {
    // 使用新的配置加载器从文件和环境变量加载基础配置
    val baseConfig = ExperimentConfig.load()

    // 使用随机种子覆盖（如果指定了）
    val configWithSeed = if (parsedArgs.randomSeed > 0) {
        baseConfig.copy(randomSeed = parsedArgs.randomSeed)
    } else {
        baseConfig
    }

    // 根据模式设置算法配置和运行参数
    return when (parsedArgs.mode) {
        "batch", "batch-multi" -> {
            val batchAlgorithms = validateAndParseAlgorithms(parsedArgs.algorithms, config.BatchAlgorithmType.values(), "批处理")
            configWithSeed.copy(
                batch = configWithSeed.batch.copy(
                    algorithms = if (batchAlgorithms.isEmpty()) emptyList() else batchAlgorithms,
                    runs = if (parsedArgs.mode == "batch-multi") parsedArgs.runs else configWithSeed.batch.runs,
                    cloudletCount = if (parsedArgs.mode == "batch-multi" && parsedArgs.taskCounts.isNotEmpty())
                        parsedArgs.taskCounts.first() else configWithSeed.batch.cloudletCount
                )
            )
        }
        "realtime", "realtime-multi" -> {
            val realtimeAlgorithms = validateAndParseAlgorithms(parsedArgs.algorithms, config.RealtimeAlgorithmType.values(), "实时")
            configWithSeed.copy(
                realtime = configWithSeed.realtime.copy(
                    algorithms = if (realtimeAlgorithms.isEmpty()) emptyList() else realtimeAlgorithms,
                    runs = if (parsedArgs.mode == "realtime-multi") parsedArgs.runs else configWithSeed.realtime.runs,
                    cloudletCount = if (parsedArgs.mode == "realtime-multi" && parsedArgs.taskCounts.isNotEmpty())
                        parsedArgs.taskCounts.first() else configWithSeed.realtime.cloudletCount
                )
            )
        }
        else -> configWithSeed
    }
}

/**
 * 验证并解析算法名称
 */
private inline fun <reified T : Enum<T>> validateAndParseAlgorithms(
    algorithmNames: List<String>,
    validAlgorithms: Array<T>,
    modeName: String
): List<T> {
    if (algorithmNames.isEmpty()) return emptyList()

    val validNames = validAlgorithms.map { it.name }
    return algorithmNames.mapNotNull { name ->
        try {
            enumValueOf<T>(name.uppercase())
        } catch (e: IllegalArgumentException) {
            Logger.warn("未知的${modeName}算法: $name，已忽略")
            Logger.info("可用的${modeName}算法: ${validNames.joinToString(", ")}")
            null
        }
    }
}


/**
 * 运行批处理模式批量任务数实验
 */
private fun runBatchCloudletCountExperiment(cloudletCounts: List<Int>, config: ExperimentConfig, experimentDir: File) {
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
private fun runRealtimeCloudletCountExperiment(cloudletCounts: List<Int>, config: ExperimentConfig, experimentDir: File) {
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
 * 验证运行环境
 * @throws IllegalStateException 当环境不符合要求时
 */
private fun validateEnvironment() {
    try {
        // 检查Java版本
        val javaVersion = System.getProperty("java.version")
        Logger.debug("检测Java版本: {}", javaVersion)

        val versionParts = javaVersion.split(".").mapNotNull { it.toIntOrNull() }
        if (versionParts.isNotEmpty()) {
            val majorVersion = versionParts[0]
            if (majorVersion < 17) {
                Logger.warn("建议使用Java 17或更高版本，当前版本: {}", javaVersion)
            }
        }

        // 检查可用内存
        val runtime = Runtime.getRuntime()
        val maxMemoryMB = runtime.maxMemory() / 1024 / 1024
        Logger.debug("最大可用内存: {} MB", maxMemoryMB)

        if (maxMemoryMB < 512) {
            Logger.warn("可用内存较少 ({}} MB)，可能影响大規模实验性能", maxMemoryMB)
        }

        // 检查编码设置
        val fileEncoding = System.getProperty("file.encoding", "unknown")
        val stdoutEncoding = System.getProperty("sun.stdout.encoding", "unknown")
        val stderrEncoding = System.getProperty("sun.stderr.encoding", "unknown")

        Logger.debug("文件编码: {}", fileEncoding)
        Logger.debug("标准输出编码: {}", stdoutEncoding)
        Logger.debug("标准错误编码: {}", stderrEncoding)

        // 建议UTF-8编码
        if (fileEncoding != "UTF-8") {
            Logger.warn("建议使用UTF-8编码，当前文件编码: {}", fileEncoding)
        }

        // 检查CloudSim Plus依赖
        try {
            Class.forName("org.cloudsimplus.core.CloudSimPlus")
            Logger.debug("CloudSim Plus依赖加载成功")
        } catch (e: ClassNotFoundException) {
            throw IllegalStateException("找不到CloudSim Plus依赖，请检查classpath", e)
        }

    } catch (e: Exception) {
        Logger.error("环境验证失败: " + e.message, e)
        throw IllegalStateException("环境验证失败: ${e.message}", e)
    }
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
