package broker

import config.RealtimeSchedulingConfig
import datacenter.RealtimeTraceMetadataProvider
import org.cloudsimplus.cloudlets.Cloudlet
import scheduler.CloudletId
import scheduler.RealtimeTaskRecord
import scheduler.RegionId
import scheduler.TenantId
import kotlin.math.ceil

private const val PRIORITY_SAMPLING_SALT = 41
private const val HIGH_PRIORITY_INDEX_SALT = 43
private const val LOW_PRIORITY_INDEX_SALT = 47

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
            expectedDuration = traceMetadata?.expectedDuration,
            dependencyIds = traceMetadata?.dependencyIds.orEmpty(),
            workflowId = traceMetadata?.workflowId,
            stageIndex = traceMetadata?.stageIndex,
            workloadClass = traceMetadata?.workloadClass,
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
    ): Int =
        priorityCandidate(cloudlet, tracePriority)
            .coerceIn(0, schedulingConfig.priorityLevels.coerceAtLeast(1) - 1)

    private fun priorityCandidate(
        cloudlet: Cloudlet,
        tracePriority: Int?,
    ): Int =
        when {
            schedulingConfig.priorityLevels <= 1 -> 0
            tracePriority != null -> tracePriority.coerceIn(0, schedulingConfig.priorityLevels - 1)
            else -> sampledPriority(cloudlet, schedulingConfig.priorityLevels)
        }

    private fun sampledPriority(
        cloudlet: Cloudlet,
        levels: Int,
    ): Int {
        val highPriorityCutoff =
            ceil(levels * schedulingConfig.highPriorityRatio)
                .toInt()
                .coerceIn(0, levels)
        val highPriority =
            deterministicUnit(CloudletId(cloudlet.id), 0, PRIORITY_SAMPLING_SALT) < schedulingConfig.highPriorityRatio
        return when {
            highPriorityCutoff <= 0 ->
                deterministicIndex(cloudlet.id, salt = HIGH_PRIORITY_INDEX_SALT, modulo = levels)
            highPriority ->
                deterministicIndex(cloudlet.id, salt = HIGH_PRIORITY_INDEX_SALT, modulo = highPriorityCutoff)
            else ->
                highPriorityCutoff +
                    deterministicIndex(
                        cloudlet.id,
                        salt = LOW_PRIORITY_INDEX_SALT,
                        modulo = levels - highPriorityCutoff,
                    )
        }
    }

    private fun deadlineFor(
        cloudlet: Cloudlet,
        arrivalTime: Double,
        fastestVmMips: Double?,
    ): Double? =
        fastestVmMips
            ?.takeIf { schedulingConfig.deadlineFactor > 0.0 }
            ?.let { fastestMips ->
                val estimatedRuntime = cloudlet.length.toDouble() / fastestMips
                arrivalTime + estimatedRuntime * schedulingConfig.deadlineFactor
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
