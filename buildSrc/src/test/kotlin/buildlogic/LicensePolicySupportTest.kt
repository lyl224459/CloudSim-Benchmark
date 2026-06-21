package buildlogic

import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LicensePolicySupportTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `policy reads string and object licenses plus aliases`() {
        val policyFile =
            file(
                "policy.json",
                """
                {
                  "version": 1,
                  "allowedLicenses": ["MIT", {"moduleLicense": "Apache-2.0"}],
                  "aliases": {"Apache License 2.0": "Apache-2.0"}
                }
                """.trimIndent(),
            )

        val policy = LicensePolicySupport.readPolicy(policyFile)

        assertEquals(setOf("Apache-2.0", "MIT"), policy.allowedLicenses)
        assertEquals("Apache-2.0", policy.aliases.getValue("Apache License 2.0"))
    }

    @Test
    fun `components normalize aliases and retain multiple licenses`() {
        val sbom =
            file(
                "sbom.json",
                """
                {
                  "components": [
                    {
                      "name": "fixture",
                      "version": "1.0",
                      "licenses": [
                        {"license": {"id": "MIT"}},
                        {"license": {"name": "Apache License 2.0"}}
                      ]
                    }
                  ]
                }
                """.trimIndent(),
            )

        val components = LicensePolicySupport.readComponents(sbom, mapOf("Apache License 2.0" to "Apache-2.0"))

        assertEquals(setOf("Apache-2.0", "MIT"), components.single().licenses)
    }

    @Test
    fun `policy and sbom missing required fields fail clearly`() {
        val emptyPolicy =
            file(
                "empty-policy.json",
                """{"version":1,"allowedLicenses":[],"aliases":{}}""",
            )
        val missingAllowed = file("missing-allowed.json", """{"version":1,"aliases":{}}""")
        val missingComponents = file("missing-components.json", """{"bomFormat":"CycloneDX"}""")

        assertContains(
            assertFailsWith<GradleException> { LicensePolicySupport.readPolicy(emptyPolicy) }.message.orEmpty(),
            "must not be empty",
        )
        assertContains(
            assertFailsWith<GradleException> { LicensePolicySupport.readPolicy(missingAllowed) }.message.orEmpty(),
            "missing allowedLicenses",
        )
        assertContains(
            assertFailsWith<GradleException> {
                LicensePolicySupport.readComponents(missingComponents, emptyMap())
            }.message.orEmpty(),
            "missing components",
        )
    }

    @Test
    fun `report renders unknown and denied licenses as violations`() {
        val policy = RuntimeLicensePolicy(allowedLicenses = setOf("MIT"), aliases = emptyMap())
        val allowed = RuntimeLicensedComponent("allowed", "1.0", setOf("MIT"))
        val denied = RuntimeLicensedComponent("denied", "1.0", setOf("AGPL-3.0"))
        val unknown = RuntimeLicensedComponent("unknown", "1.0", emptySet())

        val report = LicensePolicySupport.renderReport(policy, listOf(allowed, denied, unknown), listOf(denied, unknown))

        assertContains(report, "Runtime license policy: FAIL")
        assertContains(report, "denied:1.0 = AGPL-3.0")
        assertContains(report, "unknown:1.0 = UNKNOWN")
    }

    private fun file(
        name: String,
        content: String,
    ): File = tempDir.resolve(name).also { it.writeText(content) }
}
