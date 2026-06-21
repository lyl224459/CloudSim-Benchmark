package scheduler

import config.RealtimeSchedulingConfig
import org.assertj.core.api.Assertions.assertThat
import org.cloudsimplus.cloudlets.CloudletSimple
import org.cloudsimplus.utilizationmodels.UtilizationModelFull
import org.cloudsimplus.vms.VmSimple
import org.junit.jupiter.api.Test
import scheduler.realtime.RealtimePlacementDecision

class RealtimeTopologyModelTest {
    @Test
    fun `vm index maps to deterministic topology location`() {
        val model =
            RealtimeTopologyModel.fromConfig(
                topologyConfig(shape = TopologyShape(regionCount = 2, racksPerRegion = 2, hostsPerRack = 2)),
                initialVmCount = 8,
            )

        assertThat(model.locationOf(0)).isEqualTo(
            RealtimeTopologyLocation(RegionId(0), RackId(0), HostId(0), FailureDomainId(0)),
        )
        assertThat(model.locationOf(1)).isEqualTo(
            RealtimeTopologyLocation(RegionId(1), RackId(0), HostId(0), FailureDomainId(4)),
        )
        assertThat(model.locationOf(2)).isEqualTo(
            RealtimeTopologyLocation(RegionId(0), RackId(1), HostId(0), FailureDomainId(2)),
        )
        assertThat(model.locationOf(4)).isEqualTo(
            RealtimeTopologyLocation(RegionId(0), RackId(0), HostId(1), FailureDomainId(1)),
        )
    }

    @Test
    fun `latency and cost reflect local rack and cross region placement`() {
        val model =
            RealtimeTopologyModel.fromConfig(
                topologyConfig(
                    shape = TopologyShape(regionCount = 2, racksPerRegion = 2),
                    network = TopologyNetwork(crossRackLatency = 0.2, crossRegionLatency = 1.5, crossRegionCost = 0.7),
                ),
                initialVmCount = 3,
            )

        assertThat(model.latencyFor(model.locationOf(0))).isEqualTo(0.0)
        assertThat(model.latencyFor(model.locationOf(1))).isEqualTo(1.5)
        assertThat(model.costFor(model.locationOf(1))).isEqualTo(0.7)
        assertThat(model.latencyFor(model.locationOf(2))).isEqualTo(0.2)
        assertThat(model.costFor(model.locationOf(2))).isEqualTo(0.0)
    }

    @Test
    fun `dynamic vm chooses least loaded failure domain`() {
        val model =
            RealtimeTopologyModel.fromConfig(
                topologyConfig(shape = TopologyShape(regionCount = 2, racksPerRegion = 1, hostsPerRack = 1)),
                initialVmCount = 2,
            )

        val location = model.registerDynamicVm(vmIndex = 2, activeVmIndexes = setOf(0))

        assertThat(location.failureDomainId).isEqualTo(FailureDomainId(1))
    }

    @Test
    fun `metrics count cross rack cross region latency cost and spread`() {
        val model =
            RealtimeTopologyModel.fromConfig(
                topologyConfig(
                    shape = TopologyShape(regionCount = 2, racksPerRegion = 2, hostsPerRack = 1),
                    network = TopologyNetwork(crossRackLatency = 0.2, crossRegionLatency = 1.0, crossRegionCost = 0.5),
                ),
                initialVmCount = 4,
            )

        val metrics = model.metricsFor(listOf(0, 1, 2, 3))

        assertThat(metrics.crossRegionAssignmentCount).isEqualTo(2)
        assertThat(metrics.crossRackAssignmentCount).isEqualTo(3)
        assertThat(metrics.averageTopologyLatency).isEqualTo(0.55)
        assertThat(metrics.topologyCost).isEqualTo(1.0)
        assertThat(metrics.failureDomainSpreadScore).isEqualTo(1.0)
    }

    @Test
    fun `physical candidates enforce capacity and reuse image cache`() {
        val model =
            RealtimeTopologyModel.fromConfig(
                RealtimeSchedulingConfig(
                    physicalTopologyEnabled = true,
                    imageCacheEnabled = true,
                    regionCount = 1,
                    racksPerRegion = 1,
                    hostCountPerRack = 1,
                    hostCpuCapacity = 1.0,
                    imageCacheCapacity = 1,
                ),
                initialVmCount = 1,
            )
        val workload =
            RealtimeWorkloadDescriptor(
                cloudletId = CloudletId(1),
                tenantId = TenantId(0),
                priority = 0,
                deadline = null,
                requestedCpu = 2.0,
                requestedRam = 0.0,
                requestedBw = 0.0,
                requestedIo = 0.0,
                dataRegion = RegionId(0),
                inputDataSizeGb = 0.0,
                imageId = "base-image",
                imageSizeGb = 10.0,
            )

        val rejected = model.candidatesFor(listOf(nodeState(0)), createVms(1), workload, emptyList()).single()
        assertThat(rejected.placement).isInstanceOf(RealtimePlacementDecision.Rejected::class.java)

        val acceptedWorkload = workload.copy(requestedCpu = 0.5)
        val first = model.candidatesFor(listOf(nodeState(0)), createVms(1), acceptedWorkload, emptyList()).single()
        assertThat(first.acceptedPlacement?.imageCacheHit).isFalse()

        model.recordSubmission(0, acceptedWorkload)
        val second = model.candidatesFor(listOf(nodeState(0)), createVms(1), acceptedWorkload, emptyList()).single()
        assertThat(second.acceptedPlacement?.imageCacheHit).isTrue()
    }

