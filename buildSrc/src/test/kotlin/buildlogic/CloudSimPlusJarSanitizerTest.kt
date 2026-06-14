package buildlogic

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CloudSimPlusJarSanitizerTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `sanitize repository copies raw repo and removes runtime jar class path`() {
        val rawRepo = tempDir.resolve("raw")
        val sanitizedRepo = tempDir.resolve("sanitized")
        val rawJar = rawRepo.resolve("org/cloudsimplus/cloudsimplus/8.5.7/cloudsimplus-8.5.7.jar")
        rawJar.parentFile.mkdirs()
        createJar(rawJar, classPath = "bad.jar")
        rawJar.resolveSibling("cloudsimplus-8.5.7-sources.jar").also(::createJar)
        rawJar.resolveSibling("cloudsimplus-8.5.7.pom").writeText("<project/>")

        val runtimeJars =
            CloudSimPlusJarSanitizer.sanitizeRepository(
                rawMavenRepo = rawRepo,
                sanitizedMavenRepo = sanitizedRepo,
                artifactGroup = "org.cloudsimplus",
                artifactName = "cloudsimplus",
                artifactVersion = "8.5.7",
            )

        assertTrue(runtimeJars.single().path.contains("sanitized"))
        assertJarClassPath(rawJar, "bad.jar")
        assertJarClassPath(runtimeJars.single(), null)
        assertTrue(runtimeJars.single().resolveSibling("cloudsimplus-8.5.7.pom").isFile)
        assertFalse(runtimeJars.single().resolveSibling("cloudsimplus-8.5.7-sources.jar").exists())
    }

    @Test
    fun `sanitize jar returns false and leaves jar stable when class path is absent`() {
        val jar = tempDir.resolve("cloudsimplus-8.5.7.jar")
        createJar(jar)

        assertFalse(CloudSimPlusJarSanitizer.sanitizeJar(jar))
        assertFalse(jar.resolveSibling("${jar.name}.tmp").exists())
        assertJarClassPath(jar, null)
    }

    private fun createJar(
        jar: File,
        classPath: String? = null,
    ) {
        val manifest =
            Manifest().also { manifest ->
                manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                classPath?.let { manifest.mainAttributes[Attributes.Name.CLASS_PATH] = it }
            }
        JarOutputStream(jar.outputStream(), manifest).use { output ->
            output.putNextEntry(JarEntry("example.txt"))
            output.write("content".toByteArray())
            output.closeEntry()
        }
    }

    private fun assertJarClassPath(
        jar: File,
        expected: String?,
    ) {
        JarFile(jar).use { jarFile ->
            val actual = jarFile.manifest.mainAttributes.getValue(Attributes.Name.CLASS_PATH)
            if (expected == null) {
                assertNull(actual)
            } else {
                assertTrue(actual == expected)
            }
        }
    }
}
