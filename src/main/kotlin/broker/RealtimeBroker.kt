package broker

import config.RealtimeSchedulingConfig
import org.cloudsimplus.brokers.DatacenterBrokerSimple
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.core.CloudSimPlus
import org.cloudsimplus.core.events.SimEvent
import org.cloudsimplus.vms.Vm
import scheduler.CloudletId
import scheduler.RealtimeNodeStateTracker
import scheduler.RealtimeResourceModel
import scheduler.RealtimeScheduler
import scheduler.RealtimeSchedulingContext
import scheduler.RealtimeTaskLifecycle
import scheduler.RealtimeTaskMetadata
import scheduler.RealtimeTaskRecord
import scheduler.RealtimeTopologyMetrics
import scheduler.RealtimeTopologyModel
import scheduler.RealtimeVmLifecycleManager
import java.util.Random
import kotlin.math.ceil

/**
 * 实时调度代理。
 *
 * 通过 CloudSim 事件在任务到达时提交 cloudlet，并在到达时调用调度器。
 */
class RealtimeBroker(
    private val cloudSim: CloudSimPlus,
    private val scheduler: RealtimeScheduler,
    initialVmList: List<Vm>,
    private val schedulingConfig: RealtimeSchedulingConfig = RealtimeSchedulingConfig()
) : DatacenterBrokerSimple(cloudSim) {

    companion object {
        private const val ARRIVAL_EVENT_TAG = 9001
        private const val SUBMIT_EVENT_TAG = 9002
        private const val TIMEOUT_EVENT_TAG = 9003
        private const val RUNTIME_FAILURE_EVENT_TAG = 9004
        private const val AUTOSCALE_TICK_EVENT_TAG = 9005
    }

    private val waitingCloudlets = mutableListOf<Cloudlet>()
    private val pendingCloudlets = mutableListOf<Cloudlet>()
    private val realtimeCloudlets = mutableListOf<Cloudlet>()
    private val arrivalTimes = mutableMapOf<Long, Double>()
    private val preassignedVmIds = mutableMapOf<Long, Int>()
    private val attempts = mutableMapOf<Long, Int>()
    private val lifecycleStore = RealtimeTaskLifecycleStore()
    private val vmReservations = mutableMapOf<Long, Int>()
    private val topologyModel = RealtimeTopologyModel.fromConfig(schedulingConfig, initialVmList.size)
    private val vmLifecycleManager = RealtimeVmLifecycleManager(initialVmList, schedulingConfig, topologyModel)
    private val vmList: List<Vm> get() = vmLifecycleManager.vmList
    private val nodeStateTracker = RealtimeNodeStateTracker(
        vmLifecycleManager.vmList,
        schedulingConfig.vmQueueCapacity,
        RealtimeResourceModel(
            enabled = schedulingConfig.resourceModelEnabled,
            networkLatency = schedulingConfig.networkLatency,
            imagePullDelay = schedulingConfig.imagePullDelay,
            ioWeight = schedulingConfig.ioWeight,
            ramWeight = schedulingConfig.ramWeight,
            bwWeight = schedulingConfig.bwWeight
        ),
        topologyModel
    )
    private val failureRandom = Random(0L)
    private val admissionController = RealtimeAdmissionController(schedulingConfig)
    private val failureController = RealtimeFailureController(schedulingConfig, ::deterministicUnit)
    private val timeoutController = RealtimeTimeoutController(schedulingConfig)
    private val preemptionController = RealtimePreemptionController(schedulingConfig)
    private val tenantController = RealtimeTenantController(schedulingConfig)
    private var rejectedCount = 0
    private var capacityRejectedCount = 0
    private var resourceRejectedCount = 0
    private var tenantQuotaRejectedCount = 0
    private var submittedCount = 0
    private var retryCount = 0
    private var retrySuccessCount = 0
    private var permanentFailedCount = 0
    private var runtimeFailureCount = 0
    private var timeoutCancelledCount = 0
    private var migrationCount = 0
    private var checkpointRecoveryCount = 0
    private var preemptedCount = 0
    private var preemptionSuccessCount = 0
    private var preemptionFailedCount = 0
    private var preemptionDelayTotal = 0.0
    private var preemptionPenaltyTotal = 0.0
    private var checkpointLossTotal = 0L
    private var hostFailureCount = 0
    private var rackFailureCount = 0
    private var regionFailureCount = 0
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

    private data class CloudletEventPayload(
        val cloudlet: Cloudlet,
        val attempt: Int
    )

    fun submitCloudletListRealtime(cloudletList: List<Cloudlet>) {
        for (cloudlet in cloudletList) {
            arrivalTimes[cloudlet.id] = cloudlet.submissionDelay
            lifecycleStore.put(createMetadata(cloudlet))
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

    fun getResourceRejectedCount(): Int = resourceRejectedCount

    fun getTenantQuotaRejectedCount(): Int = tenantQuotaRejectedCount

    fun getSubmittedCount(): Int = submittedCount

    fun getRetryCount(): Int = retryCount

    fun getRetrySuccessCount(): Int = retrySuccessCount

    fun getPermanentFailedCount(): Int = permanentFailedCount

    fun getRuntimeFailureCount(): Int = runtimeFailureCount

    fun getTimeoutCancelledCount(): Int = timeoutCancelledCount

    fun getMigrationCount(): Int = migrationCount

    fun getCheckpointRecoveryCount(): Int = checkpointRecoveryCount

    fun getScaleOutCount(): Int = vmLifecycleManager.getScaleOutCount()

    fun getScaleInCount(): Int = vmLifecycleManager.getScaleInCount()

    fun getActiveVmPeak(): Int = vmLifecycleManager.getActiveVmPeak()

    fun getAutoscalingCost(): Double = vmLifecycleManager.getAutoscalingCost()

    fun getColdStartDelayTotal(): Double = vmLifecycleManager.getColdStartDelayTotal()

    fun getAverageDecisionDelay(): Double {
        return if (decisionCount > 0) decisionDelayTotal / decisionCount else 0.0
    }

    fun getAverageQueueDepth(): Double {
        return if (queueDepthSampleCount > 0) queueDepthSampleTotal.toDouble() / queueDepthSampleCount.toDouble() else 0.0
    }

    fun getMaxQueueDepth(): Int = maxQueueDepth

    fun getTaskMetadata(cloudlet: Cloudlet): RealtimeTaskMetadata? = lifecycleStore.get(cloudlet.id)

    fun getSlaViolationCount(cloudlets: List<Cloudlet>): Int {
        if (schedulingConfig.deadlineFactor <= 0.0) return 0
        return cloudlets.count { cloudlet ->
            cloudlet.status == Cloudlet.Status.SUCCESS &&
                lifecycleStore.get(cloudlet.id)?.deadline?.let { deadline -> cloudlet.finishTime > deadline } == true
        }
    }

    fun getRetrySuccessRate(): Double {
        return if (retryCount > 0) retrySuccessCount.toDouble() / retryCount.toDouble() else 0.0
    }

    fun getPreemptedCount(): Int = preemptedCount

    fun getPreemptionSuccessCount(): Int = preemptionSuccessCount

    fun getPreemptionFailedCount(): Int = preemptionFailedCount

    fun getAveragePreemptionDelay(): Double =
        if (preemptedCount > 0) preemptionDelayTotal / preemptedCount.toDouble() else 0.0

    fun getPreemptionPenalty(): Double = preemptionPenaltyTotal

    fun getCheckpointLossTotal(): Long = checkpointLossTotal

    fun getTenantFairnessIndex(cloudlets: List<Cloudlet>): Double =
        tenantController.fairnessIndex(cloudlets, lifecycleStore.snapshot())

    fun getTopologyMetrics(cloudlets: List<Cloudlet>): RealtimeTopologyMetrics =
        topologyModel.metricsFor(
            cloudlets.mapNotNull { cloudlet ->
                lifecycleStore.get(cloudlet.id)?.assignedVmIndex ?: vmReservations[cloudlet.id] ?: cloudlet.vm?.id?.toInt()
            }
        )

    fun getHostFailureCount(): Int = hostFailureCount

    fun getRackFailureCount(): Int = rackFailureCount

    fun getRegionFailureCount(): Int = regionFailureCount

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
            TIMEOUT_EVENT_TAG -> {
                val payload = event.data as CloudletEventPayload
                onCloudletTimeout(payload.cloudlet, payload.attempt)
                return
            }
            RUNTIME_FAILURE_EVENT_TAG -> {
                val payload = event.data as CloudletEventPayload
                onRuntimeFailure(payload.cloudlet, payload.attempt)
                return
            }
            AUTOSCALE_TICK_EVENT_TAG -> {
                onAutoscaleTick(event.time)
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
        if (schedulingConfig.autoscalingEnabled && schedulingConfig.scaleInIdleTime > 0.0) {
            schedule(schedulingConfig.scaleInIdleTime, AUTOSCALE_TICK_EVENT_TAG, Unit)
        }
    }

    private fun onCloudletArrival(cloudlet: Cloudlet, arrivalTime: Double) {
        val currentLifecycle = lifecycleStore.get(cloudlet.id)?.lifecycle
        if (currentLifecycle == RealtimeTaskLifecycle.RETRYING || currentLifecycle == RealtimeTaskLifecycle.MIGRATING) {
            updateMetadata(cloudlet) { it.copy(lifecycle = RealtimeTaskLifecycle.ARRIVED) }
        }
        refreshVmLifecycles(arrivalTime)
        var activeCloudlets = getActiveCloudlets()
        submitNewDynamicVmsIfNeeded(activeCloudlets.size, arrivalTime)
        val initialContext = schedulingContext(cloudlet, activeCloudlets, arrivalTime)
        val incomingRecord = lifecycleStore.get(cloudlet.id) ?: createMetadata(cloudlet)
        when (val tenantDecision = tenantController.decide(incomingRecord, activeTenantRecords())) {
            TenantAdmissionDecision.Accepted -> Unit
            is TenantAdmissionDecision.Rejected -> {
                rejectCloudlet(cloudlet, RealtimeRejectReason.TENANT_QUOTA)
                return
            }
        }
        when (val admission = admissionController.decide(activeCloudlets.size, initialContext.nodeStates)) {
            AdmissionDecision.Accepted -> Unit
            is AdmissionDecision.Rejected -> {
                if (!tryPreemptFor(cloudlet, activeCloudlets, arrivalTime)) {
                    rejectCloudlet(cloudlet, admission.reason)
                    return
                }
                activeCloudlets = getActiveCloudlets()
            }
        }

        val selection = selectVmId(cloudlet, activeCloudlets, arrivalTime)
        if (selection == null) {
            if (!tryPreemptFor(cloudlet, activeCloudlets, arrivalTime)) {
                val reason = latestRejectionReason(cloudlet, activeCloudlets, arrivalTime)
                rejectCloudlet(cloudlet, reason)
                return
            }
            activeCloudlets = getActiveCloudlets()
        }

        val finalSelection = selection ?: selectVmId(cloudlet, getActiveCloudlets(), arrivalTime)
        if (finalSelection == null) {
            preemptionFailedCount++
            rejectCloudlet(cloudlet, latestRejectionReason(cloudlet, getActiveCloudlets(), arrivalTime))
            return
        }

        val (selectedVmId, failurePressure) = finalSelection
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
        if (lifecycleStore.get(cloudlet.id)?.lifecycle != RealtimeTaskLifecycle.PENDING_DECISION) {
            pendingCloudlets.removeIf { it.id == cloudlet.id }
            return
        }
        pendingCloudlets.removeIf { it.id == cloudlet.id }
        val attempt = attempts.getOrDefault(cloudlet.id, 0)
        when (val failureDecision = failureController.submitAttempt(CloudletId(cloudlet.id), attempt, submission.failurePressure)) {
            FailureDecision.Continue -> Unit
            is FailureDecision.Retry -> {
                attempts[cloudlet.id] = attempt + 1
                vmReservations.remove(cloudlet.id)
                updateMetadata(cloudlet) {
                    it.copy(
                        attempt = attempt + 1,
                        assignedVmIndex = null,
                        lifecycle = RealtimeTaskLifecycle.RETRYING
                    )
                }
                retryCount++
                schedule(failureDecision.delay, ARRIVAL_EVENT_TAG, cloudlet)
                return
            }
            FailureDecision.PermanentlyFail -> {
                cloudlet.setStatus(Cloudlet.Status.FAILED)
                vmReservations.remove(cloudlet.id)
                updateMetadata(cloudlet) { it.copy(lifecycle = RealtimeTaskLifecycle.FAILED) }
                permanentFailedCount++
                return
            }
        }

        cloudlet.setVm(vmList[submission.vmIndex])
        cloudlet.setSubmissionDelay(0.0)
        waitingCloudlets.add(cloudlet)
        updateMetadata(cloudlet) { it.copy(lifecycle = RealtimeTaskLifecycle.RUNNING) }
        vmLifecycleManager.markBusy(submission.vmIndex, cloudSim.clock())
        if (attempts.getOrDefault(cloudlet.id, 0) > 0) {
            retrySuccessCount++
        }
        submittedCount++
        scheduleRuntimeEvents(cloudlet)
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

    private fun tryPreemptFor(cloudlet: Cloudlet, activeCloudlets: List<Cloudlet>, currentTime: Double): Boolean {
        val incoming = lifecycleStore.get(cloudlet.id) ?: createMetadata(cloudlet)
        val candidates = preemptionController.candidates(incoming, activeCloudlets, lifecycleStore.snapshot(), vmReservations)
        return when (val decision = preemptionController.decide(incoming, candidates)) {
            PreemptionDecision.None -> false
            is PreemptionDecision.Preempt -> preemptVictim(decision, currentTime)
        }
    }

    private fun preemptVictim(decision: PreemptionDecision.Preempt, currentTime: Double): Boolean {
        val victim = (pendingCloudlets + waitingCloudlets).firstOrNull { it.id == decision.victimCloudletId.value }
            ?: return false
        if (victim.status == Cloudlet.Status.SUCCESS || victim.status == Cloudlet.Status.FAILED) return false

        victim.vm?.cloudletScheduler?.cloudletFail(victim)
        pendingCloudlets.removeIf { it.id == victim.id }
        waitingCloudlets.removeIf { it.id == victim.id }
        vmReservations.remove(victim.id)

        val recoveredLength = recoveredLength(victim)
        val originalLength = victim.length
        val lostLength = (originalLength - recoveredLength).coerceAtLeast(0L)
        if (recoveredLength > 0L) {
            checkpointRecoveryCount++
            victim.setLength((originalLength - recoveredLength).coerceAtLeast(1L))
        }
        checkpointLossTotal += lostLength
        preemptedCount++
        preemptionSuccessCount++
        preemptionDelayTotal += decision.delay
        preemptionPenaltyTotal += decision.penalty
        if (schedulingConfig.migrationDelay > 0.0 || decision.delay > 0.0) {
            migrationCount++
        }

        updateMetadata(victim) {
            it.copy(
                assignedVmIndex = null,
                lifecycle = if (schedulingConfig.migrationDelay > 0.0 || decision.delay > 0.0) {
                    RealtimeTaskLifecycle.MIGRATING
                } else {
                    RealtimeTaskLifecycle.PREEMPTED
                },
                preemptedCount = it.preemptedCount + 1,
                preemptionDelayTotal = it.preemptionDelayTotal + decision.delay,
                checkpointRecoveredLength = it.checkpointRecoveredLength + recoveredLength,
                checkpointLossTotal = it.checkpointLossTotal + lostLength,
                migratedCount = it.migratedCount + if (schedulingConfig.migrationDelay > 0.0 || decision.delay > 0.0) 1 else 0
            )
        }

        val attempt = attempts.getOrDefault(victim.id, 0)
        attempts[victim.id] = attempt + 1
        updateMetadata(victim) {
            it.copy(
                attempt = attempts.getOrDefault(victim.id, 0),
                lifecycle = RealtimeTaskLifecycle.RETRYING
            )
        }
        retryCount++
        schedule(decision.delay + schedulingConfig.migrationDelay + failureController.retryDelay(attempt), ARRIVAL_EVENT_TAG, victim)
        return true
    }

    private fun schedulingContext(
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        currentTime: Double
    ): RealtimeSchedulingContext =
        RealtimeSchedulingContext(
            newCloudlet = cloudlet,
            activeCloudlets = activeCloudlets,
            vmList = vmLifecycleManager.vmList,
            currentTime = currentTime,
            nodeStates = nodeStateTracker.snapshot(
                activeCloudlets,
                currentTime,
                vmReservations,
                vmLifecycleManager.snapshots(),
                cloudlet
            ),
            taskMetadata = lifecycleStore.get(cloudlet.id) ?: createMetadata(cloudlet),
            queuePolicy = schedulingConfig.normalizedQueuePolicy(),
            topologyPolicy = schedulingConfig.normalizedTopologyPolicy(),
            preemptionCandidates = preemptionController.candidates(
                incoming = lifecycleStore.get(cloudlet.id) ?: createMetadata(cloudlet),
                activeCloudlets = activeCloudlets,
                records = lifecycleStore.snapshot(),
                vmReservations = vmReservations
            )
        )

    private fun decisionDelay(cloudlet: Cloudlet): Double {
        val jitter = if (schedulingConfig.decisionJitter > 0.0) {
            deterministicUnit(cloudlet.id, attempts.getOrDefault(cloudlet.id, 0), salt = 11) * schedulingConfig.decisionJitter
        } else {
            0.0
        }
        return schedulingConfig.decisionDelay + jitter
    }

    private fun scheduleRuntimeEvents(cloudlet: Cloudlet) {
        val attempt = attempts.getOrDefault(cloudlet.id, 0)
        if (schedulingConfig.taskTimeout > 0.0) {
            val arrivalTime = getArrivalTime(cloudlet)
            val delay = (arrivalTime + schedulingConfig.taskTimeout - cloudSim.clock()).coerceAtLeast(0.0)
            schedule(delay, TIMEOUT_EVENT_TAG, CloudletEventPayload(cloudlet, attempt))
        }

        val runtimeFailureRate = effectiveRuntimeFailureRate(cloudlet)
        if (runtimeFailureRate > 0.0 && deterministicUnit(cloudlet.id, attempt, salt = 61) < runtimeFailureRate) {
            recordTopologyFailure(cloudlet, attempt)
            val runtime = estimatedRuntime(cloudlet)
            val delay = (runtime * (0.25 + deterministicUnit(cloudlet.id, attempt, salt = 67) * 0.5)).coerceAtLeast(0.001)
            schedule(delay, RUNTIME_FAILURE_EVENT_TAG, CloudletEventPayload(cloudlet, attempt))
        }
    }

    private fun effectiveRuntimeFailureRate(cloudlet: Cloudlet): Double {
        val assignedVmIndex = lifecycleStore.get(cloudlet.id)?.assignedVmIndex ?: 0
        val states = nodeStateTracker.snapshot(getActiveCloudlets(), cloudSim.clock(), vmReservations, vmLifecycleManager.snapshots())
        val pressure = states.getOrNull(assignedVmIndex)?.failurePressure ?: 0.0
        return (schedulingConfig.runtimeFailureRate +
            schedulingConfig.nodeFailureRate +
            pressure * schedulingConfig.overloadFailureMultiplier +
            topologyModel.failurePressure(topologyModel.locationOf(assignedVmIndex))).coerceIn(0.0, 1.0)
    }

    private fun recordTopologyFailure(cloudlet: Cloudlet, attempt: Int) {
        if (!schedulingConfig.topologyEnabled) return
        val assignedVmIndex = lifecycleStore.get(cloudlet.id)?.assignedVmIndex ?: return
        val location = topologyModel.locationOf(assignedVmIndex)
        val unit = deterministicUnit(cloudlet.id, attempt, salt = 71)
        val hostCutoff = schedulingConfig.hostFailureRate
        val rackCutoff = hostCutoff + schedulingConfig.rackFailureRate
        val regionCutoff = rackCutoff + if (location.regionId.value != schedulingConfig.localRegion) {
            schedulingConfig.regionFailureRate
        } else {
            0.0
        }
        when {
            unit < hostCutoff -> hostFailureCount++
            unit < rackCutoff -> rackFailureCount++
            unit < regionCutoff -> regionFailureCount++
        }
    }

    private fun onCloudletTimeout(cloudlet: Cloudlet, attempt: Int) {
        if (attempt != attempts.getOrDefault(cloudlet.id, 0)) return
        if (cloudlet.status == Cloudlet.Status.SUCCESS || cloudlet.status == Cloudlet.Status.FAILED) return
        when (timeoutController.decide().action) {
            config.RealtimeTimeoutAction.FAIL -> failRunningCloudlet(cloudlet, "timeout_fail", retry = false)
            config.RealtimeTimeoutAction.CANCEL -> {
                cancelCloudlet(cloudlet)
                timeoutCancelledCount++
                updateMetadata(cloudlet) { it.copy(timeoutActionTaken = schedulingConfig.timeoutAction) }
            }
            config.RealtimeTimeoutAction.RETRY -> {
                timeoutCancelledCount++
                retryInterruptedCloudlet(cloudlet, "timeout_retry")
            }
            config.RealtimeTimeoutAction.DEGRADE -> {
                val degradedLength = (cloudlet.length * 0.75).toLong().coerceAtLeast(1L)
                cloudlet.setLength(degradedLength)
                updateMetadata(cloudlet) { it.copy(timeoutActionTaken = schedulingConfig.timeoutAction) }
            }
        }
    }

    private fun onRuntimeFailure(cloudlet: Cloudlet, attempt: Int) {
        if (attempt != attempts.getOrDefault(cloudlet.id, 0)) return
        if (cloudlet.status == Cloudlet.Status.SUCCESS || cloudlet.status == Cloudlet.Status.FAILED) return
        runtimeFailureCount++
        retryInterruptedCloudlet(cloudlet, "runtime_failure")
    }

    private fun retryInterruptedCloudlet(cloudlet: Cloudlet, reason: String) {
        val scheduler = cloudlet.vm?.cloudletScheduler
        scheduler?.cloudletFail(cloudlet)
        if (schedulingConfig.migrationDelay > 0.0) migrationCount++
        val recoveredLength = recoveredLength(cloudlet)
        val lostLength = (cloudlet.length - recoveredLength).coerceAtLeast(0L)
        if (recoveredLength > 0L) {
            checkpointRecoveryCount++
            cloudlet.setLength((cloudlet.length - recoveredLength).coerceAtLeast(1L))
        }
        updateMetadata(cloudlet) {
            it.copy(
                interruptedCount = it.interruptedCount + 1,
                checkpointRecoveredLength = it.checkpointRecoveredLength + recoveredLength,
                checkpointLossTotal = it.checkpointLossTotal + lostLength,
                timeoutActionTaken = if (reason.startsWith("timeout")) schedulingConfig.timeoutAction else it.timeoutActionTaken,
                migratedCount = it.migratedCount + if (schedulingConfig.migrationDelay > 0.0) 1 else 0
            )
        }
        checkpointLossTotal += lostLength
        retryCloudlet(cloudlet)
    }

    private fun failRunningCloudlet(cloudlet: Cloudlet, reason: String, retry: Boolean) {
        cloudlet.vm?.cloudletScheduler?.cloudletFail(cloudlet)
        updateMetadata(cloudlet) { it.copy(timeoutActionTaken = reason, lifecycle = RealtimeTaskLifecycle.FAILED) }
        if (retry) {
            retryCloudlet(cloudlet)
        } else {
            vmReservations.remove(cloudlet.id)
            permanentFailedCount++
        }
    }

    private fun cancelCloudlet(cloudlet: Cloudlet) {
        cloudlet.vm?.cloudletScheduler?.cloudletCancel(cloudlet)
        cloudlet.setStatus(Cloudlet.Status.FAILED)
        vmReservations.remove(cloudlet.id)
        updateMetadata(cloudlet) { it.copy(lifecycle = RealtimeTaskLifecycle.FAILED) }
        permanentFailedCount++
    }

    private fun retryCloudlet(cloudlet: Cloudlet) {
        val attempt = attempts.getOrDefault(cloudlet.id, 0)
        if (attempt >= schedulingConfig.retryLimit) {
            cloudlet.setStatus(Cloudlet.Status.FAILED)
            vmReservations.remove(cloudlet.id)
            permanentFailedCount++
            updateMetadata(cloudlet) { it.copy(lifecycle = RealtimeTaskLifecycle.FAILED) }
            return
        }
        attempts[cloudlet.id] = attempt + 1
        vmReservations.remove(cloudlet.id)
        waitingCloudlets.removeIf { it.id == cloudlet.id }
        pendingCloudlets.removeIf { it.id == cloudlet.id }
        updateMetadata(cloudlet) {
            it.copy(
                attempt = attempt + 1,
                assignedVmIndex = null,
                lifecycle = RealtimeTaskLifecycle.RETRYING
            )
        }
        retryCount++
        schedule(failureController.retryDelay(attempt) + schedulingConfig.migrationDelay, ARRIVAL_EVENT_TAG, cloudlet)
    }

    private fun recoveredLength(cloudlet: Cloudlet): Long {
        if (schedulingConfig.checkpointInterval <= 0.0) return 0L
        val elapsed = (cloudSim.clock() - cloudlet.getStartTime()).coerceAtLeast(0.0)
        val checkpoints = kotlin.math.floor(elapsed / schedulingConfig.checkpointInterval).toLong()
        if (checkpoints <= 0) return 0L
        val runtime = estimatedRuntime(cloudlet).coerceAtLeast(0.001)
        return (cloudlet.length * (elapsed / runtime).coerceIn(0.0, 1.0)).toLong()
    }

    private fun estimatedRuntime(cloudlet: Cloudlet): Double {
        val vm = cloudlet.vm ?: vmList.first()
        return cloudlet.length.toDouble() / vm.mips.coerceAtLeast(1.0)
    }

    private fun deterministicUnit(cloudletId: CloudletId, attempt: Int, salt: Int): Double {
        failureRandom.setSeed(cloudletId.value * 1_000_003L + attempt * 9_176L + salt)
        return failureRandom.nextDouble()
    }

    private fun deterministicUnit(cloudletId: Long, attempt: Int, salt: Int): Double =
        deterministicUnit(CloudletId(cloudletId), attempt, salt)

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

    private fun createMetadata(cloudlet: Cloudlet): RealtimeTaskRecord {
        val arrivalTime = arrivalTimes[cloudlet.id] ?: cloudlet.submissionDelay
        return RealtimeTaskRecord(
            cloudletId = cloudlet.id,
            originalArrivalTime = arrivalTime,
            attempt = attempts.getOrDefault(cloudlet.id, 0),
            priority = priorityFor(cloudlet),
            deadline = deadlineFor(cloudlet, arrivalTime),
            tenantId = tenantController.tenantFor(CloudletId(cloudlet.id), ::deterministicUnit)
        )
    }

    private fun updateMetadata(cloudlet: Cloudlet, transform: (RealtimeTaskRecord) -> RealtimeTaskRecord) {
        lifecycleStore.updateOrPut(createMetadata(cloudlet), transform)
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

    private fun rejectCloudlet(cloudlet: Cloudlet, reason: RealtimeRejectReason) {
        rejectedCount++
        when (reason) {
            RealtimeRejectReason.QUEUE -> Unit
            RealtimeRejectReason.CAPACITY -> capacityRejectedCount++
            RealtimeRejectReason.RESOURCE -> resourceRejectedCount++
            RealtimeRejectReason.TENANT_QUOTA -> tenantQuotaRejectedCount++
        }
        cloudlet.setStatus(Cloudlet.Status.FAILED)
        vmReservations.remove(cloudlet.id)
        updateMetadata(cloudlet) { it.copy(lifecycle = RealtimeTaskLifecycle.REJECTED) }
    }

    private fun sampleQueueDepth(activeCloudlets: List<Cloudlet>, selectedVmIndex: Int, currentTime: Double) {
        val states = nodeStateTracker.snapshot(activeCloudlets, currentTime, vmReservations, vmLifecycleManager.snapshots())
        val selectedDepth = states.getOrNull(selectedVmIndex)?.queueDepth ?: 0
        queueDepthSampleTotal += selectedDepth
        queueDepthSampleCount++
        if (selectedDepth > maxQueueDepth) maxQueueDepth = selectedDepth
    }

    private fun pruneCompletedReservations() {
        vmReservations.keys.removeIf { cloudletId ->
            val lifecycle = lifecycleStore.get(cloudletId)?.lifecycle
            lifecycle == RealtimeTaskLifecycle.REJECTED || lifecycle == RealtimeTaskLifecycle.FAILED
        }
    }

    private fun activeTenantRecords(): List<RealtimeTaskRecord> =
        lifecycleStore.snapshot().filter { record ->
            record.lifecycle == RealtimeTaskLifecycle.PENDING_DECISION ||
                record.lifecycle == RealtimeTaskLifecycle.SUBMITTED ||
                record.lifecycle == RealtimeTaskLifecycle.RUNNING ||
                record.lifecycle == RealtimeTaskLifecycle.PREEMPTED ||
                record.lifecycle == RealtimeTaskLifecycle.MIGRATING ||
                record.lifecycle == RealtimeTaskLifecycle.RETRYING
        }

    private fun submitNewDynamicVmsIfNeeded(queueDepth: Int, currentTime: Double) {
        val activeIndexes = getActiveCloudlets().mapNotNull { vmReservations[it.id] ?: it.vm?.id?.toInt() }.toSet()
        val newVms = vmLifecycleManager.maybeScaleOut(queueDepth, currentTime, activeIndexes)
        if (newVms.isNotEmpty()) {
            submitVmList(newVms, schedulingConfig.vmColdStartDelay)
            if (schedulingConfig.scaleInIdleTime > 0.0) {
                schedule(schedulingConfig.scaleInIdleTime, AUTOSCALE_TICK_EVENT_TAG, Unit)
            }
        }
    }

    private fun refreshVmLifecycles(currentTime: Double) {
        val activeIndexes = getActiveCloudlets().mapNotNull { vmReservations[it.id] ?: it.vm?.id?.toInt() }.toSet()
        vmLifecycleManager.refresh(currentTime, activeIndexes)
    }

    private fun onAutoscaleTick(currentTime: Double) {
        val activeIndexes = getActiveCloudlets().mapNotNull { vmReservations[it.id] ?: it.vm?.id?.toInt() }.toSet()
        vmLifecycleManager.refresh(currentTime, activeIndexes)
        vmLifecycleManager.maybeScaleIn(currentTime, activeIndexes)
        if (schedulingConfig.autoscalingEnabled &&
            schedulingConfig.scaleInIdleTime > 0.0 &&
            vmLifecycleManager.hasLiveDynamicVms()
        ) {
            schedule(schedulingConfig.scaleInIdleTime, AUTOSCALE_TICK_EVENT_TAG, Unit)
        }
    }

    private fun latestRejectionReason(cloudlet: Cloudlet, activeCloudlets: List<Cloudlet>, currentTime: Double): RealtimeRejectReason {
        val states = nodeStateTracker.snapshot(activeCloudlets, currentTime, vmReservations, vmLifecycleManager.snapshots(), cloudlet)
        return if (states.any { !it.resourceAcceptingWork }) RealtimeRejectReason.RESOURCE else RealtimeRejectReason.CAPACITY
    }

    private fun cloudletArrivalComparator(): Comparator<Cloudlet> {
        val base = compareBy<Cloudlet> { it.submissionDelay }
        return when (schedulingConfig.normalizedQueuePolicy()) {
            config.RealtimeQueuePolicy.PRIORITY -> base
                .thenBy { lifecycleStore.get(it.id)?.priority ?: Int.MAX_VALUE }
                .thenBy { it.id }
            config.RealtimeQueuePolicy.DEADLINE -> base
                .thenBy { lifecycleStore.get(it.id)?.deadline ?: Double.POSITIVE_INFINITY }
                .thenBy { it.id }
            config.RealtimeQueuePolicy.FIFO -> base.thenBy { it.id }
        }
    }
}
