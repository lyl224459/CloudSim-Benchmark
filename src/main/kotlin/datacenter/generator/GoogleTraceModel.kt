package datacenter.generator

internal const val DEFAULT_GOOGLE_TRACE_FILE_PATH = "data/google_trace/task_events.csv"
internal const val DEFAULT_GOOGLE_TRACE_MAX_TASKS = 1000
internal const val GOOGLE_TRACE_SCHEDULE_EVENT = 0
internal const val GOOGLE_TRACE_EVICT_EVENT = 1
internal const val GOOGLE_TRACE_FAIL_EVENT = 2
internal const val GOOGLE_TRACE_KILL_EVENT = 4
internal const val GOOGLE_TRACE_LOST_EVENT = 5

internal data class GoogleTraceRecord(
    val timestamp: Long,
    val jobId: Long,
    val taskIndex: Int,
    val machineId: Long?,
    val eventType: Int,
    val userName: String?,
    val schedulingClass: Int,
    val priority: Int,
    val cpuRequest: Double?,
    val memoryRequest: Double?,
    val diskSpaceRequest: Double?,
    val differentMachinesRestriction: Boolean,
)

internal data class GoogleTraceLoadRequest(
    val traceFilePath: String,
    val maxTasks: Int,
    val timeWindowStart: Long,
    val timeWindowEnd: Long,
)

internal data class GoogleTraceLoadStats(
    val loadedCount: Int,
    val skippedWindowCount: Int,
    val malformedCount: Int,
)
