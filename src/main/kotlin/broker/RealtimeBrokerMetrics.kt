package broker

import scheduler.RealtimeCandidateScoreRecord
import scheduler.RealtimeNodeState

data class RealtimeBrokerMetricsSnapshot(
    val rejectedCount: Int,
    val capacityRejectedCount: Int,
    val resourceRejectedCount: Int,
    val deadlineRejectedCount: Int,
    val deadlineDegradedCount: Int,
    val deadlineRetryLaterCount: Int,
    val deadlineMissAcceptedCount: Int,
    val dependencyBlockedCount: Int,
    val dependencyReleasedCount: Int,
    val dependencyRejectedCount: Int,
    val rescheduleAttemptCount: Int,
    val rescheduleSuccessCount: Int,
    val rescheduleFailureCount: Int,
    val averageRescheduleDelay: Double,
    val tenantQuotaRejectedCount: Int,
    val tenantBudgetRejectedCount: Int,
    val submittedCount: Int,
    val retryCount: Int,
    val retrySuccessCount: Int,
    val permanentFailedCount: Int,
    val runtimeFailureCount: Int,
    val timeoutCancelledCount: Int,
    val migrationCount: Int,
    val checkpointRecoveryCount: Int,
    val preemptedCount: Int,
    val preemptionSuccessCount: Int,
    val preemptionFailedCount: Int,
    val averagePreemptionDelay: Double,
    val preemptionPenaltyTotal: Double,
    val checkpointLossTotal: Long,
    val hostFailureCount: Int,
    val rackFailureCount: Int,
    val regionFailureCount: Int,
    val averageDecisionDelay: Double,
    val averageQueueDepth: Double,
    val maxQueueDepth: Int,
    val averageRealtimeScore: Double,
    val averageSelectedLatenessPenalty: Double,
    val averageSelectedDeadlineSlack: Double,
    val averageCandidateScoreSpread: Double,
    val averagePhysicalHostUtilization: Double,
    val averageHostResourceFragmentation: Double,
    val averageNetworkTransferDelay: Double,
    val imageCacheHitRate: Double,
    val averageNoisyNeighborPressure: Double,
)

@Suppress("TooManyFunctions") // Metrics facade keeps stable scalar and snapshot accessors in one type.
class RealtimeBrokerMetrics {
    var rejectedCount: Int = 0
        private set
    var capacityRejectedCount: Int = 0
        private set
    var resourceRejectedCount: Int = 0
        private set
    var deadlineRejectedCount: Int = 0
        private set
    var deadlineDegradedCount: Int = 0
        private set
    var deadlineRetryLaterCount: Int = 0
        private set
    var deadlineMissAcceptedCount: Int = 0
        private set
    var dependencyBlockedCount: Int = 0
        private set
    var dependencyReleasedCount: Int = 0
        private set
    var dependencyRejectedCount: Int = 0
        private set
    var rescheduleAttemptCount: Int = 0
        private set
    var rescheduleSuccessCount: Int = 0
        private set
    var rescheduleFailureCount: Int = 0
        private set
    private var rescheduleDelayTotal = 0.0
    var tenantQuotaRejectedCount: Int = 0
        private set
    var tenantBudgetRejectedCount: Int = 0
        private set
    var submittedCount: Int = 0
        private set
    var retryCount: Int = 0
        private set
    var retrySuccessCount: Int = 0
        private set
    var permanentFailedCount: Int = 0
        private set
    var runtimeFailureCount: Int = 0
        private set
    var timeoutCancelledCount: Int = 0
        private set
    var migrationCount: Int = 0
        private set
    var checkpointRecoveryCount: Int = 0
        private set
    var preemptedCount: Int = 0
        private set
    var preemptionSuccessCount: Int = 0
        private set
    var preemptionFailedCount: Int = 0
        private set
    private var preemptionDelayTotal = 0.0
    private var preemptionPenaltyTotal = 0.0
    private var checkpointLossTotal = 0L
    var hostFailureCount: Int = 0
        private set
    var rackFailureCount: Int = 0
        private set
    var regionFailureCount: Int = 0
        private set
    private var decisionDelayTotal = 0.0
    private var decisionCount = 0
    private var queueDepthSampleTotal = 0
    private var queueDepthSampleCount = 0
    var maxQueueDepth: Int = 0
        private set
    private var realtimeScoreTotal = 0.0
    private var selectedLatenessPenaltyTotal = 0.0
    private var selectedDeadlineSlackTotal = 0.0
    private var candidateScoreSpreadTotal = 0.0
    private var candidateScoreDecisionCount = 0
    private val candidateScoreRecords = mutableListOf<RealtimeCandidateScoreRecord>()
    private var physicalHostUtilizationTotal = 0.0
    private var hostResourceFragmentationTotal = 0.0
    private var networkTransferDelayTotal = 0.0
    private var noisyNeighborPressureTotal = 0.0
    private var placementMetricCount = 0
    private var imageCacheHitCount = 0
    private var imageCacheDecisionCount = 0

