package buildlogic

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals

class LicensePolicyTaskFunctionalTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `allowed and aliased licenses pass`() {
        val fixture = fixture("allowed")
        fixture.resolve("policy.json").writeText(policy())
        fixture.resolve("sbom.json").writeText(sbom("GPLv3", "Apache-2.0"))
        fixture.writeBuild(task())

        val result = fixture.run("checkLicense")

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkLicense")?.outcome)
        assertContains(fixture.resolve("build/license-policy.txt").readText(), "Runtime license policy: PASS")
        assertContains(fixture.resolve("build/license-policy.txt").readText(), "GPL-3.0")
    }

    @Test
    fun `unknown and unapproved licenses fail`() {
        val unknown = fixture("unknown")
        unknown.resolve("policy.json").writeText(policy())
        unknown.resolve("sbom.json").writeText(sbom())
        unknown.writeBuild(task())

        val rejected = fixture("rejected")
        rejected.resolve("policy.json").writeText(policy())
        rejected.resolve("sbom.json").writeText(sbom("AGPL-3.0"))
        rejected.writeBuild(task())

        assertContains(unknown.runAndFail("checkLicense").output, "UNKNOWN")
        assertContains(rejected.runAndFail("checkLicense").output, "AGPL-3.0")
    }

    @Test
    fun `invalid policy fails and configuration cache is reusable`() {
        val invalid = fixture("invalid")
        invalid.resolve("policy.json").writeText("""{"version":2,"allowedLicenses":["MIT"],"aliases":{}}""")
        invalid.resolve("sbom.json").writeText(sbom("MIT"))
        invalid.writeBuild(task())

        val valid = fixture("cache")
        valid.resolve("policy.json").writeText(policy())
        valid.resolve("sbom.json").writeText(sbom("MIT"))
        valid.writeBuild(task())

        assertContains(invalid.runAndFail("checkLicense").output, "Unsupported license policy version")
        valid.run("checkLicense", "--configuration-cache")
        assertContains(valid.run("checkLicense", "--configuration-cache").output, "Reusing configuration cache")
    }

    private fun policy(): String =
        """
        {
          "version": 1,
          "allowedLicenses": [
            {"moduleLicense":"Apache-2.0"},
            {"moduleLicense":"GPL-3.0"},
            {"moduleLicense":"MIT"}
          ],
          "aliases": {"GPLv3": "GPL-3.0"}
        }
        """.trimIndent()

    private fun sbom(vararg licenses: String): String {
        val rendered =
            licenses.joinToString(",") { license ->
                """{"license":{"id":"$license"}}"""
            }
        return """{"components":[{"name":"fixture","version":"1.0","licenses":[$rendered]}]}"""
    }

    private fun task(): String =
        """
        import buildlogic.VerifyLicensePolicyTask

        tasks.register('checkLicense', VerifyLicensePolicyTask) {
            sbomFile.set(layout.projectDirectory.file('sbom.json'))
            policyFile.set(layout.projectDirectory.file('policy.json'))
            reportFile.set(layout.buildDirectory.file('license-policy.txt'))
        }
        """

    private fun fixture(name: String) = GradleTaskFixture(tempDir.resolve(name))
}
