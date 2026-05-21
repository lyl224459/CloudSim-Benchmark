package util

import config.ExperimentConfig
import config.SystemConfig
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Compatibility facade for older call sites. New execution code should use
 * [ExperimentOutputContext] directly.
 */
@Deprecated("Use ExperimentOutputContext")
object ResultsManager {
    private const val DEFAULT_RESULTS_DIR = "runs"
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    @Deprecated("Use ExperimentOutputContext.from(SystemConfig, experimentDir)")
    fun configure(config: SystemConfig) {
        // Stateless compatibility hook.
    }

    @Deprecated("Use ExperimentOutputContext.from(SystemConfig, experimentDir)")
    fun configure(config: ExperimentConfig) {
        // Stateless compatibility hook.
    }

    fun getBaseResultsDirectory(): File {
        return File(DEFAULT_RESULTS_DIR).also(File::mkdirs)
    }

    fun getModeResultsDirectory(mode: String): File {
        return File(getBaseResultsDirectory(), mode.lowercase()).also(File::mkdirs)
    }

    fun createExperimentDirectory(mode: String, experimentName: String? = null): File {
        return ExperimentOutputContext.createExperimentDirectory(
            baseResultsDir = getBaseResultsDirectory(),
            mode = mode,
            experimentName = experimentName
        )
    }

    fun getResultsDirectory(): File {
        return getBaseResultsDirectory()
    }

    fun isCsvEnabled(): Boolean = true

    fun getCsvDelimiter(): String = ","

    fun csvLine(values: Iterable<Any?>): String {
        return ExperimentOutputContext(null).csvLine(values)
    }

    fun generateFileName(prefix: String, suffix: String = ".csv"): File {
        val timestamp = LocalDateTime.now().format(dateTimeFormatter)
        return File(getResultsDirectory(), "${prefix}_${timestamp}${suffix}")
    }

    fun generateResultFileName(experimentDir: File, fileName: String, suffix: String = ".csv"): File {
        return ExperimentOutputContext(experimentDir).generateResultFileName(fileName, suffix)
    }

    fun saveExperimentInfo(experimentDir: File, info: Map<String, Any>) {
        ExperimentOutputContext(experimentDir).saveExperimentInfo(info)
    }

    fun saveAlgorithmTrialResult(
        experimentDir: File,
        algorithmName: String,
        trial: Int,
        metrics: Map<String, Double>
    ) {
        val fileName = algorithmName.replace(" ", "_") + ".csv"
        val file = File(experimentDir, fileName)
        val isNew = !file.exists()
        java.io.FileOutputStream(file, true).bufferedWriter().use { writer ->
            val csv = ExperimentOutputContext(experimentDir)
            if (isNew) {
                writer.write(csv.csvLine(listOf("Trial") + metrics.keys.toList()) + "\n")
            }
            writer.write(csv.csvLine(listOf(trial) + metrics.values.map { String.format("%.6f", it) }) + "\n")
        }
    }

    fun saveSummaryResults(
        experimentDir: File,
        summaryData: List<Map<String, Any>>,
        headers: List<String>
    ) {
        ExperimentOutputContext(experimentDir).saveSummaryResults(summaryData, headers)
    }

    fun saveResolvedConfig(experimentDir: File, content: String) {
        ExperimentOutputContext(experimentDir).saveResolvedConfig(content)
    }

    fun generateBatchResultFileName(experimentDir: File? = null): File {
        return experimentDir?.let { generateResultFileName(it, "batch_comparison") }
            ?: generateFileName("batch_comparison")
    }

    fun generateRealtimeResultFileName(experimentDir: File? = null): File {
        return experimentDir?.let { generateResultFileName(it, "realtime_comparison") }
            ?: generateFileName("realtime_comparison")
    }

    fun generateBatchCloudletCountResultFileName(): File {
        return generateFileName("batch_cloudlet_count_comparison")
    }

    fun generateRealtimeCloudletCountResultFileName(): File {
        return generateFileName("realtime_cloudlet_count_comparison")
    }
}
