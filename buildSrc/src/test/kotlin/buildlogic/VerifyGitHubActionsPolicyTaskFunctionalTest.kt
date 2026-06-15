package buildlogic

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals

class VerifyGitHubActionsPolicyTaskFunctionalTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `approved Node 24 actions pass and produce report`() {
        val fixture = fixture("approved")
        fixture.resolve(".github/workflows/ci.yml").apply {
            parentFile.mkdirs()
            writeText("steps:\n- uses: actions/checkout@v6\n- uses: actions/setup-java@v5\n")
        }
        fixture.writeBuild(policyTask())

        val result = fixture.run("verifyActions")

        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyActions")?.outcome)
        assertContains(fixture.resolve("build/actions-policy.txt").readText(), "PASS")
    }

    @Test
    fun `legacy Node 20 action major fails policy`() {
        val fixture = fixture("legacy")
        fixture.resolve(".github/workflows/ci.yml").apply {
            parentFile.mkdirs()
            writeText("steps:\n- uses: actions/checkout@v4\n")
        }
        fixture.writeBuild(policyTask())

        val result = fixture.runAndFail("verifyActions")

        assertContains(result.output, "actions/checkout@v4")
        assertContains(fixture.resolve("build/actions-policy.txt").readText(), "FAIL")
    }

    private fun policyTask(): String =
        """
        import buildlogic.VerifyGitHubActionsPolicyTask

        tasks.register('verifyActions', VerifyGitHubActionsPolicyTask) {
            workflowFiles.from(fileTree('.github/workflows') { include('*.yml', '*.yaml') })
            reportFile.set(layout.buildDirectory.file('actions-policy.txt'))
        }
        """

    private fun fixture(name: String) = GradleTaskFixture(tempDir.resolve(name))
}
