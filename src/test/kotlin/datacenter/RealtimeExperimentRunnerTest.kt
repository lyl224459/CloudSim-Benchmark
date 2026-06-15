package datacenter

import config.CloudletGeneratorType
import config.ObjectiveWeightsConfig
import config.RealtimeArrivalConfig
import config.RealtimeSchedulingConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import scheduler.RealtimeMinLoadScheduler

class RealtimeExperimentRunnerTest {
    private val scheduling = RealtimeSchedulingConfig()
    private val config =
        RealtimeExperimentConfigSnapshot(
            cloudletCount = 3,
            simulationDuration = 20.0,
            arrivalRate = 5.0,
            generatorType = CloudletGeneratorType.UNIFORM,
            googleTraceConfig = null,
            arrival = RealtimeArrivalConfig(distribution = "uniform"),
            scheduling = scheduling,
        )
    private val runner =
        RealtimeExperimentRunner(
            config,
            RealtimeMetricsCollector(scheduling, ObjectiveWeightsConfig()),
        )

    @Test
    fun `runner executes a small fixed seed realtime simulation`() {
        val result =
            runner.run(
                RealtimeExperimentRunRequest("MIN_LOAD", 42L) { vms ->
                    RealtimeMinLoadScheduler(vms)
                },
            )

        assertThat(result.algorithmName).isEqualTo("MIN_LOAD")
        assertThat(result.submittedCount).isGreaterThanOrEqualTo(0)
        assertThat(result.metrics.values.values).allSatisfy { value ->
            assertThat(value.isFinite()).isTrue()
        }
    }

    @Test
    fun `runner propagates scheduler factory failures`() {
        val failure = IllegalStateException("scheduler factory failed")

        val thrown =
            assertThrows<IllegalStateException> {
                runner.run(RealtimeExperimentRunRequest("BROKEN", 42L) { throw failure })
            }

        assertThat(thrown).isSameAs(failure)
    }
}
