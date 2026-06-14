package datacenter

import config.CloudletGeneratorType
import config.GoogleTraceConfig
import datacenter.generator.CloudletGeneratorFactory
import datacenter.generator.GoogleTraceCloudletGenerator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.Random

/**
 * 任务生成器测试
 */
class GeneratorTest {
    private val random = Random(42)

    @Test
    fun `should create log normal generator correctly`() {
        // Given
        val generator =
            CloudletGeneratorFactory.createGenerator(
                CloudletGeneratorType.LOG_NORMAL,
                null,
            )

        // When - Then
        assertThat(generator).isNotNull()
    }

    @Test
    fun `should create uniform generator correctly`() {
        // Given
        val generator =
            CloudletGeneratorFactory.createGenerator(
                CloudletGeneratorType.UNIFORM,
                null,
            )

        // When - Then
        assertThat(generator).isNotNull()
    }

    @Test
    fun `should create google trace generator correctly`() {
        // Given
        val googleTraceConfig =
            GoogleTraceConfig(
                filePath = "test/path.csv",
                maxTasks = 100,
                timeWindowStart = 0L,
                timeWindowEnd = Long.MAX_VALUE,
            )

        // When
        val generator =
            CloudletGeneratorFactory.createGenerator(
                CloudletGeneratorType.GOOGLE_TRACE,
                googleTraceConfig,
            )

        // Then
        assertThat(generator).isNotNull()
    }

    @Test
    fun `should generate log normal cloudlets with correct properties`() {
        // Given
        val generator =
            CloudletGeneratorFactory.createGenerator(
                CloudletGeneratorType.LOG_NORMAL,
                null,
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
        val generator =
            CloudletGeneratorFactory.createGenerator(
                CloudletGeneratorType.UNIFORM,
                null,
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
        val generator =
            CloudletGeneratorFactory.createGenerator(
                CloudletGeneratorType.LOG_NORMAL,
                null,
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
        val generator =
            CloudletGeneratorFactory.createGenerator(
                CloudletGeneratorType.LOG_NORMAL,
                null,
            )

        // When
        val cloudlets = generator.createCloudlets(0, 0, random)

        // Then
        assertThat(cloudlets).isEmpty()
    }

    @Test
    fun `google trace generator falls back when trace file is missing`() {
        val generator =
            GoogleTraceCloudletGenerator(
                traceFilePath = "build/tmp/missing-google-trace.csv",
                maxTasks = 5,
            )

        val specs = generator.createCloudletSpecs(userId = 0, count = 3, random)

        assertThat(specs).hasSize(3)
        assertThat(specs).allSatisfy { spec ->
            assertThat(spec.cloudlet.length).isGreaterThan(0L)
            assertThat(spec.traceMetadata).isNotNull()
        }
    }

    @Test
    fun `google trace generator falls back when trace file is empty`() {
        val traceFile =
            Files.createTempFile("google-trace-empty-", ".csv").toFile().apply {
                writeText("")
            }

        val specs =
            try {
                GoogleTraceCloudletGenerator(
                    traceFilePath = traceFile.absolutePath,
                    maxTasks = 5,
                ).createCloudletSpecs(userId = 0, count = 2, random)
            } finally {
                traceFile.delete()
            }

        assertThat(specs).hasSize(2)
        assertThat(specs).allSatisfy { spec ->
            assertThat(spec.cloudlet.length).isGreaterThan(0L)
            assertThat(spec.traceMetadata).isNotNull()
        }
    }

    @Test
    fun `google trace generator skips malformed rows and keeps valid schedule rows`() {
        val traceFile =
            Files.createTempFile("google-trace-bad-rows-", ".csv").toFile().apply {
                writeText(
                    """
                    bad,row
                    not_a_number,2,3,4,0,user,1,5,0.5,0.2,1.0,false,extra
                    10,20,1,30,0,user-a,1,7,0.5,0.25,2.0,false,extra
                    11,21,2,31,2,user-b,1,7,0.5,0.25,2.0,false,extra
                    """.trimIndent(),
                )
            }

        val specs =
            try {
                GoogleTraceCloudletGenerator(
                    traceFilePath = traceFile.absolutePath,
                    maxTasks = 10,
                    timeWindowStart = 0L,
                    timeWindowEnd = 10L,
                ).createCloudletSpecs(userId = 0, count = 3, random)
            } finally {
                traceFile.delete()
            }

        assertThat(specs).hasSize(1)
        val metadata = specs.single().traceMetadata
        assertThat(metadata?.tenantKey).isEqualTo("user-a")
        assertThat(metadata?.priority).isEqualTo(7)
        assertThat(metadata?.requestedCpu).isEqualTo(0.5)
        assertThat(metadata?.requestedRam).isEqualTo(0.25)
        assertThat(metadata?.requestedBw).isEqualTo(500.0)
        assertThat(metadata?.requestedIo).isEqualTo(2.0)
        assertThat(metadata?.inputDataSize).isEqualTo(2.0)
        assertThat(metadata?.imageId).isEqualTo("trace-image-4")
    }
}
