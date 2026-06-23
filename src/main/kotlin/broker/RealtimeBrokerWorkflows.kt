package broker

import org.cloudsimplus.cloudlets.Cloudlet
import scheduler.CloudletId
import scheduler.RealtimeSchedulingContext
import scheduler.RealtimeTaskLifecycle
import scheduler.RealtimeTaskRecord

internal data class RealtimePendingSubmissionRequest(
    val cloudlet: Cloudlet,
    val selectedVmIndex: Int,
    val activeCloudlets: List<Cloudlet>,
    val currentTime: Double,
    val delay: Double,
    val failurePressure: Double,
)

private data class ArrivalSelection(
    val activeCloudlets: List<Cloudlet>,
    val vmIndex: Int,
    val failurePressure: Double,
)

private sealed interface ArrivalSelectionAttempt {
    data class Selected(
        val selection: ArrivalSelection,
    ) : ArrivalSelectionAttempt

    data class Rejected(
        val reason: RealtimeRejectReason,
    ) : ArrivalSelectionAttempt

    data class RetryLater(
        val delay: Double,
    ) : ArrivalSelectionAttempt

    data class NoSelection(
        val recordPreemptionFailure: Boolean,
    ) : ArrivalSelectionAttempt
}

internal interface RealtimeArrivalLifecycleContext {
    fun lifecycleOf(cloudlet: Cloudlet): RealtimeTaskLifecycle?

    fun recordArrivalEvent(
        cloudlet: Cloudlet,
        arrivalTime: Double,
    )

    fun markArrivedAfterInterruption(cloudlet: Cloudlet)

    fun decideDependencyAdmission(cloudlet: Cloudlet): RealtimeDependencyArrivalDecision

    fun taskRecord(cloudlet: Cloudlet): RealtimeTaskRecord
}

internal interface RealtimeArrivalCapacityContext {
    fun refreshVmLifecycles(currentTime: Double)

    fun recordAutoscalingArrival(currentTime: Double)

    fun activeCloudlets(): List<Cloudlet>

    fun scaleOutCommands(
        queueDepth: Int,
        currentTime: Double,
        context: RealtimeSchedulingContext? = null,
    ): List<RealtimeBrokerCommand>

    fun schedulingContext(
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        currentTime: Double,
    ): RealtimeSchedulingContext

    fun decideTenantAdmission(record: RealtimeTaskRecord): TenantAdmissionDecision

    fun decideCapacityAdmission(
        activeCloudletCount: Int,
        context: RealtimeSchedulingContext,
    ): AdmissionDecision
}

internal interface RealtimeArrivalPlacementContext {
    fun tryPreemptFor(
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
    ): Boolean

    fun selectVm(
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        currentTime: Double,
        allowDeadlinePreemption: Boolean,
    ): RealtimeVmSelectionOutcome

    fun latestRejectionReason(
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        currentTime: Double,
    ): RealtimeRejectReason
}

internal interface RealtimeArrivalSubmissionContext {
    fun rejectCloudlet(
        cloudlet: Cloudlet,
        reason: RealtimeRejectReason,
    )

    fun decisionDelay(cloudlet: Cloudlet): Double

    fun recordDecisionDelay(delay: Double)

    fun preparePendingSubmission(request: RealtimePendingSubmissionRequest): RealtimePendingSubmission

    fun retryDeadlineAdmission(
        cloudlet: Cloudlet,
        delay: Double,
    ): RealtimeBrokerCommand

    fun recordPreemptionFailed()
}

internal interface RealtimeArrivalWorkflowContext :
    RealtimeArrivalLifecycleContext,
    RealtimeArrivalCapacityContext,
    RealtimeArrivalPlacementContext,
    RealtimeArrivalSubmissionContext

