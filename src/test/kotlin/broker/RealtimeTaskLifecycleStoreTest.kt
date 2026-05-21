package broker

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import scheduler.CloudletId
import scheduler.RealtimeTaskLifecycle
import scheduler.RealtimeTaskRecord
import scheduler.VmIndex

class RealtimeTaskLifecycleStoreTest {

    @Test
    fun `value classes wrap cloudlet id and vm index safely`() {
        val record = RealtimeTaskRecord(
            cloudletId = 42L,
            originalArrivalTime = 1.5,
            assignedVmIndex = 3
        )

        assertThat(record.id).isEqualTo(CloudletId(42L))
        assertThat(record.assignedVm).isEqualTo(VmIndex(3))
    }

    @Test
    fun `valid lifecycle transitions are accepted and counted`() {
        val store = RealtimeTaskLifecycleStore()
        store.put(RealtimeTaskRecord(cloudletId = 1L, originalArrivalTime = 0.0))

        store.update(CloudletId(1L)) { it.copy(lifecycle = RealtimeTaskLifecycle.PENDING_DECISION) }
        store.update(CloudletId(1L)) { it.copy(lifecycle = RealtimeTaskLifecycle.SUBMITTED) }
        store.update(CloudletId(1L)) { it.copy(lifecycle = RealtimeTaskLifecycle.PREEMPTED, preemptedCount = 1) }
        store.update(CloudletId(1L)) { it.copy(lifecycle = RealtimeTaskLifecycle.RETRYING, attempt = 1) }

        val stats = store.stats()
        assertThat(stats.preempted).isEqualTo(1)
        assertThat(stats.retried).isEqualTo(1)
    }

    @Test
    fun `invalid lifecycle transition fails clearly`() {
        val store = RealtimeTaskLifecycleStore()
        store.put(RealtimeTaskRecord(cloudletId = 1L, originalArrivalTime = 0.0))

        assertThatThrownBy {
            store.update(CloudletId(1L)) { it.copy(lifecycle = RealtimeTaskLifecycle.COMPLETED) }
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Invalid realtime task transition")
    }
}
