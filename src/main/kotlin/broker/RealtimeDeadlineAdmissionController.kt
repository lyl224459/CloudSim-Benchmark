package broker

import config.RealtimeDeadlineMissAction
import config.RealtimeDeadlineType
import config.RealtimeSchedulingConfig
import scheduler.NodeCandidate
import scheduler.RealtimeCandidateScoreCalculator
import scheduler.RealtimeNodeState
import scheduler.RealtimeSchedulingContext
import kotlin.math.pow

private const val DEADLINE_MISS_REASON = "deadline_miss"

internal enum class DeadlineAdmissionMetricAction {
    MISS_ACCEPTED,
    DEGRADED,
}

internal sealed interface DeadlineAdmissionResult {
    data class Continue(
        val context: RealtimeSchedulingContext,
        val metricAction: DeadlineAdmissionMetricAction? = null,
    ) : DeadlineAdmissionResult

    data object NeedsPreemption : DeadlineAdmissionResult

    data object Reject : DeadlineAdmissionResult

    data class RetryLater(
        val delay: Double,
    ) : DeadlineAdmissionResult
}

internal class RealtimeDeadlineAdmissionController(
    private val scheduling: RealtimeSchedulingConfig,
    private val scoreCalculator: RealtimeCandidateScoreCalculator = RealtimeCandidateScoreCalculator(),
) {
    fun evaluate(
        context: RealtimeSchedulingContext,
        attempt: Int,
        allowPreemption: Boolean,
    ): DeadlineAdmissionResult {
        val acceptedScores = scoreCalculator.scoreAccepted(context)
        val onTimeVmIndexes =
            acceptedScores
                .filter { it.breakdown.deadlineSlack >= 0.0 }
                .map { it.vmIndex }
                .toSet()
        return when {
            !scheduling.deadlineAdmissionEnabled || context.taskMetadata.deadline == null ->
                DeadlineAdmissionResult.Continue(context)
            acceptedScores.isEmpty() -> DeadlineAdmissionResult.Continue(context)
            onTimeVmIndexes.isNotEmpty() -> onTimeAdmissionResult(context, onTimeVmIndexes)
            allowPreemption && context.preemptionCandidates.isNotEmpty() -> DeadlineAdmissionResult.NeedsPreemption
            else -> allMissAdmissionResult(context, attempt)
        }
    }

    private fun onTimeAdmissionResult(
        context: RealtimeSchedulingContext,
        onTimeVmIndexes: Set<Int>,
    ): DeadlineAdmissionResult =
        when (scheduling.normalizedDeadlineType()) {
            RealtimeDeadlineType.SOFT -> DeadlineAdmissionResult.Continue(context)
            RealtimeDeadlineType.FIRM,
            RealtimeDeadlineType.HARD,
            -> DeadlineAdmissionResult.Continue(context.restrictDeadlineEligibleCandidates(onTimeVmIndexes))
        }

    private fun allMissAdmissionResult(
        context: RealtimeSchedulingContext,
        attempt: Int,
    ): DeadlineAdmissionResult =
        when (scheduling.normalizedDeadlineMissAction()) {
            RealtimeDeadlineMissAction.ACCEPT ->
                DeadlineAdmissionResult.Continue(context, DeadlineAdmissionMetricAction.MISS_ACCEPTED)
            RealtimeDeadlineMissAction.DEGRADE ->
                DeadlineAdmissionResult.Continue(context, DeadlineAdmissionMetricAction.DEGRADED)
            RealtimeDeadlineMissAction.REJECT -> DeadlineAdmissionResult.Reject
            RealtimeDeadlineMissAction.RETRY_LATER ->
                if (attempt < scheduling.retryLimit) {
                    DeadlineAdmissionResult.RetryLater(retryDelay(attempt))
                } else {
                    DeadlineAdmissionResult.Reject
                }
        }

    private fun retryDelay(attempt: Int): Double =
        if (scheduling.retryDelay <= 0.0) {
            0.0
        } else {
            scheduling.retryDelay * scheduling.retryBackoffMultiplier.pow(attempt.toDouble())
        }
}

private fun RealtimeSchedulingContext.restrictDeadlineEligibleCandidates(
    vmIndexes: Set<Int>,
): RealtimeSchedulingContext =
    copy(
        nodeStates = nodeStates.map { it.withDeadlineEligibility(vmIndexes) },
        nodeCandidates = nodeCandidates.map { it.withDeadlineEligibility(vmIndexes) },
    )

private fun NodeCandidate.withDeadlineEligibility(vmIndexes: Set<Int>): NodeCandidate =
    copy(nodeState = nodeState.withDeadlineEligibility(vmIndexes))

private fun RealtimeNodeState.withDeadlineEligibility(vmIndexes: Set<Int>): RealtimeNodeState =
    if (acceptingWork && vmIndex !in vmIndexes) {
        copy(
            acceptingWork = false,
            rejectionReason = rejectionReason ?: DEADLINE_MISS_REASON,
            placementFailureReason = placementFailureReason ?: DEADLINE_MISS_REASON,
        )
    } else {
        this
    }
