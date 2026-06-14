package broker

import config.RealtimeSchedulingConfig
import config.RealtimeTenantFairnessPolicy
import config.TenantSchedulingPolicy
import org.cloudsimplus.cloudlets.Cloudlet
import scheduler.RealtimeTaskRecord
import scheduler.RealtimeTenantFairnessSnapshot
import scheduler.TenantId

internal class RealtimeTenantAdmissionPolicy(
    private val scheduling: RealtimeSchedulingConfig,
    private val enabled: Boolean,
    private val quotas: List<Int>?,
    private val costBudgets: List<Double>?,
) {
    fun decide(
        incoming: RealtimeTaskRecord,
        activeRecords: List<RealtimeTaskRecord>,
    ): TenantAdmissionDecision =
        if (!enabled) {
            TenantAdmissionDecision.Accepted
        } else {
            quotaRejection(incoming, activeRecords)
                ?: budgetRejection(incoming, activeRecords)
                ?: TenantAdmissionDecision.Accepted
        }

    private fun quotaRejection(
        incoming: RealtimeTaskRecord,
        activeRecords: List<RealtimeTaskRecord>,
    ): TenantAdmissionDecision.Rejected? {
        val activeCount = activeTenantCount(incoming, activeRecords)
        val allowedQuota = quotas?.getOrNull(incoming.tenantId.value)?.plus(scheduling.tenantBurstAllowance)
        return allowedQuota
            ?.takeIf { activeCount >= it }
            ?.let { quota ->
                TenantAdmissionDecision.Rejected(
                    tenantId = incoming.tenantId,
                    reason = RealtimeRejectReason.TENANT_QUOTA,
                    limit = quota.toDouble(),
                )
            }
    }

    private fun budgetRejection(
        incoming: RealtimeTaskRecord,
        activeRecords: List<RealtimeTaskRecord>,
    ): TenantAdmissionDecision.Rejected? =
        costBudgets
            ?.getOrNull(incoming.tenantId.value)
            ?.let { budget ->
                budgetViolation(incoming, activeRecords, budget)
            }

    private fun budgetViolation(
        incoming: RealtimeTaskRecord,
        activeRecords: List<RealtimeTaskRecord>,
        budget: Double,
    ): TenantAdmissionDecision.Rejected? {
        val activeCost =
            activeRecords
                .filter { it.cloudletId != incoming.cloudletId && it.tenantId == incoming.tenantId }
                .sumOf(RealtimeTenantFairness::estimatedTaskCost)
        return budget
            .takeIf { activeCost + RealtimeTenantFairness.estimatedTaskCost(incoming) > it }
            ?.let {
                TenantAdmissionDecision.Rejected(
                    tenantId = incoming.tenantId,
                    reason = RealtimeRejectReason.TENANT_BUDGET,
                    limit = it,
                )
            }
    }

    private fun activeTenantCount(
        incoming: RealtimeTaskRecord,
        activeRecords: List<RealtimeTaskRecord>,
    ): Int =
        activeRecords.count {
            it.cloudletId != incoming.cloudletId &&
                it.tenantId == incoming.tenantId &&
                it.lifecycle.isActiveForTenantQuota()
        }
}

