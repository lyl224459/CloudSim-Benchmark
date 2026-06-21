package buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class VerifyGitHubActionsPolicyTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val workflowFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val references =
            workflowFiles.files
                .filter { it.isFile }
                .flatMap { file ->
                    ACTION_REFERENCE
                        .findAll(file.readText())
                        .map { match -> ActionReference(file.name, match.groupValues[1], match.groupValues[2]) }
                        .toList()
                }.sortedWith(compareBy(ActionReference::workflow, ActionReference::action))
        val workflows = workflowFiles.files.filter { it.isFile }.associateBy { it.name }
        val violations =
            references.filter { reference ->
                APPROVED_ACTION_MAJORS[reference.action]?.let { approved -> reference.version != approved } ?: false
            } + references.filter { reference -> reference.action in FORBIDDEN_ACTIONS } +
                missingRequiredReleaseActions(references) +
                missingRequiredSecretScanPolicy(workflows, references)
        val output = reportFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(renderReport(references, violations))
        if (violations.isNotEmpty()) {
            throw GradleException(
                "GitHub Actions policy violations: " +
                    violations.joinToString { "${it.workflow}:${it.action}@${it.version}" },
            )
        }
    }

    private fun renderReport(
        references: List<ActionReference>,
        violations: List<ActionReference>,
    ): String =
        buildString {
            appendLine("GitHub Actions Node 24 policy: ${if (violations.isEmpty()) "PASS" else "FAIL"}")
            appendLine("Approved actions:")
            APPROVED_ACTION_MAJORS.toSortedMap().forEach { (action, version) ->
                appendLine("- $action@$version")
            }
            appendLine("Observed references:")
            references.forEach { reference ->
                appendLine("- ${reference.workflow}: ${reference.action}@${reference.version}")
            }
            if (violations.isNotEmpty()) {
                appendLine("Violations:")
                violations.forEach { reference ->
                    appendLine("- ${reference.workflow}: ${reference.action}@${reference.version}")
                }
            }
        }

    private data class ActionReference(
        val workflow: String,
        val action: String,
        val version: String,
    )

    private fun missingRequiredReleaseActions(references: List<ActionReference>): List<ActionReference> {
        if (references.none { it.workflow == RELEASE_WORKFLOW }) return emptyList()
        return REQUIRED_RELEASE_ACTIONS
            .filterNot { required ->
                references.any { it.workflow == RELEASE_WORKFLOW && it.action == required && it.version == APPROVED_ACTION_MAJORS[required] }
            }.map { required -> ActionReference(RELEASE_WORKFLOW, required, "missing") }
    }

    companion object {
        private const val RELEASE_WORKFLOW = "release.yml"
        private const val SECRETS_WORKFLOW = "secrets.yml"
        private val ACTION_REFERENCE = Regex("""(?m)^\s*-?\s*uses:\s*([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)@(v\d+)\s*$""")
        private val FETCH_DEPTH_ZERO = Regex("""(?m)^\s*fetch-depth:\s*0\s*$""")
        private val REQUIRED_RELEASE_ACTIONS = setOf("actions/attest")
        private val FORBIDDEN_ACTIONS =
            setOf(
                "docker/build-push-action",
                "docker/login-action",
                "docker/metadata-action",
                "docker/setup-buildx-action",
            )
        private val APPROVED_ACTION_MAJORS =
            mapOf(
                "actions/attest" to "v4",
                "actions/cache" to "v5",
                "actions/checkout" to "v6",
                "actions/download-artifact" to "v8",
                "actions/setup-java" to "v5",
                "actions/upload-artifact" to "v7",
                "gitleaks/gitleaks-action" to "v3",
                "softprops/action-gh-release" to "v3",
            )
    }

    private fun missingRequiredSecretScanPolicy(
        workflows: Map<String, java.io.File>,
        references: List<ActionReference>,
    ): List<ActionReference> {
        val secretsWorkflow = workflows[SECRETS_WORKFLOW] ?: return listOf(
            ActionReference(SECRETS_WORKFLOW, "gitleaks/gitleaks-action", "missing"),
        )
        val missingGitleaks =
            references.none {
                it.workflow == SECRETS_WORKFLOW &&
                    it.action == "gitleaks/gitleaks-action" &&
                    it.version == APPROVED_ACTION_MAJORS["gitleaks/gitleaks-action"]
            }
        val missingFullHistoryCheckout = !FETCH_DEPTH_ZERO.containsMatchIn(secretsWorkflow.readText())
        return buildList {
            if (missingGitleaks) {
                add(ActionReference(SECRETS_WORKFLOW, "gitleaks/gitleaks-action", "missing"))
            }
            if (missingFullHistoryCheckout) {
                add(ActionReference(SECRETS_WORKFLOW, "actions/checkout(fetch-depth=0)", "missing"))
            }
        }
    }
}
