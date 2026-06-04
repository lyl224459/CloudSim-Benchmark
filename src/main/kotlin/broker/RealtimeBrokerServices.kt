package broker

import config.RealtimeSchedulingConfig
import datacenter.MutableRealtimeTraceMetadataProvider
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import scheduler.CloudletId
import scheduler.RealtimeNodeStateTracker
import scheduler.RealtimeResourceModel
import scheduler.RealtimeScheduler
import scheduler.RealtimeSchedulingContext
import scheduler.RealtimeTaskLifecycle
import scheduler.RealtimeTaskRecord
import scheduler.RealtimeTopologyModel
import scheduler.RealtimeVmLifecycleManager
import java.util.Random

private const val FAILURE_RANDOM_CLOUDLET_MULTIPLIER = 1_000_003L
private const val FAILURE_RANDOM_ATTEMPT_MULTIPLIER = 9_176L
private const val DECISION_JITTER_SALT = 11

internal class RealtimeDeterministicSampler {
    private val random = Random(0L)

    fun sample(
        cloudletId: CloudletId,
        attempt: Int,
        salt: Int,
    ): Double {
        random.setSeed(
            cloudletId.value * FAILURE_RANDOM_CLOUDLET_MULTIPLIER +
                attempt * FAILURE_RANDOM_ATTEMPT_MULTIPLIER +
                salt,
        )
        return random.nextDouble()
    }

    fun sample(
        cloudletId: Long,
        attempt: Int,
        salt: Int,
    ): Double = sample(CloudletId(cloudletId), attempt, salt)
}

internal data class RealtimeBrokerStateBundle(
    val arrival: RealtimeArrivalState,
    val lifecycleStore: RealtimeTaskLifecycleStore,
    val reservation: RealtimeReservationState,
    val metrics: RealtimeBrokerMetrics,
)

internal class RealtimeBrokerEnvironment(
    val topologyModel: RealtimeTopologyModel,
    val vmLifecycleManager: RealtimeVmLifecycleManager,
    val traceMetadataProvider: MutableRealtimeTraceMetadataProvider,
    val nodeStateTracker: RealtimeNodeStateTracker,
) {
    val vmList: List<Vm>
        get() = vmLifecycleManager.vmList
}

internal data class RealtimeBrokerCallbacks(
    val clock: () -> Double,
    val applyCommands: (Iterable<RealtimeBrokerCommand>) -> Unit,
    val submitCloudlet: (Cloudlet) -> Unit,
)

internal class RealtimeBrokerLifecycleService(
    private val state: RealtimeBrokerStateBundle,
    private val metadataFactory: RealtimeTaskMetadataFactory,
    private val environment: RealtimeBrokerEnvironment,
) {
    fun createMetadata(cloudlet: Cloudlet): RealtimeTaskRecord =
        metadataFactory.create(
            RealtimeTaskMetadataRequest(
                cloudlet = cloudlet,
                arrivalTime = state.arrival.arrivalTimeOf(cloudlet),
                attempt = state.arrival.attemptOf(cloudlet),
                fastestVmMips = environment.vmList.maxOfOrNull { it.mips },
            ),
        )

    fun updateMetadata(
        cloudlet: Cloudlet,
        transform: (RealtimeTaskRecord) -> RealtimeTaskRecord,
    ) {
        state.lifecycleStore.updateOrPut(createMetadata(cloudlet), transform)
    }

    fun lifecycleOf(cloudlet: Cloudlet): RealtimeTaskLifecycle? {
        val record = state.lifecycleStore.get(cloudlet.id)
        return record?.lifecycle
    }

    fun markArrivedAfterInterruption(cloudlet: Cloudlet) {
        updateMetadata(cloudlet) { it.copy(lifecycle = RealtimeTaskLifecycle.ARRIVED) }
    }

    fun taskRecord(cloudlet: Cloudlet): RealtimeTaskRecord {
        val existing = state.lifecycleStore.get(cloudlet.id)
        return existing ?: createMetadata(cloudlet)
    }

    fun activeTenantRecords(): List<RealtimeTaskRecord> {
        val records = state.lifecycleStore.snapshot()
        return records.filter(RealtimeTaskRecord::isActiveForBrokerAdmission)
    }
}

