import cli.CliParser
import config.RealtimeAlgorithmType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files

class MainCliOverrideTest {

    @Test
    fun `run dry-run parser resolves one batch algorithm`() {
        val command = CliParser(
            arrayOf("run", "--mode", "batch", "-a", "RANDOM", "-r", "1", "--dry-run")
        ).parse() as CliParser.RunCommand

        assertEquals("batch", command.mode)
        assertEquals(listOf("RANDOM"), command.algorithms)
        assertEquals(1, command.runs)
        assertTrue(command.dryRun)
    }

    @Test
    fun `legacy batch command fails with migration hint`() {
        val error = assertThrows<IllegalArgumentException> {
            CliParser(arrayOf("batch", "-a", "RANDOM")).parse()
        }

        assertTrue(error.message?.contains("旧入口") == true)
        assertTrue(error.message?.contains("run --mode batch") == true)
    }

    @Test
    fun `preset and algorithms together fail before execution`() {
        val error = assertThrows<IllegalArgumentException> {
            CliParser(
                arrayOf("run", "--mode", "batch", "--preset", "quick", "-a", "RANDOM")
            ).parse()
        }

        assertTrue(error.message?.contains("互斥") == true)
    }

    @Test
    fun `config plus cli override resolves batch algorithms and runs`() {
        val configFile = Files.createTempFile("cli-profile-", ".toml").toFile().apply {
            writeText("""
                defaultProfile = "batch_profile"

                [profiles.batch_profile]
                mode = "batch"
                algorithms = ["PSO", "WOA"]
                runs = 3

                [profiles.batch_profile.batch]
                cloudletCount = 100
                population = 30
                maxIter = 50
            """.trimIndent())
        }

        val resolved = try {
            resolveRun(
                CliParser.RunCommand(
                    configFile = configFile.absolutePath,
                    profile = "batch_profile",
                    algorithms = listOf("RANDOM"),
                    runs = 1
                )
            )
        } finally {
            configFile.delete()
        }

        assertEquals(listOf("RANDOM"), resolved.selectedAlgorithmNames)
        assertEquals("batch_profile", resolved.profileName)
        assertEquals(1, resolved.experimentConfig.batch.runs)
    }

    @Test
    fun `unknown realtime algorithm fails with valid candidates`() {
        val error = assertThrows<IllegalArgumentException> {
            parseRealtimeAlgorithms(listOf("NOT_REAL"))
        }

        assertTrue(error.message?.contains("未知的实时调度算法") == true)
        assertTrue(error.message?.contains(RealtimeAlgorithmType.MIN_LOAD.name) == true)
    }
}
