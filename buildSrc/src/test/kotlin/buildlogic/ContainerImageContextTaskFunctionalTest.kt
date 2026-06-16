package buildlogic

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.RandomAccessFile
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ContainerImageContextTaskFunctionalTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `valid context passes and configuration cache is reusable`() {
        val fixture = fixture("valid")
        prepareValidContext(fixture)
        fixture.writeBuild(task())

        val result = fixture.run("verifyContext")
        val firstCache = fixture.run("verifyContext", "--configuration-cache")
        val secondCache = fixture.run("verifyContext", "--configuration-cache")

        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyContext")?.outcome)
        assertContains(result.output, "Container build context verified")
        assertContains(firstCache.output, "Configuration cache entry stored")
        assertContains(secondCache.output, "Reusing configuration cache")
    }

    @Test
    fun `oversized and prohibited contexts fail with precise diagnostics`() {
        val oversized = fixture("oversized")
        prepareValidContext(oversized)
        RandomAccessFile(oversized.resolve("context/large.bin"), "rw").use { it.setLength(101) }
        refreshProvenance(oversized)
        oversized.writeBuild(task(maximumBytes = 100))

        val prohibited = fixture("prohibited")
        prepareValidContext(prohibited)
        prohibited.resolve("context/src/main.kt").apply {
            parentFile.mkdirs()
            writeText("source")
        }
        refreshProvenance(prohibited)
        prohibited.writeBuild(task())

        assertContains(oversized.runAndFail("verifyContext").output, "exceeding 100 bytes")
        assertContains(prohibited.runAndFail("verifyContext").output, "prohibited files")
    }

    @Test
    fun `missing provenance and metadata drift fail`() {
        val missing = fixture("missing-provenance")
        prepareValidContext(missing)
        missing.resolve("context/container-provenance.txt").delete()
        missing.writeBuild(task())

        val drift = fixture("metadata-drift")
        prepareValidContext(drift)
        drift.resolve("metadata.txt").writeText(metadata("b".repeat(40)))
        drift.writeBuild(task())

        assertContains(missing.runAndFail("verifyContext").output, "Missing container provenance")
        assertContains(drift.runAndFail("verifyContext").output, "provenance metadata drift")
    }

    @Test
    fun `checksum and file list drift fail`() {
        val checksum = fixture("checksum-drift")
        prepareValidContext(checksum)
        checksum.resolve("context/app.jar").appendText("drift")
        checksum.writeBuild(task())

        val files = fixture("file-drift")
        prepareValidContext(files)
        files.resolve("context/configs/default.toml").delete()
        files.writeBuild(task())

        assertContains(checksum.runAndFail("verifyContext").output, "provenance metadata drift")
        assertContains(files.runAndFail("verifyContext").output, "provenance file list drift")
    }

    private fun prepareValidContext(fixture: GradleTaskFixture) {
        fixture.resolve("metadata.txt").writeText(metadata())
        fixture.resolve("context/app.jar").apply {
            parentFile.mkdirs()
            writeText("jar")
        }
        fixture.resolve("context/Containerfile").writeText("FROM scratch")
        fixture.resolve("context/configs/default.toml").apply {
            parentFile.mkdirs()
            writeText("mode = 'batch'")
        }
        refreshProvenance(fixture)
    }

    private fun refreshProvenance(fixture: GradleTaskFixture) {
        val context = fixture.resolve("context")
        val jar = context.resolve("app.jar")
        context.resolve("container-provenance.txt").writeText(
            ContainerImageContextProvenance.render(
                CloudSimPlusMetadata("v8.5.7", "a".repeat(40), "8.5.7"),
                jar,
                contextFiles(context),
            ),
        )
    }

    private fun task(maximumBytes: Long = 50L * 1024L * 1024L): String =
        """
        import buildlogic.VerifyContainerImageContextTask

        tasks.register('verifyContext', VerifyContainerImageContextTask) {
            contextDirectory.set(layout.projectDirectory.dir('context'))
            cloudSimPlusMetadataFile.set(layout.projectDirectory.file('metadata.txt'))
            maximumBytes.set(${maximumBytes}L)
        }
        """

    private fun fixture(name: String) = GradleTaskFixture(tempDir.resolve(name))
}
