package scheduler.realtime

import org.cloudsimplus.vms.Vm
import kotlin.math.abs

/**
 * Earliest Deadline First baseline for realtime arrivals.
 */
class RealtimeEdfScheduler(
    vmList: List<Vm>,
) : RealtimeSchedulerBase(vmList) {
    @Suppress("MaxLineLength") // ktlint keeps simple scheduler dispatch as a body expression.
    override fun scheduleOnArrival(context: RealtimeSchedulingContext): Int = selectAcceptedOrFallback(context, comparatorFor(context))

    private fun comparatorFor(context: RealtimeSchedulingContext): Comparator<RealtimeNodeState> {
        val scoresByVmIndex = scoreByVmIndex(context)
        return if (context.taskMetadata.deadline == null) {
            earliestFinishComparator(scoresByVmIndex)
        } else {
            compareBy<RealtimeNodeState> { scoresByVmIndex[it.vmIndex]?.breakdown?.latenessPenalty ?: 0.0 }
                .thenBy { scoresByVmIndex[it.vmIndex]?.breakdown?.projectedFinishTime ?: Double.POSITIVE_INFINITY }
                .then(stableCandidateTieBreaker())
        }
    }
}

/**
 * Least Laxity First baseline for realtime arrivals.
 */
class RealtimeLlfScheduler(
    vmList: List<Vm>,
) : RealtimeSchedulerBase(vmList) {
    @Suppress("MaxLineLength") // ktlint keeps simple scheduler dispatch as a body expression.
    override fun scheduleOnArrival(context: RealtimeSchedulingContext): Int = selectAcceptedOrFallback(context, comparatorFor(context))

    private fun comparatorFor(context: RealtimeSchedulingContext): Comparator<RealtimeNodeState> {
        val scoresByVmIndex = scoreByVmIndex(context)
        return if (context.taskMetadata.deadline == null) {
            earliestFinishComparator(scoresByVmIndex)
        } else {
            compareBy<RealtimeNodeState> { (scoresByVmIndex[it.vmIndex]?.breakdown?.deadlineSlack ?: 0.0) < 0.0 }
                .thenBy { abs(scoresByVmIndex[it.vmIndex]?.breakdown?.deadlineSlack ?: 0.0) }
                .thenBy { scoresByVmIndex[it.vmIndex]?.breakdown?.projectedFinishTime ?: Double.POSITIVE_INFINITY }
                .then(stableCandidateTieBreaker())
        }
    }
}

/**
 * Earliest Finish Time baseline for realtime arrivals.
 */
class RealtimeEftScheduler(
    vmList: List<Vm>,
) : RealtimeSchedulerBase(vmList) {
    override fun scheduleOnArrival(context: RealtimeSchedulingContext): Int =
        selectAcceptedOrFallback(context, earliestFinishComparator(scoreByVmIndex(context)))
}

/**
 * Shortest Remaining Processing Time baseline for realtime arrivals.
 */
class RealtimeSrptScheduler(
    vmList: List<Vm>,
) : RealtimeSchedulerBase(vmList) {
    override fun scheduleOnArrival(context: RealtimeSchedulingContext): Int {
        val scoresByVmIndex = scoreByVmIndex(context)
        return selectAcceptedOrFallback(
            context,
            compareBy<RealtimeNodeState> {
                scoresByVmIndex[it.vmIndex]?.breakdown?.estimatedRuntime ?: Double.POSITIVE_INFINITY
            }.thenBy { scoresByVmIndex[it.vmIndex]?.breakdown?.projectedFinishTime ?: Double.POSITIVE_INFINITY }
                .then(stableCandidateTieBreaker()),
        )
    }
}

/**
 * Priority/deadline mixed baseline for scenarios where the broker exposes preemption candidates.
 */
class RealtimePriorityDeadlineScheduler(
    vmList: List<Vm>,
) : RealtimeSchedulerBase(vmList) {
    override fun scheduleOnArrival(context: RealtimeSchedulingContext): Int {
        val preemptableVmIndexes = context.preemptionCandidates.map { it.victimVmIndex.value }.toSet()
        val scoresByVmIndex = scoreByVmIndex(context)
        return selectAcceptedOrFallback(
            context,
            compareBy<RealtimeNodeState> { it.vmIndex !in preemptableVmIndexes }
                .thenBy { scoresByVmIndex[it.vmIndex]?.totalScore ?: Double.POSITIVE_INFINITY }
                .thenBy { scoresByVmIndex[it.vmIndex]?.breakdown?.latenessPenalty ?: 0.0 }
                .thenBy { scoresByVmIndex[it.vmIndex]?.breakdown?.projectedFinishTime ?: Double.POSITIVE_INFINITY }
                .then(stableCandidateTieBreaker()),
        )
    }
}

private fun earliestFinishComparator(scoresByVmIndex: Map<Int, RealtimeCandidateScore>): Comparator<RealtimeNodeState> =
    compareBy<RealtimeNodeState> {
        scoresByVmIndex[it.vmIndex]?.breakdown?.projectedFinishTime ?: Double.POSITIVE_INFINITY
    }.thenBy { scoresByVmIndex[it.vmIndex]?.totalScore ?: Double.POSITIVE_INFINITY }
        .thenBy { it.queueDepth }
        .thenBy { it.vmIndex }
