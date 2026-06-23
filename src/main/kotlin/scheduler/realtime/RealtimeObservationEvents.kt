package scheduler.realtime

enum class RealtimeObservationEventType {
    ARRIVAL,
    DEPENDENCY_BLOCKED,
    DEPENDENCY_RELEASED,
    DEPENDENCY_REJECTED,
    TENANT_REJECTED,
    CAPACITY_REJECTED,
    RESOURCE_REJECTED,
    DEADLINE_REJECTED,
    DEADLINE_DEGRADED,
    DEADLINE_RETRY_LATER,
    DEADLINE_MISS_ACCEPTED,
    VM_SELECTED,
    PENDING_SUBMIT,
    SUBMITTED,
    COMPLETED,
    TIMEOUT,
    RUNTIME_FAILURE,
    RETRY_SCHEDULED,
    PERMANENT_FAILURE,
    PREEMPTION,
    RESCHEDULE_ATTEMPT,
    RESCHEDULE_SUCCESS,
    RESCHEDULE_FAILURE,
    AUTOSCALING_EVALUATED,
    AUTOSCALING_SCALE_OUT,
    AUTOSCALING_SCALE_IN,
    AUTOSCALING_DRAIN,
    AUTOSCALING_COOLDOWN_SKIPPED,
    AUTOSCALING_WARM_POOL,
}

enum class RealtimeObservationEventScope {
    BROKER,
    CLOUDLET,
    VM,
    AUTOSCALING,
}

@Suppress("LongParameterList") // Event records intentionally mirror the fixed realtime_events.csv schema.
data class RealtimeObservationEventRecord(
    val eventId: Long,
    val eventTime: Double,
    val eventScope: RealtimeObservationEventScope,
    val eventType: RealtimeObservationEventType,
    val cloudletId: Long? = null,
    val tenantId: Int? = null,
    val priority: Int? = null,
    val lifecycleFrom: RealtimeTaskLifecycle? = null,
    val lifecycleTo: RealtimeTaskLifecycle? = null,
    val vmIndex: Int? = null,
    val previousVmIndex: Int? = null,
    val selectedVmIndex: Int? = null,
    val reason: String? = null,
    val decision: String? = null,
    val deadline: Double? = null,
    val deadlineSlack: Double? = null,
    val latenessPenalty: Double? = null,
    val queueDepth: Int? = null,
    val activeVmCount: Int? = null,
    val hostUtilization: Double? = null,
    val tenantFairnessPressure: Double? = null,
    val autoscalingPressure: Double? = null,
    val retryCount: Int? = null,
    val rescheduleCount: Int? = null,
    val preemptionCount: Int? = null,
    val migrationCount: Int? = null,
)

@Suppress("LongParameterList") // Drafts intentionally mirror the fixed realtime_events.csv schema.
data class RealtimeObservationEventDraft(
    val eventTime: Double,
    val eventScope: RealtimeObservationEventScope,
    val eventType: RealtimeObservationEventType,
    val cloudletId: Long? = null,
    val tenantId: Int? = null,
    val priority: Int? = null,
    val lifecycleFrom: RealtimeTaskLifecycle? = null,
    val lifecycleTo: RealtimeTaskLifecycle? = null,
    val vmIndex: Int? = null,
    val previousVmIndex: Int? = null,
    val selectedVmIndex: Int? = null,
    val reason: String? = null,
    val decision: String? = null,
    val deadline: Double? = null,
    val deadlineSlack: Double? = null,
    val latenessPenalty: Double? = null,
    val queueDepth: Int? = null,
    val activeVmCount: Int? = null,
    val hostUtilization: Double? = null,
    val tenantFairnessPressure: Double? = null,
    val autoscalingPressure: Double? = null,
    val retryCount: Int? = null,
    val rescheduleCount: Int? = null,
    val preemptionCount: Int? = null,
    val migrationCount: Int? = null,
) {
    fun toRecord(eventId: Long): RealtimeObservationEventRecord =
        RealtimeObservationEventRecord(
            eventId = eventId,
            eventTime = eventTime,
            eventScope = eventScope,
            eventType = eventType,
            cloudletId = cloudletId,
            tenantId = tenantId,
            priority = priority,
            lifecycleFrom = lifecycleFrom,
            lifecycleTo = lifecycleTo,
            vmIndex = vmIndex,
            previousVmIndex = previousVmIndex,
            selectedVmIndex = selectedVmIndex,
            reason = reason,
            decision = decision,
            deadline = deadline,
            deadlineSlack = deadlineSlack,
            latenessPenalty = latenessPenalty,
            queueDepth = queueDepth,
            activeVmCount = activeVmCount,
            hostUtilization = hostUtilization,
            tenantFairnessPressure = tenantFairnessPressure,
            autoscalingPressure = autoscalingPressure,
            retryCount = retryCount,
            rescheduleCount = rescheduleCount,
            preemptionCount = preemptionCount,
            migrationCount = migrationCount,
        )
}

class RealtimeObservationRecorder(
    private val enabled: Boolean,
) {
    private val records = mutableListOf<RealtimeObservationEventRecord>()
    private var nextEventId = 1L

    fun record(draft: RealtimeObservationEventDraft): RealtimeObservationEventRecord? {
        if (!enabled) return null
        val record = draft.toRecord(nextEventId++)
        records += record
        return record
    }

    fun snapshot(): List<RealtimeObservationEventRecord> = records.toList()
}
