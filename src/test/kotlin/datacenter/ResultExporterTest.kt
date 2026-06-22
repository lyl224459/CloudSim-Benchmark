package datacenter

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import scheduler.RealtimeCandidateScoreRecord
import scheduler.RealtimeScoreBreakdown
import util.ExperimentOutputContext
import java.io.File

class ResultExporterTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `batch exporter writes trial comparison and summary csv`(): Unit =
        runBlocking {
            val exporter = BatchResultExporter(ExperimentOutputContext(tempDir), runs = 2)
            val summary = partialBatchSummary()

            exporter.saveTrial(summary.outcomes.first())
            exporter.exportToCsv(listOf(summary))
            exporter.saveSummary(listOf(summary))
            exporter.printComparisonResults(listOf(summary))

            assertThat(File(tempDir, "RANDOM.csv").readLines().first()).contains("Trial", "Status", "Makespan")
            assertThat(File(tempDir, "batch_comparison.csv").readLines()).hasSize(2)
            assertThat(File(tempDir, "summary_avg.csv").readLines()).hasSize(2)
            assertThat(File(tempDir, "batch_comparison.csv").readText()).contains("PARTIAL_FAILURE", "TimeoutException")
        }

    @Test
    fun `realtime exporter writes failed trial and empty summary values`(): Unit =
        runBlocking {
            val exporter = RealtimeResultExporter(ExperimentOutputContext(tempDir))
            val summary = failedRealtimeSummary()

            exporter.saveTrialOutcome(summary.outcomes.single())
            exporter.exportRealtimeToCSV(listOf(summary))
            exporter.saveSummaryResults(listOf(summary))
            exporter.printComparisonResults(listOf(summary))

            assertThat(File(tempDir, "MIN_LOAD.csv").readText()).contains("FAILED", "runtime failure")
            assertThat(File(tempDir, "realtime_comparison.csv").readLines()).hasSize(2)
            assertThat(File(tempDir, "summary_avg.csv").readLines()).hasSize(2)
        }

    @Test
    fun `realtime exporter writes candidate score detail csv for successful trials`(): Unit =
        runBlocking {
            val exporter = RealtimeResultExporter(ExperimentOutputContext(tempDir))
            val summary = successfulRealtimeSummary()

            exporter.saveTrialOutcome(summary.outcomes.single())

            val lines = File(tempDir, REALTIME_CANDIDATE_SCORE_FILE).readLines()
            assertThat(lines.first()).isEqualTo(realtimeCandidateScoreCsvHeaders.joinToString(","))
            assertThat(lines).hasSize(3)
            assertThat(lines[1].split(",").take(8))
                .containsExactly("MIN_LOAD", "1", "42", "3.000000", "1", "0", "true", "false")
            assertThat(lines[2].split(",").take(8))
                .containsExactly("MIN_LOAD", "1", "42", "3.000000", "1", "1", "true", "true")
        }

    @Test
    fun `csv disabled exporters do not create files`(): Unit =
        runBlocking {
            val disabled = ExperimentOutputContext(tempDir, csvEnabled = false)

            BatchResultExporter(disabled, runs = 1).apply {
                saveTrial(partialBatchSummary().outcomes.first())
                exportToCsv(listOf(partialBatchSummary()))
                saveSummary(listOf(partialBatchSummary()))
            }
            RealtimeResultExporter(disabled).apply {
                saveTrialOutcome(successfulRealtimeSummary().outcomes.single())
                exportRealtimeToCSV(listOf(successfulRealtimeSummary()))
                saveSummaryResults(listOf(successfulRealtimeSummary()))
            }
            BatchCloudletCountResultExporter(disabled).export(emptyMap())
            RealtimeCloudletCountResultExporter(disabled).export(emptyMap())

            assertThat(tempDir.listFiles()).isEmpty()
        }

    @Test
    fun `cloudlet count exporters sort counts and preserve headers`() {
        BatchCloudletCountResultExporter(ExperimentOutputContext(tempDir)).export(
            linkedMapOf(100 to listOf(partialBatchSummary()), 50 to emptyList()),
        )
        RealtimeCloudletCountResultExporter(ExperimentOutputContext(tempDir)).export(
            linkedMapOf(100 to listOf(failedRealtimeSummary()), 50 to emptyList()),
        )

        val batchLines = File(tempDir, "batch_cloudlet_count_summary.csv").readLines()
        val realtimeLines = File(tempDir, "realtime_cloudlet_count_summary.csv").readLines()
        assertThat(batchLines.first()).isEqualTo(batchCloudletCountSummaryCsvHeaders.joinToString(","))
        assertThat(realtimeLines.first()).isEqualTo(RealtimeMetricSchema.cloudletCountSummaryHeaders.joinToString(","))
        assertThat(batchLines[1]).startsWith("100,RANDOM")
        assertThat(realtimeLines[1]).startsWith("100,MIN_LOAD")
    }

    @Test
    fun `export failure propagates to caller`() {
        val outputPath = File(tempDir, "not-a-directory").also { it.writeText("file") }

        assertThrows<Exception> {
            BatchResultExporter(ExperimentOutputContext(outputPath), runs = 1)
                .exportToCsv(listOf(partialBatchSummary()))
        }
        assertThrows<Exception> {
            RealtimeResultExporter(ExperimentOutputContext(outputPath))
                .exportRealtimeToCSV(listOf(failedRealtimeSummary()))
        }
    }

    private fun partialBatchSummary(): BatchRunSummary {
        val success = BatchRunOutcome.Success(AlgorithmResult("RANDOM", 1.0, 2.0, 3.0, 4.0, 5.0), run = 1)
        val failure = BatchRunOutcome.Failed("RANDOM", run = 2, "TimeoutException", "timed out")
        return BatchRunSummary(
            algorithmName = "RANDOM",
            status = BatchRunStatus.PARTIAL_FAILURE,
            average = success.result,
            statistics = null,
            outcomes = listOf(success, failure),
        )
    }

    private fun failedRealtimeSummary() =
        RealtimeRunSummary(
            algorithmName = "MIN_LOAD",
            status = RealtimeRunStatus.FAILED,
            average = null,
            statistics = null,
            outcomes =
                listOf(
                    RealtimeRunOutcome.Failed(
                        algorithmName = "MIN_LOAD",
                        run = 1,
                        errorType = "IllegalStateException",
                        errorMessage = "runtime failure",
                    ),
                ),
        )

    private fun successfulRealtimeSummary(): RealtimeRunSummary {
        val result =
            RealtimeAlgorithmResult(
                algorithmName = "MIN_LOAD",
                metrics =
                    RealtimeMetricValues.of(
                        RealtimeMetricKey.MAKESPAN to 1.0,
                        RealtimeMetricKey.AVERAGE_REALTIME_SCORE to 6.0,
                    ),
                candidateScores =
                    listOf(
                        candidateScore(candidateVmIndex = 0, selected = false),
                        candidateScore(candidateVmIndex = 1, selected = true),
                    ),
            )
        return RealtimeRunSummary(
            algorithmName = "MIN_LOAD",
            status = RealtimeRunStatus.SUCCESS,
            average = result,
            statistics = null,
            outcomes = listOf(RealtimeRunOutcome.Success(result, run = 1)),
        )
    }

    private fun candidateScore(
        candidateVmIndex: Int,
        selected: Boolean,
    ): RealtimeCandidateScoreRecord =
        RealtimeCandidateScoreRecord(
            cloudletId = 42L,
            arrivalTime = 3.0,
            selectedVmIndex = 1,
            candidateVmIndex = candidateVmIndex,
            accepted = true,
            selected = selected,
            breakdown =
                RealtimeScoreBreakdown(
                    totalScore = if (selected) 6.0 else 9.0,
                    projectedFinishTime = if (selected) 4.0 else 7.0,
                    estimatedRuntime = 1.0,
                    deadlineSlack = if (selected) 2.0 else -1.0,
                    latenessPenalty = if (selected) 0.0 else 1.0,
                    priorityPressure = 0.5,
                    preemptionCost = 0.0,
                    resourcePressure = 0.0,
                    topologyLatency = 0.0,
                    topologyCost = 0.0,
                    tenantFairnessPressure = 0.0,
                    queuePressure = 0.0,
                ),
        )
}
