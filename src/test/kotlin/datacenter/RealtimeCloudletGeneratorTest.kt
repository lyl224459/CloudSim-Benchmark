package datacenter

import config.CloudletGeneratorType
import config.RealtimeArrivalConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Random

class RealtimeCloudletGeneratorTest {
    @Test
    fun `uniform arrival creates evenly spaced cloudlets`() {
        val generator =
            RealtimeCloudletGenerator(
                random = Random(42),
                arrivalRate = 2.0,
                generatorType = CloudletGeneratorType.LOG_NORMAL,
                arrivalConfig = RealtimeArrivalConfig(distribution = "uniform"),
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
        val generator =
            RealtimeCloudletGenerator(
                random = Random(7),
                arrivalRate = 3.0,
                generatorType = CloudletGeneratorType.LOG_NORMAL,
                arrivalConfig = RealtimeArrivalConfig(distribution = "poisson"),
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
        val generator =
            RealtimeCloudletGenerator(
                random = Random(99),
                arrivalRate = 4.0,
                generatorType = CloudletGeneratorType.LOG_NORMAL,
                arrivalConfig =
                    RealtimeArrivalConfig(
                        distribution = "burst",
                        burstIntensity = 3.0,
                        burstDuration = 2.0,
                    ),
            )

        val cloudlets = generator.createRealtimeCloudlets(0, count = 20, simulationDuration = 10.0)
        val intervals = cloudlets.map { it.submissionDelay }.zipWithNext { a, b -> b - a }

        assertThat(intervals).isNotEmpty
        assertThat(intervals.maxOrNull()).isGreaterThan(intervals.minOrNull())
    }

    @Test
    fun `periodic arrival with jitter is reproducible for fixed seed`() {
        fun arrivals(): List<Double> =
            RealtimeCloudletGenerator(
                random = Random(123),
                arrivalRate = 2.0,
                generatorType = CloudletGeneratorType.LOG_NORMAL,
                arrivalConfig =
                    RealtimeArrivalConfig(
                        distribution = "periodic",
                        periodSeconds = 1.5,
                        arrivalJitter = 0.2,
                    ),
            ).createRealtimeCloudlets(0, count = 8, simulationDuration = 20.0)
                .map { it.submissionDelay }

        val first = arrivals()
        val second = arrivals()

        assertThat(first).containsExactlyElementsOf(second)
        assertThat(first).isSorted
    }

    @Test
    fun `sporadic arrival samples bounded inter-arrival intervals`() {
        val generator =
            RealtimeCloudletGenerator(
                random = Random(321),
                arrivalRate = 1.0,
                generatorType = CloudletGeneratorType.LOG_NORMAL,
                arrivalConfig =
                    RealtimeArrivalConfig(
                        distribution = "sporadic",
                        sporadicMinInterArrival = 0.5,
                        sporadicMaxInterArrival = 1.0,
                    ),
            )

        val arrivals =
            generator
                .createRealtimeCloudlets(0, count = 10, simulationDuration = 20.0)
                .map { it.submissionDelay }

        assertThat(arrivals).isSorted
        assertThat(arrivals.zipWithNext { a, b -> b - a }).allSatisfy { interval ->
            assertThat(interval).isBetween(0.5, 1.0)
        }
    }

    @Test
    fun `mixed workload records expected duration matching adjusted cloudlet length`() {
        val batch =
            RealtimeCloudletGenerator(
                random = Random(11),
                arrivalRate = 5.0,
                generatorType = CloudletGeneratorType.UNIFORM,
                arrivalConfig =
                    RealtimeArrivalConfig(
                        distribution = "uniform",
                        workloadPattern = "mixed_short_long",
                        shortTaskRatio = 1.0,
                        shortTaskLengthMultiplier = 0.25,
                        runtimeReferenceMips = 1000.0,
                    ),
            ).createRealtimeCloudletBatch(0, count = 5, simulationDuration = 10.0)

        assertThat(batch.specs).hasSize(5)
        assertThat(batch.specs).allSatisfy { spec ->
            assertThat(spec.traceMetadata?.workloadClass).isEqualTo("short")
            assertThat(spec.traceMetadata?.expectedDuration).isEqualTo(spec.cloudlet.length / 1000.0)
        }
    }

    @Test
    fun `dag chain workload emits stable predecessor dependencies`() {
        val batch =
            RealtimeCloudletGenerator(
                random = Random(15),
                arrivalRate = 5.0,
                generatorType = CloudletGeneratorType.UNIFORM,
                arrivalConfig =
                    RealtimeArrivalConfig(
                        distribution = "uniform",
                        workloadPattern = "dag_chain",
                    ),
            ).createRealtimeCloudletBatch(0, count = 4, simulationDuration = 10.0)

        val ids = batch.specs.map { it.cloudlet.id }

        assertThat(ids).doesNotHaveDuplicates()
        assertThat(batch.specs[0].traceMetadata?.dependencyIds).isEmpty()
        assertThat(batch.specs[1].traceMetadata?.dependencyIds).containsExactly(ids[0])
        assertThat(batch.specs[2].traceMetadata?.dependencyIds).containsExactly(ids[1])
        assertThat(batch.specs[3].traceMetadata?.dependencyIds).containsExactly(ids[2])
    }

    @Test
    fun `layered dag workload emits acyclic stage dependencies`() {
        val batch =
            RealtimeCloudletGenerator(
                random = Random(19),
                arrivalRate = 5.0,
                generatorType = CloudletGeneratorType.UNIFORM,
                arrivalConfig =
                    RealtimeArrivalConfig(
                        distribution = "uniform",
                        workloadPattern = "dag_layered",
                        dagDepth = 2,
                        dagWidth = 2,
                        dagFanOut = 1,
                    ),
            ).createRealtimeCloudletBatch(0, count = 4, simulationDuration = 10.0)

        val ids = batch.specs.map { it.cloudlet.id }

        assertThat(ids).doesNotHaveDuplicates()
        assertThat(batch.specs.map { it.traceMetadata?.stageIndex }).containsExactly(0, 0, 1, 1)
        assertThat(batch.specs[0].traceMetadata?.dependencyIds).isEmpty()
        assertThat(batch.specs[1].traceMetadata?.dependencyIds).isEmpty()
        assertThat(batch.specs[2].traceMetadata?.dependencyIds).containsExactly(ids[0])
        assertThat(batch.specs[3].traceMetadata?.dependencyIds).containsExactly(ids[1])
    }
}
