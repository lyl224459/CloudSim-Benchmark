package broker

import config.RealtimePreemptionPolicy
import config.RealtimeSchedulingConfig
import config.RealtimeTenantFairnessPolicy
import org.cloudsimplus.cloudlets.Cloudlet
import scheduler.CloudletId
import scheduler.RealtimeNodeState
import scheduler.RealtimePreemptionCandidate
import scheduler.RealtimeTaskLifecycle
import scheduler.RealtimeTaskRecord
import scheduler.TenantId
import scheduler.VmIndex
import kotlin.math.pow

enum class RealtimeRejectReason {
    QUEUE,
    CAPACITY,
    RESOURCE,
    TENANT_QUOTA
}

sealed interface AdmissionDecision {
    data object Accepted : AdmissionDecision
    data class Rejected(val reason: RealtimeRejectReason) : AdmissionDecision
}

class RealtimeAdmissionController(
    private val scheduling: RealtimeSchedulingConfig
) {
    fun decide(activeCloudletCount: Int, nodeStates: List<RealtimeNodeState>): AdmissionDecision {
        if (activeCloudletCount >= scheduling.maxQueueSize) {
            return AdmissionDecision.Rejected(RealtimeRejectReason.QUEUE)
        }
        if (nodeStates.isNotEmpty() && nodeStates.none { it.acceptingWork }) {
            val reason = if (nodeStates.any { !it.resourceAcceptingWork }) {
                RealtimeRejectReason.RESOURCE
            } else {
                RealtimeRejectReason.CAPACITY
            }
            return AdmissionDecision.Rejected(reason)
        }
        return AdmissionDecision.Accepted
    }
}

data class TenantSnapshot(
    val tenantId: TenantId,
    val activeCount: Int,
    val completedCount: Int,
    val quota: Int?,
    val weight: Double,
    val fairnessScore: Double
)

sealed interface TenantAdmissionDecision {
    data object Accepted : TenantAdmissionDecision
    data class Rejected(val tenantId: TenantId, val quota: Int) : TenantAdmissionDecision
}

class RealtimeTenantController(
    private val scheduling: RealtimeSchedulingConfig
) {
    private val enabled: Boolean = scheduling.multiTenantEnabled
    private val tenantCount: Int = scheduling.tenantCount.coerceAtLeast(1)
    private val quotas: List<Int>? = scheduling.tenantQuota.takeIf { it.isNotEmpty() }
    private val weights: List<Double> = when {
        scheduling.tenantWeights.isEmpty() -> List(tenantCount) { 1.0 }
        else -> scheduling.tenantWeights
    }
    private val policy: RealtimeTenantFairnessPolicy = scheduling.normalizedTenantFairnessPolicy()

    fun tenantFor(cloudletId: CloudletId, sampler: (CloudletId, Int, Int) -> Double): TenantId {
        if (!enabled) return TenantId(0)
        val index = (sampler(cloudletId, 0, 83) * tenantCount).toInt().coerceIn(0, tenantCount - 1)
        return TenantId(index)
    }

    fun decide(incoming: RealtimeTaskRecord, activeRecords: List<RealtimeTaskRecord>): TenantAdmissionDecision {
        if (!enabled) return TenantAdmissionDecision.Accepted
        val quota = quotas?.getOrNull(incoming.tenantId.value) ?: return TenantAdmissionDecision.Accepted
        val activeCount = activeRecords.count {
            it.cloudletId != incoming.cloudletId &&
                it.tenantId == incoming.tenantId &&
                it.lifecycle.isActiveForTenantQuota()
        }
        return if (activeCount >= quota) {
            TenantAdmissionDecision.Rejected(incoming.tenantId, quota)
        } else {
            TenantAdmissionDecision.Accepted
        }
    }

    fun snapshots(
        records: List<RealtimeTaskRecord>,
        completedCloudlets: List<Cloudlet> = emptyList()
    ): List<TenantSnapshot> {
        val completedByTenant = completedCloudlets
            .mapNotNull { cloudlet -> records.firstOrNull { it.cloudletId == cloudlet.id } }
            .groupingBy { it.tenantId.value }
            .eachCount()
        val activeByTenant = records
            .filter { it.lifecycle.isActiveForTenantQuota() }
            .groupingBy { it.tenantId.value }
            .eachCount()
        return (0 until tenantCount).map { index ->
            val activeCount = activeByTenant[index] ?: 0
            val completedCount = completedByTenant[index] ?: 0
            val weight = weights.getOrNull(index) ?: 1.0
            TenantSnapshot(
                tenantId = TenantId(index),
                activeCount = activeCount,
                completedCount = completedCount,
                quota = quotas?.getOrNull(index),
                weight = weight,
                fairnessScore = fairnessScore(activeCount, completedCount, weight)
            )
        }
    }

    fun fairnessIndex(completedCloudlets: List<Cloudlet>, records: List<RealtimeTaskRecord>): Double {
        if (!enabled) return 1.0
        val recordById = records.associateBy { it.cloudletId }
        val completedByTenant = completedCloudlets
            .filter { it.status == Cloudlet.Status.SUCCESS }
            .mapNotNull { cloudlet -> recordById[cloudlet.id] }
            .groupingBy { it.tenantId.value }
            .eachCount()
        val counts = (0 until tenantCount).map { (completedByTenant[it] ?: 0).toDouble() }
        val sum = counts.sum()
        if (sum <= 0.0) return 1.0
        val squareSum = counts.sumOf { it * it }
        if (squareSum <= 0.0) return 1.0
        return (sum * sum) / (tenantCount.toDouble() * squareSum)
    }

    private fun fairnessScore(activeCount: Int, completedCount: Int, weight: Double): Double {
        val base = when (policy) {
            RealtimeTenantFairnessPolicy.QUOTA_FIRST -> activeCount.toDouble()
            RealtimeTenantFairnessPolicy.WEIGHTED_FAIR -> (activeCount + completedCount).toDouble() / weight
        }
        return base
    }

    private fun RealtimeTaskLifecycle.isActiveForTenantQuota(): Boolean =
        this == RealtimeTaskLifecycle.PENDING_DECISION ||
            this == RealtimeTaskLifecycle.SUBMITTED ||
            this == RealtimeTaskLifecycle.RUNNING ||
            this == RealtimeTaskLifecycle.PREEMPTED ||
            this == RealtimeTaskLifecycle.MIGRATING ||
            this == RealtimeTaskLifecycle.RETRYING
}

