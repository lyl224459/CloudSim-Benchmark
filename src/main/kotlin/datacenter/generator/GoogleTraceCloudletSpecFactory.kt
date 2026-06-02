package datacenter.generator

import datacenter.RealtimeCloudletSpec
import datacenter.RealtimeTraceMetadata
import org.cloudsimplus.cloudlets.CloudletSimple
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic

private const val DEFAULT_CPU_REQUEST = 0.5
private const val BASE_CLOUDLET_LENGTH = 100_000L
private const val PRIORITY_LENGTH_BASE = 1.0
private const val PRIORITY_LENGTH_WEIGHT = 0.1
private const val CLOUDLET_PES = 1
private const val CLOUDLET_FILE_SIZE = 1000L
private const val CLOUDLET_OUTPUT_SIZE = 1000L
private const val REQUESTED_BW_SCALE = 1000.0
private const val DATA_REGION_MODULO = 3
private const val IMAGE_ID_MODULO = 16
private const val IMAGE_SIZE_SCALE = 1024.0
private const val DEFAULT_INPUT_DATA_SIZE = 1.0
private const val DEFAULT_IMAGE_SIZE = 1.0
private const val TENANT_HASH_MULTIPLIER = 31
private const val TENANT_ID_MODULO = 1024
private const val RETRY_HINT_VALUE = 1
private const val DEFAULT_RETRY_HINT = 0

private val retryHintEvents =
    setOf(
        GOOGLE_TRACE_EVICT_EVENT,
        GOOGLE_TRACE_FAIL_EVENT,
        GOOGLE_TRACE_KILL_EVENT,
        GOOGLE_TRACE_LOST_EVENT,
    )

internal object GoogleTraceCloudletSpecFactory {
    fun create(record: GoogleTraceRecord): RealtimeCloudletSpec {
        val cloudlet =
            CloudletSimple(cloudletLength(record), CLOUDLET_PES)
                .setFileSize(CLOUDLET_FILE_SIZE)
                .setOutputSize(CLOUDLET_OUTPUT_SIZE)
                .setUtilizationModel(UtilizationModelDynamic())
                .setPriority(record.priority)
        return RealtimeCloudletSpec(
            cloudlet,
            traceMetadata = traceMetadata(record),
        )
    }

    private fun cloudletLength(record: GoogleTraceRecord): Long {
        val cpuRequest = record.cpuRequest ?: DEFAULT_CPU_REQUEST
        val priorityMultiplier =
            PRIORITY_LENGTH_BASE +
                record.priority * PRIORITY_LENGTH_WEIGHT
        return (BASE_CLOUDLET_LENGTH * cpuRequest * priorityMultiplier).toLong()
    }

    private fun traceMetadata(record: GoogleTraceRecord): RealtimeTraceMetadata =
        RealtimeTraceMetadata(
            tenantKey = record.userName,
            tenantId = record.userName?.let(::stableTenantId),
            priority = record.priority,
            requestedCpu = record.cpuRequest,
            requestedRam = record.memoryRequest,
            requestedBw = record.cpuRequest?.times(REQUESTED_BW_SCALE),
            requestedIo = record.diskSpaceRequest,
            dataRegion = record.userName?.let { Math.floorMod(stableTenantId(it), DATA_REGION_MODULO) },
            inputDataSize = inputDataSize(record),
            imageId = "trace-image-${Math.floorMod(record.jobId.toInt(), IMAGE_ID_MODULO)}",
            imageSize = record.memoryRequest?.times(IMAGE_SIZE_SCALE)?.coerceAtLeast(0.0) ?: DEFAULT_IMAGE_SIZE,
            retryHint = retryHint(record),
        )

    private fun inputDataSize(record: GoogleTraceRecord): Double =
        record.diskSpaceRequest?.coerceAtLeast(0.0)
            ?: record.memoryRequest?.coerceAtLeast(0.0)
            ?: DEFAULT_INPUT_DATA_SIZE

    private fun retryHint(record: GoogleTraceRecord): Int =
        if (record.eventType in retryHintEvents) {
            RETRY_HINT_VALUE
        } else {
            DEFAULT_RETRY_HINT
        }

    private fun stableTenantId(userName: String): Int =
        userName
            .fold(0) { acc, char -> acc * TENANT_HASH_MULTIPLIER + char.code }
            .let { Math.floorMod(it, TENANT_ID_MODULO) }
}
