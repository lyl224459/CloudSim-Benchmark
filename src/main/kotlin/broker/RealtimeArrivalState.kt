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
