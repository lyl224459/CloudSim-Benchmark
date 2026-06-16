package datacenter

internal object RealtimeMetricCatalog {
    val metrics: List<RealtimeMetricDefinition> =
        RealtimePerformanceMetricDefinitions.metrics +
            RealtimeAdmissionMetricDefinitions.metrics +
            RealtimeResourceMetricDefinitions.metrics +
            RealtimeReliabilityMetricDefinitions.metrics +
            RealtimeTenantMetricDefinitions.metrics +
            RealtimeTopologyMetricDefinitions.metrics

    fun definitionFor(key: RealtimeMetricKey): RealtimeMetricDefinition = metricsByKey.getValue(key)

    private val metricsByKey: Map<RealtimeMetricKey, RealtimeMetricDefinition> = metrics.associateBy { it.key }

    init {
        require(metrics.size == metricsByKey.size) {
            "Realtime metric definitions must use unique keys"
        }
        require(metricsByKey.keys == RealtimeMetricKey.entries.toSet()) {
            "Realtime metric definitions must cover every RealtimeMetricKey"
        }
    }
}

@Suppress("LongParameterList") // Schema builder mirrors the metric definition columns one-to-one.
internal fun realtimeMetric(
    key: RealtimeMetricKey,
    csvName: String,
    unit: String,
    direction: RealtimeMetricDirection,
    description: String,
    kind: RealtimeMetricValueKind = RealtimeMetricValueKind.DOUBLE,
): RealtimeMetricDefinition = RealtimeMetricDefinition(key, csvName, unit, direction, description, kind)
