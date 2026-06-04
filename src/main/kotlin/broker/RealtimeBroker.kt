package broker

import config.RealtimeSchedulingConfig
import datacenter.MutableRealtimeTraceMetadataProvider
import datacenter.RealtimeCloudletBatch
import datacenter.RealtimeCloudletSpec
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

private const val FAILURE_RANDOM_CLOUDLET_MULTIPLIER = 1_000_003L
private const val FAILURE_RANDOM_ATTEMPT_MULTIPLIER = 9_176L
private const val DECISION_JITTER_SALT = 11

/**
 * 实时调度代理。
 *
 * 通过 CloudSim 事件在任务到达时提交 cloudlet，并在到达时调用调度器。
 */
class RealtimeBroker(
    private val cloudSim: CloudSimPlus,
    private val scheduler: RealtimeScheduler,
    initialVmList: List<Vm>,
    private val schedulingConfig: RealtimeSchedulingConfig = RealtimeSchedulingConfig(),
) : DatacenterBrokerSimple(cloudSim) {
    private val arrivalState = RealtimeArrivalState()
    private val lifecycleStore = RealtimeTaskLifecycleStore()
    private val reservationState = RealtimeReservationState()
    private val topologyModel = RealtimeTopologyModel.fromConfig(schedulingConfig, initialVmList.size)
    private val vmLifecycleManager = RealtimeVmLifecycleManager(initialVmList, schedulingConfig, topologyModel)
    private val vmList: List<Vm> get() = vmLifecycleManager.vmList
    private val traceMetadataProvider = MutableRealtimeTraceMetadataProvider()
    private val nodeStateTracker =
        RealtimeNodeStateTracker(
            vmLifecycleManager.vmList,
            schedulingConfig.vmQueueCapacity,
            RealtimeResourceModel(
                enabled = schedulingConfig.resourceModelEnabled,
                networkLatency = schedulingConfig.networkLatency,
                imagePullDelay = schedulingConfig.imagePullDelay,
                ioWeight = schedulingConfig.ioWeight,
                ramWeight = schedulingConfig.ramWeight,
                bwWeight = schedulingConfig.bwWeight,
                traceMetadataProvider = traceMetadataProvider,
            ),
            topologyModel,
        )
    private val failureRandom = Random(0L)
    private val admissionController = RealtimeAdmissionController(schedulingConfig)
    private val failureController = RealtimeFailureController(schedulingConfig, ::deterministicUnit)
    private val timeoutController = RealtimeTimeoutController(schedulingConfig)
    private val preemptionController = RealtimePreemptionController(schedulingConfig)
    private val tenantController = RealtimeTenantController(schedulingConfig)
    private val taskMetadataFactory =
        RealtimeTaskMetadataFactory(
            schedulingConfig,
            traceMetadataProvider,
            tenantController,
            ::deterministicUnit,
        )
    private val tenantFairnessContextBuilder = TenantFairnessContextBuilder(tenantController)
    private val vmReservationPolicy = RealtimeVmReservationPolicy(schedulingConfig)
    private val brokerMetrics = RealtimeBrokerMetrics()
    private val recoveryEstimator =
        RealtimeCloudletRecoveryEstimator(
            schedulingConfig,
            { cloudSim.clock() },
            { vmList },
        )
    private val runtimeEventController =
        RealtimeRuntimeEventController(
            schedulingConfig,
            topologyModel,
            { cloudletId, attempt, salt -> deterministicUnit(cloudletId, attempt, salt) },
            recoveryEstimator::estimatedRuntime,
        )
    private val topologyAccountingController = RealtimeTopologyAccountingController(topologyModel, brokerMetrics)
    private val autoscalingController = RealtimeAutoscalingController(schedulingConfig, vmLifecycleManager)
    private val interruptionController =
        RealtimeTaskInterruptionController(
            schedulingConfig,
            RealtimeTaskInterruptionState(arrivalState, reservationState, brokerMetrics),
            RealtimeTaskInterruptionServices(
                failureController,
                timeoutController,
                recoveryEstimator,
                ::updateMetadata,
            ),
        )
    private val preemptionExecutor =
        RealtimePreemptionExecutor(
            schedulingConfig,
            RealtimePreemptionState(arrivalState, reservationState, brokerMetrics),
            RealtimePreemptionServices(
                failureController,
                recoveryEstimator,
                ::updateMetadata,
            ),
        )
    private val eventRouter = RealtimeBrokerEventRouter()
    private val cloudletOrdering = RealtimeCloudletOrdering(schedulingConfig, lifecycleStore)
    private val commandExecutor =
        RealtimeBrokerCommandExecutor(
            schedule = { delay, tag, data -> schedule(delay, tag, data) },
            submitVms = { vms, delay -> submitVmList(vms, delay) },
        )
    private val arrivalWorkflow = RealtimeArrivalWorkflow(ArrivalWorkflowAdapter())
    private val submissionWorkflow = RealtimeSubmissionWorkflow(SubmissionWorkflowAdapter())
    private val readModel =
        RealtimeBrokerReadModel(
            schedulingConfig,
            arrivalState,
            lifecycleStore,
            reservationState,
            brokerMetrics,
            vmLifecycleManager,
            tenantController,
            topologyModel,
        )

    fun submitCloudletBatchRealtime(batch: RealtimeCloudletBatch) {
        submitCloudletSpecsRealtime(batch.specs)
    }

    fun submitCloudletSpecsRealtime(specs: List<RealtimeCloudletSpec>) {
        traceMetadataProvider.putAll(specs)
        submitCloudletListRealtime(specs.map { it.cloudlet })
    }

    fun submitCloudletListRealtime(cloudletList: List<Cloudlet>) {
        for (cloudlet in cloudletList) {
            arrivalState.recordArrival(cloudlet)
            lifecycleStore.put(createMetadata(cloudlet))
        }
        val sortedCloudlets = cloudletList.sortedWith(cloudletOrdering.arrivalComparator())
        if (schedulingConfig.strategy.equals("static", ignoreCase = true)) {
            val previewWaiting = mutableListOf<Cloudlet>()
            for (cloudlet in sortedCloudlets) {
                val context = schedulingContext(cloudlet, previewWaiting.toList(), cloudlet.submissionDelay)
                arrivalState.preassign(cloudlet, scheduler.scheduleOnArrival(context))
                previewWaiting.add(cloudlet)
            }
        }

        arrivalState.addRealtimeCloudlets(sortedCloudlets)
    }

    fun getWaitingCloudlets(): List<Cloudlet> = readModel.waitingCloudlets()

    private fun getActiveCloudlets(): List<Cloudlet> = readModel.activeCloudlets()

    fun getRejectedCount(): Int = readModel.rejectedCount()

    fun getCapacityRejectedCount(): Int = readModel.capacityRejectedCount()

    fun getResourceRejectedCount(): Int = readModel.resourceRejectedCount()

    fun getTenantQuotaRejectedCount(): Int = readModel.tenantQuotaRejectedCount()

    fun getTenantBudgetRejectedCount(): Int = readModel.tenantBudgetRejectedCount()

    fun getSubmittedCount(): Int = readModel.submittedCount()

    fun getRetryCount(): Int = readModel.retryCount()

    fun getRetrySuccessCount(): Int = readModel.retrySuccessCount()

    fun getPermanentFailedCount(): Int = readModel.permanentFailedCount()

    fun getRuntimeFailureCount(): Int = readModel.runtimeFailureCount()

    fun getTimeoutCancelledCount(): Int = readModel.timeoutCancelledCount()

    fun getMigrationCount(): Int = readModel.migrationCount()

    fun getCheckpointRecoveryCount(): Int = readModel.checkpointRecoveryCount()

    fun getScaleOutCount(): Int = readModel.scaleOutCount()

    fun getScaleInCount(): Int = readModel.scaleInCount()

    fun getActiveVmPeak(): Int = readModel.activeVmPeak()

    fun getAutoscalingCost(): Double = readModel.autoscalingCost()

    fun getColdStartDelayTotal(): Double = readModel.coldStartDelayTotal()

    fun getAverageDecisionDelay(): Double = readModel.averageDecisionDelay()

    fun getAverageQueueDepth(): Double = readModel.averageQueueDepth()

    fun getMaxQueueDepth(): Int = readModel.maxQueueDepth()

    fun getTaskMetadata(cloudlet: Cloudlet): RealtimeTaskMetadata? = readModel.taskMetadata(cloudlet)

    fun getSlaViolationCount(cloudlets: List<Cloudlet>): Int = readModel.slaViolationCount(cloudlets)

    fun getRetrySuccessRate(): Double = readModel.retrySuccessRate()

    fun getPreemptedCount(): Int = readModel.preemptedCount()

    fun getPreemptionSuccessCount(): Int = readModel.preemptionSuccessCount()

    fun getPreemptionFailedCount(): Int = readModel.preemptionFailedCount()

    fun getAveragePreemptionDelay(): Double = readModel.averagePreemptionDelay()

    fun getPreemptionPenalty(): Double = readModel.preemptionPenalty()

    fun getCheckpointLossTotal(): Long = readModel.checkpointLossTotal()

    fun getTenantFairnessIndex(cloudlets: List<Cloudlet>): Double = readModel.tenantFairnessIndex(cloudlets)

    fun getDominantResourceFairnessIndex(): Double = readModel.dominantResourceFairnessIndex()

    fun getFairnessViolationCount(): Int = readModel.fairnessViolationCount()

    fun getTenantSlaPenalty(cloudlets: List<Cloudlet>): Double = readModel.tenantSlaPenalty(cloudlets)

    fun getCostSlaTradeoffScore(
        cost: Double,
        tenantSlaPenalty: Double,
    ): Double = readModel.costSlaTradeoffScore(cost, tenantSlaPenalty)

    fun getRetrySuccessByTenant(cloudlets: List<Cloudlet>): Double = readModel.retrySuccessByTenant(cloudlets)

    fun getTopologyMetrics(cloudlets: List<Cloudlet>): RealtimeTopologyMetrics = readModel.topologyMetrics(cloudlets)

    fun getHostFailureCount(): Int = readModel.hostFailureCount()

    fun getRackFailureCount(): Int = readModel.rackFailureCount()

    fun getRegionFailureCount(): Int = readModel.regionFailureCount()

    fun getArrivalTime(cloudlet: Cloudlet): Double = readModel.arrivalTime(cloudlet)

    fun getTimeoutCount(timeoutSeconds: Double): Int = readModel.timeoutCount(timeoutSeconds)

    override fun processEvent(event: SimEvent) {
        when (val brokerEvent = eventRouter.route(event)) {
            is RealtimeBrokerEvent.Arrival ->
                applyCommands(arrivalWorkflow.onArrival(brokerEvent.cloudlet, brokerEvent.time))
            is RealtimeBrokerEvent.Submit ->
                applyCommands(submissionWorkflow.onSubmit(brokerEvent.submission))
            is RealtimeBrokerEvent.Timeout ->
                applyCommands(
                    interruptionController.onTimeout(brokerEvent.payload.cloudlet, brokerEvent.payload.attempt),
                )
            is RealtimeBrokerEvent.RuntimeFailure ->
                applyCommands(
                    interruptionController.onRuntimeFailure(brokerEvent.payload.cloudlet, brokerEvent.payload.attempt),
                )
            is RealtimeBrokerEvent.AutoscaleTick ->
                applyCommands(onAutoscaleTick(brokerEvent.time))
            is RealtimeBrokerEvent.Unknown ->
                super.processEvent(brokerEvent.event)
        }
    }

    private fun applyCommands(commands: Iterable<RealtimeBrokerCommand>) {
        commandExecutor.applyAll(commands)
    }

    private fun applyCommand(command: RealtimeBrokerCommand) {
        commandExecutor.apply(command)
    }

    override fun startInternal() {
        super.startInternal()
        for (cloudlet in arrivalState.realtimeCloudletsSnapshot()) {
            schedule(cloudlet.submissionDelay, RealtimeBrokerEventTags.ARRIVAL, cloudlet)
        }
        if (schedulingConfig.autoscalingEnabled && schedulingConfig.scaleInIdleTime > 0.0) {
            applyCommand(RealtimeBrokerCommand.ScheduleAutoscaleTick(schedulingConfig.scaleInIdleTime))
        }
    }

    private fun lifecycleOf(cloudlet: Cloudlet): RealtimeTaskLifecycle? = lifecycleStore.get(cloudlet.id)?.lifecycle

    private fun markArrivedAfterInterruption(cloudlet: Cloudlet) {
        updateMetadata(cloudlet) { it.copy(lifecycle = RealtimeTaskLifecycle.ARRIVED) }
    }

    private fun activeCloudlets(): List<Cloudlet> = getActiveCloudlets()

    private fun scaleOutCommands(
        queueDepth: Int,
        currentTime: Double,
    ): List<RealtimeBrokerCommand> = autoscalingController.scaleOutCommands(queueDepth, currentTime, activeVmIndexes())

    private fun taskRecord(cloudlet: Cloudlet) = lifecycleStore.get(cloudlet.id) ?: createMetadata(cloudlet)

    private fun decideTenantAdmission(record: RealtimeTaskRecord): TenantAdmissionDecision =
        tenantController.decide(record, activeTenantRecords())

    private fun decideCapacityAdmission(
        activeCloudletCount: Int,
        context: RealtimeSchedulingContext,
    ): AdmissionDecision = admissionController.decide(activeCloudletCount, context.nodeStates)

    private fun selectVm(
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        currentTime: Double,
    ): Pair<Int, Double>? = selectVmId(cloudlet, activeCloudlets, currentTime)

    private fun recordDecisionDelay(delay: Double) {
        brokerMetrics.recordDecisionDelay(delay)
    }

    private fun preparePendingSubmission(request: RealtimePendingSubmissionRequest): RealtimePendingSubmission {
        val cloudlet = request.cloudlet
        val boundedIndex = request.selectedVmIndex.coerceIn(vmList.indices)
        cloudlet.setVm(vmList[boundedIndex])
        reservationState.reserve(cloudlet, boundedIndex)
        arrivalState.addPending(cloudlet)
        updateMetadata(cloudlet) {
            it.copy(
                assignedVmIndex = boundedIndex,
                lastDecisionDelay = request.delay,
                lifecycle = RealtimeTaskLifecycle.PENDING_DECISION,
            )
        }
        sampleQueueDepth(request.activeCloudlets, boundedIndex, request.currentTime)
        return RealtimePendingSubmission(cloudlet, boundedIndex, request.delay, request.failurePressure)
    }

    private fun recordPreemptionFailed() {
        brokerMetrics.recordPreemptionFailed()
    }

    private fun isPendingDecision(cloudlet: Cloudlet): Boolean =
        lifecycleStore.get(cloudlet.id)?.lifecycle == RealtimeTaskLifecycle.PENDING_DECISION

    private fun discardPending(cloudlet: Cloudlet) {
        arrivalState.removePending(CloudletId(cloudlet.id))
    }

    private fun attemptOf(cloudlet: Cloudlet): Int = arrivalState.attemptOf(cloudlet)

    private fun submitAttemptDecision(
        cloudletId: CloudletId,
        attempt: Int,
        failurePressure: Double,
    ): FailureDecision = failureController.submitAttempt(cloudletId, attempt, failurePressure)

    private fun retryPendingSubmission(
        cloudlet: Cloudlet,
        attempt: Int,
        delay: Double,
    ): RealtimeBrokerCommand {
        arrivalState.incrementAttempt(cloudlet)
        reservationState.remove(cloudlet)
        updateMetadata(cloudlet) {
            it.copy(
                attempt = attempt + 1,
                assignedVmIndex = null,
                lifecycle = RealtimeTaskLifecycle.RETRYING,
            )
        }
        brokerMetrics.recordRetry()
        return RealtimeBrokerCommand.ScheduleArrival(delay, cloudlet)
    }

    private fun permanentlyFailPendingSubmission(cloudlet: Cloudlet) {
        cloudlet.setStatus(Cloudlet.Status.FAILED)
        reservationState.remove(cloudlet)
        updateMetadata(cloudlet) { it.copy(lifecycle = RealtimeTaskLifecycle.FAILED) }
        brokerMetrics.recordPermanentFailure()
    }

    private fun submitAcceptedCloudlet(submission: RealtimePendingSubmission) {
        val cloudlet = submission.cloudlet
        cloudlet.setVm(vmList[submission.vmIndex])
        cloudlet.setSubmissionDelay(0.0)
        arrivalState.addWaiting(cloudlet)
        updateMetadata(cloudlet) { it.copy(lifecycle = RealtimeTaskLifecycle.RUNNING) }
        lifecycleStore.get(cloudlet.id)?.let { record ->
            topologyAccountingController.recordSubmission(submission.vmIndex, record)
        }
        vmLifecycleManager.markBusy(submission.vmIndex, cloudSim.clock())
        if (arrivalState.attemptOf(cloudlet) > 0) {
            brokerMetrics.recordRetrySuccess()
        }
        brokerMetrics.recordSubmitted()
        scheduleRuntimeEvents(cloudlet, submission.failurePressure)
        submitCloudlet(cloudlet)
    }

    private fun selectVmId(
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        currentTime: Double,
    ): Pair<Int, Double>? {
        val strategy = schedulingConfig.strategy.lowercase()
        val context = schedulingContext(cloudlet, activeCloudlets, currentTime)
        val selected = schedulerSelectedVm(strategy, cloudlet, activeCloudlets, currentTime, context)
        return selected?.let { validatedVmSelection(it, cloudlet, activeCloudlets, context) }
    }

    private fun schedulerSelectedVm(
        strategy: String,
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        currentTime: Double,
        context: RealtimeSchedulingContext,
    ): Int? {
        if (context.hasNoAcceptedCapacityCandidate()) return null
        return if (strategy == "static") {
            arrivalState.preassignedVmIndexOf(cloudlet)
                ?: scheduler.scheduleOnArrival(schedulingContext(cloudlet, activeCloudlets, currentTime))
        } else {
            scheduler.scheduleOnArrival(context)
        }
    }

    private fun validatedVmSelection(
        selectedVmIndex: Int,
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        context: RealtimeSchedulingContext,
    ): Pair<Int, Double>? {
        val reserved = applyReservationPolicy(selectedVmIndex, cloudlet, activeCloudlets)
        val bounded = reserved.coerceIn(vmList.indices)
        val placementState = context.acceptedCandidates.firstOrNull { it.vmIndex == bounded }?.nodeState
        val state = placementState ?: context.nodeStates.getOrNull(bounded)
        return when {
            context.nodeCandidates.isNotEmpty() && placementState == null -> null
            state != null && !state.acceptingWork -> null
            else -> bounded to (state?.failurePressure ?: 0.0)
        }
    }

    private fun tryPreemptFor(
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
    ): Boolean {
        val incoming = lifecycleStore.get(cloudlet.id) ?: createMetadata(cloudlet)
        val candidates =
            preemptionController.candidates(
                incoming,
                activeCloudlets,
                lifecycleStore.snapshot(),
                reservationState.rawReservations(),
            )
        return when (val decision = preemptionController.decide(candidates)) {
            PreemptionDecision.None -> false
            is PreemptionDecision.Preempt -> {
                val result = preemptionExecutor.preempt(decision)
                applyCommands(result.commands)
                result.applied
            }
        }
    }

    private fun schedulingContext(
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        currentTime: Double,
    ): RealtimeSchedulingContext {
        val taskMetadata = lifecycleStore.get(cloudlet.id) ?: createMetadata(cloudlet)
        val records = lifecycleStore.snapshot()
        val nodeStates =
            nodeStateTracker.snapshot(
                activeCloudlets,
                currentTime,
                reservationState.rawReservations(),
                vmLifecycleManager.snapshots(),
                cloudlet,
            )
        val nodeCandidates =
            topologyModel.candidatesFor(
                states = nodeStates,
                vmList = vmLifecycleManager.vmList,
                workload = taskMetadata.workloadDescriptor(),
                records = records,
            )
        return RealtimeSchedulingContext(
            newCloudlet = cloudlet,
            activeCloudlets = activeCloudlets,
            vmList = vmLifecycleManager.vmList,
            currentTime = currentTime,
            nodeStates = nodeStates,
            taskMetadata = taskMetadata,
            queuePolicy = schedulingConfig.normalizedQueuePolicy(),
            topologyPolicy = schedulingConfig.normalizedTopologyPolicy(),
            tenantSchedulingPolicy = schedulingConfig.normalizedTenantSchedulingPolicy(),
            tenantSnapshots = tenantFairnessContextBuilder.snapshots(records),
            nodeCandidates = nodeCandidates,
            preemptionCandidates =
                preemptionController.candidates(
                    incoming = taskMetadata,
                    activeCloudlets = activeCloudlets,
                    records = records,
                    vmReservations = reservationState.rawReservations(),
                ),
        )
    }

    private fun decisionDelay(cloudlet: Cloudlet): Double {
        val jitter =
            if (schedulingConfig.decisionJitter > 0.0) {
                deterministicUnit(
                    cloudlet.id,
                    arrivalState.attemptOf(cloudlet),
                    salt = DECISION_JITTER_SALT,
                ) * schedulingConfig.decisionJitter
            } else {
                0.0
            }
        return schedulingConfig.decisionDelay + jitter
    }

    private fun scheduleRuntimeEvents(
        cloudlet: Cloudlet,
        submissionFailurePressure: Double,
    ) {
        val attempt = arrivalState.attemptOf(cloudlet)
        val assignedVmIndex = lifecycleStore.get(cloudlet.id)?.assignedVmIndex ?: 0
        val pressure = runtimeFailurePressure(assignedVmIndex, submissionFailurePressure)
        val plan =
            runtimeEventController.planRuntimeEvents(
                cloudlet = cloudlet,
                attempt = attempt,
                timing =
                    RealtimeRuntimeEventTiming(
                        arrivalTime = getArrivalTime(cloudlet),
                        currentTime = cloudSim.clock(),
                    ),
                assignment =
                    RealtimeRuntimeEventAssignment(
                        vmIndex = assignedVmIndex,
                        nodeFailurePressure = pressure,
                    ),
            )
        topologyAccountingController.recordFailure(plan.topologyFailureDomain)
        applyCommands(plan.commands)
    }

    private fun runtimeFailurePressure(
        assignedVmIndex: Int,
        submissionFailurePressure: Double,
    ): Double {
        val states =
            nodeStateTracker.snapshot(
                getActiveCloudlets(),
                cloudSim.clock(),
                reservationState.rawReservations(),
                vmLifecycleManager.snapshots(),
            )
        return states.getOrNull(assignedVmIndex)?.failurePressure ?: submissionFailurePressure
    }

    private fun deterministicUnit(
        cloudletId: CloudletId,
        attempt: Int,
        salt: Int,
    ): Double {
        failureRandom.setSeed(
            cloudletId.value * FAILURE_RANDOM_CLOUDLET_MULTIPLIER +
                attempt * FAILURE_RANDOM_ATTEMPT_MULTIPLIER +
                salt,
        )
        return failureRandom.nextDouble()
    }

    private fun deterministicUnit(
        cloudletId: Long,
        attempt: Int,
        salt: Int,
    ): Double = deterministicUnit(CloudletId(cloudletId), attempt, salt)

    private fun applyReservationPolicy(
        selectedVmId: Int,
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
    ): Int = vmReservationPolicy.select(selectedVmId, cloudlet, activeCloudlets, vmList)

    private fun createMetadata(cloudlet: Cloudlet): RealtimeTaskRecord =
        taskMetadataFactory.create(
            RealtimeTaskMetadataRequest(
                cloudlet = cloudlet,
                arrivalTime = arrivalState.arrivalTimeOf(cloudlet),
                attempt = arrivalState.attemptOf(cloudlet),
                fastestVmMips = vmList.maxOfOrNull { it.mips },
            ),
        )

    private fun updateMetadata(
        cloudlet: Cloudlet,
        transform: (RealtimeTaskRecord) -> RealtimeTaskRecord,
    ) {
        lifecycleStore.updateOrPut(createMetadata(cloudlet), transform)
    }

    private fun rejectCloudlet(
        cloudlet: Cloudlet,
        reason: RealtimeRejectReason,
    ) {
        brokerMetrics.recordRejected(reason)
        cloudlet.setStatus(Cloudlet.Status.FAILED)
        reservationState.remove(cloudlet)
        updateMetadata(cloudlet) { it.copy(lifecycle = RealtimeTaskLifecycle.REJECTED) }
    }

    private fun sampleQueueDepth(
        activeCloudlets: List<Cloudlet>,
        selectedVmIndex: Int,
        currentTime: Double,
    ) {
        val states =
            nodeStateTracker.snapshot(
                activeCloudlets,
                currentTime,
                reservationState.rawReservations(),
                vmLifecycleManager.snapshots(),
            )
        val selectedDepth = states.getOrNull(selectedVmIndex)?.queueDepth ?: 0
        brokerMetrics.recordQueueDepth(selectedDepth)
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

    private fun refreshVmLifecycles(currentTime: Double) {
        autoscalingController.refresh(currentTime, activeVmIndexes())
    }

    private fun onAutoscaleTick(currentTime: Double): List<RealtimeBrokerCommand> {
        val activeIndexes = activeVmIndexes()
        return autoscalingController.tickCommands(currentTime, activeIndexes)
    }

    private fun latestRejectionReason(
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        currentTime: Double,
    ): RealtimeRejectReason {
        val context = schedulingContext(cloudlet, activeCloudlets, currentTime)
        if (context.nodeCandidates.isNotEmpty() && context.acceptedCandidates.isEmpty()) {
            return RealtimeRejectReason.RESOURCE
        }
        return if (context.nodeStates.any { !it.resourceAcceptingWork }) {
            RealtimeRejectReason.RESOURCE
        } else {
            RealtimeRejectReason.CAPACITY
        }
    }

    private inner class ArrivalWorkflowAdapter : RealtimeArrivalWorkflowContext {
        override fun lifecycleOf(cloudlet: Cloudlet): RealtimeTaskLifecycle? = this@RealtimeBroker.lifecycleOf(cloudlet)

        override fun markArrivedAfterInterruption(cloudlet: Cloudlet) {
            this@RealtimeBroker.markArrivedAfterInterruption(cloudlet)
        }

        override fun refreshVmLifecycles(currentTime: Double) = this@RealtimeBroker.refreshVmLifecycles(currentTime)

        override fun activeCloudlets(): List<Cloudlet> = this@RealtimeBroker.activeCloudlets()

        override fun scaleOutCommands(
            queueDepth: Int,
            currentTime: Double,
        ): List<RealtimeBrokerCommand> = this@RealtimeBroker.scaleOutCommands(queueDepth, currentTime)

        override fun schedulingContext(
            cloudlet: Cloudlet,
            activeCloudlets: List<Cloudlet>,
            currentTime: Double,
        ): RealtimeSchedulingContext = this@RealtimeBroker.schedulingContext(cloudlet, activeCloudlets, currentTime)

        override fun taskRecord(cloudlet: Cloudlet): RealtimeTaskRecord = this@RealtimeBroker.taskRecord(cloudlet)

        override fun activeTenantRecords(): List<RealtimeTaskRecord> = this@RealtimeBroker.activeTenantRecords()

        override fun decideTenantAdmission(record: RealtimeTaskRecord): TenantAdmissionDecision =
            this@RealtimeBroker.decideTenantAdmission(record)

        override fun decideCapacityAdmission(
            activeCloudletCount: Int,
            context: RealtimeSchedulingContext,
        ): AdmissionDecision = this@RealtimeBroker.decideCapacityAdmission(activeCloudletCount, context)

        override fun tryPreemptFor(
            cloudlet: Cloudlet,
            activeCloudlets: List<Cloudlet>,
        ): Boolean = this@RealtimeBroker.tryPreemptFor(cloudlet, activeCloudlets)

        override fun selectVm(
            cloudlet: Cloudlet,
            activeCloudlets: List<Cloudlet>,
            currentTime: Double,
        ): Pair<Int, Double>? = this@RealtimeBroker.selectVm(cloudlet, activeCloudlets, currentTime)

        override fun latestRejectionReason(
            cloudlet: Cloudlet,
            activeCloudlets: List<Cloudlet>,
            currentTime: Double,
        ): RealtimeRejectReason = this@RealtimeBroker.latestRejectionReason(cloudlet, activeCloudlets, currentTime)

        override fun rejectCloudlet(
            cloudlet: Cloudlet,
            reason: RealtimeRejectReason,
        ) = this@RealtimeBroker.rejectCloudlet(cloudlet, reason)

        override fun decisionDelay(cloudlet: Cloudlet): Double = this@RealtimeBroker.decisionDelay(cloudlet)

        override fun recordDecisionDelay(delay: Double) = this@RealtimeBroker.recordDecisionDelay(delay)

        override fun preparePendingSubmission(request: RealtimePendingSubmissionRequest): RealtimePendingSubmission =
            this@RealtimeBroker.preparePendingSubmission(request)

        override fun recordPreemptionFailed() = this@RealtimeBroker.recordPreemptionFailed()
    }

    private inner class SubmissionWorkflowAdapter : RealtimeSubmissionWorkflowContext {
        override fun isPendingDecision(cloudlet: Cloudlet): Boolean = this@RealtimeBroker.isPendingDecision(cloudlet)

        override fun discardPending(cloudlet: Cloudlet) = this@RealtimeBroker.discardPending(cloudlet)

        override fun attemptOf(cloudlet: Cloudlet): Int = this@RealtimeBroker.attemptOf(cloudlet)

        override fun submitAttemptDecision(
            cloudletId: CloudletId,
            attempt: Int,
            failurePressure: Double,
        ): FailureDecision = this@RealtimeBroker.submitAttemptDecision(cloudletId, attempt, failurePressure)

        override fun retryPendingSubmission(
            cloudlet: Cloudlet,
            attempt: Int,
            delay: Double,
        ): RealtimeBrokerCommand = this@RealtimeBroker.retryPendingSubmission(cloudlet, attempt, delay)

        override fun permanentlyFailPendingSubmission(cloudlet: Cloudlet) {
            this@RealtimeBroker.permanentlyFailPendingSubmission(cloudlet)
        }

        override fun submitAcceptedCloudlet(submission: RealtimePendingSubmission) {
            this@RealtimeBroker.submitAcceptedCloudlet(submission)
        }
    }

    private fun activeVmIndexes(): Set<Int> =
        RealtimeActiveVmIndexResolver(vmList, reservationState)
            .indexesFor(getActiveCloudlets())
}

private fun RealtimeSchedulingContext.hasNoAcceptedCapacityCandidate(): Boolean =
    (hasCapacityLimit || nodeCandidates.isNotEmpty()) && candidateNodeStates.isEmpty()
