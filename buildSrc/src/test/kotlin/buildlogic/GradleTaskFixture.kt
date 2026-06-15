package buildlogic

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.test.fail

internal class GradleTaskFixture(
    val project: File,
) {
    init {
        project.mkdirs()
        project.resolve("settings.gradle").writeText("rootProject.name = '${project.name}'")
    }

    fun resolve(path: String): File = project.resolve(path)

    fun fakeExecutable(
        name: String,
        windowsBody: String,
        unixBody: String,
    ): File {
        val windows = System.getProperty("os.name").lowercase().contains("windows")
        val executable = resolve(if (windows) "$name.cmd" else name)
        executable.writeText(
            if (windows) {
                "@echo off\r\nchcp 65001 >nul\r\n${windowsBody.trimIndent()}\r\n"
            } else {
                "#!/bin/sh\n${unixBody.trimIndent()}\n"
            },
        )
        executable.setExecutable(true)
        return executable
    }

    fun git(
        directory: File,
        vararg arguments: String,
    ): String {
        val process =
            ProcessBuilder(listOf("git") + arguments)
                .directory(directory)
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() != 0) {
            fail("Git command failed: git ${arguments.joinToString(" ")}\n$output")
        }
        return output
    }

    fun createGitCheckout(relativePath: String = "source"): Pair<File, String> {
        val source = resolve(relativePath).also(File::mkdirs)
        git(source, "init")
        git(source, "config", "user.email", "test@example.com")
        git(source, "config", "user.name", "Test")
        source.resolve("pom.xml").writeText(pom())
        git(source, "add", "pom.xml")
        git(source, "commit", "-m", "fixture")
        return source to git(source, "rev-parse", "HEAD")
    }

    fun writeBuild(content: String) {
        val fixturePlugin =
            """
            plugins {
                id 'cloudsim-benchmark.buildlogic-test-fixture'
            }
            """.trimIndent()
        val script = content.trimIndent().replaceFirst("\n\n", "\n\n$fixturePlugin\n\n")
        project.resolve("build.gradle").writeText(script)
    }

    fun run(vararg arguments: String) =
        runner(*arguments).build()

    fun runAndFail(vararg arguments: String) =
        runner(*arguments).buildAndFail()

    private fun runner(vararg arguments: String): GradleRunner {
        val runner =
            GradleRunner
            .create()
            .withProjectDir(project)
            .withPluginClasspath()
        val commonArguments = arguments.toMutableList().apply { add("--stacktrace") }
        if ("--configuration-cache" !in arguments) {
            commonArguments += "-Dorg.gradle.jvmargs=${jacocoAgentArgument()}"
        }
        return runner.withArguments(commonArguments)
    }

    private fun jacocoAgentArgument(): String {
        val agent = System.getProperty("buildlogic.testkit.jacocoAgent")
        val outputDirectory = File(System.getProperty("buildlogic.testkit.jacocoDirectory")).also(File::mkdirs)
        val destination = outputDirectory.resolve("${project.name}.exec")
        return "-javaagent:${agent.normalizedPath()}=destfile=${destination.absolutePath.normalizedPath()}," +
            "append=true,includes=buildlogic.*"
    }

    private fun String.normalizedPath(): String = replace('\\', '/')
}

internal fun metadata(commit: String = "a".repeat(40)): String =
    """
    ref=v8.5.7
    commit=$commit
    version=8.5.7
    """.trimIndent() + System.lineSeparator()

internal fun pom(version: String = "8.5.7"): String =
    """
    <project xmlns="http://maven.apache.org/POM/4.0.0">
      <modelVersion>4.0.0</modelVersion>
      <groupId>org.cloudsimplus</groupId>
      <artifactId>cloudsimplus</artifactId>
      <version>$version</version>
    </project>
    """.trimIndent()

internal fun createJarWithClassPath(file: File) {
    file.parentFile.mkdirs()
    val manifest =
        Manifest().also {
            it.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            it.mainAttributes[Attributes.Name.CLASS_PATH] = "dependency.jar"
        }
    JarOutputStream(file.outputStream(), manifest).use { output ->
        output.putNextEntry(JarEntry("fixture.txt"))
        output.write("fixture".toByteArray())
        output.closeEntry()
    }
}

internal fun String.gradlePath(): String = replace("\\", "\\\\")
