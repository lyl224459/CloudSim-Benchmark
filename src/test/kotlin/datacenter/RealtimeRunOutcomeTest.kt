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
        val runner = RealtimeComparisonRunner(resolvedAlgorithms = emptyList())
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
        val runner = RealtimeComparisonRunner(resolvedAlgorithms = emptyList())
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
        RealtimeAlgorithmResult(
            algorithmName = algorithmName,
            makespan = makespan,
            loadBalance = 1.0,
            cost = 2.0,
            totalTime = 3.0,
            fitness = 4.0,
            averageWaitingTime = 5.0,
            averageResponseTime = 6.0,
            rejectedCount = 0,
            timeoutCount = 0,
            failedCount = 0,
            retryCount = 0,
            permanentFailedCount = 0,
            averageDecisionDelay = 0.1,
            completedCount = 10,
            submittedCount = 10,
            slaViolationCount = 0,
            slaViolationRate = 0.0,
            capacityRejectedCount = 0,
            averageQueueDepth = 1.0,
            maxQueueDepth = 2,
            p95ResponseTime = 6.5,
            p99ResponseTime = 7.0,
            scaleOutCount = 0,
            scaleInCount = 0,
            activeVmPeak = 3,
            autoscalingCost = 0.0,
            coldStartDelayTotal = 0.0,
            resourceRejectedCount = 0,
            runtimeFailureCount = 0,
            timeoutCancelledCount = 0,
            migrationCount = 0,
            checkpointRecoveryCount = 0,
            retrySuccessRate = 0.0,
            slaPenalty = 0.0,
            preemptedCount = 0,
            preemptionSuccessCount = 0,
            preemptionFailedCount = 0,
            averagePreemptionDelay = 0.0,
            preemptionPenalty = 0.0,
            checkpointLossTotal = 0,
            tenantQuotaRejectedCount = 0,
            tenantBudgetRejectedCount = 0,
            tenantFairnessIndex = 1.0,
            fairnessViolationCount = 0,
            tenantSlaPenalty = 0.0,
            dominantResourceFairnessIndex = 1.0,
            costSlaTradeoffScore = 1.0,
            retrySuccessByTenant = 0.0,
            crossRackAssignmentCount = 0,
            crossRegionAssignmentCount = 0,
            averageTopologyLatency = 0.0,
            topologyCost = 0.0,
            hostFailureCount = 0,
            rackFailureCount = 0,
            regionFailureCount = 0,
            failureDomainSpreadScore = 1.0,
        )
}
