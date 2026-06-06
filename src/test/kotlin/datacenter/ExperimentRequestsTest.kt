package datacenter

import config.BatchConfig
import config.OptimizerConfig
import config.RealtimeConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ExperimentRequestsTest {
    @Test
    fun `batch multi request creates single count child without changing execution`() {
        val request =
            BatchExperimentRequest(
                batch = BatchConfig(cloudletCounts = listOf(50, 100)),
                execution = ExperimentExecutionRequest(randomSeed = 42L),
            )

        val child = request.copy(batch = request.batch.copy(cloudletCount = 100))

        assertThat(child.batch.cloudletCount).isEqualTo(100)
        assertThat(child.batch.cloudletCounts).containsExactly(50, 100)
        assertThat(child.execution).isSameAs(request.execution)
    }

    @Test
    fun `realtime request keeps optimizer and execution settings`() {
        val request =
            RealtimeExperimentRequest(
                realtime = RealtimeConfig(cloudletCount = 500),
                optimizer = OptimizerConfig(population = 30, maxIter = 40),
                execution = ExperimentExecutionRequest(randomSeed = 7L),
            )

        assertThat(request.realtime.cloudletCount).isEqualTo(500)
        assertThat(request.optimizer.population).isEqualTo(30)
        assertThat(request.optimizer.maxIter).isEqualTo(40)
        assertThat(request.execution.randomSeed).isEqualTo(7L)
    }
}
