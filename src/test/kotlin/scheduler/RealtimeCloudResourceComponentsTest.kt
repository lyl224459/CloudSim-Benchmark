package scheduler

import config.DataLocalityPolicy
import datacenter.RealtimeCloudletSpec
import datacenter.RealtimeTraceMetadata
import datacenter.RealtimeTraceMetadataProvider
import org.assertj.core.api.Assertions.assertThat
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.cloudlets.CloudletSimple
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared
import org.cloudsimplus.utilizationmodels.UtilizationModelFull
import org.cloudsimplus.vms.Vm
import org.cloudsimplus.vms.VmSimple
import org.junit.jupiter.api.Test
import scheduler.realtime.RealtimePlacementDecision

class RealtimeCloudResourceComponentsTest {
    @Test
    fun `topology annotator returns empty candidates when no topology feature is enabled`() {
        val annotator = annotator(config = annotationConfig())

        val candidates =
            annotator.annotate(
                states = listOf(nodeState(vmIndex = 0, acceptingWork = true, availableTime = 0.0)),
                vmList = listOf(createVm(id = 0, ram = 1024, bw = 1000, storage = 1000)),
                workload = workload(),
                records = emptyList(),
            )

        assertThat(candidates).isEmpty()
    }

    @Test
    fun `topology annotator scores data locality and image cache decisions`() {
        val local = location(0, region = 0)
        val remote = location(1, region = 1)
        val annotator =
            annotator(
                config =
                    annotationConfig(
                        dataLocalityEnabled = true,
                        imageCacheEnabled = true,
                    ),
                imageCacheByHost = mapOf(local to setOf("image-a")),
                locationOf = { index -> if (index == 0) local else remote },
                latencyFor = { 0.5 },
            )

        val candidates =
            annotator.annotate(
                states =
                    listOf(
                        nodeState(vmIndex = 0, acceptingWork = true, availableTime = 0.0),
                        nodeState(vmIndex = 1, acceptingWork = true, availableTime = 0.0),
                    ),
                vmList =
                    listOf(
                        createVm(id = 0, ram = 1024, bw = 1000, storage = 1000),
                        createVm(id = 1, ram = 1024, bw = 1000, storage = 1000),
                    ),
                workload = workload(dataRegion = RegionId(0), imageId = "image-a"),
                records = emptyList(),
            )

        val localPlacement = requireNotNull(candidates.first { it.vmIndex == 0 }.acceptedPlacement)
        val remotePlacement = requireNotNull(candidates.first { it.vmIndex == 1 }.acceptedPlacement)
        assertThat(candidates.first().vmIndex).isEqualTo(0)
        assertThat(localPlacement.dataLocal).isTrue()
        assertThat(localPlacement.imageCacheHit).isTrue()
        assertThat(remotePlacement.dataLocal).isFalse()
        assertThat(remotePlacement.imageCacheHit).isFalse()
        assertThat(remotePlacement.networkTransferDelay).isGreaterThan(0.0)
        assertThat(remotePlacement.imagePullDelay).isGreaterThan(0.0)
        assertThat(remotePlacement.score).isGreaterThan(localPlacement.score)
    }

    @Test
    fun `topology annotator rejects projected physical demand above capacity`() {
        val host0 = location(0)
        val annotator =
            annotator(
                config =
                    annotationConfig(
                        physicalTopologyEnabled = true,
                        hostCapacity = HostCapacity(cpu = 2.0),
                    ),
                locationOf = { index -> location(index) },
            )
        val active =
            RealtimeTaskRecord(
                cloudletId = 1,
                originalArrivalTime = 0.0,
                assignedVmIndex = 0,
                lifecycle = RealtimeTaskLifecycle.RUNNING,
                requestedCpu = 1.0,
            )

        val candidates =
            annotator.annotate(
                states =
                    listOf(
                        nodeState(vmIndex = 0, acceptingWork = true, availableTime = 0.0),
                        nodeState(vmIndex = 1, acceptingWork = true, availableTime = 0.0),
                    ),
                vmList =
                    listOf(
                        createVm(id = 0, ram = 1024, bw = 1000, storage = 1000),
                        createVm(id = 1, ram = 1024, bw = 1000, storage = 1000),
                    ),
                workload = workload(demand = RealtimeResourceDemand(cpu = 1.5)),
                records = listOf(active, active.copy(cloudletId = 2, lifecycle = RealtimeTaskLifecycle.COMPLETED)),
            )

        val rejected = candidates.first { it.vmIndex == 0 }.placement
        val accepted = requireNotNull(candidates.first { it.vmIndex == 1 }.acceptedPlacement)
        assertThat(rejected).isEqualTo(
            RealtimePlacementDecision.Rejected(VmIndex(0), host0, "physical_cpu_capacity"),
        )
        assertThat(accepted.hostState.allocatedCpu).isEqualTo(1.5)
    }

