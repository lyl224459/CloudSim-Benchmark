package util

import config.CsvConfig
import config.OutputConfig
import config.SystemConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

class ResultsManagerTest {

    @Test
    fun `csv disabled suppresses trial file output`() {
        val tempDir = Files.createTempDirectory("results-manager-test").toFile()
        try {
            ResultsManager.configure(
                SystemConfig.createDefault().copy(
                    output = OutputConfig(
                        resultsDir = tempDir.absolutePath,
                        csv = CsvConfig(enabled = false, delimiter = ";")
                    )
                )
            )

            ResultsManager.saveAlgorithmTrialResult(
                experimentDir = tempDir,
                algorithmName = "PSO",
                trial = 1,
                metrics = mapOf("Fitness" to 1.23)
            )

            assertThat(tempDir.listFiles()).isEmpty()
        } finally {
            tempDir.deleteRecursively()
            ResultsManager.configure(SystemConfig.createDefault())
        }
    }

    @Test
    fun `csv delimiter is applied when enabled`() {
        val tempDir = Files.createTempDirectory("results-manager-delimiter").toFile()
        try {
            ResultsManager.configure(
                SystemConfig.createDefault().copy(
                    output = OutputConfig(
                        resultsDir = tempDir.absolutePath,
                        csv = CsvConfig(enabled = true, delimiter = ";")
                    )
                )
            )

            ResultsManager.saveAlgorithmTrialResult(
                experimentDir = tempDir,
                algorithmName = "PSO",
                trial = 1,
                metrics = mapOf("Fitness" to 1.23, "Cost" to 4.56)
            )

            val file = tempDir.resolve("PSO.csv")
            assertThat(file).exists()
            assertThat(file.readText()).contains("Trial;Fitness;Cost")
        } finally {
            tempDir.deleteRecursively()
            ResultsManager.configure(SystemConfig.createDefault())
        }
    }
}
