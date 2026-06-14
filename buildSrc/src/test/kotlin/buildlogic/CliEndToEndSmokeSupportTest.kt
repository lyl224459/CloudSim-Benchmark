package buildlogic

import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CliEndToEndSmokeSupportTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `scenarios cover packaged cli production entry points`() {
        val scenarios = CliEndToEndSmokeSupport.scenarios("example.toml", tempDir)

        assertEquals(
            listOf("help", "list-batch", "config-validate", "batch-dry-run", "realtime-dry-run"),
            scenarios.map(CliSmokeScenario::name),
        )
        assertTrue(scenarios.last().arguments.containsAll(listOf("--profile", "realtime_smoke", "--dry-run")))
    }

    @Test
    fun `result validation rejects exit failures and missing output`() {
        val scenario = CliSmokeScenario("help", listOf("--help"), "expected")

        CliEndToEndSmokeSupport.verifyResult(scenario, 0, "expected text")
        assertFailsWith<GradleException> {
            CliEndToEndSmokeSupport.verifyResult(scenario, 2, "failed")
        }
        assertFailsWith<GradleException> {
            CliEndToEndSmokeSupport.verifyResult(scenario, 0, "wrong")
        }
    }
}
