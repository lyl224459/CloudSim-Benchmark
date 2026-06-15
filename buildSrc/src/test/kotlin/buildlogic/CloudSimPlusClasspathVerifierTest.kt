package buildlogic

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.test.assertFailsWith

class CloudSimPlusClasspathVerifierTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `accepts source-built jar without manifest classpath`() {
        val repo = tempDir.resolve("repo").apply(File::mkdirs)
        val jar = createJar(repo.resolve("cloudsimplus-8.5.7.jar"))
        val nonCanonicalRepoPath = repo.resolve("child").resolve("..").toPath()

        CloudSimPlusClasspathVerifier.verifyClasspath("runtime", setOf(jar), nonCanonicalRepoPath, "cloudsimplus")
    }

    @Test
    fun `rejects missing foreign and manifest classpath artifacts`() {
        val repo = tempDir.resolve("repo").apply(File::mkdirs)
        assertFailsWith<IllegalStateException> {
            CloudSimPlusClasspathVerifier.verifyClasspath("runtime", emptySet(), repo.toPath(), "cloudsimplus")
        }

        val foreign = createJar(tempDir.resolve("cloudsimplus-8.5.7.jar"))
        assertFailsWith<IllegalStateException> {
            CloudSimPlusClasspathVerifier.verifyClasspath("runtime", setOf(foreign), repo.toPath(), "cloudsimplus")
        }

        val unsafe = createJar(repo.resolve("cloudsimplus-8.5.7.jar"), "dependency.jar")
        assertFailsWith<IllegalStateException> {
            CloudSimPlusClasspathVerifier.verifyClasspath("runtime", setOf(unsafe), repo.toPath(), "cloudsimplus")
        }
    }

    private fun createJar(
        file: File,
        classPath: String? = null,
    ): File {
        file.parentFile.mkdirs()
        val manifest =
            Manifest().apply {
                mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                classPath?.let { mainAttributes[Attributes.Name.CLASS_PATH] = it }
            }
        JarOutputStream(file.outputStream(), manifest).use { }
        return file
    }
}
