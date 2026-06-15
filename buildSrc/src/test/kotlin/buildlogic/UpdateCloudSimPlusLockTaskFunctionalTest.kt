package buildlogic

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals

class UpdateCloudSimPlusLockTaskFunctionalTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `lock update requires explicit authorization`() {
        val fixture = fixture("lock-update-denied")
        fixture.resolve("metadata.txt").writeText(metadata())
        fixture.writeBuild(updateTask(allowed = false))

        assertContains(fixture.runAndFail("updateLock").output, "requires -Pcloudsimplus.ref")
    }

    @Test
    fun `lock update writes canonical content without rewriting unchanged output`() {
        val fixture = fixture("lock-update-success")
        fixture.resolve("metadata.txt").writeText(metadata())
        fixture.writeBuild(updateTask(allowed = true))

        val result = fixture.run("updateLock")
        val lock = fixture.resolve("lock.txt")
        val modified = lock.lastModified()

        assertEquals(TaskOutcome.SUCCESS, result.task(":updateLock")?.outcome)
        assertEquals(
            CloudSimPlusLockSupport.read(fixture.resolve("metadata.txt")),
            CloudSimPlusLockSupport.read(lock),
        )
        fixture.run("updateLock", "--rerun-tasks")
        assertEquals(modified, lock.lastModified())
    }

    @Test
    fun `metadata drift updates the lock and configuration cache is reusable`() {
        val fixture = fixture("lock-update-drift")
        fixture.resolve("metadata.txt").writeText(metadata())
        fixture.writeBuild(updateTask(allowed = true))
        fixture.run("updateLock")

        val updated = metadata("b".repeat(40))
        fixture.resolve("metadata.txt").writeText(updated)
        fixture.run("updateLock")

        assertEquals(
            CloudSimPlusLockSupport.read(fixture.resolve("metadata.txt")),
            CloudSimPlusLockSupport.read(fixture.resolve("lock.txt")),
        )
        fixture.run("updateLock", "--configuration-cache")
        assertContains(fixture.run("updateLock", "--configuration-cache").output, "Reusing configuration cache")
    }

    private fun updateTask(allowed: Boolean): String =
        """
        import buildlogic.UpdateCloudSimPlusLockTask

        tasks.register('updateLock', UpdateCloudSimPlusLockTask) {
            updateAllowed.set($allowed)
            metadataFile.set(layout.projectDirectory.file('metadata.txt'))
            lockFile.set(layout.projectDirectory.file('lock.txt'))
        }
        """

    private fun fixture(name: String) = GradleTaskFixture(tempDir.resolve(name))
}