    @Test
    fun `data locality policy changes only remote placement score penalty`() {
        val remote = location(0, region = 1)
        val ignorePlacement =
            remotePlacementForPolicy(DataLocalityPolicy.IGNORE, remote)
        val preferLocalPlacement =
            remotePlacementForPolicy(DataLocalityPolicy.PREFER_LOCAL, remote)

        assertThat(ignorePlacement.dataLocal).isFalse()
        assertThat(ignorePlacement.networkTransferGb).isEqualTo(4.0)
        assertThat(preferLocalPlacement.networkTransferDelay).isEqualTo(ignorePlacement.networkTransferDelay)
        assertThat(preferLocalPlacement.score - ignorePlacement.score).isEqualTo(4.0)
    }

    @Test
    fun `topology annotator rejects ram bandwidth and io capacity pressure`() {
        val scenarios =
            listOf(
                capacityScenario(
                    hostCapacity = HostCapacity(ram = 1.0),
                    workload = workload(demand = RealtimeResourceDemand(ram = 2.0)),
                ) to
                    "physical_ram_capacity",
                capacityScenario(
                    hostCapacity = HostCapacity(bw = 1.0),
                    workload = workload(demand = RealtimeResourceDemand(bw = 2.0)),
                ) to
                    "physical_bw_capacity",
                capacityScenario(
                    hostCapacity = HostCapacity(io = 1.0),
                    workload = workload(demand = RealtimeResourceDemand(io = 2.0)),
                ) to
                    "physical_io_capacity",
            )

        val reasons =
            scenarios.map { (scenario, _) ->
                val placement =
                    scenario.annotator
                        .annotate(
                            states = listOf(nodeState(vmIndex = 0, acceptingWork = true, availableTime = 0.0)),
                            vmList = listOf(createVm(id = 0, ram = 1024, bw = 1000, storage = 1000)),
                            workload = scenario.workload,
                            records = emptyList(),
                        ).single()
                        .placement as RealtimePlacementDecision.Rejected
                placement.reason
            }

        assertThat(reasons).containsExactlyElementsOf(scenarios.map { it.second })
    }

    @Test
    fun `cpu overcommit throttles within ratio and rejects above hard limit`() {
        val annotator =
            annotator(
                config =
                    annotationConfig(
                        physicalTopologyEnabled = true,
                        hostCapacity = HostCapacity(cpu = 2.0),
                        cpuOvercommitRatio = 1.5,
                    ),
            )

        val accepted =
            requireNotNull(
                annotator
                    .annotate(
                        states = listOf(nodeState(vmIndex = 0, acceptingWork = true, availableTime = 0.0)),
                        vmList = listOf(createVm(id = 0, ram = 1024, bw = 1000, storage = 1000)),
                        workload = workload(demand = RealtimeResourceDemand(cpu = 2.5)),
                        records = emptyList(),
                    ).single()
                    .acceptedPlacement,
            )
        val rejected =
            annotator
                .annotate(
                    states = listOf(nodeState(vmIndex = 0, acceptingWork = true, availableTime = 0.0)),
                    vmList = listOf(createVm(id = 0, ram = 1024, bw = 1000, storage = 1000)),
                    workload = workload(demand = RealtimeResourceDemand(cpu = 3.1)),
                    records = emptyList(),
                ).single()
                .placement as RealtimePlacementDecision.Rejected

        assertThat(accepted.hostResourceDelay).isGreaterThan(0.0)
        assertThat(rejected.reason).isEqualTo("physical_cpu_capacity")
    }

