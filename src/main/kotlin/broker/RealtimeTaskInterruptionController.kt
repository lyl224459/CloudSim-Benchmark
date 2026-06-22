package broker

import config.RealtimeSchedulingConfig
import config.RealtimeTimeoutAction
import org.cloudsimplus.cloudlets.Cloudlet
import scheduler.CloudletId
import scheduler.RealtimeTaskLifecycle
import scheduler.RealtimeTaskRecord

private const val TIMEOUT_DEGRADE_LENGTH_RATIO = 0.75
private const val MIN_INTERRUPTED_CLOUDLET_LENGTH = 1L

internal typealias RealtimeMetadataUpdater = (Cloudlet, (RealtimeTaskRecord) -> RealtimeTaskRecord) -> Unit

internal data class RealtimeTaskInterruptionState(
    val arrival: RealtimeArrivalState,
    val reservation: RealtimeReservationState,
    val metrics: RealtimeBrokerMetrics,
)

internal data class RealtimeTaskInterruptionServices(
    val failure: RealtimeFailureController,
    val timeout: RealtimeTimeoutController,
    val recovery: RealtimeCloudletRecoveryEstimator,
    val updateMetadata: RealtimeMetadataUpdater,
    val onTerminalFailure: (Cloudlet) -> List<RealtimeBrokerCommand> = { emptyList() },
)

internal class RealtimeTaskInterruptionController(
    private val scheduling: RealtimeSchedulingConfig,
    private val state: RealtimeTaskInterruptionState,
    private val services: RealtimeTaskInterruptionServices,
) {
    private val retryHandler = RealtimeInterruptedCloudletRetryHandler(scheduling, state, services)

    fun onTimeout(
        cloudlet: Cloudlet,
        attempt: Int,
        runtimeToken: Int,
    ): List<RealtimeBrokerCommand> =
        if (shouldIgnoreInterruption(cloudlet, attempt, runtimeToken)) {
            emptyList()
        } else {
            handleTimeout(cloudlet)
        }

    fun onRuntimeFailure(
        cloudlet: Cloudlet,
        attempt: Int,
        runtimeToken: Int,
    ): List<RealtimeBrokerCommand> =
        if (shouldIgnoreInterruption(cloudlet, attempt, runtimeToken)) {
            emptyList()
        } else {
            state.metrics.recordRuntimeFailure()
            retryHandler.retryInterruptedCloudlet(cloudlet, "runtime_failure")
        }

    private fun handleTimeout(cloudlet: Cloudlet): List<RealtimeBrokerCommand> =
        when (services.timeout.decide().action) {
            RealtimeTimeoutAction.FAIL -> failRunningCloudlet(cloudlet, "timeout_fail")
            RealtimeTimeoutAction.CANCEL -> cancelCloudlet(cloudlet)
            RealtimeTimeoutAction.RETRY -> retryTimedOutCloudlet(cloudlet)
            RealtimeTimeoutAction.DEGRADE -> degradeCloudlet(cloudlet)
        }

    private fun retryTimedOutCloudlet(cloudlet: Cloudlet): List<RealtimeBrokerCommand> {
        state.metrics.recordTimeoutCancelled()
        return retryHandler.retryInterruptedCloudlet(cloudlet, "timeout_retry")
    }

    private fun degradeCloudlet(cloudlet: Cloudlet): List<RealtimeBrokerCommand> {
        val degradedLength = (cloudlet.length * TIMEOUT_DEGRADE_LENGTH_RATIO).toLong()
        cloudlet.setLength(degradedLength.coerceAtLeast(MIN_INTERRUPTED_CLOUDLET_LENGTH))
        services.updateMetadata(cloudlet) { it.copy(timeoutActionTaken = scheduling.timeoutAction) }
        return emptyList()
    }

    private fun failRunningCloudlet(
        cloudlet: Cloudlet,
        reason: String,
    ): List<RealtimeBrokerCommand> {
        cloudlet.vm?.cloudletScheduler?.cloudletFail(cloudlet)
        services.updateMetadata(cloudlet) {
            it.copy(
                timeoutActionTaken = reason,
                lifecycle = RealtimeTaskLifecycle.FAILED,
            )
        }
        return retryHandler.markPermanentFailure(cloudlet)
    }

    private fun cancelCloudlet(cloudlet: Cloudlet): List<RealtimeBrokerCommand> {
        cloudlet.vm?.cloudletScheduler?.cloudletCancel(cloudlet)
        cloudlet.setStatus(Cloudlet.Status.FAILED)
        state.metrics.recordTimeoutCancelled()
        services.updateMetadata(cloudlet) { it.copy(timeoutActionTaken = scheduling.timeoutAction) }
        return retryHandler.markPermanentFailure(cloudlet)
    }

    private fun shouldIgnoreInterruption(
        cloudlet: Cloudlet,
        attempt: Int,
        runtimeToken: Int,
    ): Boolean =
        attempt != state.arrival.attemptOf(cloudlet) ||
            !state.arrival.isCurrentRuntimeToken(cloudlet, runtimeToken) ||
            cloudlet.isTerminalRealtimeCloudlet()
}

