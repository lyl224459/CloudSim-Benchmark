package cli

import config.ExperimentConfig
import config.SystemConfig
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import util.ExperimentOutputContext
import java.io.File

class CommandExecutionCoordinatorTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `coordinator maps resolved run to launcher and output context`() =
        runBlocking {
            val events = mutableListOf<String>()
            val resolved = resolved("batch")
            val coordinator =
                coordinator(
                    resolved = resolved,
                    outputContext = ExperimentOutputContext(tempDir),
                    events = events,
                )

            coordinator.run(CliParser.RunCommand(mode = "batch"))

            assertThat(events).containsExactly("validate", "output:batch_20260101_000000", "launch:batch")
            assertThat(File(tempDir, "resolved_config.json")).exists()
        }

    @Test
    fun `dry run validates and resolves without creating output or launching`() =
        runBlocking {
            val events = mutableListOf<String>()
            val coordinator =
                coordinator(
                    resolved = resolved("realtime"),
                    outputContext = ExperimentOutputContext(tempDir),
                    events = events,
                )

            coordinator.run(CliParser.RunCommand(mode = "realtime", dryRun = true))

            assertThat(events).containsExactly("validate")
        }

    @Test
    fun `missing output directory fails before launch`() {
        val events = mutableListOf<String>()
        val coordinator =
            coordinator(
                resolved = resolved("batch"),
                outputContext = ExperimentOutputContext(null),
                events = events,
            )

        val error =
            assertThrows<IllegalStateException> {
                runBlocking { coordinator.run(CliParser.RunCommand(mode = "batch")) }
            }

        assertThat(error.message).contains("输出目录")
        assertThat(events).containsExactly("validate", "output:batch_20260101_000000")
    }

    private fun coordinator(
        resolved: ResolvedExperimentConfig,
        outputContext: ExperimentOutputContext,
        events: MutableList<String>,
    ) = CommandExecutionCoordinator(
        CommandExecutionServices(
            resolve = { resolved },
            validateEnvironment = { events += "validate" },
            timestamp = { "20260101_000000" },
            createOutputContext = { _, name ->
                events += "output:$name"
                outputContext
            },
            launcher = ResolvedExperimentLauncher { config, _ -> events += "launch:${config.mode}" },
        ),
    )

    private fun resolved(mode: String) =
        ResolvedExperimentConfig(
            command = CliParser.RunCommand(mode = mode),
            systemConfig = SystemConfig(),
            experimentConfig = ExperimentConfig(),
            mode = mode,
            profile = ResolvedProfile(name = null, presetName = null),
            algorithms = emptyList(),
            taskCounts = emptyList(),
            execution = ResolvedExecutionOptions(useCoroutines = false, maxConcurrency = 0, dryRun = false),
            output =
                ResolvedOutputConfig(
                    resultsDir = tempDir.absolutePath,
                    csvEnabled = true,
                    csvDelimiter = ",",
                    nameFormat = "{mode}_{timestamp}",
                ),
        )
}
