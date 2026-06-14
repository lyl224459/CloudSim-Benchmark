package buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

private val DETEKT_BASELINE_ASSIGNMENT = Regex("""(?m)^\s*baseline\s*=""")

abstract class VerifyNoDetektBaselineTask : DefaultTask() {
    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val baselineFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val buildScripts: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val existingBaselines = baselineFiles.files.filter(File::exists)
        if (existingBaselines.isNotEmpty()) {
            throw GradleException(
                "Detekt baseline files are forbidden: ${existingBaselines.joinToString { it.path }}",
            )
        }

        val configuredScripts =
            buildScripts.files.filter { script ->
                script.isFile && DETEKT_BASELINE_ASSIGNMENT.containsMatchIn(script.readText())
            }
        if (configuredScripts.isNotEmpty()) {
            throw GradleException(
                "Detekt baseline configuration is forbidden: ${configuredScripts.joinToString { it.path }}",
            )
        }
    }
}
