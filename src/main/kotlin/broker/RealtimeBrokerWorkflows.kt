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

private data class ArrivalSelectionAttempt(
    val selection: ArrivalSelection?,
    val recordPreemptionFailure: Boolean,
)

internal interface RealtimeArrivalWorkflowContext {
    fun lifecycleOf(cloudlet: Cloudlet): RealtimeTaskLifecycle?

    fun markArrivedAfterInterruption(cloudlet: Cloudlet)

    fun refreshVmLifecycles(currentTime: Double)

    fun activeCloudlets(): List<Cloudlet>

    fun scaleOutCommands(
        queueDepth: Int,
        currentTime: Double,
    ): List<RealtimeBrokerCommand>

    fun schedulingContext(
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        currentTime: Double,
    ): RealtimeSchedulingContext

    fun taskRecord(cloudlet: Cloudlet): RealtimeTaskRecord

    fun activeTenantRecords(): List<RealtimeTaskRecord>

    fun decideTenantAdmission(record: RealtimeTaskRecord): TenantAdmissionDecision

    fun decideCapacityAdmission(
        activeCloudletCount: Int,
        context: RealtimeSchedulingContext,
    ): AdmissionDecision

    fun tryPreemptFor(
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
    ): Boolean

    fun selectVm(
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        currentTime: Double,
    ): Pair<Int, Double>?

    fun latestRejectionReason(
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        currentTime: Double,
    ): RealtimeRejectReason

    fun rejectCloudlet(
        cloudlet: Cloudlet,
        reason: RealtimeRejectReason,
    )

    fun decisionDelay(cloudlet: Cloudlet): Double

    fun recordDecisionDelay(delay: Double)

    fun preparePendingSubmission(request: RealtimePendingSubmissionRequest): RealtimePendingSubmission

    fun recordPreemptionFailed()
}

internal class RealtimeArrivalWorkflow(
    private val context: RealtimeArrivalWorkflowContext,
) {
    fun onArrival(
        cloudlet: Cloudlet,
        arrivalTime: Double,
    ): List<RealtimeBrokerCommand> =
        buildList {
            val currentLifecycle = context.lifecycleOf(cloudlet)
            val arrivedAfterInterruption =
                currentLifecycle == RealtimeTaskLifecycle.RETRYING ||
                    currentLifecycle == RealtimeTaskLifecycle.MIGRATING
            if (arrivedAfterInterruption) {
                context.markArrivedAfterInterruption(cloudlet)
            }

            context.refreshVmLifecycles(arrivalTime)
            var activeCloudlets = context.activeCloudlets()
            addAll(context.scaleOutCommands(activeCloudlets.size, arrivalTime))

            val initialContext = context.schedulingContext(cloudlet, activeCloudlets, arrivalTime)
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
            val finalSelection = selectionAttempt.selection
            if (finalSelection == null) {
                if (selectionAttempt.recordPreemptionFailure) {
                    context.recordPreemptionFailed()
                }
                context.rejectCloudlet(
                    cloudlet,
                    context.latestRejectionReason(cloudlet, context.activeCloudlets(), arrivalTime),
                )
                return@buildList
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
        var currentActiveCloudlets = activeCloudlets
        val selection = context.selectVm(cloudlet, currentActiveCloudlets, arrivalTime)
        var preempted = false
        if (selection == null) {
            if (!context.tryPreemptFor(cloudlet, currentActiveCloudlets)) {
                return ArrivalSelectionAttempt(selection = null, recordPreemptionFailure = false)
            }
            preempted = true
            currentActiveCloudlets = context.activeCloudlets()
        }

        val finalSelection = selection ?: context.selectVm(cloudlet, currentActiveCloudlets, arrivalTime)
        return ArrivalSelectionAttempt(
            selection =
                finalSelection?.let { (vmIndex, failurePressure) ->
                    ArrivalSelection(currentActiveCloudlets, vmIndex, failurePressure)
                },
            recordPreemptionFailure = preempted && finalSelection == null,
        )
    }
}

internal interface RealtimeSubmissionWorkflowContext {
    fun isPendingDecision(cloudlet: Cloudlet): Boolean

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

    fun submitAcceptedCloudlet(submission: RealtimePendingSubmission)
}

internal class RealtimeSubmissionWorkflow(
    private val context: RealtimeSubmissionWorkflowContext,
) {
    fun onSubmit(submission: RealtimePendingSubmission): List<RealtimeBrokerCommand> {
        val cloudlet = submission.cloudlet
        if (!context.isPendingDecision(cloudlet)) {
            context.discardPending(cloudlet)
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
            FailureDecision.Continue -> {
                context.submitAcceptedCloudlet(submission)
                emptyList()
            }
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
