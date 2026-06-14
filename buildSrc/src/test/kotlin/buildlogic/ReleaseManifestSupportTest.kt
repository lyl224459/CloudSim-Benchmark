package buildlogic

import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ReleaseManifestSupportTest {
    @Test
    fun `round trips metadata and ordered assets`() {
        val expected =
            ReleaseManifest(
                format = ReleaseManifestSupport.CURRENT_FORMAT,
                cloudSimPlus =
                    CloudSimPlusMetadata(
                        ref = "v8.5.7",
                        commit = "f23d4b165402e4976de854ceed5e52bc7b78c520",
                        version = "8.5.7",
                    ),
                assets = listOf("app.jar", "release-manifest.txt"),
            )

        assertEquals(expected, ReleaseManifestSupport.parse(expected.render()))
    }

    @Test
    fun `detects metadata and asset drift`() {
        val manifest =
            ReleaseManifest(
                format = ReleaseManifestSupport.CURRENT_FORMAT,
                cloudSimPlus = metadata("v8.5.7"),
                assets = listOf("app.jar"),
            )

        assertThrows(GradleException::class.java) {
            ReleaseManifestSupport.validate(manifest, metadata("v8.5.6"), listOf("app.jar"))
        }
        assertThrows(GradleException::class.java) {
            ReleaseManifestSupport.validate(manifest, metadata("v8.5.7"), listOf("other.jar"))
        }
    }

    private fun metadata(ref: String): CloudSimPlusMetadata =
        CloudSimPlusMetadata(
            ref = ref,
            commit = "f23d4b165402e4976de854ceed5e52bc7b78c520",
            version = "8.5.7",
        )
}
