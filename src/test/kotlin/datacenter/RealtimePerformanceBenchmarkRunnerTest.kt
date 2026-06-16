package datacenter

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertFailsWith

class RealtimePerformanceBenchmarkRunnerTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `smoke benchmark writes parseable json report`() {
        val outputFile = File(tempDir, "benchmark.json")
        val config =
            RealtimePerformanceBenchmarkConfig(
                cloudletCounts = listOf(5),
                algorithms = listOf(RealtimePerformanceBenchmarkAlgorithm.REALTIME_MIN_LOAD),
                measuredRuns = 1,
                randomSeed = 7L,
                outputFile = outputFile.absolutePath,
            )
        val runner = RealtimePerformanceBenchmarkRunner(config)
        val report = runner.run()

        runner.write(report)
        val parsed = Json.decodeFromString<RealtimePerformanceBenchmarkReport>(outputFile.readText())

        assertThat(parsed.config.cloudletCounts).containsExactly(5)
        assertThat(parsed.results).hasSize(1)
        assertThat(parsed.results.single().algorithm).isEqualTo("Realtime MinLoad")
        assertThat(parsed.results.single().status).isEqualTo(RealtimePerformanceBenchmarkStatus.SUCCESS)
        assertThat(parsed.results.single().runIndex).isEqualTo(1)
        assertThat(parsed.results.single().elapsedMillis).isGreaterThanOrEqualTo(0L)
        assertThat(parsed.results.single().memoryDeltaBytes).isGreaterThanOrEqualTo(0L)
    }

    @Test
    fun `benchmark report can record failed results without breaking json schema`() {
        val outputFile = File(tempDir, "failed-benchmark.json")
        RealtimePerformanceBenchmarkReport(
            generatedAt = "2026-01-01T00:00:00Z",
            config =
                RealtimePerformanceBenchmarkConfig(
                    cloudletCounts = listOf(10),
                    algorithms = listOf(RealtimePerformanceBenchmarkAlgorithm.REALTIME_MIN_LOAD),
                    measuredRuns = 1,
                    outputFile = outputFile.absolutePath,
                ),
            results =
                listOf(
                    RealtimePerformanceBenchmarkResult(
                        algorithm = "Realtime MinLoad",
                        mode = RealtimePerformanceBenchmarkMode.REALTIME,
                        cloudletCount = 10,
                        runIndex = 1,
                        status = RealtimePerformanceBenchmarkStatus.FAILED,
                        elapsedMillis = 2L,
                        memoryDeltaBytes = 0L,
                        errorType = "IllegalStateException",
                        errorMessage = "boom",
                    ),
                ),
        ).also { report ->
            RealtimePerformanceBenchmarkRunner(report.config).write(report)
        }

        val parsed = Json.decodeFromString<RealtimePerformanceBenchmarkReport>(outputFile.readText())

        assertThat(parsed.results.single().status).isEqualTo(RealtimePerformanceBenchmarkStatus.FAILED)
        assertThat(parsed.results.single().errorType).isEqualTo("IllegalStateException")
        assertThat(parsed.results.single().errorMessage).isEqualTo("boom")
    }

    @Test
    fun `runner records injected failures and coerces measured runs`() {
        val runner =
            RealtimePerformanceBenchmarkRunner(
                RealtimePerformanceBenchmarkConfig(
                    cloudletCounts = listOf(1),
                    algorithms = listOf(RealtimePerformanceBenchmarkAlgorithm.PSO),
                    measuredRuns = 0,
                ),
            ) { _, _, _ -> error("benchmark failed") }

        val result = runner.run().results.single()

        assertThat(result.runIndex).isEqualTo(1)
        assertThat(result.status).isEqualTo(RealtimePerformanceBenchmarkStatus.FAILED)
        assertThat(result.errorType).isEqualTo("IllegalStateException")
        assertThat(result.errorMessage).isEqualTo("benchmark failed")
    }

    @Test
    fun `algorithm parsing covers blank aliases distinct and unknown values`() {
        assertThat(RealtimePerformanceBenchmarkAlgorithm.parseList(""))
            .containsExactlyElementsOf(RealtimePerformanceBenchmarkAlgorithm.entries)
        assertThat(RealtimePerformanceBenchmarkAlgorithm.parseList("pso,pso,realtime-min-load"))
            .containsExactly(
                RealtimePerformanceBenchmarkAlgorithm.PSO,
                RealtimePerformanceBenchmarkAlgorithm.REALTIME_MIN_LOAD,
            )
        assertFailsWith<IllegalArgumentException> {
            RealtimePerformanceBenchmarkAlgorithm.parseList("unknown")
        }
    }

    @Test
    fun `benchmark option parsing covers defaults coercion and invalid arguments`() {
        val options =
            arrayOf(
                "--sizes",
                "2,bad,-1",
                "--algorithms",
                "pso,realtime-min-load",
                "--runs",
                "0",
                "--seed",
                "bad",
                "--population",
                "0",
                "--maxIter",
                "bad",
                "--output",
                "custom.json",
            ).toBenchmarkOptions()
        val config = options.toBenchmarkConfig()

        assertThat(config.cloudletCounts).containsExactly(2)
        assertThat(config.algorithms).containsExactly(
            RealtimePerformanceBenchmarkAlgorithm.PSO,
            RealtimePerformanceBenchmarkAlgorithm.REALTIME_MIN_LOAD,
        )
        assertThat(config.measuredRuns).isEqualTo(1)
        assertThat(config.randomSeed).isZero()
        assertThat(config.population).isEqualTo(1)
        assertThat(config.maxIter).isEqualTo(10)
        assertThat(config.outputFile).isEqualTo("custom.json")
        assertThat("bad,-1".toIntList()).containsExactly(100, 1_000, 10_000)

        assertFailsWith<IllegalArgumentException> {
            arrayOf("--sizes").toBenchmarkOptions()
        }
        assertFailsWith<IllegalArgumentException> {
            arrayOf("sizes", "1").toBenchmarkOptions()
        }
    }

    @Test
    fun `top level benchmark execution covers batch realtime and bounded scheduler result`() {
        executeBenchmark(
            RealtimePerformanceBenchmarkConfig(population = 1, maxIter = 1),
            RealtimePerformanceBenchmarkAlgorithm.PSO,
            cloudletCount = 1,
            seed = 3L,
        )
        executeBenchmark(
            RealtimePerformanceBenchmarkConfig(population = 1, maxIter = 1),
            RealtimePerformanceBenchmarkAlgorithm.REALTIME_MIN_LOAD,
            cloudletCount = 1,
            seed = 3L,
        )
    }

    @Test
    fun `write failure is propagated`() {
        val parentFile = File(tempDir, "not-a-directory").also { it.writeText("file") }
        val output = File(parentFile, "benchmark.json")
        val config = RealtimePerformanceBenchmarkConfig(outputFile = output.absolutePath)
        val report = RealtimePerformanceBenchmarkRunner(config) { _, _, _ -> }.run()

        assertFailsWith<Exception> {
            RealtimePerformanceBenchmarkRunner(config).write(report)
        }
    }
}