    @Test
    fun `network bandwidth sharing divides route capacity by active transfers`() {
        val remote = location(0, region = 1)
        val annotator =
            annotator(
                config =
                    annotationConfig(
                        dataLocalityEnabled = true,
                        networkBandwidthSharingEnabled = true,
                        crossRegionBandwidth = 2.0,
                    ),
                locationOf = { remote },
                latencyFor = { 0.0 },
            )
        val active =
            RealtimeTaskRecord(
                cloudletId = 1,
                originalArrivalTime = 0.0,
                assignedVmIndex = 0,
                lifecycle = RealtimeTaskLifecycle.RUNNING,
                dataRegion = RegionId(0),
                inputDataSizeGb = 1.0,
            )

        val placement =
            requireNotNull(
                annotator
                    .annotate(
                        states = listOf(nodeState(vmIndex = 0, acceptingWork = true, availableTime = 0.0)),
                        vmList = listOf(createVm(id = 0, ram = 1024, bw = 1000, storage = 1000)),
                        workload = workload(dataRegion = RegionId(0), inputDataSizeGb = 4.0),
                        records = listOf(active),
                    ).single()
                    .acceptedPlacement,
            )

        assertThat(placement.networkTransferDelay).isEqualTo(4.0)
    }

    @Test
    fun `network sharing rejects remote transfer when configured bandwidth is zero`() {
        val remote = location(0, region = 1)
        val annotator =
            annotator(
                config =
                    annotationConfig(
                        dataLocalityEnabled = true,
                        networkBandwidthSharingEnabled = true,
                        crossRegionBandwidth = 0.0,
                    ),
                locationOf = { remote },
            )

        val placement =
            annotator
                .annotate(
                    states = listOf(nodeState(vmIndex = 0, acceptingWork = true, availableTime = 0.0)),
                    vmList = listOf(createVm(id = 0, ram = 1024, bw = 1000, storage = 1000)),
                    workload = workload(dataRegion = RegionId(0), inputDataSizeGb = 1.0),
                    records = emptyList(),
                ).single()
                .placement as RealtimePlacementDecision.Rejected

        assertThat(placement.reason).isEqualTo("network_bandwidth_capacity")
    }

    @Test
    fun `storage iops sharing adds host resource delay`() {
        val annotator =
            annotator(
                config =
                    annotationConfig(
                        physicalTopologyEnabled = true,
                        storageIopsSharingEnabled = true,
                        hostCapacity = HostCapacity(io = 10.0),
                    ),
            )

        val placement =
            requireNotNull(
                annotator
                    .annotate(
                        states = listOf(nodeState(vmIndex = 0, acceptingWork = true, availableTime = 0.0)),
                        vmList = listOf(createVm(id = 0, ram = 1024, bw = 1000, storage = 1000)),
                        workload = workload(demand = RealtimeResourceDemand(io = 5.0)),
                        records = emptyList(),
                    ).single()
                    .acceptedPlacement,
            )

        assertThat(placement.hostResourceDelay).isEqualTo(0.5)
    }

    @Test
    fun `image pull queue multiplies cache miss delay on same host`() {
        val host = location(0)
        val annotator =
            annotator(
                config =
                    annotationConfig(
                        imageCacheEnabled = true,
                        imagePullQueueEnabled = true,
                    ),
                locationOf = { host },
            )
        val activeMiss =
            RealtimeTaskRecord(
                cloudletId = 1,
                originalArrivalTime = 0.0,
                assignedVmIndex = 0,
                lifecycle = RealtimeTaskLifecycle.RUNNING,
                imageId = "active-image",
                imageSizeGb = 2.0,
            )

        val placement =
            requireNotNull(
                annotator
                    .annotate(
                        states = listOf(nodeState(vmIndex = 0, acceptingWork = true, availableTime = 0.0)),
                        vmList = listOf(createVm(id = 0, ram = 1024, bw = 1000, storage = 1000)),
                        workload = workload(imageId = "incoming-image", imageSizeGb = 2.0),
                        records = listOf(activeMiss),
                    ).single()
                    .acceptedPlacement,
            )

        assertThat(placement.imagePullDelay).isEqualTo(0.004)
    }

    @Test
    fun `noisy neighbor pressure increases placement resource pressure`() {
        val host = location(0)
        val annotator =
            annotator(
                config =
                    annotationConfig(
                        physicalTopologyEnabled = true,
                        hostCapacity = HostCapacity(cpu = 4.0),
                        noisyNeighborPenaltyWeight = 2.0,
                    ),
                locationOf = { host },
            )
        val active =
            RealtimeTaskRecord(
                cloudletId = 1,
                originalArrivalTime = 0.0,
                assignedVmIndex = 0,
                lifecycle = RealtimeTaskLifecycle.RUNNING,
                requestedCpu = 2.0,
            )

        val candidate =
            annotator
                .annotate(
                    states = listOf(nodeState(vmIndex = 0, acceptingWork = true, availableTime = 0.0)),
                    vmList = listOf(createVm(id = 0, ram = 1024, bw = 1000, storage = 1000)),
                    workload = workload(demand = RealtimeResourceDemand(cpu = 1.0)),
                    records = listOf(active),
                ).single()

        assertThat(candidate.acceptedPlacement?.noisyNeighborPressure).isEqualTo(2.0)
        assertThat(candidate.nodeState.resourcePressure).isEqualTo(2.0)
    }

