package buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(because = "Updates a mutable Git checkout")
abstract class PrepareCloudSimPlusSourceTask : DefaultTask() {
    @get:Input
    abstract val repositoryUrl: Property<String>

    @get:Input
    abstract val autoUpdate: Property<Boolean>

    @get:Input
    abstract val offline: Property<Boolean>

    @get:Input
    abstract val requestedRef: Property<String>

    @get:InputFile
    abstract val lockFile: RegularFileProperty

    @get:Input
    abstract val enforceLock: Property<Boolean>

    @get:Input
    abstract val networkProxy: Property<String>

    @get:Input
    abstract val gitTimeoutSeconds: Property<Long>

    @get:Internal
    abstract val rootDir: DirectoryProperty

    @get:Internal
    abstract val sourceDir: DirectoryProperty

    @get:OutputFile
    abstract val versionFile: RegularFileProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun prepare() {
        val git =
            CloudSimPlusGitClient(
                rootDir = rootDir.get().asFile,
                networkProxy = networkProxy.get(),
                timeoutSeconds = gitTimeoutSeconds.get(),
                temporaryDir = temporaryDir,
                logInfo = logger::info,
            )
        CloudSimPlusSourcePreparer(
            repositoryUrl = repositoryUrl.get(),
            rootDir = rootDir.get().asFile,
            git = git,
            logLifecycle = logger::lifecycle,
        ).prepare(
            source = sourceDir.get().asFile,
            versionFile = versionFile.get().asFile,
            offlineMode = offline.get(),
            autoUpdateEnabled = autoUpdate.get(),
            selectedOverride = requestedRef.get().trim(),
            lockedMetadata = CloudSimPlusLockSupport.read(lockFile.get().asFile).takeIf { enforceLock.get() },
        )
    }
}

@DisableCachingByDefault(because = "Runs Maven in a mutable source checkout")
abstract class BuildCloudSimPlusFromSourceTask
    @Inject
    constructor(
        private val execOperations: ExecOperations,
    ) : DefaultTask() {
        @get:Internal
        abstract val sourceDir: DirectoryProperty

        @get:OutputDirectory
        abstract val rawMavenRepo: DirectoryProperty

        @get:Input
        abstract val networkProxy: Property<String>

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val sourceFiles: ConfigurableFileCollection

        @TaskAction
        fun build() {
            val source = sourceDir.get().asFile
            val localRepo = rawMavenRepo.get().asFile
            localRepo.mkdirs()
            execOperations.exec {
                workingDir = source
                CloudSimPlusMavenSupport
                    .mavenOptions(networkProxy.get())
                    .takeIf(String::isNotBlank)
                    ?.let { options -> environment("MAVEN_OPTS", options) }
                commandLine(
                    CloudSimPlusMavenSupport.mavenExecutable(source),
                    "-Dmaven.repo.local=${localRepo.absolutePath}",
                    "-DskipTests",
                    "-DskipITs",
                    "-Dgpg.skip=true",
                    "-Dlicense.skip=true",
                    "-Dmaven.javadoc.skip=true",
                    "install",
                )
            }
        }
    }

@DisableCachingByDefault(because = "Copies and sanitizes locally built CloudSim Plus artifacts")
abstract class SanitizeCloudSimPlusJarManifestTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val rawMavenRepo: DirectoryProperty

    @get:OutputDirectory
    abstract val sanitizedMavenRepo: DirectoryProperty

    @get:Input
    abstract val artifactGroup: Property<String>

    @get:Input
    abstract val artifactName: Property<String>

    @TaskAction
    fun sanitize() {
        val runtimeJars =
            CloudSimPlusJarSanitizer.sanitizeRepository(
                rawMavenRepo = rawMavenRepo.get().asFile,
                sanitizedMavenRepo = sanitizedMavenRepo.get().asFile,
                artifactGroup = artifactGroup.get(),
                artifactName = artifactName.get(),
            )
        runtimeJars.forEach { jar ->
            logger.lifecycle("Sanitized CloudSim Plus jar manifest: ${jar.path}")
        }
    }
}

@DisableCachingByDefault(because = "Verifies resolved classpath artifacts")
abstract class VerifyCloudSimPlusSourceBuildTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val localMavenRepo: DirectoryProperty

    @get:Input
    abstract val artifactName: Property<String>

    @get:Classpath
    abstract val compileClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val testRuntimeClasspath: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val localRepoRoot = localMavenRepo.get().asFile.canonicalFile.toPath()
        CloudSimPlusClasspathVerifier.verifyClasspath(
            configurationName = "compileClasspath",
            files = compileClasspath.files,
            localRepoRoot = localRepoRoot,
            artifactName = artifactName.get(),
        )
        CloudSimPlusClasspathVerifier.verifyClasspath(
            configurationName = "runtimeClasspath",
            files = runtimeClasspath.files,
            localRepoRoot = localRepoRoot,
            artifactName = artifactName.get(),
        )
        CloudSimPlusClasspathVerifier.verifyClasspath(
            configurationName = "testRuntimeClasspath",
            files = testRuntimeClasspath.files,
            localRepoRoot = localRepoRoot,
            artifactName = artifactName.get(),
        )
        logger.lifecycle("CloudSim Plus source build verified via ${localMavenRepo.get().asFile.path}")
    }
}
