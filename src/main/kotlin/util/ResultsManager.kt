package util

import config.ExperimentConfig
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 结果管理器
 * 负责管理实验结果文件的保存，采用YOLO风格的目录结构
 */
object ResultsManager {
    private const val DEFAULT_RESULTS_DIR = "runs"
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    private var resultsBaseDir = DEFAULT_RESULTS_DIR

    /**
     * 配置结果目录
     */
    fun configure(config: ExperimentConfig) {
        // 从配置中获取结果目录，如果没有则使用默认值
        resultsBaseDir = "runs" // 可以从config中读取
    }

    /**
     * 获取基础结果目录
     */
    fun getBaseResultsDirectory(): File {
        val dir = File(resultsBaseDir)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * 获取模式特定的结果目录（如 runs/batch/ 或 runs/realtime/）
     */
    fun getModeResultsDirectory(mode: String): File {
        val dir = File(getBaseResultsDirectory(), mode.lowercase())
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * 生成实验目录（YOLO风格：exp{number}_{timestamp}）
     * 例如：exp1_20240101_120000
     */
    fun createExperimentDirectory(mode: String, experimentName: String? = null): File {
        val modeDir = getModeResultsDirectory(mode)
        val timestamp = LocalDateTime.now().format(dateTimeFormatter)

        // 如果没有指定实验名称，自动生成
        val expName = experimentName ?: "exp${findNextExperimentNumber(modeDir)}_${timestamp}"

        val expDir = File(modeDir, expName)
        if (!expDir.exists()) {
            expDir.mkdirs()
        }

        return expDir
    }

    /**
     * 查找下一个实验编号
     */
    private fun findNextExperimentNumber(modeDir: File): Int {
        val existingDirs = modeDir.listFiles { file ->
            file.isDirectory && file.name.startsWith("exp")
        } ?: emptyArray()

        var maxNumber = 0
        for (dir in existingDirs) {
            val number = dir.name.substringAfter("exp").substringBefore("_").toIntOrNull()
            if (number != null && number > maxNumber) {
                maxNumber = number
            }
        }

        return maxNumber + 1
    }

    /**
     * 获取结果目录（兼容旧版本）
     */
    fun getResultsDirectory(): File {
        return getBaseResultsDirectory()
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
     * 在实验目录中生成结果文件名
     */
    fun generateResultFileName(experimentDir: File, fileName: String, suffix: String = ".csv"): File {
        return File(experimentDir, fileName + suffix)
    }

    /**
     * 生成批处理模式结果文件名
     */
    fun generateBatchResultFileName(experimentDir: File? = null): File {
        return if (experimentDir != null) {
            generateResultFileName(experimentDir, "batch_comparison")
        } else {
            generateFileName("batch_comparison")
        }
    }

    /**
     * 生成实时调度模式结果文件名
     */
    fun generateRealtimeResultFileName(experimentDir: File? = null): File {
        return if (experimentDir != null) {
            generateResultFileName(experimentDir, "realtime_comparison")
        } else {
            generateFileName("realtime_comparison")
        }
    }
    
    /**
     * 生成批量任务数实验结果文件名（批处理模式）
     */
    fun generateBatchCloudletCountResultFileName(): File {
        return generateFileName("batch_cloudlet_count_comparison")
    }
    
    /**
     * 生成批量任务数实验结果文件名（实时调度模式）
     */
    fun generateRealtimeCloudletCountResultFileName(): File {
        return generateFileName("realtime_cloudlet_count_comparison")
    }
}

