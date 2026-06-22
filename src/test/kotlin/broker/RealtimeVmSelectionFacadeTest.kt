package broker

import config.RealtimeSchedulingConfig
import org.assertj.core.api.Assertions.assertThat
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.cloudlets.CloudletSimple
import org.cloudsimplus.core.CloudSimPlus
import org.cloudsimplus.vms.Vm
import org.cloudsimplus.vms.VmSimple
import org.junit.jupiter.api.Test
import scheduler.RealtimeScheduler
import scheduler.RealtimeSchedulingContext

class RealtimeVmSelectionFacadeTest {
    @Test
    fun `dynamic selection bounds scheduler result and exposes active vm indexes`() {
        val vms = createVms()
        val broker = broker(vms, fixedScheduler(99), RealtimeSchedulingConfig())
        val facade = broker.selectionFacade()
        val incoming = cloudlet(1)
        val active = cloudlet(2).also { it.setVm(vms[1]) }

        assertThat(facade.selectVm(incoming, emptyList(), 0.0)?.first).isEqualTo(1)
        assertThat(facade.activeVmIndexes(listOf(active))).containsExactly(1)
        assertThat(facade.tryPreemptFor(incoming, listOf(active)).applied).isFalse()
        assertThat(broker.getCandidateScoreRecords()).hasSize(2)
        assertThat(broker.getCandidateScoreRecords().single { it.selected }.candidateVmIndex).isEqualTo(1)
        assertThat(broker.getAverageRealtimeScore()).isGreaterThan(0.0)
    }

    @Test
    fun `static selection uses preview and deterministic decision jitter`() {
        val vms = createVms()
        val config = RealtimeSchedulingConfig(strategy = "static", decisionDelay = 2.0, decisionJitter = 1.0)
        val broker = broker(vms, fixedScheduler(0), config)
        val facade = broker.selectionFacade()
        val cloudlet = cloudlet(3)

        assertThat(facade.staticPreviewSelection(cloudlet, emptyList())).isZero()
        assertThat(facade.selectVm(cloudlet, emptyList(), 0.0)?.first).isZero()
        assertThat(facade.decisionDelay(cloudlet)).isGreaterThanOrEqualTo(2.0).isLessThan(3.0)
        assertThat(facade.decisionDelay(cloudlet)).isEqualTo(facade.decisionDelay(cloudlet))
    }

    @Test
    fun `capacity exhaustion rejects selection and reports capacity reason`() {
        val vm = VmSimple(1_000.0, 1)
        val broker =
            broker(
                listOf(vm),
                fixedScheduler(0),
                RealtimeSchedulingConfig(vmQueueCapacity = 1),
            )
        val facade = broker.selectionFacade()
        val active = cloudlet(4).also { it.setVm(vm) }
        val incoming = cloudlet(5)

        assertThat(facade.selectVm(incoming, listOf(active), 0.0)).isNull()
        assertThat(facade.latestRejectionReason(incoming, listOf(active), 0.0))
            .isEqualTo(RealtimeRejectReason.CAPACITY)
    }

    @Test
    fun `resource exhaustion rejects selection and reports resource reason`() {
        val vm = VmSimple(1_000.0, 1)
        val broker =
            broker(
                listOf(vm),
                fixedScheduler(0),
                RealtimeSchedulingConfig(
                    resourceModelEnabled = true,
                    ioWeight = 20_000.0,
                    ramWeight = 1.0,
                    bwWeight = 1.0,
                ),
            )
        val facade = broker.selectionFacade()
        val incoming = cloudlet(6)

        assertThat(facade.selectVm(incoming, emptyList(), 0.0)).isNull()
        assertThat(facade.latestRejectionReason(incoming, emptyList(), 0.0))
            .isEqualTo(RealtimeRejectReason.RESOURCE)
    }

    @Test
    fun `zero jitter returns configured decision delay`() {
        val broker = broker(createVms(), fixedScheduler(0), RealtimeSchedulingConfig(decisionDelay = 1.25))

        assertThat(broker.selectionFacade().decisionDelay(cloudlet(7))).isEqualTo(1.25)
    }

    private fun broker(
        vms: List<Vm>,
        scheduler: RealtimeScheduler,
        config: RealtimeSchedulingConfig,
    ): RealtimeBroker = RealtimeBroker(CloudSimPlus(), scheduler, vms, config)

    private fun fixedScheduler(index: Int): RealtimeScheduler =
        object : RealtimeScheduler {
            override fun scheduleOnArrival(context: RealtimeSchedulingContext): Int = index
        }

    private fun createVms(): List<Vm> =
        listOf(
            VmSimple(1_000.0, 1).also { it.setId(10) },
            VmSimple(2_000.0, 1).also { it.setId(11) },
        )

    private fun cloudlet(id: Long): Cloudlet = CloudletSimple(1_000, 1).also { it.setId(id) }

    private fun RealtimeBroker.selectionFacade(): RealtimeVmSelectionFacade =
        javaClass
            .getDeclaredField("vmSelectionFacade")
            .apply { isAccessible = true }
            .get(this) as RealtimeVmSelectionFacade
}
