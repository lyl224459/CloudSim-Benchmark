package broker

import config.RealtimeSchedulingConfig
import org.cloudsimplus.brokers.DatacenterBrokerSimple
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.core.CloudSimPlus
import org.cloudsimplus.core.events.SimEvent
import org.cloudsimplus.vms.Vm
import scheduler.RealtimeNodeStateTracker
import scheduler.RealtimeScheduler
import scheduler.RealtimeSchedulingContext
import java.util.Random
import kotlin.math.pow

/**
 * 实时调度代理。
 *
 * 通过 CloudSim 事件在任务到达时提交 cloudlet，并在到达时调用调度器。
 */
class RealtimeBroker(
    simulation: CloudSimPlus,
    private val scheduler: RealtimeScheduler,
    private val vmList: List<Vm>,
    private val schedulingConfig: RealtimeSchedulingConfig = RealtimeSchedulingConfig()
) : DatacenterBrokerSimple(simulation) {

    companion object {
        private const val ARRIVAL_EVENT_TAG = 9001
        private const val SUBMIT_EVENT_TAG = 9002
    }

    private val waitingCloudlets = mutableListOf<Cloudlet>()
    private val pendingCloudlets = mutableListOf<Cloudlet>()
    private val realtimeCloudlets = mutableListOf<Cloudlet>()
    private val arrivalTimes = mutableMapOf<Long, Double>()
    private val preassignedVmIds = mutableMapOf<Long, Int>()
    private val attempts = mutableMapOf<Long, Int>()
    private val nodeStateTracker = RealtimeNodeStateTracker(vmList)
    private val failureRandom = Random(0L)
    private var rejectedCount = 0
    private var submittedCount = 0
    private var retryCount = 0
    private var permanentFailedCount = 0
    private var decisionDelayTotal = 0.0
    private var decisionCount = 0

    private data class PendingSubmission(
        val cloudlet: Cloudlet,
        val vmIndex: Int,
        val decisionDelay: Double
    )

    fun submitCloudletListRealtime(cloudletList: List<Cloudlet>) {
        val sortedCloudlets = cloudletList.sortedBy { it.submissionDelay }
        if (schedulingConfig.strategy.equals("static", ignoreCase = true)) {
            val previewWaiting = mutableListOf<Cloudlet>()
            for (cloudlet in sortedCloudlets) {
                val context = schedulingContext(cloudlet, previewWaiting.toList(), cloudlet.submissionDelay)
                preassignedVmIds[cloudlet.id] = scheduler.scheduleOnArrival(context)
                previewWaiting.add(cloudlet)
            }
        }

        for (cloudlet in sortedCloudlets) {
            arrivalTimes[cloudlet.id] = cloudlet.submissionDelay
        }
        realtimeCloudlets.addAll(sortedCloudlets)
    }

    fun getWaitingCloudlets(): List<Cloudlet> = waitingCloudlets.filter {
        it.status != Cloudlet.Status.SUCCESS && it.status != Cloudlet.Status.FAILED
    }

    private fun getActiveCloudlets(): List<Cloudlet> =
        (pendingCloudlets + getWaitingCloudlets()).distinctBy { it.id }

    fun getRejectedCount(): Int = rejectedCount

    fun getSubmittedCount(): Int = submittedCount

    fun getRetryCount(): Int = retryCount

    fun getPermanentFailedCount(): Int = permanentFailedCount

    fun getAverageDecisionDelay(): Double {
        return if (decisionCount > 0) decisionDelayTotal / decisionCount else 0.0
    }

    fun getArrivalTime(cloudlet: Cloudlet): Double = arrivalTimes[cloudlet.id] ?: cloudlet.submissionDelay

    fun getTimeoutCount(timeoutSeconds: Double): Int {
        if (timeoutSeconds <= 0.0) return 0
        return waitingCloudlets.count { cloudlet ->
            val arrivalTime = getArrivalTime(cloudlet)
            val finishTime = cloudlet.finishTime
            val elapsed = if (cloudlet.status == Cloudlet.Status.SUCCESS) {
                finishTime - arrivalTime
            } else {
                Double.POSITIVE_INFINITY
            }
            elapsed > timeoutSeconds
        }
    }

    override fun processEvent(event: SimEvent) {
        when (event.tag) {
            ARRIVAL_EVENT_TAG -> {
                val cloudlet = event.data as Cloudlet
                onCloudletArrival(cloudlet, event.time)
                return
            }
            SUBMIT_EVENT_TAG -> {
                val submission = event.data as PendingSubmission
                submitPendingCloudlet(submission)
                return
            }
        }
        super.processEvent(event)
    }

    override fun startInternal() {
        super.startInternal()
        for (cloudlet in realtimeCloudlets) {
            schedule(cloudlet.submissionDelay, ARRIVAL_EVENT_TAG, cloudlet)
        }
    }

    private fun onCloudletArrival(cloudlet: Cloudlet, arrivalTime: Double) {
        val activeCloudlets = getActiveCloudlets()
        if (activeCloudlets.size >= schedulingConfig.maxQueueSize) {
            rejectedCount++
            return
        }

        val selectedVmId = selectVmId(cloudlet, activeCloudlets, arrivalTime)
        val delay = decisionDelay(cloudlet)
        decisionDelayTotal += delay
        decisionCount++
        cloudlet.setVm(vmList[selectedVmId.coerceIn(vmList.indices)])
        pendingCloudlets.add(cloudlet)
        schedule(delay, SUBMIT_EVENT_TAG, PendingSubmission(cloudlet, selectedVmId.coerceIn(vmList.indices), delay))
    }

    private fun submitPendingCloudlet(submission: PendingSubmission) {
        val cloudlet = submission.cloudlet
        pendingCloudlets.removeIf { it.id == cloudlet.id }
        if (shouldFailAttempt(cloudlet)) {
            val attempt = attempts.getOrDefault(cloudlet.id, 0)
            if (attempt < schedulingConfig.retryLimit) {
                attempts[cloudlet.id] = attempt + 1
                retryCount++
                schedule(retryDelay(attempt), ARRIVAL_EVENT_TAG, cloudlet)
            } else {
                cloudlet.setStatus(Cloudlet.Status.FAILED)
                permanentFailedCount++
            }
            return
        }

        cloudlet.setVm(vmList[submission.vmIndex])
        cloudlet.setSubmissionDelay(0.0)
        waitingCloudlets.add(cloudlet)
        submittedCount++
        submitCloudlet(cloudlet)
    }

    private fun selectVmId(cloudlet: Cloudlet, activeCloudlets: List<Cloudlet>, currentTime: Double): Int {
        val strategy = schedulingConfig.strategy.lowercase()
        val selected = if (strategy == "static") {
            preassignedVmIds[cloudlet.id] ?: scheduler.scheduleOnArrival(schedulingContext(cloudlet, activeCloudlets, currentTime))
        } else {
            scheduler.scheduleOnArrival(schedulingContext(cloudlet, activeCloudlets, currentTime))
        }

        return applyReservationPolicy(selected, cloudlet, activeCloudlets)
    }

    private fun schedulingContext(
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        currentTime: Double
    ): RealtimeSchedulingContext =
        RealtimeSchedulingContext(
            newCloudlet = cloudlet,
            activeCloudlets = activeCloudlets,
            vmList = vmList,
            currentTime = currentTime,
            nodeStates = nodeStateTracker.snapshot(activeCloudlets, currentTime)
        )

    private fun decisionDelay(cloudlet: Cloudlet): Double {
        val jitter = if (schedulingConfig.decisionJitter > 0.0) {
            deterministicUnit(cloudlet.id, attempts.getOrDefault(cloudlet.id, 0), salt = 11) * schedulingConfig.decisionJitter
        } else {
            0.0
        }
        return schedulingConfig.decisionDelay + jitter
    }

    private fun shouldFailAttempt(cloudlet: Cloudlet): Boolean {
        if (schedulingConfig.failureRate <= 0.0) return false
        if (schedulingConfig.failureRate >= 1.0) return true
        val attempt = attempts.getOrDefault(cloudlet.id, 0)
        return deterministicUnit(cloudlet.id, attempt, salt = 29) < schedulingConfig.failureRate
    }

    private fun retryDelay(attempt: Int): Double {
        if (schedulingConfig.retryDelay <= 0.0) return 0.0
        return schedulingConfig.retryDelay * schedulingConfig.retryBackoffMultiplier.pow(attempt.toDouble())
    }

    private fun deterministicUnit(cloudletId: Long, attempt: Int, salt: Int): Double {
        failureRandom.setSeed(cloudletId * 1_000_003L + attempt * 9_176L + salt)
        return failureRandom.nextDouble()
    }

    private fun applyReservationPolicy(selectedVmId: Int, cloudlet: Cloudlet, activeCloudlets: List<Cloudlet>): Int {
        return when (schedulingConfig.resourceReservation.lowercase()) {
            "partial" -> applyPartialReservation(selectedVmId, activeCloudlets)
            "full" -> applyFullReservation(cloudlet, selectedVmId, activeCloudlets)
            else -> selectedVmId
        }
    }

    private fun applyPartialReservation(selectedVmId: Int, activeCloudlets: List<Cloudlet>): Int {
        val selectedVm = vmList[selectedVmId.coerceIn(vmList.indices)]
        val highestMips = vmList.maxOf { it.mips }
        val hasIdleNonHigh = vmList.any { it.mips < highestMips && activeCloudlets.none { cloudlet -> cloudlet.vm?.id == it.id } }
        if (selectedVm.mips == highestMips && hasIdleNonHigh) {
            return vmList.indexOfFirst { it.mips < highestMips }
                .takeIf { it >= 0 } ?: selectedVmId
        }
        return selectedVmId
    }

    private fun applyFullReservation(cloudlet: Cloudlet, selectedVmId: Int, activeCloudlets: List<Cloudlet>): Int {
        val length = cloudlet.length
        val groups = vmList.groupBy { it.mips }.toSortedMap()
        val desiredGroup = when {
            length < 20000 -> groups.keys.firstOrNull()
            length < 40000 -> groups.keys.elementAtOrNull(1) ?: groups.keys.firstOrNull()
            else -> groups.keys.lastOrNull()
        } ?: return selectedVmId

        val candidateVmIds = groups[desiredGroup].orEmpty().map { it.id.toInt() }
        if (candidateVmIds.isEmpty()) return selectedVmId

        val leastLoaded = candidateVmIds.minByOrNull { vmId ->
            activeCloudlets.count { it.vm?.id?.toInt() == vmId }
        }
        return leastLoaded ?: selectedVmId
    }
}
