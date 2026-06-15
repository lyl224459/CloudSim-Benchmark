package buildlogic

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals

class CloudSimPlusVerifyLockTaskFunctionalTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `verify lock succeeds and compatibility mode skips`() {
        val fixture = fixture("verify-success")
        val (_, commit) = fixture.createGitCheckout()
        fixture.resolve("lock.txt").writeText(metadata(commit))
        fixture.resolve("metadata.txt").writeText(metadata(commit))
        fixture.writeBuild(verifyTasks())

        val verified = fixture.run("verifyLock")
        val skipped = fixture.run("verifySkipped")

        assertEquals(TaskOutcome.SUCCESS, verified.task(":verifyLock")?.outcome)
        assertContains(verified.output, "CloudSim Plus lock verified")
        assertContains(skipped.output, "lock verification skipped")
    }

    @Test
    fun `verify lock reports metadata and checkout drift`() {
        val metadataDrift = fixture("verify-metadata-drift")
        val (_, commit) = metadataDrift.createGitCheckout()
        metadataDrift.resolve("lock.txt").writeText(metadata(commit))
        metadataDrift.resolve("metadata.txt").writeText(metadata("b".repeat(40)))
        metadataDrift.writeBuild(verifyTasks())
        assertContains(metadataDrift.runAndFail("verifyLock").output, "metadata drift")

        val checkoutDrift = fixture("verify-checkout-drift")
        val (_, actualCommit) = checkoutDrift.createGitCheckout()
        checkoutDrift.resolve("lock.txt").writeText(metadata("c".repeat(40)))
        checkoutDrift.resolve("metadata.txt").writeText(metadata("c".repeat(40)))
        checkoutDrift.writeBuild(verifyTasks())
        val output = checkoutDrift.runAndFail("verifyLock").output
        assertContains(output, "checkout drift")
        assertContains(output, actualCommit)
    }

    @Test
    fun `verify lock reports submodule gitlink drift`() {
        val fixture = fixture("verify-gitlink-drift")
        val (_, commit) = fixture.createGitCheckout()
        fixture.git(fixture.project, "init")
        fixture.resolve("lock.txt").writeText(metadata(commit))
        fixture.resolve("metadata.txt").writeText(metadata(commit))
        fixture.writeBuild(verifyTasks())

        assertContains(fixture.runAndFail("verifyLock").output, "submodule gitlink drift")
    }

    private fun verifyTasks(): String =
        """
        import buildlogic.VerifyCloudSimPlusLockTask

        tasks.register('verifyLock', VerifyCloudSimPlusLockTask) {
            lockFile.set(layout.projectDirectory.file('lock.txt'))
            metadataFile.set(layout.projectDirectory.file('metadata.txt'))
            rootDir.set(layout.projectDirectory)
            sourceDir.set(layout.projectDirectory.dir('source'))
            enforceLock.set(true)
        }
        tasks.register('verifySkipped', VerifyCloudSimPlusLockTask) {
            lockFile.set(layout.projectDirectory.file('lock.txt'))
            metadataFile.set(layout.projectDirectory.file('metadata.txt'))
            rootDir.set(layout.projectDirectory)
            sourceDir.set(layout.projectDirectory.dir('source'))
            enforceLock.set(false)
        }
        """

    private fun fixture(name: String) = GradleTaskFixture(tempDir.resolve(name))
}
