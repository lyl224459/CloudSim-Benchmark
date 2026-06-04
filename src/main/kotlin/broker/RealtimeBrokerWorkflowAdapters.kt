package broker

import org.cloudsimplus.cloudlets.Cloudlet
import scheduler.CloudletId
import scheduler.RealtimeSchedulingContext
import scheduler.RealtimeTaskLifecycle
import scheduler.RealtimeTaskRecord

internal data class RealtimeAdmissionServices(
    val admissionController: RealtimeAdmissionController,
    val tenantController: RealtimeTenantController,
)

internal data class RealtimeArrivalCoreServices(
    val lifecycleService: RealtimeBrokerLifecycleService,
    val readModel: RealtimeBrokerReadModel,
    val vmSelectionFacade: RealtimeVmSelectionFacade,
    val submissionService: RealtimeSubmissionService,
    val metrics: RealtimeBrokerMetrics,
)

internal data class RealtimeArrivalControlServices(
    val autoscalingController: RealtimeAutoscalingController,
    val admissionServices: RealtimeAdmissionServices,
    val commandExecutor: RealtimeBrokerCommandExecutor,
)

@Suppress("TooManyFunctions")
internal class RealtimeArrivalWorkflowAdapter(
    private val core: RealtimeArrivalCoreServices,
    private val controls: RealtimeArrivalControlServices,
) : RealtimeArrivalWorkflowContext {
    override fun lifecycleOf(cloudlet: Cloudlet): RealtimeTaskLifecycle? = core.lifecycleService.lifecycleOf(cloudlet)

    override fun markArrivedAfterInterruption(cloudlet: Cloudlet) {
        core.lifecycleService.markArrivedAfterInterruption(cloudlet)
    }

    override fun refreshVmLifecycles(currentTime: Double) {
        controls.autoscalingController.refresh(currentTime, activeVmIndexes())
    }

    override fun activeCloudlets(): List<Cloudlet> = core.readModel.activeCloudlets()

    override fun scaleOutCommands(
        queueDepth: Int,
        currentTime: Double,
    ): List<RealtimeBrokerCommand> {
        val activeIndexes = activeVmIndexes()
        return controls.autoscalingController.scaleOutCommands(queueDepth, currentTime, activeIndexes)
    }

    override fun schedulingContext(
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        currentTime: Double,
    ): RealtimeSchedulingContext = core.vmSelectionFacade.schedulingContext(cloudlet, activeCloudlets, currentTime)

    override fun taskRecord(cloudlet: Cloudlet): RealtimeTaskRecord = core.lifecycleService.taskRecord(cloudlet)

    override fun decideTenantAdmission(record: RealtimeTaskRecord): TenantAdmissionDecision =
        controls.admissionServices.tenantController.decide(
            record,
            core.lifecycleService.activeTenantRecords(),
        )

    override fun decideCapacityAdmission(
        activeCloudletCount: Int,
        context: RealtimeSchedulingContext,
    ): AdmissionDecision {
        val admissionController = controls.admissionServices.admissionController
        return admissionController.decide(activeCloudletCount, context.nodeStates)
    }

    override fun tryPreemptFor(
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
    ): Boolean {
        val attempt = core.vmSelectionFacade.tryPreemptFor(cloudlet, activeCloudlets)
        controls.commandExecutor.applyAll(attempt.commands)
        return attempt.applied
    }

    override fun selectVm(
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        currentTime: Double,
    ): Pair<Int, Double>? = core.vmSelectionFacade.selectVm(cloudlet, activeCloudlets, currentTime)

    override fun latestRejectionReason(
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        currentTime: Double,
    ): RealtimeRejectReason = core.vmSelectionFacade.latestRejectionReason(cloudlet, activeCloudlets, currentTime)

    override fun rejectCloudlet(
        cloudlet: Cloudlet,
        reason: RealtimeRejectReason,
    ) {
        core.submissionService.rejectCloudlet(cloudlet, reason)
    }

    override fun decisionDelay(cloudlet: Cloudlet): Double = core.vmSelectionFacade.decisionDelay(cloudlet)

    override fun recordDecisionDelay(delay: Double) {
        core.metrics.recordDecisionDelay(delay)
    }

    override fun preparePendingSubmission(request: RealtimePendingSubmissionRequest): RealtimePendingSubmission =
        core.submissionService.preparePendingSubmission(request)

    override fun recordPreemptionFailed() {
        core.metrics.recordPreemptionFailed()
    }

    private fun activeVmIndexes(): Set<Int> = core.vmSelectionFacade.activeVmIndexes(core.readModel.activeCloudlets())
}

internal class RealtimeSubmissionWorkflowAdapter(
    private val submissionService: RealtimeSubmissionService,
) : RealtimeSubmissionWorkflowContext {
    override fun isPendingDecision(cloudlet: Cloudlet): Boolean = submissionService.isPendingDecision(cloudlet)

    override fun discardPending(cloudlet: Cloudlet) {
        submissionService.discardPending(cloudlet)
    }

    override fun attemptOf(cloudlet: Cloudlet): Int = submissionService.attemptOf(cloudlet)

    override fun submitAttemptDecision(
        cloudletId: CloudletId,
        attempt: Int,
        failurePressure: Double,
    ): FailureDecision = submissionService.submitAttemptDecision(cloudletId, attempt, failurePressure)

    override fun retryPendingSubmission(
        cloudlet: Cloudlet,
        attempt: Int,
        delay: Double,
    ): RealtimeBrokerCommand = submissionService.retryPendingSubmission(cloudlet, attempt, delay)

    override fun permanentlyFailPendingSubmission(cloudlet: Cloudlet) {
        submissionService.permanentlyFailPendingSubmission(cloudlet)
    }

    override fun submitAcceptedCloudlet(submission: RealtimePendingSubmission): List<RealtimeBrokerCommand> =
        submissionService.submitAcceptedCloudlet(submission)
}
