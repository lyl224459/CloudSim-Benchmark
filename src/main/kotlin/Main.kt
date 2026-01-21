import config.ExperimentConfig
import config.SystemConfig
import config.ConfigurationManager
import config.BatchAlgorithmType
import config.RealtimeAlgorithmType
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
 * 简化的命令行参数解析器
 * 只支持命名参数格式，放弃向后兼容性以获得更清晰的接口
 */
class CommandLineParser(private val args: Array<String>) {

    data class ParsedArgs(
        val mode: String? = null,  // 现在可以为空，因为可以从配置文件获取
        val algorithms: List<String> = emptyList(),
        val randomSeed: Long = 0L,
        val taskCounts: List<Int> = emptyList(),
        val runs: Int = 1,
        val configFile: String? = null,
        val verbose: Boolean = false,
        val outputDir: String? = null,
        val useCoroutines: Boolean = true,
        val maxConcurrency: Int = 0
    )

    private val namedArgs = mutableMapOf<String, String>()

    fun parse(): ParsedArgs {
        val mode = parseArgs()

        return when (mode) {
            "batch", "b" -> parseBatchArgs(mode)
            "realtime", "r" -> parseRealtimeArgs(mode)
            "batch-multi", "bm" -> parseBatchMultiArgs(mode)
            "realtime-multi", "rm" -> parseRealtimeMultiArgs(mode)
            "coroutine-demo", "cd" -> ParsedArgs("coroutine-demo")
            null -> ParsedArgs()  // 从配置文件获取模式
            else -> throw IllegalArgumentException("未知的运行模式: $mode。可用模式: batch, realtime, batch-multi, realtime-multi, coroutine-demo")
        }
    }

    private fun parseArgs(): String? {
        var i = 0
        var mode: String? = null

        while (i < args.size) {
            val arg = args[i]
            when {
                arg.startsWith("--") -> {
                    // 长参数: --name value 或 --flag
                    val name = arg.substring(2)
                    if (name == "mode") {
                        if (i + 1 < args.size) {
                            mode = args[i + 1].lowercase()
                            i += 2
                        } else {
                            throw IllegalArgumentException("--mode 参数需要指定值")
                        }
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
                        "v" -> {
                            namedArgs["verbose"] = "true"
                            i++
                        }
                        "h" -> {
                            namedArgs["help"] = "true"
                            i++
                        }
                        "p" -> {
                            namedArgs["sequential"] = "false"
                            i++
                        }
                        "S" -> {
                            namedArgs["sequential"] = "true"
                            i++
                        }
                        "a" -> {
                            if (i + 1 < args.size && !args[i + 1].startsWith("-")) {
                                namedArgs["algorithms"] = args[i + 1]
                                i += 2
                            } else {
                                throw IllegalArgumentException("-a 参数需要指定算法列表")
                            }
                        }
                        "s" -> {
                            if (i + 1 < args.size && !args[i + 1].startsWith("-")) {
                                namedArgs["seed"] = args[i + 1]
                                i += 2
                            } else {
                                throw IllegalArgumentException("-s 参数需要指定种子值")
                            }
                        }
                        "r" -> {
                            if (i + 1 < args.size && !args[i + 1].startsWith("-")) {
                                namedArgs["runs"] = args[i + 1]
                                i += 2
                            } else {
                                throw IllegalArgumentException("-r 参数需要指定运行次数")
                            }
                        }
                        "t" -> {
                            if (i + 1 < args.size && !args[i + 1].startsWith("-")) {
                                namedArgs["tasks"] = args[i + 1]
                                i += 2
                            } else {
                                throw IllegalArgumentException("-t 参数需要指定任务数列表")
                            }
                        }
                        "c" -> {
                            if (i + 1 < args.size && !args[i + 1].startsWith("-")) {
                                namedArgs["config"] = args[i + 1]
                                i += 2
                            } else {
                                throw IllegalArgumentException("-c 参数需要指定配置文件")
                            }
                        }
                        "o" -> {
                            if (i + 1 < args.size && !args[i + 1].startsWith("-")) {
                                namedArgs["output"] = args[i + 1]
                                i += 2
                            } else {
                                throw IllegalArgumentException("-o 参数需要指定输出目录")
                            }
                        }
                        "C" -> {
                            if (i + 1 < args.size && !args[i + 1].startsWith("-")) {
                                namedArgs["concurrency"] = args[i + 1]
                                i += 2
                            } else {
                                throw IllegalArgumentException("-C 参数需要指定并发数")
                            }
                        }
                        else -> throw IllegalArgumentException("未知的短参数: -$name")
                    }
                }
                else -> {
                    // 第一个非参数作为模式
                    if (mode == null) {
                        mode = arg.lowercase()
                        i++
                    } else {
                        throw IllegalArgumentException("意外的位置参数: $arg。所有参数都应使用命名格式 (--name value)")
                    }
                }
            }
        }

        return mode
    }

