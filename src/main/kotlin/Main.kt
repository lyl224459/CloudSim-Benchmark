import config.ExperimentConfig
import datacenter.ComparisonRunner
import datacenter.RealtimeComparisonRunner
import datacenter.BatchCloudletCountRunner
import datacenter.RealtimeCloudletCountRunner
import datacenter.CoroutineDemo
import util.Logger
import util.ResultsManager
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDateTime
import kotlin.system.exitProcess

/**
 * 统一的命令行参数解析器
 * 支持命名参数格式，提供更好的可扩展性和一致性
 */
class CommandLineParser(private val args: Array<String>) {

    data class ParsedArgs(
        val mode: String,
        val algorithms: List<String> = emptyList(),
        val randomSeed: Long = 0L,
        val taskCounts: List<Int> = emptyList(),
        val runs: Int = 1,
        val configFile: String? = null,
        val verbose: Boolean = false,
        val outputDir: String? = null
    )

    private val namedArgs = mutableMapOf<String, String>()
    private var positionalArgs = listOf<String>()

    fun parse(): ParsedArgs {
        val mode = parseArgs()

        return when (mode) {
            "batch", "b" -> parseBatchArgs(mode)
            "realtime", "r" -> parseRealtimeArgs(mode)
            "batch-multi", "bm" -> parseBatchMultiArgs(mode)
            "realtime-multi", "rm" -> parseRealtimeMultiArgs(mode)
            "coroutine-demo", "cd" -> ParsedArgs("coroutine-demo")
            else -> throw IllegalArgumentException("未知的运行模式: $mode")
        }
    }

    private fun parseArgs(): String {
        var i = 0
        var mode: String? = null

        while (i < args.size) {
            val arg = args[i]
            when {
                arg.startsWith("--") -> {
                    // 长参数: --name value
                    val name = arg.substring(2)
                    if (name == "mode") {
                        mode = args.getOrNull(i + 1)?.lowercase()
                        i += 2
                    } else if (i + 1 < args.size && !args[i + 1].startsWith("-")) {
                        namedArgs[name] = args[i + 1]
                        i += 2
                    } else {
                        namedArgs[name] = "true" // 布尔标志
                        i++
                    }
                }
                arg.startsWith("-") && arg.length > 1 -> {
                    // 短参数: -n value 或 -v (布尔)
                    val name = arg.substring(1)
                    when (name) {
                        "v" -> namedArgs["verbose"] = "true"
                        "h" -> namedArgs["help"] = "true"
                        else -> {
                            if (i + 1 < args.size && !args[i + 1].startsWith("-")) {
                                namedArgs[name] = args[i + 1]
                                i += 2
                            } else {
                                namedArgs[name] = "true"
                                i++
                            }
                        }
                    }
                }
                else -> {
                    // 第一个位置参数作为模式（如果还没有设置模式）
                    if (mode == null) {
                        mode = arg.lowercase()
                        i++
                    } else {
                        // 其他位置参数
                        positionalArgs = args.drop(i)
                        break
                    }
                }
            }
        }

        return mode ?: "realtime"
    }

    private fun parseBatchArgs(mode: String): ParsedArgs {
        return ParsedArgs(
            mode = mode,
            algorithms = parseAlgorithms(namedArgs["algorithms"] ?: parseLegacyAlgorithms(positionalArgs)),
            randomSeed = parseRandomSeed(namedArgs["seed"] ?: parseLegacyRandomSeed(positionalArgs)),
            runs = parseRuns(namedArgs["runs"]),
            configFile = namedArgs["config"],
            verbose = parseBoolean(namedArgs["verbose"]),
            outputDir = namedArgs["output"]
        )
    }

    private fun parseRealtimeArgs(mode: String): ParsedArgs {
        return ParsedArgs(
            mode = mode,
            algorithms = parseAlgorithms(namedArgs["algorithms"] ?: parseLegacyAlgorithms(positionalArgs)),
            randomSeed = parseRandomSeed(namedArgs["seed"] ?: parseLegacyRandomSeed(positionalArgs)),
            runs = parseRuns(namedArgs["runs"]),
            configFile = namedArgs["config"],
            verbose = parseBoolean(namedArgs["verbose"]),
            outputDir = namedArgs["output"]
        )
    }

