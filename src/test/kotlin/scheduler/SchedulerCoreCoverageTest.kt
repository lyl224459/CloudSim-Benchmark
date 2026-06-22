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
    fun `realtime deadline baselines choose expected candidates`() {
        val vms = vms(3)
        val states =
            listOf(
                nodeState(0, accepting = true, NodeStateOverrides(availableTime = 0.1)),
                nodeState(1, accepting = true, NodeStateOverrides(availableTime = 0.6)),
                nodeState(2, accepting = true, NodeStateOverrides(availableTime = 0.0)),
            )
        val context =
            context(
                vms,
                active = cloudlets(3),
                states = states,
                taskMetadata =
                    RealtimeTaskRecord(
                        cloudletId = 1L,
                        originalArrivalTime = 0.0,
                        priority = 10,
                        deadline = 1.2,
                    ),
                preemptionCandidates =
                    listOf(
                        RealtimePreemptionCandidate(
                            victimCloudletId = CloudletId(99L),
                            victimVmIndex = VmIndex(1),
                            victimPriority = 1,
                            victimDeadline = 2.0,
                            preemptedCount = 0,
                        ),
                    ),
            )

        assertThat(RealtimeEdfScheduler(vms).scheduleOnArrival(context)).isEqualTo(2)
        assertThat(RealtimeLlfScheduler(vms).scheduleOnArrival(context)).isEqualTo(0)
        assertThat(RealtimeEftScheduler(vms).scheduleOnArrival(context)).isEqualTo(2)
        assertThat(RealtimeSrptScheduler(vms).scheduleOnArrival(context)).isEqualTo(2)
        assertThat(RealtimePriorityDeadlineScheduler(vms).scheduleOnArrival(context)).isEqualTo(1)
        assertThat(
            RealtimeLlfScheduler(vms).scheduleOnArrival(
                context.copy(taskMetadata = context.taskMetadata.copy(deadline = 0.5)),
            ),
        ).isEqualTo(2)
    }

    @Test
    fun `realtime deadline baselines do not select rejected candidates`() {
        val vms = vms(3)
        val context =
            context(
                vms,
                states =
                    listOf(
                        nodeState(0, accepting = false, NodeStateOverrides(availableTime = 0.0)),
                        nodeState(1, accepting = true, NodeStateOverrides(availableTime = 4.0)),
                        nodeState(2, accepting = false, NodeStateOverrides(availableTime = 0.1)),
                    ),
                taskMetadata = RealtimeTaskRecord(cloudletId = 1L, originalArrivalTime = 0.0, deadline = 5.0),
            )

        listOf(
            RealtimeEdfScheduler(vms),
            RealtimeLlfScheduler(vms),
            RealtimeEftScheduler(vms),
            RealtimeSrptScheduler(vms),
            RealtimePriorityDeadlineScheduler(vms),
        ).forEach { scheduler ->
            assertThat(scheduler.scheduleOnArrival(context)).isEqualTo(1)
        }
    }

    @Test
    fun `edf and llf fall back to eft without deadline`() {
        val vms = vms(3)
        val context =
            context(
                vms,
                states =
                    listOf(
                        nodeState(0, accepting = true, NodeStateOverrides(availableTime = 1.0)),
                        nodeState(1, accepting = true, NodeStateOverrides(availableTime = 0.4)),
                        nodeState(2, accepting = true, NodeStateOverrides(availableTime = 0.0)),
                    ),
            )
        val eftChoice = RealtimeEftScheduler(vms).scheduleOnArrival(context)

        assertThat(RealtimeEdfScheduler(vms).scheduleOnArrival(context)).isEqualTo(eftChoice)
        assertThat(RealtimeLlfScheduler(vms).scheduleOnArrival(context)).isEqualTo(eftChoice)
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
        newCloudlet: Cloudlet = cloudlets(1).first(),
        taskMetadata: RealtimeTaskMetadata =
            RealtimeTaskRecord(
                cloudletId = newCloudlet.id,
                originalArrivalTime = newCloudlet.submissionDelay,
            ),
        preemptionCandidates: List<RealtimePreemptionCandidate> = emptyList(),
    ): RealtimeSchedulingContext =
        RealtimeSchedulingContext(
            newCloudlet = newCloudlet,
            activeCloudlets = active,
            vmList = vms,
            currentTime = 0.0,
            nodeStates = states,
            taskMetadata = taskMetadata,
            preemptionCandidates = preemptionCandidates,
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
            estimatedLoad = overrides.estimatedLoad ?: overrides.queueDepth.toDouble(),
            availableTime = overrides.availableTime ?: overrides.queueDepth.toDouble(),
            failurePressure = 0.0,
            resourcePressure = overrides.resourcePressure,
            topologyLatency = overrides.latency,
            topologyCost = overrides.topologyCost,
            failureDomainLoad = overrides.domainLoad,
        )

    private data class NodeStateOverrides(
        val queueDepth: Int = 0,
        val slots: Int? = null,
        val estimatedLoad: Double? = null,
        val availableTime: Double? = null,
        val latency: Double = 0.0,
        val topologyCost: Double = 0.0,
        val domainLoad: Int = 0,
        val resourcePressure: Double = 0.0,
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
