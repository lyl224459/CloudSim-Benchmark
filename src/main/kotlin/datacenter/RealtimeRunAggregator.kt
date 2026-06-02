package datacenter

import util.StatisticalValue
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal object RealtimeRunAggregator {
    fun buildSummary(
        algorithmName: String,
        outcomes: List<RealtimeRunOutcome>,
    ): RealtimeRunSummary {
        val runResults = outcomes.filterIsInstance<RealtimeRunOutcome.Success>().map { it.result }
        val failedCount = outcomes.count { it is RealtimeRunOutcome.Failed }
        return RealtimeRunSummary(
            algorithmName = algorithmName,
            status = RealtimeRunStatus.from(runResults.size, failedCount),
            average = runResults.takeIf { it.isNotEmpty() }?.let(::averageResults),
            statistics = runResults.takeIf { it.isNotEmpty() }?.let(::statisticsFor),
            outcomes = outcomes,
        )
    }

    private fun averageResults(runResults: List<RealtimeAlgorithmResult>): RealtimeAlgorithmResult {
        val values =
            RealtimeMetricSchema.metrics.associate { metric ->
                val average = runResults.map { it[metric.key] }.average()
                metric.key to average.asStoredAverage(metric.kind)
            }
        return RealtimeAlgorithmResult(
            algorithmName = runResults[0].algorithmName,
            metrics = RealtimeMetricValues(values),
        )
    }

    private fun statisticsFor(results: List<RealtimeAlgorithmResult>): RealtimeAlgorithmStatistics {
        val statistics =
            RealtimeMetricSchema.metrics.associate { metric ->
                metric.key to StatisticalValue.fromArray(results.map { it[metric.key] }.toDoubleArray())
            }
        return RealtimeAlgorithmStatistics(results[0].algorithmName, statistics)
    }

    private fun Double.asStoredAverage(kind: RealtimeMetricValueKind): Double =
        when (kind) {
            RealtimeMetricValueKind.DOUBLE -> this
            RealtimeMetricValueKind.INT -> roundToInt().toDouble()
            RealtimeMetricValueKind.LONG -> roundToLong().toDouble()
        }
}
