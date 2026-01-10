package datacenter

import datacenter.generator.CloudletGeneratorFactory
import org.junit.jupiter.api.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import java.util.*
import config.CloudletGeneratorType
import config.GoogleTraceConfig

/**
 * 任务生成器测试
 */
class GeneratorTest {

    private val random = Random(42)

    @Test
    fun `should create log normal generator correctly`() {
        // Given
        val generator = CloudletGeneratorFactory.createGenerator(
            CloudletGeneratorType.LOG_NORMAL,
            random,
            null
        )

        // When - Then
        assertThat(generator).isNotNull()
    }

    @Test
    fun `should create uniform generator correctly`() {
        // Given
        val generator = CloudletGeneratorFactory.createGenerator(
            CloudletGeneratorType.UNIFORM,
            random,
            null
        )

        // When - Then
        assertThat(generator).isNotNull()
    }

    @Test
    fun `should create google trace generator correctly`() {
        // Given
        val googleTraceConfig = GoogleTraceConfig(
            filePath = "test/path.csv",
            maxTasks = 100,
            timeWindowStart = 0L,
            timeWindowEnd = Long.MAX_VALUE
        )

        // When
        val generator = CloudletGeneratorFactory.createGenerator(
            CloudletGeneratorType.GOOGLE_TRACE,
            random,
            googleTraceConfig
        )

        // Then
        assertThat(generator).isNotNull()
    }

    @Test
    fun `should generate log normal cloudlets with correct properties`() {
        // Given
        val generator = CloudletGeneratorFactory.createGenerator(
            CloudletGeneratorType.LOG_NORMAL,
            random,
            null
        )

        // When
        val cloudlets = generator.createCloudlets(0, 5, random)

        // Then
        assertThat(cloudlets).hasSize(5)

        for (cloudlet in cloudlets) {
            assertThat(cloudlet.length).isGreaterThan(0L)
            assertThat(cloudlet.fileSize).isGreaterThanOrEqualTo(0L)
            assertThat(cloudlet.outputSize).isGreaterThanOrEqualTo(0L)
        }
    }

    @Test
    fun `should generate uniform cloudlets with correct properties`() {
        // Given
        val generator = CloudletGeneratorFactory.createGenerator(
            CloudletGeneratorType.UNIFORM,
            random,
            null
        )

        // When
        val cloudlets = generator.createCloudlets(0, 5, random)

        // Then
        assertThat(cloudlets).hasSize(5)

        for (cloudlet in cloudlets) {
            assertThat(cloudlet.length).isGreaterThan(0L)
            assertThat(cloudlet.fileSize).isGreaterThanOrEqualTo(0L)
            assertThat(cloudlet.outputSize).isGreaterThanOrEqualTo(0L)
        }
    }

    @Test
    fun `should generate cloudlets with utilization models`() {
        // Given
        val generator = CloudletGeneratorFactory.createGenerator(
            CloudletGeneratorType.LOG_NORMAL,
            random,
            null
        )

        // When
        val cloudlets = generator.createCloudlets(0, 3, random)

        // Then
        for (cloudlet in cloudlets) {
            assertThat(cloudlet.utilizationModelCpu).isNotNull()
            assertThat(cloudlet.utilizationModelRam).isNotNull()
            assertThat(cloudlet.utilizationModelBw).isNotNull()
        }
    }

    @Test
    fun `should handle zero cloudlet count gracefully`() {
        // Given
        val generator = CloudletGeneratorFactory.createGenerator(
            CloudletGeneratorType.LOG_NORMAL,
            random,
            null
        )

        // When
        val cloudlets = generator.createCloudlets(0, 0, random)

        // Then
        assertThat(cloudlets).isEmpty()
    }
}
