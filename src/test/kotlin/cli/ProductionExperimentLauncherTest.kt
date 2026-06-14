package cli

import config.BatchConfig
import config.ExperimentConfig
import config.RealtimeConfig
import config.SystemConfig
import datacenter.BatchExperimentRequest
import datacenter.RealtimeExperimentRequest
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import util.ExperimentOutputContext
import java.io.File

class ProductionExperimentLauncherTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `launcher maps all production modes to matching requests`(): Unit =
        runBlocking {
            val batch = mutableListOf<BatchExperimentRequest>()
            val batchMulti = mutableListOf<BatchExperimentRequest>()
            val realtime = mutableListOf<RealtimeExperimentRequest>()
            val realtimeMulti = mutableListOf<RealtimeExperimentRequest>()
            val launcher =
                ProductionExperimentLauncher(
                    ProductionExperimentLaunchServices(
                        runBatch = batch::add,
                        runBatchMulti = batchMulti::add,
                        runRealtime = realtime::add,
                        runRealtimeMulti = realtimeMulti::add,
                    ),
                )
            val output = ExperimentOutputContext(tempDir)

            launcher.launch(resolved("batch"), output)
            launcher.launch(resolved("batch-multi"), output)
            launcher.launch(resolved("realtime"), output)
            launcher.launch(resolved("realtime-multi"), output)

            assertThat(batch.single().batch.cloudletCount).isEqualTo(12)
            assertThat(batchMulti.single().batch.cloudletCounts).containsExactly(50, 100)
            assertThat(realtime.single().realtime.cloudletCount).isEqualTo(24)
            assertThat(realtimeMulti.single().realtime.cloudletCounts).containsExactly(50, 100)
            assertThat(batch.single().execution.randomSeed).isEqualTo(37L)
            assertThat(realtime.single().execution.outputContext).isSameAs(output)
        }

    @Test
    fun `launcher rejects unknown mode and missing output directory`() {
        val launcher = ProductionExperimentLauncher(recordingServices())

        val unknown =
            assertThrows<IllegalArgumentException> {
                runBlocking { launcher.launch(resolved("unknown"), ExperimentOutputContext(tempDir)) }
            }
        val missingOutput =
            assertThrows<IllegalStateException> {
                runBlocking { launcher.launch(resolved("batch"), ExperimentOutputContext(null)) }
            }

        assertThat(unknown.message).contains("未知的运行模式")
        assertThat(missingOutput.message).contains("输出目录")
    }

    private fun recordingServices() =
        ProductionExperimentLaunchServices(
            runBatch = {},
            runBatchMulti = {},
            runRealtime = {},
            runRealtimeMulti = {},
        )

    private fun resolved(mode: String) =
        ResolvedExperimentConfig(
            command = CliParser.RunCommand(mode = mode),
            systemConfig = SystemConfig(),
            experimentConfig =
                ExperimentConfig(
                    randomSeed = 37L,
                    batch = BatchConfig(cloudletCount = 12),
                    realtime = RealtimeConfig(cloudletCount = 24),
                ),
            mode = mode,
            profile = ResolvedProfile(name = "profile-a", presetName = null),
            algorithms = emptyList(),
            taskCounts = listOf(50, 100),
            execution = ResolvedExecutionOptions(useCoroutines = false, maxConcurrency = 2, dryRun = false),
            output =
                ResolvedOutputConfig(
                    resultsDir = tempDir.absolutePath,
                    csvEnabled = true,
                    csvDelimiter = ",",
                    nameFormat = "{mode}_{timestamp}",
                ),
        )
}
