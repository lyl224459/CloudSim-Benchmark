package scheduler

import datacenter.ObjectiveFunction
import datacenter.SchedulerObjectiveFunction
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.cloudlets.CloudletSimple
import org.cloudsimplus.vms.Vm
import org.cloudsimplus.vms.VmSimple
import org.junit.jupiter.api.*
import org.assertj.core.api.Assertions.assertThat
import java.util.*
import org.cloudsimplus.utilizationmodels.UtilizationModelFull

/**
 * PSO算法测试
 */
class PSOTest {

    private lateinit var mockObjectiveFunction: ObjectiveFunction
    private lateinit var cloudlets: List<Cloudlet>
    private lateinit var vms: List<Vm>
    private val random = Random(42)

    @BeforeEach
    fun setUp() {
        // 创建模拟的云任务和虚拟机
        cloudlets = createMockCloudlets(10)
        vms = createMockVms(3)

        // 创建目标函数
        val objectiveFunction = SchedulerObjectiveFunction(cloudlets, vms, config.ObjectiveWeightsConfig())
        mockObjectiveFunction = objectiveFunction
    }

    @Test
    fun `should initialize PSO with correct parameters`() {
        // Given
        val population = 20
        val maxIter = 50
        val dim = cloudlets.size

        // When
        val pso = PSO(mockObjectiveFunction, population, 0.0, (vms.size - 1).toDouble(), dim, maxIter, random)

        // Then
        assertThat(pso).isNotNull()
    }

    @Test
    fun `should execute PSO and return valid result`() {
        // Given
        val population = 10
        val maxIter = 20
        val dim = cloudlets.size

        val pso = PSO(mockObjectiveFunction, population, 0.0, (vms.size - 1).toDouble(), dim, maxIter, random)

        // When
        val result = pso.execute()

        // Then
        assertThat(result).isNotNull()
        assertThat(result.size).isEqualTo(dim)

        // 检查结果在有效范围内
        for (allocation in result) {
            assertThat(allocation).isBetween(0, vms.size - 1)
        }
    }

    @Test
    fun `should handle edge case with single VM`() {
        // Given
        val singleVm = listOf(vms[0])
        val objectiveFunction = SchedulerObjectiveFunction(cloudlets, singleVm, config.ObjectiveWeightsConfig())
        val dim = cloudlets.size

        val pso = PSO(objectiveFunction, 10, 0.0, 0.0, dim, 10, random)

        // When
        val result = pso.execute()

        // Then
        assertThat(result).isNotNull()
        assertThat(result.size).isEqualTo(dim)

        // 所有分配都应该为0（只有一台VM）
        for (allocation in result) {
            assertThat(allocation).isEqualTo(0)
        }
    }

    @Test
    fun `should handle edge case with single cloudlet`() {
        // Given
        val singleCloudlet = listOf(cloudlets[0])
        val objectiveFunction = SchedulerObjectiveFunction(singleCloudlet, vms, config.ObjectiveWeightsConfig())
        val dim = singleCloudlet.size

        val pso = PSO(objectiveFunction, 10, 0.0, (vms.size - 1).toDouble(), dim, 10, random)

        // When
        val result = pso.execute()

        // Then
        assertThat(result).isNotNull()
        assertThat(result.size).isEqualTo(dim)
        assertThat(result[0]).isBetween(0, vms.size - 1)
    }

    @Test
    fun `should respect bounds in PSO execution`() {
        // Given
        val population = 15
        val maxIter = 30
        val dim = cloudlets.size
        val lb = 0.0
        val ub = (vms.size - 1).toDouble()

        val pso = PSO(mockObjectiveFunction, population, lb, ub, dim, maxIter, random)

        // When
        val result = pso.execute()

        // Then
        for (allocation in result) {
            assertThat(allocation.toDouble())
                .isGreaterThanOrEqualTo(lb)
                .isLessThanOrEqualTo(ub)
        }
    }

    @Test
    fun `should produce different results with different random seeds`() {
        // Given
        val population = 10
        val maxIter = 20
        val dim = cloudlets.size

        val pso1 = PSO(mockObjectiveFunction, population, 0.0, (vms.size - 1).toDouble(), dim, maxIter, Random(42))
        val pso2 = PSO(mockObjectiveFunction, population, 0.0, (vms.size - 1).toDouble(), dim, maxIter, Random(123))

        // When
        val result1 = pso1.execute()
        val result2 = pso2.execute()

        // Then
        // 不同的随机种子应该产生不同的结果（至少大部分不同）
        var differentCount = 0
        for (i in result1.indices) {
            if (result1[i] != result2[i]) {
                differentCount++
            }
        }
        assertThat(differentCount).isGreaterThan(0) // 至少有一些不同
    }

    // 辅助方法：创建模拟云任务
    private fun createMockCloudlets(count: Int): List<Cloudlet> {
        val utilizationModel = UtilizationModelFull()
        return (0 until count).map { i ->
            CloudletSimple(1000L + i * 100, 1)
                .setFileSize(100L)
                .setOutputSize(50L)
                .setUtilizationModelCpu(utilizationModel)
                .setUtilizationModelRam(utilizationModel)
                .setUtilizationModelBw(utilizationModel)
        }
    }

    // 辅助方法：创建模拟虚拟机
    private fun createMockVms(count: Int): List<Vm> {
        return (0 until count).map { i ->
            VmSimple(1000.0 + i * 500, 1)
                .setRam(2048L)
                .setBw(1000L)
                .setSize(10000L)
        }
    }
}
