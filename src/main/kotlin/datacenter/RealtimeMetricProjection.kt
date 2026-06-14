package datacenter

internal object RealtimeMetricProjection {
    val summaryMetadataHeaders =
        listOf(
            "Algorithm",
            "Status",
            "ErrorType",
            "ErrorMessage",
            "Runs",
            "SuccessfulRuns",
            "FailedRuns",
        )
    val trialMetadataHeaders = listOf("Trial", "Status", "ErrorType", "ErrorMessage")
    val metricHeaders: List<String> = RealtimeMetricCatalog.metrics.map { it.csvName }
    val summaryHeaders: List<String> = summaryHeaders()
    val trialHeaders: List<String> = trialMetadataHeaders + metricHeaders
    val cloudletCountSummaryHeaders: List<String> = summaryHeaders(prefixHeaders = listOf("CloudletCount"))

    fun summaryHeaders(prefixHeaders: List<String> = emptyList()): List<String> =
        prefixHeaders +
            summaryMetadataHeaders +
            RealtimeMetricCatalog.metrics.map { it.meanHeader } +
            RealtimeMetricCatalog.metrics.map { it.stdDevHeader }

    fun trialMetricValues(result: RealtimeAlgorithmResult): List<Any> {
        val values = RealtimeMetricCatalog.metrics.map { it.trialValue(result) }
        return values
    }

    fun meanMetricValues(statistics: RealtimeAlgorithmStatistics): List<Double> =
        RealtimeMetricCatalog.metrics.map { it.meanValue(statistics) }

    fun stdDevMetricValues(statistics: RealtimeAlgorithmStatistics): List<Double> =
        RealtimeMetricCatalog.metrics.map { it.stdDevValue(statistics) }

    fun blankMetricValues(): List<String> = RealtimeMetricCatalog.metrics.map { "" }

    fun trialMetricMap(result: RealtimeAlgorithmResult): Map<String, Any> =
        RealtimeMetricCatalog.metrics.associateTo(linkedMapOf()) { it.csvName to it.trialValue(result) }

    fun meanMetricMap(statistics: RealtimeAlgorithmStatistics): Map<String, Any> =
        RealtimeMetricCatalog.metrics.associateTo(linkedMapOf()) { it.csvName to it.meanValue(statistics) }

    fun stdDevMetricMap(statistics: RealtimeAlgorithmStatistics): Map<String, Any> =
        RealtimeMetricCatalog.metrics.associateTo(linkedMapOf()) { it.csvName to it.stdDevValue(statistics) }
}