internal class RealtimeTenantMetricsView(
    private val scheduling: RealtimeSchedulingConfig,
    private val tenantCount: Int,
    private val weights: List<Double>,
    private val quotas: List<Int>?,
    private val costBudgets: List<Double>?,
) {
    private val enabled: Boolean = scheduling.multiTenantEnabled
    private val policy: RealtimeTenantFairnessPolicy = scheduling.normalizedTenantFairnessPolicy()
    private val schedulingPolicy: TenantSchedulingPolicy = scheduling.normalizedTenantSchedulingPolicy()

    fun snapshots(
        records: List<RealtimeTaskRecord>,
        completedCloudlets: List<Cloudlet> = emptyList(),
    ): List<RealtimeTenantFairnessSnapshot> =
        RealtimeTenantAggregates
            .build(records, completedCloudlets, scheduling.tenantSlaPenaltyWeight)
            .let { aggregates ->
                (0 until tenantCount).map { index -> snapshot(index, aggregates) }
            }

    fun fairnessIndex(
        completedCloudlets: List<Cloudlet>,
        records: List<RealtimeTaskRecord>,
    ): Double =
        if (!enabled) {
            DEFAULT_FAIRNESS_INDEX
        } else {
            val recordById = records.associateBy { it.cloudletId }
            val completedByTenant = RealtimeTenantAggregates.successfulCompletedByTenant(completedCloudlets, recordById)
            val counts = (0 until tenantCount).map { (completedByTenant[it] ?: 0).toDouble() }
            RealtimeTenantFairness.jainsIndex(counts)
        }

    fun dominantIndex(records: List<RealtimeTaskRecord>): Double =
        if (enabled) {
            val shares =
                (0 until tenantCount).map { tenant ->
                    RealtimeTenantFairness.dominantResourceShare(records.filter { it.tenantId.value == tenant })
                }
            RealtimeTenantFairness.jainsIndex(shares)
        } else {
            DEFAULT_FAIRNESS_INDEX
        }

    fun fairnessViolationCount(records: List<RealtimeTaskRecord>): Int =
        if (enabled) {
            val snapshot = snapshots(records)
            val maxPressure = snapshot.maxOfOrNull { it.fairnessPressure } ?: 0.0
            snapshot.takeIf { maxPressure > 0.0 }?.count { maxPressure - it.fairnessPressure > 1.0 } ?: 0
        } else {
            0
        }

    fun tenantSlaPenalty(
        cloudlets: List<Cloudlet>,
        records: List<RealtimeTaskRecord>,
    ): Double = snapshots(records, cloudlets).sumOf { it.slaPenalty }

    fun costSlaTradeoffScore(
        cost: Double,
        tenantSlaPenalty: Double,
    ): Double = cost + tenantSlaPenalty

    fun retrySuccessByTenant(
        cloudlets: List<Cloudlet>,
        records: List<RealtimeTaskRecord>,
    ): Double =
        if (enabled) {
            val successById =
                cloudlets
                    .filter { it.status == Cloudlet.Status.SUCCESS }
                    .map { it.id }
                    .toSet()
            val rates = RealtimeTenantAggregates.retrySuccessRates(records, successById)
            rates.takeIf { it.isNotEmpty() }?.average() ?: DEFAULT_FAIRNESS_INDEX
        } else {
            DEFAULT_FAIRNESS_INDEX
        }

    private fun snapshot(
        index: Int,
        aggregates: TenantFairnessAggregates,
    ): RealtimeTenantFairnessSnapshot {
        val activeCount = aggregates.activeByTenant[index] ?: 0
        val completedCount = aggregates.completedByTenant[index] ?: 0
        val weight = weights.getOrNull(index) ?: DEFAULT_TENANT_WEIGHT
        val dominantShare = aggregates.resourceByTenant[index] ?: 0.0
        val budgetUsed = aggregates.budgetByTenant[index] ?: 0.0
        val budgetLimit = costBudgets?.getOrNull(index)
        val slaPenalty = aggregates.slaPenaltyByTenant[index] ?: 0.0
        val pressureInput =
            TenantFairnessPressureInput(
                activity =
                    TenantActivityPressure(
                        activeCount = activeCount,
                        completedCount = completedCount,
                        weight = weight,
                    ),
                resource =
                    TenantResourcePressure(
                        dominantResourceShare = dominantShare,
                        budgetUsed = budgetUsed,
                        budgetLimit = budgetLimit,
                        slaPenalty = slaPenalty,
                    ),
            )
        return RealtimeTenantFairnessSnapshot(
            tenantId = TenantId(index),
            activeCount = activeCount,
            completedCount = completedCount,
            quota = quotas?.getOrNull(index),
            weight = weight,
            fairnessScore = fairnessScore(activeCount, completedCount, weight),
            dominantResourceShare = dominantShare,
            budgetUsed = budgetUsed,
            budgetLimit = budgetLimit,
            slaPenalty = slaPenalty,
            fairnessPressure =
                RealtimeTenantFairness.pressure(
                    schedulingPolicy,
                    scheduling.tenantSlaPenaltyWeight,
                    pressureInput,
                ),
        )
    }

    private fun fairnessScore(
        activeCount: Int,
        completedCount: Int,
        weight: Double,
    ): Double =
        when (policy) {
            RealtimeTenantFairnessPolicy.QUOTA_FIRST -> activeCount.toDouble()
            RealtimeTenantFairnessPolicy.WEIGHTED_FAIR -> (activeCount + completedCount).toDouble() / weight
        }
}
