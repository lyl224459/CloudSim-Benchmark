package broker

import config.RealtimeSchedulingConfig
import org.cloudsimplus.cloudlets.Cloudlet
import scheduler.RealtimeTaskLifecycle
import scheduler.RealtimeTaskMetadata
import scheduler.RealtimeTopologyMetrics
import scheduler.RealtimeTopologyModel
import scheduler.RealtimeVmLifecycleManager

internal class RealtimeBrokerReadModel(
    private val scheduling: RealtimeSchedulingConfig,
    private val arrivalState: RealtimeArrivalState,
    private val lifecycleStore: RealtimeTaskLifecycleStore,
    private val reservationState: RealtimeReservationState,
    private val metrics: RealtimeBrokerMetrics,
    private val vmLifecycleManager: RealtimeVmLifecycleManager,
    private val tenantController: RealtimeTenantController,
    private val topologyModel: RealtimeTopologyModel,
) {
    fun waitingCloudlets(): List<Cloudlet> = arrivalState.waitingCloudletsSnapshot()

    fun activeCloudlets(): List<Cloudlet> {
        pruneCompletedReservations()
        return (arrivalState.pendingCloudletsSnapshot() + waitingCloudlets())
            .distinctBy { it.id }
            .filter { it.status != Cloudlet.Status.FAILED }
    }

    fun rejectedCount(): Int = metrics.rejectedCount

    fun capacityRejectedCount(): Int = metrics.capacityRejectedCount

    fun resourceRejectedCount(): Int = metrics.resourceRejectedCount

    fun tenantQuotaRejectedCount(): Int = metrics.tenantQuotaRejectedCount

    fun tenantBudgetRejectedCount(): Int = metrics.tenantBudgetRejectedCount

    fun submittedCount(): Int = metrics.submittedCount

    fun retryCount(): Int = metrics.retryCount

    fun retrySuccessCount(): Int = metrics.retrySuccessCount

    fun permanentFailedCount(): Int = metrics.permanentFailedCount

    fun runtimeFailureCount(): Int = metrics.runtimeFailureCount

    fun timeoutCancelledCount(): Int = metrics.timeoutCancelledCount

    fun migrationCount(): Int = metrics.migrationCount

    fun checkpointRecoveryCount(): Int = metrics.checkpointRecoveryCount

    fun scaleOutCount(): Int = vmLifecycleManager.getScaleOutCount()

    fun scaleInCount(): Int = vmLifecycleManager.getScaleInCount()

    fun activeVmPeak(): Int = vmLifecycleManager.getActiveVmPeak()

    fun autoscalingCost(): Double = vmLifecycleManager.getAutoscalingCost()

    fun coldStartDelayTotal(): Double = vmLifecycleManager.getColdStartDelayTotal()

    fun averageDecisionDelay(): Double = metrics.averageDecisionDelay

    fun averageQueueDepth(): Double = metrics.averageQueueDepth

    fun maxQueueDepth(): Int = metrics.maxQueueDepth

    fun taskMetadata(cloudlet: Cloudlet): RealtimeTaskMetadata? = lifecycleStore.get(cloudlet.id)

    fun retrySuccessRate(): Double = metrics.retrySuccessRate

    fun preemptedCount(): Int = metrics.preemptedCount

    fun preemptionSuccessCount(): Int = metrics.preemptionSuccessCount

    fun preemptionFailedCount(): Int = metrics.preemptionFailedCount

    fun averagePreemptionDelay(): Double = metrics.averagePreemptionDelay

    fun preemptionPenalty(): Double = metrics.preemptionPenalty

    fun checkpointLossTotal(): Long = metrics.checkpointLoss

    fun hostFailureCount(): Int = metrics.hostFailureCount

    fun rackFailureCount(): Int = metrics.rackFailureCount

    fun regionFailureCount(): Int = metrics.regionFailureCount

    fun arrivalTime(cloudlet: Cloudlet): Double = arrivalState.arrivalTimeOf(cloudlet)

    fun slaViolationCount(cloudlets: List<Cloudlet>): Int {
        if (scheduling.deadlineFactor <= 0.0) return 0
        return cloudlets.count { cloudlet ->
            cloudlet.status == Cloudlet.Status.SUCCESS &&
                lifecycleStore.get(cloudlet.id)?.deadline?.let { deadline -> cloudlet.finishTime > deadline } == true
        }
    }

    fun tenantFairnessIndex(cloudlets: List<Cloudlet>): Double {
        val records = lifecycleStore.snapshot()
        return tenantController.fairnessIndex(cloudlets, records)
    }

    fun dominantResourceFairnessIndex(): Double {
        val records = lifecycleStore.snapshot()
        return tenantController.dominantResourceFairnessIndex(records)
    }

    fun fairnessViolationCount(): Int = tenantController.fairnessViolationCount(lifecycleStore.snapshot())

    fun tenantSlaPenalty(cloudlets: List<Cloudlet>): Double {
        val records = lifecycleStore.snapshot()
        return tenantController.tenantSlaPenalty(cloudlets, records)
    }

    fun costSlaTradeoffScore(
        cost: Double,
        tenantSlaPenalty: Double,
    ): Double = tenantController.costSlaTradeoffScore(cost, tenantSlaPenalty)

    fun retrySuccessByTenant(cloudlets: List<Cloudlet>): Double =
        tenantController.retrySuccessByTenant(cloudlets, lifecycleStore.snapshot())

    fun topologyMetrics(cloudlets: List<Cloudlet>): RealtimeTopologyMetrics =
        topologyModel.metricsFor(
            cloudlets.mapNotNull { cloudlet ->
                lifecycleStore.get(cloudlet.id)?.assignedVmIndex
                    ?: reservationState.assignedVmIndexOf(cloudlet)
                    ?: cloudlet.vm?.id?.toInt()
            },
        )

    fun timeoutCount(timeoutSeconds: Double): Int {
        if (timeoutSeconds <= 0.0) return 0
        return waitingCloudlets().count { cloudlet ->
            val elapsed =
                if (cloudlet.status == Cloudlet.Status.SUCCESS) {
                    cloudlet.finishTime - arrivalTime(cloudlet)
                } else {
                    Double.POSITIVE_INFINITY
                }
            elapsed > timeoutSeconds
        }
    }

    private fun pruneCompletedReservations() {
        reservationState.prune { cloudletId -> lifecycleStore.get(cloudletId)?.lifecycle }
    }
}

internal fun RealtimeTaskLifecycle.isActiveForRealtimeBroker(): Boolean =
    this == RealtimeTaskLifecycle.PENDING_DECISION ||
        this == RealtimeTaskLifecycle.SUBMITTED ||
        this == RealtimeTaskLifecycle.RUNNING ||
        this == RealtimeTaskLifecycle.PREEMPTED ||
        this == RealtimeTaskLifecycle.MIGRATING ||
        this == RealtimeTaskLifecycle.RETRYING
