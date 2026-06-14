package buildlogic

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class VerifyNoDetektBaselineTaskTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `passes when no baseline file or configuration exists`() {
        val task = taskWith(buildScript = "plugins { id(\"io.gitlab.arturbosch.detekt\") }")

        task.verify()
    }

    @Test
    fun `fails when baseline file exists`() {
        val task = taskWith(buildScript = "")
        tempDir.resolve("detekt-baseline.xml").writeText("<SmellBaseline/>")

        val error = assertFailsWith<Exception> { task.verify() }

        assertContains(error.message.orEmpty(), "baseline files are forbidden")
    }

    @Test
    fun `fails when detekt baseline is configured`() {
        val task = taskWith(buildScript = "detekt {\n    baseline = file(\"detekt-baseline.xml\")\n}")

        val error = assertFailsWith<Exception> { task.verify() }

        assertContains(error.message.orEmpty(), "baseline configuration is forbidden")
    }

    private fun taskWith(buildScript: String): VerifyNoDetektBaselineTask {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val buildFile = tempDir.resolve("build.gradle.kts").apply { writeText(buildScript) }
        return project.tasks.register("verifyNoDetektBaseline", VerifyNoDetektBaselineTask::class.java).get().apply {
            baselineFiles.from(tempDir.resolve("detekt-baseline.xml"))
            buildScripts.from(buildFile)
        }
    }
}
