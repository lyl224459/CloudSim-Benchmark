package buildlogic

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals

class BuildWarningAuditTaskFunctionalTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `clean logs write a passing report and reuse configuration cache`() {
        val fixture = fixture("warning-audit-success")
        writeCleanLogs(fixture)
        fixture.writeBuild(auditTask())

        val result = fixture.run("auditWarnings")

        assertEquals(TaskOutcome.SUCCESS, result.task(":auditWarnings")?.outcome)
        assertContains(fixture.resolve("build/audit.md").readText(), "Status: PASS")
        fixture.run("auditWarnings", "--configuration-cache")
        assertContains(fixture.run("auditWarnings", "--configuration-cache").output, "Reusing configuration cache")
    }

    @Test
    fun `unknown warnings and missing logs fail with a report`() {
        val unknown = fixture("warning-audit-unknown")
        writeCleanLogs(unknown)
        unknown.resolve("logs/compileKotlin.log").writeText("WARNING: unknown compiler warning")
        unknown.writeBuild(auditTask())

        assertContains(unknown.runAndFail("auditWarnings").output, "found 1 violation")
        assertContains(unknown.resolve("build/audit.md").readText(), "Status: FAIL")

        val missing = fixture("warning-audit-missing")
        writeCleanLogs(missing)
        missing.resolve("logs/detekt.log").delete()
        missing.writeBuild(auditTask())
        assertContains(missing.runAndFail("auditWarnings").output, "found 1 violation")
    }

    private fun writeCleanLogs(fixture: GradleTaskFixture) {
        BuildWarningSource.entries.forEach { source ->
            fixture.resolve("logs/${source.logFileName}")
                .also { it.parentFile.mkdirs() }
                .writeText("BUILD SUCCESSFUL")
        }
    }

    private fun auditTask(): String =
        """
        import buildlogic.BuildWarningAuditTask

        tasks.register('auditWarnings', BuildWarningAuditTask) {
            logDirectory.set(layout.projectDirectory.dir('logs'))
            reportFile.set(layout.buildDirectory.file('audit.md'))
        }
        """

    private fun fixture(name: String) = GradleTaskFixture(tempDir.resolve(name))
}
