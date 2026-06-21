package broker

import config.RealtimeSchedulingConfig
import datacenter.DatacenterCreator
import datacenter.DatacenterType
import datacenter.RealtimeCloudletSpec
import datacenter.RealtimeTraceMetadata
import org.assertj.core.api.Assertions.assertThat
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.cloudlets.CloudletSimple
import org.cloudsimplus.core.CloudSimPlus
import org.cloudsimplus.utilizationmodels.UtilizationModelFull
import org.cloudsimplus.vms.Vm
import org.cloudsimplus.vms.VmSimple
import org.junit.jupiter.api.Test
import scheduler.RealtimeMinLoadScheduler
import scheduler.RealtimeSchedulerBase
import scheduler.RealtimeSchedulingContext

class RealtimeBrokerCloudSemanticsTest {
    @Test
    fun `decision delay postpones submission and is counted`() {
        val fixture = brokerFixture(RealtimeSchedulingConfig(decisionDelay = 2.0))
        fixture.broker.submitCloudletListRealtime(listOf(createCloudlet()))

        fixture.simulation.start()

        assertThat(fixture.broker.getSubmittedCount()).isEqualTo(1)
        assertThat(fixture.broker.getAverageDecisionDelay()).isEqualTo(2.0)
    }

    @Test
    fun `failed attempts retry until retry limit then become permanent failures`() {
        val fixture =
            brokerFixture(
                RealtimeSchedulingConfig(
                    failureRate = 1.0,
                    retryLimit = 2,
                    retryDelay = 0.1,
                    retryBackoffMultiplier = 1.0,
                ),
            )
        fixture.broker.submitCloudletListRealtime(listOf(createCloudlet()))

        fixture.simulation.start()

        assertThat(fixture.broker.getSubmittedCount()).isEqualTo(0)
        assertThat(fixture.broker.getRetryCount()).isEqualTo(2)
        assertThat(fixture.broker.getPermanentFailedCount()).isEqualTo(1)
    }

    @Test
    fun `rejected tasks do not retry`() {
        val fixture = brokerFixture(RealtimeSchedulingConfig(maxQueueSize = 1, failureRate = 1.0, retryLimit = 2))
        fixture.broker.submitCloudletListRealtime(listOf(createCloudlet(0), createCloudlet(1)))

        fixture.simulation.start()

        assertThat(fixture.broker.getRejectedCount()).isGreaterThanOrEqualTo(1)
        assertThat(fixture.broker.getRetryCount()).isLessThanOrEqualTo(2)
    }

    @Test
    fun `vm queue capacity rejects excess tasks without retry`() {
        val fixture =
            brokerFixture(
                RealtimeSchedulingConfig(vmQueueCapacity = 1, decisionDelay = 10.0, failureRate = 0.0, retryLimit = 3),
            )
        fixture.broker.submitCloudletListRealtime(
            listOf(
                createCloudlet(0, length = 20_000),
                createCloudlet(1, length = 20_000),
            ),
        )

        fixture.simulation.start()

        assertThat(fixture.broker.getCapacityRejectedCount()).isEqualTo(1)
        assertThat(fixture.broker.getRetryCount()).isEqualTo(0)
    }

    @Test
    fun `deadline factor counts completed sla violations`() {
        val fixture = brokerFixture(RealtimeSchedulingConfig(deadlineFactor = 0.1))
        val cloudlet = createCloudlet(length = 20_000)
        fixture.broker.submitCloudletListRealtime(listOf(cloudlet))

        fixture.simulation.start()

        assertThat(fixture.broker.getSlaViolationCount(listOf(cloudlet))).isEqualTo(1)
    }

