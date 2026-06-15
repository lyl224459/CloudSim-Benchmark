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
        fixture.writeBuild(containerTask("definitely-missing-docker", ci = false))

        val action = fixture.run("containerSmoke")
        val firstCache = fixture.run("containerSmoke", "--configuration-cache")
        val secondCache = fixture.run("containerSmoke", "--configuration-cache")

        assertEquals(TaskOutcome.SUCCESS, action.task(":containerSmoke")?.outcome)
        assertContains(action.output, "skipping local containerImageSmoke")
        assertContains(firstCache.output, "Configuration cache entry stored")
        assertContains(secondCache.output, "Reusing configuration cache")
    }

    @Test
    fun `container smoke fails in CI when docker is missing`() {
        val fixture = fixture("container-ci")
        fixture.resolve("Containerfile").writeText("FROM scratch")
        fixture.writeBuild(containerTask("definitely-missing-docker", ci = true))

        assertContains(fixture.runAndFail("containerSmoke").output, "required for containerImageSmoke in CI")
    }

    @Test
    fun `container smoke executes ordinary docker and buildx commands`() {
        val fixture = fixture("container-commands")
        val commandLog = fixture.resolve("docker-commands.txt")
        val docker = fakeDocker(fixture, commandLog, "docker-ok", "")
        fixture.resolve("Containerfile").writeText("FROM scratch")
        fixture.writeBuild(
            containerTask(docker.absolutePath, ci = true, taskName = "ordinary") +
                containerTask(docker.absolutePath, ci = true, taskName = "buildx", buildx = true),
        )

        fixture.run("ordinary", "buildx")

        val commands = commandLog.readLines()
        assertEquals(2, commands.count { it.contains("--version") })
        assertContains(commands.joinToString("\n"), "build -t fixture-image")
        assertContains(commands.joinToString("\n"), "buildx build --load")
        assertContains(commands.joinToString("\n"), "run --rm --read-only")
        assertContains(commands.joinToString("\n"), "/app/runs:rw,nosuid,nodev")
        assertContains(commands.joinToString("\n"), "fixture-image --help")
    }

    @Test
    fun `container smoke propagates build and run failures`() {
        val buildFixture = fixture("container-build-fail")
        val buildDocker = fakeDocker(buildFixture, buildFixture.resolve("commands.txt"), "docker", "build")
        buildFixture.resolve("Containerfile").writeText("FROM scratch")
        buildFixture.writeBuild(containerTask(buildDocker.absolutePath, ci = true))

        val runFixture = fixture("container-run-fail")
        val runDocker = fakeDocker(runFixture, runFixture.resolve("commands.txt"), "docker", "run")
        runFixture.resolve("Containerfile").writeText("FROM scratch")
        runFixture.writeBuild(containerTask(runDocker.absolutePath, ci = true))

        assertContains(buildFixture.runAndFail("containerSmoke").output, "non-zero exit value")
        assertContains(runFixture.runAndFail("containerSmoke").output, "non-zero exit value")
    }

    private fun containerTask(
        docker: String,
        ci: Boolean,
        taskName: String = "containerSmoke",
        buildx: Boolean = false,
    ): String =
        """
        import buildlogic.ContainerImageSmokeTask

        tasks.register('$taskName', ContainerImageSmokeTask) {
            imageName.set('fixture-image')
            dockerExecutable.set('${docker.gradlePath()}')
            ci.set($ci)
            useBuildx.set($buildx)
            useGitHubActionsCache.set($buildx)
            contextDirectory.set(layout.projectDirectory)
            containerFile.set(layout.projectDirectory.file('Containerfile'))
        }
        """

    private fun fakeDocker(
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
