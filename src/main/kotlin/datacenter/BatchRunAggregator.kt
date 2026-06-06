package datacenter

import util.StatisticalValue

internal object BatchRunAggregator {
    fun buildSummary(
        algorithmName: String,
        outcomes: List<BatchRunOutcome>,
    ): BatchRunSummary {
        val results = outcomes.filterIsInstance<BatchRunOutcome.Success>().map { it.result }
        val failedCount = outcomes.count { it is BatchRunOutcome.Failed }
        return BatchRunSummary(
            algorithmName = algorithmName,
            status = BatchRunStatus.from(results.size, failedCount),
            average = results.takeIf(List<*>::isNotEmpty)?.let(::average),
            statistics = results.takeIf(List<*>::isNotEmpty)?.let(::statistics),
            outcomes = outcomes,
        )
    }

    private fun average(results: List<AlgorithmResult>): AlgorithmResult =
        AlgorithmResult(
            algorithmName = results.first().algorithmName,
            makespan = results.map { it.makespan }.average(),
            loadBalance = results.map { it.loadBalance }.average(),
            cost = results.map { it.cost }.average(),
            totalTime = results.map { it.totalTime }.average(),
            fitness = results.map { it.fitness }.average(),
        )

    private fun statistics(results: List<AlgorithmResult>): AlgorithmStatistics =
        AlgorithmStatistics(
            algorithmName = results.first().algorithmName,
            makespan = statisticalValue(results.map { it.makespan }),
            loadBalance = statisticalValue(results.map { it.loadBalance }),
            cost = statisticalValue(results.map { it.cost }),
            totalTime = statisticalValue(results.map { it.totalTime }),
            fitness = statisticalValue(results.map { it.fitness }),
        )

    private fun statisticalValue(values: List<Double>): StatisticalValue {
        val array = values.toDoubleArray()
        return StatisticalValue.fromArray(array)
    }
}
