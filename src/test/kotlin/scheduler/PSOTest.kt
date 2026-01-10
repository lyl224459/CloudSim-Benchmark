package scheduler

import datacenter.ObjectiveFunction
import datacenter.SchedulerObjectiveFunction
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import org.junit.jupiter.api.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import java.util.*

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
        // PSO是内部类，无法直接测试内部状态，但至少能创建实例
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
            assertThat(allocation).isBetween(0.0, (vms.size - 1).toDouble())
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
            assertThat(allocation).isEqualTo(0.0)
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
        assertThat(result[0]).isBetween(0.0, (vms.size - 1).toDouble())
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
            assertThat(allocation)
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
            if (Math.abs(result1[i] - result2[i]) > 0.1) {
                differentCount++
            }
        }
        assertThat(differentCount).isGreaterThan(0) // 至少有一些不同
    }

    // 辅助方法：创建模拟云任务
    private fun createMockCloudlets(count: Int): List<Cloudlet> {
        return (0 until count).map { i ->
            object : Cloudlet {
                override fun getId(): Int = i
                override fun getLength(): Long = 1000L + i * 100
                override fun setLength(length: Long) {}
                override fun getFileSize(): Long = 100L
                override fun setFileSize(fileSize: Long) {}
                override fun getOutputSize(): Long = 50L
                override fun setOutputSize(outputSize: Long) {}
                override fun getVm(): Vm? = null
                override fun setVm(vm: Vm?) {}
                override fun getSubmissionTime(): Double = 0.0
                override fun setSubmissionTime(submissionTime: Double) {}
                override fun getExecStartTime(): Double = 0.0
                override fun getFinishTime(): Double = 0.0
                override fun getActualCpuTime(): Double = 0.0
                override fun getCostPerSec(): Double = 0.0
                override fun getTotalCost(): Double = 0.0
                override fun getStatus(): Int = 0
                override fun setStatus(status: Int) {}
                override fun isFinished(): Boolean = false
                override fun addRequiredFile(file: String?) {}
                override fun deleteRequiredFile(file: String?) {}
                override fun getRequiredFiles(): MutableList<String> = mutableListOf()
                override fun addRequiredFiles(files: MutableList<String>?) {}
                override fun getPriority(): Int = 0
                override fun setPriority(priority: Int) {}
                override fun getNumberOfPes(): Int = 1
                override fun setNumberOfPes(numberOfPes: Int) {}
                override fun getUtilizationModelCpu(): org.cloudsimplus.utilizationmodels.UtilizationModel? = null
                override fun setUtilizationModelCpu(utilizationModelCpu: org.cloudsimplus.utilizationmodels.UtilizationModel?) {}
                override fun getUtilizationModelRam(): org.cloudsimplus.utilizationmodels.UtilizationModel? = null
                override fun setUtilizationModelRam(utilizationModelRam: org.cloudsimplus.utilizationmodels.UtilizationModel?) {}
                override fun getUtilizationModelBw(): org.cloudsimplus.utilizationmodels.UtilizationModel? = null
                override fun setUtilizationModelBw(utilizationModelBw: org.cloudsimplus.utilizationmodels.UtilizationModel?) {}
                override fun getUtilizationOfCpu(): Double = 0.0
                override fun getUtilizationOfRam(): Double = 0.0
                override fun getUtilizationOfBw(): Double = 0.0
                override fun getUtilizationModel(): org.cloudsimplus.utilizationmodels.UtilizationModel = object : org.cloudsimplus.utilizationmodels.UtilizationModel {
                    override fun getUtilization(time: Double): Double = 1.0
                }
                override fun setUtilizationModel(utilizationModel: org.cloudsimplus.utilizationmodels.UtilizationModel?) {}
            }
        }
    }

    // 辅助方法：创建模拟虚拟机
    private fun createMockVms(count: Int): List<Vm> {
        return (0 until count).map { i ->
            object : Vm {
                override fun getId(): Int = i
                override fun getMips(): Double = 1000.0 + i * 500
                override fun setMips(mips: Double) {}
                override fun getNumberOfPes(): Int = 1
                override fun setNumberOfPes(numberOfPes: Int) {}
                override fun getRam(): Int = 2048
                override fun setRam(ram: Int) {}
                override fun getBw(): Long = 1000L
                override fun setBw(bw: Long) {}
                override fun getSize(): Long = 10000L
                override fun setSize(size: Long) {}
                override fun getHost(): org.cloudsimplus.hosts.Host? = null
                override fun setHost(host: org.cloudsimplus.hosts.Host?) {}
                override fun getCloudletScheduler(): org.cloudsimplus.schedulers.cloudlet.CloudletScheduler? = null
                override fun setCloudletScheduler(cloudletScheduler: org.cloudsimplus.schedulers.cloudlet.CloudletScheduler?) {}
                override fun getTotalCost(): Double = 0.0
                override fun getProcessingCost(): Double = 0.0
                override fun getMemoryCost(): Double = 0.0
                override fun getStorageCost(): Double = 0.0
                override fun getBwCost(): Double = 0.0
                override fun getCurrentAllocatedSize(): Long = 0L
                override fun getCurrentAllocatedRam(): Int = 0
                override fun getCurrentAllocatedBw(): Long = 0L
                override fun getCurrentAllocatedMips(): Double = 0.0
                override fun getCurrentRequestedMips(): Double = 0.0
                override fun getCurrentRequestedRam(): Int = 0
                override fun getCurrentRequestedBw(): Long = 0L
                override fun getCurrentRequestedSize(): Long = 0L
                override fun isCreated(): Boolean = true
                override fun isSuitableForCloudlet(cloudlet: Cloudlet?): Boolean = true
                override fun updateProcessing(mipsShare: Double, currentTime: Double) {}
                override fun getStartTime(): Double = 0.0
                override fun getTotalExecutionTime(): Double = 0.0
                override fun getCpuPercentUsage(): Double = 0.0
                override fun getCpuPercentRequested(): Double = 0.0
                override fun getRamPercentUsage(): Double = 0.0
                override fun getRamPercentRequested(): Double = 0.0
                override fun getBwPercentUsage(): Double = 0.0
                override fun getBwPercentRequested(): Double = 0.0
                override fun getStateHistory(): MutableList<org.cloudsimplus.core.SimEntityState> = mutableListOf()
                override fun getDescription(): String = "Mock VM $i"
                override fun setDescription(description: String) {}
                override fun getVmm(): String = "Xen"
                override fun setVmm(vmm: String) {}
            }
        }
    }
}