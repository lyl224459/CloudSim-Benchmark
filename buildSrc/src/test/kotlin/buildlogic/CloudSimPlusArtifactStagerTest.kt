package buildlogic

import org.gradle.api.GradleException
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CloudSimPlusArtifactStagerTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `stages only locked runtime jar and pom`() {
        val source = tempDir.resolve("source")
        source.resolve("target").mkdirs()
        source.resolve("target/cloudsimplus-8.5.7.jar").writeText("jar")
        source.resolve("target/cloudsimplus-8.5.7-sources.jar").writeText("sources")
        source.resolve("pom.xml").writeText(pom("8.5.7"))
        val rawRepo = tempDir.resolve("raw")

        val staged =
            CloudSimPlusArtifactStager.stage(
                sourceDir = source,
                rawMavenRepo = rawRepo,
                artifactGroup = "org.cloudsimplus",
                artifactName = "cloudsimplus",
                artifactVersion = "8.5.7",
            )

        assertEquals(listOf("cloudsimplus-8.5.7.jar", "cloudsimplus-8.5.7.pom"), staged.map(File::getName))
        assertEquals(2, rawRepo.walkTopDown().count(File::isFile))
    }

    @Test
    fun `rejects missing or mismatched runtime jar`() {
        val source = tempDir.resolve("source").also(File::mkdirs)
        source.resolve("pom.xml").writeText(pom("8.5.7"))

        val failure =
            assertFailsWith<GradleException> {
                CloudSimPlusArtifactStager.stage(
                    sourceDir = source,
                    rawMavenRepo = tempDir.resolve("raw"),
                    artifactGroup = "org.cloudsimplus",
                    artifactName = "cloudsimplus",
                    artifactVersion = "8.5.7",
                )
            }

        assertTrue(failure.message.orEmpty().contains("exactly one"))
    }

    @Test
    fun `rejects multiple runtime jars and pom version drift`() {
        val source = tempDir.resolve("source")
        source.resolve("target").mkdirs()
        source.resolve("target/cloudsimplus-8.5.7.jar").writeText("jar")
        source.resolve("target/cloudsimplus-8.5.6.jar").writeText("stale jar")
        source.resolve("pom.xml").writeText(pom("8.5.6"))

        val multipleJarFailure = assertFailsWith<GradleException> { stage(source) }
        assertTrue(multipleJarFailure.message.orEmpty().contains("cloudsimplus-8.5.6.jar"))

        source.resolve("target/cloudsimplus-8.5.6.jar").delete()
        val pomFailure = assertFailsWith<GradleException> { stage(source) }
        assertTrue(pomFailure.message.orEmpty().contains("POM version mismatch"))
    }

    @Test
    fun `rejects missing pom after matching runtime jar is found`() {
        val source = tempDir.resolve("missing-pom")
        source.resolve("target").mkdirs()
        source.resolve("target/cloudsimplus-8.5.7.jar").writeText("jar")

        val failure = assertFailsWith<GradleException> { stage(source) }

        assertTrue(failure.message.orEmpty().contains("POM not found"))
    }

    private fun stage(source: File): List<File> =
        CloudSimPlusArtifactStager.stage(
            sourceDir = source,
            rawMavenRepo = tempDir.resolve("raw"),
            artifactGroup = "org.cloudsimplus",
            artifactName = "cloudsimplus",
            artifactVersion = "8.5.7",
        )

    private fun pom(version: String): String =
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>org.cloudsimplus</groupId>
          <artifactId>cloudsimplus</artifactId>
          <version>$version</version>
        </project>
        """.trimIndent()
}
