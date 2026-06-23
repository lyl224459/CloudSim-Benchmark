package broker

import org.cloudsimplus.cloudlets.Cloudlet
import scheduler.CloudletId
import scheduler.RealtimeObservationEventType
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
    val dependencyController: RealtimeDependencyController,
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
        val before = lifecycleService.taskRecord(cloudlet)
        val decisionToken = state.arrival.issueDecisionToken(cloudlet)
        cloudlet.setVm(environment.vmList[boundedIndex])
        state.reservation.reserve(cloudlet, boundedIndex)
        state.arrival.addPending(cloudlet)
        val after =
            before.copy(
                assignedVmIndex = boundedIndex,
                lastDecisionDelay = request.delay,
                lifecycle = RealtimeTaskLifecycle.PENDING_DECISION,
            )
        lifecycleService.updateMetadata(cloudlet) { after }
        sampleQueueDepth(request.activeCloudlets, boundedIndex, request.currentTime)
        state.metrics.recordTaskObservation(
            eventTime = request.currentTime,
            eventType = RealtimeObservationEventType.PENDING_SUBMIT,
            record = after,
            lifecycleFrom = before.lifecycle,
            lifecycleTo = after.lifecycle,
            vmIndex = boundedIndex,
            selectedVmIndex = boundedIndex,
            decision = "delay=${request.delay}",
            activeVmCount = environment.vmList.size,
        )
        return RealtimePendingSubmission(cloudlet, boundedIndex, request.delay, request.failurePressure, decisionToken)
    }

    fun rejectCloudlet(
        cloudlet: Cloudlet,
        reason: RealtimeRejectReason,
    ) {
        val before = lifecycleService.taskRecord(cloudlet)
        state.metrics.recordRejected(reason)
        cloudlet.setStatus(Cloudlet.Status.FAILED)
        state.reservation.remove(cloudlet)
        lifecycleService.updateMetadata(cloudlet) { it.copy(lifecycle = RealtimeTaskLifecycle.REJECTED) }
        state.metrics.recordTaskObservation(
            eventTime = callbacks.clock(),
            eventType = reason.rejectionEventType(),
            record = before.copy(lifecycle = RealtimeTaskLifecycle.REJECTED),
            lifecycleFrom = before.lifecycle,
            lifecycleTo = RealtimeTaskLifecycle.REJECTED,
            reason = reason.name,
        )
        callbacks.applyCommands(controllers.dependencyController.onTerminalFailure(cloudlet))
    }

    fun isCurrentPendingDecision(submission: RealtimePendingSubmission): Boolean =
        state.lifecycleStore.get(submission.cloudlet.id)?.lifecycle == RealtimeTaskLifecycle.PENDING_DECISION &&
            state.arrival.isCurrentDecisionToken(submission.cloudlet, submission.decisionToken)

    fun discardPending(cloudlet: Cloudlet) {
        state.arrival.removePending(CloudletId(cloudlet.id))
    }

    fun isCurrentRuntimeEvent(payload: RealtimeCloudletEventPayload): Boolean =
        state.arrival.isCurrentRuntimeToken(payload.cloudlet, payload.runtimeToken)

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
        val before = lifecycleService.taskRecord(cloudlet)
        state.arrival.incrementAttempt(cloudlet)
        state.reservation.remove(cloudlet)
        val after =
            before.copy(
                attempt = attempt + 1,
                assignedVmIndex = null,
                lifecycle = RealtimeTaskLifecycle.RETRYING,
            )
        lifecycleService.updateMetadata(cloudlet) { after }
        state.metrics.recordRetry()
        state.metrics.recordTaskObservation(
            eventTime = callbacks.clock(),
            eventType = RealtimeObservationEventType.RETRY_SCHEDULED,
            record = after,
            lifecycleFrom = before.lifecycle,
            lifecycleTo = after.lifecycle,
            reason = "submit_failure",
            decision = "delay=$delay",
        )
        return RealtimeBrokerCommand.ScheduleArrival(delay, cloudlet)
    }

    fun retryDeadlineAdmission(
        cloudlet: Cloudlet,
        delay: Double,
    ): RealtimeBrokerCommand {
        val attempt = state.arrival.attemptOf(cloudlet)
        val before = lifecycleService.taskRecord(cloudlet)
        state.arrival.incrementAttempt(cloudlet)
        state.reservation.remove(cloudlet)
        val after =
            before.copy(
                attempt = attempt + 1,
                assignedVmIndex = null,
                lifecycle = RealtimeTaskLifecycle.RETRYING,
            )
        lifecycleService.updateMetadata(cloudlet) { after }
        state.metrics.recordRetry()
        state.metrics.recordDeadlineRetryLater()
        state.metrics.recordTaskObservation(
            eventTime = callbacks.clock(),
            eventType = RealtimeObservationEventType.DEADLINE_RETRY_LATER,
            record = after,
            lifecycleFrom = before.lifecycle,
            lifecycleTo = after.lifecycle,
            reason = "deadline_miss",
            decision = "delay=$delay",
        )
        state.metrics.recordTaskObservation(
            eventTime = callbacks.clock(),
            eventType = RealtimeObservationEventType.RETRY_SCHEDULED,
            record = after,
            lifecycleFrom = before.lifecycle,
            lifecycleTo = after.lifecycle,
            reason = "deadline_retry_later",
            decision = "delay=$delay",
        )
        return RealtimeBrokerCommand.ScheduleArrival(delay, cloudlet)
    }

    fun permanentlyFailPendingSubmission(cloudlet: Cloudlet) {
        val before = lifecycleService.taskRecord(cloudlet)
        cloudlet.setStatus(Cloudlet.Status.FAILED)
        state.reservation.remove(cloudlet)
        lifecycleService.updateMetadata(cloudlet) { it.copy(lifecycle = RealtimeTaskLifecycle.FAILED) }
        state.metrics.recordPermanentFailure()
        state.metrics.recordTaskObservation(
            eventTime = callbacks.clock(),
            eventType = RealtimeObservationEventType.PERMANENT_FAILURE,
            record = before.copy(lifecycle = RealtimeTaskLifecycle.FAILED),
            lifecycleFrom = before.lifecycle,
            lifecycleTo = RealtimeTaskLifecycle.FAILED,
            reason = "submit_failure",
        )
        callbacks.applyCommands(controllers.dependencyController.onTerminalFailure(cloudlet))
    }

    fun submitAcceptedCloudlet(submission: RealtimePendingSubmission): List<RealtimeBrokerCommand> {
        val cloudlet = submission.cloudlet
        val before = lifecycleService.taskRecord(cloudlet)
        cloudlet.setVm(environment.vmList[submission.vmIndex])
        cloudlet.setSubmissionDelay(0.0)
        state.arrival.addWaiting(cloudlet)
        val after = before.copy(lifecycle = RealtimeTaskLifecycle.RUNNING)
        lifecycleService.updateMetadata(cloudlet) { after }
        lifecycleService.taskRecord(cloudlet).let { record ->
            controllers.runtimePlanner.recordSubmission(submission.vmIndex, record)
        }
        environment.vmLifecycleManager.markBusy(submission.vmIndex, callbacks.clock())
        if (state.arrival.attemptOf(cloudlet) > 0) {
            state.metrics.recordRetrySuccess()
        }
        state.metrics.recordSubmitted()
        state.metrics.recordTaskObservation(
            eventTime = callbacks.clock(),
            eventType = RealtimeObservationEventType.SUBMITTED,
            record = after,
            lifecycleFrom = before.lifecycle,
            lifecycleTo = after.lifecycle,
            vmIndex = submission.vmIndex,
            selectedVmIndex = submission.vmIndex,
        )
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

private fun RealtimeRejectReason.rejectionEventType(): RealtimeObservationEventType =
    when (this) {
        RealtimeRejectReason.QUEUE,
        RealtimeRejectReason.CAPACITY,
        -> RealtimeObservationEventType.CAPACITY_REJECTED
        RealtimeRejectReason.RESOURCE -> RealtimeObservationEventType.RESOURCE_REJECTED
        RealtimeRejectReason.DEADLINE -> RealtimeObservationEventType.DEADLINE_REJECTED
        RealtimeRejectReason.DEPENDENCY -> RealtimeObservationEventType.DEPENDENCY_REJECTED
        RealtimeRejectReason.TENANT_QUOTA,
        RealtimeRejectReason.TENANT_BUDGET,
        -> RealtimeObservationEventType.TENANT_REJECTED
    }
