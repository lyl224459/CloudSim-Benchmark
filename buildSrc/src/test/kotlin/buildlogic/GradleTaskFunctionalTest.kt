package buildlogic

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GradleTaskFunctionalTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `container smoke skips locally and reuses configuration cache`() {
        val project = fixture("container-local")
        project.resolve("Containerfile").writeText("FROM scratch")
        project.writeBuild(
            """
            import buildlogic.ContainerImageSmokeTask

            tasks.register('containerSmoke', ContainerImageSmokeTask) {
                imageName.set('fixture-image')
                dockerExecutable.set('definitely-missing-docker')
                ci.set(false)
                useBuildx.set(false)
                useGitHubActionsCache.set(false)
                contextDirectory.set(layout.projectDirectory)
                containerFile.set(layout.projectDirectory.file('Containerfile'))
            }
            """,
        )

        val first = project.run("containerSmoke", "--configuration-cache")
        val second = project.run("containerSmoke", "--configuration-cache")

        assertEquals(TaskOutcome.SUCCESS, first.task(":containerSmoke")?.outcome)
        assertContains(first.output, "skipping local containerImageSmoke")
        assertContains(second.output, "Reusing configuration cache")
    }

    @Test
    fun `container smoke fails in CI when docker is missing`() {
        val project = fixture("container-ci")
        project.resolve("Containerfile").writeText("FROM scratch")
        project.writeBuild(
            """
            import buildlogic.ContainerImageSmokeTask

            tasks.register('containerSmoke', ContainerImageSmokeTask) {
                imageName.set('fixture-image')
                dockerExecutable.set('definitely-missing-docker')
                ci.set(true)
                useBuildx.set(true)
                useGitHubActionsCache.set(true)
                contextDirectory.set(layout.projectDirectory)
                containerFile.set(layout.projectDirectory.file('Containerfile'))
            }
            """,
        )

        val result = project.runAndFail("containerSmoke")

        assertContains(result.output, "required for containerImageSmoke in CI")
    }

    @Test
    fun `sanitize task copies artifact and removes manifest classpath`() {
        val project = fixture("sanitize")
        val rawRepo = project.resolve("raw")
        val inputJar = rawRepo.resolve("org/cloudsimplus/cloudsimplus/8.5.7/cloudsimplus-8.5.7.jar")
        createJarWithClassPath(inputJar)
        inputJar.resolveSibling("cloudsimplus-8.5.7.pom").writeText("<project/>")
        project.writeBuild(
            """
            import buildlogic.SanitizeCloudSimPlusJarManifestTask

            tasks.register('sanitize', SanitizeCloudSimPlusJarManifestTask) {
                rawMavenRepo.set(layout.projectDirectory.dir('raw'))
                sanitizedMavenRepo.set(layout.projectDirectory.dir('sanitized'))
                artifactGroup.set('org.cloudsimplus')
                artifactName.set('cloudsimplus')
                artifactVersion.set('8.5.7')
            }
            """,
        )

        val result = project.run("sanitize", "--configuration-cache")
        val outputJar = project.resolve("sanitized/org/cloudsimplus/cloudsimplus/8.5.7/cloudsimplus-8.5.7.jar")

        assertEquals(TaskOutcome.SUCCESS, result.task(":sanitize")?.outcome)
        assertTrue(outputJar.isFile)
        JarFile(outputJar).use { jar ->
            assertFalse(jar.manifest.mainAttributes.containsKey(Attributes.Name.CLASS_PATH))
            assertTrue(jar.getEntry("fixture.txt") != null)
        }
    }

    @Test
    fun `verify lock task skips compatibility mode and rejects metadata drift`() {
        val project = fixture("verify-lock")
        project.resolve("lock.txt").writeText(metadata("a".repeat(40)))
        project.resolve("metadata.txt").writeText(metadata("b".repeat(40)))
        project.writeBuild(
            """
            import buildlogic.VerifyCloudSimPlusLockTask

            tasks.register('verifySkipped', VerifyCloudSimPlusLockTask) {
                lockFile.set(layout.projectDirectory.file('lock.txt'))
                metadataFile.set(layout.projectDirectory.file('metadata.txt'))
                rootDir.set(layout.projectDirectory)
                sourceDir.set(layout.projectDirectory.dir('source'))
                enforceLock.set(false)
            }
            tasks.register('verifyDrift', VerifyCloudSimPlusLockTask) {
                lockFile.set(layout.projectDirectory.file('lock.txt'))
                metadataFile.set(layout.projectDirectory.file('metadata.txt'))
                rootDir.set(layout.projectDirectory)
                sourceDir.set(layout.projectDirectory.dir('source'))
                enforceLock.set(true)
            }
            """,
        )

        val skipped = project.run("verifySkipped")
        val drift = project.runAndFail("verifyDrift")

        assertContains(skipped.output, "lock verification skipped")
        assertContains(drift.output, "metadata drift")
    }

    @Test
    fun `prepare task reports missing offline checkout`() {
        val project = fixture("prepare")
        project.resolve("lock.txt").writeText(metadata("a".repeat(40)))
        project.writeBuild(
            """
            import buildlogic.PrepareCloudSimPlusSourceTask

            tasks.register('prepareSource', PrepareCloudSimPlusSourceTask) {
                repositoryUrl.set('https://invalid.example/cloudsimplus.git')
                autoUpdate.set(false)
                offline.set(true)
                requestedRef.set('')
                lockFile.set(layout.projectDirectory.file('lock.txt'))
                enforceLock.set(true)
                networkProxy.set('')
                gitTimeoutSeconds.set(1L)
                rootDir.set(layout.projectDirectory)
                sourceDir.set(layout.projectDirectory.dir('missing-source'))
                versionFile.set(layout.buildDirectory.file('cloudsimplus-version.txt'))
            }
            """,
        )

        val result = project.runAndFail("prepareSource")

        assertContains(result.output, "CloudSim Plus source is missing")
    }

    @Test
    fun `build source task surfaces invalid Maven executable`() {
        val project = fixture("build-source")
        project.resolve("source").mkdirs()
        project.writeBuild(
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
                mavenExecutableOverride.set('definitely-missing-maven')
                sourceFiles.from(layout.projectDirectory.file('source/pom.xml'))
            }
            """,
        )
        project.resolve("source/pom.xml").writeText("<project/>")
        project.resolve("metadata.txt").writeText(metadata("a".repeat(40)))

        val result = project.runAndFail("buildSource")

        assertContains(result.output, "definitely-missing-maven")
    }

    private fun fixture(name: String): File =
        tempDir.resolve(name).also { project ->
            project.mkdirs()
            project.resolve("settings.gradle").writeText("rootProject.name = '$name'")
        }

    private fun File.writeBuild(content: String) {
        val fixturePlugin =
            """
            plugins {
                id 'cloudsim-benchmark.buildlogic-test-fixture'
            }
            """.trimIndent()
        val script = content.trimIndent().replaceFirst("\n\n", "\n\n$fixturePlugin\n\n")
        resolve("build.gradle").writeText(script)
    }

    private fun File.run(vararg arguments: String) =
        runner(*arguments).build()

    private fun File.runAndFail(vararg arguments: String) =
        runner(*arguments).buildAndFail()

    private fun File.runner(vararg arguments: String): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(this)
            .withPluginClasspath()
            .withArguments(*arguments, "--stacktrace")

    private fun metadata(commit: String) =
        """
        ref=v8.5.7
        commit=$commit
        version=8.5.7
        """.trimIndent() + System.lineSeparator()

    private fun createJarWithClassPath(file: File) {
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
}
