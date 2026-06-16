package buildlogic

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

@CacheableTask
abstract class VerifyLicensePolicyTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sbomFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val policyFile: RegularFileProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val policy = LicensePolicySupport.readPolicy(policyFile.get().asFile)
        val components = LicensePolicySupport.readComponents(sbomFile.get().asFile, policy.aliases)
        val violations =
            components.filter { component ->
                component.licenses.isEmpty() || component.licenses.any { it !in policy.allowedLicenses }
            }
        val report = reportFile.get().asFile
        report.parentFile.mkdirs()
        report.writeText(LicensePolicySupport.renderReport(policy, components, violations))
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Runtime license policy violations: " +
                    violations.joinToString { "${it.name}:${it.version}=${it.licenses.ifEmpty { setOf("UNKNOWN") }}" },
            )
        }
    }
}

internal data class RuntimeLicensePolicy(
    val allowedLicenses: Set<String>,
    val aliases: Map<String, String>,
)

internal data class RuntimeLicensedComponent(
    val name: String,
    val version: String,
    val licenses: Set<String>,
)

internal object LicensePolicySupport {
    fun readPolicy(file: File): RuntimeLicensePolicy {
        val root = file.parseJsonObject("license policy")
        val version = (root["version"] as? Number)?.toInt()
        if (version != POLICY_VERSION) {
            throw GradleException("Unsupported license policy version: $version")
        }
        val allowed =
            root.list("allowedLicenses")
                .map { entry ->
                    when (entry) {
                        is String -> entry
                        is Map<*, *> -> entry["moduleLicense"]?.toString().orEmpty()
                        else -> ""
                    }
                }.filter(String::isNotBlank)
                .toSet()
        if (allowed.isEmpty()) {
            throw GradleException("License policy allowedLicenses must not be empty")
        }
        val aliases =
            (root["aliases"] as? Map<*, *>)
                ?.entries
                ?.associate { (key, value) -> key.toString() to value.toString() }
                .orEmpty()
        return RuntimeLicensePolicy(allowed, aliases)
    }

    fun readComponents(
        file: File,
        aliases: Map<String, String>,
    ): List<RuntimeLicensedComponent> {
        val root = file.parseJsonObject("CycloneDX SBOM")
        val components = root["components"] as? List<*> ?: throw GradleException("CycloneDX SBOM is missing components")
        return components
            .map { raw ->
                val component = raw as? Map<*, *> ?: throw GradleException("Invalid CycloneDX component entry")
                val licenses =
                    (component["licenses"] as? List<*>)
                        .orEmpty()
                        .mapNotNull { entry ->
                            val licenseEntry = entry as? Map<*, *> ?: return@mapNotNull null
                            val license = licenseEntry["license"] as? Map<*, *> ?: return@mapNotNull null
                            val value = license["id"]?.toString() ?: license["name"]?.toString()
                            value?.let { aliases[it] ?: it }
                        }.toSet()
                RuntimeLicensedComponent(
                    name = component["name"]?.toString().orEmpty(),
                    version = component["version"]?.toString().orEmpty(),
                    licenses = licenses,
                )
            }.sortedWith(compareBy(RuntimeLicensedComponent::name, RuntimeLicensedComponent::version))
    }

    fun renderReport(
        policy: RuntimeLicensePolicy,
        components: List<RuntimeLicensedComponent>,
        violations: List<RuntimeLicensedComponent>,
    ): String =
        buildString {
            appendLine("Runtime license policy: ${if (violations.isEmpty()) "PASS" else "FAIL"}")
            appendLine("Allowed licenses: ${policy.allowedLicenses.sorted().joinToString(", ")}")
            appendLine("Components:")
            components.forEach { component ->
                appendLine(
                    "- ${component.name}:${component.version} = " +
                        component.licenses.ifEmpty { setOf("UNKNOWN") }.sorted().joinToString(", "),
                )
            }
            if (violations.isNotEmpty()) {
                appendLine("Violations:")
                violations.forEach { component ->
                    appendLine(
                        "- ${component.name}:${component.version} = " +
                            component.licenses.ifEmpty { setOf("UNKNOWN") }.sorted().joinToString(", "),
                    )
                }
            }
        }

    private fun File.parseJsonObject(description: String): Map<*, *> =
        JsonSlurper().parse(this) as? Map<*, *> ?: throw GradleException("Invalid $description JSON: $path")

    private fun Map<*, *>.list(name: String): List<*> =
        (this[name] as? List<*>)
            ?: throw GradleException("License policy is missing $name")

    private const val POLICY_VERSION = 1
}
