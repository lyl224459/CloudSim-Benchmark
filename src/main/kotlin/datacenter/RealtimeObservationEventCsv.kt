package datacenter

import scheduler.RealtimeObservationEventRecord

internal const val REALTIME_OBSERVATION_EVENT_FILE = "realtime_events.csv"

internal val realtimeObservationEventCsvHeaders =
    listOf(
        "Algorithm",
        "Run",
        "EventId",
        "EventTime",
        "EventScope",
        "EventType",
        "CloudletId",
        "TenantId",
        "Priority",
        "LifecycleFrom",
        "LifecycleTo",
        "VmIndex",
        "PreviousVmIndex",
        "SelectedVmIndex",
        "Reason",
        "Decision",
        "Deadline",
        "DeadlineSlack",
        "LatenessPenalty",
        "QueueDepth",
        "ActiveVmCount",
        "HostUtilization",
        "TenantFairnessPressure",
        "AutoscalingPressure",
        "RetryCount",
        "RescheduleCount",
        "PreemptionCount",
        "MigrationCount",
    )

internal fun RealtimeObservationEventRecord.toCsvRow(
    algorithmName: String,
    run: Int,
): List<Any?> =
    listOf(
        algorithmName,
        run,
        eventId,
        eventTime,
        eventScope,
        eventType,
        cloudletId,
        tenantId,
        priority,
        lifecycleFrom,
        lifecycleTo,
        vmIndex,
        previousVmIndex,
        selectedVmIndex,
        reason,
        decision,
        deadline,
        deadlineSlack,
        latenessPenalty,
        queueDepth,
        activeVmCount,
        hostUtilization,
        tenantFairnessPressure,
        autoscalingPressure,
        retryCount,
        rescheduleCount,
        preemptionCount,
        migrationCount,
    )
