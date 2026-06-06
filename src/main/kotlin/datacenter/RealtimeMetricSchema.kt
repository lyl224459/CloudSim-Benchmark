package datacenter

object RealtimeMetricSchema {
    val summaryMetadataHeaders: List<String> = RealtimeMetricProjection.summaryMetadataHeaders
    val trialMetadataHeaders: List<String> = RealtimeMetricProjection.trialMetadataHeaders
    val metrics: List<RealtimeMetricDefinition> = RealtimeMetricCatalog.metrics
    val metricHeaders: List<String> = RealtimeMetricProjection.metricHeaders
    val summaryHeaders: List<String> = RealtimeMetricProjection.summaryHeaders
    val trialHeaders: List<String> = RealtimeMetricProjection.trialHeaders
    val cloudletCountSummaryHeaders: List<String> = RealtimeMetricProjection.cloudletCountSummaryHeaders

    fun summaryHeaders(prefixHeaders: List<String> = emptyList()): List<String> {
        val headers = RealtimeMetricProjection.summaryHeaders(prefixHeaders)
        return headers
    }

    fun trialMetricValues(result: RealtimeAlgorithmResult): List<Any> {
        val values = RealtimeMetricProjection.trialMetricValues(result)
        return values
    }

    fun meanMetricValues(statistics: RealtimeAlgorithmStatistics): List<Double> {
        val values = RealtimeMetricProjection.meanMetricValues(statistics)
        return values
    }

    fun stdDevMetricValues(statistics: RealtimeAlgorithmStatistics): List<Double> {
        val values = RealtimeMetricProjection.stdDevMetricValues(statistics)
        return values
    }

    fun blankMetricValues(): List<String> = RealtimeMetricProjection.blankMetricValues()

    fun trialMetricMap(result: RealtimeAlgorithmResult): Map<String, Any> {
        val metrics = RealtimeMetricProjection.trialMetricMap(result)
        return metrics
    }

    fun meanMetricMap(statistics: RealtimeAlgorithmStatistics): Map<String, Any> {
        val metrics = RealtimeMetricProjection.meanMetricMap(statistics)
        return metrics
    }

    fun stdDevMetricMap(statistics: RealtimeAlgorithmStatistics): Map<String, Any> {
        val metrics = RealtimeMetricProjection.stdDevMetricMap(statistics)
        return metrics
    }

    fun definitionFor(key: RealtimeMetricKey): RealtimeMetricDefinition = RealtimeMetricCatalog.definitionFor(key)
}
