package broker

import config.RealtimePreemptionPolicy
import config.RealtimeSchedulingConfig
import org.cloudsimplus.cloudlets.Cloudlet
import scheduler.CloudletId
import scheduler.RealtimeNodeState
import scheduler.RealtimePreemptionCandidate
import scheduler.RealtimeTaskRecord
import scheduler.RealtimeTenantFairnessSnapshot
import scheduler.TenantId
import scheduler.VmIndex
import kotlin.math.pow

enum class RealtimeRejectReason {
    QUEUE,
    CAPACITY,
    RESOURCE,
    DEADLINE,
    DEPENDENCY,
    TENANT_QUOTA,
    TENANT_BUDGET,
}

sealed interface AdmissionDecision {
    data object Accepted : AdmissionDecision

    data class Rejected(
        val reason: RealtimeRejectReason,
    ) : AdmissionDecision
}

class RealtimeAdmissionController(
    private val scheduling: RealtimeSchedulingConfig,
) {
    fun decide(
        activeCloudletCount: Int,
        nodeStates: List<RealtimeNodeState>,
    ): AdmissionDecision =
        when {
            activeCloudletCount >= scheduling.maxQueueSize ->
                AdmissionDecision.Rejected(RealtimeRejectReason.QUEUE)
            nodeStates.hasNoAcceptingWork() ->
                AdmissionDecision.Rejected(nodeStates.rejectReasonForNoAcceptingWork())
            else -> AdmissionDecision.Accepted
        }

    private fun List<RealtimeNodeState>.hasNoAcceptingWork(): Boolean = isNotEmpty() && none { it.acceptingWork }

    private fun List<RealtimeNodeState>.rejectReasonForNoAcceptingWork(): RealtimeRejectReason =
        if (any { !it.resourceAcceptingWork }) {
            RealtimeRejectReason.RESOURCE
        } else {
            RealtimeRejectReason.CAPACITY
        }
}

sealed interface TenantAdmissionDecision {
    data object Accepted : TenantAdmissionDecision

    data class Rejected(
        val tenantId: TenantId,
        val reason: RealtimeRejectReason,
        val limit: Double,
    ) : TenantAdmissionDecision
}

class RealtimeTenantController(
    private val scheduling: RealtimeSchedulingConfig,
) {
    private val enabled: Boolean = scheduling.multiTenantEnabled
    private val tenantCount: Int = scheduling.tenantCount.coerceAtLeast(1)
    private val quotas: List<Int>? = scheduling.tenantQuota.takeIf { it.isNotEmpty() }
    private val weights: List<Double> =
        when {
            scheduling.tenantWeights.isEmpty() -> List(tenantCount) { DEFAULT_TENANT_WEIGHT }
            else -> scheduling.tenantWeights
        }
    private val costBudgets: List<Double>? = scheduling.tenantCostBudget.takeIf { it.isNotEmpty() }
    private val admission =
        RealtimeTenantAdmissionPolicy(
            scheduling = scheduling,
            enabled = enabled,
            quotas = quotas,
            costBudgets = costBudgets,
        )
    private val metrics =
        RealtimeTenantMetricsView(
            scheduling = scheduling,
            tenantCount = tenantCount,
            weights = weights,
            quotas = quotas,
            costBudgets = costBudgets,
        )

    fun tenantFor(
        cloudletId: CloudletId,
        sampler: (CloudletId, Int, Int) -> Double,
    ): TenantId {
        if (!enabled) return TenantId(0)
        val index = (sampler(cloudletId, 0, TENANT_ASSIGNMENT_SALT) * tenantCount).toInt().coerceIn(0, tenantCount - 1)
        return TenantId(index)
    }

    fun decide(
        incoming: RealtimeTaskRecord,
        activeRecords: List<RealtimeTaskRecord>,
    ): TenantAdmissionDecision = admission.decide(incoming, activeRecords)

    fun snapshots(
        records: List<RealtimeTaskRecord>,
        completedCloudlets: List<Cloudlet> = emptyList(),
    ): List<RealtimeTenantFairnessSnapshot> = metrics.snapshots(records, completedCloudlets)

    fun fairnessIndex(
        completedCloudlets: List<Cloudlet>,
        records: List<RealtimeTaskRecord>,
    ): Double = metrics.fairnessIndex(completedCloudlets, records)

    fun dominantResourceFairnessIndex(records: List<RealtimeTaskRecord>): Double = metrics.dominantIndex(records)

    fun fairnessViolationCount(records: List<RealtimeTaskRecord>): Int = metrics.fairnessViolationCount(records)

    fun tenantSlaPenalty(
        cloudlets: List<Cloudlet>,
        records: List<RealtimeTaskRecord>,
    ): Double = metrics.tenantSlaPenalty(cloudlets, records)

    fun costSlaTradeoffScore(
        cost: Double,
        tenantSlaPenalty: Double,
    ): Double = metrics.costSlaTradeoffScore(cost, tenantSlaPenalty)

    fun retrySuccessByTenant(
        cloudlets: List<Cloudlet>,
        records: List<RealtimeTaskRecord>,
    ): Double = metrics.retrySuccessByTenant(cloudlets, records)
}

