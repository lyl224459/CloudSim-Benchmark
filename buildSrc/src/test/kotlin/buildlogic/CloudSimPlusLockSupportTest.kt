package buildlogic

import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CloudSimPlusLockSupportTest {
    @Test
    fun `parses stable lock metadata`() {
        val metadata = CloudSimPlusLockSupport.parse(LOCK_CONTENT)

        assertEquals("v8.5.7", metadata.ref)
        assertEquals(COMMIT, metadata.commit)
        assertEquals("8.5.7", metadata.version)
    }

    @Test
    fun `rejects missing and unknown lock fields`() {
        assertThrows(GradleException::class.java) {
            CloudSimPlusLockSupport.parse("ref=v8.5.7\ncommit=$COMMIT\n")
        }
        assertThrows(GradleException::class.java) {
            CloudSimPlusLockSupport.parse("$LOCK_CONTENT\nextra=value\n")
        }
    }

    @Test
    fun `rejects non release version and invalid commit`() {
        assertThrows(GradleException::class.java) {
            CloudSimPlusLockSupport.parse("ref=v8.5.7\ncommit=abc\nversion=8.5.7\n")
        }
        assertThrows(GradleException::class.java) {
            CloudSimPlusLockSupport.parse("ref=v8.5.7-rc1\ncommit=$COMMIT\nversion=8.5.7-rc1\n")
        }
    }

    @Test
    fun `selects explicit ref then latest release then lock`() {
        assertEquals(
            "custom-ref",
            CloudSimPlusRefSelection.select("custom-ref", autoUpdateEnabled = true, lockedRef = "locked") { "latest" },
        )
        assertEquals(
            "latest",
            CloudSimPlusRefSelection.select("", autoUpdateEnabled = true, lockedRef = "locked") { "latest" },
        )
        assertEquals(
            "locked",
            CloudSimPlusRefSelection.select("", autoUpdateEnabled = false, lockedRef = "locked") { "latest" },
        )
    }

    private companion object {
        const val COMMIT = "f23d4b165402e4976de854ceed5e52bc7b78c520"
        const val LOCK_CONTENT = "ref=v8.5.7\ncommit=$COMMIT\nversion=8.5.7\n"
    }
}
