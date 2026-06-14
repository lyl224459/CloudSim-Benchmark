package buildlogic

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CloudSimPlusMavenSupportTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `package arguments use dependency cache and disable upstream artifact attachment profile`() {
        val arguments = CloudSimPlusMavenSupport.packageArguments(tempDir)

        assertTrue("-Dmaven.repo.local=${tempDir.absolutePath}" in arguments)
        assertTrue("-P!default" in arguments)
        assertTrue("-Dmaven.javadoc.skip=true" in arguments)
        assertEquals("package", arguments.last())
    }

    @Test
    fun `maven executable falls back when wrapper script is missing`() {
        assertEquals("mvn.cmd", CloudSimPlusMavenSupport.mavenExecutable(tempDir, osName = "Windows 11"))
        assertEquals("mvn", CloudSimPlusMavenSupport.mavenExecutable(tempDir, osName = "Linux"))
    }

    @Test
    fun `maven executable falls back when wrapper jar has no main class`() {
        val wrapper = tempDir.resolve("mvnw.cmd").also { it.writeText("@echo off") }
        val wrapperJar = tempDir.resolve(".mvn/wrapper/maven-wrapper.jar")
        wrapperJar.parentFile.mkdirs()
        JarOutputStream(wrapperJar.outputStream()).use { output ->
            output.putNextEntry(JarEntry("not-the-wrapper.txt"))
            output.closeEntry()
        }

        assertEquals("mvn.cmd", CloudSimPlusMavenSupport.mavenExecutable(tempDir, osName = "Windows 11"))
        assertTrue(wrapper.isFile)
    }

    @Test
    fun `maven executable uses wrapper when script and main class exist`() {
        val wrapper = tempDir.resolve("mvnw.cmd").also { it.writeText("@echo off") }
        val wrapperJar = tempDir.resolve(".mvn/wrapper/maven-wrapper.jar")
        wrapperJar.parentFile.mkdirs()
        JarOutputStream(wrapperJar.outputStream()).use { output ->
            output.putNextEntry(JarEntry("org/apache/maven/wrapper/MavenWrapperMain.class"))
            output.write(byteArrayOf(0))
            output.closeEntry()
        }

        assertEquals(wrapper.absolutePath, CloudSimPlusMavenSupport.mavenExecutable(tempDir, osName = "Windows 11"))
    }

    @Test
    fun `maven options converts http proxy to maven properties`() {
        val options =
            CloudSimPlusMavenSupport.mavenOptions(
                proxy = "http://10.53.115.91:7890",
                existingOptions = "-Xmx1g",
            )

        assertTrue("-Xmx1g" in options)
        assertTrue("--enable-native-access=ALL-UNNAMED" in options)
        assertTrue("--sun-misc-unsafe-memory-access=allow" in options)
        assertTrue("-Dhttp.proxyHost=10.53.115.91" in options)
        assertTrue("-Dhttp.proxyPort=7890" in options)
        assertTrue("-Dhttps.proxyHost=10.53.115.91" in options)
        assertTrue("-Dhttps.proxyPort=7890" in options)
    }

    @Test
    fun `maven options does not duplicate jdk compatibility flags`() {
        val options =
            CloudSimPlusMavenSupport.mavenOptions(
                proxy = "",
                existingOptions = "--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow",
            )

        assertEquals(1, Regex("--enable-native-access=ALL-UNNAMED").findAll(options).count())
        assertEquals(1, Regex("--sun-misc-unsafe-memory-access=allow").findAll(options).count())
    }
}
