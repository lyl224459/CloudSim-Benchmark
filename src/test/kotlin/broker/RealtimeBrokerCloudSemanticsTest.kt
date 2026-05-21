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
import scheduler.RealtimeSchedulingContext
import scheduler.RealtimeMinLoadScheduler
import scheduler.RealtimeSchedulerBase

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

    @Test
    fun `vm queue capacity rejects excess tasks without retry`() {
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "test-dc", DatacenterType.LOW)
        val vm = createVm()
        val broker = RealtimeBroker(
            simulation,
            RealtimeMinLoadScheduler(listOf(vm)),
            listOf(vm),
            RealtimeSchedulingConfig(vmQueueCapacity = 1, decisionDelay = 10.0, failureRate = 0.0, retryLimit = 3)
        )
        broker.submitVmList(listOf(vm))
        broker.submitCloudletListRealtime(listOf(createCloudlet(0, length = 20_000), createCloudlet(1, length = 20_000)))

        simulation.start()

        assertThat(broker.getCapacityRejectedCount()).isEqualTo(1)
        assertThat(broker.getRetryCount()).isEqualTo(0)
    }

    @Test
    fun `deadline factor counts completed sla violations`() {
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "test-dc", DatacenterType.LOW)
        val vm = createVm()
        val cloudlet = createCloudlet(length = 20_000)
        val broker = RealtimeBroker(
            simulation,
            RealtimeMinLoadScheduler(listOf(vm)),
            listOf(vm),
            RealtimeSchedulingConfig(deadlineFactor = 0.1)
        )
        broker.submitVmList(listOf(vm))
        broker.submitCloudletListRealtime(listOf(cloudlet))

        simulation.start()

        assertThat(broker.getSlaViolationCount(listOf(cloudlet))).isEqualTo(1)
    }

    @Test
    fun `priority queue policy orders same-time arrivals before scheduling`() {
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "test-dc", DatacenterType.LOW)
        val vm = createVm()
        val observedPriorities = mutableListOf<Int>()
        val scheduler = object : RealtimeSchedulerBase(listOf(vm)) {
            override fun scheduleOnArrival(context: RealtimeSchedulingContext): Int {
                observedPriorities.add(context.taskMetadata.priority)
                return 0
            }
        }
        val broker = RealtimeBroker(
            simulation,
            scheduler,
            listOf(vm),
            RealtimeSchedulingConfig(queuePolicy = "priority", priorityLevels = 3, highPriorityRatio = 1.0)
        )
        broker.submitVmList(listOf(vm))
        broker.submitCloudletListRealtime(listOf(createCloudlet(10), createCloudlet(1), createCloudlet(3)))

        simulation.start()

        assertThat(observedPriorities).isSorted
    }

    @Test
    fun `overload failure pressure can fail attempts without base failure rate`() {
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "test-dc", DatacenterType.LOW)
        val vm = createVm()
        val broker = RealtimeBroker(
            simulation,
            RealtimeMinLoadScheduler(listOf(vm)),
            listOf(vm),
            RealtimeSchedulingConfig(vmQueueCapacity = 3, decisionDelay = 10.0, overloadFailureMultiplier = 1.0)
        )
        broker.submitVmList(listOf(vm))
        broker.submitCloudletListRealtime(
            listOf(
                createCloudlet(0, length = 20_000),
                createCloudlet(1, length = 20_000),
                createCloudlet(2, length = 20_000)
            )
        )

        simulation.start()

        assertThat(broker.getPermanentFailedCount()).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `autoscaling creates dynamic vm after queue pressure and records cost`() {
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "test-dc", DatacenterType.LOW)
        val vm = createVm()
        val broker = RealtimeBroker(
            simulation,
            RealtimeMinLoadScheduler(listOf(vm)),
            listOf(vm),
            RealtimeSchedulingConfig(
                autoscalingEnabled = true,
                scaleOutQueueThreshold = 1,
                maxDynamicVms = 1,
                vmColdStartDelay = 0.0,
                scaleOutCost = 0.75,
                vmQueueCapacity = 3
            )
        )
        broker.submitVmList(listOf(vm))
        broker.submitCloudletListRealtime(listOf(createCloudlet(0), createCloudlet(1)))

        simulation.start()

        assertThat(broker.getScaleOutCount()).isEqualTo(1)
        assertThat(broker.getActiveVmPeak()).isGreaterThanOrEqualTo(2)
        assertThat(broker.getAutoscalingCost()).isEqualTo(0.75)
    }

    @Test
    fun `resource model rejects tasks when vm resources are exhausted`() {
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "test-dc", DatacenterType.LOW)
        val vm = createVm()
        val broker = RealtimeBroker(
            simulation,
            RealtimeMinLoadScheduler(listOf(vm)),
            listOf(vm),
            RealtimeSchedulingConfig(
                resourceModelEnabled = true,
                ioWeight = 20_000.0,
                ramWeight = 1.0,
                bwWeight = 1.0
            )
        )
        broker.submitVmList(listOf(vm))
        broker.submitCloudletListRealtime(listOf(createCloudlet(0, length = 1000)))

        simulation.start()

        assertThat(broker.getResourceRejectedCount()).isEqualTo(1)
        assertThat(broker.getSubmittedCount()).isEqualTo(0)
    }

    @Test
    fun `runtime failure retries running tasks`() {
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "test-dc", DatacenterType.LOW)
        val vm = createVm()
        val broker = RealtimeBroker(
            simulation,
            RealtimeMinLoadScheduler(listOf(vm)),
            listOf(vm),
            RealtimeSchedulingConfig(
                runtimeFailureRate = 1.0,
                retryLimit = 1,
                retryDelay = 0.1,
                checkpointInterval = 0.1
            )
        )
        broker.submitVmList(listOf(vm))
        broker.submitCloudletListRealtime(listOf(createCloudlet(0, length = 20_000)))

        simulation.start()

        assertThat(broker.getRuntimeFailureCount()).isGreaterThanOrEqualTo(1)
        assertThat(broker.getRetryCount()).isEqualTo(1)
    }

    @Test
    fun `timeout retry action cancels current attempt and retries`() {
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "test-dc", DatacenterType.LOW)
        val vm = createVm()
        val broker = RealtimeBroker(
            simulation,
            RealtimeMinLoadScheduler(listOf(vm)),
            listOf(vm),
            RealtimeSchedulingConfig(
                taskTimeout = 0.2,
                timeoutAction = "retry",
                retryLimit = 1,
                retryDelay = 0.1
            )
        )
        broker.submitVmList(listOf(vm))
        broker.submitCloudletListRealtime(listOf(createCloudlet(0, length = 20_000)))

        simulation.start()

        assertThat(broker.getTimeoutCancelledCount()).isGreaterThanOrEqualTo(1)
        assertThat(broker.getRetryCount()).isEqualTo(1)
    }

    private fun createVm(): Vm =
        VmSimple(1000.0, 1)
            .setRam(1024)
            .setBw(1000)
            .setSize(10000)

    private fun createCloudlet(id: Int = 0, length: Long = 1000): Cloudlet {
        val utilizationModel = UtilizationModelFull()
        val cloudlet = CloudletSimple(length, 1)
        cloudlet.setId(id.toLong())
        cloudlet.setFileSize(100)
        cloudlet.setOutputSize(100)
        cloudlet.setSubmissionDelay(0.1)
        cloudlet.setUtilizationModelCpu(utilizationModel)
        cloudlet.setUtilizationModelRam(utilizationModel)
        cloudlet.setUtilizationModelBw(utilizationModel)
        return cloudlet
    }
}
