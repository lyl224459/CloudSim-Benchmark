package util

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ExperimentOutputContextFailureTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `output writes propagate filesystem failures`() {
        val outputPath = tempDir.resolve("not-a-directory").apply { writeText("occupied") }
        val context = ExperimentOutputContext(outputPath)

        assertThatThrownBy { context.saveExperimentInfo(mapOf("mode" to "batch")) }.isInstanceOf(Exception::class.java)
        assertThatThrownBy { context.saveResolvedConfig("{}") }.isInstanceOf(Exception::class.java)
        assertThatThrownBy { context.saveSummaryRows(listOf(listOf("PSO")), listOf("Algorithm")) }
            .isInstanceOf(Exception::class.java)
        assertThatThrownBy {
            runBlocking {
                context.saveAlgorithmTrialRow("PSO", listOf("Trial"), listOf(1))
            }
        }.isInstanceOf(Exception::class.java)
    }

    @Test
    fun `base result path failures are propagated`() {
        val outputPath = tempDir.resolve("not-a-directory").apply { writeText("occupied") }
        val context = ExperimentOutputContext(experimentDir = null, baseResultsDir = outputPath)

        assertThatThrownBy { context.generateResultFileName("result").writeText("data") }
            .isInstanceOf(Exception::class.java)
    }
}
