package broker

import config.RealtimeReschedulingPolicy
import config.RealtimeSchedulingConfig
import org.cloudsimplus.cloudlets.Cloudlet
import scheduler.CloudletId
import scheduler.RealtimeCandidateScore
import scheduler.RealtimeCandidateScoreCalculator
import scheduler.RealtimeTaskLifecycle
import scheduler.RealtimeTaskRecord

private const val SCORE_IMPROVEMENT_EPSILON = 1.0e-9
private const val MIN_RESCHEDULED_CLOUDLET_LENGTH = 1L

internal class RealtimeReschedulingController(
    private val scheduling: RealtimeSchedulingConfig,
    private val state: RealtimeBrokerStateBundle,
    private val environment: RealtimeBrokerEnvironment,
    private val lifecycleService: RealtimeBrokerLifecycleService,
    private val vmSelectionFacade: RealtimeVmSelectionFacade,
    private val submissionService: RealtimeSubmissionService,
    private val recoveryEstimator: RealtimeCloudletRecoveryEstimator,
) {
    private val scoreCalculator = RealtimeCandidateScoreCalculator()

    fun tickCommands(currentTime: Double): List<RealtimeBrokerCommand> {
        if (!scheduling.reschedulingEnabled) return emptyList()
        val activeCloudlets = activeCloudlets()
        environment.vmLifecycleManager.refresh(currentTime, vmSelectionFacade.activeVmIndexes(activeCloudlets))
        val rescheduleCommands =
            orderedCandidates(activeCloudlets, currentTime).mapNotNull { cloudlet ->
                rescheduleIfUseful(cloudlet, activeCloudlets, currentTime)
            }
        return rescheduleCommands + nextTickCommand(currentTime)
    }

    private fun rescheduleIfUseful(
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        currentTime: Double,
    ): RealtimeBrokerCommand? {
        val record = lifecycleService.taskRecord(cloudlet)
        if (record.rescheduleCount >= scheduling.maxReschedulesPerTask) return null
        val currentVmIndex = currentVmIndex(cloudlet, record) ?: return null
        val activeWithoutSelf = activeCloudlets.filterNot { it.id == cloudlet.id }
        val context = vmSelectionFacade.schedulingContext(cloudlet, activeWithoutSelf, currentTime)
        val outcome =
            vmSelectionFacade.selectVm(
                cloudlet = cloudlet,
                activeCloudlets = activeWithoutSelf,
                currentTime = currentTime,
                allowDeadlinePreemption = false,
                recordSelectionMetrics = false,
            )
        state.metrics.recordRescheduleAttempt()
        val selected = outcome as? RealtimeVmSelectionOutcome.Selected
        if (selected == null || selected.vmIndex == currentVmIndex) {
            state.metrics.recordRescheduleFailure()
            return null
        }
        val scores = scoreCalculator.scoreAccepted(context)
        val currentScore = scores.firstOrNull { it.vmIndex == currentVmIndex }
        val selectedScore = scores.firstOrNull { it.vmIndex == selected.vmIndex }
        if (selectedScore == null || !shouldReschedule(record.deadline != null, currentScore, selectedScore)) {
            state.metrics.recordRescheduleFailure()
            return null
        }
        return executeReschedule(cloudlet, activeWithoutSelf, currentTime, selected)
    }

    private fun shouldReschedule(
        hasDeadline: Boolean,
        currentScore: RealtimeCandidateScore?,
        selectedScore: RealtimeCandidateScore,
    ): Boolean {
        val currentTotal = currentScore?.totalScore ?: Double.POSITIVE_INFINITY
        val selectedTotal = selectedScore.totalScore
        val scoreImproves = selectedTotal + SCORE_IMPROVEMENT_EPSILON < currentTotal
        val currentSlack = currentScore?.breakdown?.deadlineSlack ?: Double.NEGATIVE_INFINITY
        val selectedSlack = selectedScore.breakdown.deadlineSlack
        val deadlineImproves =
            hasDeadline &&
                currentSlack < 0.0 &&
                selectedSlack > currentSlack + SCORE_IMPROVEMENT_EPSILON
        return when (scheduling.normalizedReschedulingPolicy()) {
            RealtimeReschedulingPolicy.DEADLINE_SCORE -> deadlineImproves || scoreImproves
            RealtimeReschedulingPolicy.SCORE_ONLY -> scoreImproves
            RealtimeReschedulingPolicy.DEADLINE_ONLY -> deadlineImproves
        }
    }

    private fun executeReschedule(
        cloudlet: Cloudlet,
        activeWithoutSelf: List<Cloudlet>,
        currentTime: Double,
        selected: RealtimeVmSelectionOutcome.Selected,
    ): RealtimeBrokerCommand {
        val wasRunning = lifecycleService.lifecycleOf(cloudlet) == RealtimeTaskLifecycle.RUNNING
        val migrationDelay = if (wasRunning) scheduling.migrationDelay else 0.0
        if (wasRunning) {
            interruptRunningCloudlet(cloudlet)
        } else {
            clearPendingCloudlet(cloudlet)
        }
        val decisionDelay = vmSelectionFacade.decisionDelay(cloudlet)
        val totalDelay = migrationDelay + decisionDelay
        val rescheduleCount = state.arrival.incrementRescheduleCount(cloudlet)
        lifecycleService.updateMetadata(cloudlet) {
            it.copy(
                rescheduleCount = rescheduleCount,
                migratedCount = it.migratedCount + if (migrationDelay > 0.0) 1 else 0,
            )
        }
        if (migrationDelay > 0.0) {
            state.metrics.recordMigration()
        }
        state.metrics.recordRescheduleSuccess(totalDelay)
        val submission =
            submissionService.preparePendingSubmission(
                RealtimePendingSubmissionRequest(
                    cloudlet = cloudlet,
                    selectedVmIndex = selected.vmIndex,
                    activeCloudlets = activeWithoutSelf,
                    currentTime = currentTime,
                    delay = totalDelay,
                    failurePressure = selected.failurePressure,
                ),
            )
        return RealtimeBrokerCommand.ScheduleSubmit(totalDelay, submission)
    }

    private fun interruptRunningCloudlet(cloudlet: Cloudlet) {
        cloudlet.vm?.cloudletScheduler?.cloudletFail(cloudlet)
        val recovery = recoveryEstimator.estimate(cloudlet)
        if (recovery.recoveredLength > 0L) {
            state.metrics.recordCheckpointRecovery()
            cloudlet.setLength(
                (cloudlet.length - recovery.recoveredLength).coerceAtLeast(MIN_RESCHEDULED_CLOUDLET_LENGTH),
            )
        }
        state.metrics.addCheckpointLoss(recovery.lostLength)
        clearQueuedState(cloudlet)
    }

    private fun clearPendingCloudlet(cloudlet: Cloudlet) {
        state.arrival.removePending(CloudletId(cloudlet.id))
        state.reservation.remove(cloudlet)
    }

    private fun clearQueuedState(cloudlet: Cloudlet) {
        state.arrival.removePending(CloudletId(cloudlet.id))
        state.arrival.removeWaiting(CloudletId(cloudlet.id))
        state.reservation.remove(cloudlet)
    }

    private fun activeCloudlets(): List<Cloudlet> =
        (state.arrival.pendingCloudletsSnapshot() + state.arrival.waitingCloudletsSnapshot())
            .distinctBy { it.id }
            .filterNot(Cloudlet::isTerminalRealtimeCloudlet)

    private fun orderedCandidates(
        activeCloudlets: List<Cloudlet>,
        currentTime: Double,
    ): List<Cloudlet> =
        activeCloudlets.sortedWith(
            compareBy<Cloudlet> {
                lifecycleService.taskRecord(it).deadline?.minus(currentTime) ?: Double.POSITIVE_INFINITY
            }.thenByDescending {
                lifecycleService.taskRecord(it).priority
            }.thenBy {
                state.arrival.rescheduleCountOf(it)
            }.thenBy {
                it.id
            },
        )

    private fun currentVmIndex(
        cloudlet: Cloudlet,
        record: RealtimeTaskRecord,
    ): Int? =
        state.reservation.assignedVmIndexOf(cloudlet)
            ?: record.assignedVmIndex
            ?: cloudlet.vm?.let { vm -> environment.vmList.indexOfFirst { it.id == vm.id }.takeIf { it >= 0 } }

    private fun nextTickCommand(currentTime: Double): List<RealtimeBrokerCommand> =
        if (shouldScheduleNextTick(currentTime)) {
            listOf(RealtimeBrokerCommand.ScheduleRescheduleTick(scheduling.reschedulingInterval))
        } else {
            emptyList()
        }

    private fun shouldScheduleNextTick(currentTime: Double): Boolean =
        scheduling.reschedulingEnabled &&
            scheduling.reschedulingInterval > 0.0 &&
            (hasEligibleActiveCandidate() || hasFutureRealtimeArrival(currentTime))

    private fun hasEligibleActiveCandidate(): Boolean =
        activeCloudlets().any { cloudlet ->
            lifecycleService.taskRecord(cloudlet).rescheduleCount < scheduling.maxReschedulesPerTask
        }

    private fun hasFutureRealtimeArrival(currentTime: Double): Boolean =
        state.arrival.realtimeCloudletsSnapshot().any { cloudlet ->
            !cloudlet.isTerminalRealtimeCloudlet() && state.arrival.arrivalTimeOf(cloudlet) > currentTime
        }
}
