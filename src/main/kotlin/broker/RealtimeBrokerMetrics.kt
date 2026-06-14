package broker

data class RealtimeBrokerMetricsSnapshot(
    val rejectedCount: Int,
    val capacityRejectedCount: Int,
    val resourceRejectedCount: Int,
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
)

class RealtimeBrokerMetrics {
    var rejectedCount: Int = 0
        private set
    var capacityRejectedCount: Int = 0
        private set
    var resourceRejectedCount: Int = 0
        private set
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

    fun recordRejected(reason: RealtimeRejectReason) {
        rejectedCount++
        when (reason) {
            RealtimeRejectReason.QUEUE -> Unit
            RealtimeRejectReason.CAPACITY -> capacityRejectedCount++
            RealtimeRejectReason.RESOURCE -> resourceRejectedCount++
            RealtimeRejectReason.TENANT_QUOTA -> tenantQuotaRejectedCount++
            RealtimeRejectReason.TENANT_BUDGET -> tenantBudgetRejectedCount++
        }
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
        )
}

enum class RealtimeFailureDomain {
    HOST,
    RACK,
    REGION,
}
