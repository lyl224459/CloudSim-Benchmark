package buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CloudSimPlusVersioningTest {
    @Test
    fun `latest release tag accepts v-prefixed and plain semver tags`() {
        val refs =
            """
            abc	refs/tags/v8.5.7
            def	refs/tags/8.5.8
            ghi	refs/tags/v8.5.6
            """.trimIndent()

        assertEquals("8.5.8", CloudSimPlusVersioning.parseLatestReleaseTag(refs))
    }

    @Test
    fun `latest release tag skips prerelease and non semver tags`() {
        val refs =
            """
            abc	refs/tags/v8.5.9-RC1
            def	refs/tags/latest
            ghi	refs/tags/v8.5
            """.trimIndent()

        assertNull(CloudSimPlusVersioning.parseLatestReleaseTag(refs))
    }

    @Test
    fun `local tag parser returns version tuple for semver only`() {
        assertEquals(listOf(8, 5, 7) to "v8.5.7", CloudSimPlusVersioning.tagVersion("v8.5.7"))
        assertEquals(listOf(8, 5, 7) to "8.5.7", CloudSimPlusVersioning.tagVersion("8.5.7"))
        assertNull(CloudSimPlusVersioning.tagVersion("v8.5.7-RC1"))
    }
}
