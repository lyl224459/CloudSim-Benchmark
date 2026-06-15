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
    fun `locked source verification becomes up to date and supports configuration cache`() {
        val fixture = fixture("locked-success")
        val (source, commit) = fixture.createGitCheckout()
        fixture.git(source, "tag", "v8.5.7")
        fixture.resolve("lock.txt").writeText(metadata(commit))
        fixture.writeBuild(lockedTask())

        val first = fixture.run("verifyLocked", "--configuration-cache")
        val second = fixture.run("verifyLocked", "--configuration-cache")

        assertEquals(TaskOutcome.SUCCESS, first.task(":verifyLocked")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":verifyLocked")?.outcome)
        assertContains(second.output, "Reusing configuration cache")
        assertEquals(metadata(commit).normalizedLines(), fixture.resolve("build/locked-version.txt").readText().normalizedLines())
    }

    @Test
    fun `locked source verification reports missing source and checkout drift`() {
        val missing = fixture("locked-missing")
        missing.resolve("lock.txt").writeText(metadata())
        missing.writeBuild(lockedTask(source = "missing-source"))
        assertContains(missing.runAndFail("verifyLocked").output, "git submodule update --init --recursive")

        val drift = fixture("locked-drift")
        val (source, _) = drift.createGitCheckout()
        drift.git(source, "tag", "v8.5.7")
        drift.resolve("lock.txt").writeText(metadata("b".repeat(40)))
        drift.writeBuild(lockedTask())
        assertContains(drift.runAndFail("verifyLocked").output, "checkout drift")
    }

    @Test
    fun `locked source verification reports tag and POM version drift`() {
        val tagDrift = fixture("locked-tag-drift")
        val (tagSource, tagCommit) = tagDrift.createGitCheckout()
        tagDrift.git(tagSource, "tag", "v8.5.6")
        tagDrift.resolve("lock.txt").writeText(metadata(tagCommit))
        tagDrift.writeBuild(lockedTask())
        assertContains(tagDrift.runAndFail("verifyLocked").output, "tag drift")

        val versionDrift = fixture("locked-version-drift")
        val (versionSource, versionCommit) = versionDrift.createGitCheckout()
        versionDrift.git(versionSource, "tag", "v8.5.7")
        versionSource.resolve("pom.xml").writeText("<project><version>8.5.6</version></project>")
        versionDrift.resolve("lock.txt").writeText(metadata(versionCommit))
        versionDrift.writeBuild(lockedTask())
        assertContains(versionDrift.runAndFail("verifyLocked").output, "POM version drift")
    }

    @Test
    fun `locked source verification reports parent repository gitlink drift`() {
        val fixture = fixture("locked-gitlink-drift")
        val (source, commit) = fixture.createGitCheckout()
        fixture.git(source, "tag", "v8.5.7")
        fixture.git(fixture.project, "init")
        fixture.resolve("lock.txt").writeText(metadata(commit))
        fixture.writeBuild(lockedTask())

        assertContains(fixture.runAndFail("verifyLocked").output, "submodule gitlink drift")
    }

    @Test
    fun `mutable source preparation always runs for explicit ref`() {
        val fixture = fixture("mutable-success")
        val (source, commit) = fixture.createGitCheckout()
        fixture.git(source, "tag", "v8.5.7")
        fixture.writeBuild(mutableTask())

        val first = fixture.run("prepareMutable")
        val second = fixture.run("prepareMutable")

        assertEquals(TaskOutcome.SUCCESS, first.task(":prepareMutable")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, second.task(":prepareMutable")?.outcome)
        assertEquals(metadata(commit).normalizedLines(), fixture.resolve("build/mutable-version.txt").readText().normalizedLines())
    }

    private fun lockedTask(source: String = "source"): String =
        """
        import buildlogic.VerifyLockedCloudSimPlusSourceTask

        tasks.register('verifyLocked', VerifyLockedCloudSimPlusSourceTask) {
            lockFile.set(layout.projectDirectory.file('lock.txt'))
            sourcePom.from(layout.projectDirectory.file('$source/pom.xml'))
            gitStateFiles.from(layout.projectDirectory.files('$source/.git/HEAD', '$source/.git/index'))
            rootDir.set(layout.projectDirectory)
            sourceDir.set(layout.projectDirectory.dir('$source'))
            versionFile.set(layout.buildDirectory.file('locked-version.txt'))
        }
        """

    private fun mutableTask(): String =
        """
        import buildlogic.PrepareMutableCloudSimPlusSourceTask

        tasks.register('prepareMutable', PrepareMutableCloudSimPlusSourceTask) {
            repositoryUrl.set('https://invalid.example/cloudsimplus.git')
            autoUpdate.set(false)
            offline.set(true)
            requestedRef.set('v8.5.7')
            networkProxy.set('')
            gitTimeoutSeconds.set(5L)
            rootDir.set(layout.projectDirectory)
            sourceDir.set(layout.projectDirectory.dir('source'))
            versionFile.set(layout.buildDirectory.file('mutable-version.txt'))
        }
        """

    private fun fixture(name: String) = GradleTaskFixture(tempDir.resolve(name))
}

private fun String.normalizedLines(): String = replace("\r\n", "\n")
