package util

import config.SystemConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

@Suppress("TooManyFunctions") // Output context intentionally centralizes file, CSV and lock helpers.
data class ExperimentOutputContext(
    val experimentDir: File?,
    val baseResultsDir: File = File("runs"),
    val csvEnabled: Boolean = true,
    val csvDelimiter: String = ",",
    private val writeLocks: ConcurrentHashMap<String, Mutex> = ConcurrentHashMap(),
) {
    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    fun csvLine(values: Iterable<Any?>): String = CsvRowWriter(csvDelimiter).line(values)

    fun generateResultFileName(
        fileName: String,
        suffix: String = ".csv",
    ): File {
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
        metrics: Map<String, Double>,
    ) {
        saveAlgorithmTrialRow(
            algorithmName = algorithmName,
            headers = listOf("Trial") + metrics.keys.toList(),
            row = listOf(trial) + metrics.values,
        )
    }

    suspend fun saveAlgorithmTrialRow(
        algorithmName: String,
        headers: List<String>,
        row: List<Any?>,
    ) {
        val dir = experimentDir ?: return
        if (!csvEnabled) return

        val fileName = algorithmName.replace(" ", "_") + ".csv"
        val file = File(dir, fileName)
        val lock = writeLocks.computeIfAbsent(file.absolutePath) { Mutex() }

        lock.withLock {
            val isNew = !file.exists()
            val schema = CsvTableSchema(headers)
            val csvWriter = CsvRowWriter(csvDelimiter)
            java.io.FileOutputStream(file, true).bufferedWriter().use { writer ->
                if (isNew) {
                    csvWriter.writeHeader(writer, schema)
                }
                csvWriter.writeRow(writer, schema, row)
            }
        }
    }

    suspend fun appendCsvRows(
        fileName: String,
        headers: List<String>,
        rows: List<List<Any?>>,
    ) {
        val dir = experimentDir ?: return
        if (!csvEnabled || rows.isEmpty()) return

        val file = File(dir, fileName)
        val lock = writeLocks.computeIfAbsent(file.absolutePath) { Mutex() }

        lock.withLock {
            val isNew = !file.exists()
            val schema = CsvTableSchema(headers)
            val csvWriter = CsvRowWriter(csvDelimiter)
            java.io.FileOutputStream(file, true).bufferedWriter().use { writer ->
                if (isNew) {
                    csvWriter.writeHeader(writer, schema)
                }
                rows.forEach { row -> csvWriter.writeRow(writer, schema, row) }
            }
        }
    }

    fun saveSummaryRows(
        rows: List<List<Any?>>,
        headers: List<String>,
    ) {
        val dir = experimentDir ?: return
        if (!csvEnabled) return

        val summaryFile = File(dir, "summary_avg.csv")
        val schema = CsvTableSchema(headers)
        CsvRowWriter(csvDelimiter).writeTable(summaryFile, schema, rows)
    }

    fun saveSummaryResults(
        summaryData: List<Map<String, Any?>>,
        headers: List<String>,
    ) {
        val schema = CsvTableSchema(headers)
        saveSummaryRows(summaryData.map(schema::rowFrom), headers)
    }

    private fun ensureBaseDirectory(): File {
        if (!baseResultsDir.exists()) {
            baseResultsDir.mkdirs()
        }
        return baseResultsDir
    }

    companion object {
        private val experimentNameFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

        fun from(
            systemConfig: SystemConfig,
            experimentDir: File?,
        ): ExperimentOutputContext =
            ExperimentOutputContext(
                experimentDir = experimentDir,
                baseResultsDir = File(systemConfig.output.resultsDir),
                csvEnabled = systemConfig.output.csv.enabled,
                csvDelimiter = systemConfig.output.csv.delimiter,
            )

        fun createExperiment(
            systemConfig: SystemConfig,
            mode: String,
            experimentName: String? = null,
        ): ExperimentOutputContext {
            val baseResultsDir = File(systemConfig.output.resultsDir)
            val experimentDir = createExperimentDirectory(baseResultsDir, mode, experimentName)
            return from(systemConfig, experimentDir)
        }

        fun createExperimentDirectory(
            baseResultsDir: File,
            mode: String,
            experimentName: String? = null,
        ): File = createExperimentDirectory(baseResultsDir, mode, experimentName, LocalDateTime.now())

        internal fun createExperimentDirectory(
            baseResultsDir: File,
            mode: String,
            experimentName: String?,
            now: LocalDateTime,
        ): File {
            val modeDir = File(baseResultsDir, mode.lowercase()).also(File::mkdirs)
            val resolvedName =
                experimentName
                    ?: "exp${findNextExperimentNumber(modeDir)}_${now.format(experimentNameFormatter)}"
            return File(modeDir, resolvedName).also(File::mkdirs)
        }

        private fun findNextExperimentNumber(modeDir: File): Int {
            val existingDirs =
                modeDir.listFiles { file ->
                    file.isDirectory && file.name.startsWith("exp")
                } ?: emptyArray()

            return existingDirs
                .mapNotNull {
                    it.name
                        .substringAfter("exp")
                        .substringBefore("_")
                        .toIntOrNull()
                }.maxOrNull()
                ?.plus(1)
                ?: 1
        }
    }
}
