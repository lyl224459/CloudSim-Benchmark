package broker

import scheduler.RealtimeNodeState
import scheduler.RealtimeObservationEventDraft
import scheduler.RealtimeObservationEventScope
import scheduler.RealtimeObservationEventType
import scheduler.RealtimeScoreBreakdown
import scheduler.RealtimeTaskLifecycle
import scheduler.RealtimeTaskRecord

@Suppress("LongParameterList") // Event helper maps broker state into the fixed observation CSV schema.
internal fun RealtimeBrokerMetrics.recordTaskObservation(
    eventTime: Double,
    eventType: RealtimeObservationEventType,
    record: RealtimeTaskRecord,
    lifecycleFrom: RealtimeTaskLifecycle? = null,
    lifecycleTo: RealtimeTaskLifecycle? = null,
    vmIndex: Int? = record.assignedVmIndex,
    previousVmIndex: Int? = null,
    selectedVmIndex: Int? = null,
    reason: String? = null,
    decision: String? = null,
    score: RealtimeScoreBreakdown? = null,
    queueDepth: Int? = null,
    activeVmCount: Int? = null,
    nodeState: RealtimeNodeState? = null,
    tenantFairnessPressure: Double? = null,
) {
    recordObservation(
        RealtimeObservationEventDraft(
            eventTime = eventTime,
            eventScope = RealtimeObservationEventScope.CLOUDLET,
            eventType = eventType,
            cloudletId = record.cloudletId,
            tenantId = record.tenantId.value,
            priority = record.priority,
            lifecycleFrom = lifecycleFrom,
            lifecycleTo = lifecycleTo,
            vmIndex = vmIndex,
            previousVmIndex = previousVmIndex,
            selectedVmIndex = selectedVmIndex,
            reason = reason,
            decision = decision,
            deadline = record.deadline,
            deadlineSlack = score?.deadlineSlack,
            latenessPenalty = score?.latenessPenalty,
            queueDepth = queueDepth,
            activeVmCount = activeVmCount,
            hostUtilization = nodeState?.physicalHostUtilization,
            tenantFairnessPressure = tenantFairnessPressure,
            retryCount = record.attempt,
            rescheduleCount = record.rescheduleCount,
            preemptionCount = record.preemptedCount,
            migrationCount = record.migratedCount,
        ),
    )
}

@Suppress("LongParameterList") // Broker events intentionally map directly to the fixed observation schema.
internal fun RealtimeBrokerMetrics.recordBrokerObservation(
    eventTime: Double,
    eventType: RealtimeObservationEventType,
    eventScope: RealtimeObservationEventScope = RealtimeObservationEventScope.BROKER,
    vmIndex: Int? = null,
    reason: String? = null,
    decision: String? = null,
    queueDepth: Int? = null,
    activeVmCount: Int? = null,
    autoscalingPressure: Double? = null,
) {
    recordObservation(
        RealtimeObservationEventDraft(
            eventTime = eventTime,
            eventScope = eventScope,
            eventType = eventType,
            vmIndex = vmIndex,
            reason = reason,
            decision = decision,
            queueDepth = queueDepth,
            activeVmCount = activeVmCount,
            autoscalingPressure = autoscalingPressure,
        ),
    )
}