    @Test
    fun `priority queue policy orders same-time arrivals before scheduling`() {
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "test-dc", DatacenterType.LOW)
        val vm = createVm()
        val observedPriorities = mutableListOf<Int>()
        val scheduler =
            object : RealtimeSchedulerBase(listOf(vm)) {
                override fun scheduleOnArrival(context: RealtimeSchedulingContext): Int {
                    observedPriorities.add(context.taskMetadata.priority)
                    return 0
                }
            }
        val broker =
            RealtimeBroker(
                simulation,
                scheduler,
                listOf(vm),
                RealtimeSchedulingConfig(queuePolicy = "priority", priorityLevels = 3, highPriorityRatio = 1.0),
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
        val broker =
            RealtimeBroker(
                simulation,
                RealtimeMinLoadScheduler(listOf(vm)),
                listOf(vm),
                RealtimeSchedulingConfig(vmQueueCapacity = 3, decisionDelay = 10.0, overloadFailureMultiplier = 1.0),
            )
        broker.submitVmList(listOf(vm))
        broker.submitCloudletListRealtime(
            listOf(
                createCloudlet(0, length = 20_000),
                createCloudlet(1, length = 20_000),
                createCloudlet(2, length = 20_000),
            ),
        )

        simulation.start()

        assertThat(broker.getPermanentFailedCount()).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `autoscaling creates dynamic vm after queue pressure and records cost`() {
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "test-dc", DatacenterType.LOW)
        val vm = createVm()
        val broker =
            RealtimeBroker(
                simulation,
                RealtimeMinLoadScheduler(listOf(vm)),
                listOf(vm),
                RealtimeSchedulingConfig(
                    autoscalingEnabled = true,
                    scaleOutQueueThreshold = 1,
                    maxDynamicVms = 1,
                    vmColdStartDelay = 0.0,
                    scaleOutCost = 0.75,
                    vmQueueCapacity = 3,
                ),
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
        val broker =
            RealtimeBroker(
                simulation,
                RealtimeMinLoadScheduler(listOf(vm)),
                listOf(vm),
                RealtimeSchedulingConfig(
                    resourceModelEnabled = true,
                    ioWeight = 20_000.0,
                    ramWeight = 1.0,
                    bwWeight = 1.0,
                ),
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
        val broker =
            RealtimeBroker(
                simulation,
                RealtimeMinLoadScheduler(listOf(vm)),
                listOf(vm),
                RealtimeSchedulingConfig(
                    runtimeFailureRate = 1.0,
                    retryLimit = 1,
                    retryDelay = 0.1,
                    checkpointInterval = 0.1,
                ),
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
        val broker =
            RealtimeBroker(
                simulation,
                RealtimeMinLoadScheduler(listOf(vm)),
                listOf(vm),
                RealtimeSchedulingConfig(
                    taskTimeout = 0.2,
                    timeoutAction = "retry",
                    retryLimit = 1,
                    retryDelay = 0.1,
                ),
            )
        broker.submitVmList(listOf(vm))
        broker.submitCloudletListRealtime(listOf(createCloudlet(0, length = 20_000)))

        simulation.start()

        assertThat(broker.getTimeoutCancelledCount()).isGreaterThanOrEqualTo(1)
        assertThat(broker.getRetryCount()).isEqualTo(1)
    }

    @Test
    fun `tenant quota rejects excess tenant work without retry`() {
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "test-dc", DatacenterType.LOW)
        val vm = createVm()
        val broker =
            RealtimeBroker(
                simulation,
                RealtimeMinLoadScheduler(listOf(vm)),
                listOf(vm),
                RealtimeSchedulingConfig(
                    multiTenantEnabled = true,
                    tenantCount = 1,
                    tenantQuota = listOf(1),
                    decisionDelay = 10.0,
                    retryLimit = 2,
                ),
            )
        broker.submitVmList(listOf(vm))
        broker.submitCloudletListRealtime(listOf(createCloudlet(0), createCloudlet(1)))

        simulation.start()

        assertThat(broker.getTenantQuotaRejectedCount()).isEqualTo(1)
        assertThat(broker.getRetryCount()).isEqualTo(0)
    }

    @Test
    fun `tenant fairness index is reported in bounded range`() {
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "test-dc", DatacenterType.LOW)
        val vm = createVm()
        val cloudlets = listOf(createCloudlet(0), createCloudlet(1), createCloudlet(2), createCloudlet(3))
        val broker =
            RealtimeBroker(
                simulation,
                RealtimeMinLoadScheduler(listOf(vm)),
                listOf(vm),
                RealtimeSchedulingConfig(
                    multiTenantEnabled = true,
                    tenantCount = 2,
                    tenantWeights = listOf(1.0, 2.0),
                    tenantFairnessPolicy = "weighted_fair",
                ),
            )
        broker.submitVmList(listOf(vm))
        broker.submitCloudletListRealtime(cloudlets)

        simulation.start()

        assertThat(broker.getTenantFairnessIndex(cloudlets)).isBetween(0.0, 1.0)
    }

    @Test
    fun `trace metadata overrides tenant priority deadline and resource request`() {
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "test-dc", DatacenterType.LOW)
        val vm = createVm()
        val cloudlet = createCloudlet(42, length = 1_000, submissionDelay = 0.1)
        val traceMetadata =
            RealtimeTraceMetadata(
                tenantKey = "trace-user",
                tenantId = 5,
                priority = 0,
                deadline = 3.5,
                requestedCpu = 0.5,
                requestedRam = 128.0,
                requestedBw = 64.0,
                requestedIo = 32.0,
                dataRegion = 1,
                inputDataSize = 2.5,
                imageId = "trace-image-a",
                imageSize = 3.0,
                retryHint = 1,
            )
        val broker =
            RealtimeBroker(
                simulation,
                RealtimeMinLoadScheduler(listOf(vm)),
                listOf(vm),
                RealtimeSchedulingConfig(
                    multiTenantEnabled = true,
                    tenantCount = 3,
                    priorityLevels = 3,
                    deadlineFactor = 10.0,
                    resourceModelEnabled = true,
                ),
            )
        broker.submitVmList(listOf(vm))
        broker.submitCloudletSpecsRealtime(listOf(RealtimeCloudletSpec(cloudlet, traceMetadata)))

        simulation.start()

        val metadata = broker.getTaskMetadata(cloudlet)
        assertThat(metadata?.tenantId?.value).isEqualTo(2)
        assertThat(metadata?.tenantKey).isEqualTo("trace-user")
        assertThat(metadata?.priority).isEqualTo(0)
        assertThat(metadata?.deadline).isEqualTo(3.5)
        assertThat(metadata?.requestedRam).isEqualTo(128.0)
        assertThat(metadata?.dataRegion?.value).isEqualTo(1)
        assertThat(metadata?.inputDataSizeGb).isEqualTo(2.5)
        assertThat(metadata?.imageId).isEqualTo("trace-image-a")
        assertThat(metadata?.imageSizeGb).isEqualTo(3.0)
        assertThat(metadata?.traceRetryHint).isEqualTo(1)
    }

