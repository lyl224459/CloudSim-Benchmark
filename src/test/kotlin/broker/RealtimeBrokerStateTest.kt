package broker

import org.assertj.core.api.Assertions.assertThat
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.cloudlets.CloudletSimple
import org.cloudsimplus.utilizationmodels.UtilizationModelFull
import org.cloudsimplus.vms.VmSimple
import org.junit.jupiter.api.Test
import scheduler.CloudletId
import scheduler.RealtimeTaskLifecycle
import scheduler.VmIndex

class RealtimeBrokerStateTest {
    @Test
    fun `arrival state tracks attempts preassignment and immutable snapshots`() {
        val state = RealtimeArrivalState()
        val cloudlet = createCloudlet(id = 42, submissionDelay = 3.5)

        state.recordArrival(cloudlet)
        state.addPending(cloudlet)
        state.preassign(cloudlet, vmIndex = 2)
        state.incrementAttempt(cloudlet)
        state.incrementAttempt(cloudlet)
        val snapshot = state.snapshot()
        state.incrementAttempt(cloudlet)

        assertThat(state.arrivalTimeOf(cloudlet)).isEqualTo(3.5)
        assertThat(state.preassignedVmIndexOf(cloudlet)).isEqualTo(2)
        assertThat(state.attemptOf(cloudlet)).isEqualTo(3)
        assertThat(snapshot.pendingCloudletIds).containsExactly(CloudletId(42L))
        assertThat(snapshot.preassignedVmIndexes).containsEntry(CloudletId(42L), VmIndex(2))
        assertThat(snapshot.attempts).containsEntry(CloudletId(42L), 2)
    }

    @Test
    fun `reservation state prunes failed and rejected reservations`() {
        val state = RealtimeReservationState()
        val failed = createCloudlet(id = 1)
        val running = createCloudlet(id = 2)

        state.reserve(failed, vmIndex = 3)
        state.reserve(running, vmIndex = 4)
        state.prune { cloudletId ->
            when (cloudletId) {
                CloudletId(1L) -> RealtimeTaskLifecycle.FAILED
                CloudletId(2L) -> RealtimeTaskLifecycle.RUNNING
                else -> null
            }
        }

        assertThat(state.rawReservations()).containsExactlyEntriesOf(mapOf(2L to 4))
        assertThat(state.snapshot().reservations).containsEntry(CloudletId(2L), VmIndex(4))
    }

    @Test
    fun `metrics state records reject counters and queue depth samples`() {
        val metrics = RealtimeBrokerMetrics()

        metrics.recordRejected(RealtimeRejectReason.CAPACITY)
        metrics.recordRejected(RealtimeRejectReason.RESOURCE)
        metrics.recordDecisionDelay(2.0)
        metrics.recordDecisionDelay(4.0)
        metrics.recordQueueDepth(3)
        metrics.recordQueueDepth(7)
        metrics.recordRetry()
        metrics.recordRetrySuccess()
        metrics.recordTimeoutCancelled()
        metrics.recordTopologyFailure(RealtimeFailureDomain.HOST)

        val snapshot = metrics.snapshot()
        assertThat(snapshot.rejectedCount).isEqualTo(2)
        assertThat(snapshot.capacityRejectedCount).isEqualTo(1)
        assertThat(snapshot.resourceRejectedCount).isEqualTo(1)
        assertThat(snapshot.averageDecisionDelay).isEqualTo(3.0)
        assertThat(snapshot.averageQueueDepth).isEqualTo(5.0)
        assertThat(snapshot.maxQueueDepth).isEqualTo(7)
        assertThat(snapshot.retrySuccessCount).isEqualTo(1)
        assertThat(snapshot.timeoutCancelledCount).isEqualTo(1)
        assertThat(snapshot.hostFailureCount).isEqualTo(1)
    }

    @Test
    fun `active vm resolver maps vm ids back to indexes`() {
        val reservationState = RealtimeReservationState()
        val vm0 = VmSimple(1000.0, 1).also { it.setId(10) }
        val vm1 = VmSimple(1000.0, 1).also { it.setId(11) }
        val cloudlet = createCloudlet(id = 7).also { it.setVm(vm1) }

        val indexes = RealtimeActiveVmIndexResolver(listOf(vm0, vm1), reservationState).indexesFor(listOf(cloudlet))

        assertThat(indexes).containsExactly(1)
    }

    @Test
    fun `active vm resolver prefers reservation indexes over cloudlet vm ids`() {
        val reservationState = RealtimeReservationState()
        val vm0 = VmSimple(1000.0, 1).also { it.setId(10) }
        val vm1 = VmSimple(1000.0, 1).also { it.setId(11) }
        val cloudlet = createCloudlet(id = 8).also { it.setVm(vm1) }

        reservationState.reserve(cloudlet, vmIndex = 0)

        val indexes = RealtimeActiveVmIndexResolver(listOf(vm0, vm1), reservationState).indexesFor(listOf(cloudlet))

        assertThat(indexes).containsExactly(0)
    }

    private fun createCloudlet(
        id: Int,
        submissionDelay: Double = 0.0,
    ): Cloudlet {
        val utilizationModel = UtilizationModelFull()
        return CloudletSimple(1000, 1).apply {
            setId(id.toLong())
            setFileSize(100)
            setOutputSize(100)
            setSubmissionDelay(submissionDelay)
            setUtilizationModelCpu(utilizationModel)
            setUtilizationModelRam(utilizationModel)
            setUtilizationModelBw(utilizationModel)
        }
    }
}
