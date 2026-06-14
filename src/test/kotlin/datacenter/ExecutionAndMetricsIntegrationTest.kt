package datacenter

import broker.RealtimeBroker
import config.BatchConfig
import config.ObjectiveWeightsConfig
import config.RealtimeSchedulingConfig
import org.assertj.core.api.Assertions.assertThat
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.cloudlets.CloudletSimple
import org.cloudsimplus.core.CloudSimPlus
import org.cloudsimplus.utilizationmodels.UtilizationModelFull
import org.cloudsimplus.vms.Vm
import org.cloudsimplus.vms.VmSimple
import org.junit.jupiter.api.Test
import scheduler.RandomScheduler
import scheduler.RealtimeMinLoadScheduler
import java.util.Random

class ExecutionAndMetricsIntegrationTest {
    @Test
    fun `batch executor produces finite metrics for a small fixed-seed simulation`() {
        val config = BatchConfig(cloudletCount = 4)
        val result =
            BatchAlgorithmExecutor(config).run("Random", 42L) { cloudlets, vms ->
                RandomScheduler(cloudlets, vms, config.objectiveWeights, Random(42L))
            }

        assertThat(result.algorithmName).isEqualTo("Random")
        assertThat(
            listOf(result.makespan, result.loadBalance, result.cost, result.totalTime, result.fitness),
        ).allSatisfy { value -> assertThat(value.isFinite()).isTrue() }
    }

    @Test
    fun `realtime metrics handle failed task and non-contiguous vm ids`() {
        val vmList = createVms()
        val broker =
            RealtimeBroker(
                CloudSimPlus(),
                RealtimeMinLoadScheduler(vmList),
                vmList,
                RealtimeSchedulingConfig(),
            )
        val failed =
            createCloudlet().also {
                it.setVm(vmList[1])
                it.setStatus(Cloudlet.Status.FAILED)
            }
        val collector = RealtimeMetricsCollector(RealtimeSchedulingConfig(), ObjectiveWeightsConfig())

        val result =
            collector.collect(
                RealtimeMetricCollectionRequest(
                    algorithmName = "MinLoad",
                    cloudletList = listOf(failed),
                    finishedCloudlets = listOf(failed),
                    vmList = vmList,
                    broker = broker,
                ),
            )

        assertThat(result.failedCount).isEqualTo(1)
        assertThat(result.completedCount).isZero()
        assertThat(result.fitness.isFinite()).isTrue()
        assertThat(result.metrics.values.keys).containsAll(RealtimeMetricKey.entries)
    }

    private fun createVms(): List<Vm> =
        listOf(
            VmSimple(1_000.0, 1).also { it.setId(10) },
            VmSimple(2_000.0, 1).also { it.setId(11) },
        )

    private fun createCloudlet(): Cloudlet {
        val utilization = UtilizationModelFull()
        return CloudletSimple(1_000, 1).apply {
            setId(7)
            setFileSize(100)
            setOutputSize(100)
            setUtilizationModelCpu(utilization)
            setUtilizationModelRam(utilization)
            setUtilizationModelBw(utilization)
        }
    }
}
