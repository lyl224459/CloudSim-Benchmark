package datacenter

enum class RealtimeRunStatus {
    SUCCESS,
    PARTIAL_FAILURE,
    FAILED,
    ;

    companion object {
        fun from(
            successfulRuns: Int,
            failedRuns: Int,
        ): RealtimeRunStatus =
            when {
                failedRuns == 0 -> SUCCESS
                successfulRuns == 0 -> FAILED
                else -> PARTIAL_FAILURE
            }
    }
}

sealed interface RealtimeRunOutcome {
    val algorithmName: String
    val run: Int

    data class Success(
        val result: RealtimeAlgorithmResult,
        override val run: Int,
    ) : RealtimeRunOutcome {
        override val algorithmName: String = result.algorithmName
    }

    data class Failed(
        override val algorithmName: String,
        override val run: Int,
        val errorType: String,
        val errorMessage: String,
    ) : RealtimeRunOutcome
}

internal data class RealtimeRunSummary(
    val algorithmName: String,
    val status: RealtimeRunStatus,
    val average: RealtimeAlgorithmResult?,
    val statistics: RealtimeAlgorithmStatistics?,
    val outcomes: List<RealtimeRunOutcome>,
) {
    val successfulRuns: List<RealtimeRunOutcome.Success>
        get() = outcomes.filterIsInstance<RealtimeRunOutcome.Success>()

    val failedRuns: List<RealtimeRunOutcome.Failed>
        get() = outcomes.filterIsInstance<RealtimeRunOutcome.Failed>()

    val runResults: List<RealtimeAlgorithmResult>
        get() = successfulRuns.map { it.result }

    val errorType: String
        get() = failedRuns.joinToString("|") { it.errorType }.ifBlank { "" }

    val errorMessage: String
        get() = failedRuns.joinToString(" | ") { "#${it.run} ${it.errorMessage}" }.ifBlank { "" }
}

internal val realtimeMetricHeaders: List<String> = RealtimeMetricSchema.metricHeaders

internal val realtimeSummaryCsvHeaders: List<String> = RealtimeMetricSchema.summaryHeaders

internal val realtimeTrialCsvHeaders: List<String> = RealtimeMetricSchema.trialHeaders

fun RealtimeAlgorithmResult.toMetricMap(): Map<String, Any> = RealtimeMetricSchema.trialMetricMap(this)

fun RealtimeAlgorithmStatistics.toMeanMetricMap(): Map<String, Any> = RealtimeMetricSchema.meanMetricMap(this)

fun RealtimeAlgorithmStatistics.toStdDevMetricMap(): Map<String, Any> = RealtimeMetricSchema.stdDevMetricMap(this)

internal fun RealtimeRunOutcome.toTrialCsvRow(): List<Any?> =
    when (this) {
        is RealtimeRunOutcome.Success ->
            listOf(run, RealtimeRunStatus.SUCCESS, "", "") + RealtimeMetricSchema.trialMetricValues(result)
        is RealtimeRunOutcome.Failed ->
            listOf(run, RealtimeRunStatus.FAILED, errorType, errorMessage) + RealtimeMetricSchema.blankMetricValues()
    }

internal fun RealtimeRunSummary.toCsvRow(): List<Any?> {
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
        realtimeMetricHeaders.map { means[it] } +
        realtimeMetricHeaders.map { stdDevs[it] }
}

internal fun RealtimeRunSummary.toSummaryMap(): Map<String, Any?> = realtimeSummaryCsvHeaders.zip(toCsvRow()).toMap()

internal fun RealtimeRunSummary.toCloudletCountCsvRow(cloudletCount: Int): List<Any?> = listOf(cloudletCount) + toCsvRow()
