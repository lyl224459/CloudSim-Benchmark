package broker

import config.RealtimeSchedulingConfig
import datacenter.RealtimeTraceMetadataProvider
import org.cloudsimplus.cloudlets.Cloudlet
import scheduler.CloudletId
import scheduler.RealtimeTaskRecord
import scheduler.RegionId
import scheduler.TenantId
import kotlin.math.ceil

internal data class RealtimeTaskMetadataRequest(
    val cloudlet: Cloudlet,
    val arrivalTime: Double,
    val attempt: Int,
    val fastestVmMips: Double?,
)

internal class RealtimeTaskMetadataFactory(
    private val schedulingConfig: RealtimeSchedulingConfig,
    private val traceMetadataProvider: RealtimeTraceMetadataProvider,
    private val tenantController: RealtimeTenantController,
    private val deterministicUnit: (CloudletId, Int, Int) -> Double,
) {
    fun create(request: RealtimeTaskMetadataRequest): RealtimeTaskRecord {
        val cloudlet = request.cloudlet
        val traceMetadata = traceMetadataProvider.metadataFor(cloudlet)
        val tenantId =
            traceMetadata
                ?.tenantId
                ?.let { Math.floorMod(it, schedulingConfig.tenantCount.coerceAtLeast(1)) }
                ?.let(::TenantId)
                ?: tenantController.tenantFor(CloudletId(cloudlet.id), deterministicUnit)

        return RealtimeTaskRecord(
            cloudletId = cloudlet.id,
            originalArrivalTime = request.arrivalTime,
            attempt = request.attempt,
            priority = priorityFor(cloudlet, traceMetadata?.priority),
            deadline = traceMetadata?.deadline ?: deadlineFor(cloudlet, request.arrivalTime, request.fastestVmMips),
            tenantId = tenantId,
            tenantKey = traceMetadata?.tenantKey,
            requestedCpu = traceMetadata?.requestedCpu,
            requestedRam = traceMetadata?.requestedRam,
            requestedBw = traceMetadata?.requestedBw,
            requestedIo = traceMetadata?.requestedIo,
            dataRegion =
                traceMetadata
                    ?.dataRegion
                    ?.let { Math.floorMod(it, schedulingConfig.regionCount.coerceAtLeast(1)) }
                    ?.let(::RegionId),
            inputDataSizeGb = traceMetadata?.inputDataSize ?: 0.0,
            imageId = traceMetadata?.imageId,
            imageSizeGb = traceMetadata?.imageSize ?: 0.0,
            traceRetryHint = traceMetadata?.retryHint,
        )
    }

    private fun priorityFor(
        cloudlet: Cloudlet,
        tracePriority: Int?,
    ): Int {
        val levels = schedulingConfig.priorityLevels.coerceAtLeast(1)
        if (levels == 1) return 0
        if (tracePriority != null) {
            return tracePriority.coerceIn(0, levels - 1)
        }
        val highPriorityCutoff = ceil(levels * schedulingConfig.highPriorityRatio).toInt().coerceIn(0, levels)
        if (highPriorityCutoff <= 0) return deterministicIndex(cloudlet.id, salt = 43, modulo = levels)
        val highPriority = deterministicUnit(CloudletId(cloudlet.id), 0, 41) < schedulingConfig.highPriorityRatio
        return if (highPriority) {
            deterministicIndex(cloudlet.id, salt = 43, modulo = highPriorityCutoff)
        } else {
            highPriorityCutoff + deterministicIndex(cloudlet.id, salt = 47, modulo = levels - highPriorityCutoff)
        }.coerceIn(0, levels - 1)
    }

    private fun deadlineFor(
        cloudlet: Cloudlet,
        arrivalTime: Double,
        fastestVmMips: Double?,
    ): Double? {
        if (schedulingConfig.deadlineFactor <= 0.0) return null
        val fastestMips = fastestVmMips ?: return null
        val estimatedRuntime = cloudlet.length.toDouble() / fastestMips
        return arrivalTime + estimatedRuntime * schedulingConfig.deadlineFactor
    }

    private fun deterministicIndex(
        cloudletId: Long,
        salt: Int,
        modulo: Int,
    ): Int {
        if (modulo <= 1) return 0
        return (deterministicUnit(CloudletId(cloudletId), 0, salt) * modulo).toInt().coerceIn(0, modulo - 1)
    }
}
