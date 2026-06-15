package buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Updates a repository-maintained lock file")
abstract class UpdateCloudSimPlusLockTask : DefaultTask() {
    @get:Input
    abstract val updateAllowed: Property<Boolean>

    @get:InputFile
    abstract val metadataFile: RegularFileProperty

    @get:OutputFile
    abstract val lockFile: RegularFileProperty

    @TaskAction
    fun update() {
        if (!updateAllowed.get()) {
            throw GradleException("updateCloudSimPlusLock requires -Pcloudsimplus.ref=<ref> or -Pcloudsimplus.autoUpdate=true")
        }
        val metadata = CloudSimPlusLockSupport.read(metadataFile.get().asFile)
        CloudSimPlusLockSupport.writeIfChanged(lockFile.get().asFile, metadata)
        logger.lifecycle("CloudSim Plus lock updated: $metadata")
    }
}
