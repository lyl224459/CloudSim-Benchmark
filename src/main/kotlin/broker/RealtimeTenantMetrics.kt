package broker

import config.TenantSchedulingPolicy
import org.cloudsimplus.cloudlets.Cloudlet
import scheduler.RealtimeTaskLifecycle
import scheduler.RealtimeTaskRecord
import kotlin.math.abs

internal const val TENANT_ASSIGNMENT_SALT = 83
internal const val FAILURE_ATTEMPT_SALT = 29
internal const val DEFAULT_TENANT_WEIGHT = 1.0
internal const val DEFAULT_TASK_COST = 1.0
internal const val DEFAULT_FAIRNESS_INDEX = 1.0
internal const val ZERO_EPSILON = 1e-12

internal data class TenantFairnessAggregates(
    val completedByTenant: Map<Int, Int>,
    val activeByTenant: Map<Int, Int>,
    val resourceByTenant: Map<Int, Double>,
    val budgetByTenant: Map<Int, Double>,
    val slaPenaltyByTenant: Map<Int, Double>,
)

internal data class TenantActivityPressure(
    val activeCount: Int,
    val completedCount: Int,
    val weight: Double,
)

internal data class TenantResourcePressure(
    val dominantResourceShare: Double,
    val budgetUsed: Double,
    val budgetLimit: Double?,
    val slaPenalty: Double,
)

internal data class TenantFairnessPressureInput(
    val activity: TenantActivityPressure,
    val resource: TenantResourcePressure,
)

internal object RealtimeTenantAggregates {
    fun build(
        records: List<RealtimeTaskRecord>,
        completedCloudlets: List<Cloudlet>,
        penaltyWeight: Double,
    ): TenantFairnessAggregates {
        val recordById = records.associateBy { it.cloudletId }
        return TenantFairnessAggregates(
            completedByTenant = completedByTenant(completedCloudlets, recordById),
            activeByTenant = activeByTenant(records),
            resourceByTenant = resourceByTenant(records),
            budgetByTenant = budgetByTenant(records),
            slaPenaltyByTenant = slaPenaltyByTenant(completedCloudlets, recordById, penaltyWeight),
        )
    }

    fun successfulCompletedByTenant(
        completedCloudlets: List<Cloudlet>,
        recordById: Map<Long, RealtimeTaskRecord>,
    ): Map<Int, Int> =
        completedCloudlets
            .filter { it.status == Cloudlet.Status.SUCCESS }
            .mapNotNull { cloudlet -> recordById[cloudlet.id] }
            .groupingBy { it.tenantId.value }
            .eachCount()

    fun retrySuccessRates(
        records: List<RealtimeTaskRecord>,
        successById: Set<Long>,
    ): List<Double> =
        records
            .filter { it.attempt > 0 }
            .groupBy { it.tenantId.value }
            .values
            .map { tenantRecords ->
                tenantRecords.count { it.cloudletId in successById }.toDouble() / tenantRecords.size.toDouble()
            }

    private fun completedByTenant(
        completedCloudlets: List<Cloudlet>,
        recordById: Map<Long, RealtimeTaskRecord>,
    ): Map<Int, Int> =
        completedCloudlets
            .mapNotNull { cloudlet -> recordById[cloudlet.id] }
            .groupingBy { it.tenantId.value }
            .eachCount()

    private fun activeByTenant(records: List<RealtimeTaskRecord>): Map<Int, Int> =
        records
            .filter { it.lifecycle.isActiveForTenantQuota() }
            .groupingBy { it.tenantId.value }
            .eachCount()

    private fun resourceByTenant(records: List<RealtimeTaskRecord>): Map<Int, Double> =
        records
            .filter { it.lifecycle.isActiveForTenantQuota() }
            .groupBy { it.tenantId.value }
            .mapValues { (_, tenantRecords) -> RealtimeTenantFairness.dominantResourceShare(tenantRecords) }

    private fun budgetByTenant(records: List<RealtimeTaskRecord>): Map<Int, Double> =
        records
            .groupBy { it.tenantId.value }
            .mapValues { (_, tenantRecords) -> tenantRecords.sumOf(RealtimeTenantFairness::estimatedTaskCost) }

