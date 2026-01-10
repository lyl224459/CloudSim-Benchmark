package datacenter

import datacenter.generator.*
import org.junit.jupiter.api.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import java.io.File
import java.util.*

/**
 * 任务生成器测试
 */
class GeneratorTest {

    private val random = Random(42)
    private lateinit var vmList: List<org.cloudsimplus.vms.Vm>

    @BeforeEach
    fun setUp() {
        vmList = createMockVms(3)
    }

    @Test
    fun `should create log normal generator correctly`() {
        // Given
        val generator = CloudletGeneratorFactory.createGenerator(
            config.CloudletGeneratorType.LOG_NORMAL,
            10,
            random,
            null
        )

        // When - Then
        assertThat(generator).isInstanceOf(LogNormalCloudletGenerator::class.java)
    }

    @Test
    fun `should create uniform generator correctly`() {
        // Given
        val generator = CloudletGeneratorFactory.createGenerator(
            config.CloudletGeneratorType.UNIFORM,
            10,
            random,
            null
        )

        // When - Then
        assertThat(generator).isInstanceOf(UniformCloudletGenerator::class.java)
    }

    @Test
    fun `should create google trace generator correctly`() {
        // Given
        val googleTraceConfig = config.GoogleTraceConfig(
            filePath = "test/path.csv",
            maxTasks = 100,
            timeWindowStart = 0L,
            timeWindowEnd = Long.MAX_VALUE
        )

        // When
        val generator = CloudletGeneratorFactory.createGenerator(
            config.CloudletGeneratorType.GOOGLE_TRACE,
            10,
            random,
            googleTraceConfig
        )

        // Then
        assertThat(generator).isInstanceOf(GoogleTraceCloudletGenerator::class.java)
    }

