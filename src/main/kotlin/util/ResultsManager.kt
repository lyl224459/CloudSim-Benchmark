package util

import config.ExperimentConfig
import config.SystemConfig
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
    private var csvEnabled = true
    private var csvDelimiter = ","

    /**
     * 配置结果目录
     */
    fun configure(config: SystemConfig) {
        resultsBaseDir = config.output.resultsDir
        csvEnabled = config.output.csv.enabled
        csvDelimiter = config.output.csv.delimiter
    }

    /**
     * @deprecated Use configure(SystemConfig) so output.resultsDir is honored.
     */
    @Deprecated("Use configure(SystemConfig)")
    fun configure(config: ExperimentConfig) {
        resultsBaseDir = DEFAULT_RESULTS_DIR
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

    fun isCsvEnabled(): Boolean = csvEnabled

    fun getCsvDelimiter(): String = csvDelimiter

    fun csvLine(values: Iterable<Any?>): String {
        return values.joinToString(csvDelimiter) { value ->
            escapeCsv(value?.toString().orEmpty())
        }
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(csvDelimiter) || value.contains('"') || value.contains('\n') || value.contains('\r')
        if (!needsQuotes) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
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
        return File(experimentDir, if (fileName.endsWith(suffix)) fileName else fileName + suffix)
    }

    /**
     * 保存实验信息到文本文件
     */
    fun saveExperimentInfo(experimentDir: File, info: Map<String, Any>) {
        val infoFile = File(experimentDir, "experiment_info.txt")
        infoFile.bufferedWriter().use { writer ->
            writer.write("=== CloudSim-Benchmark 实验信息 ===\n")
            writer.write("生成时间: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}\n\n")
            info.forEach { (key, value) ->
                writer.write("$key: $value\n")
            }
        }
    }

    /**
     * 保存单次试验结果到对应算法的 CSV 文件
     */
    fun saveAlgorithmTrialResult(
        experimentDir: File,
        algorithmName: String,
        trial: Int,
        metrics: Map<String, Double>
    ) {
        if (!csvEnabled) {
            return
        }
        val fileName = algorithmName.replace(" ", "_") + ".csv"
        val file = File(experimentDir, fileName)
        val isNew = !file.exists()

        java.io.FileOutputStream(file, true).bufferedWriter().use { writer ->
            if (isNew) {
                writer.write(csvLine(listOf("Trial") + metrics.keys.toList()) + "\n")
            }
            writer.write(csvLine(listOf(trial) + metrics.values.map { String.format("%.6f", it) }) + "\n")
        }
    }

    /**
     * 保存所有算法的平均汇总结果
     */
    fun saveSummaryResults(
        experimentDir: File,
        summaryData: List<Map<String, Any>>,
        headers: List<String>
    ) {
        if (!csvEnabled) {
            return
        }
        val summaryFile = File(experimentDir, "summary_avg.csv")
        summaryFile.bufferedWriter().use { writer ->
            writer.write(csvLine(headers) + "\n")
            for (row in summaryData) {
                writer.write(csvLine(headers.map { header ->
                    val value = row[header]
                    if (value is Double) String.format("%.6f", value) else value.toString()
                }) + "\n")
            }
        }
    }

    fun saveResolvedConfig(experimentDir: File, content: String) {
        val file = File(experimentDir, "resolved_config.json")
        file.writeText(content)
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

