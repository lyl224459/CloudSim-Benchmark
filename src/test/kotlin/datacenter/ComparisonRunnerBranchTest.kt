package datacenter

import config.BatchConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import scheduler.AlgorithmRegistry
import scheduler.ResolvedAlgorithm
import scheduler.ResolvedAlgorithmSettings
import util.ExperimentConcurrency
import util.ExperimentOutputContext

class ComparisonRunnerBranchTest {
    @Test
    fun `runner rejects empty algorithms`() {
        val runner = runner(emptyList()) { _, _, _ -> result("unused") }

        val error = assertThrows<IllegalArgumentException> { runBlocking { runner.runComparisonSummaries() } }

        assertThat(error.message).contains("已解析的算法列表")
    }

    @Test
    fun `runner converts supported algorithm failures and sorts summaries`(): Unit =
        runBlocking {
            val algorithms = listOf(algorithm("WOA"), algorithm("RANDOM"))
            val runner =
                runner(algorithms) { name, _, _ ->
                    when (name) {
                        "WOA" -> throw IllegalArgumentException("invalid")
                        else -> result(name)
                    }
                }

            val summaries = runner.runComparisonWithStatistics()

            assertThat(summaries.map { it.algorithmName }).containsExactly("Random")
        }

    @Test
    fun `runner keeps all failed summary and propagates cancellation`() {
        val failedRunner = runner(listOf(algorithm("RANDOM"))) { _, _, _ -> throw IllegalStateException("failed") }
        val cancelledRunner =
            runner(listOf(algorithm("RANDOM"))) { _, _, _ ->
                throw CancellationException("cancelled")
            }

        val failed = runBlocking { failedRunner.runComparisonSummaries().single() }

        assertThat(failed.status).isEqualTo(BatchRunStatus.FAILED)
        assertThat(failed.failedRuns.single().errorMessage).contains("failed")
        assertThrows<CancellationException> { runBlocking { cancelledRunner.runComparisonSummaries() } }
    }

    @Test
    fun `runner propagates exporter failures after successful execution`() {
        val failure = IllegalStateException("export failed")
        val runner =
            ComparisonRunner(
                request = request(listOf(algorithm("RANDOM"))),
                executor = BatchExecutionService { name, _, _ -> result(name) },
                exporter =
                    object : BatchExportService {
                        override fun printComparisonResults(summaries: List<BatchRunSummary>) = Unit

                        override fun exportToCsv(summaries: List<BatchRunSummary>) = throw failure

                        override fun saveSummary(summaries: List<BatchRunSummary>) = Unit

                        override suspend fun saveTrial(outcome: BatchRunOutcome) = Unit
                    },
            )

        val thrown = assertThrows<IllegalStateException> { runBlocking { runner.runComparison() } }
        assertThat(thrown.message).isEqualTo(failure.message)
    }

    private fun runner(
        algorithms: List<ResolvedAlgorithm>,
        execute: BatchExecutionService,
    ): ComparisonRunner =
        ComparisonRunner(
            request(algorithms),
            execute,
            NoOpBatchExporter,
        )

    private fun request(algorithms: List<ResolvedAlgorithm>): BatchExperimentRequest =
        BatchExperimentRequest(
            batch = BatchConfig(runs = 1),
            execution =
                ExperimentExecutionRequest(
                    randomSeed = 40,
                    resolvedAlgorithms = algorithms,
                    outputContext = ExperimentOutputContext(null),
                    concurrency = ExperimentConcurrency(useCoroutines = false),
                ),
        )

    private fun algorithm(name: String): ResolvedAlgorithm =
        ResolvedAlgorithm(AlgorithmRegistry.resolveBatch(name), ResolvedAlgorithmSettings(2, 2))

    private fun result(name: String): AlgorithmResult = AlgorithmResult(name, 1.0, 1.0, 1.0, 1.0, 1.0)
}

private object NoOpBatchExporter : BatchExportService {
    override fun printComparisonResults(summaries: List<BatchRunSummary>) = Unit

    override fun exportToCsv(summaries: List<BatchRunSummary>) = Unit

    override fun saveSummary(summaries: List<BatchRunSummary>) = Unit

    override suspend fun saveTrial(outcome: BatchRunOutcome) = Unit
}