    @Test
    fun `image pull delay falls back when image size or vm bandwidth is zero`() {
        val annotator =
            annotator(
                config = annotationConfig(imageCacheEnabled = true),
            )
        val candidates =
            annotator.annotate(
                states =
                    listOf(
                        nodeState(vmIndex = 0, acceptingWork = true, availableTime = 0.0),
                        nodeState(vmIndex = 1, acceptingWork = true, availableTime = 0.0),
                    ),
                vmList =
                    listOf(
                        createVm(id = 0, ram = 1024, bw = 0, storage = 1000),
                        createVm(id = 1, ram = 1024, bw = 1000, storage = 1000),
                    ),
                workload = workload(imageId = "cold-image", imageSizeGb = 0.0),
                records = emptyList(),
            )

        val zeroBandwidthPlacement = requireNotNull(candidates.first { it.vmIndex == 0 }.acceptedPlacement)
        val normalBandwidthPlacement = requireNotNull(candidates.first { it.vmIndex == 1 }.acceptedPlacement)
        assertThat(zeroBandwidthPlacement.imageCacheHit).isFalse()
        assertThat(zeroBandwidthPlacement.imagePullDelay).isEqualTo(1.0)
        assertThat(normalBandwidthPlacement.imagePullDelay).isEqualTo(0.001)
    }

    @Test
    fun `resource snapshot combines active reservations and incoming resource demand`() {
        val vm = createVm(id = 0, ram = 1024, bw = 1000, storage = 1000)
        val active = createCloudlet(id = 1).also { it.setVm(vm) }
        val incoming = createCloudlet(id = 2)
        val metadataProvider =
            RealtimeTraceMetadataProvider.fromSpecs(
                listOf(
                    RealtimeCloudletSpec(
                        active,
                        RealtimeTraceMetadata(requestedRam = 512.0, requestedBw = 100.0, requestedIo = 50.0),
                    ),
                    RealtimeCloudletSpec(
                        incoming,
                        RealtimeTraceMetadata(requestedRam = 600.0, requestedBw = 100.0, requestedIo = 50.0),
                    ),
                ),
            )
        val resourceModel =
            RealtimeResourceModel(
                enabled = true,
                networkLatency = 0.1,
                imagePullDelay = 0.2,
                ioWeight = 0.0,
                ramWeight = 0.0,
                bwWeight = 0.0,
                traceMetadataProvider = metadataProvider,
            )

        val state =
            ResourceSnapshotBuilder(listOf(vm), vmQueueCapacity = 3, resourceModel = resourceModel)
                .build(
                    ResourceSnapshotRequest(
                        activeCloudlets = listOf(active),
                        currentTime = 10.0,
                        reservedVmIndexes = mapOf(99L to 0),
                        incomingCloudlet = incoming,
                    ),
                ).single()

        assertThat(state.queueDepth).isEqualTo(2)
        assertThat(state.availableSlots).isEqualTo(1)
        assertThat(state.ramPressure).isEqualTo(0.5)
        assertThat(state.resourceAcceptingWork).isFalse()
        assertThat(state.acceptingWork).isFalse()
        assertThat(state.rejectionReason).isEqualTo("resource_capacity")
    }

