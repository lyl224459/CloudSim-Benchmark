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
import scheduler.RealtimeTaskLifecycle
import scheduler.RealtimeTaskMetadata
import java.util.Random
import kotlin.math.ceil
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
    private val metadataByCloudletId = mutableMapOf<Long, RealtimeTaskMetadata>()
    private val vmReservations = mutableMapOf<Long, Int>()
    private val nodeStateTracker = RealtimeNodeStateTracker(vmList, schedulingConfig.vmQueueCapacity)
    private val failureRandom = Random(0L)
    private var rejectedCount = 0
    private var capacityRejectedCount = 0
    private var submittedCount = 0
    private var retryCount = 0
    private var permanentFailedCount = 0
    private var decisionDelayTotal = 0.0
    private var decisionCount = 0
    private var queueDepthSampleTotal = 0
    private var queueDepthSampleCount = 0
    private var maxQueueDepth = 0

    private data class PendingSubmission(
        val cloudlet: Cloudlet,
        val vmIndex: Int,
        val decisionDelay: Double,
        val failurePressure: Double
    )

    fun submitCloudletListRealtime(cloudletList: List<Cloudlet>) {
        for (cloudlet in cloudletList) {
            arrivalTimes[cloudlet.id] = cloudlet.submissionDelay
            metadataByCloudletId[cloudlet.id] = createMetadata(cloudlet)
        }
        val sortedCloudlets = cloudletList.sortedWith(cloudletArrivalComparator())
        if (schedulingConfig.strategy.equals("static", ignoreCase = true)) {
            val previewWaiting = mutableListOf<Cloudlet>()
            for (cloudlet in sortedCloudlets) {
                val context = schedulingContext(cloudlet, previewWaiting.toList(), cloudlet.submissionDelay)
                preassignedVmIds[cloudlet.id] = scheduler.scheduleOnArrival(context)
                previewWaiting.add(cloudlet)
            }
        }

        realtimeCloudlets.addAll(sortedCloudlets)
    }

    fun getWaitingCloudlets(): List<Cloudlet> = waitingCloudlets.filter {
        it.status != Cloudlet.Status.SUCCESS && it.status != Cloudlet.Status.FAILED
    }

    private fun getActiveCloudlets(): List<Cloudlet> {
        pruneCompletedReservations()
        return (pendingCloudlets + getWaitingCloudlets())
            .distinctBy { it.id }
            .filter { it.status != Cloudlet.Status.FAILED }
    }

    fun getRejectedCount(): Int = rejectedCount

    fun getCapacityRejectedCount(): Int = capacityRejectedCount

    fun getSubmittedCount(): Int = submittedCount

    fun getRetryCount(): Int = retryCount

    fun getPermanentFailedCount(): Int = permanentFailedCount

    fun getAverageDecisionDelay(): Double {
        return if (decisionCount > 0) decisionDelayTotal / decisionCount else 0.0
    }

    fun getAverageQueueDepth(): Double {
        return if (queueDepthSampleCount > 0) queueDepthSampleTotal.toDouble() / queueDepthSampleCount.toDouble() else 0.0
    }

    fun getMaxQueueDepth(): Int = maxQueueDepth

    fun getTaskMetadata(cloudlet: Cloudlet): RealtimeTaskMetadata? = metadataByCloudletId[cloudlet.id]

    fun getSlaViolationCount(cloudlets: List<Cloudlet>): Int {
        if (schedulingConfig.deadlineFactor <= 0.0) return 0
        return cloudlets.count { cloudlet ->
            cloudlet.status == Cloudlet.Status.SUCCESS &&
                metadataByCloudletId[cloudlet.id]?.deadline?.let { deadline -> cloudlet.finishTime > deadline } == true
        }
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
            rejectCloudlet(cloudlet, capacity = false)
            return
        }

        val selection = selectVmId(cloudlet, activeCloudlets, arrivalTime)
        if (selection == null) {
            rejectCloudlet(cloudlet, capacity = true)
            return
        }

        val (selectedVmId, failurePressure) = selection
        val delay = decisionDelay(cloudlet)
        decisionDelayTotal += delay
        decisionCount++
        cloudlet.setVm(vmList[selectedVmId.coerceIn(vmList.indices)])
        vmReservations[cloudlet.id] = selectedVmId.coerceIn(vmList.indices)
        pendingCloudlets.add(cloudlet)
        updateMetadata(cloudlet) {
            it.copy(
                assignedVmIndex = selectedVmId.coerceIn(vmList.indices),
                lastDecisionDelay = delay,
                lifecycle = RealtimeTaskLifecycle.PENDING_DECISION
            )
        }
        sampleQueueDepth(activeCloudlets, selectedVmId.coerceIn(vmList.indices), arrivalTime)
        schedule(delay, SUBMIT_EVENT_TAG, PendingSubmission(cloudlet, selectedVmId.coerceIn(vmList.indices), delay, failurePressure))
    }

    private fun submitPendingCloudlet(submission: PendingSubmission) {
        val cloudlet = submission.cloudlet
        pendingCloudlets.removeIf { it.id == cloudlet.id }
        if (shouldFailAttempt(cloudlet, submission.failurePressure)) {
            val attempt = attempts.getOrDefault(cloudlet.id, 0)
            if (attempt < schedulingConfig.retryLimit) {
                attempts[cloudlet.id] = attempt + 1
                vmReservations.remove(cloudlet.id)
                updateMetadata(cloudlet) {
                    it.copy(
                        attempt = attempt + 1,
                        assignedVmIndex = null,
                        lifecycle = RealtimeTaskLifecycle.ARRIVED
                    )
                }
                retryCount++
                schedule(retryDelay(attempt), ARRIVAL_EVENT_TAG, cloudlet)
            } else {
                cloudlet.setStatus(Cloudlet.Status.FAILED)
                vmReservations.remove(cloudlet.id)
                updateMetadata(cloudlet) { it.copy(lifecycle = RealtimeTaskLifecycle.FAILED) }
                permanentFailedCount++
            }
            return
        }

        cloudlet.setVm(vmList[submission.vmIndex])
        cloudlet.setSubmissionDelay(0.0)
        waitingCloudlets.add(cloudlet)
        updateMetadata(cloudlet) { it.copy(lifecycle = RealtimeTaskLifecycle.SUBMITTED) }
        submittedCount++
        submitCloudlet(cloudlet)
    }

    private fun selectVmId(cloudlet: Cloudlet, activeCloudlets: List<Cloudlet>, currentTime: Double): Pair<Int, Double>? {
        val strategy = schedulingConfig.strategy.lowercase()
        val context = schedulingContext(cloudlet, activeCloudlets, currentTime)
        if (context.hasCapacityLimit && context.candidateNodeStates.isEmpty()) return null

        val selected = if (strategy == "static") {
            preassignedVmIds[cloudlet.id] ?: scheduler.scheduleOnArrival(schedulingContext(cloudlet, activeCloudlets, currentTime))
        } else {
            scheduler.scheduleOnArrival(context)
        }

        val reserved = applyReservationPolicy(selected, cloudlet, activeCloudlets)
        val bounded = reserved.coerceIn(vmList.indices)
        val state = context.nodeStates.getOrNull(bounded)
        if (state != null && !state.acceptingWork) return null
        return bounded to (state?.failurePressure ?: 0.0)
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
            nodeStates = nodeStateTracker.snapshot(activeCloudlets, currentTime, vmReservations),
            taskMetadata = metadataByCloudletId[cloudlet.id] ?: createMetadata(cloudlet),
            queuePolicy = schedulingConfig.normalizedQueuePolicy()
        )

    private fun decisionDelay(cloudlet: Cloudlet): Double {
        val jitter = if (schedulingConfig.decisionJitter > 0.0) {
            deterministicUnit(cloudlet.id, attempts.getOrDefault(cloudlet.id, 0), salt = 11) * schedulingConfig.decisionJitter
        } else {
            0.0
        }
        return schedulingConfig.decisionDelay + jitter
    }

    private fun shouldFailAttempt(cloudlet: Cloudlet, failurePressure: Double): Boolean {
        val effectiveFailureRate = (schedulingConfig.failureRate +
            failurePressure * schedulingConfig.overloadFailureMultiplier).coerceIn(0.0, 1.0)
        if (effectiveFailureRate <= 0.0) return false
        if (effectiveFailureRate >= 1.0) return true
        val attempt = attempts.getOrDefault(cloudlet.id, 0)
        return deterministicUnit(cloudlet.id, attempt, salt = 29) < effectiveFailureRate
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

    private fun createMetadata(cloudlet: Cloudlet): RealtimeTaskMetadata {
        val arrivalTime = arrivalTimes[cloudlet.id] ?: cloudlet.submissionDelay
        return RealtimeTaskMetadata(
            cloudletId = cloudlet.id,
            originalArrivalTime = arrivalTime,
            attempt = attempts.getOrDefault(cloudlet.id, 0),
            priority = priorityFor(cloudlet),
            deadline = deadlineFor(cloudlet, arrivalTime)
        )
    }

    private fun updateMetadata(cloudlet: Cloudlet, transform: (RealtimeTaskMetadata) -> RealtimeTaskMetadata) {
        val current = metadataByCloudletId[cloudlet.id] ?: createMetadata(cloudlet)
        metadataByCloudletId[cloudlet.id] = transform(current)
    }

    private fun priorityFor(cloudlet: Cloudlet): Int {
        val levels = schedulingConfig.priorityLevels.coerceAtLeast(1)
        if (levels == 1) return 0
        val highPriorityCutoff = ceil(levels * schedulingConfig.highPriorityRatio).toInt().coerceIn(0, levels)
        if (highPriorityCutoff <= 0) return deterministicIndex(cloudlet.id, salt = 43, modulo = levels)
        val highPriority = deterministicUnit(cloudlet.id, attempt = 0, salt = 41) < schedulingConfig.highPriorityRatio
        return if (highPriority) {
            deterministicIndex(cloudlet.id, salt = 43, modulo = highPriorityCutoff)
        } else {
            highPriorityCutoff + deterministicIndex(cloudlet.id, salt = 47, modulo = levels - highPriorityCutoff)
        }.coerceIn(0, levels - 1)
    }

    private fun deadlineFor(cloudlet: Cloudlet, arrivalTime: Double): Double? {
        if (schedulingConfig.deadlineFactor <= 0.0) return null
        val fastestMips = vmList.maxOfOrNull { it.mips } ?: return null
        val estimatedRuntime = cloudlet.length.toDouble() / fastestMips
        return arrivalTime + estimatedRuntime * schedulingConfig.deadlineFactor
    }

    private fun deterministicIndex(cloudletId: Long, salt: Int, modulo: Int): Int {
        if (modulo <= 1) return 0
        return (deterministicUnit(cloudletId, attempt = 0, salt = salt) * modulo).toInt().coerceIn(0, modulo - 1)
    }

    private fun rejectCloudlet(cloudlet: Cloudlet, capacity: Boolean) {
        rejectedCount++
        if (capacity) capacityRejectedCount++
        cloudlet.setStatus(Cloudlet.Status.FAILED)
        vmReservations.remove(cloudlet.id)
        updateMetadata(cloudlet) { it.copy(lifecycle = RealtimeTaskLifecycle.REJECTED) }
    }

    private fun sampleQueueDepth(activeCloudlets: List<Cloudlet>, selectedVmIndex: Int, currentTime: Double) {
        val states = nodeStateTracker.snapshot(activeCloudlets, currentTime, vmReservations)
        val selectedDepth = states.getOrNull(selectedVmIndex)?.queueDepth ?: 0
        queueDepthSampleTotal += selectedDepth
        queueDepthSampleCount++
        if (selectedDepth > maxQueueDepth) maxQueueDepth = selectedDepth
    }

    private fun pruneCompletedReservations() {
        vmReservations.keys.removeIf { cloudletId ->
            val lifecycle = metadataByCloudletId[cloudletId]?.lifecycle
            lifecycle == RealtimeTaskLifecycle.REJECTED || lifecycle == RealtimeTaskLifecycle.FAILED
        }
    }

    private fun cloudletArrivalComparator(): Comparator<Cloudlet> {
        val base = compareBy<Cloudlet> { it.submissionDelay }
        return when (schedulingConfig.normalizedQueuePolicy()) {
            config.RealtimeQueuePolicy.PRIORITY -> base
                .thenBy { metadataByCloudletId[it.id]?.priority ?: Int.MAX_VALUE }
                .thenBy { it.id }
            config.RealtimeQueuePolicy.DEADLINE -> base
                .thenBy { metadataByCloudletId[it.id]?.deadline ?: Double.POSITIVE_INFINITY }
                .thenBy { it.id }
            config.RealtimeQueuePolicy.FIFO -> base.thenBy { it.id }
        }
    }
}
