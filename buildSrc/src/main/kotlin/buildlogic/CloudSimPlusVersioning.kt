package buildlogic

import org.gradle.api.GradleException
import java.io.File

internal object CloudSimPlusVersioning {
    private val remoteSemverTag = Regex("""refs/tags/(v?(\d+)\.(\d+)\.(\d+))$""")
    private val semverTag = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")
    private val pomVersion =
        Regex("""<artifactId>\s*cloudsimplus\s*</artifactId>[\s\S]*?<version>\s*([^<]+)\s*</version>""")

    fun parseLatestReleaseTag(refs: String): String? =
        refs
            .lineSequence()
            .mapNotNull { line ->
                val match = remoteSemverTag.find(line.trim()) ?: return@mapNotNull null
                val version =
                    listOf(
                        match.groupValues[2],
                        match.groupValues[3],
                        match.groupValues[4],
                    ).map(String::toInt)
                version to match.groupValues[1]
            }.maxWithOrNull(compareBy({ it.first[0] }, { it.first[1] }, { it.first[2] }))
            ?.second

    fun tagVersion(tag: String): Pair<List<Int>, String>? {
        val match = semverTag.matchEntire(tag) ?: return null
        val (major, minor, patch) = match.destructured
        return listOf(major.toInt(), minor.toInt(), patch.toInt()) to tag
    }

    fun readCloudSimPlusVersion(source: File): String {
        val pom = source.resolve("pom.xml")
        check(pom.isFile) { "CloudSim Plus pom.xml not found: ${pom.path}" }
        return pomVersion
            .find(pom.readText())
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?: "unknown"
    }

    fun latestReleaseTagFromLocalTags(
        tags: String,
        source: File,
    ): String =
        tags
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapNotNull(::tagVersion)
            .maxWithOrNull(compareBy({ it.first[0] }, { it.first[1] }, { it.first[2] }))
            ?.second
            ?: throw GradleException("No semver release tag found in ${source.path}")
}
