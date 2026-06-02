package scheduler

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

class RealtimeCloudResourceComponentsTest {
    @Test
    fun `resource snapshot combines active reservations and incoming resource demand`() {
        val vm = createVm(id = 0, ram = 1024, bw = 1000, storage = 1000)
        val active = createCloudlet(id = 1).also { it.setVm(vm) }
        val incoming = createCloudlet(id = 2)
        val metadataProvider =
            RealtimeTraceMetadataProvider.fromSpecs(
                listOf(
                    RealtimeCloudletSpec(active, RealtimeTraceMetadata(requestedRam = 512.0, requestedBw = 100.0, requestedIo = 50.0)),
                    RealtimeCloudletSpec(incoming, RealtimeTraceMetadata(requestedRam = 600.0, requestedBw = 100.0, requestedIo = 50.0)),
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

    private fun location(index: Int): RealtimeTopologyLocation =
        RealtimeTopologyLocation(RegionId(0), RackId(0), HostId(index), FailureDomainId(index))

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
}
