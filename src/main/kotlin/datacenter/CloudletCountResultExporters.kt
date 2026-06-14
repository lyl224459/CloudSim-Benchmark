package datacenter

import util.CsvRowWriter
import util.CsvTableSchema
import util.ExperimentOutputContext
import util.Logger
import util.StatisticalValue
import java.text.DecimalFormat
import java.util.Locale

internal fun interface BatchCloudletCountExportService {
    fun export(results: Map<Int, List<BatchRunSummary>>)
}

internal class BatchCloudletCountResultExporter(
    private val outputContext: ExperimentOutputContext,
) : BatchCloudletCountExportService {
    override fun export(results: Map<Int, List<BatchRunSummary>>) {
        if (outputContext.csvEnabled) {
            val rows =
                results.toSortedMap().flatMap { (count, summaries) ->
                    summaries.map { it.toCloudletCountCsvRow(count) }
                }
            CsvRowWriter(outputContext.csvDelimiter).writeTable(
                outputContext.generateResultFileName("batch_cloudlet_count_summary"),
                CsvTableSchema(batchCloudletCountSummaryCsvHeaders),
                rows,
            )
            outputContext.saveSummaryRows(rows, batchCloudletCountSummaryCsvHeaders)
        } else {
            Logger.info("CSV 输出已禁用，跳过批处理批量任务数结果导出")
        }
        CloudletCountSummaryTable.print(
            title = "批量任务数实验汇总",
            results = results.mapValues { (_, summaries) -> summaries.associate { it.algorithmName to it.statistics } },
            metrics =
                listOf(
                    "Makespan" to AlgorithmStatistics::makespan,
                    "LoadBalance" to AlgorithmStatistics::loadBalance,
                    "Cost" to AlgorithmStatistics::cost,
                    "Fitness" to AlgorithmStatistics::fitness,
                ),
        )
    }
}

internal fun interface RealtimeCloudletCountExportService {
    fun export(results: Map<Int, List<RealtimeRunSummary>>)
}

internal class RealtimeCloudletCountResultExporter(
    private val outputContext: ExperimentOutputContext,
) : RealtimeCloudletCountExportService {
    override fun export(results: Map<Int, List<RealtimeRunSummary>>) {
        if (outputContext.csvEnabled) {
            val rows =
                results.toSortedMap().flatMap { (count, summaries) ->
                    summaries.map { it.toCloudletCountCsvRow(count) }
                }
            CsvRowWriter(outputContext.csvDelimiter).writeTable(
                outputContext.generateResultFileName("realtime_cloudlet_count_summary"),
                CsvTableSchema(RealtimeMetricSchema.cloudletCountSummaryHeaders),
                rows,
            )
            outputContext.saveSummaryRows(rows, RealtimeMetricSchema.cloudletCountSummaryHeaders)
        } else {
            Logger.info("CSV 输出已禁用，跳过实时批量任务数结果导出")
        }
        CloudletCountSummaryTable.print(
            title = "实时调度批量任务数实验汇总",
            results = results.mapValues { (_, summaries) -> summaries.associate { it.algorithmName to it.statistics } },
            metrics =
                listOf(
                    "Makespan" to RealtimeAlgorithmStatistics::makespan,
                    "LoadBalance" to RealtimeAlgorithmStatistics::loadBalance,
                    "Cost" to RealtimeAlgorithmStatistics::cost,
                    "Fitness" to RealtimeAlgorithmStatistics::fitness,
                    "AvgWaitingTime" to RealtimeAlgorithmStatistics::averageWaitingTime,
                    "AvgResponseTime" to RealtimeAlgorithmStatistics::averageResponseTime,
                ),
        )
    }
}

private object CloudletCountSummaryTable {
    private const val TABLE_WIDTH = 100
    private const val COUNT_COLUMN_WIDTH = 12
    private const val ALGORITHM_COLUMN_WIDTH = 20
    private val decimalFormat = DecimalFormat("###.##")

    fun <T> print(
        title: String,
        results: Map<Int, Map<String, T?>>,
        metrics: List<Pair<String, (T) -> StatisticalValue>>,
    ) {
        val algorithms =
            results.values
                .flatMap(Map<String, T?>::keys)
                .distinct()
                .sorted()
        Logger.info("\n${"=".repeat(TABLE_WIDTH)}")
        Logger.info(title)
        Logger.info("${"=".repeat(TABLE_WIDTH)}")
        metrics.forEach { (name, value) -> printMetric(name, value, results, algorithms) }
        Logger.info("${"=".repeat(TABLE_WIDTH)}")
    }

    private fun <T> printMetric(
        name: String,
        value: (T) -> StatisticalValue,
        results: Map<Int, Map<String, T?>>,
        algorithms: List<String>,
    ) {
        Logger.info("\n{} (平均值):", name)
        Logger.info(cell("%-${COUNT_COLUMN_WIDTH}s", "任务数"))
        algorithms.forEach { Logger.info(cell("%-${ALGORITHM_COLUMN_WIDTH}s", it)) }
        Logger.info("")
        Logger.info("-".repeat(TABLE_WIDTH))
        results.toSortedMap().forEach { (count, byAlgorithm) ->
            Logger.info(cell("%-${COUNT_COLUMN_WIDTH}d", count))
            algorithms.forEach { algorithm ->
                val stat = byAlgorithm[algorithm]?.let(value)
                val text = stat?.let { "${decimalFormat.format(it.mean)} ± ${decimalFormat.format(it.stdDev)}" } ?: "-"
                Logger.info(cell("%-${ALGORITHM_COLUMN_WIDTH}s", text))
            }
            Logger.info("")
        }
    }

    private fun cell(
        format: String,
        value: Any,
    ): String = String.format(Locale.ROOT, format, value)
}
