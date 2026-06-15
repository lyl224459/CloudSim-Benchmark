package datacenter

import broker.RealtimeBroker
import config.BatchConfig
import config.DatacenterConfig
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import scheduler.RandomScheduler
import scheduler.RealtimeMinLoadScheduler
import java.util.Random
import kotlin.test.assertFailsWith

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

    @Test
    fun `batch metric calculator handles cost tiers failed tasks and unknown vm ids`() {
        val low = metricVm(10, DatacenterConfig.L_MIPS.toDouble())
        val medium = metricVm(11, DatacenterConfig.M_MIPS.toDouble())
        val high = metricVm(12, DatacenterConfig.H_MIPS.toDouble())
        val unknown = metricVm(99, 123.0)
        val metrics =
            BatchExecutionMetricsCalculator.calculate(
                listOf(
                    metricCloudlet(low, 2.0, 4.0),
                    metricCloudlet(medium, 3.0, 7.0),
                    metricCloudlet(high, 4.0, 6.0),
                    metricCloudlet(unknown, 5.0, 8.0),
                    metricCloudlet(low, 100.0, 100.0, Cloudlet.Status.FAILED),
                ),
                listOf(low, medium, high),
            )

        assertThat(metrics.makespan).isEqualTo(8.0)
        assertThat(metrics.cost).isEqualTo(
            2.0 * DatacenterConfig.L_PRICE +
                3.0 * DatacenterConfig.M_PRICE +
                4.0 * DatacenterConfig.H_PRICE +
                5.0 * DatacenterConfig.L_PRICE,
        )
        assertThat(metrics.loadBalance).isFinite()
    }

    @Test
    fun `batch executor propagates scheduler factory failure`() {
        assertFailsWith<IllegalStateException> {
            BatchAlgorithmExecutor(BatchConfig(cloudletCount = 1)).run("broken", 1L) { _, _ ->
                error("scheduler factory failed")
            }
        }
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

    private fun metricVm(
        id: Long,
        mips: Double,
    ): Vm =
        mock {
            on { this.id } doReturn id
            on { this.mips } doReturn mips
        }

    private fun metricCloudlet(
        vm: Vm,
        executionTime: Double,
        finishTime: Double,
        status: Cloudlet.Status = Cloudlet.Status.SUCCESS,
    ): Cloudlet =
        mock {
            on { this.status } doReturn status
            on { this.finishTime } doReturn finishTime
            on { getTotalExecutionTime() } doReturn executionTime
            on { this.vm } doReturn vm
        }
}
