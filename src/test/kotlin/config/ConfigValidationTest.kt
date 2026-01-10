package config

import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy

/**
 * 配置验证测试
 */
class ConfigValidationTest {

    @Test
    fun `should create valid default config`() {
        // Given
        val config = ExperimentConfig.createDefault()

        // When - Then
        assertThat(config.batch.cloudletCount).isGreaterThan(0)
        assertThat(config.batch.population).isGreaterThan(0)
        assertThat(config.batch.maxIter).isGreaterThan(0)
        assertThat(config.realtime.cloudletCount).isGreaterThan(0)
        assertThat(config.realtime.simulationDuration).isGreaterThan(0.0)
        assertThat(config.optimizer.population).isGreaterThan(0)
    }

    @Test
    fun `should validate batch config parameters`() {
        // Given
        val invalidConfig = ExperimentConfig.createDefault().copy(
            batch = ExperimentConfig.createDefault().batch.copy(
                cloudletCount = -1,
                population = 0,
                maxIter = -5,
                runs = 0
            )
        )

        // When - Then
        try {
            ExperimentConfig.validate(invalidConfig)
            fail("Expected ConfigValidationException")
        } catch (e: ConfigValidationException) {
            assertThat(e.errors.any { it.message.contains("批处理任务数必须大于0") }).isTrue()
            assertThat(e.errors.any { it.message.contains("批处理种群大小必须大于0") }).isTrue()
            assertThat(e.errors.any { it.message.contains("批处理最大迭代次数必须大于0") }).isTrue()
            assertThat(e.errors.any { it.message.contains("批处理运行次数必须大于0") }).isTrue()
        }
    }

    @Test
    fun `should validate realtime config parameters`() {
        // Given
        val invalidConfig = ExperimentConfig.createDefault().copy(
            realtime = ExperimentConfig.createDefault().realtime.copy(
                cloudletCount = 0,
                simulationDuration = -1.0,
                arrivalRate = 0.0,
                runs = -1
            )
        )

        // When - Then
        try {
            ExperimentConfig.validate(invalidConfig)
            fail("Expected ConfigValidationException")
        } catch (e: ConfigValidationException) {
            assertThat(e.errors.any { it.message.contains("实时调度任务数必须大于0") }).isTrue()
            assertThat(e.errors.any { it.message.contains("仿真持续时间必须大于0") }).isTrue()
            assertThat(e.errors.any { it.message.contains("到达率必须大于0") }).isTrue()
            assertThat(e.errors.any { it.message.contains("实时调度运行次数必须大于0") }).isTrue()
        }
    }

    @Test
    fun `should validate optimizer config parameters`() {
        // Given
        val invalidConfig = ExperimentConfig.createDefault().copy(
            optimizer = ExperimentConfig.createDefault().optimizer.copy(
                population = -1,
                maxIter = 0
            )
        )

        // When - Then
        try {
            ExperimentConfig.validate(invalidConfig)
            fail("Expected ConfigValidationException")
        } catch (e: ConfigValidationException) {
            assertThat(e.errors.any { it.message.contains("优化算法种群大小必须大于0") }).isTrue()
            assertThat(e.errors.any { it.message.contains("优化算法最大迭代次数必须大于0") }).isTrue()
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = [-0.1, 1.1, 2.0])
    fun `should validate objective weights range`(invalidWeight: Double) {
        // Given - When - Then
        assertThatThrownBy {
            ObjectiveWeightsConfig(cost = invalidWeight)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("权重必须在[0,1]范围内")

        assertThatThrownBy {
            ObjectiveWeightsConfig(totalTime = invalidWeight)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("权重必须在[0,1]范围内")
    }

    @Test
    fun `should validate objective weights sum greater than zero`() {
        // Given - When - Then
        assertThatThrownBy {
            ObjectiveWeightsConfig(cost = 0.0, totalTime = 0.0, loadBalance = 0.0, makespan = 0.0)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("权重总和必须大于0")
    }

    @Test
    fun `should create valid objective weights config`() {
        // Given
        val weights = ObjectiveWeightsConfig(
            cost = 0.3,
            totalTime = 0.4,
            loadBalance = 0.2,
            makespan = 0.1
        )

        // When - Then
        assertThat(weights.cost).isEqualTo(0.3)
        assertThat(weights.totalTime).isEqualTo(0.4)
        assertThat(weights.loadBalance).isEqualTo(0.2)
        assertThat(weights.makespan).isEqualTo(0.1)
    }

    @Test
    fun `should parse generator types correctly`() {
        // Given - When - Then
        assertThat(ExperimentConfig.parseGeneratorType("LOG_NORMAL")).isEqualTo(CloudletGeneratorType.LOG_NORMAL)
        assertThat(ExperimentConfig.parseGeneratorType("UNIFORM")).isEqualTo(CloudletGeneratorType.UNIFORM)
        assertThat(ExperimentConfig.parseGeneratorType("GOOGLE_TRACE")).isEqualTo(CloudletGeneratorType.GOOGLE_TRACE)

        // Case insensitive
        assertThat(ExperimentConfig.parseGeneratorType("log_normal")).isEqualTo(CloudletGeneratorType.LOG_NORMAL)
        assertThat(ExperimentConfig.parseGeneratorType("uniform")).isEqualTo(CloudletGeneratorType.UNIFORM)
    }

    @Test
    fun `should handle invalid generator type gracefully`() {
        // Given - When
        val type = ExperimentConfig.parseGeneratorType("INVALID_TYPE")
        
        // Then
        assertThat(type).isEqualTo(CloudletGeneratorType.LOG_NORMAL)
    }

    @Test
    fun `should create valid generator configs`() {
        // Given - When - Then
        assertThat(GeneratorConfig.LOG_NORMAL.type).isEqualTo("LOG_NORMAL")
        assertThat(GeneratorConfig.UNIFORM.type).isEqualTo("UNIFORM")
        assertThat(GeneratorConfig.GOOGLE_TRACE.type).isEqualTo("GOOGLE_TRACE")
    }

    @Test
    fun `should validate google trace config parameters`() {
        // Given
        val config = GoogleTraceConfig(
            filePath = "test/path.csv",
            maxTasks = 100,
            timeWindowStart = 1000L,
            timeWindowEnd = 2000L
        )

        // When - Then
        assertThat(config.filePath).isEqualTo("test/path.csv")
        assertThat(config.maxTasks).isEqualTo(100)
        assertThat(config.timeWindowStart).isEqualTo(1000L)
        assertThat(config.timeWindowEnd).isEqualTo(2000L)
    }
}