internal class RealtimeArrivalWorkflow(
    private val context: RealtimeArrivalWorkflowContext,
) {
    @Suppress(
        "CyclomaticComplexMethod",
        "LongMethod",
    ) // Arrival handling is the workflow boundary for admission, preemption and submission.
    fun onArrival(
        cloudlet: Cloudlet,
        arrivalTime: Double,
    ): List<RealtimeBrokerCommand> =
        buildList {
            val currentLifecycle = context.lifecycleOf(cloudlet)
            if (currentLifecycle.isTerminalArrivalLifecycle()) {
                return@buildList
            }
            context.recordArrivalEvent(cloudlet, arrivalTime)
            val arrivedAfterInterruption =
                currentLifecycle == RealtimeTaskLifecycle.RETRYING ||
                    currentLifecycle == RealtimeTaskLifecycle.MIGRATING
            if (arrivedAfterInterruption) {
                context.markArrivedAfterInterruption(cloudlet)
            }

            when (val dependencyDecision = context.decideDependencyAdmission(cloudlet)) {
                RealtimeDependencyArrivalDecision.Ready -> Unit
                RealtimeDependencyArrivalDecision.Blocked -> return@buildList
                is RealtimeDependencyArrivalDecision.Rejected -> {
                    addAll(dependencyDecision.commands)
                    return@buildList
                }
            }

            context.refreshVmLifecycles(arrivalTime)
            context.recordAutoscalingArrival(arrivalTime)
            var activeCloudlets = context.activeCloudlets()
            var initialContext = context.schedulingContext(cloudlet, activeCloudlets, arrivalTime)
            val scaleOutCommands = context.scaleOutCommands(activeCloudlets.size, arrivalTime, initialContext)
            addAll(scaleOutCommands)
            if (scaleOutCommands.isNotEmpty()) {
                activeCloudlets = context.activeCloudlets()
                initialContext = context.schedulingContext(cloudlet, activeCloudlets, arrivalTime)
            }
            val incomingRecord = context.taskRecord(cloudlet)
            when (val tenantDecision = context.decideTenantAdmission(incomingRecord)) {
                TenantAdmissionDecision.Accepted -> Unit
                is TenantAdmissionDecision.Rejected -> {
                    context.rejectCloudlet(cloudlet, tenantDecision.reason)
                    return@buildList
                }
            }

            when (val admission = context.decideCapacityAdmission(activeCloudlets.size, initialContext)) {
                AdmissionDecision.Accepted -> Unit
                is AdmissionDecision.Rejected -> {
                    if (!context.tryPreemptFor(cloudlet, activeCloudlets)) {
                        context.rejectCloudlet(cloudlet, admission.reason)
                        return@buildList
                    }
                    activeCloudlets = context.activeCloudlets()
                }
            }

            val selectionAttempt = selectVmWithPreemption(cloudlet, activeCloudlets, arrivalTime)
            val finalSelection =
                when (selectionAttempt) {
                    is ArrivalSelectionAttempt.Selected -> selectionAttempt.selection
                    is ArrivalSelectionAttempt.Rejected -> {
                        context.rejectCloudlet(cloudlet, selectionAttempt.reason)
                        return@buildList
                    }
                    is ArrivalSelectionAttempt.RetryLater -> {
                        add(context.retryDeadlineAdmission(cloudlet, selectionAttempt.delay))
                        return@buildList
                    }
                    is ArrivalSelectionAttempt.NoSelection -> {
                        if (selectionAttempt.recordPreemptionFailure) {
                            context.recordPreemptionFailed()
                        }
                        context.rejectCloudlet(
                            cloudlet,
                            context.latestRejectionReason(cloudlet, context.activeCloudlets(), arrivalTime),
                        )
                        return@buildList
                    }
                }

            val delay = context.decisionDelay(cloudlet)
            context.recordDecisionDelay(delay)
            val submission =
                context.preparePendingSubmission(
                    RealtimePendingSubmissionRequest(
                        cloudlet = cloudlet,
                        selectedVmIndex = finalSelection.vmIndex,
                        activeCloudlets = finalSelection.activeCloudlets,
                        currentTime = arrivalTime,
                        delay = delay,
                        failurePressure = finalSelection.failurePressure,
                    ),
                )
            add(RealtimeBrokerCommand.ScheduleSubmit(delay, submission))
        }

    private fun selectVmWithPreemption(
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        arrivalTime: Double,
    ): ArrivalSelectionAttempt {
        val selection = context.selectVm(cloudlet, activeCloudlets, arrivalTime, allowDeadlinePreemption = true)
        return when (selection) {
            is RealtimeVmSelectionOutcome.Selected ->
                ArrivalSelectionAttempt.Selected(
                    ArrivalSelection(activeCloudlets, selection.vmIndex, selection.failurePressure),
                )
            RealtimeVmSelectionOutcome.NeedsPreemption ->
                selectAfterDeadlinePreemption(cloudlet, activeCloudlets, arrivalTime)
            RealtimeVmSelectionOutcome.NoSelection ->
                selectAfterCapacityPreemption(cloudlet, activeCloudlets, arrivalTime)
            is RealtimeVmSelectionOutcome.Rejected -> ArrivalSelectionAttempt.Rejected(selection.reason)
            is RealtimeVmSelectionOutcome.RetryLater -> ArrivalSelectionAttempt.RetryLater(selection.delay)
        }
    }

    private fun selectAfterDeadlinePreemption(
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        arrivalTime: Double,
    ): ArrivalSelectionAttempt {
        if (!context.tryPreemptFor(cloudlet, activeCloudlets)) {
            return selectionAttemptFromOutcome(
                outcome = context.selectVm(cloudlet, activeCloudlets, arrivalTime, allowDeadlinePreemption = false),
                activeCloudlets = activeCloudlets,
                recordPreemptionFailure = false,
            )
        }
        val currentActiveCloudlets = context.activeCloudlets()
        return selectionAttemptFromOutcome(
            outcome = context.selectVm(cloudlet, currentActiveCloudlets, arrivalTime, allowDeadlinePreemption = false),
            activeCloudlets = currentActiveCloudlets,
            recordPreemptionFailure = false,
        )
    }

    private fun selectAfterCapacityPreemption(
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        arrivalTime: Double,
    ): ArrivalSelectionAttempt {
        if (!context.tryPreemptFor(cloudlet, activeCloudlets)) {
            return ArrivalSelectionAttempt.NoSelection(recordPreemptionFailure = false)
        }
        val currentActiveCloudlets = context.activeCloudlets()
        return selectionAttemptFromOutcome(
            outcome = context.selectVm(cloudlet, currentActiveCloudlets, arrivalTime, allowDeadlinePreemption = false),
            activeCloudlets = currentActiveCloudlets,
            recordPreemptionFailure = true,
        )
    }

    private fun selectionAttemptFromOutcome(
        outcome: RealtimeVmSelectionOutcome,
        activeCloudlets: List<Cloudlet>,
        recordPreemptionFailure: Boolean,
    ): ArrivalSelectionAttempt =
        when (outcome) {
            is RealtimeVmSelectionOutcome.Selected ->
                ArrivalSelectionAttempt.Selected(
                    ArrivalSelection(activeCloudlets, outcome.vmIndex, outcome.failurePressure),
                )
            RealtimeVmSelectionOutcome.NeedsPreemption ->
                ArrivalSelectionAttempt.NoSelection(recordPreemptionFailure)
            RealtimeVmSelectionOutcome.NoSelection ->
                ArrivalSelectionAttempt.NoSelection(recordPreemptionFailure)
            is RealtimeVmSelectionOutcome.Rejected -> ArrivalSelectionAttempt.Rejected(outcome.reason)
            is RealtimeVmSelectionOutcome.RetryLater -> ArrivalSelectionAttempt.RetryLater(outcome.delay)
        }
}

