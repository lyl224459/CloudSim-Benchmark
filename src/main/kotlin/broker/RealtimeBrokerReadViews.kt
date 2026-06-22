package broker

import org.cloudsimplus.cloudlets.Cloudlet
import scheduler.RealtimeCandidateScoreRecord
import scheduler.RealtimeTaskMetadata
import scheduler.RealtimeTopologyMetrics
import scheduler.RealtimeVmLifecycleManager

internal class RealtimeBrokerTaskReadView(
    private val dependencies: RealtimeBrokerReadDependencies,
) {
    fun waitingCloudlets(): List<Cloudlet> = dependencies.arrivalState.waitingCloudletsSnapshot()

    fun activeCloudlets(): List<Cloudlet> {
        dependencies.reservationState.prune { cloudletId -> dependencies.lifecycleStore.get(cloudletId)?.lifecycle }
        return (dependencies.arrivalState.pendingCloudletsSnapshot() + waitingCloudlets())
            .distinctBy { it.id }
            .filter { it.status != Cloudlet.Status.FAILED }
    }

    fun taskMetadata(cloudlet: Cloudlet): RealtimeTaskMetadata? = dependencies.lifecycleStore.get(cloudlet.id)

    fun arrivalTime(cloudlet: Cloudlet): Double = dependencies.arrivalState.arrivalTimeOf(cloudlet)

    fun slaViolationCount(cloudlets: List<Cloudlet>): Int {
        if (dependencies.scheduling.deadlineFactor <= 0.0) return 0
        return cloudlets.count { cloudlet ->
            cloudlet.status == Cloudlet.Status.SUCCESS &&
                dependencies.lifecycleStore
                    .get(cloudlet.id)
                    ?.deadline
                    ?.let { cloudlet.finishTime > it } == true
        }
    }

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
}

@Suppress("TooManyFunctions") // Scalar view deliberately groups broker counters behind one read facade.
internal class RealtimeBrokerScalarMetricsView(
    private val metrics: RealtimeBrokerMetrics,
    private val vmLifecycleManager: RealtimeVmLifecycleManager,
) {
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

    fun averageRealtimeScore(): Double = metrics.averageRealtimeScore

    fun averageSelectedLatenessPenalty(): Double = metrics.averageSelectedLatenessPenalty

    fun averageSelectedDeadlineSlack(): Double = metrics.averageSelectedDeadlineSlack

    fun averageCandidateScoreSpread(): Double = metrics.averageCandidateScoreSpread

    fun candidateScoreRecords(): List<RealtimeCandidateScoreRecord> = metrics.candidateScoreRecords()

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
}

internal class RealtimeBrokerTenantTopologyView(
    private val dependencies: RealtimeBrokerReadDependencies,
) {
    fun tenantFairnessIndex(cloudlets: List<Cloudlet>): Double =
        dependencies.tenantController.fairnessIndex(cloudlets, dependencies.lifecycleStore.snapshot())

    fun dominantResourceFairnessIndex(): Double =
        dependencies.tenantController.dominantResourceFairnessIndex(dependencies.lifecycleStore.snapshot())

    fun fairnessViolationCount(): Int =
        dependencies.tenantController.fairnessViolationCount(
            dependencies.lifecycleStore.snapshot(),
        )

    fun tenantSlaPenalty(cloudlets: List<Cloudlet>): Double =
        dependencies.tenantController.tenantSlaPenalty(cloudlets, dependencies.lifecycleStore.snapshot())

    fun costSlaTradeoffScore(
        cost: Double,
        tenantSlaPenalty: Double,
    ): Double = dependencies.tenantController.costSlaTradeoffScore(cost, tenantSlaPenalty)

    fun retrySuccessByTenant(cloudlets: List<Cloudlet>): Double =
        dependencies.tenantController.retrySuccessByTenant(cloudlets, dependencies.lifecycleStore.snapshot())

    fun topologyMetrics(cloudlets: List<Cloudlet>): RealtimeTopologyMetrics =
        dependencies.topologyModel.metricsFor(
            cloudlets.mapNotNull { cloudlet ->
                dependencies.lifecycleStore.get(cloudlet.id)?.assignedVmIndex
                    ?: dependencies.reservationState.assignedVmIndexOf(cloudlet)
                    ?: cloudlet.vm?.id?.toInt()
            },
        )
}