    @Test
    fun `candidate scorer keeps rejected placements for diagnostics`() {
        val accepted =
            NodeCandidate(
                nodeState = nodeState(vmIndex = 0, acceptingWork = true, availableTime = 2.0),
                placement = acceptedPlacement(vmIndex = 0, score = 3.0),
                score = 0.0,
            )
        val nonAccepting =
            NodeCandidate(
                nodeState = nodeState(vmIndex = 1, acceptingWork = false, availableTime = 1.0),
                placement = acceptedPlacement(vmIndex = 1, score = 1.0),
                score = 0.0,
            )
        val rejected =
            NodeCandidate(
                nodeState = nodeState(vmIndex = 2, acceptingWork = false, availableTime = 5.0),
                placement =
                    RealtimePlacementDecision.Rejected(
                        vmIndex = VmIndex(2),
                        location = location(2),
                        reason = "physical_cpu_capacity",
                    ),
                score = 0.0,
            )

        val scored = VmCandidateScorer().score(listOf(accepted, nonAccepting, rejected))

        assertThat(scored.map { it.vmIndex }).containsExactly(0, 2)
        assertThat(scored.first().score).isEqualTo(5.0)
        assertThat(scored.last().placement).isInstanceOf(RealtimePlacementDecision.Rejected::class.java)
    }

    @Test
    fun `physical host metrics use active cloud resource demand`() {
        val model =
            RealtimeTopologyModel.fromConfig(
                config.RealtimeSchedulingConfig(
                    physicalTopologyEnabled = true,
                    regionCount = 1,
                    racksPerRegion = 1,
                    hostCountPerRack = 1,
                    hostCpuCapacity = 4.0,
                    hostRamCapacity = 8.0,
                    hostBwCapacity = 100.0,
                    hostIoCapacity = 100.0,
                ),
                initialVmCount = 1,
            )
        val record =
            RealtimeTaskRecord(
                cloudletId = 1,
                originalArrivalTime = 0.0,
                assignedVmIndex = 0,
                lifecycle = RealtimeTaskLifecycle.RUNNING,
                requestedCpu = 2.0,
                requestedRam = 4.0,
                requestedBw = 10.0,
                requestedIo = 5.0,
            )

        val metrics = model.physicalHostMetrics(listOf(record))

        assertThat(metrics.averageUtilization).isEqualTo(0.5)
        assertThat(metrics.averageFragmentation).isGreaterThan(0.0)
    }

    private fun acceptedPlacement(
        vmIndex: Int,
        score: Double,
    ): RealtimePlacementDecision.Accepted =
        RealtimePlacementDecision.Accepted(
            vmIndex = VmIndex(vmIndex),
            location = location(vmIndex),
            hostState =
                RealtimeHostState(
                    location = location(vmIndex),
                    cpuCapacity = 4.0,
                    ramCapacity = 8.0,
                    bwCapacity = 100.0,
                    ioCapacity = 100.0,
                ),
            dataLocal = true,
            imageCacheHit = false,
            networkTransferDelay = 0.0,
            networkTransferGb = 0.0,
            imagePullDelay = 0.0,
            hostResourceDelay = 0.0,
            noisyNeighborPressure = 0.0,
            topologyCost = 0.0,
            score = score,
        )

    private fun nodeState(
        vmIndex: Int,
        acceptingWork: Boolean,
        availableTime: Double,
    ): RealtimeNodeState =
        RealtimeNodeState(
            vmIndex = vmIndex,
            vmId = vmIndex.toLong(),
            runningCount = 0,
            pendingCount = 0,
            queueDepth = 0,
            availableSlots = Int.MAX_VALUE,
            acceptingWork = acceptingWork,
            estimatedLoad = availableTime,
            availableTime = availableTime,
            failurePressure = 0.0,
        )

    private fun annotator(
        config: TopologyCandidateAnnotationConfig,
        imageCacheByHost: Map<RealtimeTopologyLocation, Set<String>> = emptyMap(),
        locationOf: (Int) -> RealtimeTopologyLocation = { index -> location(index) },
        latencyFor: (RealtimeTopologyLocation) -> Double = { 0.0 },
        costFor: (RealtimeTopologyLocation) -> Double = { 0.0 },
    ): TopologyCandidateAnnotator =
        TopologyCandidateAnnotator(
            config = config,
            locationOf = locationOf,
            latencyFor = latencyFor,
            costFor = costFor,
            imageCacheByHost = imageCacheByHost,
        )

