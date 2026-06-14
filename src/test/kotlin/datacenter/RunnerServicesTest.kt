package datacenter

import config.BatchConfig
import config.RealtimeConfig
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import scheduler.AlgorithmRegistry
import scheduler.ResolvedAlgorithm
import scheduler.ResolvedAlgorithmSettings
import util.ExperimentConcurrency
import util.ExperimentOutputContext
import java.io.File

class RunnerServicesTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `batch runner increments seeds saves trials and keeps partial failures`(): Unit =
        runBlocking {
            val exporter = RecordingBatchExporter()
            val seeds = mutableListOf<Long>()
            val runner =
                ComparisonRunner(
                    request = batchRequest(runs = 3),
                    executor =
                        BatchExecutionService { algorithmName, seed, _ ->
                            seeds += seed
                            if (seed == 12L) error("failed trial")
                            batchResult(algorithmName, seed.toDouble())
                        },
                    exporter = exporter,
                )

            val summaries = runner.runComparisonSummaries()

            assertThat(seeds).containsExactly(11L, 12L, 13L)
            assertThat(summaries.single().status).isEqualTo(BatchRunStatus.PARTIAL_FAILURE)
            assertThat(exporter.trials).hasSize(3)
        }

    @Test
    fun `realtime runner increments seeds and exports failed trial`(): Unit =
        runBlocking {
            val exporter = RecordingRealtimeExporter()
            val seeds = mutableListOf<Long>()
            val runner =
                RealtimeComparisonRunner(
                    request = realtimeRequest(runs = 2),
                    experimentRunner =
                        RealtimeExperimentService { request ->
                            seeds += request.runSeed
                            if (request.runSeed == 12L) error("failed trial")
                            realtimeResult(request.algorithmName, request.runSeed.toDouble())
                        },
                    exporter = exporter,
                )

            val summaries = runner.runComparisonSummaries()

            assertThat(seeds).containsExactly(11L, 12L)
            assertThat(summaries.single().status).isEqualTo(RealtimeRunStatus.PARTIAL_FAILURE)
            assertThat(exporter.trials).hasSize(2)
        }

    @Test
    fun `cloudlet count runners create sorted child requests and child output directories`(): Unit =
        runBlocking {
            val batchChildren = mutableListOf<BatchExperimentRequest>()
            val realtimeChildren = mutableListOf<RealtimeExperimentRequest>()
            val batchExports = mutableListOf<Map<Int, List<BatchRunSummary>>>()
            val realtimeExports = mutableListOf<Map<Int, List<RealtimeRunSummary>>>()

            BatchCloudletCountRunner(
                request = batchRequest().copy(batch = BatchConfig(cloudletCounts = listOf(100, 50))),
                summaryRunner =
                    BatchSummaryRunnerFactory { child ->
                        batchChildren += child
                        emptyList()
                    },
                exporter = BatchCloudletCountExportService(batchExports::add),
            ).runExperiment()
            RealtimeCloudletCountRunner(
                request = realtimeRequest().copy(realtime = RealtimeConfig(cloudletCounts = listOf(100, 50))),
                summaryRunner =
                    RealtimeSummaryRunnerFactory { child ->
                        realtimeChildren += child
                        emptyList()
                    },
                exporter = RealtimeCloudletCountExportService(realtimeExports::add),
            ).runBatchExperiment()

            assertThat(batchChildren.map { it.batch.cloudletCount }).containsExactly(100, 50)
            assertThat(realtimeChildren.map { it.realtime.cloudletCount }).containsExactly(100, 50)
            assertThat(batchChildren.map { it.execution.randomSeed }).containsOnly(10L)
            assertThat(realtimeChildren.map { it.execution.randomSeed }).containsOnly(10L)
            assertThat(
                batchChildren.map {
                    it.execution.outputContext.experimentDir
                        ?.name
                },
            ).containsExactly("cloudlets_100", "cloudlets_50")
            assertThat(
                realtimeChildren.map {
                    it.execution.outputContext.experimentDir
                        ?.name
                },
            ).containsExactly("cloudlets_100", "cloudlets_50")
            assertThat(batchExports.single().keys).containsExactlyInAnyOrder(50, 100)
            assertThat(realtimeExports.single().keys).containsExactlyInAnyOrder(50, 100)
        }

    @Test
    fun `cloudlet count runners sort nonempty summaries before exporting`(): Unit =
        runBlocking {
            val batchExports = mutableListOf<Map<Int, List<BatchRunSummary>>>()
            val realtimeExports = mutableListOf<Map<Int, List<RealtimeRunSummary>>>()

            BatchCloudletCountRunner(
                request = batchRequest().copy(batch = BatchConfig(cloudletCounts = listOf(50))),
                summaryRunner = BatchSummaryRunnerFactory { listOf(batchSummary("ZETA"), batchSummary("ALPHA")) },
                exporter = BatchCloudletCountExportService(batchExports::add),
            ).runExperiment()
            RealtimeCloudletCountRunner(
                request = realtimeRequest().copy(realtime = RealtimeConfig(cloudletCounts = listOf(50))),
                summaryRunner =
                    RealtimeSummaryRunnerFactory {
                        listOf(realtimeSummary("ZETA"), realtimeSummary("ALPHA"))
                    },
                exporter = RealtimeCloudletCountExportService(realtimeExports::add),
            ).runBatchExperiment()

            assertThat(batchExports.single().getValue(50).map { it.algorithmName }).containsExactly("ALPHA", "ZETA")
            assertThat(realtimeExports.single().getValue(50).map { it.algorithmName }).containsExactly("ALPHA", "ZETA")
        }

    @Test
    fun `cloudlet count runner propagates exporter failures`() {
        val failure = IllegalStateException("export failed")
        val runner =
            BatchCloudletCountRunner(
                request = batchRequest().copy(batch = BatchConfig(cloudletCounts = listOf(50))),
                summaryRunner = BatchSummaryRunnerFactory { emptyList() },
                exporter = BatchCloudletCountExportService { throw failure },
            )

        val thrown = assertThrows<IllegalStateException> { runBlocking { runner.runExperiment() } }

        assertThat(thrown).isSameAs(failure)
    }

    private fun batchRequest(runs: Int = 1): BatchExperimentRequest =
        BatchExperimentRequest(
            batch = BatchConfig(runs = runs),
            execution = executionRequest(batchAlgorithm()),
        )

    private fun realtimeRequest(runs: Int = 1): RealtimeExperimentRequest =
        RealtimeExperimentRequest(
            realtime = RealtimeConfig(runs = runs),
            execution = executionRequest(realtimeAlgorithm()),
        )

    private fun executionRequest(algorithm: ResolvedAlgorithm) =
        ExperimentExecutionRequest(
            randomSeed = 10L,
            resolvedAlgorithms = listOf(algorithm),
            outputContext = ExperimentOutputContext(tempDir),
            concurrency = ExperimentConcurrency(useCoroutines = false),
        )

    private fun batchAlgorithm() =
        ResolvedAlgorithm(
            AlgorithmRegistry.resolveBatch("RANDOM"),
            ResolvedAlgorithmSettings(population = 2, maxIter = 2),
        )

    private fun realtimeAlgorithm() =
        ResolvedAlgorithm(
            AlgorithmRegistry.resolveRealtime("MIN_LOAD"),
            ResolvedAlgorithmSettings(population = 2, maxIter = 2),
        )

    private fun batchResult(
        algorithmName: String,
        value: Double,
    ) = AlgorithmResult(algorithmName, value, value, value, value, value)

    private fun realtimeResult(
        algorithmName: String,
        value: Double,
    ) = RealtimeAlgorithmResult(
        algorithmName,
        RealtimeMetricValues.of(
            RealtimeMetricKey.MAKESPAN to value,
            RealtimeMetricKey.FITNESS to value,
        ),
    )

    private fun batchSummary(algorithmName: String): BatchRunSummary =
        BatchRunAggregator.buildSummary(
            algorithmName,
            listOf(BatchRunOutcome.Success(batchResult(algorithmName, 1.0), run = 1)),
        )

    private fun realtimeSummary(algorithmName: String): RealtimeRunSummary =
        RealtimeRunAggregator.buildSummary(
            algorithmName,
            listOf(RealtimeRunOutcome.Success(realtimeResult(algorithmName, 1.0), run = 1)),
        )
}

private class RecordingBatchExporter : BatchExportService {
    val trials = mutableListOf<BatchRunOutcome>()

    override fun printComparisonResults(summaries: List<BatchRunSummary>) = Unit

    override fun exportToCsv(summaries: List<BatchRunSummary>) = Unit

    override fun saveSummary(summaries: List<BatchRunSummary>) = Unit

    override suspend fun saveTrial(outcome: BatchRunOutcome) {
        trials += outcome
    }
}

private class RecordingRealtimeExporter : RealtimeExportService {
    val trials = mutableListOf<RealtimeRunOutcome>()

    override suspend fun saveTrialOutcome(outcome: RealtimeRunOutcome) {
        trials += outcome
    }

    override fun printComparisonResults(summaries: List<RealtimeRunSummary>) = Unit

    override fun exportRealtimeToCSV(summaries: List<RealtimeRunSummary>) = Unit

    override fun saveSummaryResults(summaries: List<RealtimeRunSummary>) = Unit
}
