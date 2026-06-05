package datacenter

enum class BatchRunStatus {
    SUCCESS,
    PARTIAL_FAILURE,
    FAILED,
    ;

    companion object {
        fun from(
            successfulRuns: Int,
            failedRuns: Int,
        ): BatchRunStatus =
            when {
                failedRuns == 0 -> SUCCESS
                successfulRuns == 0 -> FAILED
                else -> PARTIAL_FAILURE
            }
    }
}

sealed interface BatchRunOutcome {
    val algorithmName: String
    val run: Int

    data class Success(
        val result: AlgorithmResult,
        override val run: Int,
    ) : BatchRunOutcome {
        override val algorithmName: String = result.algorithmName
    }

    data class Failed(
        override val algorithmName: String,
        override val run: Int,
        val errorType: String,
        val errorMessage: String,
    ) : BatchRunOutcome
}

internal data class BatchRunSummary(
    val algorithmName: String,
    val status: BatchRunStatus,
    val average: AlgorithmResult?,
    val statistics: AlgorithmStatistics?,
    val outcomes: List<BatchRunOutcome>,
) {
    val successfulRuns: List<BatchRunOutcome.Success>
        get() = outcomes.filterIsInstance<BatchRunOutcome.Success>()

    val failedRuns: List<BatchRunOutcome.Failed>
        get() = outcomes.filterIsInstance<BatchRunOutcome.Failed>()

    val runResults: List<AlgorithmResult>
        get() = successfulRuns.map { it.result }

    val errorType: String
        get() = failedRuns.joinToString("|") { it.errorType }.ifBlank { "" }

    val errorMessage: String
        get() = failedRuns.joinToString(" | ") { "#${it.run} ${it.errorMessage}" }.ifBlank { "" }
}

internal val batchMetricHeaders = listOf("Makespan", "LoadBalance", "Cost", "TotalTime", "Fitness")

internal val batchSummaryCsvHeaders =
    listOf("Algorithm", "Status", "ErrorType", "ErrorMessage", "Runs", "SuccessfulRuns", "FailedRuns") +
        batchMetricHeaders.map { "${it}_Mean" } +
        batchMetricHeaders.map { "${it}_StdDev" }

internal val batchTrialCsvHeaders =
    listOf("Trial", "Status", "ErrorType", "ErrorMessage") + batchMetricHeaders

internal val batchCloudletCountSummaryCsvHeaders =
    listOf("CloudletCount") + batchSummaryCsvHeaders

fun AlgorithmResult.toMetricMap(): Map<String, Any> =
    linkedMapOf(
        "Makespan" to makespan,
        "LoadBalance" to loadBalance,
        "Cost" to cost,
        "TotalTime" to totalTime,
        "Fitness" to fitness,
    )

fun AlgorithmStatistics.toMeanMetricMap(): Map<String, Any> =
    linkedMapOf(
        "Makespan" to makespan.mean,
        "LoadBalance" to loadBalance.mean,
        "Cost" to cost.mean,
        "TotalTime" to totalTime.mean,
        "Fitness" to fitness.mean,
    )

fun AlgorithmStatistics.toStdDevMetricMap(): Map<String, Any> =
    linkedMapOf(
        "Makespan" to makespan.stdDev,
        "LoadBalance" to loadBalance.stdDev,
        "Cost" to cost.stdDev,
        "TotalTime" to totalTime.stdDev,
        "Fitness" to fitness.stdDev,
    )

internal fun BatchRunOutcome.toTrialCsvRow(): List<Any?> =
    when (this) {
        is BatchRunOutcome.Success ->
            listOf(run, BatchRunStatus.SUCCESS, "", "") + batchMetricHeaders.map { result.toMetricMap()[it] }
        is BatchRunOutcome.Failed ->
            listOf(run, BatchRunStatus.FAILED, errorType, errorMessage) + batchMetricHeaders.map { "" }
    }

internal fun BatchRunSummary.toCsvRow(): List<Any?> {
    val means = statistics?.toMeanMetricMap() ?: average?.toMetricMap().orEmpty()
    val stdDevs = statistics?.toStdDevMetricMap().orEmpty()
    return listOf(
        algorithmName,
        status,
        errorType,
        errorMessage,
        outcomes.size,
        successfulRuns.size,
        failedRuns.size,
    ) +
        batchMetricHeaders.map { means[it] } +
        batchMetricHeaders.map { stdDevs[it] }
}

internal fun BatchRunSummary.toSummaryMap(): Map<String, Any?> = batchSummaryCsvHeaders.zip(toCsvRow()).toMap()

internal fun BatchRunSummary.toCloudletCountCsvRow(cloudletCount: Int): List<Any?> = listOf(cloudletCount) + toCsvRow()
