package buildlogic

import org.gradle.api.GradleException

data class ReleaseManifest(
    val format: Int,
    val cloudSimPlus: CloudSimPlusMetadata,
    val assets: List<String>,
) {
    fun render(): String =
        buildList {
            add("format=$format")
            add("cloudsimplus.ref=${cloudSimPlus.ref}")
            add("cloudsimplus.commit=${cloudSimPlus.commit}")
            add("cloudsimplus.version=${cloudSimPlus.version}")
            assets.forEach { add("asset=$it") }
        }.joinToString(System.lineSeparator()) + System.lineSeparator()
}

object ReleaseManifestSupport {
    const val CURRENT_FORMAT = 2

    fun parse(content: String): ReleaseManifest {
        val entries =
            content
                .lineSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .map { line ->
                    val separator = line.indexOf('=')
                    if (separator <= 0) throw GradleException("Invalid release manifest line: $line")
                    line.substring(0, separator) to line.substring(separator + 1)
                }.toList()
        val singleValues = entries.filterNot { it.first == "asset" }.toMap()
        val format = singleValues["format"]?.toIntOrNull() ?: throw GradleException("Missing release manifest format")
        val metadata =
            CloudSimPlusMetadata(
                ref = singleValues["cloudsimplus.ref"].orEmpty(),
                commit = singleValues["cloudsimplus.commit"].orEmpty(),
                version = singleValues["cloudsimplus.version"].orEmpty(),
            )
        CloudSimPlusLockSupport.validate(metadata)
        return ReleaseManifest(format, metadata, entries.filter { it.first == "asset" }.map { it.second })
    }

    fun validate(
        manifest: ReleaseManifest,
        expectedCloudSimPlus: CloudSimPlusMetadata?,
        expectedAssets: List<String>,
    ) {
        if (manifest.format != CURRENT_FORMAT) {
            throw GradleException("Unsupported release manifest format: ${manifest.format}")
        }
        if (manifest.assets != expectedAssets) {
            throw GradleException("Release manifest drift: expected $expectedAssets but found ${manifest.assets}")
        }
        if (expectedCloudSimPlus != null && manifest.cloudSimPlus != expectedCloudSimPlus) {
            throw GradleException(
                "Release manifest CloudSim Plus metadata drift: expected $expectedCloudSimPlus but found ${manifest.cloudSimPlus}",
            )
        }
    }
}
