package config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class RealtimeConfigValidatorSnapshotTest {
    @Test
    fun `realtime validation errors match committed snapshot`() {
        val defaults = ExperimentConfig.createDefault()
        val invalidConfig =
            defaults.copy(
                realtime =
                    defaults.realtime.copy(
                        scheduling =
                            RealtimeSchedulingConfig(
                                decisionDelay = -1.0,
                                runtimeFailureRate = 1.5,
                                tenantCount = 0,
                                topologyPolicy = "random",
                            ),
                    ),
            )
        val error = assertThrows<ConfigValidationException> { ExperimentConfig.validate(invalidConfig) }
        val rendered =
            error.errors.joinToString(separator = "\n", postfix = "\n") {
                "${it.field}|${it.message}"
            }
        val snapshot =
            File("src/test/resources/snapshots/realtime-validation-errors.txt")
                .readText()
                .replace("\r\n", "\n")

        assertThat(rendered).isEqualTo(snapshot)
    }
}
