package cli

import config.AlgorithmConfig
import config.ConfigurationManager
import config.ExperimentConfig
import config.ExperimentMode
import config.SystemConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class RunResolverBranchTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `load base configs treats blank config path as default config`() {
        val configs = RunResolver.loadBaseConfigs("   ")

        assertThat(configs.systemConfig).isEqualTo(SystemConfig.createDefault())
        assertThat(configs.experimentConfig).isEqualTo(ExperimentConfig.createDefault())
    }

    @Test
    fun `load base configs reads non blank config path`() {
        val configs = RunResolver.loadBaseConfigs("configs/examples/batch_test.toml")

        assertThat(configs.experimentConfig.defaultProfile).isEqualTo("batch_test")
        assertThat(configs.experimentConfig.profiles).containsKey("batch_test")
        assertThat(configs.systemConfig.output.resultsDir).isEqualTo("runs")
    }

    @Test
    fun `merge algorithm library skips invalid non empty library`() {
        val invalidLibrary =
            tempDir.resolve("algorithms.toml").apply {
                writeText(
                    """
                    [algorithms.RANDOM]
                    enabled = "not-a-boolean"
                    """.trimIndent(),
                )
            }
        val configs =
            LoadedRunConfigs(
                systemConfig = SystemConfig.createDefault(),
                experimentConfig = ExperimentConfig.createDefault(),
            )

        val merged = RunResolver.mergeAlgorithmLibrary(configs, invalidLibrary)

        assertThat(merged).isSameAs(configs)
    }

    @Test
    fun `merge algorithm library keeps user algorithm overrides above library defaults`() {
        val library =
            tempDir.resolve("algorithms.toml").apply {
                writeText(
                    """
                    [algorithms.PSO]
                    enabled = false
                    population = 99
                    maxIter = 88

                    [presets.library_batch]
                    algorithms = ["PSO"]
                    """.trimIndent(),
                )
            }
        val userPso = AlgorithmConfig(enabled = true, population = 7, maxIter = 8)
        val configs =
            LoadedRunConfigs(
                systemConfig = SystemConfig.createDefault(),
                experimentConfig =
                    ExperimentConfig.createDefault().copy(
                        algorithmConfigs = mapOf("PSO" to userPso),
                    ),
            )

        val merged = RunResolver.mergeAlgorithmLibrary(configs, library)

        assertThat(merged.experimentConfig.algorithmConfigs["PSO"]).isSameAs(userPso)
        assertThat(merged.experimentConfig.presets).containsKey("library_batch")
    }

    @Test
    fun `render experiment name handles realtime task token preset and empty algorithms`() {
        val base =
            RunResolver.resolve(
                CliParser.RunCommand(
                    mode = "realtime",
                    algorithms = listOf("MIN_LOAD"),
                    dryRun = true,
                ),
            )
        val output = base.output.copy(nameFormat = "{mode} {timestamp} {algorithms} {preset} {tasks}")
        val realtime =
            base.copy(
                algorithms = emptyList(),
                profile = base.profile.copy(presetName = "fast"),
                output = output,
            )
        val realtimeMulti =
            realtime.copy(
                mode = "realtime-multi",
                taskCounts = listOf(5, 10),
            )

        assertThat(RunResolver.renderExperimentName(realtime, "20260616"))
            .isEqualTo("realtime_20260616_ALL_fast_200")
        assertThat(RunResolver.renderExperimentName(realtimeMulti, "20260616"))
            .isEqualTo("realtime-multi_20260616_ALL_fast_5-10")
    }

    @Test
    fun `apply run overrides falls back to experiment mode seed output and selected algorithms`() {
        val base =
            ConfigurationManager.LoadedConfigs(
                systemConfig = SystemConfig.createDefault(),
                experimentConfig =
                    ExperimentConfig.createDefault().copy(
                        mode = ExperimentMode.REALTIME_MULTI,
                        randomSeed = 99L,
                    ),
            )
        val selectedAlgorithms = mapOf("RANDOM" to AlgorithmConfig(enabled = true, population = 3, maxIter = 4))

        val resolved =
            applyRunOverrides(
                configs = base,
                command = CliParser.RunCommand(),
                selectionAlgorithmConfigs = selectedAlgorithms,
            )

        assertThat(resolved.experimentConfig.mode).isEqualTo(ExperimentMode.REALTIME_MULTI)
        assertThat(resolved.experimentConfig.randomSeed).isEqualTo(99L)
        assertThat(resolved.experimentConfig.algorithmConfigs).isSameAs(selectedAlgorithms)
        assertThat(resolved.systemConfig.output.resultsDir).isEqualTo(base.systemConfig.output.resultsDir)
    }

    @Test
    fun `apply run overrides applies command mode seed and output directory`() {
        val base = ConfigurationManager.LoadedConfigs(SystemConfig.createDefault(), ExperimentConfig.createDefault())

        val resolved =
            applyRunOverrides(
                configs = base,
                command =
                    CliParser.RunCommand(
                        mode = "batch-multi",
                        randomSeed = 7L,
                        outputDir = "runs/custom",
                    ),
            )

        assertThat(resolved.experimentConfig.mode).isEqualTo(ExperimentMode.BATCH_MULTI)
        assertThat(resolved.experimentConfig.randomSeed).isEqualTo(7L)
        assertThat(resolved.systemConfig.output.resultsDir).isEqualTo("runs/custom")
    }
}