    private fun parseBatchArgs(mode: String): ParsedArgs {
        return ParsedArgs(
            mode = mode,
            algorithms = parseAlgorithms(namedArgs["algorithms"] ?: throw IllegalArgumentException("$mode 模式需要指定算法列表 (--algorithms ALGO1,ALGO2 或 --algorithms ALL)"), "batch"),
            randomSeed = parseRandomSeed(namedArgs["seed"]),
            runs = parseRuns(namedArgs["runs"]),
            configFile = namedArgs["config"],
            verbose = parseBoolean(namedArgs["verbose"]),
            outputDir = namedArgs["output"],
            useCoroutines = !parseBoolean(namedArgs["sequential"]),
            maxConcurrency = parseConcurrency(namedArgs["concurrency"])
        )
    }

    private fun parseRealtimeArgs(mode: String): ParsedArgs {
        return ParsedArgs(
            mode = mode,
            algorithms = parseAlgorithms(namedArgs["algorithms"] ?: throw IllegalArgumentException("$mode 模式需要指定算法列表 (--algorithms ALGO1,ALGO2 或 --algorithms ALL)"), "realtime"),
            randomSeed = parseRandomSeed(namedArgs["seed"]),
            runs = parseRuns(namedArgs["runs"]),
            configFile = namedArgs["config"],
            verbose = parseBoolean(namedArgs["verbose"]),
            outputDir = namedArgs["output"],
            useCoroutines = !parseBoolean(namedArgs["sequential"]),
            maxConcurrency = parseConcurrency(namedArgs["concurrency"])
        )
    }

    private fun parseBatchMultiArgs(mode: String): ParsedArgs {
        return ParsedArgs(
            mode = mode,
            algorithms = parseAlgorithms(namedArgs["algorithms"] ?: throw IllegalArgumentException("$mode 模式需要指定算法列表 (--algorithms ALGO1,ALGO2 或 --algorithms ALL)"), "batch"),
            randomSeed = parseRandomSeed(namedArgs["seed"]),
            taskCounts = parseTaskCounts(namedArgs["tasks"] ?: throw IllegalArgumentException("$mode 模式需要指定任务数列表 (--tasks COUNT1,COUNT2)")),
            runs = parseRuns(namedArgs["runs"]),
            configFile = namedArgs["config"],
            verbose = parseBoolean(namedArgs["verbose"]),
            outputDir = namedArgs["output"],
            useCoroutines = !parseBoolean(namedArgs["sequential"]),
            maxConcurrency = parseConcurrency(namedArgs["concurrency"])
        )
    }

