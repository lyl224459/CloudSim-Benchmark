package buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(because = "Runs an external container smoke check")
abstract class ContainerImageSmokeTask
    @Inject
    constructor(
        private val execOperations: ExecOperations,
    ) : DefaultTask() {
        @get:Input
        abstract val imageName: Property<String>

        @get:Input
        abstract val containerExecutable: Property<String>

        @get:Input
        abstract val ci: Property<Boolean>

        @get:Internal
        abstract val contextDirectory: DirectoryProperty

        @get:Internal
        abstract val containerFile: RegularFileProperty

        init {
            doNotTrackState("Runs an external container runtime and intentionally performs a smoke check every time")
            outputs.upToDateWhen { false }
        }

        @TaskAction
        fun smoke() {
            val containerRuntime = containerExecutable.get()
            val ciBuild = ci.get()
            if (!isContainerRuntimeAvailable(containerRuntime)) {
                val message = ContainerImageSmokeSupport.missingContainerRuntimeMessage(containerRuntime, ciBuild)
                if (ciBuild) {
                    throw GradleException(message)
                }
                logger.lifecycle(message)
                return
            }

            execOperations.exec {
                workingDir = contextDirectory.get().asFile
                commandLine(
                    ContainerImageSmokeSupport.buildCommand(
                        containerExecutable = containerRuntime,
                        imageName = imageName.get(),
                        containerFile = containerFile.get().asFile,
                    ),
                )
            }
            execOperations.exec {
                commandLine(
                    ContainerImageSmokeSupport.runCommand(
                        containerExecutable = containerRuntime,
                        imageName = imageName.get(),
                    ),
                )
            }
            execOperations.exec {
                commandLine(
                    ContainerImageSmokeSupport.inspectCommand(
                        containerExecutable = containerRuntime,
                        imageName = imageName.get(),
                    ),
                )
            }
        }

        private fun isContainerRuntimeAvailable(containerRuntime: String): Boolean =
            runCatching {
                execOperations.exec {
                    commandLine(containerRuntime, "--version")
                    isIgnoreExitValue = true
                }.exitValue == 0
            }.getOrDefault(false)
    }
