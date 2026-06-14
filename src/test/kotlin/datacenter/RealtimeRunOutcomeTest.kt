package datacenter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import util.CsvRowWriter

class RealtimeRunOutcomeTest {
    @Test
    fun `status reflects success partial failure and all failed runs`() {
        assertThat(RealtimeRunStatus.from(successfulRuns = 2, failedRuns = 0)).isEqualTo(RealtimeRunStatus.SUCCESS)
        val partialFailure = RealtimeRunStatus.from(successfulRuns = 1, failedRuns = 1)
        assertThat(partialFailure).isEqualTo(RealtimeRunStatus.PARTIAL_FAILURE)
        assertThat(RealtimeRunStatus.from(successfulRuns = 0, failedRuns = 2)).isEqualTo(RealtimeRunStatus.FAILED)
    }

    @Test
    fun `summary statistics use only successful outcomes`() {
        val runner = RealtimeComparisonRunner(RealtimeExperimentRequest())
        val summary =
            runner.buildRealtimeSummary(
                algorithmName = "PSO-Realtime",
                outcomes =
                    listOf(
                        RealtimeRunOutcome.Success(realtimeResult("PSO-Realtime", makespan = 10.0), run = 1),
                        RealtimeRunOutcome.Failed(
                            "PSO-Realtime",
                            run = 2,
                            errorType = "IllegalStateException",
                            errorMessage = "boom",
                        ),
                        RealtimeRunOutcome.Success(realtimeResult("PSO-Realtime", makespan = 30.0), run = 3),
                    ),
            )

        assertThat(summary.status).isEqualTo(RealtimeRunStatus.PARTIAL_FAILURE)
        assertThat(summary.successfulRuns).hasSize(2)
        assertThat(summary.failedRuns).hasSize(1)
        assertThat(summary.average?.makespan).isEqualTo(20.0)
        assertThat(summary.statistics?.makespan?.mean).isEqualTo(20.0)
    }

    @Test
    fun `failed summary writes status and blank metric fields`() {
        val runner = RealtimeComparisonRunner(RealtimeExperimentRequest())
        val summary =
            runner.buildRealtimeSummary(
                algorithmName = "WOA-Realtime",
                outcomes =
                    listOf(
                        RealtimeRunOutcome.Failed(
                            "WOA-Realtime",
                            run = 1,
                            errorType = "TimeoutException",
                            errorMessage = "timed out",
                        ),
                    ),
            )
        val row = summary.toCsvRow()
        val makespanIndex = realtimeSummaryCsvHeaders.indexOf("Makespan_Mean")

        assertThat(summary.status).isEqualTo(RealtimeRunStatus.FAILED)
        assertThat(summary.average).isNull()
        assertThat(summary.statistics).isNull()
        assertThat(row[realtimeSummaryCsvHeaders.indexOf("Status")]).isEqualTo(RealtimeRunStatus.FAILED)
        assertThat(row[realtimeSummaryCsvHeaders.indexOf("ErrorType")]).isEqualTo("TimeoutException")
        assertThat(row[makespanIndex]).isNull()
        assertThat(CsvRowWriter().line(row)).doesNotContain("null")
    }

    @Test
    fun `trial csv row keeps failure metrics blank without sentinel values`() {
        val failed =
            RealtimeRunOutcome.Failed(
                algorithmName = "GWO-Realtime",
                run = 7,
                errorType = "IllegalArgumentException",
                errorMessage = "bad input",
            )
        val row = failed.toTrialCsvRow()

        assertThat(row.take(4)).containsExactly(7, RealtimeRunStatus.FAILED, "IllegalArgumentException", "bad input")
        assertThat(row.drop(4)).allSatisfy { value -> assertThat(value).isEqualTo("") }
    }

    private fun realtimeResult(
        algorithmName: String,
        makespan: Double,
    ): RealtimeAlgorithmResult =
        realtimeResultFixture(
            algorithmName,
            RealtimeMetricKey.MAKESPAN to makespan,
            RealtimeMetricKey.LOAD_BALANCE to 1.0,
            RealtimeMetricKey.COST to 2.0,
            RealtimeMetricKey.TOTAL_TIME to 3.0,
            RealtimeMetricKey.FITNESS to 4.0,
            RealtimeMetricKey.AVERAGE_WAITING_TIME to 5.0,
            RealtimeMetricKey.AVERAGE_RESPONSE_TIME to 6.0,
            RealtimeMetricKey.COMPLETED_COUNT to 10,
            RealtimeMetricKey.SUBMITTED_COUNT to 10,
        )
}
