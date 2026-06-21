package broker

import config.RealtimeSchedulingConfig
import org.assertj.core.api.Assertions.assertThat
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.cloudlets.CloudletSimple
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared
import org.cloudsimplus.utilizationmodels.UtilizationModelFull
import org.cloudsimplus.vms.Vm
import org.cloudsimplus.vms.VmSimple
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import scheduler.RealtimeTaskLifecycle
import scheduler.RealtimeTaskRecord
import scheduler.RealtimeTopologyModel
import scheduler.RealtimeVmLifecycleManager

private typealias ReadDeps = RealtimeBrokerReadDependencies

class RealtimeBrokerReadViewsTest {
    @Test
    fun `active cloudlets filter failed status and prune terminal reservations`() {
        val pending = cloudlet(id = 1)
        val waiting = cloudlet(id = 2)
        val failed = cloudlet(id = 3).also { it.setStatus(Cloudlet.Status.FAILED) }
        val rejected = cloudlet(id = 4)
        val deps = deps()
        deps.arrivalState.addPending(pending)
        deps.arrivalState.addWaiting(waiting)
        deps.arrivalState.addPending(failed)
        deps.reservationState.reserve(rejected, vmIndex = 1)
        deps.reservationState.reserve(waiting, vmIndex = 0)
        deps.lifecycleStore.put(record(rejected.id, lifecycle = RealtimeTaskLifecycle.REJECTED))
        deps.lifecycleStore.put(record(waiting.id, lifecycle = RealtimeTaskLifecycle.RUNNING))

        val active = RealtimeBrokerTaskReadView(deps).activeCloudlets()

        assertThat(active.map(Cloudlet::getId)).containsExactly(1L, 2L)
        assertThat(deps.reservationState.rawReservations()).containsExactlyEntriesOf(mapOf(2L to 0))
    }

    @Test
    fun `metadata and arrival lookups fall back for unknown tasks`() {
        val deps = deps()
        val cloudlet = cloudlet(id = 7, submissionDelay = 4.5)

        val view = RealtimeBrokerTaskReadView(deps)

        assertThat(view.taskMetadata(cloudlet)).isNull()
        assertThat(view.arrivalTime(cloudlet)).isEqualTo(4.5)
    }

    @Test
    fun `timeout and sla counters honor disabled settings and deadlines`() {
        val deps = deps(RealtimeSchedulingConfig(deadlineFactor = 1.0))
        val waiting = cloudlet(id = 8)
        val success = completedCloudlet(id = 9, finishTime = 12.0)
        deps.arrivalState.addWaiting(waiting)
        deps.lifecycleStore.put(record(success.id, deadline = 10.0, lifecycle = RealtimeTaskLifecycle.COMPLETED))
        val view = RealtimeBrokerTaskReadView(deps)

        assertThat(view.timeoutCount(timeoutSeconds = 0.0)).isZero()
        assertThat(view.timeoutCount(timeoutSeconds = 5.0)).isEqualTo(1)
        assertThat(view.slaViolationCount(listOf(success))).isEqualTo(1)

        val disabled = RealtimeBrokerTaskReadView(deps())
        assertThat(disabled.slaViolationCount(listOf(success))).isZero()
    }

    @Test
    fun `topology metrics use metadata reservation and cloudlet vm fallbacks`() {
        val scheduling =
            RealtimeSchedulingConfig(
                topologyEnabled = true,
                regionCount = 2,
                racksPerRegion = 1,
                hostsPerRack = 1,
                localRegion = 0,
                crossRegionLatency = 2.0,
                crossRegionCost = 3.0,
            )
        val deps = deps(scheduling)
        val metadataAssigned = cloudlet(id = 10)
        val reserved = cloudlet(id = 11)
        val vmAssigned = cloudlet(id = 12).also { it.setVm(vm(id = 1)) }
        deps.lifecycleStore.put(record(metadataAssigned.id, assignedVmIndex = 1))
        deps.reservationState.reserve(reserved, vmIndex = 1)

        val metrics =
            RealtimeBrokerTenantTopologyView(deps)
                .topologyMetrics(listOf(metadataAssigned, reserved, vmAssigned))

        assertThat(metrics.crossRegionAssignmentCount).isEqualTo(3)
        assertThat(metrics.crossRackAssignmentCount).isEqualTo(3)
        assertThat(metrics.averageTopologyLatency).isEqualTo(2.0)
        assertThat(metrics.topologyCost).isEqualTo(9.0)
    }

    private fun deps(scheduling: RealtimeSchedulingConfig = RealtimeSchedulingConfig()): ReadDeps {
        val vms = listOf(vm(id = 0), vm(id = 1))
        return RealtimeBrokerReadDependencies(
            scheduling = scheduling,
            arrivalState = RealtimeArrivalState(),
            lifecycleStore = RealtimeTaskLifecycleStore(),
            reservationState = RealtimeReservationState(),
            metrics = RealtimeBrokerMetrics(),
            vmLifecycleManager =
                RealtimeVmLifecycleManager(
                    initialVms = vms,
                    scheduling = scheduling,
                    topologyModel = RealtimeTopologyModel.fromConfig(scheduling, initialVmCount = vms.size),
                ),
            tenantController = RealtimeTenantController(scheduling),
            topologyModel = RealtimeTopologyModel.fromConfig(scheduling, initialVmCount = vms.size),
        )
    }

    private fun record(
        cloudletId: Long,
        assignedVmIndex: Int? = null,
        deadline: Double? = null,
        lifecycle: RealtimeTaskLifecycle = RealtimeTaskLifecycle.RUNNING,
    ): RealtimeTaskRecord =
        RealtimeTaskRecord(
            cloudletId = cloudletId,
            originalArrivalTime = 0.0,
            assignedVmIndex = assignedVmIndex,
            deadline = deadline,
            lifecycle = lifecycle,
        )

    private fun cloudlet(
        id: Int,
        submissionDelay: Double = 0.0,
    ): Cloudlet =
        CloudletSimple(1000, 1).apply {
            setId(id.toLong())
            setFileSize(100)
            setOutputSize(100)
            setSubmissionDelay(submissionDelay)
            setUtilizationModel(UtilizationModelFull())
        }

    private fun completedCloudlet(
        id: Int,
        finishTime: Double,
    ): Cloudlet =
        mock {
            on { this.id } doReturn id.toLong()
            on { this.status } doReturn Cloudlet.Status.SUCCESS
            on { this.finishTime } doReturn finishTime
        }

    private fun vm(id: Int): Vm =
        VmSimple(1000.0, 1)
            .setRam(1024)
            .setBw(1000)
            .setSize(10_000)
            .setCloudletScheduler(CloudletSchedulerSpaceShared())
            .also { it.setId(id.toLong()) }
}
