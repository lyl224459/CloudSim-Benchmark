package scheduler

import config.ObjectiveWeightsConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.cloudlets.CloudletSimple
import org.cloudsimplus.utilizationmodels.UtilizationModelFull
import org.cloudsimplus.vms.Vm
import org.cloudsimplus.vms.VmSimple
import org.junit.jupiter.api.Test
import java.util.Random

class SchedulerOptimizationTest {

    @Test
    fun `scheduler rejects empty vm list before allocation`() {
        assertThatThrownBy {
            object : Scheduler(createCloudlets(1), emptyList()) {
                override fun allocate(): IntArray = intArrayOf(0)
            }
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("至少 1 台可用 VM")
    }

    @Test
    fun `scheduler rejects allocation length mismatch`() {
        val scheduler = object : Scheduler(createCloudlets(2), createVms(1)) {
            override fun allocate(): IntArray = intArrayOf(0)
        }

        assertThatThrownBy { scheduler.schedule() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("分配数量")
    }

    @Test
    fun `scheduler rejects negative vm index`() {
        val scheduler = object : Scheduler(createCloudlets(1), createVms(1)) {
            override fun allocate(): IntArray = intArrayOf(-1)
        }

        assertThatThrownBy { scheduler.schedule() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("非法 VM 下标")
    }

    @Test
    fun `scheduler rejects out of range vm index`() {
        val scheduler = object : Scheduler(createCloudlets(1), createVms(1)) {
            override fun allocate(): IntArray = intArrayOf(1)
        }

        assertThatThrownBy { scheduler.schedule() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("可用范围")
    }

    @Test
    fun `algorithm registry resolves aliases by mode`() {
        assertThat(AlgorithmRegistry.resolve(AlgorithmMode.BATCH, "Improved-RL").name)
            .isEqualTo("IMPROVED_RL")
        assertThat(AlgorithmRegistry.resolve(AlgorithmMode.REALTIME, "MINLOAD").name)
            .isEqualTo("MIN_LOAD")
        assertThat(AlgorithmRegistry.resolve(AlgorithmMode.REALTIME, "PSO").name)
            .isEqualTo("PSO_REALTIME")
    }

    @Test
    fun `algorithm registry rejects ALL mixed with other names`() {
        assertThatThrownBy {
            AlgorithmRegistry.resolveAll(AlgorithmMode.BATCH, listOf("ALL", "PSO"))
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("ALL")
    }

    @Test
    fun `assignment vector codec clamps and rounds allocations`() {
        val codec = AssignmentVectorCodec(dim = 4, lowerBound = 0, upperBound = 2)
        val allocation = codec.toAllocation(doubleArrayOf(-1.2, 0.49, 1.51, 9.0))

        assertThat(allocation.toList()).containsExactly(0, 0, 2, 2)
    }

    @Test
    fun `assignment vector codec handles single vm bounds`() {
        val codec = AssignmentVectorCodec(dim = 3, lowerBound = 0, upperBound = 0)

        assertThat(codec.toAllocation(doubleArrayOf(-1.0, 0.4, 5.0)).toList())
            .containsExactly(0, 0, 0)
    }

    @Test
    fun `realtime optimizer factories pass objective weights`() {
        val weights = ObjectiveWeightsConfig(cost = 0.1, totalTime = 0.2, loadBalance = 0.3, makespan = 0.4)
        val settings = ResolvedAlgorithmSettings(population = 3, maxIter = 2)
        val vms = createVms(2)

        val pso = AlgorithmRegistry.resolve(AlgorithmMode.REALTIME, "PSO")
            .realtimeFactory!!
            .invoke(vms, weights, settings, 42L)
        val woa = AlgorithmRegistry.resolve(AlgorithmMode.REALTIME, "WOA")
            .realtimeFactory!!
            .invoke(vms, weights, settings, 42L)

        assertThat((pso as RealtimePSOScheduler).objectiveWeights).isEqualTo(weights)
        assertThat((woa as RealtimeWOAScheduler).objectiveWeights).isEqualTo(weights)
    }

    @Test
    fun `realtime optimizers use lightweight path below threshold`() {
        val scheduler = RealtimePSOScheduler(
            createVms(2),
            population = 3,
            maxIter = 2,
            objectiveWeights = ObjectiveWeightsConfig(),
            random = Random(42)
        )
        val newCloudlet = createCloudlets(1).first()

        val vmIndex = scheduler.scheduleOnArrival(newCloudlet, emptyList(), createVms(2))

        assertThat(vmIndex).isBetween(0, 1)
    }

    @Test
    fun `metaheuristic schedulers return valid allocations`() {
        val cloudlets = createCloudlets(6)
        val vms = createVms(3)

        val schedulers = listOf(
            PSOScheduler(cloudlets, vms, population = 4, maxIter = 3, random = Random(42)),
            WOAScheduler(cloudlets, vms, population = 4, maxIter = 3, random = Random(42)),
            GWOScheduler(cloudlets, vms, population = 4, maxIter = 3, random = Random(42)),
            HHOScheduler(cloudlets, vms, population = 4, maxIter = 3, random = Random(42))
        )

        for (scheduler in schedulers) {
            val allocation = scheduler.allocate()
            assertThat(allocation.size).isEqualTo(cloudlets.size)
            assertThat(allocation.toList()).allSatisfy { vmIndex ->
                assertThat(vmIndex).isBetween(0, vms.lastIndex)
            }
        }
    }

    private fun createCloudlets(count: Int): List<Cloudlet> {
        val utilizationModel = UtilizationModelFull()
        return (0 until count).map { index ->
            CloudletSimple(1000L + index * 100L, 1)
                .setFileSize(100)
                .setOutputSize(50)
                .setUtilizationModelCpu(utilizationModel)
                .setUtilizationModelRam(utilizationModel)
                .setUtilizationModelBw(utilizationModel)
        }
    }

    private fun createVms(count: Int): List<Vm> {
        return (0 until count).map { index ->
            VmSimple(1000.0 + index * 250.0, 1)
                .setRam(1024)
                .setBw(1000)
                .setSize(10000)
        }
    }
}
