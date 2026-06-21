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
        fixture.writeSecretsWorkflow()
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
        fixture.writeSecretsWorkflow()
        fixture.writeBuild(policyTask())

        val result = fixture.runAndFail("verifyActions")

        assertContains(result.output, "actions/checkout@v4")
        assertContains(fixture.resolve("build/actions-policy.txt").readText(), "FAIL")
    }

    @Test
    fun `release workflow requires approved attest action`() {
        val fixture = fixture("release-attest")
        fixture.writeSecretsWorkflow()
        fixture.resolve(".github/workflows/release.yml").apply {
            parentFile.mkdirs()
            writeText("steps:\n- uses: actions/checkout@v6\n")
        }
        fixture.writeBuild(policyTask())

        assertContains(fixture.runAndFail("verifyActions").output, "actions/attest@missing")

        fixture.resolve(".github/workflows/release.yml")
            .writeText("steps:\n- uses: actions/checkout@v6\n- uses: actions/attest@v4\n")
        assertEquals(TaskOutcome.SUCCESS, fixture.run("verifyActions").task(":verifyActions")?.outcome)
    }

    @Test
    fun `docker image actions are forbidden`() {
        val fixture = fixture("forbidden-docker")
        fixture.resolve(".github/workflows/release.yml").apply {
            parentFile.mkdirs()
            writeText(
                """
                steps:
                - uses: actions/checkout@v6
                - uses: actions/attest@v4
                - uses: docker/build-push-action@v7
                """.trimIndent(),
            )
        }
        fixture.writeSecretsWorkflow()
        fixture.writeBuild(policyTask())

        assertContains(fixture.runAndFail("verifyActions").output, "docker/build-push-action@v7")
    }

    @Test
    fun `secrets workflow must use approved gitleaks and full history checkout`() {
        val fixture = fixture("secrets-policy")
        fixture.resolve(".github/workflows/secrets.yml").apply {
            parentFile.mkdirs()
            writeText("steps:\n- uses: actions/checkout@v6\n- uses: gitleaks/gitleaks-action@v2\n")
        }
        fixture.writeBuild(policyTask())

        val result = fixture.runAndFail("verifyActions")

        assertContains(result.output, "gitleaks/gitleaks-action@v2")
        assertContains(result.output, "actions/checkout(fetch-depth=0)@missing")
    }

    @Test
    fun `missing secrets workflow fails policy`() {
        val fixture = fixture("missing-secrets")
        fixture.resolve(".github/workflows/ci.yml").apply {
            parentFile.mkdirs()
            writeText("steps:\n- uses: actions/checkout@v6\n")
        }
        fixture.writeBuild(policyTask())

        assertContains(fixture.runAndFail("verifyActions").output, "secrets.yml:gitleaks/gitleaks-action@missing")
    }

    private fun policyTask(): String =
        """
        import buildlogic.VerifyGitHubActionsPolicyTask

        tasks.register('verifyActions', VerifyGitHubActionsPolicyTask) {
            workflowFiles.from(fileTree('.github/workflows') { include('*.yml', '*.yaml') })
            reportFile.set(layout.buildDirectory.file('actions-policy.txt'))
        }
        """

    private fun GradleTaskFixture.writeSecretsWorkflow() {
        resolve(".github/workflows/secrets.yml").apply {
            parentFile.mkdirs()
            writeText(
                """
                steps:
                - uses: actions/checkout@v6
                  with:
                    fetch-depth: 0
                - uses: gitleaks/gitleaks-action@v3
                """.trimIndent(),
            )
        }
    }

    private fun fixture(name: String) = GradleTaskFixture(tempDir.resolve(name))
}
