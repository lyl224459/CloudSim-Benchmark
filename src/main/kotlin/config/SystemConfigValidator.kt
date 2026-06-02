package config

import util.Logger
import java.nio.file.InvalidPathException
import java.nio.file.Paths

internal object SystemConfigValidator {
    private const val MAX_CONCURRENCY_CPU_MULTIPLIER = 4
    private val validLogLevels = setOf("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF")
    private val heapSizeRegex = Regex("\\d+[kmg]", RegexOption.IGNORE_CASE)
    private val validGcAlgorithms = setOf("G1", "ZGC", "Shenandoah", "CMS", "Serial", "Parallel")

    fun validate(config: SystemConfig) {
        val context = SystemConfigValidationContext()
        validateOutputConfig(config.output, context)
        validateLoggingConfig(config.logging, context)
        validateExperimentConfig(config.experiment, context)
        validateJvmConfig(config.jvm, context)
        context.throwIfInvalid()
        Logger.debug("系统配置验证通过")
    }

    private fun validateOutputConfig(
        output: OutputConfig,
        context: SystemConfigValidationContext,
    ) {
        if (output.resultsDir.isBlank()) {
            context.error("output.resultsDir", output.resultsDir, "输出目录不能为空")
        }
        if (output.resultsDir.contains("..")) {
            context.error("output.resultsDir", output.resultsDir, "输出目录不能包含 '..' 以防止路径遍历")
        }
        try {
            Paths.get(output.resultsDir)
        } catch (exception: InvalidPathException) {
            context.error("output.resultsDir", output.resultsDir, "输出目录路径格式无效: ${exception.message}")
        }
    }

    private fun validateLoggingConfig(
        logging: LoggingConfig,
        context: SystemConfigValidationContext,
    ) {
        if (logging.level.uppercase() !in validLogLevels) {
            context.error("logging.level", logging.level, "日志级别必须是以下值之一: ${validLogLevels.joinToString(", ")}")
        }
    }

    private fun validateExperimentConfig(
        experiment: SystemExperimentConfig,
        context: SystemConfigValidationContext,
    ) {
        if (experiment.nameFormat.isBlank()) {
            context.error("experiment.nameFormat", experiment.nameFormat, "实验名称格式不能为空")
        }
        if (experiment.maxConcurrent <= 0) {
            context.error("experiment.maxConcurrent", experiment.maxConcurrent.toString(), "最大并发数必须大于0")
        }

        val cpuCores = Runtime.getRuntime().availableProcessors()
        val maxRecommendedConcurrency = cpuCores * MAX_CONCURRENCY_CPU_MULTIPLIER
        if (experiment.maxConcurrent > maxRecommendedConcurrency) {
            context.error(
                "experiment.maxConcurrent",
                experiment.maxConcurrent.toString(),
                "最大并发数(${experiment.maxConcurrent})远超CPU核心数($cpuCores)的4倍，可能导致性能问题",
            )
        }
    }

    private fun validateJvmConfig(
        jvm: JvmConfig,
        context: SystemConfigValidationContext,
    ) {
        if (!heapSizeRegex.matches(jvm.maxHeapSize)) {
            context.error(
                "jvm.maxHeapSize",
                jvm.maxHeapSize,
                "JVM最大堆大小格式无效，应为数字+单位(k/m/g)，如: 2g, 512m, 1024k",
            )
        }
        if (jvm.gcAlgorithm !in validGcAlgorithms) {
            context.error(
                "jvm.gcAlgorithm",
                jvm.gcAlgorithm,
                "JVM垃圾收集算法必须是以下值之一: ${validGcAlgorithms.joinToString(", ")}",
            )
        }
    }
}

private class SystemConfigValidationContext {
    private val errors = mutableListOf<ValidationError>()

    fun error(
        field: String,
        value: String,
        message: String,
    ) {
        errors.add(ValidationError(field, value, message))
    }

    fun throwIfInvalid() {
        if (errors.isNotEmpty()) {
            throw ConfigValidationException("系统配置验证失败，共发现 ${errors.size} 个错误", errors)
        }
    }
}
