package buildlogic

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CliEndToEndSmokeTaskFunctionalTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `cli smoke writes report keeps dry run clean and supports configuration cache`() {
        val fixture = fixture("cli-smoke-success")
        val java = fakeJava(fixture, output = successfulOutput(), exitCode = 0)
        writeInputs(fixture)
        fixture.writeBuild(cliTask(java.absolutePath))

        val result = fixture.run("cliSmoke")

        assertEquals(TaskOutcome.SUCCESS, result.task(":cliSmoke")?.outcome)
        assertContains(fixture.resolve("build/cli-smoke.txt").readText(), "## realtime-dry-run")
        assertFalse(fixture.resolve("build/dry-run").exists())

        fixture.run("cliSmoke", "--configuration-cache")
        assertContains(fixture.run("cliSmoke", "--configuration-cache").output, "Reusing configuration cache")
    }

    @Test
    fun `cli smoke reports nonzero exit and missing expected output`() {
        val failed = fixture("cli-smoke-exit")
        val failedJava = fakeJava(failed, output = "failed", exitCode = 7)
        writeInputs(failed)
        failed.writeBuild(cliTask(failedJava.absolutePath))
        assertContains(failed.runAndFail("cliSmoke").output, "exited with 7")

        val missing = fixture("cli-smoke-missing-output")
        val missingJava = fakeJava(missing, output = "wrong", exitCode = 0)
        writeInputs(missing)
        missing.writeBuild(cliTask(missingJava.absolutePath))
        assertContains(missing.runAndFail("cliSmoke").output, "did not contain")
    }

    private fun writeInputs(fixture: GradleTaskFixture) {
        fixture.resolve("fixture.jar").writeText("unused")
        fixture.resolve("config.toml").writeText("[profiles]")
    }

    private fun fakeJava(
        fixture: GradleTaskFixture,
        output: String,
        exitCode: Int,
    ): File =
        fixture.fakeExecutable(
            "fake-java",
            """
            echo $output
            exit /b $exitCode
            """,
            """
            echo '$output'
            exit $exitCode
            """,
        )

    private fun successfulOutput(): String =
        listOf(
            "CloudSim-Benchmark CLI",
            "可用算法 (batch)",
            "配置验证通过",
            "Dry run: 不会创建实验目录或结果文件",
        ).joinToString(" ")

    private fun cliTask(java: String): String =
        """
        import buildlogic.CliEndToEndSmokeTask

        tasks.register('cliSmoke', CliEndToEndSmokeTask) {
            executableJar.set(layout.projectDirectory.file('fixture.jar'))
            exampleConfig.set(layout.projectDirectory.file('config.toml'))
            javaExecutable.set('${java.gradlePath()}')
            jvmArguments.set([])
            dryRunRoot.set(layout.buildDirectory.dir('dry-run'))
            reportFile.set(layout.buildDirectory.file('cli-smoke.txt'))
        }
        """

    private fun fixture(name: String) = GradleTaskFixture(tempDir.resolve(name))
}
