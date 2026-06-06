package datacenter

import util.CsvRowWriter
import util.CsvTableSchema
import util.ExperimentOutputContext
import util.Logger
import java.text.DecimalFormat
import java.util.Locale

internal class BatchResultExporter(
    private val outputContext: ExperimentOutputContext,
    private val runs: Int,
) {
    private val decimalFormat = DecimalFormat("###.##")

    fun printComparisonResults(summaries: List<BatchRunSummary>) {
        printHeader()
        summaries.forEach(::printRow)
        Logger.result("-".repeat(RESULT_SEPARATOR_WIDTH))
        printBestResults(summaries.mapNotNull { it.average })
        Logger.result("${"=".repeat(RESULT_SEPARATOR_WIDTH)}\n")
    }

    fun exportToCsv(summaries: List<BatchRunSummary>) {
        if (!outputContext.csvEnabled) {
            Logger.info("CSV 输出已禁用，跳过批处理结果导出")
            return
        }
        val csvFile = outputContext.generateResultFileName("batch_comparison")
        CsvRowWriter(outputContext.csvDelimiter).writeTable(
            csvFile,
            CsvTableSchema(batchSummaryCsvHeaders),
            summaries.map { it.toCsvRow() },
        )
        Logger.info("结果已导出到: {}", csvFile.absolutePath)
        if (runs > 1) Logger.info("注: 导出值为 {} 次运行的平均值与标准差", runs)
    }

    fun saveSummary(summaries: List<BatchRunSummary>) {
        outputContext.saveSummaryRows(summaries.map { it.toCsvRow() }, batchSummaryCsvHeaders)
    }

    suspend fun saveTrial(outcome: BatchRunOutcome) {
        outputContext.saveAlgorithmTrialRow(outcome.algorithmName, batchTrialCsvHeaders, outcome.toTrialCsvRow())
    }

    private fun printHeader() {
        Logger.result("\n${"=".repeat(RESULT_SEPARATOR_WIDTH)}")
        Logger.result("算法对比结果汇总")
        Logger.result("${"=".repeat(RESULT_SEPARATOR_WIDTH)}")
        Logger.result(
            format(
                "%-12s %-16s %-8s %-8s %-15s %-15s %-15s %-15s %-15s",
                "算法",
                "状态",
                "成功",
                "失败",
                "Makespan",
                "Load Balance",
                "Cost",
                "Total Time",
                "Fitness",
            ),
        )
        Logger.result("-".repeat(RESULT_SEPARATOR_WIDTH))
    }

    private fun printRow(summary: BatchRunSummary) {
        val result = summary.average
        Logger.result(
            format(
                "%-12s %-16s %-8d %-8d %-15s %-15s %-15s %-15s %-15s",
                summary.algorithmName,
                summary.status,
                summary.successfulRuns.size,
                summary.failedRuns.size,
                result?.makespan.formatOrBlank(),
                result?.loadBalance.formatOrBlank(),
                result?.cost.formatOrBlank(),
                result?.totalTime.formatOrBlank(),
                result?.fitness.formatOrBlank(),
            ),
        )
    }

    private fun printBestResults(results: List<AlgorithmResult>) {
        Logger.result("\n最优值:")
        printBest("最小 Makespan", results.minByOrNull { it.makespan }, AlgorithmResult::makespan)
        printBest("最小 Load Balance", results.minByOrNull { it.loadBalance }, AlgorithmResult::loadBalance)
        printBest("最小 Cost", results.minByOrNull { it.cost }, AlgorithmResult::cost)
        printBest("最小 Fitness", results.minByOrNull { it.fitness }, AlgorithmResult::fitness)
    }

    private fun printBest(
        label: String,
        result: AlgorithmResult?,
        value: (AlgorithmResult) -> Double,
    ) {
        Logger.result("  $label: {} ({})", result?.algorithmName.orEmpty(), result?.let(value).formatOrBlank())
    }

    private fun format(
        pattern: String,
        vararg values: Any,
    ): String = String.format(Locale.ROOT, pattern, *values)

    private fun Double?.formatOrBlank(): String = this?.let(decimalFormat::format).orEmpty()

    private companion object {
        const val RESULT_SEPARATOR_WIDTH = 80
    }
}
