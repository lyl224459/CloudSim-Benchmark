package broker

import scheduler.RealtimeTaskLifecycle

internal object RealtimeTaskTransitionPolicy {
    private val transitions =
        mapOf(
            RealtimeTaskLifecycle.ARRIVED to
                allowed(
                    RealtimeTaskLifecycle.PENDING_DECISION,
                    RealtimeTaskLifecycle.RETRYING,
                    RealtimeTaskLifecycle.REJECTED,
                    RealtimeTaskLifecycle.FAILED,
                ),
            RealtimeTaskLifecycle.PENDING_DECISION to
                allowed(
                    RealtimeTaskLifecycle.SUBMITTED,
                    RealtimeTaskLifecycle.RUNNING,
                    RealtimeTaskLifecycle.PREEMPTED,
                    RealtimeTaskLifecycle.MIGRATING,
                    RealtimeTaskLifecycle.ARRIVED,
                    RealtimeTaskLifecycle.RETRYING,
                    RealtimeTaskLifecycle.REJECTED,
                    RealtimeTaskLifecycle.FAILED,
                ),
            RealtimeTaskLifecycle.SUBMITTED to
                allowed(
                    RealtimeTaskLifecycle.RUNNING,
                    RealtimeTaskLifecycle.PREEMPTED,
                    RealtimeTaskLifecycle.MIGRATING,
                    RealtimeTaskLifecycle.RETRYING,
                    RealtimeTaskLifecycle.COMPLETED,
                    RealtimeTaskLifecycle.FAILED,
                    RealtimeTaskLifecycle.CANCELLED,
                    RealtimeTaskLifecycle.TIMED_OUT,
                ),
            RealtimeTaskLifecycle.RUNNING to
                allowed(
                    RealtimeTaskLifecycle.PENDING_DECISION,
                    RealtimeTaskLifecycle.ARRIVED,
                    RealtimeTaskLifecycle.PREEMPTED,
                    RealtimeTaskLifecycle.MIGRATING,
                    RealtimeTaskLifecycle.RETRYING,
                    RealtimeTaskLifecycle.COMPLETED,
                    RealtimeTaskLifecycle.FAILED,
                    RealtimeTaskLifecycle.CANCELLED,
                    RealtimeTaskLifecycle.TIMED_OUT,
                ),
            RealtimeTaskLifecycle.PREEMPTED to
                allowed(
                    RealtimeTaskLifecycle.MIGRATING,
                    RealtimeTaskLifecycle.RETRYING,
                    RealtimeTaskLifecycle.FAILED,
                ),
            RealtimeTaskLifecycle.MIGRATING to
                allowed(
                    RealtimeTaskLifecycle.RETRYING,
                    RealtimeTaskLifecycle.ARRIVED,
                    RealtimeTaskLifecycle.FAILED,
                ),
            RealtimeTaskLifecycle.RETRYING to
                allowed(
                    RealtimeTaskLifecycle.ARRIVED,
                    RealtimeTaskLifecycle.PENDING_DECISION,
                    RealtimeTaskLifecycle.REJECTED,
                    RealtimeTaskLifecycle.FAILED,
                ),
        )

    fun allows(
        from: RealtimeTaskLifecycle,
        to: RealtimeTaskLifecycle,
    ): Boolean = from == to || to in transitions[from].orEmpty()

    private fun allowed(vararg values: RealtimeTaskLifecycle): Set<RealtimeTaskLifecycle> = values.toSet()
}
