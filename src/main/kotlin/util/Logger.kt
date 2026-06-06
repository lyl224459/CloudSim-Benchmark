package util

import mu.KotlinLogging
import java.util.IllegalFormatException

/**
 * 日志工具类
 * 提供统一的日志接口
 */
object Logger {
    /**
     * 主日志记录器
     */
    private val mainLogger = KotlinLogging.logger {}

    /**
     * 结果日志记录器（用于记录实验结果，不包含时间戳等）
     */
    private val resultLogger = KotlinLogging.logger("RESULTS")

    /**
     * 信息日志
     */
    fun info(message: String) {
        mainLogger.info { message }
    }

    /**
     * 信息日志（带参数）
     */
    fun info(
        message: String,
        vararg args: Any?,
    ) {
        mainLogger.info { LogMessageFormatter.format(message, *args) }
    }

    /**
     * 调试日志
     */
    fun debug(message: String) {
        mainLogger.debug { message }
    }

    /**
     * 调试日志（带参数）
     */
    fun debug(
        message: String,
        vararg args: Any?,
    ) {
        mainLogger.debug { LogMessageFormatter.format(message, *args) }
    }

    /**
     * 警告日志
     */
    fun warn(message: String) {
        mainLogger.warn { message }
    }

    /**
     * 警告日志（带参数）
     */
    fun warn(
        message: String,
        vararg args: Any?,
    ) {
        mainLogger.warn { LogMessageFormatter.format(message, *args) }
    }

    /**
     * 错误日志
     */
    fun error(
        message: String,
        throwable: Throwable? = null,
    ) {
        if (throwable != null) {
            mainLogger.error(throwable) { message }
        } else {
            mainLogger.error { message }
        }
    }

    /**
     * 错误日志（带参数）
     */
    fun error(
        message: String,
        throwable: Throwable? = null,
        vararg args: Any?,
    ) {
        val formattedMessage = LogMessageFormatter.format(message, *args)
        if (throwable != null) {
            mainLogger.error(throwable) { formattedMessage }
        } else {
            mainLogger.error { formattedMessage }
        }
    }

    /**
     * 结果日志（用于记录实验结果，格式简洁）
     */
    fun result(message: String) {
        resultLogger.info { message }
    }

    /**
     * 结果日志（带参数）
     */
    fun result(
        message: String,
        vararg args: Any?,
    ) {
        resultLogger.info { LogMessageFormatter.format(message, *args) }
    }
}

private object LogMessageFormatter {
    fun format(
        message: String,
        vararg args: Any?,
    ): String =
        try {
            if (args.isEmpty()) {
                message
            } else {
                java.lang.String.format(message.replace("%", "%%").replace("{}", "%s"), *args)
            }
        } catch (exception: IllegalFormatException) {
            "Log message format failed: ${exception.message} | Pattern: $message | Args: ${args.contentToString()}"
        }
}
