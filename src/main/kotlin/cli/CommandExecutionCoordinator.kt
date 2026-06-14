package cli

import datacenter.BatchCloudletCountRunner
import datacenter.BatchExperimentRequest
import datacenter.ComparisonRunner
import datacenter.ExperimentExecutionRequest
import datacenter.RealtimeCloudletCountRunner
import datacenter.RealtimeComparisonRunner
import datacenter.RealtimeExperimentRequest
import util.ExperimentConcurrency
import util.ExperimentOutputContext
import util.Logger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal fun interface ResolvedExperimentLauncher {
    suspend fun launch(
        resolved: ResolvedExperimentConfig,
        outputContext: ExperimentOutputContext,
    )
}

internal data class CommandExecutionServices(
    val resolve: (CliParser.RunCommand) -> ResolvedExperimentConfig,
    val validateEnvironment: () -> Unit,
    val timestamp: () -> String,
    val createOutputContext: (ResolvedExperimentConfig, String) -> ExperimentOutputContext,
    val launcher: ResolvedExperimentLauncher,
) {
    companion object {
        private val timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

        fun production(): CommandExecutionServices =
            CommandExecutionServices(
                resolve = RunResolver::resolve,
                validateEnvironment = CommandExecutor::validateEnvironment,
                timestamp = { LocalDateTime.now().format(timestampFormatter) },
                createOutputContext = { resolved, experimentName ->
                    ExperimentOutputContext.createExperiment(
                        resolved.systemConfig,
                        resolved.mode,
                        experimentName,
                    )
                },
                launcher = ProductionExperimentLauncher(),
            )
    }
}

internal class CommandExecutionCoordinator(
    private val services: CommandExecutionServices,
) {
    suspend fun run(command: CliParser.RunCommand) {
        services.validateEnvironment()
        val resolved = services.resolve(command)
        CommandExecutor.configureLogging(resolved.systemConfig)

        if (command.dryRun) {
            DryRunPrinter.printDryRun(resolved)
            return
        }

        val timestamp = services.timestamp()
        val experimentName = RunResolver.renderExperimentName(resolved, timestamp)
        val outputContext = services.createOutputContext(resolved, experimentName)
        val experimentDir = checkNotNull(outputContext.experimentDir) { "无法创建实验输出目录" }

        Logger.info("实验目录: {}", experimentDir.absolutePath)
        outputContext.saveResolvedConfig(DryRunPrinter.resolvedJson(resolved, experimentDir, timestamp))
        services.launcher.launch(resolved, outputContext)
        Logger.info("CloudSim-Benchmark 执行完成")
    }
}

internal data class ProductionExperimentLaunchServices(
    val runBatch: suspend (BatchExperimentRequest) -> Unit,
    val runBatchMulti: suspend (BatchExperimentRequest) -> Unit,
    val runRealtime: suspend (RealtimeExperimentRequest) -> Unit,
    val runRealtimeMulti: suspend (RealtimeExperimentRequest) -> Unit,
) {
    companion object {
        fun production(): ProductionExperimentLaunchServices =
            ProductionExperimentLaunchServices(
                runBatch = { ComparisonRunner(it).runComparison() },
                runBatchMulti = { BatchCloudletCountRunner(it).runExperiment() },
                runRealtime = { RealtimeComparisonRunner(it).runComparison() },
                runRealtimeMulti = { RealtimeCloudletCountRunner(it).runBatchExperiment() },
            )
    }
}

internal class ProductionExperimentLauncher(
    private val services: ProductionExperimentLaunchServices = ProductionExperimentLaunchServices.production(),
) : ResolvedExperimentLauncher {
    override suspend fun launch(
        resolved: ResolvedExperimentConfig,
        outputContext: ExperimentOutputContext,
    ) {
        checkNotNull(outputContext.experimentDir) { "运行实验需要有效的输出目录" }
        val executionRequest = resolved.executionRequest(outputContext)
        when (resolved.mode) {
            "batch" -> runBatch(resolved, executionRequest)
            "batch-multi" -> runBatchMulti(resolved, executionRequest)
            "realtime" -> runRealtime(resolved, executionRequest)
            "realtime-multi" -> runRealtimeMulti(resolved, executionRequest)
            else -> throw IllegalArgumentException("未知的运行模式: ${resolved.mode}")
        }
    }

    private suspend fun runBatch(
        resolved: ResolvedExperimentConfig,
        execution: ExperimentExecutionRequest,
    ) {
        Logger.info("开始批处理调度算法对比实验...")
        services.runBatch(BatchExperimentRequest(resolved.experimentConfig.batch, execution))
        Logger.info("批处理实验完成！")
    }

    private suspend fun runBatchMulti(
        resolved: ResolvedExperimentConfig,
        execution: ExperimentExecutionRequest,
    ) {
        Logger.info("开始批处理模式批量任务数实验...")
        services.runBatchMulti(
            BatchExperimentRequest(
                resolved.experimentConfig.batch.copy(cloudletCounts = resolved.taskCounts),
                execution,
            ),
        )
    }

    private suspend fun runRealtime(
        resolved: ResolvedExperimentConfig,
        execution: ExperimentExecutionRequest,
    ) {
        Logger.info("开始实时调度算法对比实验...")
        services.runRealtime(
            RealtimeExperimentRequest(
                resolved.experimentConfig.realtime,
                resolved.experimentConfig.optimizer,
                execution,
            ),
        )
        Logger.info("实时调度实验完成！")
    }

    private suspend fun runRealtimeMulti(
        resolved: ResolvedExperimentConfig,
        execution: ExperimentExecutionRequest,
    ) {
        Logger.info("开始实时调度模式批量任务数实验...")
        services.runRealtimeMulti(
            RealtimeExperimentRequest(
                resolved.experimentConfig.realtime.copy(cloudletCounts = resolved.taskCounts),
                resolved.experimentConfig.optimizer,
                execution,
            ),
        )
    }

    private fun ResolvedExperimentConfig.executionRequest(outputContext: ExperimentOutputContext) =
        ExperimentExecutionRequest(
            randomSeed = experimentConfig.randomSeed,
            resolvedAlgorithms = algorithms,
            outputContext = outputContext,
            concurrency = ExperimentConcurrency(execution.useCoroutines, execution.maxConcurrency),
        )
}
