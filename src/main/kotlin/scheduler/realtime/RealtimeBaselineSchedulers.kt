package scheduler.realtime

import org.cloudsimplus.vms.Vm
import kotlin.math.abs

/**
 * Earliest Deadline First baseline for realtime arrivals.
 */
class RealtimeEdfScheduler(
    vmList: List<Vm>,
) : RealtimeSchedulerBase(vmList) {
    override fun scheduleOnArrival(context: RealtimeSchedulingContext): Int =
        selectAcceptedOrFallback(context, comparatorFor(context))

    private fun comparatorFor(context: RealtimeSchedulingContext): Comparator<RealtimeNodeState> =
        if (context.taskMetadata.deadline == null) {
            earliestFinishComparator(context)
        } else {
            compareBy<RealtimeNodeState> { deadlineLateness(context, it) }
                .thenBy { projectedFinishTime(context, it) }
                .then(stableCandidateTieBreaker())
        }
}

/**
 * Least Laxity First baseline for realtime arrivals.
 */
class RealtimeLlfScheduler(
    vmList: List<Vm>,
) : RealtimeSchedulerBase(vmList) {
    override fun scheduleOnArrival(context: RealtimeSchedulingContext): Int =
        selectAcceptedOrFallback(context, comparatorFor(context))

    private fun comparatorFor(context: RealtimeSchedulingContext): Comparator<RealtimeNodeState> =
        if (context.taskMetadata.deadline == null) {
            earliestFinishComparator(context)
        } else {
            compareBy<RealtimeNodeState> { (deadlineSlack(context, it) ?: 0.0) < 0.0 }
                .thenBy { abs(deadlineSlack(context, it) ?: 0.0) }
                .thenBy { projectedFinishTime(context, it) }
                .then(stableCandidateTieBreaker())
        }
}

/**
 * Earliest Finish Time baseline for realtime arrivals.
 */
class RealtimeEftScheduler(
    vmList: List<Vm>,
) : RealtimeSchedulerBase(vmList) {
    override fun scheduleOnArrival(context: RealtimeSchedulingContext): Int =
        selectAcceptedOrFallback(context, earliestFinishComparator(context))
}

/**
 * Shortest Remaining Processing Time baseline for realtime arrivals.
 */
class RealtimeSrptScheduler(
    vmList: List<Vm>,
) : RealtimeSchedulerBase(vmList) {
    override fun scheduleOnArrival(context: RealtimeSchedulingContext): Int =
        selectAcceptedOrFallback(
            context,
            compareBy<RealtimeNodeState> { estimatedRuntime(context, it) }
                .thenBy { projectedFinishTime(context, it) }
                .then(stableCandidateTieBreaker()),
        )
}

/**
 * Priority/deadline mixed baseline for scenarios where the broker exposes preemption candidates.
 */
class RealtimePriorityDeadlineScheduler(
    vmList: List<Vm>,
) : RealtimeSchedulerBase(vmList) {
    override fun scheduleOnArrival(context: RealtimeSchedulingContext): Int {
        val preemptableVmIndexes = context.preemptionCandidates.map { it.victimVmIndex.value }.toSet()
        return selectAcceptedOrFallback(
            context,
            compareBy<RealtimeNodeState> { it.vmIndex !in preemptableVmIndexes }
                .thenBy { deadlineLateness(context, it) }
                .thenBy { projectedFinishTime(context, it) }
                .thenBy { it.queueDepth }
                .thenBy { it.resourcePressure }
                .then(stableCandidateTieBreaker()),
        )
    }
}