    private fun parseBatchMultiArgs(mode: String): ParsedArgs {
        val taskCounts = parseTaskCounts(namedArgs["tasks"] ?: positionalArgs.getOrNull(0)
            ?: throw IllegalArgumentException("$mode 模式需要指定任务数列表 (--tasks 或位置参数)"))

        return ParsedArgs(
            mode = mode,
            algorithms = parseAlgorithms(namedArgs["algorithms"] ?: parseLegacyAlgorithms(positionalArgs.drop(1))),
            randomSeed = parseRandomSeed(namedArgs["seed"] ?: parseLegacyRandomSeed(positionalArgs.drop(2))),
            taskCounts = taskCounts,
            runs = parseRuns(namedArgs["runs"] ?: parseLegacyRuns(positionalArgs.drop(3))),
            configFile = namedArgs["config"],
            verbose = parseBoolean(namedArgs["verbose"]),
            outputDir = namedArgs["output"]
        )
    }

    private fun parseRealtimeMultiArgs(mode: String): ParsedArgs {
        val taskCounts = parseTaskCounts(namedArgs["tasks"] ?: positionalArgs.getOrNull(0)
            ?: throw IllegalArgumentException("$mode 模式需要指定任务数列表 (--tasks 或位置参数)"))

        return ParsedArgs(
            mode = mode,
            algorithms = parseAlgorithms(namedArgs["algorithms"] ?: parseLegacyAlgorithms(positionalArgs.drop(1))),
            randomSeed = parseRandomSeed(namedArgs["seed"] ?: parseLegacyRandomSeed(positionalArgs.drop(2))),
            taskCounts = taskCounts,
            runs = parseRuns(namedArgs["runs"] ?: parseLegacyRuns(positionalArgs.drop(3))),
            configFile = namedArgs["config"],
            verbose = parseBoolean(namedArgs["verbose"]),
            outputDir = namedArgs["output"]
        )
    }

    private fun parseLegacyAlgorithms(args: List<String>): String? {
        // 过滤掉命名参数、随机种子（大数字 > 1000）、运行次数（小数字 1-100），剩下的当作算法名称
        return args.filter { arg ->
            !arg.startsWith("-") &&
            arg.toLongOrNull()?.let { num -> num <= 1000 } != true &&
            arg.toIntOrNull()?.let { num -> num in 1..100 } != true
        }.joinToString(",").takeIf { it.isNotEmpty() }
    }

    private fun parseLegacyRandomSeed(args: List<String>): String? {
        return args.firstOrNull { it.toLongOrNull()?.let { num -> num > 1000 } == true }
    }

    private fun parseLegacyRuns(args: List<String>): String? {
        return args.firstOrNull { it.toIntOrNull()?.let { num -> num in 1..100 } == true }
    }

    private fun parseAlgorithms(value: String?): List<String> {
        return value?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    }

    private fun parseRandomSeed(value: String?): Long {
        return value?.toLongOrNull() ?: 0L
    }

    private fun parseRuns(value: String?): Int {
        return value?.toIntOrNull()?.takeIf { it > 0 } ?: 1
    }