    val averageDecisionDelay: Double
        get() = if (decisionCount > 0) decisionDelayTotal / decisionCount else 0.0
    val averageQueueDepth: Double
        get() = if (queueDepthSampleCount > 0) queueDepthSampleTotal.toDouble() / queueDepthSampleCount else 0.0
    val averagePreemptionDelay: Double
        get() = if (preemptedCount > 0) preemptionDelayTotal / preemptedCount else 0.0
    val preemptionPenalty: Double get() = preemptionPenaltyTotal
    val checkpointLoss: Long get() = checkpointLossTotal
    val retrySuccessRate: Double
        get() = if (retryCount > 0) retrySuccessCount.toDouble() / retryCount else 0.0
    val averageRescheduleDelay: Double
        get() = if (rescheduleSuccessCount > 0) rescheduleDelayTotal / rescheduleSuccessCount else 0.0
    val averageRealtimeScore: Double
        get() = if (candidateScoreDecisionCount > 0) realtimeScoreTotal / candidateScoreDecisionCount else 0.0
    val averageSelectedLatenessPenalty: Double
        get() =
            if (candidateScoreDecisionCount > 0) {
                selectedLatenessPenaltyTotal / candidateScoreDecisionCount
            } else {
                0.0
            }
    val averageSelectedDeadlineSlack: Double
        get() =
            if (candidateScoreDecisionCount > 0) {
                selectedDeadlineSlackTotal / candidateScoreDecisionCount
            } else {
                0.0
            }
    val averageCandidateScoreSpread: Double
        get() =
            if (candidateScoreDecisionCount > 0) {
                candidateScoreSpreadTotal / candidateScoreDecisionCount
            } else {
                0.0
            }
    val averagePhysicalHostUtilization: Double
        get() = if (placementMetricCount > 0) physicalHostUtilizationTotal / placementMetricCount else 0.0
    val averageHostResourceFragmentation: Double
        get() = if (placementMetricCount > 0) hostResourceFragmentationTotal / placementMetricCount else 0.0
    val averageNetworkTransferDelay: Double
        get() = if (placementMetricCount > 0) networkTransferDelayTotal / placementMetricCount else 0.0
    val imageCacheHitRate: Double
        get() = if (imageCacheDecisionCount > 0) imageCacheHitCount.toDouble() / imageCacheDecisionCount else 0.0
    val averageNoisyNeighborPressure: Double
        get() = if (placementMetricCount > 0) noisyNeighborPressureTotal / placementMetricCount else 0.0

    fun recordRejected(reason: RealtimeRejectReason) {
        rejectedCount++
        when (reason) {
            RealtimeRejectReason.QUEUE -> Unit
            RealtimeRejectReason.CAPACITY -> capacityRejectedCount++
            RealtimeRejectReason.RESOURCE -> resourceRejectedCount++
            RealtimeRejectReason.DEADLINE -> deadlineRejectedCount++
            RealtimeRejectReason.DEPENDENCY -> Unit
            RealtimeRejectReason.TENANT_QUOTA -> tenantQuotaRejectedCount++
            RealtimeRejectReason.TENANT_BUDGET -> tenantBudgetRejectedCount++
        }
    }

    fun recordDependencyBlocked() {
        dependencyBlockedCount++
    }

    fun recordDependencyReleased() {
        dependencyReleasedCount++
    }

    fun recordDependencyRejected() {
        dependencyRejectedCount++
    }

    internal fun recordDeadlineAdmission(action: DeadlineAdmissionMetricAction?) {
        when (action) {
            DeadlineAdmissionMetricAction.MISS_ACCEPTED -> deadlineMissAcceptedCount++
            DeadlineAdmissionMetricAction.DEGRADED -> deadlineDegradedCount++
            null -> Unit
        }
    }

    fun recordDeadlineRetryLater() {
        deadlineRetryLaterCount++
    }

    fun recordRescheduleAttempt() {
        rescheduleAttemptCount++
    }

    fun recordRescheduleSuccess(delay: Double) {
        rescheduleSuccessCount++
        rescheduleDelayTotal += delay
    }

    fun recordRescheduleFailure() {
        rescheduleFailureCount++
    }

    fun recordSubmitted() {
        submittedCount++
    }

    fun recordRetry() {
        retryCount++
    }

