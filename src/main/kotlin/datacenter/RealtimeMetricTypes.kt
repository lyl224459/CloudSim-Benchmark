package datacenter

import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class RealtimeMetricDirection(
    val label: String,
) {
    LOWER_IS_BETTER("越低越好"),
    HIGHER_IS_BETTER("越高越好"),
    NEUTRAL("中性"),
}

enum class RealtimeMetricValueKind {
    DOUBLE,
    INT,
    LONG,
}

enum class RealtimeMetricKey {
    MAKESPAN,
    LOAD_BALANCE,
    COST,
    TOTAL_TIME,
    FITNESS,
    AVERAGE_REALTIME_SCORE,
    AVERAGE_SELECTED_LATENESS_PENALTY,
    AVERAGE_SELECTED_DEADLINE_SLACK,
    AVERAGE_CANDIDATE_SCORE_SPREAD,
    AVERAGE_WAITING_TIME,
    AVERAGE_RESPONSE_TIME,
    REJECTED_COUNT,
    TIMEOUT_COUNT,
    FAILED_COUNT,
    RETRY_COUNT,
    PERMANENT_FAILED_COUNT,
    AVERAGE_DECISION_DELAY,
    COMPLETED_COUNT,
    SUBMITTED_COUNT,
    SLA_VIOLATION_COUNT,
    SLA_VIOLATION_RATE,
    CAPACITY_REJECTED_COUNT,
    DEADLINE_REJECTED_COUNT,
    DEADLINE_DEGRADED_COUNT,
    DEADLINE_RETRY_LATER_COUNT,
    DEADLINE_MISS_ACCEPTED_COUNT,
    DEPENDENCY_BLOCKED_COUNT,
    DEPENDENCY_RELEASED_COUNT,
    DEPENDENCY_REJECTED_COUNT,
    RESCHEDULE_ATTEMPT_COUNT,
    RESCHEDULE_SUCCESS_COUNT,
    RESCHEDULE_FAILURE_COUNT,
    AVERAGE_RESCHEDULE_DELAY,
    AVERAGE_QUEUE_DEPTH,
    MAX_QUEUE_DEPTH,
    P95_RESPONSE_TIME,
    P99_RESPONSE_TIME,
    SCALE_OUT_COUNT,
    SCALE_IN_COUNT,
    ACTIVE_VM_PEAK,
    AUTOSCALING_COST,
    COLD_START_DELAY_TOTAL,
    AVERAGE_AUTOSCALING_PRESSURE,
    AVERAGE_DEADLINE_SLACK_PRESSURE,
    AVERAGE_ARRIVAL_RATE_PRESSURE,
    SCALE_COOLDOWN_SKIPPED_COUNT,
    WARM_POOL_HIT_RATE,
    SCALE_IN_DRAIN_COUNT,
    AUTOSCALING_VM_SECONDS,
    RESOURCE_REJECTED_COUNT,
    AVERAGE_PHYSICAL_HOST_UTILIZATION,
    AVERAGE_HOST_RESOURCE_FRAGMENTATION,
    AVERAGE_NETWORK_TRANSFER_DELAY,
    IMAGE_CACHE_HIT_RATE,
    AVERAGE_NOISY_NEIGHBOR_PRESSURE,
    RUNTIME_FAILURE_COUNT,
    TIMEOUT_CANCELLED_COUNT,
    MIGRATION_COUNT,
    CHECKPOINT_RECOVERY_COUNT,
    RETRY_SUCCESS_RATE,
    SLA_PENALTY,
    PREEMPTED_COUNT,
    PREEMPTION_SUCCESS_COUNT,
    PREEMPTION_FAILED_COUNT,
    AVERAGE_PREEMPTION_DELAY,
    PREEMPTION_PENALTY,
    CHECKPOINT_LOSS_TOTAL,
    TENANT_QUOTA_REJECTED_COUNT,
    TENANT_BUDGET_REJECTED_COUNT,
    TENANT_FAIRNESS_INDEX,
    FAIRNESS_VIOLATION_COUNT,
    TENANT_SLA_PENALTY,
    DOMINANT_RESOURCE_FAIRNESS_INDEX,
    COST_SLA_TRADEOFF_SCORE,
    RETRY_SUCCESS_BY_TENANT,
    CROSS_RACK_ASSIGNMENT_COUNT,
    CROSS_REGION_ASSIGNMENT_COUNT,
    AVERAGE_TOPOLOGY_LATENCY,
    TOPOLOGY_COST,
    HOST_FAILURE_COUNT,
    RACK_FAILURE_COUNT,
    REGION_FAILURE_COUNT,
    FAILURE_DOMAIN_SPREAD_SCORE,
}

data class RealtimeMetricValues(
    val values: Map<RealtimeMetricKey, Double>,
) {
    operator fun get(key: RealtimeMetricKey): Double = values[key] ?: 0.0

    fun valueForKey(key: RealtimeMetricKey): Number {
        val definition = RealtimeMetricSchema.definitionFor(key)
        return when (definition.kind) {
            RealtimeMetricValueKind.DOUBLE -> this[key]
            RealtimeMetricValueKind.INT -> this[key].roundToInt()
            RealtimeMetricValueKind.LONG -> this[key].roundToLong()
        }
    }

    fun valueFor(definition: RealtimeMetricDefinition): Any =
        when (definition.kind) {
            RealtimeMetricValueKind.DOUBLE -> this[definition.key]
            RealtimeMetricValueKind.INT -> this[definition.key].roundToInt()
            RealtimeMetricValueKind.LONG -> this[definition.key].roundToLong()
        }

    companion object {
        fun of(vararg values: Pair<RealtimeMetricKey, Number>): RealtimeMetricValues =
            RealtimeMetricValues(values.associate { (key, value) -> key to value.toDouble() })
    }
}

@Suppress("LongParameterList") // Metric schema rows intentionally expose all CSV metadata fields.
data class RealtimeMetricDefinition(
    val key: RealtimeMetricKey,
    val csvName: String,
    val unit: String,
    val direction: RealtimeMetricDirection,
    val description: String,
    val kind: RealtimeMetricValueKind = RealtimeMetricValueKind.DOUBLE,
) {
    val meanHeader: String get() = "${csvName}_Mean"
    val stdDevHeader: String get() = "${csvName}_StdDev"

    fun trialValue(result: RealtimeAlgorithmResult): Any = result.metrics.valueFor(this)

    fun statisticValue(statistics: RealtimeAlgorithmStatistics) = statistics.metrics.getValue(key)

    fun meanValue(statistics: RealtimeAlgorithmStatistics): Double = statisticValue(statistics).mean

    fun stdDevValue(statistics: RealtimeAlgorithmStatistics): Double = statisticValue(statistics).stdDev
}
