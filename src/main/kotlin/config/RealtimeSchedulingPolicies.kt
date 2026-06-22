package config

enum class RealtimeQueuePolicy(
    val configValue: String,
) {
    FIFO("fifo"),
    PRIORITY("priority"),
    DEADLINE("deadline"),
    ;

    companion object {
        fun parse(value: String): RealtimeQueuePolicy =
            entries.firstOrNull { it.configValue.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("未知实时队列策略: $value")

        fun valuesForConfig(): Set<String> = entries.map { it.configValue }.toSet()
    }
}

enum class RealtimeDeadlineType(
    val configValue: String,
) {
    SOFT("soft"),
    FIRM("firm"),
    HARD("hard"),
    ;

    companion object {
        fun parse(value: String): RealtimeDeadlineType =
            entries.firstOrNull { it.configValue.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("未知实时 deadline 类型: $value")

        fun valuesForConfig(): Set<String> = entries.map { it.configValue }.toSet()
    }
}

enum class RealtimeDeadlineMissAction(
    val configValue: String,
) {
    ACCEPT("accept"),
    REJECT("reject"),
    DEGRADE("degrade"),
    RETRY_LATER("retry_later"),
    ;

    companion object {
        fun parse(value: String): RealtimeDeadlineMissAction =
            entries.firstOrNull { it.configValue.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("未知实时 deadline miss 动作: $value")

        fun valuesForConfig(): Set<String> = entries.map { it.configValue }.toSet()
    }
}

enum class RealtimeTimeoutAction(
    val configValue: String,
) {
    FAIL("fail"),
    RETRY("retry"),
    CANCEL("cancel"),
    DEGRADE("degrade"),
    ;

    companion object {
        fun parse(value: String): RealtimeTimeoutAction =
            entries.firstOrNull { it.configValue.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("未知实时超时动作: $value")

        fun valuesForConfig(): Set<String> = entries.map { it.configValue }.toSet()
    }
}

enum class RealtimePreemptionPolicy(
    val configValue: String,
) {
    PRIORITY_THEN_DEADLINE("priority_then_deadline"),
    DEADLINE_THEN_PRIORITY("deadline_then_priority"),
    ;

    companion object {
        fun parse(value: String): RealtimePreemptionPolicy =
            entries.firstOrNull { it.configValue.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("未知实时抢占策略: $value")

        fun valuesForConfig(): Set<String> = entries.map { it.configValue }.toSet()
    }
}

enum class RealtimeTenantFairnessPolicy(
    val configValue: String,
) {
    QUOTA_FIRST("quota_first"),
    WEIGHTED_FAIR("weighted_fair"),
    ;

    companion object {
        fun parse(value: String): RealtimeTenantFairnessPolicy =
            entries.firstOrNull { it.configValue.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("未知实时租户公平策略: $value")

        fun valuesForConfig(): Set<String> = entries.map { it.configValue }.toSet()
    }
}

enum class TenantSchedulingPolicy(
    val configValue: String,
) {
    QUOTA_FIRST("quota_first"),
    WEIGHTED_FAIR("weighted_fair"),
    DOMINANT_RESOURCE_FAIRNESS("dominant_resource_fairness"),
    ;

    companion object {
        fun parse(value: String): TenantSchedulingPolicy =
            entries.firstOrNull { it.configValue.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("未知实时租户调度策略: $value")

        fun valuesForConfig(): Set<String> = entries.map { it.configValue }.toSet()
    }
}

enum class RealtimeTopologyPolicy(
    val configValue: String,
) {
    LATENCY_AWARE("latency_aware"),
    SPREAD_FAULT_DOMAINS("spread_fault_domains"),
    ;

    companion object {
        fun parse(value: String): RealtimeTopologyPolicy =
            entries.firstOrNull { it.configValue.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("未知实时拓扑策略: $value")

        fun valuesForConfig(): Set<String> = entries.map { it.configValue }.toSet()
    }
}

enum class DataLocalityPolicy(
    val configValue: String,
) {
    PREFER_LOCAL("prefer_local"),
    BALANCED("balanced"),
    IGNORE("ignore"),
    ;

    companion object {
        fun parse(value: String): DataLocalityPolicy =
            entries.firstOrNull { it.configValue.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("未知数据本地性策略: $value")

        fun valuesForConfig(): Set<String> = entries.map { it.configValue }.toSet()
    }
}
