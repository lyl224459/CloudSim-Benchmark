package buildlogic

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CloudSimPlusSourcePreparerTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `locked preparation checks out commit and writes stable metadata`() {
        val source = sourceCheckout("8.5.7")
        val operations = FakeGitOperations(COMMIT, exactTag = "v8.5.7")
        val metadataFile = tempDir.resolve("metadata.txt")
        val expected = CloudSimPlusMetadata("v8.5.7", COMMIT, "8.5.7")

        preparer(operations).prepare(source, metadataFile, false, false, "", expected)
        val timestamp = metadataFile.lastModified()
        preparer(operations).prepare(source, metadataFile, false, false, "", expected)

        assertEquals(expected, CloudSimPlusLockSupport.read(metadataFile))
        assertEquals(timestamp, metadataFile.lastModified())
        assertTrue(operations.commands.any { it.containsAll(listOf("checkout", COMMIT)) })
    }

    @Test
    fun `explicit ref takes precedence and fetches requested ref`() {
        val source = sourceCheckout("8.5.8")
        val operations = FakeGitOperations(COMMIT, exactTag = "v8.5.7")

        preparer(operations).prepare(source, tempDir.resolve("metadata.txt"), false, true, "v8.5.8", null)

        assertTrue(operations.commands.any { it.containsAll(listOf("fetch", "v8.5.8")) })
        assertTrue(operations.commands.any { it.containsAll(listOf("checkout", "v8.5.8")) })
    }

    @Test
    fun `offline mode rejects missing checkout and lock drift`() {
        val operations = FakeGitOperations(COMMIT, exactTag = "v8.5.7")
        assertFailsWith<org.gradle.api.GradleException> {
            preparer(operations).prepare(tempDir.resolve("missing"), tempDir.resolve("metadata.txt"), true, false, "", null)
        }

        val source = sourceCheckout("8.5.8")
        assertFailsWith<org.gradle.api.GradleException> {
            preparer(operations).prepare(
                source,
                tempDir.resolve("metadata.txt"),
                true,
                false,
                "",
                CloudSimPlusMetadata("v8.5.7", COMMIT, "8.5.7"),
            )
        }
    }

    private fun preparer(operations: FakeGitOperations) =
        CloudSimPlusSourcePreparer("https://example.invalid/cloudsimplus.git", tempDir, operations) { }

    private fun sourceCheckout(version: String): File =
        tempDir.resolve("source-$version").apply {
            mkdirs()
            resolve(".git").mkdir()
            resolve("pom.xml").writeText("<project><artifactId>cloudsimplus</artifactId><version>$version</version></project>")
        }

    private class FakeGitOperations(
        private val commit: String,
        private val exactTag: String,
    ) : CloudSimPlusGitOperations {
        val commands = mutableListOf<List<String>>()

        override fun exec(
            args: List<String>,
            workDir: File,
            ignoreExit: Boolean,
        ): String {
            commands += args
            return when {
                args.contains("--is-inside-work-tree") -> "true"
                args.contains("rev-parse") && args.contains("HEAD") -> commit
                args.contains("describe") -> exactTag
                args.contains("ls-remote") -> "$commit\trefs/tags/v8.5.8"
                args.contains("--list") -> "v8.5.7\nv8.5.8"
                else -> ""
            }
        }
    }

    private companion object {
        const val COMMIT = "f23d4b165402e4976de854ceed5e52bc7b78c520"
    }
}
