package datacenter.generator

private const val MIN_TRACE_FIELD_COUNT = 13
private const val CSV_SEPARATOR = ","

private object GoogleTraceColumn {
    const val TIMESTAMP = 0
    const val JOB_ID = 1
    const val TASK_INDEX = 2
    const val MACHINE_ID = 3
    const val EVENT_TYPE = 4
    const val USER_NAME = 5
    const val SCHEDULING_CLASS = 6
    const val PRIORITY = 7
    const val CPU_REQUEST = 8
    const val MEMORY_REQUEST = 9
    const val DISK_SPACE_REQUEST = 10
    const val DIFFERENT_MACHINES_RESTRICTION = 11
}

internal object GoogleTraceRecordParser {
    fun parse(line: String): GoogleTraceRecord? {
        val fields = line.split(CSV_SEPARATOR)
        if (fields.size < MIN_TRACE_FIELD_COUNT) {
            return null
        }
        return runCatching {
            GoogleTraceRecord(
                timestamp = fields[GoogleTraceColumn.TIMESTAMP].toLong(),
                jobId = fields[GoogleTraceColumn.JOB_ID].toLong(),
                taskIndex = fields[GoogleTraceColumn.TASK_INDEX].toInt(),
                machineId = fields[GoogleTraceColumn.MACHINE_ID].toOptionalLong(),
                eventType = fields[GoogleTraceColumn.EVENT_TYPE].toInt(),
                userName = fields[GoogleTraceColumn.USER_NAME].takeIf(String::isNotEmpty),
                schedulingClass = fields[GoogleTraceColumn.SCHEDULING_CLASS].toInt(),
                priority = fields[GoogleTraceColumn.PRIORITY].toInt(),
                cpuRequest = fields[GoogleTraceColumn.CPU_REQUEST].toOptionalDouble(),
                memoryRequest = fields[GoogleTraceColumn.MEMORY_REQUEST].toOptionalDouble(),
                diskSpaceRequest = fields[GoogleTraceColumn.DISK_SPACE_REQUEST].toOptionalDouble(),
                differentMachinesRestriction =
                    fields[GoogleTraceColumn.DIFFERENT_MACHINES_RESTRICTION].toBoolean(),
            )
        }.getOrNull()
    }

    private fun String.toOptionalLong(): Long? = takeIf(String::isNotEmpty)?.toLong()

    private fun String.toOptionalDouble(): Double? = takeIf(String::isNotEmpty)?.toDouble()
}
