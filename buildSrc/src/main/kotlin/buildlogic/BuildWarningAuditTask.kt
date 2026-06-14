package buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Audits externally captured build logs")
abstract class BuildWarningAuditTask : DefaultTask() {
    @get:Internal
    abstract val logDirectory: DirectoryProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun audit() {
        val result = BuildWarningAuditSupport.audit(logDirectory.get().asFile)
        val report = reportFile.get().asFile
        report.parentFile.mkdirs()
        report.writeText(BuildWarningAuditSupport.renderMarkdown(result))
        logger.lifecycle("Build warning audit report: ${report.absolutePath}")

        if (result.violations.isNotEmpty()) {
            throw GradleException(
                "Build warning audit found ${result.violations.size} violation(s). See ${report.absolutePath}",
            )
        }
    }
}
