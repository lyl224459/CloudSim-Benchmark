package cli

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CommandExecutorProductionPathTest {
    @TempDir
    lateinit var tempDir: File

    private val exampleConfig = "configs/examples/single_config_example.toml"

    @Test
    fun `command executor handles help list validate and print commands`(): Unit =
        runBlocking {
            CommandExecutor.execute(CliParser.HelpCommand)
            CommandExecutor.execute(CliParser.ListAlgorithmsCommand("batch"))
            CommandExecutor.execute(CliParser.ListAlgorithmsCommand("realtime"))
            CommandExecutor.execute(CliParser.ListProfilesCommand(exampleConfig))
            CommandExecutor.execute(CliParser.ListPresetsCommand(exampleConfig))
            CommandExecutor.execute(CliParser.ConfigValidateCommand(exampleConfig))
            CommandExecutor.execute(CliParser.ConfigPrintCommand(exampleConfig, "batch_small"))
        }

    @Test
    fun `batch and realtime dry runs do not create output directories`(): Unit =
        runBlocking {
            CommandExecutor.execute(dryRunCommand("batch_small"))
            CommandExecutor.execute(dryRunCommand("realtime_smoke"))

            assertThat(tempDir.listFiles()).isEmpty()
        }

    @Test
    fun `command executor propagates configuration failures`() {
        val error =
            assertThrows<IllegalArgumentException> {
                runBlocking {
                    CommandExecutor.execute(CliParser.ConfigValidateCommand(File(tempDir, "missing.toml").path))
                }
            }

        assertThat(error.message).contains("配置文件不存在")
    }

    private fun dryRunCommand(profile: String) =
        CliParser.RunCommand(
            configFile = exampleConfig,
            profile = profile,
            outputDir = tempDir.absolutePath,
            dryRun = true,
        )
}