    private fun parseBoolean(value: String?): Boolean {
        return value?.lowercase() in listOf("true", "1", "yes", "on")
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
 * 支持批处理和实时调度两种模式（默认使用协程优化）
 */
fun main(args: Array<String>) = runBlocking {
    try {
        // 设置全局异常处理器
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Logger.error("未捕获的异常在线程 " + thread.name + " 中发生", throwable)
            exitProcess(1)
        }

        Logger.info("CloudSim-Benchmark 启动（协程优化模式）")
        Logger.debug("Java版本: {}", System.getProperty("java.version"))
        Logger.debug("Kotlin版本: {}", kotlin.KotlinVersion.CURRENT)
        Logger.debug("操作系统: {}", System.getProperty("os.name"))
        Logger.debug("命令行参数: {}", args.joinToString(" "))

        // 验证基本环境
        validateEnvironment()

        // 检查是否显示帮助信息
        if (args.contains("--help") || args.contains("-h")) {
            printUsage()
            exitProcess(0)
        }

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
        "coroutine-demo", "cd" -> {
            Logger.info("运行协程优化功能演示...")
            CoroutineDemo.runDemo()
        }
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
                googleTraceConfig = config.batch.googleTraceConfig,
                objectiveWeights = config.batch.objectiveWeights,
                experimentDir = experimentDir
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
                googleTraceConfig = config.realtime.googleTraceConfig,
                objectiveWeights = config.realtime.objectiveWeights,
                experimentDir = experimentDir
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

    } catch (e: config.ConfigValidationException) {
        Logger.error("配置验证失败: " + e.message)
        // ConfigValidationException 已经包含详细的错误信息，无需额外打印堆栈
        System.exit(1)
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
        generatorType = config.batch.generatorType,
        experimentDir = experimentDir
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
        generatorType = config.realtime.generatorType,
        experimentDir = experimentDir
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
    Logger.info("CloudSim-Benchmark 统一命令行参数格式")
    Logger.info("")
    Logger.info("用法: java -jar cloudsim-benchmark.jar [mode] [命名参数...]")
    Logger.info("")
    Logger.info("运行模式:")
    Logger.info("  batch, b          - 批处理调度模式（所有任务一次性提交）")
    Logger.info("  realtime, r       - 实时调度模式（任务动态到达，默认）")
    Logger.info("  batch-multi, bm   - 批处理模式批量任务数实验")
    Logger.info("  realtime-multi, rm - 实时调度模式批量任务数实验")
    Logger.info("  coroutine-demo, cd - 协程优化功能演示")
    Logger.info("")
    Logger.info("命名参数 (支持长参数 --name value 和短参数 -n value):")
    Logger.info("  --algorithms, -a ALGO1,ALGO2,...  要运行的算法列表")
    Logger.info("                                    批处理: RANDOM, PSO, WOA, GWO, HHO, RL, IMPROVED_RL")
    Logger.info("                                    实时: MIN_LOAD, RANDOM, PSO_REALTIME, WOA_REALTIME")
    Logger.info("  --seed, -s SEED                  随机数种子 (默认: 0)")
    Logger.info("  --tasks, -t COUNT1,COUNT2,...    批量实验的任务数列表 (multi模式必需)")
    Logger.info("  --runs, -r COUNT                 运行次数 (默认: 1)")
    Logger.info("  --config, -c FILE               配置文件路径")
    Logger.info("  --output, -o DIR                输出目录")
    Logger.info("  --verbose, -v                   详细输出模式")
    Logger.info("  --help, -h                      显示此帮助信息")
    Logger.info("")
    Logger.info("示例:")
    Logger.info("")
    Logger.info("  # 基本用法 - 运行所有算法")
    Logger.info("  java -jar cloudsim-benchmark.jar batch")
    Logger.info("  java -jar cloudsim-benchmark.jar realtime")
    Logger.info("")
    Logger.info("  # 指定算法 (新格式，推荐)")
    Logger.info("  java -jar cloudsim-benchmark.jar batch --algorithms PSO,WOA,GWO")
    Logger.info("  java -jar cloudsim-benchmark.jar realtime -a PSO_REALTIME,WOA_REALTIME")
    Logger.info("")
    Logger.info("  # 随机种子和运行次数")
    Logger.info("  java -jar cloudsim-benchmark.jar batch --algorithms PSO --seed 42 --runs 5")
    Logger.info("  java -jar cloudsim-benchmark.jar realtime -a WOA_REALTIME -s 123 -r 10")
    Logger.info("")
    Logger.info("  # 批量任务数实验")
    Logger.info("  java -jar cloudsim-benchmark.jar batch-multi --tasks 50,100,200,500 --algorithms PSO,WOA")
    Logger.info("  java -jar cloudsim-benchmark.jar realtime-multi -t 50,100,200 -a PSO_REALTIME -s 42")
    Logger.info("")
    Logger.info("  # 高级配置")
    Logger.info("  java -jar cloudsim-benchmark.jar batch --config custom.toml --output results/ --verbose")
    Logger.info("")
    Logger.info("向后兼容 (旧格式仍支持，但推荐使用新格式):")
    Logger.info("  java -jar cloudsim-benchmark.jar batch PSO,WOA 42    # 旧格式")
    Logger.info("  java -jar cloudsim-benchmark.jar batch -a PSO,WOA -s 42  # 新格式")
    Logger.info("")
    Logger.info("配置:")
    Logger.info("  所有配置参数在 config.ExperimentConfig 中统一管理")
    Logger.info("  支持 TOML 配置文件，运行时可通过 --config 指定")
    Logger.info("  批量实验的每个任务数会运行多次并计算统计值")
}