sealed interface FailureDecision {
    data object Continue : FailureDecision

    data class Retry(
        val delay: Double,
    ) : FailureDecision

    data object PermanentlyFail : FailureDecision
}

class RealtimeFailureController(
    private val scheduling: RealtimeSchedulingConfig,
    private val unitSampler: (CloudletId, Int, Int) -> Double,
) {
    fun submitAttempt(
        cloudletId: CloudletId,
        attempt: Int,
        failurePressure: Double,
    ): FailureDecision =
        when {
            !failedAttempt(cloudletId, attempt, failurePressure) -> FailureDecision.Continue
            attempt < scheduling.retryLimit -> {
                FailureDecision.Retry(retryDelay(attempt))
            }
            else -> FailureDecision.PermanentlyFail
        }

    private fun failedAttempt(
        cloudletId: CloudletId,
        attempt: Int,
        failurePressure: Double,
    ): Boolean {
        val rate = effectiveFailureRate(failurePressure)
        return rate > 0.0 && (rate >= 1.0 || unitSampler(cloudletId, attempt, FAILURE_ATTEMPT_SALT) < rate)
    }

    private fun effectiveFailureRate(failurePressure: Double): Double =
        (
            scheduling.failureRate +
                failurePressure * scheduling.overloadFailureMultiplier
        ).coerceIn(0.0, 1.0)

    fun retryDelay(attempt: Int): Double =
        if (scheduling.retryDelay <= 0.0) {
            0.0
        } else {
            scheduling.retryDelay * scheduling.retryBackoffMultiplier.pow(attempt.toDouble())
        }
}

data class TimeoutDecision(
    val action: config.RealtimeTimeoutAction,
)

class RealtimeTimeoutController(
    private val scheduling: RealtimeSchedulingConfig,
) {
    fun decide(): TimeoutDecision = TimeoutDecision(scheduling.normalizedTimeoutAction())
}

sealed interface PreemptionDecision {
    data object None : PreemptionDecision

    data class Preempt(
        val victimCloudletId: CloudletId,
        val victimVmIndex: VmIndex,
        val delay: Double,
        val penalty: Double,
    ) : PreemptionDecision
}

class RealtimePreemptionController(
    private val scheduling: RealtimeSchedulingConfig,
) {
    fun candidates(
        incoming: RealtimeTaskRecord,
        activeCloudlets: List<Cloudlet>,
        records: List<RealtimeTaskRecord>,
        vmReservations: Map<Long, Int>,
    ): List<RealtimePreemptionCandidate> =
        if (scheduling.preemptionEnabled) {
            val recordById = records.associateBy { it.cloudletId }
            activeCloudlets
                .mapNotNull { cloudlet ->
                    val victim = recordById[cloudlet.id] ?: return@mapNotNull null
                    val vmIndex = victimVmIndex(cloudlet, victim, vmReservations) ?: return@mapNotNull null
                    if (!canPreempt(incoming, victim)) return@mapNotNull null
                    RealtimePreemptionCandidate(
                        victimCloudletId = CloudletId(victim.cloudletId),
                        victimVmIndex = VmIndex(vmIndex),
                        victimPriority = victim.priority,
                        victimDeadline = victim.deadline,
                        preemptedCount = victim.preemptedCount,
                    )
                }.sortedWith(candidateComparator(incoming))
        } else {
            emptyList()
        }

    private fun victimVmIndex(
        cloudlet: Cloudlet,
        victim: RealtimeTaskRecord,
        vmReservations: Map<Long, Int>,
    ): Int? =
        vmReservations[cloudlet.id]
            ?: victim.assignedVmIndex
            ?: cloudlet.vm?.id?.toInt()

    fun decide(candidates: List<RealtimePreemptionCandidate>): PreemptionDecision =
        if (scheduling.preemptionEnabled) {
            candidates
                .firstOrNull()
                ?.let { selected ->
                    PreemptionDecision.Preempt(
                        victimCloudletId = selected.victimCloudletId,
                        victimVmIndex = selected.victimVmIndex,
                        delay = scheduling.preemptionDelay,
                        penalty = scheduling.preemptionPenalty,
                    )
                } ?: PreemptionDecision.None
        } else {
            PreemptionDecision.None
        }

    private fun canPreempt(
        incoming: RealtimeTaskRecord,
        victim: RealtimeTaskRecord,
    ): Boolean {
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
            RealtimePreemptionPolicy.PRIORITY_THEN_DEADLINE ->
                compareByDescending<RealtimePreemptionCandidate> {
                    it.victimPriority - incoming.priority
                }.thenBy { it.victimDeadline ?: Double.POSITIVE_INFINITY }
            RealtimePreemptionPolicy.DEADLINE_THEN_PRIORITY ->
                compareBy<RealtimePreemptionCandidate> {
                    it.victimDeadline ?: Double.POSITIVE_INFINITY
                }.thenByDescending { it.victimPriority - incoming.priority }
        }.thenBy { it.preemptedCount }
    }
}
