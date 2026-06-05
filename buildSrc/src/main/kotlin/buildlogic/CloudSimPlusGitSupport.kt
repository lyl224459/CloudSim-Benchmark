package buildlogic

import org.gradle.api.GradleException
import java.io.File
import java.util.concurrent.TimeUnit

internal class CloudSimPlusGitClient(
    private val rootDir: File,
    private val networkProxy: String,
    private val timeoutSeconds: Long,
    private val temporaryDir: File,
    private val logInfo: (String) -> Unit,
) {
    fun exec(
        args: List<String>,
        workDir: File = rootDir,
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
            logInfo("Git command failed, trying next fallback if available: ${command.joinToString(" ")}")
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
        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        val output = outputFile.readText().trim()
        outputFile.delete()
        if (!completed) {
            process.destroyForcibly()
            process.waitFor(PROCESS_SHUTDOWN_SECONDS, TimeUnit.SECONDS)
            return GitResult(
                exitCode = -1,
                output = "Timed out after ${timeoutSeconds}s: ${command.joinToString(" ")}\n$output",
            )
        }
        return GitResult(process.exitValue(), output)
    }

    private fun gitOptionFallbacks(): List<List<String>> =
        buildList {
            val proxy = networkProxy.trim()
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
    }
}

internal class CloudSimPlusSourcePreparer(
    private val repositoryUrl: String,
    private val rootDir: File,
    private val git: CloudSimPlusGitClient,
    private val logLifecycle: (String) -> Unit,
) {
    fun prepare(
        source: File,
        versionFile: File,
        offlineMode: Boolean,
        autoUpdateEnabled: Boolean,
        selectedOverride: String,
    ) {
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
        git.exec(listOf("-C", source.path, "checkout", selectedRef))

        val commit = git.exec(listOf("-C", source.path, "rev-parse", "HEAD"))
        val cloudSimVersion = CloudSimPlusVersioning.readCloudSimPlusVersion(source)
        writeVersionFileIfChanged(versionFile, selectedRef, commit, cloudSimVersion)
        logLifecycle("CloudSim Plus source ready: ref=$selectedRef commit=$commit version=$cloudSimVersion")
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
            git.exec(
                listOf("submodule", "update", "--init", "--recursive", "--", "third_party/cloudsimplus"),
                ignoreExit = true,
            )
        if (gitMarker.exists() && isUsableGitCheckout(source)) {
            logLifecycle("CloudSim Plus submodule initialized: $submoduleResult")
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
        return git.exec(
            listOf("-C", source.path, "rev-parse", "--is-inside-work-tree"),
            ignoreExit = true,
        ).trim() == "true"
    }

    private fun resetBrokenCheckout(source: File) {
        logLifecycle("CloudSim Plus source has a broken git checkout; cloning a fresh copy: ${source.path}")
        if (!source.deleteRecursively()) {
            throw GradleException("Failed to remove broken CloudSim Plus checkout: ${source.path}")
        }
        source.parentFile.mkdirs()
    }

    private fun cloneSourceCheckout(
        source: File,
        selectedOverride: String,
    ) {
        val shallowCloneRef = selectedOverride.takeIf { it.isNotBlank() } ?: latestReleaseTagFromRemote()
        val cloneArgs =
            if (shallowCloneRef.isNullOrBlank()) {
                listOf("clone", repositoryUrl, source.path)
            } else {
                listOf(
                    "clone",
                    "--depth",
                    "1",
                    "--branch",
                    shallowCloneRef,
                    "--single-branch",
                    repositoryUrl,
                    source.path,
                )
            }
        git.exec(cloneArgs, rootDir)
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
            logLifecycle("CloudSim Plus already at $selectedRef; skipping fetch")
            return
        }
        val fetchArgs =
            if (selectedOverride.isBlank()) {
                listOf("-C", source.path, "fetch", "--depth", "1", "origin", "tag", selectedRef)
            } else {
                listOf("-C", source.path, "fetch", "--depth", "1", "origin", selectedRef)
            }
        git.exec(fetchArgs, ignoreExit = true)
    }

    private fun currentExactTag(source: File): String? =
        git.exec(
            listOf("-C", source.path, "describe", "--tags", "--exact-match"),
            ignoreExit = true,
        ).trim().takeIf(String::isNotBlank)

    private fun latestReleaseTagFromRemote(): String? =
        git.exec(
            listOf("ls-remote", "--tags", repositoryUrl),
            ignoreExit = true,
        ).let(CloudSimPlusVersioning::parseLatestReleaseTag)

    private fun latestReleaseTag(source: File): String =
        CloudSimPlusVersioning.latestReleaseTagFromLocalTags(
            tags = git.exec(listOf("-C", source.path, "tag", "--list")),
            source = source,
        )

    private fun writeVersionFileIfChanged(
        versionFile: File,
        selectedRef: String,
        commit: String,
        cloudSimVersion: String,
    ) {
        val content =
            listOf(
                "ref=$selectedRef",
                "commit=$commit",
                "version=$cloudSimVersion",
            ).joinToString(System.lineSeparator()) + System.lineSeparator()
        versionFile.parentFile.mkdirs()
        if (!versionFile.isFile || versionFile.readText() != content) {
            versionFile.writeText(content)
        }
    }
}
