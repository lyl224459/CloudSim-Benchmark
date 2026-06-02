package util

import config.CsvConfig
import config.OutputConfig
import config.SystemConfig
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

class ExperimentOutputContextConfigTest {
    @Test
    fun `csv disabled suppresses trial file output`() =
        runBlocking {
            val tempDir = Files.createTempDirectory("output-context-disabled").toFile()
            try {
                val context =
                    ExperimentOutputContext.from(
                        SystemConfig.createDefault().copy(
                            output =
                                OutputConfig(
                                    resultsDir = tempDir.absolutePath,
                                    csv = CsvConfig(enabled = false, delimiter = ";"),
                                ),
                        ),
                        tempDir,
                    )

                context.saveAlgorithmTrialResult(
                    algorithmName = "PSO",
                    trial = 1,
                    metrics = mapOf("Fitness" to 1.23),
                )

                assertThat(tempDir.listFiles()).isEmpty()
            } finally {
                tempDir.deleteRecursively()
            }
        }

    @Test
    fun `csv delimiter is applied when enabled`() =
        runBlocking {
            val tempDir = Files.createTempDirectory("output-context-delimiter").toFile()
            try {
                val context =
                    ExperimentOutputContext.from(
                        SystemConfig.createDefault().copy(
                            output =
                                OutputConfig(
                                    resultsDir = tempDir.absolutePath,
                                    csv = CsvConfig(enabled = true, delimiter = ";"),
                                ),
                        ),
                        tempDir,
                    )

                context.saveAlgorithmTrialResult(
                    algorithmName = "PSO",
                    trial = 1,
                    metrics = mapOf("Fitness" to 1.23, "Cost" to 4.56),
                )

                val file = tempDir.resolve("PSO.csv")
                assertThat(file).exists()
                assertThat(file.readText()).contains("Trial;Fitness;Cost")
            } finally {
                tempDir.deleteRecursively()
            }
        }

    @Test
    fun `creates experiment directory and saves resolved config`() {
        val tempDir = Files.createTempDirectory("output-context-experiment").toFile()
        try {
            val systemConfig =
                SystemConfig.createDefault().copy(
                    output = OutputConfig(resultsDir = tempDir.absolutePath),
                )
            val context = ExperimentOutputContext.createExperiment(systemConfig, "batch", "demo_001")

            context.saveResolvedConfig("""{"mode":"batch"}""")

            val experimentDir = tempDir.resolve("batch").resolve("demo_001")
            assertThat(context.experimentDir).isEqualTo(experimentDir)
            assertThat(experimentDir).isDirectory()
            assertThat(experimentDir.resolve("resolved_config.json").readText()).isEqualTo("""{"mode":"batch"}""")
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
