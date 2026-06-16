package buildlogic

import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CloudSimPlusGitSupportTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `git state files resolve directory gitdir file refs and missing markers`() {
        val root = tempDir.resolve("root").also(File::mkdirs)
        val rootGit = root.resolve(".git").also(File::mkdirs)
        rootGit.resolve("HEAD").writeText("ref: refs/heads/main")
        rootGit.resolve("index").writeText("index")
        rootGit.resolve("refs/heads/main").also { it.parentFile.mkdirs() }.writeText("a".repeat(40))

        val source = root.resolve("source").also(File::mkdirs)
        val sourceGit = root.resolve(".git/modules/source").also(File::mkdirs)
        source.resolve(".git").writeText("gitdir: ../.git/modules/source")
        sourceGit.resolve("HEAD").writeText("b".repeat(40))
        sourceGit.resolve("packed-refs").writeText("packed")

        val files = CloudSimPlusGitStateFiles.resolve(root, source).map { it.canonicalFile }

        assertTrue(rootGit.resolve("HEAD").canonicalFile in files)
        assertTrue(rootGit.resolve("refs/heads/main").canonicalFile in files)
        assertTrue(sourceGit.resolve("HEAD").canonicalFile in files)
        assertTrue(sourceGit.resolve("packed-refs").canonicalFile in files)
        assertTrue(CloudSimPlusGitStateFiles.resolve(tempDir.resolve("missing"), tempDir.resolve("other")).isEmpty())
    }

    @Test
    fun `git client falls back when configured proxy command fails`() {
        val log = tempDir.resolve("fallback.log")
        val git = proxyFallbackGit(log)
        val client =
            CloudSimPlusGitClient(
                rootDir = tempDir,
                networkProxy = "http://10.53.115.91:7890",
                timeoutSeconds = 5,
                temporaryDir = tempDir.resolve("tmp"),
                logInfo = {},
                gitExecutable = git.absolutePath,
            )

        val output = client.exec(listOf("status"), ignoreExit = false)

        assertEquals("fallback-ok", output)
        assertContains(log.readText(), "http.proxy=http://10.53.115.91:7890")
        assertContains(log.readText(), "status")
    }

    @Test
    fun `git client reports timeout and tries proxy fallbacks`() {
        val log = tempDir.resolve("commands.log")
        val git = fakeGit(log)
        val messages = mutableListOf<String>()
        val client =
            CloudSimPlusGitClient(
                rootDir = tempDir,
                networkProxy = "http://10.53.115.91:7890",
                timeoutSeconds = 1,
                temporaryDir = tempDir.resolve("tmp"),
                logInfo = messages::add,
                gitExecutable = git.absolutePath,
            )

        val error =
            assertFailsWith<GradleException> {
                client.exec(listOf("status"), ignoreExit = false)
            }

        assertContains(error.message.orEmpty(), "Timed out after 1s")
        assertTrue(messages.isNotEmpty())
        assertContains(log.readText(), "http.proxy=http://10.53.115.91:7890")
        assertContains(log.readText(), "http.proxy=")
    }

    private fun fakeGit(log: File): File {
        val windows = System.getProperty("os.name").lowercase().contains("windows")
        val executable = tempDir.resolve(if (windows) "fake-git.cmd" else "fake-git")
        executable.writeText(
            if (windows) {
                """
                @echo off
                echo %*>>"${log.absolutePath}"
                ping -n 4 127.0.0.1 >nul
                exit /b 9
                """.trimIndent()
            } else {
                """
                #!/bin/sh
                echo "${'$'}*" >> "${log.absolutePath}"
                sleep 2
                exit 9
                """.trimIndent()
            },
        )
        executable.setExecutable(true)
        return executable
    }

    private fun proxyFallbackGit(log: File): File {
        val windows = System.getProperty("os.name").lowercase().contains("windows")
        val executable = tempDir.resolve(if (windows) "proxy-fallback-git.cmd" else "proxy-fallback-git")
        executable.writeText(
            if (windows) {
                """
                @echo off
                echo %*>>"${log.absolutePath}"
                echo %* | findstr /C:"http.proxy=http://10.53.115.91:7890" >nul
                if %ERRORLEVEL%==0 exit /b 7
                echo fallback-ok
                exit /b 0
                """.trimIndent()
            } else {
                """
                #!/bin/sh
                echo "${'$'}*" >> "${log.absolutePath}"
                case "${'$'}*" in
                  *http.proxy=http://10.53.115.91:7890*) exit 7 ;;
                esac
                echo fallback-ok
                exit 0
                """.trimIndent()
            },
        )
        executable.setExecutable(true)
        return executable
    }
}
