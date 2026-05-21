package broker

import config.RealtimeSchedulingConfig
import datacenter.DatacenterCreator
import datacenter.DatacenterType
import org.assertj.core.api.Assertions.assertThat
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.cloudlets.CloudletSimple
import org.cloudsimplus.core.CloudSimPlus
import org.cloudsimplus.utilizationmodels.UtilizationModelFull
import org.cloudsimplus.vms.Vm
import org.cloudsimplus.vms.VmSimple
import org.junit.jupiter.api.Test
import scheduler.RealtimeMinLoadScheduler

class RealtimeBrokerCloudSemanticsTest {

    @Test
    fun `decision delay postpones submission and is counted`() {
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "test-dc", DatacenterType.LOW)
        val vm = createVm()
        val broker = RealtimeBroker(
            simulation,
            RealtimeMinLoadScheduler(listOf(vm)),
            listOf(vm),
            RealtimeSchedulingConfig(decisionDelay = 2.0)
        )
        broker.submitVmList(listOf(vm))
        broker.submitCloudletListRealtime(listOf(createCloudlet()))

        simulation.start()

        assertThat(broker.getSubmittedCount()).isEqualTo(1)
        assertThat(broker.getAverageDecisionDelay()).isEqualTo(2.0)
    }

    @Test
    fun `failed attempts retry until retry limit then become permanent failures`() {
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "test-dc", DatacenterType.LOW)
        val vm = createVm()
        val broker = RealtimeBroker(
            simulation,
            RealtimeMinLoadScheduler(listOf(vm)),
            listOf(vm),
            RealtimeSchedulingConfig(
                failureRate = 1.0,
                retryLimit = 2,
                retryDelay = 0.1,
                retryBackoffMultiplier = 1.0
            )
        )
        broker.submitVmList(listOf(vm))
        broker.submitCloudletListRealtime(listOf(createCloudlet()))

        simulation.start()

        assertThat(broker.getSubmittedCount()).isEqualTo(0)
        assertThat(broker.getRetryCount()).isEqualTo(2)
        assertThat(broker.getPermanentFailedCount()).isEqualTo(1)
    }

    @Test
    fun `rejected tasks do not retry`() {
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "test-dc", DatacenterType.LOW)
        val vm = createVm()
        val broker = RealtimeBroker(
            simulation,
            RealtimeMinLoadScheduler(listOf(vm)),
            listOf(vm),
            RealtimeSchedulingConfig(maxQueueSize = 1, failureRate = 1.0, retryLimit = 2)
        )
        broker.submitVmList(listOf(vm))
        broker.submitCloudletListRealtime(listOf(createCloudlet(0), createCloudlet(1)))

        simulation.start()

        assertThat(broker.getRejectedCount()).isGreaterThanOrEqualTo(1)
        assertThat(broker.getRetryCount()).isLessThanOrEqualTo(2)
    }

    private fun createVm(): Vm =
        VmSimple(1000.0, 1)
            .setRam(1024)
            .setBw(1000)
            .setSize(10000)

    private fun createCloudlet(id: Int = 0): Cloudlet {
        val utilizationModel = UtilizationModelFull()
        val cloudlet = CloudletSimple(1000, 1)
        cloudlet.setId(id.toLong())
        cloudlet.setFileSize(100)
        cloudlet.setOutputSize(100)
        cloudlet.setUtilizationModelCpu(utilizationModel)
        cloudlet.setUtilizationModelRam(utilizationModel)
        cloudlet.setUtilizationModelBw(utilizationModel)
        return cloudlet
    }
}
