package datacenter

import util.CsvRowWriter
import util.CsvTableSchema
import util.ExperimentOutputContext
import util.Logger
import java.text.DecimalFormat
import java.util.Locale

private const val RESULT_TABLE_WIDTH = 120
private const val RESULT_HEADER_FORMAT = "%-15s %-16s %-8s %-8s %-12s %-12s %-12s %-12s %-8s %-8s %-8s"
private const val RESULT_ROW_FORMAT = "%-15s %-16s %-8d %-8d %-12s %-12s %-12s %-12s %-8s %-8s %-8s"

internal class RealtimeResultExporter(
    private val outputContext: ExperimentOutputContext,
    private val dft: DecimalFormat = DecimalFormat("###.##"),
) {
    suspend fun saveTrialOutcome(outcome: RealtimeRunOutcome) {
        outputContext.saveAlgorithmTrialRow(
            algorithmName = outcome.algorithmName,
            headers = realtimeTrialCsvHeaders,
            row = outcome.toTrialCsvRow(),
        )
    }

    fun printComparisonResults(summaries: List<RealtimeRunSummary>) {
        Logger.result("\n${"=".repeat(RESULT_TABLE_WIDTH)}")
        Logger.result("实时调度算法对比结果汇总")
        Logger.result("${"=".repeat(RESULT_TABLE_WIDTH)}")
        Logger.result(resultHeader())
        Logger.result("-".repeat(RESULT_TABLE_WIDTH))

        summaries.forEach { summary ->
            Logger.result(summary.resultRow())
        }
        Logger.result("${"=".repeat(RESULT_TABLE_WIDTH)}")
    }

    fun exportRealtimeToCSV(summaries: List<RealtimeRunSummary>) {
        if (!outputContext.csvEnabled) {
            Logger.info("CSV 输出已禁用，跳过实时结果导出")
            return
        }

        val csvFile = outputContext.generateResultFileName("realtime_comparison")
        CsvRowWriter(outputContext.csvDelimiter).writeTable(
            csvFile,
            CsvTableSchema(realtimeSummaryCsvHeaders),
            summaries.map { it.toCsvRow() },
        )
        Logger.info("实时调度结果已导出到: {}", csvFile.absolutePath)
    }

    fun saveSummaryResults(summaries: List<RealtimeRunSummary>) {
        outputContext.saveSummaryRows(
            rows = summaries.map { it.toCsvRow() },
            headers = realtimeSummaryCsvHeaders,
        )
    }

    private fun Double?.formatOrBlank(): String = this?.let { dft.format(it) }.orEmpty()

    private fun resultHeader(): String =
        String.format(
            Locale.ROOT,
            RESULT_HEADER_FORMAT,
            "算法",
            "状态",
            "成功",
            "失败",
            "Makespan",
            "LB",
            "Cost",
            "AvgWait",
            "Reject",
            "Timeout",
            "Failed",
        )

    private fun RealtimeRunSummary.resultRow(): String {
        val result = average
        return String.format(
            Locale.ROOT,
            RESULT_ROW_FORMAT,
            algorithmName,
            status,
            successfulRuns.size,
            failedRuns.size,
            result?.makespan.formatOrBlank(),
            result?.loadBalance.formatOrBlank(),
            result?.cost.formatOrBlank(),
            result?.averageWaitingTime.formatOrBlank(),
            result?.rejectedCount?.toString().orEmpty(),
            result?.timeoutCount?.toString().orEmpty(),
            result?.failedCount?.toString().orEmpty(),
        )
    }
}
