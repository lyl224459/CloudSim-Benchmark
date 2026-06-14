import cli.CliParser
import cli.CommandExecutor
import cli.ResolvedExperimentConfig
import config.BatchAlgorithmType
import config.RealtimeAlgorithmType
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import util.Logger
import kotlin.system.exitProcess
import cli.parseBatchAlgorithms as registryParseBatchAlgorithms
import cli.parseRealtimeAlgorithms as registryParseRealtimeAlgorithms
import cli.resolveRun as resolveRunCommand

typealias CommandLineParser = CliParser

typealias ResolvedRun = ResolvedExperimentConfig

private val mainLogger = KotlinLogging.logger("Main")

@Suppress("TooGenericExceptionCaught") // CLI boundary keeps a stable exit code for unexpected failures.
fun main(args: Array<String>) =
    runBlocking {
        try {
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                Logger.error("未捕获的异常在线程 " + thread.name + " 中发生", throwable)
                exitProcess(1)
            }

            CommandExecutor.execute(CliParser(args).parse())
        } catch (e: config.ConfigValidationException) {
            Logger.error("配置验证失败: " + e.message)
            exitProcess(1)
        } catch (e: IllegalArgumentException) {
            Logger.error("参数错误: " + e.message)
            if (mainLogger.isDebugEnabled) {
                e.printStackTrace()
            }
            exitProcess(1)
        } catch (e: IllegalStateException) {
            Logger.error("环境错误: " + e.message, e)
            if (mainLogger.isDebugEnabled) {
                e.printStackTrace()
            }
            exitProcess(1)
        } catch (e: OutOfMemoryError) {
            Logger.error("内存不足错误，请增加JVM内存参数 (-Xmx)")
            if (mainLogger.isDebugEnabled) {
                e.printStackTrace()
            }
            exitProcess(1)
        } catch (e: Exception) {
            Logger.error("程序执行时发生未预期的错误: " + e.message, e)
            exitProcess(1)
        }
    }

internal fun resolveRun(command: CliParser.RunCommand): ResolvedExperimentConfig = resolveRunCommand(command)

internal fun parseBatchAlgorithms(algorithmNames: List<String>): List<BatchAlgorithmType> {
    val algorithms = registryParseBatchAlgorithms(algorithmNames)
    return algorithms
}

internal fun parseRealtimeAlgorithms(algorithmNames: List<String>): List<RealtimeAlgorithmType> =
    registryParseRealtimeAlgorithms(algorithmNames)

internal fun applyRunOverrides(
    configs: config.ConfigurationManager.LoadedConfigs,
    command: CliParser.RunCommand,
    selectionAlgorithmConfigs: Map<String, config.AlgorithmConfig> = configs.experimentConfig.algorithmConfigs,
): config.ConfigurationManager.LoadedConfigs = cli.applyRunOverrides(configs, command, selectionAlgorithmConfigs)
