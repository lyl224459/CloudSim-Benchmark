package broker

import config.RealtimeSchedulingConfig
import org.assertj.core.api.Assertions.assertThat
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.cloudlets.CloudletSimple
import org.cloudsimplus.vms.VmSimple
import org.junit.jupiter.api.Test
import scheduler.RealtimeTaskLifecycle
import scheduler.RealtimeTaskRecord

class RealtimeTaskInterruptionControllerBranchTest {
    @Test
    fun `stale attempts and terminal cloudlets are ignored`() {
        val stale = harness(RealtimeSchedulingConfig(), cloudlet())
        val terminalCloudlet = cloudlet().apply { setStatus(Cloudlet.Status.FAILED) }
        val terminal = harness(RealtimeSchedulingConfig(), terminalCloudlet)

        assertThat(
            stale.controller.onRuntimeFailure(stale.cloudlet, attempt = 1, runtimeToken = stale.runtimeToken),
        ).isEmpty()
        assertThat(
            stale.controller.onRuntimeFailure(stale.cloudlet, attempt = 0, runtimeToken = stale.runtimeToken + 1),
        ).isEmpty()
        assertThat(
            terminal.controller.onTimeout(terminal.cloudlet, attempt = 0, runtimeToken = terminal.runtimeToken),
        ).isEmpty()
        assertThat(stale.metrics.snapshot().runtimeFailureCount).isZero()
        assertThat(terminal.metadata.lifecycle).isEqualTo(RealtimeTaskLifecycle.ARRIVED)
    }

    @Test
    fun `timeout fail cancel and degrade actions preserve their contracts`() {
        val failed = harness(RealtimeSchedulingConfig(timeoutAction = "fail"), cloudlet())
        val cancelled = harness(RealtimeSchedulingConfig(timeoutAction = "cancel"), cloudlet())
        val degraded = harness(RealtimeSchedulingConfig(timeoutAction = "degrade"), cloudlet())

        failed.controller.onTimeout(failed.cloudlet, attempt = 0, runtimeToken = failed.runtimeToken)
        cancelled.controller.onTimeout(cancelled.cloudlet, attempt = 0, runtimeToken = cancelled.runtimeToken)
        degraded.controller.onTimeout(degraded.cloudlet, attempt = 0, runtimeToken = degraded.runtimeToken)

        assertThat(failed.metadata.lifecycle).isEqualTo(RealtimeTaskLifecycle.FAILED)
        assertThat(failed.metrics.snapshot().permanentFailedCount).isEqualTo(1)
        assertThat(cancelled.cloudlet.status).isEqualTo(Cloudlet.Status.FAILED)
        assertThat(cancelled.metrics.snapshot().timeoutCancelledCount).isEqualTo(1)
        assertThat(degraded.cloudlet.length).isEqualTo(750)
        assertThat(degraded.metadata.timeoutActionTaken).isEqualTo("degrade")
    }

    @Test
    fun `runtime failure stops at retry limit`() {
        val harness = harness(RealtimeSchedulingConfig(retryLimit = 0), cloudlet())

        val commands =
            harness.controller.onRuntimeFailure(harness.cloudlet, attempt = 0, runtimeToken = harness.runtimeToken)

        assertThat(commands).isEmpty()
        assertThat(harness.metadata.lifecycle).isEqualTo(RealtimeTaskLifecycle.FAILED)
        assertThat(harness.metrics.snapshot().runtimeFailureCount).isEqualTo(1)
        assertThat(harness.metrics.snapshot().permanentFailedCount).isEqualTo(1)
    }

    @Test
    fun `runtime failure retry records migration checkpoint recovery and loss`() {
        val scheduling =
            RealtimeSchedulingConfig(
                retryLimit = 1,
                retryDelay = 0.25,
                migrationDelay = 0.5,
                checkpointInterval = 1.0,
            )
        val harness = harness(scheduling, cloudlet(), clock = 2.0)

        val commands =
            harness.controller.onRuntimeFailure(harness.cloudlet, attempt = 0, runtimeToken = harness.runtimeToken)
        val snapshot = harness.metrics.snapshot()

        assertThat(commands).containsExactly(
            RealtimeBrokerCommand.ScheduleArrival(delay = 0.75, cloudlet = harness.cloudlet),
        )
        assertThat(snapshot.migrationCount).isEqualTo(1)
        assertThat(snapshot.checkpointRecoveryCount).isEqualTo(1)
        assertThat(snapshot.checkpointLossTotal).isPositive()
        assertThat(harness.metadata.lifecycle).isEqualTo(RealtimeTaskLifecycle.RETRYING)
    }

    private fun harness(
        scheduling: RealtimeSchedulingConfig,
        cloudlet: Cloudlet,
        clock: Double = 0.0,
    ): InterruptionHarness {
        val harness = InterruptionHarness(cloudlet)
        val state = RealtimeTaskInterruptionState(harness.arrival, RealtimeReservationState(), harness.metrics)
        harness.arrival.recordArrival(cloudlet)
        harness.runtimeToken = harness.arrival.issueRuntimeToken(cloudlet)
        harness.controller =
            RealtimeTaskInterruptionController(
                scheduling,
                state,
                RealtimeTaskInterruptionServices(
                    failure = RealtimeFailureController(scheduling) { _, _, _ -> 0.0 },
                    timeout = RealtimeTimeoutController(scheduling),
                    recovery =
                        RealtimeCloudletRecoveryEstimator(
                            scheduling,
                            { clock },
                            { listOf(VmSimple(1000.0, 1)) },
                        ),
                    updateMetadata = { _, transform -> harness.metadata = transform(harness.metadata) },
                ),
            )
        return harness
    }

    private fun cloudlet(): Cloudlet = CloudletSimple(1000, 1).apply { setId(42) }

    private class InterruptionHarness(
        val cloudlet: Cloudlet,
        val arrival: RealtimeArrivalState = RealtimeArrivalState(),
        val metrics: RealtimeBrokerMetrics = RealtimeBrokerMetrics(),
        var runtimeToken: Int = 0,
        var metadata: RealtimeTaskRecord =
            RealtimeTaskRecord(
                cloudletId = cloudlet.id,
                originalArrivalTime = 0.0,
                attempt = 0,
                priority = 0,
                deadline = null,
            ),
    ) {
        lateinit var controller: RealtimeTaskInterruptionController
    }
}
