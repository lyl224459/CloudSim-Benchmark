package util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime

class ExperimentOutputContextTest {
    @TempDir
    lateinit var fixtureDir: File

    @Test
    fun `concurrent trial writes keep a single header and complete rows`(): Unit =
        runBlocking {
            val tempDir = Files.createTempDirectory("experiment-output-concurrent").toFile()
            try {
                val context = ExperimentOutputContext(tempDir)

                (1..50)
                    .map { trial ->
                        async(Dispatchers.Default) {
                            context.saveAlgorithmTrialResult(
                                algorithmName = "PSO",
                                trial = trial,
                                metrics = mapOf("Fitness" to trial.toDouble(), "Cost" to trial + 0.5),
                            )
                        }
                    }.awaitAll()

                val lines = tempDir.resolve("PSO.csv").readLines()
                assertThat(lines).hasSize(51)
                assertThat(lines.first()).isEqualTo("Trial,Fitness,Cost")
                assertThat(lines.drop(1)).allSatisfy { line ->
                    assertThat(line.split(",")).hasSize(3)
                }
                assertThat(lines.count { it == "Trial,Fitness,Cost" }).isEqualTo(1)
            } finally {
                tempDir.deleteRecursively()
            }
        }

    @Test
    fun `child output context inherits csv settings and writes into child directory`(): Unit =
        runBlocking {
            val tempDir = Files.createTempDirectory("experiment-output-child").toFile()
            try {
                val parent =
                    ExperimentOutputContext(
                        experimentDir = tempDir,
                        csvEnabled = true,
                        csvDelimiter = ";",
                    )
                val child = parent.child("cloudlets_100")

                child.saveAlgorithmTrialResult(
                    algorithmName = "Random",
                    trial = 1,
                    metrics = mapOf("Fitness" to 2.0),
                )

                val childFile = tempDir.resolve("cloudlets_100").resolve("Random.csv")
                assertThat(childFile).exists()
                assertThat(childFile.readText()).contains("Trial;Fitness")
                assertThat(tempDir.resolve("Random.csv")).doesNotExist()
            } finally {
                tempDir.deleteRecursively()
            }
        }

    @Test
    fun `experiment directory numbering and explicit names are deterministic`() {
        val modeDir = fixtureDir.resolve("batch").also(File::mkdirs)
        modeDir.resolve("exp1_old").mkdir()
        modeDir.resolve("exp7_old").mkdir()
        modeDir.resolve("other").mkdir()

        val generated =
            ExperimentOutputContext.createExperimentDirectory(
                baseResultsDir = fixtureDir,
                mode = "BATCH",
                experimentName = null,
                now = LocalDateTime.of(2026, 6, 15, 10, 11, 12),
            )
        val named =
            ExperimentOutputContext.createExperimentDirectory(
                baseResultsDir = fixtureDir,
                mode = "realtime",
                experimentName = "fixed",
                now = LocalDateTime.of(2026, 6, 15, 10, 11, 12),
            )

        assertThat(generated.name).isEqualTo("exp8_20260615_101112")
        assertThat(named.name).isEqualTo("fixed")
    }
}