    private fun parseRealtimeMultiArgs(mode: String): ParsedArgs {
        return ParsedArgs(
            mode = mode,
            algorithms = parseAlgorithms(namedArgs["algorithms"] ?: throw IllegalArgumentException("$mode 模式需要指定算法列表 (--algorithms ALGO1,ALGO2 或 --algorithms ALL)"), "realtime"),
            randomSeed = parseRandomSeed(namedArgs["seed"]),
            taskCounts = parseTaskCounts(namedArgs["tasks"] ?: throw IllegalArgumentException("$mode 模式需要指定任务数列表 (--tasks COUNT1,COUNT2)")),
            runs = parseRuns(namedArgs["runs"]),
            configFile = namedArgs["config"],
            verbose = parseBoolean(namedArgs["verbose"]),
            outputDir = namedArgs["output"],
            useCoroutines = !parseBoolean(namedArgs["sequential"]),
            maxConcurrency = parseConcurrency(namedArgs["concurrency"])
        )
    }

    private fun parseAlgorithms(value: String?, mode: String): List<String> {
        if (value == null) return emptyList()

        val algorithms = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        // 处理特殊值 "ALL"
        if (algorithms.size == 1 && algorithms[0].uppercase() == "ALL") {
            return when (mode) {
                "batch", "batch-multi" -> BatchAlgorithmType.values().map { it.name }
                "realtime", "realtime-multi" -> RealtimeAlgorithmType.values().map { it.name }
                else -> throw IllegalArgumentException("未知的运行模式: $mode")
            }
        }

        return algorithms
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

    private fun parseConcurrency(value: String?): Int {
        return value?.toIntOrNull()?.takeIf { it > 0 } ?: 0
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

        // 使用配置管理器加载配置
        val configs = loadConfigs(parsedArgs)
        
        // 确定运行模式：优先使用命令行参数，其次使用配置文件中的模式
        val mode = if (!parsedArgs.mode.isNullOrEmpty()) {
            parsedArgs.mode
        } else {
            // 将枚举转换为小写字符串
            configs.experimentConfig.mode.name.lowercase()
        }
        
        Logger.info("运行模式: {}", mode)

        // 根据系统配置更新日志设置
        configureLogging(configs.systemConfig)

        // 配置结果管理器
        ResultsManager.configure(configs.experimentConfig)

        // 创建实验目录
        val experimentDir = ResultsManager.createExperimentDirectory(
            mode,
            experimentName = "${mode}_${LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}"
        )

        Logger.info("实验目录: {}", experimentDir.absolutePath)

        when (mode) {
        "coroutine-demo", "cd" -> {
            Logger.info("运行协程优化功能演示...")
            CoroutineDemo.runDemo()
        }
        "batch", "b" -> {
            Logger.info("开始批处理调度算法对比实验...")
            Logger.info("配置: 任务数={}, 种群={}, 迭代={}, 运行次数={}", 
                configs.experimentConfig.batch.cloudletCount, configs.experimentConfig.batch.population, configs.experimentConfig.batch.maxIter, configs.experimentConfig.batch.runs)
            Logger.info("随机数种子: {}", configs.experimentConfig.randomSeed)
            val runner = ComparisonRunner(
                cloudletCount = configs.experimentConfig.batch.cloudletCount,
                population = configs.experimentConfig.batch.population,
                maxIter = configs.experimentConfig.batch.maxIter,
                randomSeed = configs.experimentConfig.randomSeed,
                algorithms = configs.experimentConfig.batch.algorithms,
                runs = configs.experimentConfig.batch.runs,
                generatorType = configs.experimentConfig.batch.generatorType,
                googleTraceConfig = configs.experimentConfig.batch.googleTraceConfig,
                objectiveWeights = configs.experimentConfig.batch.objectiveWeights,
                experimentDir = experimentDir,
                useCoroutines = parsedArgs.useCoroutines,
                maxConcurrency = parsedArgs.maxConcurrency
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
            runBatchCloudletCountExperiment(cloudletCounts, configs.experimentConfig, experimentDir)
        }
        "realtime-multi" -> {
            // 实时调度模式批量任务数实验
            Logger.info("开始实时调度模式批量任务数实验...")
            val cloudletCounts = parsedArgs.taskCounts.ifEmpty {
                Logger.warn("未指定任务数列表，使用默认值: 50, 100, 200, 500")
                listOf(50, 100, 200, 500)
            }
            runRealtimeCloudletCountExperiment(cloudletCounts, configs.experimentConfig, experimentDir)
        }
        "realtime", "r" -> {
            Logger.info("开始实时调度算法对比实验...")
            Logger.info("配置: 任务数={}, 持续时间={}s, 到达率={}/s, 运行次数={}", 
                configs.experimentConfig.realtime.cloudletCount, configs.experimentConfig.realtime.simulationDuration, configs.experimentConfig.realtime.arrivalRate, configs.experimentConfig.realtime.runs)
            Logger.info("优化算法: 种群={}, 迭代={}", 
                configs.experimentConfig.optimizer.population, configs.experimentConfig.optimizer.maxIter)
            Logger.info("随机数种子: {}", configs.experimentConfig.randomSeed)
            if (configs.experimentConfig.realtime.algorithms.isNotEmpty()) {
                Logger.info("选定算法: {}", configs.experimentConfig.realtime.algorithms.joinToString(", ") { it.name })
            } else {
                Logger.info("运行所有算法")
            }
            val runner = RealtimeComparisonRunner(
                cloudletCount = configs.experimentConfig.realtime.cloudletCount,
                simulationDuration = configs.experimentConfig.realtime.simulationDuration,
                arrivalRate = configs.experimentConfig.realtime.arrivalRate,
                population = configs.experimentConfig.optimizer.population,
                maxIter = configs.experimentConfig.optimizer.maxIter,
                randomSeed = configs.experimentConfig.randomSeed,
                algorithms = configs.experimentConfig.realtime.algorithms,
                runs = configs.experimentConfig.realtime.runs,
                generatorType = configs.experimentConfig.realtime.generatorType,
                googleTraceConfig = configs.experimentConfig.realtime.googleTraceConfig,
                objectiveWeights = configs.experimentConfig.realtime.objectiveWeights,
                experimentDir = experimentDir,
                useCoroutines = parsedArgs.useCoroutines,
                maxConcurrency = parsedArgs.maxConcurrency
            )
            runner.runComparison()
            Logger.info("实时调度实验完成！")
        }
        else -> {
            Logger.error("未知的运行模式: ${mode}")
            printUsage()
            throw IllegalArgumentException("未知的运行模式: ${mode}")
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
 * 加载系统配置和实验配置
 */
private fun loadConfigs(parsedArgs: CommandLineParser.ParsedArgs): ConfigurationManager.LoadedConfigs {
    return if (!parsedArgs.configFile.isNullOrEmpty()) {
        ConfigurationManager.loadFromSingleFile(parsedArgs.configFile!!)
    } else {
        // 如果没有提供配置文件，使用默认配置
        ConfigurationManager.LoadedConfigs(
            systemConfig = SystemConfig.createDefault(),
            experimentConfig = ExperimentConfig.createDefault()
        )
    }
}

/**
 * 根据系统配置更新日志设置
 */
private fun configureLogging(systemConfig: SystemConfig) {
    // 可以在这里根据系统配置更新日志级别
    // 目前我们使用logback.xml中的配置
    Logger.info("系统配置加载完成 - 输出目录: ${systemConfig.output.resultsDir}, 日志级别: ${systemConfig.logging.level}")
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
            Logger.warn("可用内存较少 ({} MB)，可能影响大規模实验性能", maxMemoryMB)
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
    Logger.info("CloudSim-Benchmark 命令行参数格式")
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
    Logger.info("  --algorithms, -a ALGO1,ALGO2,...  要运行的算法列表 (必需)")
    Logger.info("                                    或使用 'ALL' 运行该模式下的所有算法")
    Logger.info("                                    批处理: RANDOM, PSO, WOA, GWO, HHO, RL, IMPROVED_RL")
    Logger.info("                                    实时: MIN_LOAD, RANDOM, PSO_REALTIME, WOA_REALTIME")
    Logger.info("  --seed, -s SEED                  随机数种子 (默认: 0)")
    Logger.info("  --tasks, -t COUNT1,COUNT2,...    批量实验的任务数列表 (multi模式必需)")
    Logger.info("  --runs, -r COUNT                 运行次数 (默认: 1)")
    Logger.info("  --config, -c FILE               配置文件路径")
    Logger.info("  --output, -o DIR                输出目录")
    Logger.info("  --verbose, -v                   详细输出模式")
    Logger.info("  --sequential, -S                禁用协程并行执行，使用顺序执行")
    Logger.info("  --concurrency, -C NUM          最大并发数 (默认: CPU核心数)")
    Logger.info("  --help, -h                      显示此帮助信息")
    Logger.info("")
    Logger.info("示例:")
    Logger.info("")
    Logger.info("  # 批处理模式 - 运行所有算法")
    Logger.info("  java -jar cloudsim-benchmark.jar batch --algorithms ALL")
    Logger.info("  java -jar cloudsim-benchmark.jar batch -a ALL")
    Logger.info("  ")
    Logger.info("  # 从单一配置文件运行实验（配置文件中指定模式）")
    Logger.info("  java -jar cloudsim-benchmark.jar --config my_experiment_with_mode.toml")
    Logger.info("  ")
    Logger.info("  # 批处理模式 - 指定算法")
    Logger.info("  java -jar cloudsim-benchmark.jar batch --algorithms PSO,WOA,GWO")
    Logger.info("  java -jar cloudsim-benchmark.jar batch -a PSO,WOA,GWO")
    Logger.info("  ")
    Logger.info("  # 实时调度模式 - 运行所有算法")
    Logger.info("  java -jar cloudsim-benchmark.jar realtime --algorithms ALL")
    Logger.info("  java -jar cloudsim-benchmark.jar realtime -a ALL")
    Logger.info("  ")
    Logger.info("  # 实时调度模式 - 指定算法")
    Logger.info("  java -jar cloudsim-benchmark.jar realtime --algorithms PSO_REALTIME,WOA_REALTIME")
    Logger.info("  java -jar cloudsim-benchmark.jar realtime -a PSO_REALTIME,WOA_REALTIME")
    Logger.info("  ")
    Logger.info("  # 设置随机种子和运行次数")
    Logger.info("  java -jar cloudsim-benchmark.jar batch --algorithms PSO --seed 42 --runs 5")
    Logger.info("  java -jar cloudsim-benchmark.jar batch -a PSO -s 42 -r 5")
    Logger.info("  ")
    Logger.info("  # 批量任务数实验 - 运行所有算法")
    Logger.info("  java -jar cloudsim-benchmark.jar batch-multi --tasks 50,100,200,500 --algorithms ALL")
    Logger.info("  java -jar cloudsim-benchmark.jar batch-multi -t 50,100,200,500 -a ALL")
    Logger.info("  ")
    Logger.info("  # 批量任务数实验 - 指定算法")
    Logger.info("  java -jar cloudsim-benchmark.jar batch-multi --tasks 50,100,200,500 --algorithms PSO,WOA")
    Logger.info("  java -jar cloudsim-benchmark.jar batch-multi -t 50,100,200,500 -a PSO,WOA")
    Logger.info("  ")
    Logger.info("  # 高级配置")
    Logger.info("  java -jar cloudsim-benchmark.jar --config custom.toml --output results/ --verbose")
    Logger.info("  ")
    Logger.info("配置:")
    Logger.info("  实验配置在 config.ExperimentConfig 中管理")
    Logger.info("  系统配置在 config.SystemConfig 中管理")
    Logger.info("  支持 TOML 配置文件，运行时可通过 --config 指定单一配置文件")
    Logger.info("  配置文件中可指定实验模式，如：[mode] = \"batch\"")
    Logger.info("  批量实验的每个任务数会运行多次并计算统计值")
}