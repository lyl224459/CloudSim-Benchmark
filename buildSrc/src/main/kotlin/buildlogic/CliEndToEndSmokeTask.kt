package buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

internal data class CliSmokeScenario(
    val name: String,
    val arguments: List<String>,
    val expectedText: String,
)

internal object CliEndToEndSmokeSupport {
    fun scenarios(
        config: String,
        dryRunDirectory: File,
    ): List<CliSmokeScenario> =
        listOf(
            CliSmokeScenario("help", listOf("--help"), "CloudSim-Benchmark CLI"),
            CliSmokeScenario("list-batch", listOf("list", "algorithms", "--mode", "batch"), "可用算法 (batch)"),
            CliSmokeScenario("config-validate", listOf("config", "validate", "--config", config), "配置验证通过"),
            dryRunScenario("batch-dry-run", config, "batch_small", dryRunDirectory.resolve("batch")),
            dryRunScenario("realtime-dry-run", config, "realtime_smoke", dryRunDirectory.resolve("realtime")),
        )

    fun verifyResult(
        scenario: CliSmokeScenario,
        exitValue: Int,
        output: String,
    ) {
        if (exitValue != 0) {
            throw GradleException("CLI smoke '${scenario.name}' exited with $exitValue:\n$output")
        }
        if (!output.contains(scenario.expectedText)) {
            throw GradleException(
                "CLI smoke '${scenario.name}' did not contain '${scenario.expectedText}':\n$output",
            )
        }
    }

    private fun dryRunScenario(
        name: String,
        config: String,
        profile: String,
        output: File,
    ): CliSmokeScenario =
        CliSmokeScenario(
            name = name,
            arguments =
                listOf(
                    "run",
                    "--config",
                    config,
                    "--profile",
                    profile,
                    "--dry-run",
                    "--output",
                    output.absolutePath,
                ),
            expectedText = "Dry run: 不会创建实验目录或结果文件",
        )
}

@DisableCachingByDefault(because = "Executes the packaged CLI as an end-to-end smoke check")
abstract class CliEndToEndSmokeTask
    @Inject
    constructor(
        private val execOperations: ExecOperations,
    ) : DefaultTask() {
        @get:InputFile
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val executableJar: RegularFileProperty

        @get:InputFile
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val exampleConfig: RegularFileProperty

        @get:Input
        abstract val javaExecutable: Property<String>

        @get:Input
        abstract val jvmArguments: ListProperty<String>

        @get:Internal
        abstract val dryRunRoot: DirectoryProperty

        @get:OutputFile
        abstract val reportFile: RegularFileProperty

        init {
            outputs.upToDateWhen { false }
        }

        @TaskAction
        fun smoke() {
            val config = exampleConfig.get().asFile.absolutePath
            val dryRunDirectory = dryRunRoot.get().asFile
            dryRunDirectory.deleteRecursively()
            val scenarios = CliEndToEndSmokeSupport.scenarios(config, dryRunDirectory)

            val report =
                buildString {
                    scenarios.forEach { scenario ->
                        appendLine("## ${scenario.name}")
                        appendLine(execute(scenario).trim())
                        appendLine()
                    }
                }
            if (dryRunDirectory.exists()) {
                throw GradleException("CLI dry-run unexpectedly created output directory: $dryRunDirectory")
            }
            reportFile.get().asFile.apply {
                parentFile.mkdirs()
                writeText(report)
            }
            logger.lifecycle("CLI end-to-end smoke passed: ${reportFile.get().asFile}")
        }

        private fun execute(scenario: CliSmokeScenario): String {
            val output = ByteArrayOutputStream()
            val result =
                execOperations.exec {
                    commandLine(
                        listOf(javaExecutable.get()) +
                            jvmArguments.get() +
                            listOf("-jar", executableJar.get().asFile.absolutePath) +
                            scenario.arguments,
                    )
                    standardOutput = output
                    errorOutput = output
                    isIgnoreExitValue = true
                }
            val text = output.toString(Charsets.UTF_8)
            CliEndToEndSmokeSupport.verifyResult(scenario, result.exitValue, text)
            return text
        }
    }
