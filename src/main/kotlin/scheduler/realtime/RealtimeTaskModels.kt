package scheduler.realtime

@JvmInline
value class CloudletId(
    val value: Long,
)

@JvmInline
value class VmIndex(
    val value: Int,
)

@JvmInline
value class TenantId(
    val value: Int,
)

@JvmInline
value class RegionId(
    val value: Int,
)

@JvmInline
value class RackId(
    val value: Int,
)

@JvmInline
value class HostId(
    val value: Int,
)

@JvmInline
value class FailureDomainId(
    val value: Int,
)

@JvmInline
value class DatacenterId(
    val value: Int,
)

enum class RealtimeTaskLifecycle {
    ARRIVED,
    PENDING_DECISION,
    SUBMITTED,
    RUNNING,
    PREEMPTED,
    MIGRATING,
    RETRYING,
    COMPLETED,
    REJECTED,
    FAILED,
    CANCELLED,
    TIMED_OUT,
}

sealed interface RealtimeTaskState {
    val lifecycle: RealtimeTaskLifecycle

    data object Arrived : RealtimeTaskState {
        override val lifecycle = RealtimeTaskLifecycle.ARRIVED
    }

    data object PendingDecision : RealtimeTaskState {
        override val lifecycle = RealtimeTaskLifecycle.PENDING_DECISION
    }

    data object Submitted : RealtimeTaskState {
        override val lifecycle = RealtimeTaskLifecycle.SUBMITTED
    }

    data object Running : RealtimeTaskState {
        override val lifecycle = RealtimeTaskLifecycle.RUNNING
    }

    data object Preempted : RealtimeTaskState {
        override val lifecycle = RealtimeTaskLifecycle.PREEMPTED
    }

    data object Migrating : RealtimeTaskState {
        override val lifecycle = RealtimeTaskLifecycle.MIGRATING
    }

    data object Retrying : RealtimeTaskState {
        override val lifecycle = RealtimeTaskLifecycle.RETRYING
    }

    data object Completed : RealtimeTaskState {
        override val lifecycle = RealtimeTaskLifecycle.COMPLETED
    }

    data class Rejected(
        val reason: String? = null,
    ) : RealtimeTaskState {
        override val lifecycle = RealtimeTaskLifecycle.REJECTED
    }

    data class Failed(
        val reason: String? = null,
    ) : RealtimeTaskState {
        override val lifecycle = RealtimeTaskLifecycle.FAILED
    }

    data object Cancelled : RealtimeTaskState {
        override val lifecycle = RealtimeTaskLifecycle.CANCELLED
    }

    data object TimedOut : RealtimeTaskState {
        override val lifecycle = RealtimeTaskLifecycle.TIMED_OUT
    }

    companion object {
        fun fromLifecycle(lifecycle: RealtimeTaskLifecycle): RealtimeTaskState =
            when (lifecycle) {
                RealtimeTaskLifecycle.ARRIVED -> Arrived
                RealtimeTaskLifecycle.PENDING_DECISION -> PendingDecision
                RealtimeTaskLifecycle.SUBMITTED -> Submitted
                RealtimeTaskLifecycle.RUNNING -> Running
                RealtimeTaskLifecycle.PREEMPTED -> Preempted
                RealtimeTaskLifecycle.MIGRATING -> Migrating
                RealtimeTaskLifecycle.RETRYING -> Retrying
                RealtimeTaskLifecycle.COMPLETED -> Completed
                RealtimeTaskLifecycle.REJECTED -> Rejected()
                RealtimeTaskLifecycle.FAILED -> Failed()
                RealtimeTaskLifecycle.CANCELLED -> Cancelled
                RealtimeTaskLifecycle.TIMED_OUT -> TimedOut
            }
    }
}

data class RealtimeTaskRecord(
    val cloudletId: Long,
    val originalArrivalTime: Double,
    val attempt: Int = 0,
    val priority: Int = 0,
    val deadline: Double? = null,
    val assignedVmIndex: Int? = null,
    val lastDecisionDelay: Double = 0.0,
    val lifecycle: RealtimeTaskLifecycle = RealtimeTaskLifecycle.ARRIVED,
    val interruptedCount: Int = 0,
    val checkpointRecoveredLength: Long = 0L,
    val timeoutActionTaken: String? = null,
    val migratedCount: Int = 0,
    val preemptedCount: Int = 0,
    val preemptionDelayTotal: Double = 0.0,
    val checkpointLossTotal: Long = 0L,
    val tenantId: TenantId = TenantId(0),
    val tenantKey: String? = null,
    val requestedCpu: Double? = null,
    val requestedRam: Double? = null,
    val requestedBw: Double? = null,
    val requestedIo: Double? = null,
    val dataRegion: RegionId? = null,
    val inputDataSizeGb: Double = 0.0,
    val imageId: String? = null,
    val imageSizeGb: Double = 0.0,
    val traceRetryHint: Int? = null,
) {
    val id: CloudletId get() = CloudletId(cloudletId)
    val assignedVm: VmIndex? get() = assignedVmIndex?.let(::VmIndex)
    val state: RealtimeTaskState get() = RealtimeTaskState.fromLifecycle(lifecycle)

    fun workloadDescriptor(defaultDataRegion: RegionId = RegionId(0)): RealtimeWorkloadDescriptor =
        RealtimeWorkloadDescriptor(
            cloudletId = id,
            tenantId = tenantId,
            priority = priority,
            deadline = deadline,
            requestedCpu = requestedCpu ?: 1.0,
            requestedRam = requestedRam ?: 0.0,
            requestedBw = requestedBw ?: 0.0,
            requestedIo = requestedIo ?: 0.0,
            dataRegion = dataRegion ?: defaultDataRegion,
            inputDataSizeGb = inputDataSizeGb.coerceAtLeast(0.0),
            imageId = imageId,
            imageSizeGb = imageSizeGb.coerceAtLeast(0.0),
        )
}

data class RealtimeWorkloadDescriptor(
    val cloudletId: CloudletId,
    val tenantId: TenantId,
    val priority: Int,
    val deadline: Double?,
    val requestedCpu: Double,
    val requestedRam: Double,
    val requestedBw: Double,
    val requestedIo: Double,
    val dataRegion: RegionId,
    val inputDataSizeGb: Double,
    val imageId: String?,
    val imageSizeGb: Double,
)

data class RealtimeTenantFairnessSnapshot(
    val tenantId: TenantId,
    val activeCount: Int,
    val completedCount: Int,
    val quota: Int?,
    val weight: Double,
    val fairnessScore: Double,
    val dominantResourceShare: Double,
    val budgetUsed: Double,
    val budgetLimit: Double?,
    val slaPenalty: Double,
    val fairnessPressure: Double,
)

typealias RealtimeTaskMetadata = RealtimeTaskRecord

data class RealtimePreemptionCandidate(
    val victimCloudletId: CloudletId,
    val victimVmIndex: VmIndex,
    val victimPriority: Int,
    val victimDeadline: Double?,
    val preemptedCount: Int,
)
