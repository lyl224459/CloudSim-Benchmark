package cli

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import datacenter.BatchCloudletCountRunner
import datacenter.BatchExperimentRequest
import datacenter.ComparisonRunner
import datacenter.ExperimentExecutionRequest
import datacenter.RealtimeCloudletCountRunner
import datacenter.RealtimeComparisonRunner
import datacenter.RealtimeExperimentRequest
import org.slf4j.LoggerFactory
import util.ExperimentConcurrency
import util.ExperimentOutputContext
import util.Logger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val MIN_RECOMMENDED_JAVA_MAJOR = 17
private const val BYTES_PER_MEBIBYTE = 1024
private const val LOW_MEMORY_WARNING_MIB = 512

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
                val resolved =
                    RunResolver.resolve(
                        CliParser.RunCommand(configFile = command.configFile, profile = command.profile),
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
        val outputContext =
            ExperimentOutputContext.createExperiment(
                resolved.systemConfig,
                resolved.mode,
                experimentName,
            )
        val experimentDir = checkNotNull(outputContext.experimentDir) { "无法创建实验输出目录" }

        Logger.info("实验目录: {}", experimentDir.absolutePath)
        outputContext.saveResolvedConfig(DryRunPrinter.resolvedJson(resolved, experimentDir, timestamp))

        runResolvedExperiment(resolved, outputContext)
        Logger.info("CloudSim-Benchmark 执行完成")
    }

    private suspend fun runResolvedExperiment(
        resolved: ResolvedExperimentConfig,
        outputContext: ExperimentOutputContext,
    ) {
        val config = resolved.experimentConfig
        checkNotNull(outputContext.experimentDir) { "运行实验需要有效的输出目录" }
        val concurrency =
            ExperimentConcurrency(
                resolved.execution.useCoroutines,
                resolved.execution.maxConcurrency,
            )
        val executionRequest =
            ExperimentExecutionRequest(
                randomSeed = config.randomSeed,
                resolvedAlgorithms = resolved.algorithms,
                outputContext = outputContext,
                concurrency = concurrency,
            )

        when (resolved.mode) {
            "batch" -> {
                Logger.info("开始批处理调度算法对比实验...")
                val runner =
                    ComparisonRunner(
                        BatchExperimentRequest(
                            batch = config.batch,
                            execution = executionRequest,
                        ),
                    )
                runner.runComparison()
                Logger.info("批处理实验完成！")
            }
            "batch-multi" -> {
                Logger.info("开始批处理模式批量任务数实验...")
                val runner =
                    BatchCloudletCountRunner(
                        BatchExperimentRequest(
                            batch = config.batch.copy(cloudletCounts = resolved.taskCounts),
                            execution = executionRequest,
                        ),
                    )
                runner.runExperiment()
            }
            "realtime" -> {
                Logger.info("开始实时调度算法对比实验...")
                val runner =
                    RealtimeComparisonRunner(
                        RealtimeExperimentRequest(
                            realtime = config.realtime,
                            optimizer = config.optimizer,
                            execution = executionRequest,
                        ),
                    )
                runner.runComparison()
                Logger.info("实时调度实验完成！")
            }
            "realtime-multi" -> {
                Logger.info("开始实时调度模式批量任务数实验...")
                val runner =
                    RealtimeCloudletCountRunner(
                        RealtimeExperimentRequest(
                            realtime = config.realtime.copy(cloudletCounts = resolved.taskCounts),
                            optimizer = config.optimizer,
                            execution = executionRequest,
                        ),
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
            systemConfig.logging.console,
        )
    }

    private fun validateEnvironment() {
        try {
            val javaVersion = System.getProperty("java.version")
            Logger.debug("检测Java版本: {}", javaVersion)

            val versionParts = javaVersion.split(".").mapNotNull { it.toIntOrNull() }
            if (versionParts.isNotEmpty() && versionParts[0] < MIN_RECOMMENDED_JAVA_MAJOR) {
                Logger.warn("建议使用Java {}或更高版本，当前版本: {}", MIN_RECOMMENDED_JAVA_MAJOR, javaVersion)
            }

            val maxMemoryMB = Runtime.getRuntime().maxMemory() / BYTES_PER_MEBIBYTE / BYTES_PER_MEBIBYTE
            Logger.debug("最大可用内存: {} MB", maxMemoryMB)
            if (maxMemoryMB < LOW_MEMORY_WARNING_MIB) {
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
        } catch (e: IllegalStateException) {
            failEnvironmentValidation(e)
        } catch (e: SecurityException) {
            failEnvironmentValidation(e)
        }
    }

    private fun failEnvironmentValidation(exception: RuntimeException): Nothing {
        Logger.error("环境验证失败: " + exception.message, exception)
        throw IllegalStateException("环境验证失败: ${exception.message}", exception)
    }
}
