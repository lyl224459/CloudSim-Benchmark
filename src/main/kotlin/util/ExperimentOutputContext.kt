package util

import config.SystemConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

data class ExperimentOutputContext(
    val experimentDir: File?,
    val baseResultsDir: File = File("runs"),
    val csvEnabled: Boolean = true,
    val csvDelimiter: String = ",",
    private val writeLocks: ConcurrentHashMap<String, Mutex> = ConcurrentHashMap()
) {
    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    fun csvLine(values: Iterable<Any?>): String {
        return values.joinToString(csvDelimiter) { value ->
            escapeCsv(value?.toString().orEmpty())
        }
    }

    fun generateResultFileName(fileName: String, suffix: String = ".csv"): File {
        val resolvedName = if (fileName.endsWith(suffix)) fileName else fileName + suffix
        return experimentDir?.let { File(it, resolvedName) }
            ?: File(ensureBaseDirectory(), "${fileName}_${LocalDateTime.now().format(dateTimeFormatter)}$suffix")
    }

    fun child(name: String): ExperimentOutputContext {
        val childDir = experimentDir?.let { File(it, name).also(File::mkdirs) }
        return copy(experimentDir = childDir)
    }

    fun saveExperimentInfo(info: Map<String, Any>) {
        val dir = experimentDir ?: return
        val infoFile = File(dir, "experiment_info.txt")
        infoFile.bufferedWriter().use { writer ->
            writer.write("=== CloudSim-Benchmark 实验信息 ===\n")
            writer.write("生成时间: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}\n\n")
            info.forEach { (key, value) ->
                writer.write("$key: $value\n")
            }
        }
    }

    fun saveResolvedConfig(content: String) {
        val dir = experimentDir ?: return
        File(dir, "resolved_config.json").writeText(content)
    }

    suspend fun saveAlgorithmTrialResult(
        algorithmName: String,
        trial: Int,
        metrics: Map<String, Double>
    ) {
        val dir = experimentDir ?: return
        if (!csvEnabled) return

        val fileName = algorithmName.replace(" ", "_") + ".csv"
        val file = File(dir, fileName)
        val lock = writeLocks.computeIfAbsent(file.absolutePath) { Mutex() }

        lock.withLock {
            val isNew = !file.exists()
            java.io.FileOutputStream(file, true).bufferedWriter().use { writer ->
                if (isNew) {
                    writer.write(csvLine(listOf("Trial") + metrics.keys.toList()) + "\n")
                }
                writer.write(csvLine(listOf(trial) + metrics.values.map { String.format("%.6f", it) }) + "\n")
            }
        }
    }

    fun saveSummaryResults(
        summaryData: List<Map<String, Any>>,
        headers: List<String>
    ) {
        val dir = experimentDir ?: return
        if (!csvEnabled) return

        val summaryFile = File(dir, "summary_avg.csv")
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

    private fun ensureBaseDirectory(): File {
        if (!baseResultsDir.exists()) {
            baseResultsDir.mkdirs()
        }
        return baseResultsDir
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(csvDelimiter) || value.contains('"') || value.contains('\n') || value.contains('\r')
        if (!needsQuotes) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    companion object {
        private val experimentNameFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

        fun from(systemConfig: SystemConfig, experimentDir: File?): ExperimentOutputContext =
            ExperimentOutputContext(
                experimentDir = experimentDir,
                baseResultsDir = File(systemConfig.output.resultsDir),
                csvEnabled = systemConfig.output.csv.enabled,
                csvDelimiter = systemConfig.output.csv.delimiter
            )

        fun createExperiment(
            systemConfig: SystemConfig,
            mode: String,
            experimentName: String? = null
        ): ExperimentOutputContext {
            val baseResultsDir = File(systemConfig.output.resultsDir)
            val experimentDir = createExperimentDirectory(baseResultsDir, mode, experimentName)
            return from(systemConfig, experimentDir)
        }

        fun createExperimentDirectory(
            baseResultsDir: File,
            mode: String,
            experimentName: String? = null
        ): File {
            val modeDir = File(baseResultsDir, mode.lowercase()).also(File::mkdirs)
            val resolvedName = experimentName
                ?: "exp${findNextExperimentNumber(modeDir)}_${LocalDateTime.now().format(experimentNameFormatter)}"
            return File(modeDir, resolvedName).also(File::mkdirs)
        }

        private fun findNextExperimentNumber(modeDir: File): Int {
            val existingDirs = modeDir.listFiles { file ->
                file.isDirectory && file.name.startsWith("exp")
            } ?: emptyArray()

            return existingDirs
                .mapNotNull { it.name.substringAfter("exp").substringBefore("_").toIntOrNull() }
                .maxOrNull()
                ?.plus(1)
                ?: 1
        }
    }
}