    @Test
    fun `latency aware scheduler prefers earliest topology adjusted available time`() {
        val scheduler = RealtimeMinLoadScheduler(createVms(3))
        val selected =
            scheduler.scheduleOnArrival(
                RealtimeSchedulingContext(
                    newCloudlet = createCloudlet(),
                    activeCloudlets = emptyList(),
                    vmList = createVms(3),
                    currentTime = 0.0,
                    nodeStates =
                        listOf(
                            nodeState(vmIndex = 0, availableTime = 5.0, topologyLatency = 0.0),
                            nodeState(vmIndex = 1, availableTime = 1.0, topologyLatency = 1.0),
                            nodeState(vmIndex = 2, availableTime = 2.0, topologyLatency = 0.2),
                        ),
                    topologyPolicy = config.RealtimeTopologyPolicy.LATENCY_AWARE,
                ),
            )

        assertThat(selected).isEqualTo(1)
    }

    @Test
    fun `spread fault domains scheduler prefers lower domain load before latency`() {
        val scheduler = RealtimeMinLoadScheduler(createVms(3))
        val selected =
            scheduler.scheduleOnArrival(
                RealtimeSchedulingContext(
                    newCloudlet = createCloudlet(),
                    activeCloudlets = emptyList(),
                    vmList = createVms(3),
                    currentTime = 0.0,
                    nodeStates =
                        listOf(
                            nodeState(vmIndex = 0, failureDomainLoad = 3, topologyLatency = 0.0),
                            nodeState(vmIndex = 1, failureDomainLoad = 0, topologyLatency = 1.0),
                            nodeState(vmIndex = 2, failureDomainLoad = 1, topologyLatency = 0.1),
                        ),
                    topologyPolicy = config.RealtimeTopologyPolicy.SPREAD_FAULT_DOMAINS,
                ),
            )

        assertThat(selected).isEqualTo(1)
    }

    private fun topologyConfig(
        shape: TopologyShape = TopologyShape(),
        network: TopologyNetwork = TopologyNetwork(),
    ): RealtimeSchedulingConfig =
        RealtimeSchedulingConfig(
            topologyEnabled = true,
            regionCount = shape.regionCount,
            racksPerRegion = shape.racksPerRegion,
            hostsPerRack = shape.hostsPerRack,
            localRegion = 0,
            crossRackLatency = network.crossRackLatency,
            crossRegionLatency = network.crossRegionLatency,
            crossRegionCost = network.crossRegionCost,
        )

    private data class TopologyShape(
        val regionCount: Int = 2,
        val racksPerRegion: Int = 2,
        val hostsPerRack: Int = 1,
    )

    private data class TopologyNetwork(
        val crossRackLatency: Double = 0.1,
        val crossRegionLatency: Double = 1.0,
        val crossRegionCost: Double = 0.0,
    )

    private fun nodeState(
        vmIndex: Int,
        availableTime: Double = 0.0,
        topologyLatency: Double = 0.0,
        topologyCost: Double = 0.0,
        failureDomainLoad: Int = 0,
    ): RealtimeNodeState =
        RealtimeNodeState(
            vmIndex = vmIndex,
            vmId = vmIndex.toLong(),
            runningCount = 0,
            pendingCount = 0,
            queueDepth = 0,
            availableSlots = Int.MAX_VALUE,
            acceptingWork = true,
            estimatedLoad = availableTime,
            availableTime = availableTime,
            failurePressure = 0.0,
            topologyLatency = topologyLatency,
            topologyCost = topologyCost,
            failureDomainLoad = failureDomainLoad,
        )

    private fun createVms(count: Int) =
        (0 until count).map { index ->
            VmSimple(1000.0 + index, 1)
                .setRam(1024)
                .setBw(1000)
                .setSize(10000)
        }

    private fun createCloudlet(): CloudletSimple {
        val utilization = UtilizationModelFull()
        return CloudletSimple(1000, 1)
            .setUtilizationModelCpu(utilization)
            .setUtilizationModelRam(utilization)
            .setUtilizationModelBw(utilization) as CloudletSimple
    }
}
