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
        val violations =
            references.filter { reference ->
                APPROVED_ACTION_MAJORS[reference.action]?.let { approved -> reference.version != approved } ?: false
            }
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

    companion object {
        private val ACTION_REFERENCE = Regex("""(?m)^\s*-?\s*uses:\s*([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)@(v\d+)\s*$""")
        private val APPROVED_ACTION_MAJORS =
            mapOf(
                "actions/cache" to "v5",
                "actions/checkout" to "v6",
                "actions/setup-java" to "v5",
                "actions/upload-artifact" to "v7",
                "docker/build-push-action" to "v7",
                "docker/login-action" to "v4",
                "docker/metadata-action" to "v6",
                "docker/setup-buildx-action" to "v4",
                "softprops/action-gh-release" to "v3",
            )
    }
}
