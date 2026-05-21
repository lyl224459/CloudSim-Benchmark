package cli

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import datacenter.BatchCloudletCountRunner
import datacenter.ComparisonRunner
import datacenter.RealtimeCloudletCountRunner
import datacenter.RealtimeComparisonRunner
import org.slf4j.LoggerFactory
import util.Logger
import util.ExperimentConcurrency
import util.ExperimentOutputContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object CommandExecutor {
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    suspend fun execute(command: CliParser.Command) {
        when (command) {
            CliParser.HelpCommand -> DryRunPrinter.printUsage()
            is CliParser.ListAlgorithmsCommand -> DryRunPrinter.printAlgorithms(command.mode)
            is CliParser.ListProfilesCommand -> {
                val configs = RunResolver.mergeAlgorithmLibrary(RunResolver.loadBaseConfigs(command.configFile))
                DryRunPrinter.printProfiles(configs.experimentConfig)
            }
            is CliParser.ListPresetsCommand -> {
                val configs = RunResolver.mergeAlgorithmLibrary(RunResolver.loadBaseConfigs(command.configFile))
                DryRunPrinter.printPresets(configs.experimentConfig.presets)
            }
            is CliParser.ConfigValidateCommand -> {
                RunResolver.mergeAlgorithmLibrary(RunResolver.loadBaseConfigs(command.configFile))
                Logger.result("配置验证通过: {}", command.configFile)
            }
            is CliParser.ConfigPrintCommand -> {
                val resolved = RunResolver.resolve(
                    CliParser.RunCommand(configFile = command.configFile, profile = command.profile)
                )
                Logger.result(DryRunPrinter.resolvedJson(resolved, null, "resolved"))
            }
            is CliParser.RunCommand -> run(command)
        }
    }

    private suspend fun run(command: CliParser.RunCommand) {
        validateEnvironment()
        val resolved = RunResolver.resolve(command)
        configureLogging(resolved.systemConfig)

        if (command.dryRun) {
            DryRunPrinter.printDryRun(resolved)
            return
        }

        val timestamp = LocalDateTime.now().format(timestampFormatter)
        val experimentName = RunResolver.renderExperimentName(resolved, timestamp)
        val outputContext = ExperimentOutputContext.createExperiment(resolved.systemConfig, resolved.mode, experimentName)
        val experimentDir = outputContext.experimentDir
            ?: throw IllegalStateException("无法创建实验输出目录")

        Logger.info("实验目录: {}", experimentDir.absolutePath)
        outputContext.saveResolvedConfig(DryRunPrinter.resolvedJson(resolved, experimentDir, timestamp))

        runResolvedExperiment(resolved, outputContext)
        Logger.info("CloudSim-Benchmark 执行完成")
    }

    private suspend fun runResolvedExperiment(resolved: ResolvedExperimentConfig, outputContext: ExperimentOutputContext) {
        val config = resolved.experimentConfig
        val experimentDir = outputContext.experimentDir
            ?: throw IllegalStateException("运行实验需要有效的输出目录")
        val concurrency = ExperimentConcurrency(
            resolved.execution.useCoroutines,
            resolved.execution.maxConcurrency
        )

        when (resolved.mode) {
            "batch" -> {
                Logger.info("开始批处理调度算法对比实验...")
                val runner = ComparisonRunner(
                    cloudletCount = config.batch.cloudletCount,
                    population = config.batch.population,
                    maxIter = config.batch.maxIter,
                    randomSeed = config.randomSeed,
                    resolvedAlgorithms = resolved.algorithms,
                    runs = config.batch.runs,
                    generatorType = config.batch.generatorType,
                    googleTraceConfig = config.batch.googleTraceConfig,
                    objectiveWeights = config.batch.objectiveWeights,
                    experimentDir = experimentDir,
                    outputContext = outputContext,
                    concurrency = concurrency
                )
                runner.runComparison()
                Logger.info("批处理实验完成！")
            }
            "batch-multi" -> {
                Logger.info("开始批处理模式批量任务数实验...")
                val runner = BatchCloudletCountRunner(
                    cloudletCounts = resolved.taskCounts,
                    population = config.batch.population,
                    maxIter = config.batch.maxIter,
                    randomSeed = config.randomSeed,
                    resolvedAlgorithms = resolved.algorithms,
                    runs = config.batch.runs,
                    generatorType = config.batch.generatorType,
                    experimentDir = experimentDir,
                    outputContext = outputContext,
                    concurrency = concurrency
                )
                runner.runExperiment()
            }
            "realtime" -> {
                Logger.info("开始实时调度算法对比实验...")
                val runner = RealtimeComparisonRunner(
                    cloudletCount = config.realtime.cloudletCount,
                    simulationDuration = config.realtime.simulationDuration,
                    arrivalRate = config.realtime.arrivalRate,
                    population = config.optimizer.population,
                    maxIter = config.optimizer.maxIter,
                    randomSeed = config.randomSeed,
                    resolvedAlgorithms = resolved.algorithms,
                    runs = config.realtime.runs,
                    generatorType = config.realtime.generatorType,
                    googleTraceConfig = config.realtime.googleTraceConfig,
                    objectiveWeights = config.realtime.objectiveWeights,
                    arrival = config.realtime.arrival,
                    scheduling = config.realtime.scheduling,
                    experimentDir = experimentDir,
                    outputContext = outputContext,
                    concurrency = concurrency
                )
                runner.runComparison()
                Logger.info("实时调度实验完成！")
            }
            "realtime-multi" -> {
                Logger.info("开始实时调度模式批量任务数实验...")
                val runner = RealtimeCloudletCountRunner(
                    cloudletCounts = resolved.taskCounts,
                    simulationDuration = config.realtime.simulationDuration,
                    arrivalRate = config.realtime.arrivalRate,
                    population = config.optimizer.population,
                    maxIter = config.optimizer.maxIter,
                    randomSeed = config.randomSeed,
                    resolvedAlgorithms = resolved.algorithms,
                    runs = config.realtime.runs,
                    generatorType = config.realtime.generatorType,
                    arrival = config.realtime.arrival,
                    scheduling = config.realtime.scheduling,
                    experimentDir = experimentDir,
                    outputContext = outputContext,
                    concurrency = concurrency
                )
                runner.runBatchExperiment()
            }
            else -> throw IllegalArgumentException("未知的运行模式: ${resolved.mode}")
        }
    }

    private fun configureLogging(systemConfig: config.SystemConfig) {
        val loggerFactory = LoggerFactory.getILoggerFactory()
        if (loggerFactory is LoggerContext) {
            val rootLogger = loggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
            rootLogger.level = Level.toLevel(systemConfig.logging.level.uppercase(), Level.INFO)
            if (!systemConfig.logging.console) {
                rootLogger.detachAppender("CONSOLE")
                loggerFactory.getLogger("RESULTS").detachAppender("CONSOLE")
            }
            if (!systemConfig.logging.file) {
                rootLogger.detachAppender("FILE")
            }
        }
        Logger.info(
            "系统配置加载完成 - 输出目录: {}, 日志级别: {}, 控制台日志: {}",
            systemConfig.output.resultsDir,
            systemConfig.logging.level,
            systemConfig.logging.console
        )
    }

    private fun validateEnvironment() {
        try {
            val javaVersion = System.getProperty("java.version")
            Logger.debug("检测Java版本: {}", javaVersion)

            val versionParts = javaVersion.split(".").mapNotNull { it.toIntOrNull() }
            if (versionParts.isNotEmpty() && versionParts[0] < 17) {
                Logger.warn("建议使用Java 17或更高版本，当前版本: {}", javaVersion)
            }

            val maxMemoryMB = Runtime.getRuntime().maxMemory() / 1024 / 1024
            Logger.debug("最大可用内存: {} MB", maxMemoryMB)
            if (maxMemoryMB < 512) {
                Logger.warn("可用内存较少 ({} MB)，可能影响大规模实验性能", maxMemoryMB)
            }

            Logger.debug("文件编码: {}", System.getProperty("file.encoding", "unknown"))
            Logger.debug("标准输出编码: {}", System.getProperty("sun.stdout.encoding", "unknown"))
            Logger.debug("标准错误编码: {}", System.getProperty("sun.stderr.encoding", "unknown"))

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
}
