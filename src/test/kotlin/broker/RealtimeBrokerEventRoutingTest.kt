package broker

import org.assertj.core.api.Assertions.assertThat
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.cloudlets.CloudletSimple
import org.cloudsimplus.core.SimEntity
import org.cloudsimplus.core.Simulation
import org.cloudsimplus.core.events.SimEvent
import org.cloudsimplus.listeners.EventInfo
import org.cloudsimplus.listeners.EventListener
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared
import org.cloudsimplus.utilizationmodels.UtilizationModelFull
import org.cloudsimplus.vms.Vm
import org.cloudsimplus.vms.VmSimple
import org.junit.jupiter.api.Test

class RealtimeBrokerEventRoutingTest {
    @Test
    fun `router maps CloudSim events to typed broker events`() {
        val router = RealtimeBrokerEventRouter()
        val cloudlet = createCloudlet(1)
        val submission =
            RealtimePendingSubmission(
                cloudlet,
                vmIndex = 0,
                decisionDelay = 0.25,
                failurePressure = 0.1,
                decisionToken = 3,
            )
        val payload = RealtimeCloudletEventPayload(cloudlet, attempt = 2, runtimeToken = 4)

        val routed =
            listOf(
                router.route(simEvent(1.0, RealtimeBrokerEventTags.ARRIVAL, cloudlet)),
                router.route(simEvent(2.0, RealtimeBrokerEventTags.SUBMIT, submission)),
                router.route(simEvent(3.0, RealtimeBrokerEventTags.TIMEOUT, payload)),
                router.route(simEvent(4.0, RealtimeBrokerEventTags.RUNTIME_FAILURE, payload)),
                router.route(simEvent(5.0, RealtimeBrokerEventTags.AUTOSCALE_TICK, Unit)),
                router.route(simEvent(6.0, RealtimeBrokerEventTags.RESCHEDULE_TICK, Unit)),
            )

        assertThat(routed[0]).isEqualTo(RealtimeBrokerEvent.Arrival(cloudlet, time = 1.0))
        assertThat(routed[1]).isEqualTo(RealtimeBrokerEvent.Submit(submission))
        assertThat(routed[2]).isEqualTo(RealtimeBrokerEvent.Timeout(payload))
        assertThat(routed[3]).isEqualTo(RealtimeBrokerEvent.RuntimeFailure(payload))
        assertThat(routed[4]).isEqualTo(RealtimeBrokerEvent.AutoscaleTick(time = 5.0))
        assertThat(routed[5]).isEqualTo(RealtimeBrokerEvent.RescheduleTick(time = 6.0))
    }

    @Test
    fun `command executor applies scheduling and vm side effects`() {
        val cloudlet = createCloudlet(7)
        val submission =
            RealtimePendingSubmission(
                cloudlet,
                vmIndex = 0,
                decisionDelay = 0.5,
                failurePressure = 0.0,
                decisionToken = 1,
            )
        val vm = createVm(3)
        val scheduled = mutableListOf<ScheduledCommand>()
        val submittedVms = mutableListOf<Pair<List<Vm>, Double>>()
        val executor =
            RealtimeBrokerCommandExecutor(
                schedule = { delay, tag, data -> scheduled += ScheduledCommand(delay, tag, data) },
                submitVms = { vms, delay -> submittedVms += vms to delay },
            )

        executor.applyAll(
            listOf(
                RealtimeBrokerCommand.ScheduleArrival(1.0, cloudlet),
                RealtimeBrokerCommand.ScheduleSubmit(2.0, submission),
                RealtimeBrokerCommand.ScheduleAutoscaleTick(3.0),
                RealtimeBrokerCommand.ScheduleRescheduleTick(4.0),
                RealtimeBrokerCommand.SubmitVms(listOf(vm), delay = 0.75),
            ),
        )

        assertThat(scheduled).containsExactly(
            ScheduledCommand(1.0, RealtimeBrokerEventTags.ARRIVAL, cloudlet),
            ScheduledCommand(2.0, RealtimeBrokerEventTags.SUBMIT, submission),
            ScheduledCommand(3.0, RealtimeBrokerEventTags.AUTOSCALE_TICK, Unit),
            ScheduledCommand(4.0, RealtimeBrokerEventTags.RESCHEDULE_TICK, Unit),
        )
        assertThat(submittedVms).containsExactly(listOf(vm) to 0.75)
    }

    private data class ScheduledCommand(
        val delay: Double,
        val tag: Int,
        val data: Any,
    )

    private fun simEvent(
        time: Double,
        tag: Int,
        data: Any,
    ): SimEvent = TestSimEvent(time = time, tag = tag, data = data)

    private data class TestSimEvent(
        private val time: Double,
        private val tag: Int,
        private val data: Any,
    ) : SimEvent {
        override fun getTime(): Double = time

        override fun getType(): SimEvent.Type = SimEvent.Type.SEND

        override fun getDestination(): SimEntity = SimEntity.NULL

        override fun getSource(): SimEntity = SimEntity.NULL

        override fun getEndWaitingTime(): Double = 0.0

        override fun getTag(): Int = tag

        override fun getData(): Any = data

        override fun setSource(source: SimEntity): SimEvent = this

        override fun setDestination(destination: SimEntity): SimEvent = this

        override fun getSerial(): Long = 0L

        override fun setSerial(serial: Long): SimEvent = this

        override fun getSimulation(): Simulation = Simulation.NULL

        override fun setSimulation(simulation: Simulation): SimEvent = this

        override fun compareTo(other: SimEvent): Int = time.compareTo(other.time)

        @Suppress("UNCHECKED_CAST")
        override fun <T : EventInfo> getListener(): EventListener<T> = EventListener.NULL as EventListener<T>
    }

    private fun createCloudlet(id: Int): Cloudlet {
        val utilizationModel = UtilizationModelFull()
        return CloudletSimple(1000, 1).apply {
            setId(id.toLong())
            setUtilizationModelCpu(utilizationModel)
            setUtilizationModelRam(utilizationModel)
            setUtilizationModelBw(utilizationModel)
        }
    }

    private fun createVm(id: Int): Vm =
        VmSimple(1000.0, 1)
            .also { it.setId(id.toLong()) }
            .setRam(1024)
            .setBw(1000)
            .setSize(10_000)
            .setCloudletScheduler(CloudletSchedulerSpaceShared())
}