internal interface RealtimeSubmissionWorkflowContext {
    fun isCurrentPendingDecision(submission: RealtimePendingSubmission): Boolean

    fun discardPending(cloudlet: Cloudlet)

    fun attemptOf(cloudlet: Cloudlet): Int

    fun submitAttemptDecision(
        cloudletId: CloudletId,
        attempt: Int,
        failurePressure: Double,
    ): FailureDecision

    fun retryPendingSubmission(
        cloudlet: Cloudlet,
        attempt: Int,
        delay: Double,
    ): RealtimeBrokerCommand

    fun permanentlyFailPendingSubmission(cloudlet: Cloudlet)

    fun submitAcceptedCloudlet(submission: RealtimePendingSubmission): List<RealtimeBrokerCommand>
}

internal class RealtimeSubmissionWorkflow(
    private val context: RealtimeSubmissionWorkflowContext,
) {
    fun onSubmit(submission: RealtimePendingSubmission): List<RealtimeBrokerCommand> {
        val cloudlet = submission.cloudlet
        if (!context.isCurrentPendingDecision(submission)) {
            return emptyList()
        }

        context.discardPending(cloudlet)
        val attempt = context.attemptOf(cloudlet)
        return when (
            val failureDecision =
                context.submitAttemptDecision(
                    CloudletId(cloudlet.id),
                    attempt,
                    submission.failurePressure,
                )
        ) {
            FailureDecision.Continue -> context.submitAcceptedCloudlet(submission)
            is FailureDecision.Retry ->
                listOf(
                    context.retryPendingSubmission(cloudlet, attempt, failureDecision.delay),
                )
            FailureDecision.PermanentlyFail -> {
                context.permanentlyFailPendingSubmission(cloudlet)
                emptyList()
            }
        }
    }
}

private fun RealtimeTaskLifecycle?.isTerminalArrivalLifecycle(): Boolean =
    this == RealtimeTaskLifecycle.COMPLETED ||
        this == RealtimeTaskLifecycle.REJECTED ||
        this == RealtimeTaskLifecycle.FAILED ||
        this == RealtimeTaskLifecycle.CANCELLED ||
        this == RealtimeTaskLifecycle.TIMED_OUT
