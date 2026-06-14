package broker

import config.RealtimeSchedulingConfig
import org.cloudsimplus.cloudlets.Cloudlet
import scheduler.CloudletId
import scheduler.RealtimeTaskLifecycle

private const val MIN_PREEMPTED_CLOUDLET_LENGTH = 1L

internal data class RealtimePreemptionExecutionResult(
    val applied: Boolean,
    val commands: List<RealtimeBrokerCommand> = emptyList(),
)

internal data class RealtimePreemptionState(
    val arrival: RealtimeArrivalState,
    val reservation: RealtimeReservationState,
    val metrics: RealtimeBrokerMetrics,
)

internal data class RealtimePreemptionServices(
    val failure: RealtimeFailureController,
    val recovery: RealtimeCloudletRecoveryEstimator,
    val updateMetadata: RealtimeMetadataUpdater,
)

internal class RealtimePreemptionExecutor(
    private val scheduling: RealtimeSchedulingConfig,
    private val state: RealtimePreemptionState,
    private val services: RealtimePreemptionServices,
) {
    private val migrationDelayEnabled: Boolean
        get() = scheduling.migrationDelay > 0.0

    fun preempt(decision: PreemptionDecision.Preempt): RealtimePreemptionExecutionResult =
        activeVictim(decision)?.let { preemptVictim(it, decision) }
            ?: RealtimePreemptionExecutionResult(applied = false)

    private fun activeVictim(decision: PreemptionDecision.Preempt): Cloudlet? =
        state.arrival
            .queuedCloudletsSnapshot()
            .firstOrNull { it.id == decision.victimCloudletId.value }
            ?.takeUnless(Cloudlet::isTerminalRealtimeCloudlet)

    private fun preemptVictim(
        victim: Cloudlet,
        decision: PreemptionDecision.Preempt,
    ): RealtimePreemptionExecutionResult {
        failVictimScheduler(victim)
        val recovery = services.recovery.estimate(victim)
        applyRecovery(victim, recovery)
        state.metrics.recordPreemptionSuccess(decision.delay, decision.penalty)
        if (migrates(decision)) {
            state.metrics.recordMigration()
        }
        updatePreemptedMetadata(victim, decision, recovery)
        return retryPreemptedVictim(victim, decision)
    }

    private fun failVictimScheduler(victim: Cloudlet) {
        victim.vm?.cloudletScheduler?.cloudletFail(victim)
        state.arrival.removePending(CloudletId(victim.id))
        state.arrival.removeWaiting(CloudletId(victim.id))
        state.reservation.remove(victim)
    }

    private fun applyRecovery(
        victim: Cloudlet,
        recovery: RealtimeRecoveryEstimate,
    ) {
        if (recovery.recoveredLength > 0L) {
            state.metrics.recordCheckpointRecovery()
            val remainingLength =
                (victim.length - recovery.recoveredLength)
                    .coerceAtLeast(MIN_PREEMPTED_CLOUDLET_LENGTH)
            victim.setLength(remainingLength)
        }
        state.metrics.addCheckpointLoss(recovery.lostLength)
    }

    private fun updatePreemptedMetadata(
        victim: Cloudlet,
        decision: PreemptionDecision.Preempt,
        recovery: RealtimeRecoveryEstimate,
    ) {
        services.updateMetadata(victim) {
            it.copy(
                assignedVmIndex = null,
                lifecycle = preemptedLifecycle(decision),
                preemptedCount = it.preemptedCount + 1,
                preemptionDelayTotal = it.preemptionDelayTotal + decision.delay,
                checkpointRecoveredLength = it.checkpointRecoveredLength + recovery.recoveredLength,
                checkpointLossTotal = it.checkpointLossTotal + recovery.lostLength,
                migratedCount = it.migratedCount + if (migrates(decision)) 1 else 0,
            )
        }
    }

    private fun retryPreemptedVictim(
        victim: Cloudlet,
        decision: PreemptionDecision.Preempt,
    ): RealtimePreemptionExecutionResult {
        val attempt = state.arrival.attemptOf(victim)
        state.arrival.incrementAttempt(victim)
        services.updateMetadata(victim) {
            it.copy(
                attempt = state.arrival.attemptOf(victim),
                lifecycle = RealtimeTaskLifecycle.RETRYING,
            )
        }
        state.metrics.recordRetry()
        return RealtimePreemptionExecutionResult(
            applied = true,
            commands = listOf(retryArrivalCommand(victim, decision, attempt)),
        )
    }

    private fun retryArrivalCommand(
        victim: Cloudlet,
        decision: PreemptionDecision.Preempt,
        attempt: Int,
    ): RealtimeBrokerCommand.ScheduleArrival =
        RealtimeBrokerCommand.ScheduleArrival(
            delay = decision.delay + scheduling.migrationDelay + services.failure.retryDelay(attempt),
            cloudlet = victim,
        )

    private fun preemptedLifecycle(decision: PreemptionDecision.Preempt): RealtimeTaskLifecycle =
        if (migrates(decision)) {
            RealtimeTaskLifecycle.MIGRATING
        } else {
            RealtimeTaskLifecycle.PREEMPTED
        }

    private fun migrates(preempt: PreemptionDecision.Preempt): Boolean = migrationDelayEnabled || preempt.delay > 0.0
}