    private fun annotationConfig(
        physicalTopologyEnabled: Boolean = false,
        dataLocalityEnabled: Boolean = false,
        imageCacheEnabled: Boolean = false,
        hostCapacity: HostCapacity = HostCapacity(),
        dataLocalityPolicy: DataLocalityPolicy = DataLocalityPolicy.BALANCED,
        cpuOvercommitRatio: Double = 1.0,
        networkBandwidthSharingEnabled: Boolean = false,
        storageIopsSharingEnabled: Boolean = false,
        imagePullQueueEnabled: Boolean = false,
        noisyNeighborPenaltyWeight: Double = 0.0,
        crossRackBandwidth: Double = 4.0,
        crossRegionBandwidth: Double = 2.0,
    ): TopologyCandidateAnnotationConfig =
        TopologyCandidateAnnotationConfig(
            enabled =
                physicalTopologyEnabled ||
                    dataLocalityEnabled ||
                    imageCacheEnabled ||
                    noisyNeighborPenaltyWeight > 0.0,
            physicalTopologyEnabled = physicalTopologyEnabled,
            dataLocalityEnabled = dataLocalityEnabled,
            imageCacheEnabled = imageCacheEnabled,
            localRegion = RegionId(0),
            hostCpuCapacity = hostCapacity.cpu,
            cpuOvercommitRatio = cpuOvercommitRatio,
            hostRamCapacity = hostCapacity.ram,
            hostBwCapacity = hostCapacity.bw,
            hostIoCapacity = hostCapacity.io,
            networkBandwidthSharingEnabled = networkBandwidthSharingEnabled,
            storageIopsSharingEnabled = storageIopsSharingEnabled,
            imagePullQueueEnabled = imagePullQueueEnabled,
            noisyNeighborPenaltyWeight = noisyNeighborPenaltyWeight,
            crossRackBandwidth = crossRackBandwidth,
            crossRegionBandwidth = crossRegionBandwidth,
            dataLocalityPolicy = dataLocalityPolicy,
        )

    private fun workload(
        demand: RealtimeResourceDemand = RealtimeResourceDemand(cpu = 1.0),
        dataRegion: RegionId = RegionId(0),
        inputDataSizeGb: Double = 4.0,
        imageId: String? = null,
        imageSizeGb: Double = 2.0,
    ): RealtimeWorkloadDescriptor =
        RealtimeWorkloadDescriptor(
            cloudletId = CloudletId(99),
            tenantId = TenantId(0),
            priority = 0,
            deadline = null,
            requestedCpu = demand.cpu,
            requestedRam = demand.ram,
            requestedBw = demand.bw,
            requestedIo = demand.io,
            dataRegion = dataRegion,
            inputDataSizeGb = inputDataSizeGb,
            imageId = imageId,
            imageSizeGb = imageSizeGb,
        )

    private fun location(
        index: Int,
        region: Int = 0,
        rack: Int = 0,
    ): RealtimeTopologyLocation {
        val regionId = RegionId(region)
        return RealtimeTopologyLocation(regionId, RackId(rack), HostId(index), FailureDomainId(index))
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

    private fun createVm(
        id: Int,
        ram: Long,
        bw: Long,
        storage: Long,
    ): Vm =
        VmSimple(1000.0, 1)
            .also { it.setId(id.toLong()) }
            .setRam(ram)
            .setBw(bw)
            .setSize(storage)
            .setCloudletScheduler(CloudletSchedulerSpaceShared())

    private fun remotePlacementForPolicy(
        policy: DataLocalityPolicy,
        remote: RealtimeTopologyLocation,
    ): RealtimePlacementDecision.Accepted {
        val annotator =
            annotator(
                config =
                    annotationConfig(
                        dataLocalityEnabled = true,
                        dataLocalityPolicy = policy,
                    ),
                locationOf = { remote },
                latencyFor = { 0.0 },
            )
        return requireNotNull(
            annotator
                .annotate(
                    states = listOf(nodeState(vmIndex = 0, acceptingWork = true, availableTime = 0.0)),
                    vmList = listOf(createVm(id = 0, ram = 1024, bw = 1000, storage = 1000)),
                    workload = workload(dataRegion = RegionId(0)),
                    records = emptyList(),
                ).single()
                .acceptedPlacement,
        )
    }

    private fun capacityScenario(
        hostCapacity: HostCapacity,
        workload: RealtimeWorkloadDescriptor,
    ): CapacityScenario =
        CapacityScenario(
            annotator =
                annotator(
                    config =
                        annotationConfig(
                            physicalTopologyEnabled = true,
                            hostCapacity = hostCapacity,
                        ),
                ),
            workload = workload,
        )

    private data class HostCapacity(
        val cpu: Double = 100.0,
        val ram: Double = 100.0,
        val bw: Double = 100.0,
        val io: Double = 100.0,
    )

    private data class CapacityScenario(
        val annotator: TopologyCandidateAnnotator,
        val workload: RealtimeWorkloadDescriptor,
    )
}
