package buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
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
        val source = sourceDir.get().asFile
        val version = versionFile.get().asFile
        val offlineMode = offline.get()
        val autoUpdateEnabled = autoUpdate.get()
        val selectedOverride = requestedRef.get().trim()

        ensureSourceCheckout(source, offlineMode, selectedOverride)
        val selectedRef =
            selectedOverride.ifBlank {
                if (!offlineMode && autoUpdateEnabled) {
                    latestReleaseTagFromRemote() ?: latestReleaseTag(source)
                } else {
                    latestReleaseTag(source)
                }
            }
        fetchSelectedRef(source, selectedRef, offlineMode, autoUpdateEnabled, selectedOverride)
        execGit(listOf("-C", source.path, "checkout", selectedRef))

        val commit = execGit(listOf("-C", source.path, "rev-parse", "HEAD"))
        val cloudSimVersion = readCloudSimPlusVersion(source)
        version.parentFile.mkdirs()
        version.writeText(
            listOf(
                "ref=$selectedRef",
                "commit=$commit",
                "version=$cloudSimVersion",
            ).joinToString(System.lineSeparator()) + System.lineSeparator(),
        )
        logger.lifecycle("CloudSim Plus source ready: ref=$selectedRef commit=$commit version=$cloudSimVersion")
    }

    private fun ensureSourceCheckout(
        source: File,
        offlineMode: Boolean,
        selectedOverride: String,
    ) {
        val gitMarker = source.resolve(".git")
        if (gitMarker.exists() && isUsableGitCheckout(source)) {
            return
        }
        if (offlineMode) {
            throw GradleException("CloudSim Plus source is missing at ${source.path}; disable cloudsimplus.offline to clone it.")
        }

        val submoduleResult =
            execGit(
                listOf("submodule", "update", "--init", "--recursive", "--", "third_party/cloudsimplus"),
                ignoreExit = true,
            )
        if (gitMarker.exists() && isUsableGitCheckout(source)) {
            logger.lifecycle("CloudSim Plus submodule initialized: $submoduleResult")
            return
        }

        if (source.exists()) {
            if (gitMarker.exists()) {
                resetBrokenCheckout(source)
            }
            val entries = source.listFiles().orEmpty()
            if (entries.isNotEmpty()) {
                throw GradleException("CloudSim Plus source directory exists but is not a git checkout: ${source.path}")
            }
        } else {
            source.parentFile.mkdirs()
        }
        cloneSourceCheckout(source, selectedOverride)
    }

    private fun isUsableGitCheckout(source: File): Boolean {
        if (!source.resolve(".git").exists()) {
            return false
        }
        return execGit(
            listOf("-C", source.path, "rev-parse", "--is-inside-work-tree"),
            ignoreExit = true,
        ).trim() == "true"
    }

    private fun resetBrokenCheckout(source: File) {
        logger.lifecycle("CloudSim Plus source has a broken git checkout; cloning a fresh copy: ${source.path}")
        if (!source.deleteRecursively()) {
            throw GradleException("Failed to remove broken CloudSim Plus checkout: ${source.path}")
        }
        source.parentFile.mkdirs()
    }

    private fun cloneSourceCheckout(source: File, selectedOverride: String) {
        val shallowCloneRef = selectedOverride.takeIf { it.isNotBlank() } ?: latestReleaseTagFromRemote()
        val cloneArgs =
            if (shallowCloneRef.isNullOrBlank()) {
                listOf("clone", repositoryUrl.get(), source.path)
            } else {
                listOf(
                    "clone",
                    "--depth",
                    "1",
                    "--branch",
                    shallowCloneRef,
                    "--single-branch",
                    repositoryUrl.get(),
                    source.path,
                )
            }
        execGit(cloneArgs)
    }

    private fun fetchSelectedRef(
        source: File,
        selectedRef: String,
        offlineMode: Boolean,
        autoUpdateEnabled: Boolean,
        selectedOverride: String,
    ) {
        if (offlineMode || !autoUpdateEnabled) {
            return
        }
        if (currentExactTag(source) == selectedRef) {
            logger.lifecycle("CloudSim Plus already at $selectedRef; skipping fetch")
            return
        }
        val fetchArgs =
            if (selectedOverride.isBlank()) {
                listOf("-C", source.path, "fetch", "--depth", "1", "origin", "tag", selectedRef)
            } else {
                listOf("-C", source.path, "fetch", "--depth", "1", "origin", selectedRef)
            }
        execGit(fetchArgs, ignoreExit = true)
    }

    private fun currentExactTag(source: File): String? =
        execGit(
            listOf("-C", source.path, "describe", "--tags", "--exact-match"),
            ignoreExit = true,
        ).trim().takeIf(String::isNotBlank)

    private fun latestReleaseTagFromRemote(): String? =
        execGit(
            listOf("ls-remote", "--tags", repositoryUrl.get()),
            ignoreExit = true,
        ).let(::parseLatestReleaseTag)

    private fun latestReleaseTag(source: File): String =
        execGit(listOf("-C", source.path, "tag", "--list"))
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapNotNull(::tagVersion)
            .maxWithOrNull(compareBy({ it.first[0] }, { it.first[1] }, { it.first[2] }))
            ?.second
            ?: throw GradleException("No semver release tag found in ${source.path}")

    private fun parseLatestReleaseTag(refs: String): String? =
        refs
            .lineSequence()
            .mapNotNull { line ->
                val match = REMOTE_SEMVER_TAG.find(line.trim()) ?: return@mapNotNull null
                val version =
                    listOf(
                        match.groupValues[2],
                        match.groupValues[3],
                        match.groupValues[4],
                    ).map(String::toInt)
                version to match.groupValues[1]
            }.maxWithOrNull(compareBy({ it.first[0] }, { it.first[1] }, { it.first[2] }))
            ?.second

    private fun tagVersion(tag: String): Pair<List<Int>, String>? {
        val match = SEMVER_TAG.matchEntire(tag) ?: return null
        val (major, minor, patch) = match.destructured
        return listOf(major.toInt(), minor.toInt(), patch.toInt()) to tag
    }

    private fun readCloudSimPlusVersion(source: File): String {
        val pom = source.resolve("pom.xml")
        check(pom.isFile) { "CloudSim Plus pom.xml not found: ${pom.path}" }
        return POM_VERSION
            .find(pom.readText())
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?: "unknown"
    }

    private fun execGit(
        args: List<String>,
        workDir: File = rootDir.get().asFile,
        ignoreExit: Boolean = false,
    ): String {
        var lastCommand = listOf("git") + args
        var lastOutput = ""
        gitOptionFallbacks().forEach { optionPrefix ->
            val command = listOf("git") + optionPrefix + args
            val result = runGitProcess(args, workDir, optionPrefix)
            if (result.exitCode == 0) {
                return result.output
            }
            lastCommand = command
            lastOutput = result.output
            logger.info("Git command failed, trying next fallback if available: ${command.joinToString(" ")}")
        }

        if (ignoreExit) {
            return lastOutput
        }
        throw GradleException("Git command failed: ${lastCommand.joinToString(" ")}\n$lastOutput")
    }

    private fun runGitProcess(
        args: List<String>,
        workDir: File,
        optionPrefix: List<String>,
    ): GitResult {
        val command = listOf("git") + optionPrefix + args
        val outputDir = temporaryDir.also(File::mkdirs)
        val outputFile = File.createTempFile("git-", ".log", outputDir)
        val process =
            ProcessBuilder(command)
                .directory(workDir)
                .redirectErrorStream(true)
                .redirectOutput(outputFile)
                .start()
        val completed = process.waitFor(gitTimeoutSeconds.get(), TimeUnit.SECONDS)
        val output = outputFile.readText().trim()
        outputFile.delete()
        if (!completed) {
            process.destroyForcibly()
            process.waitFor(PROCESS_SHUTDOWN_SECONDS, TimeUnit.SECONDS)
            return GitResult(
                exitCode = -1,
                output = "Timed out after ${gitTimeoutSeconds.get()}s: ${command.joinToString(" ")}\n$output",
            )
        }
        return GitResult(process.exitValue(), output)
    }

    private fun gitOptionFallbacks(): List<List<String>> =
        buildList {
            val proxy = networkProxy.get().trim()
            val windows = System.getProperty("os.name").lowercase().contains("windows")
            if (proxy.isNotBlank()) {
                add(listOf("-c", "http.proxy=$proxy", "-c", "https.proxy=$proxy"))
                if (windows) {
                    add(
                        listOf(
                            "-c",
                            "http.proxy=$proxy",
                            "-c",
                            "https.proxy=$proxy",
                            "-c",
                            "http.sslBackend=schannel",
                        ),
                    )
                }
            }
            add(emptyList())
            add(listOf("-c", "http.proxy=", "-c", "https.proxy="))
            if (windows) {
                add(listOf("-c", "http.proxy=", "-c", "https.proxy=", "-c", "http.sslBackend=schannel"))
            }
        }

    private data class GitResult(
        val exitCode: Int,
        val output: String,
    )

    companion object {
        private const val PROCESS_SHUTDOWN_SECONDS = 5L
        private val REMOTE_SEMVER_TAG = Regex("""refs/tags/(v?(\d+)\.(\d+)\.(\d+))$""")
        private val SEMVER_TAG = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")
        private val POM_VERSION =
            Regex("""<artifactId>\s*cloudsimplus\s*</artifactId>[\s\S]*?<version>\s*([^<]+)\s*</version>""")
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
        abstract val localMavenRepo: DirectoryProperty

        @get:Input
        abstract val networkProxy: Property<String>

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val sourceFiles: ConfigurableFileCollection

        @TaskAction
        fun build() {
            val source = sourceDir.get().asFile
            val localRepo = localMavenRepo.get().asFile
            localRepo.mkdirs()
            execOperations.exec {
                workingDir = source
                mavenOptions().takeIf(String::isNotBlank)?.let { options ->
                    environment("MAVEN_OPTS", options)
                }
                commandLine(
                    mavenWrapper(source),
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

        private fun mavenWrapper(source: File): String {
            val windows = System.getProperty("os.name").lowercase().contains("windows")
            val wrapperScript =
                if (windows) {
                    source.resolve("mvnw.cmd")
                } else {
                    source.resolve("mvnw")
                }
            return if (hasUsableMavenWrapper(source, wrapperScript)) {
                wrapperScript.absolutePath
            } else {
                if (windows) "mvn.cmd" else "mvn"
            }
        }

        private fun hasUsableMavenWrapper(
            source: File,
            wrapperScript: File,
        ): Boolean {
            if (!wrapperScript.isFile) {
                return false
            }
            val wrapperJar = source.resolve(".mvn/wrapper/maven-wrapper.jar")
            if (!wrapperJar.isFile) {
                return false
            }
            return runCatching {
                JarFile(wrapperJar).use { jar ->
                    jar.getEntry("org/apache/maven/wrapper/MavenWrapperMain.class") != null
                }
            }.getOrDefault(false)
        }

        private fun mavenOptions(): String {
            val proxy = networkProxy.get().trim()
            val existingOptions = System.getenv("MAVEN_OPTS")?.takeIf(String::isNotBlank)
            val proxyOptions =
                if (proxy.isBlank()) {
                    null
                } else {
                    val proxyUri = URI(proxy)
                    val proxyHost = proxyUri.host ?: proxy.substringAfter("://", proxy).substringBefore(":")
                    val proxyPort =
                        proxyUri.port
                            .takeIf { it > 0 }
                            ?: proxy.substringAfterLast(":", "").toIntOrNull()
                            ?: DEFAULT_PROXY_PORT
                    listOf(
                        "-Dhttp.proxyHost=$proxyHost",
                        "-Dhttp.proxyPort=$proxyPort",
                        "-Dhttps.proxyHost=$proxyHost",
                        "-Dhttps.proxyPort=$proxyPort",
                    ).joinToString(" ")
                }
            return listOfNotNull(existingOptions, proxyOptions).joinToString(" ")
        }

        companion object {
            private const val DEFAULT_PROXY_PORT = 80
        }
    }

@DisableCachingByDefault(because = "Rewrites source-built jars in place")
abstract class SanitizeCloudSimPlusJarManifestTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val localMavenRepo: DirectoryProperty

    @get:Input
    abstract val artifactGroup: Property<String>

    @get:Input
    abstract val artifactName: Property<String>

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun sanitize() {
        val artifactRoot =
            localMavenRepo
                .get()
                .asFile
                .resolve("${artifactGroup.get()}/${artifactName.get()}".replace('.', File.separatorChar))
        check(artifactRoot.isDirectory) {
            "CloudSim Plus artifact directory not found: ${artifactRoot.path}"
        }
        val runtimeJars =
            artifactRoot
                .walkTopDown()
                .filter(::isRuntimeJar)
                .toList()
        check(runtimeJars.isNotEmpty()) {
            "No CloudSim Plus runtime jar found in ${artifactRoot.path}"
        }

        runtimeJars.forEach(::sanitizeJar)
    }

    private fun isRuntimeJar(file: File): Boolean =
        file.isFile &&
            file.extension == "jar" &&
            file.name.startsWith("${artifactName.get()}-") &&
            !file.name.endsWith("-sources.jar") &&
            !file.name.endsWith("-javadoc.jar")

    private fun sanitizeJar(jar: File) {
        val tempJar = jar.resolveSibling("${jar.name}.tmp")
        JarFile(jar).use { input ->
            val manifest = input.manifest ?: Manifest()
            if (manifest.mainAttributes.remove(Attributes.Name.CLASS_PATH) == null) {
                return
            }
            JarOutputStream(tempJar.outputStream().buffered(), manifest).use { output ->
                input.entries().asSequence().forEach { entry ->
                    if (entry.name.equals(JarFile.MANIFEST_NAME, ignoreCase = true)) {
                        return@forEach
                    }
                    output.putNextEntry(entry.copyWithoutStream())
                    if (!entry.isDirectory) {
                        input.getInputStream(entry).use { stream ->
                            stream.copyTo(output)
                        }
                    }
                    output.closeEntry()
                }
            }
        }
        replaceJar(jar, tempJar)
        logger.lifecycle("Sanitized CloudSim Plus jar manifest: ${jar.path}")
    }

    private fun JarEntry.copyWithoutStream(): JarEntry =
        JarEntry(name).also { copy ->
            copy.time = time
            copy.comment = comment
            copy.extra = extra
        }

    private fun replaceJar(jar: File, tempJar: File) {
        if (!jar.delete()) {
            tempJar.delete()
            throw GradleException("Failed to replace CloudSim Plus jar: ${jar.path}")
        }
        if (!tempJar.renameTo(jar)) {
            throw GradleException("Failed to move sanitized CloudSim Plus jar into place: ${jar.path}")
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
        verifyClasspath("compileClasspath", compileClasspath.files, localRepoRoot)
        verifyClasspath("runtimeClasspath", runtimeClasspath.files, localRepoRoot)
        verifyClasspath("testRuntimeClasspath", testRuntimeClasspath.files, localRepoRoot)
        logger.lifecycle("CloudSim Plus source build verified via ${localMavenRepo.get().asFile.path}")
    }

    private fun verifyClasspath(
        configurationName: String,
        files: Set<File>,
        localRepoRoot: java.nio.file.Path,
    ) {
        val cloudSimJars =
            files.filter { file ->
                file.name.startsWith("${artifactName.get()}-") && file.extension == "jar"
            }
        check(cloudSimJars.isNotEmpty()) {
            "No CloudSim Plus jar found in $configurationName"
        }
        cloudSimJars.forEach { jar ->
            check(jar.canonicalFile.toPath().startsWith(localRepoRoot)) {
                "CloudSim Plus jar for $configurationName is not from source build repo: ${jar.path}"
            }
            JarFile(jar).use { jarFile ->
                check(
                    jarFile.manifest
                        ?.mainAttributes
                        ?.getValue(Attributes.Name.CLASS_PATH)
                        .isNullOrBlank(),
                ) {
                    "CloudSim Plus jar for $configurationName still has manifest Class-Path: ${jar.path}"
                }
            }
        }
    }
}
