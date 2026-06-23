package broker

import config.RealtimeSchedulingConfig
import org.cloudsimplus.cloudlets.Cloudlet
import scheduler.RealtimeCandidateScoreCalculator
import scheduler.RealtimeScheduler
import scheduler.RealtimeSchedulingContext

private const val DECISION_JITTER_SALT = 11

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

internal sealed interface RealtimeVmSelectionOutcome {
    data class Selected(
        val vmIndex: Int,
        val failurePressure: Double,
    ) : RealtimeVmSelectionOutcome

    data object NeedsPreemption : RealtimeVmSelectionOutcome

    data class Rejected(
        val reason: RealtimeRejectReason,
    ) : RealtimeVmSelectionOutcome

    data class RetryLater(
        val delay: Double,
    ) : RealtimeVmSelectionOutcome

    data object NoSelection : RealtimeVmSelectionOutcome
}

@Suppress("TooManyFunctions") // Facade intentionally exposes selection, preview, preemption and read helpers together.
internal class RealtimeVmSelectionFacade(
    private val schedulingConfig: RealtimeSchedulingConfig,
    private val state: RealtimeBrokerStateBundle,
    private val environment: RealtimeBrokerEnvironment,
    private val lifecycleService: RealtimeBrokerLifecycleService,
    private val policies: RealtimeVmSelectionPolicies,
) {
    private val scoreCalculator = RealtimeCandidateScoreCalculator()
    private val deadlineAdmissionController = RealtimeDeadlineAdmissionController(schedulingConfig, scoreCalculator)

    fun selectVm(
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        currentTime: Double,
        allowDeadlinePreemption: Boolean = true,
        recordSelectionMetrics: Boolean = true,
    ): RealtimeVmSelectionOutcome {
        val strategy = schedulingConfig.strategy.lowercase()
        val context = schedulingContext(cloudlet, activeCloudlets, currentTime)
        return when (
            val admission =
                deadlineAdmissionController.evaluate(
                    context = context,
                    attempt = state.arrival.attemptOf(cloudlet),
                    allowPreemption = allowDeadlinePreemption,
                )
        ) {
            is DeadlineAdmissionResult.Continue ->
                selectFromAdmittedContext(
                    strategy = strategy,
                    cloudlet = cloudlet,
                    activeCloudlets = activeCloudlets,
                    context = admission.context,
                    metricAction = admission.metricAction,
                    recordSelectionMetrics = recordSelectionMetrics,
                )
            DeadlineAdmissionResult.NeedsPreemption -> RealtimeVmSelectionOutcome.NeedsPreemption
            DeadlineAdmissionResult.Reject -> RealtimeVmSelectionOutcome.Rejected(RealtimeRejectReason.DEADLINE)
            is DeadlineAdmissionResult.RetryLater -> RealtimeVmSelectionOutcome.RetryLater(admission.delay)
        }
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
        context: RealtimeSchedulingContext,
    ): Int? {
        if (context.hasNoAcceptedCapacityCandidate()) return null
        return if (strategy == "static") {
            state.arrival.preassignedVmIndexOf(cloudlet)
                ?: policies.scheduler.scheduleOnArrival(context)
        } else {
            policies.scheduler.scheduleOnArrival(context)
        }
    }

    private fun selectFromAdmittedContext(
        strategy: String,
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        context: RealtimeSchedulingContext,
        metricAction: DeadlineAdmissionMetricAction?,
        recordSelectionMetrics: Boolean,
    ): RealtimeVmSelectionOutcome {
        val selected = schedulerSelectedVm(strategy, cloudlet, context)
        return selected
            ?.let { validatedVmSelection(it, cloudlet, activeCloudlets, context, metricAction, recordSelectionMetrics) }
            ?: RealtimeVmSelectionOutcome.NoSelection
    }

    private fun validatedVmSelection(
        selectedVmIndex: Int,
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        context: RealtimeSchedulingContext,
        metricAction: DeadlineAdmissionMetricAction?,
        recordSelectionMetrics: Boolean,
    ): RealtimeVmSelectionOutcome.Selected? {
        val reserved = applyReservationPolicy(selectedVmIndex, cloudlet, activeCloudlets)
        val bounded = reserved.coerceIn(environment.vmList.indices)
        val placementState = context.acceptedCandidates.firstOrNull { it.vmIndex == bounded }?.nodeState
        val selectedState = placementState ?: context.nodeStates.getOrNull(bounded)
        return when {
            context.nodeCandidates.isNotEmpty() && placementState == null -> null
            selectedState != null && !selectedState.acceptingWork -> null
            else -> {
                if (recordSelectionMetrics) {
                    placementState?.let(state.metrics::recordPlacement)
                    state.metrics.recordCandidateScores(scoreCalculator.scoreRecords(context, bounded))
                    state.metrics.recordDeadlineAdmission(metricAction)
                }
                RealtimeVmSelectionOutcome.Selected(bounded, selectedState?.failurePressure ?: 0.0)
            }
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

private fun RealtimeSchedulingContext.hasNoAcceptedCapacityCandidate(): Boolean =
    (hasCapacityLimit || nodeCandidates.isNotEmpty()) && candidateNodeStates.isEmpty()