internal class RealtimeInterruptedCloudletRetryHandler(
    private val scheduling: RealtimeSchedulingConfig,
    private val state: RealtimeTaskInterruptionState,
    private val services: RealtimeTaskInterruptionServices,
) {
    fun retryInterruptedCloudlet(
        cloudlet: Cloudlet,
        reason: String,
    ): List<RealtimeBrokerCommand> {
        cloudlet.vm?.cloudletScheduler?.cloudletFail(cloudlet)
        if (scheduling.migrationDelay > 0.0) {
            state.metrics.recordMigration()
        }
        val recovery = services.recovery.estimate(cloudlet)
        applyRecovery(cloudlet, recovery)
        updateInterruptedMetadata(cloudlet, reason, recovery)
        return retryCloudlet(cloudlet)
    }

    fun retryCloudlet(cloudlet: Cloudlet): List<RealtimeBrokerCommand> {
        val attempt = state.arrival.attemptOf(cloudlet)
        return if (attempt >= scheduling.retryLimit) {
            markPermanentFailure(cloudlet)
        } else {
            scheduleRetry(cloudlet, attempt)
        }
    }

    private fun scheduleRetry(
        cloudlet: Cloudlet,
        attempt: Int,
    ): List<RealtimeBrokerCommand> {
        state.arrival.incrementAttempt(cloudlet)
        clearQueuedState(cloudlet)
        services.updateMetadata(cloudlet) {
            it.copy(
                attempt = attempt + 1,
                assignedVmIndex = null,
                lifecycle = RealtimeTaskLifecycle.RETRYING,
            )
        }
        state.metrics.recordRetry()
        return listOf(
            RealtimeBrokerCommand.ScheduleArrival(
                delay = services.failure.retryDelay(attempt) + scheduling.migrationDelay,
                cloudlet = cloudlet,
            ),
        )
    }

    private fun applyRecovery(
        cloudlet: Cloudlet,
        recovery: RealtimeRecoveryEstimate,
    ) {
        if (recovery.recoveredLength > 0L) {
            state.metrics.recordCheckpointRecovery()
            val remainingLength =
                (cloudlet.length - recovery.recoveredLength)
                    .coerceAtLeast(MIN_INTERRUPTED_CLOUDLET_LENGTH)
            cloudlet.setLength(remainingLength)
        }
        state.metrics.addCheckpointLoss(recovery.lostLength)
    }

    private fun updateInterruptedMetadata(
        cloudlet: Cloudlet,
        reason: String,
        recovery: RealtimeRecoveryEstimate,
    ) {
        services.updateMetadata(cloudlet) {
            it.copy(
                interruptedCount = it.interruptedCount + 1,
                checkpointRecoveredLength = it.checkpointRecoveredLength + recovery.recoveredLength,
                checkpointLossTotal = it.checkpointLossTotal + recovery.lostLength,
                timeoutActionTaken =
                    if (reason.startsWith("timeout")) {
                        scheduling.timeoutAction
                    } else {
                        it.timeoutActionTaken
                    },
                migratedCount = it.migratedCount + if (scheduling.migrationDelay > 0.0) 1 else 0,
            )
        }
    }

    private fun clearQueuedState(cloudlet: Cloudlet) {
        state.reservation.remove(cloudlet)
        state.arrival.removeWaiting(CloudletId(cloudlet.id))
        state.arrival.removePending(CloudletId(cloudlet.id))
    }

    fun markPermanentFailure(cloudlet: Cloudlet): List<RealtimeBrokerCommand> {
        cloudlet.setStatus(Cloudlet.Status.FAILED)
        state.reservation.remove(cloudlet)
        state.metrics.recordPermanentFailure()
        services.updateMetadata(cloudlet) { it.copy(lifecycle = RealtimeTaskLifecycle.FAILED) }
        return services.onTerminalFailure(cloudlet)
    }
}
