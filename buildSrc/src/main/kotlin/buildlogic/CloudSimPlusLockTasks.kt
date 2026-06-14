package buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Verifies mutable Git checkout state")
abstract class VerifyCloudSimPlusLockTask : DefaultTask() {
    @get:InputFile
    abstract val lockFile: RegularFileProperty

    @get:InputFile
    abstract val metadataFile: RegularFileProperty

    @get:Internal
    abstract val rootDir: DirectoryProperty

    @get:Internal
    abstract val sourceDir: DirectoryProperty

    @get:Input
    abstract val enforceLock: Property<Boolean>

    @TaskAction
    fun verify() {
        if (!enforceLock.get()) {
            logger.lifecycle("CloudSim Plus lock verification skipped for explicit/latest compatibility build")
            return
        }
        val expected = CloudSimPlusLockSupport.read(lockFile.get().asFile)
        val actual = CloudSimPlusLockSupport.read(metadataFile.get().asFile)
        check(actual == expected) {
            "CloudSim Plus metadata drift: expected=$expected actual=$actual"
        }
        val repositoryRoot = rootDir.get().asFile
        if (repositoryRoot.resolve(".git").exists()) {
            val indexedCommit =
                runGit(repositoryRoot, "ls-files", "-s", "--", "third_party/cloudsimplus")
                    .split(Regex("\\s+"))
                    .getOrNull(1)
            check(indexedCommit == expected.commit) {
                "CloudSim Plus submodule gitlink drift: expected=${expected.commit} actual=$indexedCommit"
            }
        } else {
            logger.lifecycle("Root Git metadata is unavailable; skipping submodule gitlink verification")
        }
        val checkoutCommit = runGit(sourceDir.get().asFile, "rev-parse", "HEAD")
        check(checkoutCommit == expected.commit) {
            "CloudSim Plus checkout drift: expected=${expected.commit} actual=$checkoutCommit"
        }
        logger.lifecycle("CloudSim Plus lock verified: ${expected.ref} ${expected.commit} ${expected.version}")
    }

    private fun runGit(
        directory: java.io.File,
        vararg arguments: String,
    ): String =
        ProcessBuilder(listOf("git") + arguments)
            .directory(directory)
            .redirectErrorStream(true)
            .start()
            .run {
                val output = inputStream.bufferedReader().readText().trim()
                check(waitFor() == 0) { "Git command failed in ${directory.path}: $output" }
                output
            }
}

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
