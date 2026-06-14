package cli

import config.ExperimentConfig
import config.PresetConfig
import config.ProfileConfig
import config.SystemConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DryRunPrinterTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `dry run prints stable batch field order`() {
        val output = RecordingDryRunOutput()

        DryRunPrinter.printDryRun(resolved("batch"), output)

        assertThat(output.results.take(7)).containsExactly(
            "Dry run: 不会创建实验目录或结果文件",
            "模式: batch",
            "Profile: profile-a",
            "输出目录: ${tempDir.absolutePath}",
            "随机种子: 0",
            "运行次数: 1",
            "任务数列表: 50, 100",
        )
        assertThat(output.results.last()).isEqualTo("CSV 输出: enabled=true, delimiter=','")
    }

    @Test
    fun `dry run prints realtime scheduling and topology snapshots`() {
        val output = RecordingDryRunOutput()

        DryRunPrinter.printDryRun(resolved("realtime"), output)

        assertThat(output.results).anyMatch { it.startsWith("实时到达/调度:") }
        assertThat(output.results).anyMatch { it.startsWith("物理拓扑/数据本地性:") }
    }

    @Test
    fun `usage profiles presets and algorithm listings use output sink`() {
        val output = RecordingDryRunOutput()
        val config =
            ExperimentConfig(
                defaultProfile = "zeta",
                profiles =
                    mapOf(
                        "zeta" to ProfileConfig(mode = "batch", algorithms = listOf("RANDOM")),
                    ),
            )

        printUsage(output)
        DryRunPrinter.printProfiles(config, output)
        DryRunPrinter.printPresets(mapOf("fast" to PresetConfig(algorithms = listOf("RANDOM"))), output)
        DryRunPrinter.printAlgorithms("batch", output)

        assertThat(output.infos.first()).isEqualTo("CloudSim-Benchmark CLI")
        assertThat(output.results).contains(
            "可用 profiles:",
            "  zeta -> mode=batch, algorithms=RANDOM",
            "默认 profile: zeta",
            "可用预设:",
            "  fast = RANDOM",
        )
        assertThat(output.results.last()).startsWith("可用算法 (batch):")
    }

    @Test
    fun `invalid algorithm listing mode keeps error keyword`() {
        val error =
            assertThrows<IllegalArgumentException> {
                DryRunPrinter.printAlgorithms("multi", RecordingDryRunOutput())
            }

        assertThat(error.message).contains("batch 或 realtime")
    }

    @Test
    fun `resolved json keeps stable primary field order`() {
        val json = DryRunPrinter.resolvedJson(resolved("batch"), tempDir, "20260101_000000")

        assertThat(json).contains("\"mode\": \"batch\"", "\"timestamp\": \"20260101_000000\"")
        assertThat(json.indexOf("\"mode\"")).isLessThan(json.indexOf("\"profile\""))
        assertThat(json.indexOf("\"profile\"")).isLessThan(json.indexOf("\"algorithms\""))
    }

    private fun resolved(mode: String) =
        ResolvedExperimentConfig(
            command = CliParser.RunCommand(mode = mode),
            systemConfig = SystemConfig(),
            experimentConfig = ExperimentConfig(),
            mode = mode,
            profile = ResolvedProfile(name = "profile-a", presetName = null),
            algorithms = emptyList(),
            taskCounts = listOf(50, 100),
            execution = ResolvedExecutionOptions(useCoroutines = false, maxConcurrency = 1, dryRun = true),
            output =
                ResolvedOutputConfig(
                    resultsDir = tempDir.absolutePath,
                    csvEnabled = true,
                    csvDelimiter = ",",
                    nameFormat = "{mode}_{timestamp}",
                ),
        )
}

private class RecordingDryRunOutput : DryRunOutput {
    val infos = mutableListOf<String>()
    val results = mutableListOf<String>()

    override fun info(
        message: String,
        vararg args: Any?,
    ) {
        infos += format(message, args)
    }

    override fun result(
        message: String,
        vararg args: Any?,
    ) {
        results += format(message, args)
    }

    private fun format(
        message: String,
        args: Array<out Any?>,
    ): String {
        var formatted = message
        args.forEach { argument -> formatted = formatted.replaceFirst("{}", argument.toString()) }
        return formatted
    }
}
