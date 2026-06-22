package broker

import org.cloudsimplus.cloudlets.Cloudlet
import scheduler.CloudletId
import scheduler.RealtimeTaskLifecycle

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

@Suppress("TooManyFunctions") // Submission service owns all state transitions around pending submission attempts.
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

    fun retryDeadlineAdmission(
        cloudlet: Cloudlet,
        delay: Double,
    ): RealtimeBrokerCommand {
        val attempt = state.arrival.attemptOf(cloudlet)
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
        state.metrics.recordDeadlineRetryLater()
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
