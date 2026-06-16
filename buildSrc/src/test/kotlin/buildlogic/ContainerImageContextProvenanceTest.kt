package buildlogic

import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ContainerImageContextProvenanceTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `provenance records metadata checksum and ordered files`() {
        val jar = tempDir.resolve("app.jar").also { it.writeText("jar") }
        tempDir.resolve("configs").mkdirs()
        tempDir.resolve("configs/default.toml").writeText("mode = 'batch'")

        val rendered =
            ContainerImageContextProvenance.render(
                CloudSimPlusMetadata("v8.5.7", "a".repeat(40), "8.5.7"),
                jar,
                contextFiles(tempDir),
            )

        assertContains(rendered, "cloudsimplus.ref=v8.5.7")
        assertContains(rendered, "fatJar.sha256=${sha256(jar)}")
        assertEquals(
            listOf("file=app.jar", "file=configs/default.toml"),
            rendered.lines().filter { it.startsWith("file=") },
        )
    }

    @Test
    fun `provenance verification detects checksum and file list drift`() {
        val metadata = CloudSimPlusMetadata("v8.5.7", "a".repeat(40), "8.5.7")
        val jar = tempDir.resolve("app.jar").also { it.writeText("jar") }
        tempDir.resolve("Containerfile").writeText("FROM eclipse-temurin:25-jre")
        tempDir.resolve("container-provenance.txt").writeText(
            ContainerImageContextProvenance.render(metadata, jar, contextFiles(tempDir)),
        )
        ContainerImageContextProvenance.verify(tempDir, metadata)

        jar.appendText(" drift")

        assertFailsWith<GradleException> {
            ContainerImageContextProvenance.verify(tempDir, metadata)
        }
    }
}
