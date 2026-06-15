package scheduler

import config.ObjectiveWeightsConfig
import config.RealtimeQueuePolicy
import config.RealtimeTopologyPolicy
import config.TenantSchedulingPolicy
import datacenter.SchedulerObjectiveFunction
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.cloudlets.CloudletSimple
import org.cloudsimplus.utilizationmodels.UtilizationModelFull
import org.cloudsimplus.vms.Vm
import org.cloudsimplus.vms.VmSimple
import org.junit.jupiter.api.Test
import java.util.Random
import kotlin.random.Random as KotlinRandom

class SchedulerCoreCoverageTest {
    @Test
    fun `batch schedulers reject empty task lists`() {
        val vms = vms(1)

        listOf<() -> Scheduler>(
            { RLScheduler(emptyList(), vms, episodes = 1) },
            { ImprovedRLScheduler(emptyList(), vms, episodes = 1) },
        ).forEach { createScheduler ->
            assertThatThrownBy { createScheduler() }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("至少 1 个可调度任务")
        }
    }

    @Test
    fun `rl schedulers are reproducible and produce finite valid allocations`() {
        val cloudlets = cloudlets(5)
        val homogeneousVms = vms(3, homogeneous = true)

        val rlFirst = RLScheduler(cloudlets, homogeneousVms, episodes = 8, random = KotlinRandom(42)).allocate()
        val rlSecond = RLScheduler(cloudlets, homogeneousVms, episodes = 8, random = KotlinRandom(42)).allocate()
        val improvedFirst =
            ImprovedRLScheduler(cloudlets, homogeneousVms, episodes = 8, random = KotlinRandom(42)).allocate()
        val improvedSecond =
            ImprovedRLScheduler(cloudlets, homogeneousVms, episodes = 8, random = KotlinRandom(42)).allocate()

        assertValidFiniteAllocation(cloudlets, homogeneousVms, rlFirst)
        assertValidFiniteAllocation(cloudlets, homogeneousVms, improvedFirst)
        assertThat(rlFirst.toList()).isEqualTo(rlSecond.toList())
        assertThat(improvedFirst.toList()).isEqualTo(improvedSecond.toList())
    }

    @Test
    fun `rl schedulers allocate every task to the only vm`() {
        val cloudlets = cloudlets(3)
        val singleVm = vms(1)

        assertThat(RLScheduler(cloudlets, singleVm, episodes = 3, random = KotlinRandom(7)).allocate().toList())
            .containsOnly(0)
        assertThat(ImprovedRLScheduler(cloudlets, singleVm, episodes = 3, random = KotlinRandom(7)).allocate().toList())
            .containsOnly(0)
    }

    @Test
    fun `realtime random scheduler uses accepted candidates and fixed seed`() {
        val vms = vms(3)
        val context =
            context(
                vms,
                states =
                    listOf(
                        nodeState(0, accepting = false),
                        nodeState(1, accepting = true),
                        nodeState(2, accepting = true),
                    ),
            )
        val first = RealtimeRandomScheduler(vms, Random(42))
        val second = RealtimeRandomScheduler(vms, Random(42))

        val firstSequence = List(8) { first.scheduleOnArrival(context) }
        val secondSequence = List(8) { second.scheduleOnArrival(context) }

        assertThat(firstSequence).isEqualTo(secondSequence)
        assertThat(firstSequence).allSatisfy { assertThat(it).isIn(1, 2) }
    }

    @Test
    fun `realtime base covers threshold repair fallback and noncontinuous vm ids`() {
        val vms = vms(2).onEachIndexed { index, vm -> vm.setId((10 + index).toLong()) }
        val probe = RealtimeSchedulerProbe(vms)
        val unknownVm = VmSimple(500.0, 1).apply { setId(99) }
        val unknownVmCloudlet = cloudlets(1).first().setVm(unknownVm)
        val knownVmCloudlet = cloudlets(1).first().setVm(vms[0])
        val context =
            context(
                vms,
                active = cloudlets(2),
                states = listOf(nodeState(0, accepting = false), nodeState(1, accepting = true)),
            )

        assertThat(probe.leastLoaded(listOf(unknownVmCloudlet, knownVmCloudlet))).isEqualTo(1)
        assertThat(probe.optimize(context.copy(activeCloudlets = emptyList()))).isNull()
        assertThat(probe.optimize(context)).isEqualTo(1)
        assertThat(probe.repair(context, listOf(nodeState(1, accepting = true)), 99)).isEqualTo(1)
        assertThat(probe.fallback(context)).isEqualTo(1)
    }

