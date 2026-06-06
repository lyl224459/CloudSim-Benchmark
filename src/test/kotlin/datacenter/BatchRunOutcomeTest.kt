package datacenter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import util.CsvRowWriter

class BatchRunOutcomeTest {
    @Test
    fun `status reflects success partial failure and all failed runs`() {
        assertThat(BatchRunStatus.from(successfulRuns = 2, failedRuns = 0)).isEqualTo(BatchRunStatus.SUCCESS)
        assertThat(BatchRunStatus.from(successfulRuns = 1, failedRuns = 1)).isEqualTo(BatchRunStatus.PARTIAL_FAILURE)
        assertThat(BatchRunStatus.from(successfulRuns = 0, failedRuns = 2)).isEqualTo(BatchRunStatus.FAILED)
    }

    @Test
    fun `summary statistics use only successful outcomes`() {
        val runner = ComparisonRunner(BatchExperimentRequest())
        val summary =
            runner.buildAlgorithmSummary(
                algorithmName = "PSO",
                outcomes =
                    listOf(
                        BatchRunOutcome.Success(batchResult("PSO", makespan = 10.0), run = 1),
                        BatchRunOutcome.Failed(
                            "PSO",
                            run = 2,
                            errorType = "IllegalStateException",
                            errorMessage = "boom",
                        ),
                        BatchRunOutcome.Success(batchResult("PSO", makespan = 30.0), run = 3),
                    ),
            )

        assertThat(summary.status).isEqualTo(BatchRunStatus.PARTIAL_FAILURE)
        assertThat(summary.successfulRuns).hasSize(2)
        assertThat(summary.failedRuns).hasSize(1)
        assertThat(summary.average?.makespan).isEqualTo(20.0)
        assertThat(summary.statistics?.makespan?.mean).isEqualTo(20.0)
    }

    @Test
    fun `failed summary writes status and blank metric fields`() {
        val runner = ComparisonRunner(BatchExperimentRequest())
        val summary =
            runner.buildAlgorithmSummary(
                algorithmName = "WOA",
                outcomes =
                    listOf(
                        BatchRunOutcome.Failed(
                            "WOA",
                            run = 1,
                            errorType = "TimeoutException",
                            errorMessage = "timed out",
                        ),
                    ),
            )
        val row = summary.toCsvRow()

        assertThat(summary.status).isEqualTo(BatchRunStatus.FAILED)
        assertThat(summary.average).isNull()
        assertThat(summary.statistics).isNull()
        assertThat(row).hasSize(batchSummaryCsvHeaders.size)
        assertThat(row[batchSummaryCsvHeaders.indexOf("Status")]).isEqualTo(BatchRunStatus.FAILED)
        assertThat(row[batchSummaryCsvHeaders.indexOf("ErrorType")]).isEqualTo("TimeoutException")
        assertThat(row.drop(7)).allSatisfy { value -> assertThat(value).isNull() }
        assertThat(CsvRowWriter().line(row)).doesNotContain("null")
        assertThat(row.filterIsInstance<Double>()).allSatisfy { value -> assertThat(value.isNaN()).isFalse() }
    }

    @Test
    fun `trial csv row keeps failure metrics blank without sentinel values`() {
        val failed =
            BatchRunOutcome.Failed(
                algorithmName = "GWO",
                run = 7,
                errorType = "IllegalArgumentException",
                errorMessage = "bad input",
            )
        val row = failed.toTrialCsvRow()

        assertThat(row).hasSize(batchTrialCsvHeaders.size)
        assertThat(row.take(4)).containsExactly(7, BatchRunStatus.FAILED, "IllegalArgumentException", "bad input")
        assertThat(row.drop(4)).allSatisfy { value -> assertThat(value).isEqualTo("") }
        assertThat(row.filterIsInstance<Double>()).allSatisfy { value -> assertThat(value.isNaN()).isFalse() }
    }

    @Test
    fun `cloudlet count summary row aligns with generated headers`() {
        val runner = ComparisonRunner(BatchExperimentRequest())
        val summary =
            runner.buildAlgorithmSummary(
                algorithmName = "HHO",
                outcomes = listOf(BatchRunOutcome.Success(batchResult("HHO", makespan = 12.0), run = 1)),
            )

        assertThat(summary.toCloudletCountCsvRow(100)).hasSize(batchCloudletCountSummaryCsvHeaders.size)
        assertThat(batchCloudletCountSummaryCsvHeaders).startsWith("CloudletCount", "Algorithm", "Status")
    }

    private fun batchResult(
        algorithmName: String,
        makespan: Double,
    ): AlgorithmResult =
        AlgorithmResult(
            algorithmName = algorithmName,
            makespan = makespan,
            loadBalance = 1.0,
            cost = 2.0,
            totalTime = 3.0,
            fitness = 4.0,
        )
}
