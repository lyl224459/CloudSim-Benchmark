package buildlogic

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarFile
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloudSimPlusSanitizeTaskFunctionalTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `sanitize outputs only target artifacts and becomes up to date`() {
        val fixture = fixture("sanitize")
        val inputJar = fixture.resolve("raw/org/cloudsimplus/cloudsimplus/8.5.7/cloudsimplus-8.5.7.jar")
        createJarWithClassPath(inputJar)
        inputJar.resolveSibling("cloudsimplus-8.5.7.pom").writeText("<project/>")
        fixture.writeBuild(sanitizeTask())

        val first = fixture.run("sanitize")
        val second = fixture.run("sanitize")
        val outputJar = fixture.resolve("sanitized/org/cloudsimplus/cloudsimplus/8.5.7/cloudsimplus-8.5.7.jar")

        assertEquals(TaskOutcome.SUCCESS, first.task(":sanitize")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":sanitize")?.outcome)
        assertEquals(2, fixture.resolve("sanitized").walkTopDown().count(File::isFile))
        JarFile(outputJar).use { jar ->
            assertFalse(jar.manifest.mainAttributes.containsKey(Attributes.Name.CLASS_PATH))
            assertTrue(jar.getEntry("fixture.txt") != null)
        }
    }

    @Test
    fun `sanitize reports missing artifact and supports configuration cache`() {
        val missing = fixture("sanitize-missing")
        missing.resolve("raw").mkdirs()
        missing.writeBuild(sanitizeTask())
        assertContains(missing.runAndFail("sanitize").output, "CloudSim Plus")

        val cached = fixture("sanitize-cache")
        val inputJar = cached.resolve("raw/org/cloudsimplus/cloudsimplus/8.5.7/cloudsimplus-8.5.7.jar")
        createJarWithClassPath(inputJar)
        inputJar.resolveSibling("cloudsimplus-8.5.7.pom").writeText("<project/>")
        cached.writeBuild(sanitizeTask())
        cached.run("sanitize", "--configuration-cache")
        assertContains(cached.run("sanitize", "--configuration-cache").output, "Reusing configuration cache")
    }

    private fun sanitizeTask(): String =
        """
        import buildlogic.SanitizeCloudSimPlusJarManifestTask

        tasks.register('sanitize', SanitizeCloudSimPlusJarManifestTask) {
            rawMavenRepo.set(layout.projectDirectory.dir('raw'))
            sanitizedMavenRepo.set(layout.projectDirectory.dir('sanitized'))
            artifactGroup.set('org.cloudsimplus')
            artifactName.set('cloudsimplus')
            artifactVersion.set('8.5.7')
        }
        """

    private fun fixture(name: String) = GradleTaskFixture(tempDir.resolve(name))
}
