package config

import org.junit.jupiter.api.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy

/**
 * 配置验证集成测试
 * 测试实际的配置加载和验证功能
 */
class ConfigValidationIntegrationTest {

    @Test
    fun `should load and validate default config successfully`() {
        // Given - When - Then
        // 默认配置应该能够成功加载和验证
        val config = ExperimentConfig.createDefault()
        ExperimentConfig.validate(config) // 不应该抛出异常
    }

    @Test
    fun `should reject invalid batch config with negative cloudlet count`() {
        // Given
        val invalidConfig = ExperimentConfig.createDefault().copy(
            batch = ExperimentConfig.createDefault().batch.copy(cloudletCount = -1)
        )

        // When - Then
        assertThatThrownBy { ExperimentConfig.validate(invalidConfig) }
            .isInstanceOf(ConfigValidationException::class.java)
            .hasMessageContaining("配置验证失败")
    }

    @Test
    fun `should reject invalid objective weights with negative values`() {
        // Given - When - Then
        assertThrows<IllegalArgumentException> {
            ObjectiveWeightsConfig(cost = -0.5)
        }
    }

    @Test
    fun `should reject objective weights that sum to zero`() {
        // Given - When - Then
        assertThrows<IllegalArgumentException> {
            ObjectiveWeightsConfig(cost = 0.0, totalTime = 0.0, loadBalance = 0.0, makespan = 0.0)
        }
    }

    @Test
    fun `should accept valid custom objective weights`() {
        // Given
        val validConfig = ExperimentConfig.createDefault().copy(
            batch = ExperimentConfig.createDefault().batch.copy(
                objectiveWeights = ObjectiveWeightsConfig(
                    cost = 0.3, totalTime = 0.4, loadBalance = 0.2, makespan = 0.1
                )
            )
        )

        // When - Then
        ExperimentConfig.validate(validConfig) // 不应该抛出异常
    }

    @Test
    fun `should provide detailed error information in ConfigValidationException`() {
        // Given
        val invalidConfig = ExperimentConfig.createDefault().copy(
            batch = ExperimentConfig.createDefault().batch.copy(
                cloudletCount = -1,
                population = 0
            )
        )

        // When - Then
        try {
            ExperimentConfig.validate(invalidConfig)
            fail("Expected ConfigValidationException")
        } catch (e: ConfigValidationException) {
            assertThat(e.errors.size).isGreaterThanOrEqualTo(2)
            assertThat(e.errors.any { it.field.contains("cloudletCount") }).isTrue()
            assertThat(e.errors.any { it.field.contains("population") }).isTrue()
        }
    }
}
