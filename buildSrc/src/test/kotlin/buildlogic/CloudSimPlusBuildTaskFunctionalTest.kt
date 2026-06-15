package buildlogic

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals

class CloudSimPlusBuildTaskFunctionalTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `build source stages minimal artifacts reacts to inputs and becomes up to date`() {
        val fixture = fixture("build-source-success")
        val maven = fakeMaven(fixture, succeeds = true)
        writeSource(fixture)
        fixture.writeBuild(buildTask(maven.absolutePath))

        val first = fixture.run("buildSource")
        val second = fixture.run("buildSource")
        fixture.resolve("source/pom.xml").appendText(System.lineSeparator())
        val changed = fixture.run("buildSource")

        assertEquals(TaskOutcome.SUCCESS, first.task(":buildSource")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":buildSource")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, changed.task(":buildSource")?.outcome)
        assertEquals(2, fixture.resolve("build/raw-m2").walkTopDown().count(File::isFile))
    }

    @Test
    fun `build source reports Maven failure and supports configuration cache`() {
        val failed = fixture("build-source-failed")
        val failingMaven = fakeMaven(failed, succeeds = false)
        writeSource(failed)
        failed.writeBuild(buildTask(failingMaven.absolutePath))
        assertContains(failed.runAndFail("buildSource").output, "non-zero exit value")

        val cached = fixture("build-source-cache")
        val maven = fakeMaven(cached, succeeds = true)
        writeSource(cached)
        cached.writeBuild(buildTask(maven.absolutePath))
        cached.run("buildSource", "--configuration-cache")
        assertContains(cached.run("buildSource", "--configuration-cache").output, "Reusing configuration cache")
    }

    private fun writeSource(fixture: GradleTaskFixture) {
        fixture.resolve("source").mkdirs()
        fixture.resolve("source/pom.xml").writeText(pom())
        fixture.resolve("metadata.txt").writeText(metadata())
    }

    private fun fakeMaven(
        fixture: GradleTaskFixture,
        succeeds: Boolean,
    ): File =
        fixture.fakeExecutable(
            "fake-maven",
            if (succeeds) {
                """
                if not exist target mkdir target
                echo jar>target\cloudsimplus-8.5.7.jar
                exit /b 0
                """
            } else {
                "exit /b 9"
            },
            if (succeeds) {
                """
                mkdir -p target
                printf jar > target/cloudsimplus-8.5.7.jar
                exit 0
                """
            } else {
                "exit 9"
            },
        )

    private fun buildTask(maven: String): String =
        """
        import buildlogic.BuildCloudSimPlusFromSourceTask

        tasks.register('buildSource', BuildCloudSimPlusFromSourceTask) {
            sourceDir.set(layout.projectDirectory.dir('source'))
            mavenCacheDir.set(layout.buildDirectory.dir('maven-cache'))
            rawMavenRepo.set(layout.buildDirectory.dir('raw-m2'))
            metadataFile.set(layout.projectDirectory.file('metadata.txt'))
            artifactGroup.set('org.cloudsimplus')
            artifactName.set('cloudsimplus')
            networkProxy.set('')
            mavenExecutableOverride.set('${maven.gradlePath()}')
            sourceFiles.from(layout.projectDirectory.file('source/pom.xml'))
        }
        """

    private fun fixture(name: String) = GradleTaskFixture(tempDir.resolve(name))
}