    @Test
    fun `physical host capacity rejection is surfaced as resource rejection`() {
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "test-dc", DatacenterType.LOW)
        val vm = createVm()
        val cloudlet = createCloudlet(7, length = 1_000, submissionDelay = 0.1)
        val broker =
            RealtimeBroker(
                simulation,
                RealtimeMinLoadScheduler(listOf(vm)),
                listOf(vm),
                RealtimeSchedulingConfig(
                    physicalTopologyEnabled = true,
                    regionCount = 1,
                    racksPerRegion = 1,
                    hostCountPerRack = 1,
                    hostCpuCapacity = 1.0,
                ),
            )
        broker.submitVmList(listOf(vm))
        broker.submitCloudletSpecsRealtime(
            listOf(RealtimeCloudletSpec(cloudlet, RealtimeTraceMetadata(requestedCpu = 2.0))),
        )

        simulation.start()

        assertThat(broker.getResourceRejectedCount()).isEqualTo(1)
        assertThat(broker.getCapacityRejectedCount()).isEqualTo(0)
    }

    @Test
    fun `tenant budget rejection is reported separately from quota`() {
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "test-dc", DatacenterType.LOW)
        val vm = createVm()
        val first = createCloudlet(0, length = 20_000, submissionDelay = 0.1)
        val second = createCloudlet(1, length = 20_000, submissionDelay = 0.2)
        val broker =
            RealtimeBroker(
                simulation,
                RealtimeMinLoadScheduler(listOf(vm)),
                listOf(vm),
                RealtimeSchedulingConfig(
                    multiTenantEnabled = true,
                    tenantCount = 1,
                    tenantCostBudget = listOf(1.5),
                    decisionDelay = 10.0,
                ),
            )
        broker.submitVmList(listOf(vm))
        broker.submitCloudletSpecsRealtime(
            listOf(
                RealtimeCloudletSpec(first, RealtimeTraceMetadata(tenantId = 0, requestedCpu = 1.0)),
                RealtimeCloudletSpec(second, RealtimeTraceMetadata(tenantId = 0, requestedCpu = 1.0)),
            ),
        )

        simulation.start()

        assertThat(broker.getTenantBudgetRejectedCount()).isEqualTo(1)
        assertThat(broker.getTenantQuotaRejectedCount()).isEqualTo(0)
    }

    @Test
    fun `preemption disabled rejects when vm queue is full`() {
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "test-dc", DatacenterType.LOW)
        val vm = createVm()
        val broker =
            RealtimeBroker(
                simulation,
                RealtimeMinLoadScheduler(listOf(vm)),
                listOf(vm),
                RealtimeSchedulingConfig(
                    vmQueueCapacity = 1,
                    decisionDelay = 10.0,
                    priorityLevels = 2,
                    highPriorityRatio = 1.0,
                    preemptionEnabled = false,
                    retryLimit = 1,
                ),
            )
        broker.submitVmList(listOf(vm))
        broker.submitCloudletListRealtime(
            listOf(
                createCloudlet(0, length = 20_000, submissionDelay = 0.1),
                createCloudlet(1, length = 20_000, submissionDelay = 0.2),
            ),
        )

        simulation.start()

        assertThat(broker.getPreemptedCount()).isEqualTo(0)
        assertThat(broker.getCapacityRejectedCount()).isEqualTo(1)
    }

    @Test
    fun `preemption allows high priority task to displace lower priority task`() {
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "test-dc", DatacenterType.LOW)
        val vm = createVm()
        val broker =
            RealtimeBroker(
                simulation,
                RealtimeMinLoadScheduler(listOf(vm)),
                listOf(vm),
                RealtimeSchedulingConfig(
                    vmQueueCapacity = 1,
                    decisionDelay = 10.0,
                    priorityLevels = 2,
                    highPriorityRatio = 1.0,
                    preemptionEnabled = true,
                    preemptionMinPriorityGap = 1,
                    preemptionMaxPerTask = 1,
                    preemptionDelay = 0.2,
                    preemptionPenalty = 0.5,
                    retryLimit = 2,
                    checkpointInterval = 1.0,
                ),
            )
        broker.submitVmList(listOf(vm))
        broker.submitCloudletListRealtime(
            listOf(
                createCloudlet(0, length = 20_000, submissionDelay = 0.1),
                createCloudlet(1, length = 20_000, submissionDelay = 0.2),
            ),
        )

        simulation.start()

        assertThat(broker.getPreemptedCount()).isEqualTo(1)
        assertThat(broker.getPreemptionSuccessCount()).isEqualTo(1)
        assertThat(broker.getAveragePreemptionDelay()).isEqualTo(0.2)
        assertThat(broker.getPreemptionPenalty()).isEqualTo(0.5)
        assertThat(broker.getRetryCount()).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `preemption respects minimum priority gap`() {
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "test-dc", DatacenterType.LOW)
        val vm = createVm()
        val broker =
            RealtimeBroker(
                simulation,
                RealtimeMinLoadScheduler(listOf(vm)),
                listOf(vm),
                RealtimeSchedulingConfig(
                    vmQueueCapacity = 1,
                    decisionDelay = 10.0,
                    priorityLevels = 2,
                    highPriorityRatio = 1.0,
                    preemptionEnabled = true,
                    preemptionMinPriorityGap = 2,
                    retryLimit = 1,
                ),
            )
        broker.submitVmList(listOf(vm))
        broker.submitCloudletListRealtime(
            listOf(
                createCloudlet(0, length = 20_000, submissionDelay = 0.1),
                createCloudlet(1, length = 20_000, submissionDelay = 0.2),
            ),
        )

        simulation.start()

        assertThat(broker.getPreemptedCount()).isEqualTo(0)
        assertThat(broker.getCapacityRejectedCount()).isEqualTo(1)
    }

    @Test
    fun `preemption max per task limits repeated displacement`() {
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "test-dc", DatacenterType.LOW)
        val vm = createVm()
        val broker =
            RealtimeBroker(
                simulation,
                RealtimeMinLoadScheduler(listOf(vm)),
                listOf(vm),
                RealtimeSchedulingConfig(
                    vmQueueCapacity = 1,
                    decisionDelay = 10.0,
                    priorityLevels = 3,
                    highPriorityRatio = 1.0,
                    preemptionEnabled = true,
                    preemptionMinPriorityGap = 1,
                    preemptionMaxPerTask = 1,
                    retryLimit = 3,
                ),
            )
        broker.submitVmList(listOf(vm))
        broker.submitCloudletListRealtime(
            listOf(
                createCloudlet(5, length = 20_000, submissionDelay = 0.1),
                createCloudlet(1, length = 20_000, submissionDelay = 0.2),
                createCloudlet(23, length = 20_000, submissionDelay = 0.3),
            ),
        )

        simulation.start()

        assertThat(broker.getPreemptedCount()).isLessThanOrEqualTo(2)
        assertThat(broker.getPreemptionFailedCount() + broker.getCapacityRejectedCount()).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `deadline preemption policy favors earlier deadline`() {
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "test-dc", DatacenterType.LOW)
        val vm = createVm()
        val broker =
            RealtimeBroker(
                simulation,
                RealtimeMinLoadScheduler(listOf(vm)),
                listOf(vm),
                RealtimeSchedulingConfig(
                    vmQueueCapacity = 1,
                    decisionDelay = 10.0,
                    deadlineFactor = 1.0,
                    preemptionEnabled = true,
                    preemptionPolicy = "deadline_then_priority",
                    preemptionMaxPerTask = 1,
                    retryLimit = 2,
                ),
            )
        broker.submitVmList(listOf(vm))
        broker.submitCloudletListRealtime(
            listOf(
                createCloudlet(20, length = 30_000, submissionDelay = 0.1),
                createCloudlet(0, length = 1_000, submissionDelay = 0.2),
            ),
        )

        simulation.start()

        assertThat(broker.getPreemptedCount()).isEqualTo(1)
    }

    @Test
    fun `topology host failure pressure triggers runtime failure accounting`() {
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "test-dc", DatacenterType.LOW)
        val vm = createVm()
        val broker =
            RealtimeBroker(
                simulation,
                RealtimeMinLoadScheduler(listOf(vm)),
                listOf(vm),
                RealtimeSchedulingConfig(
                    topologyEnabled = true,
                    regionCount = 1,
                    racksPerRegion = 1,
                    hostsPerRack = 1,
                    hostFailureRate = 1.0,
                    retryLimit = 0,
                ),
            )
        broker.submitVmList(listOf(vm))
        broker.submitCloudletListRealtime(listOf(createCloudlet(0, length = 20_000)))

        simulation.start()

        assertThat(broker.getHostFailureCount()).isEqualTo(1)
        assertThat(broker.getRuntimeFailureCount()).isGreaterThanOrEqualTo(1)
    }
}

