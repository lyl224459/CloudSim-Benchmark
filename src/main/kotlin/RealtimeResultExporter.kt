package datacenter

import util.CsvRowWriter
import util.CsvTableSchema
import util.ExperimentOutputContext
import util.Logger
import java.text.DecimalFormat

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
        Logger.result("\n${"=".repeat(120)}")
        Logger.result("实时调度算法对比结果汇总")
        Logger.result("${"=".repeat(120)}")
        Logger.result(
            String.format(
                "%-15s %-16s %-8s %-8s %-12s %-12s %-12s %-12s %-8s %-8s %-8s",
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
            ),
        )
        Logger.result("-".repeat(120))

        summaries.forEach { summary ->
            val result = summary.average
            Logger.result(
                String.format(
                    "%-15s %-16s %-8d %-8d %-12s %-12s %-12s %-12s %-8s %-8s %-8s",
                    summary.algorithmName,
                    summary.status,
                    summary.successfulRuns.size,
                    summary.failedRuns.size,
                    result?.makespan.formatOrBlank(),
                    result?.loadBalance.formatOrBlank(),
                    result?.cost.formatOrBlank(),
                    result?.averageWaitingTime.formatOrBlank(),
                    result?.rejectedCount?.toString().orEmpty(),
                    result?.timeoutCount?.toString().orEmpty(),
                    result?.failedCount?.toString().orEmpty(),
                ),
            )
        }
        Logger.result("${"=".repeat(120)}")
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
}