    @Test
    fun `realtime base orders queue topology and tenant policies`() {
        val vms = vms(2)
        val probe = RealtimeSchedulerProbe(vms)
        val states =
            listOf(
                nodeState(0, accepting = true, NodeStateOverrides(queueDepth = 3, slots = 1, latency = 1.0)),
                nodeState(1, accepting = true, NodeStateOverrides(slots = 4, latency = 0.1, domainLoad = 2)),
            )
        val base = context(vms, states = states)

        assertThat(probe.ordered(base.copy(queuePolicy = RealtimeQueuePolicy.PRIORITY)).first().vmIndex).isEqualTo(1)
        assertThat(probe.ordered(base.copy(queuePolicy = RealtimeQueuePolicy.DEADLINE)).first().vmIndex).isEqualTo(1)
        assertThat(
            probe
                .ordered(
                    base.copy(
                        topologyPolicy = RealtimeTopologyPolicy.SPREAD_FAULT_DOMAINS,
                        tenantSchedulingPolicy = TenantSchedulingPolicy.WEIGHTED_FAIR,
                    ),
                ).first()
                .vmIndex,
        ).isEqualTo(0)
        assertThat(
            probe.ordered(base.copy(tenantSchedulingPolicy = TenantSchedulingPolicy.DOMINANT_RESOURCE_FAIRNESS)),
        ).hasSize(2)
    }

    private fun assertValidFiniteAllocation(
        cloudlets: List<Cloudlet>,
        vms: List<Vm>,
        allocation: IntArray,
    ) {
        assertThat(allocation).hasSize(cloudlets.size)
        assertThat(allocation.toList()).allSatisfy { assertThat(it).isBetween(0, vms.lastIndex) }
        val fitness = SchedulerObjectiveFunction(cloudlets, vms, ObjectiveWeightsConfig()).calculate(allocation)
        assertThat(fitness.isFinite()).isTrue()
    }

    private fun cloudlets(count: Int): List<Cloudlet> {
        val utilization = UtilizationModelFull()
        return (0 until count).map { index ->
            CloudletSimple(1_000L + index * 250L, 1)
                .setFileSize(100)
                .setOutputSize(100)
                .setUtilizationModelCpu(utilization)
                .setUtilizationModelRam(utilization)
                .setUtilizationModelBw(utilization)
        }
    }

    private fun vms(
        count: Int,
        homogeneous: Boolean = false,
    ): List<Vm> =
        (0 until count).map { index ->
            VmSimple(if (homogeneous) 1_000.0 else 1_000.0 + index * 250.0, 1)
                .setRam(1_024)
                .setBw(1_000)
                .setSize(10_000)
        }

    private fun context(
        vms: List<Vm>,
        active: List<Cloudlet> = emptyList(),
        states: List<RealtimeNodeState>,
    ): RealtimeSchedulingContext =
        RealtimeSchedulingContext(
            newCloudlet = cloudlets(1).first(),
            activeCloudlets = active,
            vmList = vms,
            currentTime = 0.0,
            nodeStates = states,
        )

    private fun nodeState(
        vmIndex: Int,
        accepting: Boolean,
        overrides: NodeStateOverrides = NodeStateOverrides(),
    ): RealtimeNodeState =
        RealtimeNodeState(
            vmIndex = vmIndex,
            vmId = vmIndex.toLong(),
            runningCount = 0,
            pendingCount = overrides.queueDepth,
            queueDepth = overrides.queueDepth,
            availableSlots = overrides.slots ?: if (accepting) Int.MAX_VALUE else 0,
            acceptingWork = accepting,
            estimatedLoad = overrides.queueDepth.toDouble(),
            availableTime = overrides.queueDepth.toDouble(),
            failurePressure = 0.0,
            topologyLatency = overrides.latency,
            failureDomainLoad = overrides.domainLoad,
        )

    private data class NodeStateOverrides(
        val queueDepth: Int = 0,
        val slots: Int? = null,
        val latency: Double = 0.0,
        val domainLoad: Int = 0,
    )
}

private class RealtimeSchedulerProbe(
    vms: List<Vm>,
) : RealtimeSchedulerBase(vms) {
    private val chooseLastCandidate: (List<RealtimeNodeState>, List<Cloudlet>) -> Int =
        { candidates, _ -> candidates.last().vmIndex }

    override fun scheduleOnArrival(context: RealtimeSchedulingContext): Int = fallbackCandidateVm(context)

    fun leastLoaded(cloudlets: List<Cloudlet>): Int = findLeastLoadedVm(cloudlets)

    fun ordered(context: RealtimeSchedulingContext): List<RealtimeNodeState> = orderedCandidateStates(context)

    fun optimize(context: RealtimeSchedulingContext): Int? = optimizedRealtimeVm(context, chooseLastCandidate)

    fun repair(
        context: RealtimeSchedulingContext,
        candidates: List<RealtimeNodeState>,
        optimizedIndex: Int,
    ): Int = optimizedCandidateVmIndex(context, candidates, optimizedIndex)

    fun fallback(context: RealtimeSchedulingContext): Int = fallbackCandidateVm(context)
}