sealed interface FailureDecision {
    data object Continue : FailureDecision
    data class Retry(val delay: Double) : FailureDecision
    data object PermanentlyFail : FailureDecision
}

class RealtimeFailureController(
    private val scheduling: RealtimeSchedulingConfig,
    private val unitSampler: (CloudletId, Int, Int) -> Double
) {
    fun submitAttempt(cloudletId: CloudletId, attempt: Int, failurePressure: Double): FailureDecision {
        val effectiveFailureRate = (scheduling.failureRate +
            failurePressure * scheduling.overloadFailureMultiplier).coerceIn(0.0, 1.0)
        if (effectiveFailureRate <= 0.0) return FailureDecision.Continue
        val failed = effectiveFailureRate >= 1.0 || unitSampler(cloudletId, attempt, 29) < effectiveFailureRate
        if (!failed) return FailureDecision.Continue
        return if (attempt < scheduling.retryLimit) {
            FailureDecision.Retry(retryDelay(attempt))
        } else {
            FailureDecision.PermanentlyFail
        }
    }

    fun retryDelay(attempt: Int): Double {
        if (scheduling.retryDelay <= 0.0) return 0.0
        return scheduling.retryDelay * scheduling.retryBackoffMultiplier.pow(attempt.toDouble())
    }
}

data class TimeoutDecision(
    val action: config.RealtimeTimeoutAction
)

class RealtimeTimeoutController(
    private val scheduling: RealtimeSchedulingConfig
) {
    fun decide(): TimeoutDecision = TimeoutDecision(scheduling.normalizedTimeoutAction())
}

sealed interface PreemptionDecision {
    data object None : PreemptionDecision
    data class Preempt(
        val victimCloudletId: CloudletId,
        val victimVmIndex: VmIndex,
        val delay: Double,
        val penalty: Double
    ) : PreemptionDecision
}

class RealtimePreemptionController(
    private val scheduling: RealtimeSchedulingConfig
) {
    fun candidates(
        incoming: RealtimeTaskRecord,
        activeCloudlets: List<Cloudlet>,
        records: List<RealtimeTaskRecord>,
        vmReservations: Map<Long, Int>
    ): List<RealtimePreemptionCandidate> {
        if (!scheduling.preemptionEnabled) return emptyList()
        val recordById = records.associateBy { it.cloudletId }
        return activeCloudlets.mapNotNull { cloudlet ->
            val victim = recordById[cloudlet.id] ?: return@mapNotNull null
            val vmIndex = vmReservations[cloudlet.id] ?: victim.assignedVmIndex ?: cloudlet.vm?.id?.toInt() ?: return@mapNotNull null
            if (!canPreempt(incoming, victim)) return@mapNotNull null
            RealtimePreemptionCandidate(
                victimCloudletId = CloudletId(victim.cloudletId),
                victimVmIndex = VmIndex(vmIndex),
                victimPriority = victim.priority,
                victimDeadline = victim.deadline,
                preemptedCount = victim.preemptedCount
            )
        }.sortedWith(candidateComparator(incoming))
    }

    fun decide(
        incoming: RealtimeTaskRecord,
        candidates: List<RealtimePreemptionCandidate>
    ): PreemptionDecision {
        if (!scheduling.preemptionEnabled) return PreemptionDecision.None
        val selected = candidates.firstOrNull() ?: return PreemptionDecision.None
        return PreemptionDecision.Preempt(
            victimCloudletId = selected.victimCloudletId,
            victimVmIndex = selected.victimVmIndex,
            delay = scheduling.preemptionDelay,
            penalty = scheduling.preemptionPenalty
        )
    }

    private fun canPreempt(incoming: RealtimeTaskRecord, victim: RealtimeTaskRecord): Boolean {
        if (victim.preemptedCount >= scheduling.preemptionMaxPerTask) return false
        val priorityGap = victim.priority - incoming.priority
        val priorityAllows = priorityGap >= scheduling.preemptionMinPriorityGap
        val incomingDeadline = incoming.deadline
        val victimDeadline = victim.deadline
        val deadlineAllows = incomingDeadline != null && victimDeadline != null && incomingDeadline < victimDeadline
        return priorityAllows || deadlineAllows
    }

    private fun candidateComparator(incoming: RealtimeTaskRecord): Comparator<RealtimePreemptionCandidate> {
        val policy = scheduling.normalizedPreemptionPolicy()
        return when (policy) {
            RealtimePreemptionPolicy.PRIORITY_THEN_DEADLINE -> compareByDescending<RealtimePreemptionCandidate> {
                it.victimPriority - incoming.priority
            }.thenBy { it.victimDeadline ?: Double.POSITIVE_INFINITY }
            RealtimePreemptionPolicy.DEADLINE_THEN_PRIORITY -> compareBy<RealtimePreemptionCandidate> {
                it.victimDeadline ?: Double.POSITIVE_INFINITY
            }.thenByDescending { it.victimPriority - incoming.priority }
        }.thenBy { it.preemptedCount }
    }
}
