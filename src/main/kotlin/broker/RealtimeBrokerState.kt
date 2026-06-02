package broker

import org.cloudsimplus.cloudlets.Cloudlet
import scheduler.CloudletId
import scheduler.RealtimeTaskLifecycle
import scheduler.VmIndex

data class RealtimeArrivalSnapshot(
    val waitingCloudletIds: List<CloudletId>,
    val pendingCloudletIds: List<CloudletId>,
    val realtimeCloudletIds: List<CloudletId>,
    val arrivalTimes: Map<CloudletId, Double>,
    val preassignedVmIndexes: Map<CloudletId, VmIndex>,
    val attempts: Map<CloudletId, Int>,
)

class RealtimeArrivalState {
    private val waitingCloudlets = mutableListOf<Cloudlet>()
    private val pendingCloudlets = mutableListOf<Cloudlet>()
    private val realtimeCloudlets = mutableListOf<Cloudlet>()
    private val arrivalTimes = mutableMapOf<CloudletId, Double>()
    private val preassignedVmIndexes = mutableMapOf<CloudletId, VmIndex>()
    private val attempts = mutableMapOf<CloudletId, Int>()

    fun recordArrival(cloudlet: Cloudlet) {
        arrivalTimes[cloudlet.cloudletId] = cloudlet.submissionDelay
    }

    fun arrivalTimeOf(cloudlet: Cloudlet): Double = arrivalTimes[cloudlet.cloudletId] ?: cloudlet.submissionDelay

    fun addRealtimeCloudlets(cloudlets: List<Cloudlet>) {
        realtimeCloudlets += cloudlets
    }

    fun realtimeCloudletsSnapshot(): List<Cloudlet> = realtimeCloudlets.toList()

    fun waitingCloudletsSnapshot(): List<Cloudlet> =
        waitingCloudlets
            .filter { it.status != Cloudlet.Status.SUCCESS && it.status != Cloudlet.Status.FAILED }
            .toList()

    fun pendingCloudletsSnapshot(): List<Cloudlet> = pendingCloudlets.toList()

    fun queuedCloudletsSnapshot(): List<Cloudlet> = pendingCloudletsSnapshot() + waitingCloudletsSnapshot()

    fun addPending(cloudlet: Cloudlet) {
        pendingCloudlets += cloudlet
    }

    fun addWaiting(cloudlet: Cloudlet) {
        waitingCloudlets += cloudlet
    }

    fun removePending(cloudletId: CloudletId) {
        pendingCloudlets.removeIf { it.cloudletId == cloudletId }
    }

    fun removeWaiting(cloudletId: CloudletId) {
        waitingCloudlets.removeIf { it.cloudletId == cloudletId }
    }

    fun preassign(
        cloudlet: Cloudlet,
        vmIndex: Int,
    ) {
        preassignedVmIndexes[cloudlet.cloudletId] = VmIndex(vmIndex)
    }

    fun preassignedVmIndexOf(cloudlet: Cloudlet): Int? = preassignedVmIndexes[cloudlet.cloudletId]?.value

    fun attemptOf(cloudlet: Cloudlet): Int = attempts[cloudlet.cloudletId] ?: 0

    fun setAttempt(
        cloudlet: Cloudlet,
        attempt: Int,
    ) {
        attempts[cloudlet.cloudletId] = attempt
    }

    fun incrementAttempt(cloudlet: Cloudlet): Int {
        val next = attemptOf(cloudlet) + 1
        setAttempt(cloudlet, next)
        return next
    }

    fun snapshot(): RealtimeArrivalSnapshot =
        RealtimeArrivalSnapshot(
            waitingCloudletIds = waitingCloudlets.map { it.cloudletId },
            pendingCloudletIds = pendingCloudlets.map { it.cloudletId },
            realtimeCloudletIds = realtimeCloudlets.map { it.cloudletId },
            arrivalTimes = arrivalTimes.toMap(),
            preassignedVmIndexes = preassignedVmIndexes.toMap(),
            attempts = attempts.toMap(),
        )

    private val Cloudlet.cloudletId: CloudletId get() = CloudletId(id)
}

data class RealtimeReservationSnapshot(
    val reservations: Map<CloudletId, VmIndex>,
)

class RealtimeReservationState {
    private val reservations = mutableMapOf<CloudletId, VmIndex>()

    fun reserve(
        cloudlet: Cloudlet,
        vmIndex: Int,
    ) {
        reservations[cloudlet.cloudletId] = VmIndex(vmIndex)
    }

    fun remove(cloudlet: Cloudlet) {
        reservations.remove(cloudlet.cloudletId)
    }

    fun assignedVmIndexOf(cloudlet: Cloudlet): Int? = reservations[cloudlet.cloudletId]?.value

    fun rawReservations(): Map<Long, Int> = reservations.mapKeys { it.key.value }.mapValues { it.value.value }

    fun prune(lifecycleOf: (CloudletId) -> RealtimeTaskLifecycle?) {
        reservations.keys.removeIf { cloudletId ->
            lifecycleOf(cloudletId) in setOf(RealtimeTaskLifecycle.REJECTED, RealtimeTaskLifecycle.FAILED)
        }
    }

    fun snapshot(): RealtimeReservationSnapshot = RealtimeReservationSnapshot(reservations = reservations.toMap())

    private val Cloudlet.cloudletId: CloudletId get() = CloudletId(id)
}

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
        get() =
            if (queueDepthSampleCount > 0) {
                queueDepthSampleTotal.toDouble() / queueDepthSampleCount.toDouble()
            } else {
                0.0
            }

    val averagePreemptionDelay: Double
        get() = if (preemptedCount > 0) preemptionDelayTotal / preemptedCount.toDouble() else 0.0

    val preemptionPenalty: Double get() = preemptionPenaltyTotal

    val checkpointLoss: Long get() = checkpointLossTotal

    val retrySuccessRate: Double
        get() = if (retryCount > 0) retrySuccessCount.toDouble() / retryCount.toDouble() else 0.0

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
            rejectedCount = rejectedCount,
            capacityRejectedCount = capacityRejectedCount,
            resourceRejectedCount = resourceRejectedCount,
            tenantQuotaRejectedCount = tenantQuotaRejectedCount,
            tenantBudgetRejectedCount = tenantBudgetRejectedCount,
            submittedCount = submittedCount,
            retryCount = retryCount,
            retrySuccessCount = retrySuccessCount,
            permanentFailedCount = permanentFailedCount,
            runtimeFailureCount = runtimeFailureCount,
            timeoutCancelledCount = timeoutCancelledCount,
            migrationCount = migrationCount,
            checkpointRecoveryCount = checkpointRecoveryCount,
            preemptedCount = preemptedCount,
            preemptionSuccessCount = preemptionSuccessCount,
            preemptionFailedCount = preemptionFailedCount,
            averagePreemptionDelay = averagePreemptionDelay,
            preemptionPenaltyTotal = preemptionPenaltyTotal,
            checkpointLossTotal = checkpointLossTotal,
            hostFailureCount = hostFailureCount,
            rackFailureCount = rackFailureCount,
            regionFailureCount = regionFailureCount,
            averageDecisionDelay = averageDecisionDelay,
            averageQueueDepth = averageQueueDepth,
            maxQueueDepth = maxQueueDepth,
        )
}

enum class RealtimeFailureDomain {
    HOST,
    RACK,
    REGION,
}