internal data class RealtimeBrokerPreemptionComponents(
    val controller: RealtimePreemptionController,
    val executor: RealtimePreemptionExecutor,
)

internal data class RealtimeVmSelectionPolicies(
    val scheduler: RealtimeScheduler,
    val tenantFairnessContextBuilder: TenantFairnessContextBuilder,
    val reservationPolicy: RealtimeVmReservationPolicy,
    val preemption: RealtimeBrokerPreemptionComponents,
    val deterministicSampler: RealtimeDeterministicSampler,
)

internal data class RealtimeBrokerPreemptionAttempt(
    val applied: Boolean,
    val commands: List<RealtimeBrokerCommand>,
)

internal class RealtimeVmSelectionFacade(
    private val schedulingConfig: RealtimeSchedulingConfig,
    private val state: RealtimeBrokerStateBundle,
    private val environment: RealtimeBrokerEnvironment,
    private val lifecycleService: RealtimeBrokerLifecycleService,
    private val policies: RealtimeVmSelectionPolicies,
) {
    fun selectVm(
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        currentTime: Double,
    ): Pair<Int, Double>? {
        val strategy = schedulingConfig.strategy.lowercase()
        val context = schedulingContext(cloudlet, activeCloudlets, currentTime)
        val selected = schedulerSelectedVm(strategy, cloudlet, activeCloudlets, currentTime, context)
        return selected?.let { validatedVmSelection(it, cloudlet, activeCloudlets, context) }
    }

    fun staticPreviewSelection(
        cloudlet: Cloudlet,
        previewWaiting: List<Cloudlet>,
    ): Int =
        policies.scheduler.scheduleOnArrival(
            schedulingContext(cloudlet, previewWaiting, cloudlet.submissionDelay),
        )

    fun schedulingContext(
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        currentTime: Double,
    ): RealtimeSchedulingContext {
        val taskMetadata = lifecycleService.taskRecord(cloudlet)
        val records = state.lifecycleStore.snapshot()
        val nodeStates =
            environment.nodeStateTracker.snapshot(
                activeCloudlets,
                currentTime,
                state.reservation.rawReservations(),
                environment.vmLifecycleManager.snapshots(),
                cloudlet,
            )
        val nodeCandidates =
            environment.topologyModel.candidatesFor(
                states = nodeStates,
                vmList = environment.vmList,
                workload = taskMetadata.workloadDescriptor(),
                records = records,
            )
        return RealtimeSchedulingContext(
            newCloudlet = cloudlet,
            activeCloudlets = activeCloudlets,
            vmList = environment.vmList,
            currentTime = currentTime,
            nodeStates = nodeStates,
            taskMetadata = taskMetadata,
            queuePolicy = schedulingConfig.normalizedQueuePolicy(),
            topologyPolicy = schedulingConfig.normalizedTopologyPolicy(),
            tenantSchedulingPolicy = schedulingConfig.normalizedTenantSchedulingPolicy(),
            tenantSnapshots = policies.tenantFairnessContextBuilder.snapshots(records),
            nodeCandidates = nodeCandidates,
            preemptionCandidates =
                policies.preemption.controller.candidates(
                    incoming = taskMetadata,
                    activeCloudlets = activeCloudlets,
                    records = records,
                    vmReservations = state.reservation.rawReservations(),
                ),
        )
    }

    fun tryPreemptFor(
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
    ): RealtimeBrokerPreemptionAttempt {
        val incoming = lifecycleService.taskRecord(cloudlet)
        val candidates =
            policies.preemption.controller.candidates(
                incoming,
                activeCloudlets,
                state.lifecycleStore.snapshot(),
                state.reservation.rawReservations(),
            )
        return when (val decision = policies.preemption.controller.decide(candidates)) {
            PreemptionDecision.None -> RealtimeBrokerPreemptionAttempt(applied = false, commands = emptyList())
            is PreemptionDecision.Preempt -> {
                val result = policies.preemption.executor.preempt(decision)
                RealtimeBrokerPreemptionAttempt(applied = result.applied, commands = result.commands)
            }
        }
    }

    fun latestRejectionReason(
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

    fun decisionDelay(cloudlet: Cloudlet): Double {
        val jitter =
            if (schedulingConfig.decisionJitter > 0.0) {
                policies.deterministicSampler.sample(
                    cloudlet.id,
                    state.arrival.attemptOf(cloudlet),
                    salt = DECISION_JITTER_SALT,
                ) * schedulingConfig.decisionJitter
            } else {
                0.0
            }
        return schedulingConfig.decisionDelay + jitter
    }

    fun activeVmIndexes(activeCloudlets: List<Cloudlet>): Set<Int> =
        RealtimeActiveVmIndexResolver(environment.vmList, state.reservation)
            .indexesFor(activeCloudlets)

    private fun schedulerSelectedVm(
        strategy: String,
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        currentTime: Double,
        context: RealtimeSchedulingContext,
    ): Int? {
        if (context.hasNoAcceptedCapacityCandidate()) return null
        return if (strategy == "static") {
            state.arrival.preassignedVmIndexOf(cloudlet)
                ?: policies.scheduler.scheduleOnArrival(schedulingContext(cloudlet, activeCloudlets, currentTime))
        } else {
            policies.scheduler.scheduleOnArrival(context)
        }
    }

    private fun validatedVmSelection(
        selectedVmIndex: Int,
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        context: RealtimeSchedulingContext,
    ): Pair<Int, Double>? {
        val reserved = applyReservationPolicy(selectedVmIndex, cloudlet, activeCloudlets)
        val bounded = reserved.coerceIn(environment.vmList.indices)
        val placementState = context.acceptedCandidates.firstOrNull { it.vmIndex == bounded }?.nodeState
        val selectedState = placementState ?: context.nodeStates.getOrNull(bounded)
        return when {
            context.nodeCandidates.isNotEmpty() && placementState == null -> null
            selectedState != null && !selectedState.acceptingWork -> null
            else -> bounded to (selectedState?.failurePressure ?: 0.0)
        }
    }

    private fun applyReservationPolicy(
        selectedVmId: Int,
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
    ): Int =
        policies.reservationPolicy.select(
            selectedVmId,
            cloudlet,
            activeCloudlets,
            environment.vmList,
        )
}

internal class RealtimeQueueDepthSampler(
    private val state: RealtimeBrokerStateBundle,
    private val environment: RealtimeBrokerEnvironment,
) {
    fun queueDepthFor(
        activeCloudlets: List<Cloudlet>,
        selectedVmIndex: Int,
        currentTime: Double,
    ): Int {
        val states =
            environment.nodeStateTracker.snapshot(
                activeCloudlets,
                currentTime,
                state.reservation.rawReservations(),
                environment.vmLifecycleManager.snapshots(),
            )
        return states.getOrNull(selectedVmIndex)?.queueDepth ?: 0
    }
}

internal data class RealtimeSubmissionState(
    val state: RealtimeBrokerStateBundle,
    val environment: RealtimeBrokerEnvironment,
    val lifecycleService: RealtimeBrokerLifecycleService,
)

internal data class RealtimeSubmissionControllers(
    val failureController: RealtimeFailureController,
    val runtimePlanner: RealtimeRuntimeEventPlanner,
    val queueDepthSampler: RealtimeQueueDepthSampler,
)

internal class RealtimeSubmissionService(
    private val submissionState: RealtimeSubmissionState,
    private val controllers: RealtimeSubmissionControllers,
    private val callbacks: RealtimeBrokerCallbacks,
) {
    private val state: RealtimeBrokerStateBundle get() = submissionState.state
    private val environment: RealtimeBrokerEnvironment get() = submissionState.environment
    private val lifecycleService: RealtimeBrokerLifecycleService get() = submissionState.lifecycleService

    fun preparePendingSubmission(request: RealtimePendingSubmissionRequest): RealtimePendingSubmission {
        val cloudlet = request.cloudlet
        val boundedIndex = request.selectedVmIndex.coerceIn(environment.vmList.indices)
        cloudlet.setVm(environment.vmList[boundedIndex])
        state.reservation.reserve(cloudlet, boundedIndex)
        state.arrival.addPending(cloudlet)
        lifecycleService.updateMetadata(cloudlet) {
            it.copy(
                assignedVmIndex = boundedIndex,
                lastDecisionDelay = request.delay,
                lifecycle = RealtimeTaskLifecycle.PENDING_DECISION,
            )
        }
        sampleQueueDepth(request.activeCloudlets, boundedIndex, request.currentTime)
        return RealtimePendingSubmission(cloudlet, boundedIndex, request.delay, request.failurePressure)
    }

    fun rejectCloudlet(
        cloudlet: Cloudlet,
        reason: RealtimeRejectReason,
    ) {
        state.metrics.recordRejected(reason)
        cloudlet.setStatus(Cloudlet.Status.FAILED)
        state.reservation.remove(cloudlet)
        lifecycleService.updateMetadata(cloudlet) { it.copy(lifecycle = RealtimeTaskLifecycle.REJECTED) }
    }

    fun isPendingDecision(cloudlet: Cloudlet): Boolean =
        state.lifecycleStore.get(cloudlet.id)?.lifecycle == RealtimeTaskLifecycle.PENDING_DECISION

    fun discardPending(cloudlet: Cloudlet) {
        state.arrival.removePending(CloudletId(cloudlet.id))
    }

    fun attemptOf(cloudlet: Cloudlet): Int = state.arrival.attemptOf(cloudlet)

    fun submitAttemptDecision(
        cloudletId: CloudletId,
        attempt: Int,
        failurePressure: Double,
    ): FailureDecision = controllers.failureController.submitAttempt(cloudletId, attempt, failurePressure)

    fun retryPendingSubmission(
        cloudlet: Cloudlet,
        attempt: Int,
        delay: Double,
    ): RealtimeBrokerCommand {
        state.arrival.incrementAttempt(cloudlet)
        state.reservation.remove(cloudlet)
        lifecycleService.updateMetadata(cloudlet) {
            it.copy(
                attempt = attempt + 1,
                assignedVmIndex = null,
                lifecycle = RealtimeTaskLifecycle.RETRYING,
            )
        }
        state.metrics.recordRetry()
        return RealtimeBrokerCommand.ScheduleArrival(delay, cloudlet)
    }

    fun permanentlyFailPendingSubmission(cloudlet: Cloudlet) {
        cloudlet.setStatus(Cloudlet.Status.FAILED)
        state.reservation.remove(cloudlet)
        lifecycleService.updateMetadata(cloudlet) { it.copy(lifecycle = RealtimeTaskLifecycle.FAILED) }
        state.metrics.recordPermanentFailure()
    }

    fun submitAcceptedCloudlet(submission: RealtimePendingSubmission): List<RealtimeBrokerCommand> {
        val cloudlet = submission.cloudlet
        cloudlet.setVm(environment.vmList[submission.vmIndex])
        cloudlet.setSubmissionDelay(0.0)
        state.arrival.addWaiting(cloudlet)
        lifecycleService.updateMetadata(cloudlet) { it.copy(lifecycle = RealtimeTaskLifecycle.RUNNING) }
        lifecycleService.taskRecord(cloudlet).let { record ->
            controllers.runtimePlanner.recordSubmission(submission.vmIndex, record)
        }
        environment.vmLifecycleManager.markBusy(submission.vmIndex, callbacks.clock())
        if (state.arrival.attemptOf(cloudlet) > 0) {
            state.metrics.recordRetrySuccess()
        }
        state.metrics.recordSubmitted()
        val runtimeCommands = controllers.runtimePlanner.planRuntimeEvents(cloudlet, submission.failurePressure)
        callbacks.applyCommands(runtimeCommands)
        callbacks.submitCloudlet(cloudlet)
        return emptyList()
    }

    private fun sampleQueueDepth(
        activeCloudlets: List<Cloudlet>,
        selectedVmIndex: Int,
        currentTime: Double,
    ) {
        state.metrics.recordQueueDepth(
            controllers.queueDepthSampler.queueDepthFor(activeCloudlets, selectedVmIndex, currentTime),
        )
    }
}

internal class RealtimeRuntimeEventPlanner(
    private val state: RealtimeBrokerStateBundle,
    private val environment: RealtimeBrokerEnvironment,
    private val runtimeEventController: RealtimeRuntimeEventController,
    private val topologyAccountingController: RealtimeTopologyAccountingController,
    private val callbacks: RealtimeBrokerCallbacks,
) {
    fun recordSubmission(
        vmIndex: Int,
        record: RealtimeTaskRecord,
    ) {
        topologyAccountingController.recordSubmission(vmIndex, record)
    }

    fun planRuntimeEvents(
        cloudlet: Cloudlet,
        submissionFailurePressure: Double,
    ): List<RealtimeBrokerCommand> {
        val attempt = state.arrival.attemptOf(cloudlet)
        val assignedVmIndex = state.lifecycleStore.get(cloudlet.id)?.assignedVmIndex ?: 0
        val pressure = runtimeFailurePressure(assignedVmIndex, submissionFailurePressure)
        val plan =
            runtimeEventController.planRuntimeEvents(
                cloudlet = cloudlet,
                attempt = attempt,
                timing =
                    RealtimeRuntimeEventTiming(
                        arrivalTime = state.arrival.arrivalTimeOf(cloudlet),
                        currentTime = callbacks.clock(),
                    ),
                assignment =
                    RealtimeRuntimeEventAssignment(
                        vmIndex = assignedVmIndex,
                        nodeFailurePressure = pressure,
                    ),
            )
        topologyAccountingController.recordFailure(plan.topologyFailureDomain)
        return plan.commands
    }

    private fun runtimeFailurePressure(
        assignedVmIndex: Int,
        submissionFailurePressure: Double,
    ): Double {
        val states =
            environment.nodeStateTracker.snapshot(
                activeCloudlets(),
                callbacks.clock(),
                state.reservation.rawReservations(),
                environment.vmLifecycleManager.snapshots(),
            )
        return states.getOrNull(assignedVmIndex)?.failurePressure ?: submissionFailurePressure
    }

    private fun activeCloudlets(): List<Cloudlet> =
        state.arrival
            .queuedCloudletsSnapshot()
            .filterNot(Cloudlet::isTerminalRealtimeCloudlet)
}

internal fun realtimeBrokerEnvironment(
    schedulingConfig: RealtimeSchedulingConfig,
    initialVmList: List<Vm>,
    traceMetadataProvider: MutableRealtimeTraceMetadataProvider,
): RealtimeBrokerEnvironment {
    val topologyModel = RealtimeTopologyModel.fromConfig(schedulingConfig, initialVmList.size)
    val vmLifecycleManager = RealtimeVmLifecycleManager(initialVmList, schedulingConfig, topologyModel)
    return RealtimeBrokerEnvironment(
        topologyModel = topologyModel,
        vmLifecycleManager = vmLifecycleManager,
        traceMetadataProvider = traceMetadataProvider,
        nodeStateTracker =
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
            ),
    )
}

private fun RealtimeSchedulingContext.hasNoAcceptedCapacityCandidate(): Boolean =
    (hasCapacityLimit || nodeCandidates.isNotEmpty()) && candidateNodeStates.isEmpty()

private fun RealtimeTaskRecord.isActiveForBrokerAdmission(): Boolean =
    lifecycle == RealtimeTaskLifecycle.PENDING_DECISION ||
        lifecycle == RealtimeTaskLifecycle.SUBMITTED ||
        lifecycle == RealtimeTaskLifecycle.RUNNING ||
        lifecycle == RealtimeTaskLifecycle.PREEMPTED ||
        lifecycle == RealtimeTaskLifecycle.MIGRATING ||
        lifecycle == RealtimeTaskLifecycle.RETRYING
