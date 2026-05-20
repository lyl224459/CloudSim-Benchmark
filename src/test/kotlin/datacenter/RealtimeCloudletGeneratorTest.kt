package datacenter

import config.CloudletGeneratorType
import config.RealtimeArrivalConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Random

class RealtimeCloudletGeneratorTest {

    @Test
    fun `uniform arrival creates evenly spaced cloudlets`() {
        val generator = RealtimeCloudletGenerator(
            random = Random(42),
            arrivalRate = 2.0,
            generatorType = CloudletGeneratorType.LOG_NORMAL,
            arrivalConfig = RealtimeArrivalConfig(distribution = "uniform")
        )

        val cloudlets = generator.createRealtimeCloudlets(0, count = 5, simulationDuration = 10.0)
        val arrivalTimes = cloudlets.map { it.submissionDelay }
        val intervals = arrivalTimes.zipWithNext { a, b -> b - a }

        assertThat(arrivalTimes).hasSize(5)
        assertThat(intervals).allSatisfy { interval ->
            assertThat(interval).isEqualTo(0.5)
        }
    }

    @Test
    fun `poisson arrival times stay within simulation duration and remain ordered`() {
        val generator = RealtimeCloudletGenerator(
            random = Random(7),
            arrivalRate = 3.0,
            generatorType = CloudletGeneratorType.LOG_NORMAL,
            arrivalConfig = RealtimeArrivalConfig(distribution = "poisson")
        )

        val cloudlets = generator.createRealtimeCloudlets(0, count = 20, simulationDuration = 4.0)
        val arrivalTimes = cloudlets.map { it.submissionDelay }

        assertThat(arrivalTimes).isSorted
        assertThat(arrivalTimes).allSatisfy { time ->
            assertThat(time).isGreaterThan(0.0)
            assertThat(time).isLessThanOrEqualTo(4.0)
        }
    }

    @Test
    fun `burst arrival produces visible interval variation`() {
        val generator = RealtimeCloudletGenerator(
            random = Random(99),
            arrivalRate = 4.0,
            generatorType = CloudletGeneratorType.LOG_NORMAL,
            arrivalConfig = RealtimeArrivalConfig(
                distribution = "burst",
                burstIntensity = 3.0,
                burstDuration = 2.0
            )
        )

        val cloudlets = generator.createRealtimeCloudlets(0, count = 20, simulationDuration = 10.0)
        val intervals = cloudlets.map { it.submissionDelay }.zipWithNext { a, b -> b - a }

        assertThat(intervals).isNotEmpty
        assertThat(intervals.maxOrNull()).isGreaterThan(intervals.minOrNull())
    }
}
