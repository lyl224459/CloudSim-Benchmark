package config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProfileConfigValidatorFacadeTest {
    @Test
    fun `profile validation preserves field order and message keywords`() {
        val config =
            ExperimentConfig.createDefault().copy(
                defaultProfile = "missing",
                profiles =
                    mapOf(
                        "invalid" to
                            ProfileConfig(
                                mode = "unsupported",
                                algorithms = listOf("PSO"),
                                preset = "default",
                                runs = 0,
                                tasks = listOf(1, 0),
                            ),
                    ),
            )

        val exception = assertThrows<ConfigValidationException> { ExperimentConfig.validate(config) }
        val profileErrors =
            exception.errors.filter {
                it.field == "defaultProfile" || it.field.startsWith("profiles.")
            }

        assertThat(profileErrors.map { it.field }).containsExactly(
            "defaultProfile",
            "profiles.invalid.mode",
            "profiles.invalid",
            "profiles.invalid.runs",
            "profiles.invalid.tasks",
        )
        assertThat(profileErrors.map { it.message }).allSatisfy { message ->
            assertThat(message).isNotBlank()
        }
    }

    @Test
    fun `required profiles rejects an empty profile library`() {
        val exception =
            assertThrows<ConfigValidationException> {
                ExperimentConfigValidator.validate(ExperimentConfig.createDefault(), requireProfiles = true)
            }

        assertThat(exception.errors.single { it.field == "profiles" }.message).contains("不能为空")
    }
}
