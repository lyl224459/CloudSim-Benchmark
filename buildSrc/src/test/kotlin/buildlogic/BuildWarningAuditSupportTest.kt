package buildlogic

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildWarningAuditSupportTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `known warnings pass from their confirmed sources`() {
        writeCleanLogs()
        tempDir.resolve(BuildWarningSource.DETEKT.logFileName).writeText(DETEKT_WARNING)
        tempDir.resolve(BuildWarningSource.KTLINT.logFileName).writeText(KTLINT_UNSAFE_WARNING)

        val result = BuildWarningAuditSupport.audit(tempDir)

        assertTrue(result.violations.isEmpty())
        assertEquals(1, result.allowedCounts["detekt-plugin-configuration"])
        assertEquals(4, result.allowedCounts["ktlint-kotlin-compiler-unsafe"])
    }

    @Test
    fun `ktlint unsafe warning from another source fails`() {
        writeCleanLogs()
        tempDir.resolve(BuildWarningSource.COMPILE_KOTLIN.logFileName).writeText(KTLINT_UNSAFE_WARNING)

        val result = BuildWarningAuditSupport.audit(tempDir)

        assertTrue(result.violations.any { "COMPILE_KOTLIN" in it && "Unsafe" in it })
    }

    @Test
    fun `unknown warning families fail`() {
        writeCleanLogs()
        tempDir.resolve(BuildWarningSource.CLOUDSIM_PLUS_MAVEN.logFileName).writeText(
            """
            [WARNING] Jansi native access warning
            Kotlin does not yet support 25 JDK target
            Inconsistent JVM-target compatibility
            WARNING: Guava Unsafe fallback
            """.trimIndent(),
        )

        val result = BuildWarningAuditSupport.audit(tempDir)

        assertEquals(4, result.violations.size)
    }

    @Test
    fun `known warning signature with appended text fails`() {
        writeCleanLogs()
        tempDir.resolve(BuildWarningSource.DETEKT.logFileName).writeText("$DETEKT_WARNING unexpected suffix")
        tempDir.resolve(BuildWarningSource.KTLINT.logFileName).writeText(
            "WARNING: A terminally deprecated method in sun.misc.Unsafe has been called unexpectedly",
        )

        val result = BuildWarningAuditSupport.audit(tempDir)

        assertEquals(2, result.violations.size)
    }

    @Test
    fun `missing and empty logs fail and report includes removal conditions`() {
        writeCleanLogs()
        tempDir.resolve(BuildWarningSource.COMPILE_KOTLIN.logFileName).delete()
        tempDir.resolve(BuildWarningSource.DETEKT.logFileName).writeText("")

        val result = BuildWarningAuditSupport.audit(tempDir)
        val report = BuildWarningAuditSupport.renderMarkdown(result)

        assertEquals(2, result.violations.size)
        assertContains(report, "log is missing")
        assertContains(report, "log is empty")
        assertContains(report, "Remove after upgrading")
        assertContains(report, "Remove after ktlint")
    }

    @Test
    fun `clean logs pass`() {
        writeCleanLogs()

        val result = BuildWarningAuditSupport.audit(tempDir)

        assertTrue(result.violations.isEmpty())
        assertTrue(result.allowedCounts.isEmpty())
    }

    private fun writeCleanLogs() {
        BuildWarningSource.entries.forEach { source ->
            tempDir.resolve(source.logFileName).writeText("BUILD SUCCESSFUL for ${source.name}")
        }
    }

    private companion object {
        const val DETEKT_WARNING =
            "The ReportingExtension.file(String) method has been deprecated. This is scheduled to be removed in Gradle 10. " +
                "Please use the getBaseDirectory().file(String) or getBaseDirectory().dir(String) method instead. " +
                "Consult the upgrading guide for further information: " +
                "https://docs.gradle.org/9.5.1/userguide/upgrading_version_9.html#reporting_extension_file"

        val KTLINT_UNSAFE_WARNING =
            """
            WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
            WARNING: sun.misc.Unsafe::objectFieldOffset has been called by org.jetbrains.kotlin.com.intellij.util.containers.Unsafe (file:/home/runner/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-compiler-embeddable/2.1.0/hash/kotlin-compiler-embeddable-2.1.0.jar)
            WARNING: Please consider reporting this to the maintainers of class org.jetbrains.kotlin.com.intellij.util.containers.Unsafe
            WARNING: sun.misc.Unsafe::objectFieldOffset will be removed in a future release
            """.trimIndent()
    }
}
