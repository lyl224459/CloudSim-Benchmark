package util

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 结果管理器
 * 负责管理实验结果文件的保存，每次运行生成唯一的文件名
 */
object ResultsManager {
    private const val RESULTS_DIR = "results"
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    
    /**
     * 获取结果目录
     */
    fun getResultsDirectory(): File {
        val dir = File(RESULTS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * 生成唯一的文件名（基于时间戳）
     * 
     * @param prefix 文件名前缀（如 "batch" 或 "realtime"）
     * @param suffix 文件名后缀（如 ".csv"）
     * @return 完整的文件路径
     */
    fun generateFileName(prefix: String, suffix: String = ".csv"): File {
        val timestamp = LocalDateTime.now().format(dateTimeFormatter)
        val fileName = "${prefix}_${timestamp}${suffix}"
        return File(getResultsDirectory(), fileName)
    }
    
    /**
     * 生成批处理模式结果文件名
     */
    fun generateBatchResultFileName(): File {
        return generateFileName("batch_comparison")
    }
    
    /**
     * 生成实时调度模式结果文件名
     */
    fun generateRealtimeResultFileName(): File {
        return generateFileName("realtime_comparison")
    }
}