    fun recordRetrySuccess() {
        retrySuccessCount++
    }

    fun recordPermanentFailure() {
        permanentFailedCount++
    }

    fun recordRuntimeFailure() {
        runtimeFailureCount++
    }

    fun recordTimeoutCancelled() {
        timeoutCancelledCount++
    }

    fun recordMigration() {
        migrationCount++
    }

    fun recordCheckpointRecovery() {
        checkpointRecoveryCount++
    }

    fun addCheckpointLoss(loss: Long) {
        checkpointLossTotal += loss
    }

    fun recordPreemptionSuccess(
        delay: Double,
        penalty: Double,
    ) {
        preemptedCount++
        preemptionSuccessCount++
        preemptionDelayTotal += delay
        preemptionPenaltyTotal += penalty
    }

    fun recordPreemptionFailed() {
        preemptionFailedCount++
    }

    fun recordDecisionDelay(delay: Double) {
        decisionDelayTotal += delay
        decisionCount++
    }

    fun recordQueueDepth(depth: Int) {
        queueDepthSampleTotal += depth
        queueDepthSampleCount++
        maxQueueDepth = maxOf(maxQueueDepth, depth)
    }

    fun recordCandidateScores(records: List<RealtimeCandidateScoreRecord>) {
        if (records.isEmpty()) return
        candidateScoreRecords += records
        val selected = records.firstOrNull { it.selected && it.accepted } ?: return
        val acceptedScores = records.filter { it.accepted }.map { it.totalScore }
        val scoreSpread =
            if (acceptedScores.isNotEmpty()) {
                (acceptedScores.maxOrNull() ?: 0.0) - (acceptedScores.minOrNull() ?: 0.0)
            } else {
                0.0
            }
        realtimeScoreTotal += selected.totalScore
        selectedLatenessPenaltyTotal += selected.breakdown.latenessPenalty
        selectedDeadlineSlackTotal += selected.breakdown.deadlineSlack
        candidateScoreSpreadTotal += scoreSpread
        candidateScoreDecisionCount++
    }

    fun candidateScoreRecords(): List<RealtimeCandidateScoreRecord> = candidateScoreRecords.toList()

    fun recordPlacement(state: RealtimeNodeState) {
        placementMetricCount++
        physicalHostUtilizationTotal += state.physicalHostUtilization
        hostResourceFragmentationTotal += state.hostResourceFragmentation
        networkTransferDelayTotal += state.networkTransferDelay
        noisyNeighborPressureTotal += state.noisyNeighborPressure
        imageCacheDecisionCount++
        if (state.imageCacheHit) {
            imageCacheHitCount++
        }
    }

    fun recordTopologyFailure(domain: RealtimeFailureDomain) {
        when (domain) {
            RealtimeFailureDomain.HOST -> hostFailureCount++
            RealtimeFailureDomain.RACK -> rackFailureCount++
            RealtimeFailureDomain.REGION -> regionFailureCount++
        }
    }

    fun snapshot(): RealtimeBrokerMetricsSnapshot =
        RealtimeBrokerMetricsSnapshot(
            rejectedCount,
            capacityRejectedCount,
            resourceRejectedCount,
            deadlineRejectedCount,
            deadlineDegradedCount,
            deadlineRetryLaterCount,
            deadlineMissAcceptedCount,
            dependencyBlockedCount,
            dependencyReleasedCount,
            dependencyRejectedCount,
            rescheduleAttemptCount,
            rescheduleSuccessCount,
            rescheduleFailureCount,
            averageRescheduleDelay,
            tenantQuotaRejectedCount,
            tenantBudgetRejectedCount,
            submittedCount,
            retryCount,
            retrySuccessCount,
            permanentFailedCount,
            runtimeFailureCount,
            timeoutCancelledCount,
            migrationCount,
            checkpointRecoveryCount,
            preemptedCount,
            preemptionSuccessCount,
            preemptionFailedCount,
            averagePreemptionDelay,
            preemptionPenaltyTotal,
            checkpointLossTotal,
            hostFailureCount,
            rackFailureCount,
            regionFailureCount,
            averageDecisionDelay,
            averageQueueDepth,
            maxQueueDepth,
            averageRealtimeScore,
            averageSelectedLatenessPenalty,
            averageSelectedDeadlineSlack,
            averageCandidateScoreSpread,
            averagePhysicalHostUtilization,
            averageHostResourceFragmentation,
            averageNetworkTransferDelay,
            imageCacheHitRate,
            averageNoisyNeighborPressure,
        )
}

enum class RealtimeFailureDomain {
    HOST,
    RACK,
    REGION,
}
