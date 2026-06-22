package broker

import org.cloudsimplus.cloudlets.Cloudlet
import scheduler.CloudletId
import scheduler.VmIndex

data class RealtimeArrivalSnapshot(
    val waitingCloudletIds: List<CloudletId>,
    val pendingCloudletIds: List<CloudletId>,
    val realtimeCloudletIds: List<CloudletId>,
    val arrivalTimes: Map<CloudletId, Double>,
    val preassignedVmIndexes: Map<CloudletId, VmIndex>,
    val attempts: Map<CloudletId, Int>,
    val decisionTokens: Map<CloudletId, Int>,
    val runtimeTokens: Map<CloudletId, Int>,
    val rescheduleCounts: Map<CloudletId, Int>,
)

@Suppress("TooManyFunctions") // State facade owns the complete arrival lifecycle transition API.
class RealtimeArrivalState {
    private val waitingCloudlets = mutableListOf<Cloudlet>()
    private val pendingCloudlets = mutableListOf<Cloudlet>()
    private val realtimeCloudlets = mutableListOf<Cloudlet>()
    private val arrivalTimes = mutableMapOf<CloudletId, Double>()
    private val preassignedVmIndexes = mutableMapOf<CloudletId, VmIndex>()
    private val attempts = mutableMapOf<CloudletId, Int>()
    private val decisionTokens = mutableMapOf<CloudletId, Int>()
    private val runtimeTokens = mutableMapOf<CloudletId, Int>()
    private val rescheduleCounts = mutableMapOf<CloudletId, Int>()

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
        invalidateDecisionToken(cloudletId)
    }

    fun removeWaiting(cloudletId: CloudletId) {
        waitingCloudlets.removeIf { it.cloudletId == cloudletId }
        invalidateRuntimeToken(cloudletId)
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

    fun issueDecisionToken(cloudlet: Cloudlet): Int {
        val id = cloudlet.cloudletId
        val token = (decisionTokens[id] ?: 0) + 1
        decisionTokens[id] = token
        return token
    }

    fun isCurrentDecisionToken(
        cloudlet: Cloudlet,
        token: Int,
    ): Boolean = decisionTokens[cloudlet.cloudletId] == token

    fun issueRuntimeToken(cloudlet: Cloudlet): Int {
        val id = cloudlet.cloudletId
        val token = (runtimeTokens[id] ?: 0) + 1
        runtimeTokens[id] = token
        return token
    }

    fun isCurrentRuntimeToken(
        cloudlet: Cloudlet,
        token: Int,
    ): Boolean = runtimeTokens[cloudlet.cloudletId] == token

    fun rescheduleCountOf(cloudlet: Cloudlet): Int = rescheduleCounts[cloudlet.cloudletId] ?: 0

    fun incrementRescheduleCount(cloudlet: Cloudlet): Int {
        val next = rescheduleCountOf(cloudlet) + 1
        rescheduleCounts[cloudlet.cloudletId] = next
        return next
    }

    private fun invalidateDecisionToken(cloudletId: CloudletId) {
        decisionTokens[cloudletId] = (decisionTokens[cloudletId] ?: 0) + 1
    }

    private fun invalidateRuntimeToken(cloudletId: CloudletId) {
        runtimeTokens[cloudletId] = (runtimeTokens[cloudletId] ?: 0) + 1
    }

    fun snapshot(): RealtimeArrivalSnapshot =
        RealtimeArrivalSnapshot(
            waitingCloudletIds = waitingCloudlets.map { it.cloudletId },
            pendingCloudletIds = pendingCloudlets.map { it.cloudletId },
            realtimeCloudletIds = realtimeCloudlets.map { it.cloudletId },
            arrivalTimes = arrivalTimes.toMap(),
            preassignedVmIndexes = preassignedVmIndexes.toMap(),
            attempts = attempts.toMap(),
            decisionTokens = decisionTokens.toMap(),
            runtimeTokens = runtimeTokens.toMap(),
            rescheduleCounts = rescheduleCounts.toMap(),
        )

    private val Cloudlet.cloudletId: CloudletId get() = CloudletId(id)
}
