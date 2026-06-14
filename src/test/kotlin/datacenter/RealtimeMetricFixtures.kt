package datacenter

import util.StatisticalValue

internal fun realtimeResultFixture(
    algorithmName: String = "MinLoad",
    vararg metrics: Pair<RealtimeMetricKey, Number>,
): RealtimeAlgorithmResult = RealtimeAlgorithmResult(algorithmName, RealtimeMetricValues.of(*metrics))

internal fun realtimeStatisticsFixture(
    algorithmName: String = "MinLoad",
    vararg metrics: Pair<RealtimeMetricKey, StatisticalValue>,
): RealtimeAlgorithmStatistics = RealtimeAlgorithmStatistics(algorithmName, linkedMapOf(*metrics))
