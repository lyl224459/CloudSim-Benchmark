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
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.api.tasks.CacheableTask
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

@DisableCachingByDefault(because = "Updates a mutable Git checkout")
abstract class PrepareMutableCloudSimPlusSourceTask : DefaultTask() {
    @get:Input
    abstract val repositoryUrl: Property<String>

    @get:Input
    abstract val autoUpdate: Property<Boolean>

    @get:Input
    abstract val offline: Property<Boolean>

    @get:Input
    abstract val requestedRef: Property<String>

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
            lockedMetadata = null,
        )
    }
}

@CacheableTask
abstract class VerifyLockedCloudSimPlusSourceTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val lockFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourcePom: ConfigurableFileCollection

    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val gitStateFiles: ConfigurableFileCollection

    @get:Internal
    abstract val rootDir: DirectoryProperty

    @get:Internal
    abstract val sourceDir: DirectoryProperty

    @get:OutputFile
    abstract val versionFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val source = sourceDir.get().asFile
        val gitMarker = source.resolve(".git")
        val pom = sourcePom.files.singleOrNull()
        if (!gitMarker.exists() || pom == null || !pom.isFile) {
            throw org.gradle.api.GradleException(
                "CloudSim Plus locked source is missing at ${source.path}; " +
                    "run git submodule update --init --recursive.",
            )
        }

        val expected = CloudSimPlusLockSupport.read(lockFile.get().asFile)
        val checkoutCommit = runGit(source, "rev-parse", "HEAD")
        check(checkoutCommit == expected.commit) {
            "CloudSim Plus checkout drift: expected=${expected.commit} actual=$checkoutCommit"
        }
        val actualVersion = CloudSimPlusVersioning.readCloudSimPlusVersion(source)
        check(actualVersion == expected.version) {
            "CloudSim Plus POM version drift: expected=${expected.version} actual=$actualVersion"
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

        CloudSimPlusLockSupport.writeIfChanged(versionFile.get().asFile, expected)
        logger.lifecycle("CloudSim Plus locked source verified: ${expected.ref} ${expected.commit} ${expected.version}")
    }

    private fun runGit(
        directory: File,
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

@DisableCachingByDefault(because = "Runs Maven in a mutable source checkout")
abstract class BuildCloudSimPlusFromSourceTask
    @Inject
    constructor(
        private val execOperations: ExecOperations,
    ) : DefaultTask() {
        @get:Internal
        abstract val sourceDir: DirectoryProperty

        @get:Internal
        abstract val mavenCacheDir: DirectoryProperty

        @get:OutputDirectory
        abstract val rawMavenRepo: DirectoryProperty

        @get:InputFile
        abstract val metadataFile: RegularFileProperty

        @get:Input
        abstract val artifactGroup: Property<String>

        @get:Input
        abstract val artifactName: Property<String>

        @get:Input
        abstract val networkProxy: Property<String>

        @get:Input
        @get:Optional
        abstract val mavenExecutableOverride: Property<String>

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val sourceFiles: ConfigurableFileCollection

        @TaskAction
        fun build() {
            val source = sourceDir.get().asFile
            val dependencyCache = mavenCacheDir.get().asFile
            dependencyCache.mkdirs()
            val mavenExecutable =
                mavenExecutableOverride.orNull
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?: CloudSimPlusMavenSupport.mavenExecutable(source)
            execOperations.exec {
                workingDir = source
                CloudSimPlusMavenSupport
                    .mavenOptions(networkProxy.get())
                    .takeIf(String::isNotBlank)
                    ?.let { options -> environment("MAVEN_OPTS", options) }
                commandLine(
                    mavenExecutable,
                    *CloudSimPlusMavenSupport.packageArguments(dependencyCache).toTypedArray(),
                )
            }
            val metadata = CloudSimPlusLockSupport.read(metadataFile.get().asFile)
            val stagedArtifacts =
                CloudSimPlusArtifactStager.stage(
                    sourceDir = source,
                    rawMavenRepo = rawMavenRepo.get().asFile,
                    artifactGroup = artifactGroup.get(),
                    artifactName = artifactName.get(),
                    artifactVersion = metadata.version,
                )
            logger.lifecycle("Staged CloudSim Plus artifacts: ${stagedArtifacts.joinToString { it.name }}")
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

    @get:Input
    abstract val artifactVersion: Property<String>

    @TaskAction
    fun sanitize() {
        val runtimeJars =
            CloudSimPlusJarSanitizer.sanitizeRepository(
                rawMavenRepo = rawMavenRepo.get().asFile,
                sanitizedMavenRepo = sanitizedMavenRepo.get().asFile,
                artifactGroup = artifactGroup.get(),
                artifactName = artifactName.get(),
                artifactVersion = artifactVersion.get(),
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