    private fun slaPenaltyByTenant(
        completedCloudlets: List<Cloudlet>,
        recordById: Map<Long, RealtimeTaskRecord>,
        penaltyWeight: Double,
    ): Map<Int, Double> =
        completedCloudlets
            .mapNotNull { cloudlet -> tenantSlaPenalty(cloudlet, recordById[cloudlet.id], penaltyWeight) }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, penalties) -> penalties.sum() }

    private fun tenantSlaPenalty(
        cloudlet: Cloudlet,
        record: RealtimeTaskRecord?,
        penaltyWeight: Double,
    ): Pair<Int, Double>? =
        record
            ?.deadline
            ?.takeIf { cloudlet.status == Cloudlet.Status.SUCCESS && cloudlet.finishTime > it }
            ?.let { deadline -> record.tenantId.value to (cloudlet.finishTime - deadline) * penaltyWeight }
}

internal object RealtimeTenantFairness {
    fun pressure(
        policy: TenantSchedulingPolicy,
        penaltyWeight: Double,
        input: TenantFairnessPressureInput,
    ): Double =
        when (policy) {
            TenantSchedulingPolicy.QUOTA_FIRST -> input.activity.activeCount.toDouble()
            TenantSchedulingPolicy.WEIGHTED_FAIR ->
                (input.activity.activeCount + input.activity.completedCount).toDouble() / input.activity.weight
            TenantSchedulingPolicy.DOMINANT_RESOURCE_FAIRNESS ->
                input.resource.dominantResourceShare +
                    budgetPressure(input.resource.budgetUsed, input.resource.budgetLimit) +
                    input.resource.slaPenalty * penaltyWeight
        }

    fun dominantResourceShare(records: List<RealtimeTaskRecord>): Double =
        records
            .takeIf { it.isNotEmpty() }
            ?.let(::dominantResourceShareForNonEmpty)
            ?: 0.0

    fun estimatedTaskCost(record: RealtimeTaskRecord): Double =
        listOfNotNull(record.requestedCpu, record.requestedRam, record.requestedBw, record.requestedIo)
            .sum()
            .takeIf { it > 0.0 }
            ?: DEFAULT_TASK_COST

    fun jainsIndex(values: List<Double>): Double =
        values
            .takeIf { it.isNotEmpty() }
            ?.let(::jainsIndexForNonEmpty)
            ?: DEFAULT_FAIRNESS_INDEX

    private fun budgetPressure(
        used: Double,
        limit: Double?,
    ): Double =
        if (limit == null || limit <= 0.0) {
            0.0
        } else {
            used / limit
        }

    private fun dominantResourceShareForNonEmpty(records: List<RealtimeTaskRecord>): Double {
        val cpu = records.sumOf { it.requestedCpu ?: 0.0 }
        val ram = records.sumOf { it.requestedRam ?: 0.0 }
        val bw = records.sumOf { it.requestedBw ?: 0.0 }
        val io = records.sumOf { it.requestedIo ?: 0.0 }
        val total = cpu + ram + bw + io
        return if (total <= 0.0) {
            records.size.toDouble()
        } else {
            maxOf(cpu, ram, bw, io) / total
        }
    }

    private fun jainsIndexForNonEmpty(values: List<Double>): Double {
        val normalized = values.map { if (abs(it) < ZERO_EPSILON) 0.0 else it }
        val sum = normalized.sum()
        val squareSum = normalized.sumOf { it * it }
        return if (sum <= 0.0 || squareSum <= 0.0) {
            DEFAULT_FAIRNESS_INDEX
        } else {
            (sum * sum) / (normalized.size.toDouble() * squareSum)
        }
    }
}

internal fun RealtimeTaskLifecycle.isActiveForTenantQuota(): Boolean =
    this == RealtimeTaskLifecycle.PENDING_DECISION ||
        this == RealtimeTaskLifecycle.SUBMITTED ||
        this == RealtimeTaskLifecycle.RUNNING ||
        this == RealtimeTaskLifecycle.PREEMPTED ||
        this == RealtimeTaskLifecycle.MIGRATING ||
        this == RealtimeTaskLifecycle.RETRYING
