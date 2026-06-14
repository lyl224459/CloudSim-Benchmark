package buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

internal object JUnitTestInventorySupport {
    const val CURRENT_FORMAT = 1

    fun render(entries: Collection<String>): String {
        val sortedEntries = entries.toSortedSet()
        return buildString {
            appendLine("format=$CURRENT_FORMAT")
            appendLine("count=${sortedEntries.size}")
            sortedEntries.forEach { entry -> appendLine("entry=$entry") }
        }
    }

    fun parse(content: String): List<String> {
        val lines = content.lineSequence().filter(String::isNotBlank).toList()
        val format = lines.firstOrNull()?.takeValue("format")?.toIntOrNull()
        if (format != CURRENT_FORMAT) {
            throw GradleException("Unsupported JUnit test inventory format: ${format ?: "missing"}")
        }
        val expectedCount = lines.getOrNull(1)?.takeValue("count")?.toIntOrNull()
            ?: throw GradleException("JUnit test inventory count is missing or invalid")
        val entries = lines.drop(2).map { line -> line.takeValue("entry") }
        if (entries.size != expectedCount) {
            throw GradleException("JUnit test inventory count mismatch: expected $expectedCount, found ${entries.size}")
        }
        if (entries != entries.sorted() || entries.distinct().size != entries.size) {
            throw GradleException("JUnit test inventory entries must be sorted and unique")
        }
        return entries
    }

    fun difference(
        expected: Collection<String>,
        actual: Collection<String>,
    ): String? {
        val removed = expected.toSortedSet() - actual.toSet()
        val added = actual.toSortedSet() - expected.toSet()
        if (removed.isEmpty() && added.isEmpty()) {
            return null
        }
        return buildString {
            appendLine("JUnit test inventory changed.")
            removed.forEach { entry -> appendLine("- $entry") }
            added.forEach { entry -> appendLine("+ $entry") }
            append("Run updateJUnitTestInventory after reviewing the test entrypoint changes.")
        }
    }

    private fun String.takeValue(key: String): String {
        val prefix = "$key="
        if (!startsWith(prefix)) {
            throw GradleException("Invalid JUnit test inventory line; expected '$prefix': $this")
        }
        return removePrefix(prefix)
    }
}

abstract class JUnitTestInventoryTaskBase : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testClassesDirs: ConfigurableFileCollection

    @get:Classpath
    abstract val testRuntimeClasspath: ConfigurableFileCollection

    protected fun currentEntries(): List<String> =
        JUnitCompiledTestSupport.inspect(testClassesDirs.files, testRuntimeClasspath.files) { classes ->
            JUnitTestSignatureSupport.entryPoints(classes)
        }
}

@DisableCachingByDefault(because = "Explicitly updates the committed JUnit test inventory")
abstract class UpdateJUnitTestInventoryTask : JUnitTestInventoryTaskBase() {
    @get:OutputFile
    abstract val inventoryFile: RegularFileProperty

    @TaskAction
    fun update() {
        val output = inventoryFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(JUnitTestInventorySupport.render(currentEntries()))
        logger.lifecycle("Updated JUnit test inventory: ${output.path}")
    }
}

@DisableCachingByDefault(because = "Verification task has no outputs")
abstract class VerifyJUnitTestInventoryTask : JUnitTestInventoryTaskBase() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inventoryFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val expected = JUnitTestInventorySupport.parse(inventoryFile.get().asFile.readText())
        val actual = currentEntries()
        JUnitTestInventorySupport.difference(expected, actual)?.let { difference ->
            throw GradleException(difference)
        }
        logger.lifecycle("Verified JUnit test inventory: ${actual.size} entrypoints")
    }
}
