package util

import mu.KotlinLogging

/**
 * 日志工具类
 * 提供统一的日志接口
 */
object Logger {
    /**
     * 主日志记录器
     */
    private val logger = KotlinLogging.logger {}
    
    /**
     * 结果日志记录器（用于记录实验结果，不包含时间戳等）
     */
    private val resultLogger = KotlinLogging.logger("RESULTS")
    
    /**
     * 信息日志
     */
    fun info(message: String) {
        logger.info { message }
    }
    
    /**
     * 信息日志（带参数）
     */
    fun info(message: String, vararg args: Any?) {
        logger.info { 
            if (args.isEmpty()) message else java.lang.String.format(message.replace("{}", "%s"), *args)
        }
    }
    
    /**
     * 调试日志
     */
    fun debug(message: String) {
        logger.debug { message }
    }
    
    /**
     * 调试日志（带参数）
     */
    fun debug(message: String, vararg args: Any?) {
        logger.debug { 
            if (args.isEmpty()) message else java.lang.String.format(message.replace("{}", "%s"), *args)
        }
    }
    
    /**
     * 警告日志
     */
    fun warn(message: String) {
        logger.warn { message }
    }
    
    /**
     * 警告日志（带参数）
     */
    fun warn(message: String, vararg args: Any?) {
        logger.warn { 
            if (args.isEmpty()) message else java.lang.String.format(message.replace("{}", "%s"), *args)
        }
    }
    
    /**
     * 错误日志
     */
    fun error(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            logger.error(throwable) { message }
        } else {
            logger.error { message }
        }
    }
    
    /**
     * 错误日志（带参数）
     */
    fun error(message: String, throwable: Throwable? = null, vararg args: Any?) {
        val formattedMessage = if (args.isEmpty()) message else java.lang.String.format(message.replace("{}", "%s"), *args)
        if (throwable != null) {
            logger.error(throwable) { formattedMessage }
        } else {
            logger.error { formattedMessage }
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
    fun result(message: String, vararg args: Any?) {
        resultLogger.info { 
            if (args.isEmpty()) message else java.lang.String.format(message.replace("{}", "%s"), *args)
        }
    }
    
    /**
     * 获取类日志记录器
     */
    fun getLogger(clazz: Class<*>): mu.KLogger {
        return KotlinLogging.logger(clazz.name)
    }
    
    /**
     * 获取名称日志记录器
     */
    fun getLogger(name: String): mu.KLogger {
        return KotlinLogging.logger(name)
    }
}

