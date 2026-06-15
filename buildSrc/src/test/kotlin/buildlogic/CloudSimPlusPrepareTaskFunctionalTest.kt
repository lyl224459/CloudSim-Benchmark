package buildlogic

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals

class CloudSimPlusPrepareTaskFunctionalTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `prepare locked offline checkout always runs and writes metadata`() {
        val fixture = fixture("prepare-success")
        val (_, commit) = fixture.createGitCheckout()
        fixture.resolve("lock.txt").writeText(metadata(commit))
        fixture.writeBuild(prepareTask())

        val first = fixture.run("prepareSource")
        val second = fixture.run("prepareSource")

        assertEquals(TaskOutcome.SUCCESS, first.task(":prepareSource")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, second.task(":prepareSource")?.outcome)
        assertEquals(metadata(commit).normalizedLines(), fixture.resolve("build/cloudsimplus-version.txt").readText().normalizedLines())
    }

    @Test
    fun `prepare reports missing offline checkout and supports configuration cache`() {
        val missing = fixture("prepare-missing")
        missing.resolve("lock.txt").writeText(metadata())
        missing.writeBuild(prepareTask(source = "missing-source"))
        assertContains(missing.runAndFail("prepareSource").output, "CloudSim Plus source is missing")

        val cached = fixture("prepare-cache")
        val (_, commit) = cached.createGitCheckout()
        cached.resolve("lock.txt").writeText(metadata(commit))
        cached.writeBuild(prepareTask())
        cached.run("prepareSource", "--configuration-cache")
        assertContains(cached.run("prepareSource", "--configuration-cache").output, "Reusing configuration cache")
    }

    private fun prepareTask(source: String = "source"): String =
        """
        import buildlogic.PrepareCloudSimPlusSourceTask

        tasks.register('prepareSource', PrepareCloudSimPlusSourceTask) {
            repositoryUrl.set('https://invalid.example/cloudsimplus.git')
            autoUpdate.set(false)
            offline.set(true)
            requestedRef.set('')
            lockFile.set(layout.projectDirectory.file('lock.txt'))
            enforceLock.set(true)
            networkProxy.set('')
            gitTimeoutSeconds.set(5L)
            rootDir.set(layout.projectDirectory)
            sourceDir.set(layout.projectDirectory.dir('$source'))
            versionFile.set(layout.buildDirectory.file('cloudsimplus-version.txt'))
        }
        """

    private fun fixture(name: String) = GradleTaskFixture(tempDir.resolve(name))
}

private fun String.normalizedLines(): String = replace("\r\n", "\n")
