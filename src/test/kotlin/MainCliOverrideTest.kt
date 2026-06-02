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
        val command =
            CliParser(
                arrayOf("run", "--mode", "batch", "-a", "RANDOM", "-r", "1", "--dry-run"),
            ).parse() as CliParser.RunCommand

        assertEquals("batch", command.mode)
        assertEquals(listOf("RANDOM"), command.algorithms)
        assertEquals(1, command.runs)
        assertTrue(command.dryRun)
    }

    @Test
    fun `run parser treats inline and spaced long options equivalently`() {
        val spaced =
            CliParser(
                arrayOf(
                    "run",
                    "--mode",
                    "batch-multi",
                    "--algorithms",
                    "PSO,WOA",
                    "--tasks",
                    "50,100",
                    "--seed",
                    "7",
                    "--runs",
                    "2",
                    "--concurrency",
                    "3",
                ),
            ).parse()
        val inline =
            CliParser(
                arrayOf(
                    "run",
                    "--mode=batch-multi",
                    "--algorithms=PSO,WOA",
                    "--tasks=50,100",
                    "--seed=7",
                    "--runs=2",
                    "--concurrency=3",
                ),
            ).parse()

        assertEquals(spaced, inline)
    }

    @Test
    fun `run parser rejects unknown flags and positional arguments`() {
        assertThrows<IllegalArgumentException> {
            CliParser(arrayOf("run", "--mode", "batch", "--missing")).parse()
        }.also { error ->
            assertTrue(error.message?.contains("未知参数") == true)
        }

        assertThrows<IllegalArgumentException> {
            CliParser(arrayOf("run", "batch")).parse()
        }.also { error ->
            assertTrue(error.message?.contains("意外的位置参数") == true)
        }
    }

    @Test
    fun `legacy batch command fails with migration hint`() {
        val error =
            assertThrows<IllegalArgumentException> {
                CliParser(arrayOf("batch", "-a", "RANDOM")).parse()
            }

        assertTrue(error.message?.contains("旧入口") == true)
        assertTrue(error.message?.contains("run --mode batch") == true)
    }

    @Test
    fun `preset and algorithms together fail before execution`() {
        val error =
            assertThrows<IllegalArgumentException> {
                CliParser(
                    arrayOf("run", "--mode", "batch", "--preset", "quick", "-a", "RANDOM"),
                ).parse()
            }

        assertTrue(error.message?.contains("互斥") == true)
    }

    @Test
    fun `config plus cli override resolves batch algorithms and runs`() {
        val configFile =
            Files.createTempFile("cli-profile-", ".toml").toFile().apply {
                writeText(
                    """
                    defaultProfile = "batch_profile"

                    [profiles.batch_profile]
                    mode = "batch"
                    algorithms = ["PSO", "WOA"]
                    runs = 3

                    [profiles.batch_profile.batch]
                    cloudletCount = 100
                    population = 30
                    maxIter = 50
                    """.trimIndent(),
                )
            }

        val resolved =
            try {
                resolveRun(
                    CliParser.RunCommand(
                        configFile = configFile.absolutePath,
                        profile = "batch_profile",
                        algorithms = listOf("RANDOM"),
                        runs = 1,
                    ),
                )
            } finally {
                configFile.delete()
            }

        assertEquals(listOf("RANDOM"), resolved.selectedAlgorithmNames)
        assertEquals("batch_profile", resolved.profileName)
        assertEquals(1, resolved.experimentConfig.batch.runs)
    }

    @Test
    fun `resolver applies command preset before profile algorithms and profile preset`() {
        val configFile =
            Files.createTempFile("cli-priority-", ".toml").toFile().apply {
                writeText(
                    """
                    defaultProfile = "batch_profile"

                    [presets.fast]
                    algorithms = ["PSO", "WOA"]

                    [presets.profile_default]
                    algorithms = ["HHO"]

                    [profiles.batch_profile]
                    mode = "batch"
                    algorithms = ["RANDOM"]
                    runs = 3

                    [profiles.batch_profile.batch]
                    cloudletCount = 25

                    [profiles.preset_profile]
                    mode = "batch"
                    preset = "profile_default"
                    """.trimIndent(),
                )
            }

        val profileResolved =
            resolveRun(
                CliParser.RunCommand(
                    configFile = configFile.absolutePath,
                    profile = "batch_profile",
                ),
            )
        val commandPresetResolved =
            resolveRun(
                CliParser.RunCommand(
                    configFile = configFile.absolutePath,
                    profile = "batch_profile",
                    preset = "fast",
                ),
            )
        val profilePresetResolved =
            resolveRun(
                CliParser.RunCommand(
                    configFile = configFile.absolutePath,
                    profile = "preset_profile",
                ),
            )
        configFile.delete()

        assertEquals(listOf("RANDOM"), profileResolved.selectedAlgorithmNames)
        assertEquals(listOf("PSO", "WOA"), commandPresetResolved.selectedAlgorithmNames)
        assertEquals(listOf("HHO"), profilePresetResolved.selectedAlgorithmNames)
    }

    @Test
    fun `resolver keeps default multi task counts when no config provides tasks`() {
        val resolved =
            resolveRun(
                CliParser.RunCommand(
                    mode = "batch-multi",
                    algorithms = listOf("RANDOM"),
                    dryRun = true,
                ),
            )

        assertEquals(listOf(50, 100, 200, 500), resolved.taskCounts)
    }

    @Test
    fun `unknown realtime algorithm fails with valid candidates`() {
        val error =
            assertThrows<IllegalArgumentException> {
                parseRealtimeAlgorithms(listOf("NOT_REAL"))
            }

        assertTrue(error.message?.contains("未知的实时调度算法") == true)
        assertTrue(error.message?.contains(RealtimeAlgorithmType.MIN_LOAD.name) == true)
    }
}
