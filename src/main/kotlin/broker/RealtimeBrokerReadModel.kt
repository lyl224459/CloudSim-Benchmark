package broker

import config.RealtimeSchedulingConfig
import org.cloudsimplus.cloudlets.Cloudlet
import scheduler.RealtimeCandidateScoreRecord
import scheduler.RealtimeObservationEventRecord
import scheduler.RealtimeTaskLifecycle
import scheduler.RealtimeTaskMetadata
import scheduler.RealtimeTopologyMetrics
import scheduler.RealtimeTopologyModel
import scheduler.RealtimeVmLifecycleManager

internal data class RealtimeBrokerReadDependencies(
    val scheduling: RealtimeSchedulingConfig,
    val arrivalState: RealtimeArrivalState,
    val lifecycleStore: RealtimeTaskLifecycleStore,
    val reservationState: RealtimeReservationState,
    val metrics: RealtimeBrokerMetrics,
    val vmLifecycleManager: RealtimeVmLifecycleManager,
    val tenantController: RealtimeTenantController,
    val topologyModel: RealtimeTopologyModel,
)

@Suppress("TooManyFunctions") // Read facade intentionally mirrors the broker's stable metrics/query API.
internal class RealtimeBrokerReadModel(
    dependencies: RealtimeBrokerReadDependencies,
) {
    private val tasks = RealtimeBrokerTaskReadView(dependencies)
    private val scalarMetrics = RealtimeBrokerScalarMetricsView(dependencies.metrics, dependencies.vmLifecycleManager)
    private val tenantTopology = RealtimeBrokerTenantTopologyView(dependencies)

    fun waitingCloudlets(): List<Cloudlet> = tasks.waitingCloudlets()

    fun activeCloudlets(): List<Cloudlet> = tasks.activeCloudlets()

    fun rejectedCount(): Int = scalarMetrics.rejectedCount()

    fun capacityRejectedCount(): Int = scalarMetrics.capacityRejectedCount()

    fun resourceRejectedCount(): Int = scalarMetrics.resourceRejectedCount()

    fun deadlineRejectedCount(): Int = scalarMetrics.deadlineRejectedCount()

    fun deadlineDegradedCount(): Int = scalarMetrics.deadlineDegradedCount()

    fun deadlineRetryLaterCount(): Int = scalarMetrics.deadlineRetryLaterCount()

    fun deadlineMissAcceptedCount(): Int = scalarMetrics.deadlineMissAcceptedCount()

    fun dependencyBlockedCount(): Int = scalarMetrics.dependencyBlockedCount()

    fun dependencyReleasedCount(): Int = scalarMetrics.dependencyReleasedCount()

    fun dependencyRejectedCount(): Int = scalarMetrics.dependencyRejectedCount()

    fun rescheduleAttemptCount(): Int = scalarMetrics.rescheduleAttemptCount()

    fun rescheduleSuccessCount(): Int = scalarMetrics.rescheduleSuccessCount()

    fun rescheduleFailureCount(): Int = scalarMetrics.rescheduleFailureCount()

    fun averageRescheduleDelay(): Double = scalarMetrics.averageRescheduleDelay()

    fun tenantQuotaRejectedCount(): Int = scalarMetrics.tenantQuotaRejectedCount()

    fun tenantBudgetRejectedCount(): Int = scalarMetrics.tenantBudgetRejectedCount()

    fun submittedCount(): Int = scalarMetrics.submittedCount()

    fun retryCount(): Int = scalarMetrics.retryCount()

    fun retrySuccessCount(): Int = scalarMetrics.retrySuccessCount()

    fun permanentFailedCount(): Int = scalarMetrics.permanentFailedCount()

    fun runtimeFailureCount(): Int = scalarMetrics.runtimeFailureCount()

    fun timeoutCancelledCount(): Int = scalarMetrics.timeoutCancelledCount()

    fun migrationCount(): Int = scalarMetrics.migrationCount()

    fun checkpointRecoveryCount(): Int = scalarMetrics.checkpointRecoveryCount()

    fun scaleOutCount(): Int = scalarMetrics.scaleOutCount()

    fun scaleInCount(): Int = scalarMetrics.scaleInCount()

    fun activeVmPeak(): Int = scalarMetrics.activeVmPeak()

    fun autoscalingCost(): Double = scalarMetrics.autoscalingCost()

    fun coldStartDelayTotal(): Double = scalarMetrics.coldStartDelayTotal()

    fun averageAutoscalingPressure(): Double = scalarMetrics.averageAutoscalingPressure()

    fun averageDeadlineSlackPressure(): Double = scalarMetrics.averageDeadlineSlackPressure()

    fun averageArrivalRatePressure(): Double = scalarMetrics.averageArrivalRatePressure()

    fun scaleCooldownSkippedCount(): Int = scalarMetrics.scaleCooldownSkippedCount()

    fun warmPoolHitRate(): Double = scalarMetrics.warmPoolHitRate()

    fun scaleInDrainCount(): Int = scalarMetrics.scaleInDrainCount()

    fun autoscalingVmSeconds(): Double = scalarMetrics.autoscalingVmSeconds()

    fun averageDecisionDelay(): Double = scalarMetrics.averageDecisionDelay()

    fun averageQueueDepth(): Double = scalarMetrics.averageQueueDepth()

    fun maxQueueDepth(): Int = scalarMetrics.maxQueueDepth()

    fun averageRealtimeScore(): Double = scalarMetrics.averageRealtimeScore()

    fun averageSelectedLatenessPenalty(): Double = scalarMetrics.averageSelectedLatenessPenalty()

    fun averageSelectedDeadlineSlack(): Double = scalarMetrics.averageSelectedDeadlineSlack()

    fun averageCandidateScoreSpread(): Double = scalarMetrics.averageCandidateScoreSpread()

    fun averagePhysicalHostUtilization(): Double = scalarMetrics.averagePhysicalHostUtilization()

    fun averageHostResourceFragmentation(): Double = scalarMetrics.averageHostResourceFragmentation()

    fun averageNetworkTransferDelay(): Double = scalarMetrics.averageNetworkTransferDelay()

    fun imageCacheHitRate(): Double = scalarMetrics.imageCacheHitRate()

    fun averageNoisyNeighborPressure(): Double = scalarMetrics.averageNoisyNeighborPressure()

    fun candidateScoreRecords(): List<RealtimeCandidateScoreRecord> = scalarMetrics.candidateScoreRecords()

    fun observationEventRecords(): List<RealtimeObservationEventRecord> = scalarMetrics.observationEventRecords()

    fun taskMetadata(cloudlet: Cloudlet): RealtimeTaskMetadata? = tasks.taskMetadata(cloudlet)

    fun retrySuccessRate(): Double = scalarMetrics.retrySuccessRate()

    fun preemptedCount(): Int = scalarMetrics.preemptedCount()

    fun preemptionSuccessCount(): Int = scalarMetrics.preemptionSuccessCount()

    fun preemptionFailedCount(): Int = scalarMetrics.preemptionFailedCount()

    fun averagePreemptionDelay(): Double = scalarMetrics.averagePreemptionDelay()

    fun preemptionPenalty(): Double = scalarMetrics.preemptionPenalty()

    fun checkpointLossTotal(): Long = scalarMetrics.checkpointLossTotal()

    fun hostFailureCount(): Int = scalarMetrics.hostFailureCount()

    fun rackFailureCount(): Int = scalarMetrics.rackFailureCount()

    fun regionFailureCount(): Int = scalarMetrics.regionFailureCount()

    fun arrivalTime(cloudlet: Cloudlet): Double = tasks.arrivalTime(cloudlet)

    fun slaViolationCount(cloudlets: List<Cloudlet>): Int = tasks.slaViolationCount(cloudlets)

    fun tenantFairnessIndex(cloudlets: List<Cloudlet>): Double = tenantTopology.tenantFairnessIndex(cloudlets)

    fun dominantResourceFairnessIndex(): Double = tenantTopology.dominantResourceFairnessIndex()

    fun fairnessViolationCount(): Int = tenantTopology.fairnessViolationCount()

    fun tenantSlaPenalty(cloudlets: List<Cloudlet>): Double = tenantTopology.tenantSlaPenalty(cloudlets)

    fun costSlaTradeoffScore(
        cost: Double,
        tenantSlaPenalty: Double,
    ): Double = tenantTopology.costSlaTradeoffScore(cost, tenantSlaPenalty)

    fun retrySuccessByTenant(cloudlets: List<Cloudlet>): Double = tenantTopology.retrySuccessByTenant(cloudlets)

    fun topologyMetrics(cloudlets: List<Cloudlet>): RealtimeTopologyMetrics = tenantTopology.topologyMetrics(cloudlets)

    fun timeoutCount(timeoutSeconds: Double): Int = tasks.timeoutCount(timeoutSeconds)
}

internal fun RealtimeTaskLifecycle.isActiveForRealtimeBroker(): Boolean =
    this == RealtimeTaskLifecycle.PENDING_DECISION ||
        this == RealtimeTaskLifecycle.SUBMITTED ||
        this == RealtimeTaskLifecycle.RUNNING ||
        this == RealtimeTaskLifecycle.PREEMPTED ||
        this == RealtimeTaskLifecycle.MIGRATING ||
        this == RealtimeTaskLifecycle.RETRYING