    @Test
    fun `should throw exception for unsupported generator type`() {
        // Given - When - Then
        assertThatThrownBy {
            CloudletGeneratorFactory.createGenerator(
                config.CloudletGeneratorType.valueOf("UNSUPPORTED"),
                10,
                random,
                null
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("不支持的任务生成器类型")
    }

    @Test
    fun `should generate log normal cloudlets with correct properties`() {
        // Given
        val generator = CloudletGeneratorFactory.createGenerator(
            config.CloudletGeneratorType.LOG_NORMAL,
            5,
            Random(42),
            null
        ) as LogNormalCloudletGenerator

        // When
        val cloudlets = generator.generateCloudlets()

        // Then
        assertThat(cloudlets).hasSize(5)

        for (cloudlet in cloudlets) {
            assertThat(cloudlet.length).isGreaterThan(0L)
            assertThat(cloudlet.fileSize).isGreaterThanOrEqualTo(0L)
            assertThat(cloudlet.outputSize).isGreaterThanOrEqualTo(0L)
            assertThat(cloudlet.id).isGreaterThanOrEqualTo(0)
        }
    }

    @Test
    fun `should generate uniform cloudlets with correct properties`() {
        // Given
        val generator = CloudletGeneratorFactory.createGenerator(
            config.CloudletGeneratorType.UNIFORM,
            5,
            Random(42),
            null
        ) as UniformCloudletGenerator

        // When
        val cloudlets = generator.generateCloudlets()

        // Then
        assertThat(cloudlets).hasSize(5)

        for (cloudlet in cloudlets) {
            assertThat(cloudlet.length).isGreaterThan(0L)
            assertThat(cloudlet.fileSize).isGreaterThanOrEqualTo(0L)
            assertThat(cloudlet.outputSize).isGreaterThanOrEqualTo(0L)
            assertThat(cloudlet.id).isGreaterThanOrEqualTo(0)
        }
    }

    @Test
    fun `should generate cloudlets with utilization models`() {
        // Given
        val generator = CloudletGeneratorFactory.createGenerator(
            config.CloudletGeneratorType.LOG_NORMAL,
            3,
            Random(42),
            null
        )

        // When
        val cloudlets = generator.generateCloudlets()

        // Then
        for (cloudlet in cloudlets) {
            assertThat(cloudlet.utilizationModelCpu).isNotNull()
            assertThat(cloudlet.utilizationModelRam).isNotNull()
            assertThat(cloudlet.utilizationModelBw).isNotNull()
        }
    }

    @Test
    fun `should generate cloudlets with unique IDs`() {
        // Given
        val generator = CloudletGeneratorFactory.createGenerator(
            config.CloudletGeneratorType.LOG_NORMAL,
            10,
            Random(42),
            null
        )

        // When
        val cloudlets = generator.generateCloudlets()

        // Then
        val ids = cloudlets.map { it.id }
        assertThat(ids).doesNotHaveDuplicates()
        assertThat(ids.minOrNull()).isGreaterThanOrEqualTo(0)
    }

    @Test
    fun `should handle zero cloudlet count gracefully`() {
        // Given
        val generator = CloudletGeneratorFactory.createGenerator(
            config.CloudletGeneratorType.LOG_NORMAL,
            0,
            Random(42),
            null
        )

        // When
        val cloudlets = generator.generateCloudlets()

        // Then
        assertThat(cloudlets).isEmpty()
    }

    @Test
    fun `should generate cloudlets with reasonable length values for log normal`() {
        // Given
        val generator = CloudletGeneratorFactory.createGenerator(
            config.CloudletGeneratorType.LOG_NORMAL,
            20,
            Random(42),
            null
        )

        // When
        val cloudlets = generator.generateCloudlets()

        // Then
        val lengths = cloudlets.map { it.length.toDouble() }
        val mean = lengths.average()
        val stdDev = calculateStdDev(lengths)

        // 对数正态分布的值应该在合理范围内
        assertThat(mean).isGreaterThan(0.0)
        assertThat(stdDev).isGreaterThan(0.0)

        // 大部分值应该在均值±3倍标准差范围内
        val reasonableMin = mean - 3 * stdDev
        val reasonableMax = mean + 3 * stdDev

        val reasonableCount = lengths.count { it in reasonableMin..reasonableMax }
        assertThat(reasonableCount.toDouble() / lengths.size)
            .isGreaterThan(0.8) // 至少80%的值在合理范围内
    }

    @Test
    fun `should generate cloudlets with reasonable length values for uniform`() {
        // Given
        val generator = CloudletGeneratorFactory.createGenerator(
            config.CloudletGeneratorType.UNIFORM,
            20,
            Random(42),
            null
        )

        // When
        val cloudlets = generator.generateCloudlets()

        // Then
        val lengths = cloudlets.map { it.length.toDouble() }
        val min = lengths.minOrNull() ?: 0.0
        val max = lengths.maxOrNull() ?: 0.0

        // 均匀分布的值应该在合理范围内
        assertThat(min).isGreaterThan(0.0)
        assertThat(max).isGreaterThan(min)

        // 检查分布是否相对均匀（简单检查）
        val range = max - min
        val expectedMean = min + range / 2
        val actualMean = lengths.average()

        assertThat(actualMean).isCloseTo(expectedMean, within(range * 0.3)) // 在30%的范围内
    }

    @Test
    fun `should handle google trace generator with missing file gracefully`() {
        // Given
        val googleTraceConfig = config.GoogleTraceConfig(
            filePath = "nonexistent/file.csv",
            maxTasks = 10,
            timeWindowStart = 0L,
            timeWindowEnd = Long.MAX_VALUE
        )

        // When - Then
        assertThatThrownBy {
            val generator = CloudletGeneratorFactory.createGenerator(
                config.CloudletGeneratorType.GOOGLE_TRACE,
                10,
                random,
                googleTraceConfig
            )
            generator.generateCloudlets()
        }.isInstanceOf(Exception::class.java)
    }

    // 辅助方法：计算标准差
    private fun calculateStdDev(values: List<Double>): Double {
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return Math.sqrt(variance)
    }

    // 辅助方法：创建模拟虚拟机
    private fun createMockVms(count: Int): List<org.cloudsimplus.vms.Vm> {
        return (0 until count).map { i ->
            object : org.cloudsimplus.vms.Vm {
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
                override fun isSuitableForCloudlet(cloudlet: org.cloudsimplus.cloudlets.Cloudlet?): Boolean = true
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