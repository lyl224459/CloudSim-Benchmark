package broker

import scheduler.CloudletId
import scheduler.RealtimeTaskLifecycle
import scheduler.RealtimeTaskRecord

data class RealtimeLifecycleStats(
    val completed: Int,
    val failed: Int,
    val rejected: Int,
    val cancelled: Int,
    val timedOut: Int,
    val preempted: Int,
    val migrated: Int,
    val retried: Int,
)

class RealtimeTaskLifecycleStore {
    private val records = linkedMapOf<CloudletId, RealtimeTaskRecord>()

    fun put(record: RealtimeTaskRecord) {
        records[record.id] = record
    }

    fun get(id: CloudletId): RealtimeTaskRecord? = records[id]

    fun get(cloudletId: Long): RealtimeTaskRecord? = get(CloudletId(cloudletId))

    fun update(
        id: CloudletId,
        transform: (RealtimeTaskRecord) -> RealtimeTaskRecord,
    ): RealtimeTaskRecord {
        val current = records[id] ?: throw IllegalArgumentException("Unknown realtime cloudlet id: ${id.value}")
        val next = transform(current)
        validateTransition(current.lifecycle, next.lifecycle)
        records[id] = next
        return next
    }

    fun updateOrPut(
        record: RealtimeTaskRecord,
        transform: (RealtimeTaskRecord) -> RealtimeTaskRecord,
    ): RealtimeTaskRecord {
        records.putIfAbsent(record.id, record)
        return update(record.id, transform)
    }

    fun snapshot(): List<RealtimeTaskRecord> = records.values.toList()

    fun stats(): RealtimeLifecycleStats {
        val values = records.values
        return RealtimeLifecycleStats(
            completed = values.count { it.lifecycle == RealtimeTaskLifecycle.COMPLETED },
            failed = values.count { it.lifecycle == RealtimeTaskLifecycle.FAILED },
            rejected = values.count { it.lifecycle == RealtimeTaskLifecycle.REJECTED },
            cancelled = values.count { it.lifecycle == RealtimeTaskLifecycle.CANCELLED },
            timedOut = values.count { it.lifecycle == RealtimeTaskLifecycle.TIMED_OUT },
            preempted = values.sumOf { it.preemptedCount },
            migrated = values.sumOf { it.migratedCount },
            retried = values.count { it.attempt > 0 },
        )
    }

    private fun validateTransition(
        from: RealtimeTaskLifecycle,
        to: RealtimeTaskLifecycle,
    ) {
        if (from == to) return
        val allowed =
            when (from) {
                RealtimeTaskLifecycle.ARRIVED ->
                    setOf(
                        RealtimeTaskLifecycle.PENDING_DECISION,
                        RealtimeTaskLifecycle.RETRYING,
                        RealtimeTaskLifecycle.REJECTED,
                        RealtimeTaskLifecycle.FAILED,
                    )
                RealtimeTaskLifecycle.PENDING_DECISION ->
                    setOf(
                        RealtimeTaskLifecycle.SUBMITTED,
                        RealtimeTaskLifecycle.RUNNING,
                        RealtimeTaskLifecycle.PREEMPTED,
                        RealtimeTaskLifecycle.MIGRATING,
                        RealtimeTaskLifecycle.ARRIVED,
                        RealtimeTaskLifecycle.RETRYING,
                        RealtimeTaskLifecycle.REJECTED,
                        RealtimeTaskLifecycle.FAILED,
                    )
                RealtimeTaskLifecycle.SUBMITTED ->
                    setOf(
                        RealtimeTaskLifecycle.RUNNING,
                        RealtimeTaskLifecycle.PREEMPTED,
                        RealtimeTaskLifecycle.MIGRATING,
                        RealtimeTaskLifecycle.RETRYING,
                        RealtimeTaskLifecycle.COMPLETED,
                        RealtimeTaskLifecycle.FAILED,
                        RealtimeTaskLifecycle.CANCELLED,
                        RealtimeTaskLifecycle.TIMED_OUT,
                    )
                RealtimeTaskLifecycle.RUNNING ->
                    setOf(
                        RealtimeTaskLifecycle.PENDING_DECISION,
                        RealtimeTaskLifecycle.ARRIVED,
                        RealtimeTaskLifecycle.PREEMPTED,
                        RealtimeTaskLifecycle.MIGRATING,
                        RealtimeTaskLifecycle.RETRYING,
                        RealtimeTaskLifecycle.COMPLETED,
                        RealtimeTaskLifecycle.FAILED,
                        RealtimeTaskLifecycle.CANCELLED,
                        RealtimeTaskLifecycle.TIMED_OUT,
                    )
                RealtimeTaskLifecycle.PREEMPTED ->
                    setOf(
                        RealtimeTaskLifecycle.MIGRATING,
                        RealtimeTaskLifecycle.RETRYING,
                        RealtimeTaskLifecycle.FAILED,
                    )
                RealtimeTaskLifecycle.MIGRATING ->
                    setOf(
                        RealtimeTaskLifecycle.RETRYING,
                        RealtimeTaskLifecycle.ARRIVED,
                        RealtimeTaskLifecycle.FAILED,
                    )
                RealtimeTaskLifecycle.RETRYING ->
                    setOf(
                        RealtimeTaskLifecycle.ARRIVED,
                        RealtimeTaskLifecycle.PENDING_DECISION,
                        RealtimeTaskLifecycle.REJECTED,
                        RealtimeTaskLifecycle.FAILED,
                    )
                RealtimeTaskLifecycle.COMPLETED,
                RealtimeTaskLifecycle.REJECTED,
                RealtimeTaskLifecycle.FAILED,
                RealtimeTaskLifecycle.CANCELLED,
                RealtimeTaskLifecycle.TIMED_OUT,
                -> emptySet()
            }
        require(to in allowed) { "Invalid realtime task transition: $from -> $to" }
    }
}
