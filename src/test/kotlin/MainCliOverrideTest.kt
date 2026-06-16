import cli.CliParser
import cli.RunResolver
import cli.normalizeMode
import cli.supportedModes
import config.BatchAlgorithmType
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
    fun `run parser rejects missing option values with original keyword`() {
        val error =
            assertThrows<IllegalArgumentException> {
                CliParser(arrayOf("run", "--mode")).parse()
            }

        assertTrue(error.message?.contains("参数需要指定值") == true)
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
    fun `resolver rejects invalid mode with valid candidates`() {
        val error =
            assertThrows<IllegalArgumentException> {
                resolveRun(CliParser.RunCommand(mode = "unknown", dryRun = true))
            }

        assertTrue(error.message?.contains("无效运行模式") == true)
        assertTrue(error.message?.contains("batch-multi") == true)
    }

    @Test
    fun `resolver requires profile selection when config has no default`() {
        val configFile =
            Files.createTempFile("cli-missing-profile-", ".toml").toFile().apply {
                writeText(
                    """
                    [profiles.batch_profile]
                    mode = "batch"
                    algorithms = ["RANDOM"]
                    """.trimIndent(),
                )
            }

        val error =
            try {
                assertThrows<IllegalArgumentException> {
                    resolveRun(CliParser.RunCommand(configFile = configFile.absolutePath, dryRun = true))
                }
            } finally {
                configFile.delete()
            }

        assertTrue(error.message?.contains("--profile") == true)
        assertTrue(error.message?.contains("batch_profile") == true)
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

    @Test
    fun `parser covers list config help flags and aliases`() {
        assertEquals(CliParser.HelpCommand, CliParser(emptyArray()).parse())
        assertEquals(CliParser.HelpCommand, CliParser(arrayOf("--help")).parse())
        assertEquals(
            CliParser.ListAlgorithmsCommand("batch"),
            CliParser(arrayOf("list", "algorithms", "-m", "b")).parse(),
        )
        assertEquals(
            CliParser.ListProfilesCommand("experiment.toml"),
            CliParser(arrayOf("list", "profiles", "--config=experiment.toml")).parse(),
        )
        assertEquals(
            CliParser.ListPresetsCommand("experiment.toml"),
            CliParser(arrayOf("list", "presets", "-c", "experiment.toml")).parse(),
        )
        assertEquals(
            CliParser.ConfigValidateCommand("experiment.toml"),
            CliParser(arrayOf("config", "validate", "--config", "experiment.toml")).parse(),
        )
        assertEquals(
            CliParser.ConfigPrintCommand("experiment.toml", "quoted profile"),
            CliParser(arrayOf("config", "print", "--config=experiment.toml", "--profile=quoted profile")).parse(),
        )
        assertEquals("batch-multi", normalizeMode("batch_multi"))
        assertEquals(setOf("batch", "realtime", "batch-multi", "realtime-multi"), supportedModes())
    }

    @Test
    fun `parser rejects invalid subcommands values and flag assignments`() {
        listOf(
            arrayOf("unknown"),
            arrayOf("list"),
            arrayOf("list", "unknown"),
            arrayOf("list", "algorithms", "--mode", "batch-multi"),
            arrayOf("config"),
            arrayOf("config", "unknown"),
            arrayOf("run", "--verbose=yes"),
            arrayOf("run", "--runs", "zero"),
            arrayOf("run", "--tasks", "1,-2"),
            arrayOf("run", "--seed", "not-long"),
        ).forEach { arguments ->
            assertThrows<IllegalArgumentException> {
                CliParser(arguments).parse()
            }
        }
    }

    @Test
    fun `resolver public compatibility entries cover defaults names and typed algorithms`() {
        val defaults = RunResolver.loadBaseConfigs(null)
        val merged = RunResolver.mergeAlgorithmLibrary(defaults)
        val resolved =
            resolveRun(
                CliParser.RunCommand(
                    mode = "batch",
                    algorithms = listOf("RANDOM"),
                    outputDir = "custom output",
                ),
            )

        assertEquals(BatchAlgorithmType.RANDOM, RunResolver.parseBatchAlgorithms(listOf("RANDOM")).single())
        assertEquals(RealtimeAlgorithmType.MIN_LOAD, RunResolver.parseRealtimeAlgorithms(listOf("MIN_LOAD")).single())
        assertEquals("batch_20260615_RANDOM", RunResolver.renderExperimentName(resolved, "20260615"))
        assertEquals(
            "batch-multi_20260615_RANDOM_none_50-100",
            RunResolver.renderExperimentName(
                resolved.copy(
                    mode = "batch-multi",
                    taskCounts = listOf(50, 100),
                    output =
                        resolved.output.copy(
                            nameFormat = "{mode}_{timestamp}_{algorithms}_{preset}_{tasks}",
                        ),
                ),
                "20260615",
            ),
        )
        assertEquals(
            "custom_20260615",
            RunResolver.renderExperimentName(
                resolved.copy(mode = "custom", output = resolved.output.copy(nameFormat = "///")),
                "20260615",
            ),
        )
        assertEquals("custom output", resolved.output.resultsDir)
        assertEquals(100, defaults.experimentConfig.batch.cloudletCount)
        assertTrue(merged.experimentConfig.algorithmConfigs.isNotEmpty())
    }
}