private fun createVm(): Vm =
    VmSimple(1000.0, 1)
        .setRam(1024)
        .setBw(1000)
        .setSize(10000)

private fun brokerFixture(
    config: RealtimeSchedulingConfig = RealtimeSchedulingConfig(),
    schedulerFactory: (Vm) -> RealtimeSchedulerBase = { vm -> RealtimeMinLoadScheduler(listOf(vm)) },
): BrokerFixture {
    val simulation = CloudSimPlus()
    DatacenterCreator.createDatacenter(simulation, "test-dc", DatacenterType.LOW)
    val vm = createVm()
    val broker = RealtimeBroker(simulation, schedulerFactory(vm), listOf(vm), config)
    broker.submitVmList(listOf(vm))
    return BrokerFixture(simulation, broker)
}

private data class BrokerFixture(
    val simulation: CloudSimPlus,
    val broker: RealtimeBroker,
)

private fun createCloudlet(
    id: Int = 0,
    length: Long = 1000,
    submissionDelay: Double = 0.1,
): Cloudlet {
    val utilizationModel = UtilizationModelFull()
    val cloudlet = CloudletSimple(length, 1)
    cloudlet.setId(id.toLong())
    cloudlet.setFileSize(100)
    cloudlet.setOutputSize(100)
    cloudlet.setSubmissionDelay(submissionDelay)
    cloudlet.setUtilizationModelCpu(utilizationModel)
    cloudlet.setUtilizationModelRam(utilizationModel)
    cloudlet.setUtilizationModelBw(utilizationModel)
    return cloudlet
}
