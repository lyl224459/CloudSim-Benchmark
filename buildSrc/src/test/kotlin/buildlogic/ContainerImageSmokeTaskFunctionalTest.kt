package buildlogic

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ContainerImageSmokeTaskFunctionalTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `container smoke skips locally and configuration cache is reusable`() {
        val fixture = fixture("container-local")
        fixture.resolve("Containerfile").writeText("FROM scratch")
        fixture.writeBuild(containerTask("definitely-missing-podman", ci = false))

        val action = fixture.run("containerSmoke")
        val firstCache = fixture.run("containerSmoke", "--configuration-cache")
        val secondCache = fixture.run("containerSmoke", "--configuration-cache")

        assertEquals(TaskOutcome.SUCCESS, action.task(":containerSmoke")?.outcome)
        assertContains(action.output, "skipping local containerImageSmoke")
        assertContains(firstCache.output, "Configuration cache entry stored")
        assertContains(secondCache.output, "Reusing configuration cache")
    }

    @Test
    fun `container smoke fails in CI when podman is missing`() {
        val fixture = fixture("container-ci")
        fixture.resolve("Containerfile").writeText("FROM scratch")
        fixture.writeBuild(containerTask("definitely-missing-podman", ci = true))

        assertContains(fixture.runAndFail("containerSmoke").output, "required for containerImageSmoke in CI")
    }

    @Test
    fun `container smoke executes podman build run and inspect commands`() {
        val fixture = fixture("container-commands")
        val commandLog = fixture.resolve("podman-commands.txt")
        val podman = fakePodman(fixture, commandLog, "podman-ok", "")
        fixture.resolve("Containerfile").writeText("FROM scratch")
        fixture.writeBuild(containerTask(podman.absolutePath, ci = true, taskName = "ordinary"))

        fixture.run("ordinary")

        val commands = commandLog.readLines()
        assertEquals(1, commands.count { it.contains("--version") })
        assertContains(commands.joinToString("\n"), "build -t fixture-image")
        assertContains(commands.joinToString("\n"), "run --rm --read-only")
        assertContains(commands.joinToString("\n"), "type=tmpfs,destination=/app/runs,tmpfs-mode=1777")
        assertContains(commands.joinToString("\n"), "fixture-image --help")
    }

    @Test
    fun `container smoke propagates build and run failures`() {
        val buildFixture = fixture("container-build-fail")
        val buildPodman = fakePodman(buildFixture, buildFixture.resolve("commands.txt"), "podman", "build")
        buildFixture.resolve("Containerfile").writeText("FROM scratch")
        buildFixture.writeBuild(containerTask(buildPodman.absolutePath, ci = true))

        val runFixture = fixture("container-run-fail")
        val runPodman = fakePodman(runFixture, runFixture.resolve("commands.txt"), "podman", "run")
        runFixture.resolve("Containerfile").writeText("FROM scratch")
        runFixture.writeBuild(containerTask(runPodman.absolutePath, ci = true))

        assertContains(buildFixture.runAndFail("containerSmoke").output, "non-zero exit value")
        assertContains(runFixture.runAndFail("containerSmoke").output, "non-zero exit value")
    }

    private fun containerTask(
        podman: String,
        ci: Boolean,
        taskName: String = "containerSmoke",
    ): String =
        """
        import buildlogic.ContainerImageSmokeTask

        tasks.register('$taskName', ContainerImageSmokeTask) {
            imageName.set('fixture-image')
            containerExecutable.set('${podman.gradlePath()}')
            ci.set($ci)
            contextDirectory.set(layout.projectDirectory)
            containerFile.set(layout.projectDirectory.file('Containerfile'))
        }
        """

    private fun fakePodman(
        fixture: GradleTaskFixture,
        log: File,
        name: String,
        failCommand: String,
    ): File =
        fixture.fakeExecutable(
            name,
            """
            echo %*>>"${log.absolutePath}"
            if "%1"=="$failCommand" exit /b 9
            exit /b 0
            """,
            """
            echo "${'$'}*" >> "${log.absolutePath}"
            if [ "${'$'}1" = "$failCommand" ]; then exit 9; fi
            exit 0
            """,
        )

    private fun fixture(name: String) = GradleTaskFixture(tempDir.resolve(name))
}
