package buildlogic

import org.gradle.api.GradleException
import java.io.File

data class CloudSimPlusMetadata(
    val ref: String,
    val commit: String,
    val version: String,
) {
    fun render(): String =
        listOf(
            "ref=$ref",
            "commit=$commit",
            "version=$version",
        ).joinToString(System.lineSeparator()) + System.lineSeparator()
}

object CloudSimPlusLockSupport {
    private val commitPattern = Regex("^[0-9a-f]{40}$")
    private val versionPattern = Regex("^\\d+\\.\\d+\\.\\d+$")
    private val expectedKeys = setOf("ref", "commit", "version")

    fun parse(content: String): CloudSimPlusMetadata {
        val values =
            content
                .lineSequence()
                .map(String::trim)
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .associate { line ->
                    val separator = line.indexOf('=')
                    if (separator <= 0) {
                        throw GradleException("Invalid CloudSim Plus lock line: $line")
                    }
                    line.substring(0, separator).trim() to line.substring(separator + 1).trim()
                }
        val missing = expectedKeys - values.keys
        val unknown = values.keys - expectedKeys
        if (missing.isNotEmpty() || unknown.isNotEmpty()) {
            throw GradleException("Invalid CloudSim Plus lock keys: missing=$missing unknown=$unknown")
        }

        val metadata =
            CloudSimPlusMetadata(
                ref = values.getValue("ref"),
                commit = values.getValue("commit"),
                version = values.getValue("version"),
            )
        validate(metadata)
        return metadata
    }

    fun read(file: File): CloudSimPlusMetadata {
        if (!file.isFile) {
            throw GradleException("CloudSim Plus lock file is missing: ${file.path}")
        }
        return parse(file.readText())
    }

    fun writeIfChanged(
        file: File,
        metadata: CloudSimPlusMetadata,
    ) {
        validate(metadata)
        val content = metadata.render()
        file.parentFile.mkdirs()
        if (!file.isFile || file.readText() != content) {
            file.writeText(content)
        }
    }

    fun validate(metadata: CloudSimPlusMetadata) {
        if (metadata.ref.isBlank()) {
            throw GradleException("CloudSim Plus lock ref must not be blank")
        }
        if (!commitPattern.matches(metadata.commit)) {
            throw GradleException("CloudSim Plus lock commit must be a 40-character lowercase SHA: ${metadata.commit}")
        }
        if (!versionPattern.matches(metadata.version)) {
            throw GradleException("CloudSim Plus lock version must be a release semver: ${metadata.version}")
        }
    }
}

internal object CloudSimPlusRefSelection {
    fun select(
        requestedRef: String,
        autoUpdateEnabled: Boolean,
        lockedRef: String?,
        latestRelease: () -> String,
    ): String =
        when {
            requestedRef.isNotBlank() -> requestedRef
            autoUpdateEnabled -> latestRelease()
            !lockedRef.isNullOrBlank() -> lockedRef
            else -> throw GradleException("CloudSim Plus lock ref is required when auto-update is disabled")
        }
}
