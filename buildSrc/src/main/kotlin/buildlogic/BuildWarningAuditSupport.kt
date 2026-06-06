package buildlogic

import java.io.File

internal enum class BuildWarningSource(
    val logFileName: String,
) {
    COMPILE_KOTLIN("compileKotlin.log"),
    DETEKT("detekt.log"),
    KTLINT("ktlintCheck.log"),
    CLOUDSIM_PLUS_MAVEN("buildCloudSimPlusFromSource.log"),
}

internal data class BuildWarningAuditResult(
    val sourceCounts: Map<BuildWarningSource, Int>,
    val allowedCounts: Map<String, Int>,
    val violations: List<String>,
) {
    val warningCount: Int = allowedCounts.values.sum()
}

internal object BuildWarningAuditSupport {
    private const val DETEKT_DEPRECATION =
        "The ReportingExtension.file(String) method has been deprecated. This is scheduled to be removed in Gradle 10. " +
            "Please use the getBaseDirectory().file(String) or getBaseDirectory().dir(String) method instead. " +
            "Consult the upgrading guide for further information: " +
            "https://docs.gradle.org/9.5.1/userguide/upgrading_version_9.html#reporting_extension_file"

    private val warningMarkers =
        listOf(
            "WARNING:",
            "[WARNING]",
            "deprecated",
            "native access",
            "Unsafe",
            "Kotlin does not yet support",
            "Inconsistent JVM-target",
        )

    private val ansiEscape = Regex("""\u001B\[[;\d]*m""")

    private val allowedKtlintUnsafePatterns =
        listOf(
            Regex("""^WARNING: A terminally deprecated method in sun\.misc\.Unsafe has been called$"""),
            Regex(
                """^WARNING: sun\.misc\.Unsafe::objectFieldOffset has been called by """ +
                    """org\.jetbrains\.kotlin\.com\.intellij\.util\.containers\.Unsafe """ +
                    """\(file:.*/org\.jetbrains\.kotlin/kotlin-compiler-embeddable/2\.1\.0/[^/]+/""" +
                    """kotlin-compiler-embeddable-2\.1\.0\.jar\)$""",
            ),
            Regex(
                """^WARNING: Please consider reporting this to the maintainers of class """ +
                    """org\.jetbrains\.kotlin\.com\.intellij\.util\.containers\.Unsafe$""",
            ),
            Regex("""^WARNING: sun\.misc\.Unsafe::objectFieldOffset will be removed in a future release$"""),
        )

    fun audit(logDirectory: File): BuildWarningAuditResult {
        val violations = mutableListOf<String>()
        val sourceCounts = linkedMapOf<BuildWarningSource, Int>()
        val allowedCounts = linkedMapOf<String, Int>()

        BuildWarningSource.entries.forEach { source ->
            val logFile = logDirectory.resolve(source.logFileName)
            val lines = readableLines(logFile, source, violations)
            sourceCounts[source] = lines.size
            lines.forEachIndexed { index, line ->
                classifyLine(source, index + 1, line, allowedCounts, violations)
            }
        }

        return BuildWarningAuditResult(sourceCounts, allowedCounts, violations)
    }

    fun renderMarkdown(result: BuildWarningAuditResult): String =
        buildString {
            appendLine("# Build Warning Audit")
            appendLine()
            appendLine("- Status: ${if (result.violations.isEmpty()) "PASS" else "FAIL"}")
            appendLine("- Allowed warning lines: ${result.warningCount}")
            appendLine()
            appendLine("## Sources")
            appendLine()
            appendLine("| Source | Log lines |")
            appendLine("| --- | ---: |")
            BuildWarningSource.entries.forEach { source ->
                appendLine("| `${source.name}` | ${result.sourceCounts[source] ?: 0} |")
            }
            appendLine()
            appendLine("## Allowed Warnings")
            appendLine()
            appendLine(
                "- `detekt-plugin-configuration`: Gradle 10 deprecation from stable detekt 1.23.8. " +
                    "Remove after upgrading to a stable detekt version without this warning.",
            )
            appendLine(
                "- `ktlint-kotlin-compiler-unsafe`: JDK 25 warning from ktlint's embedded Kotlin compiler. " +
                    "Remove after ktlint no longer invokes this Unsafe API.",
            )
            appendLine()
            if (result.allowedCounts.isEmpty()) {
                appendLine("No allowed warning signatures were observed.")
            } else {
                result.allowedCounts.forEach { (name, count) ->
                    appendLine("- `$name`: $count line(s)")
                }
            }
            appendLine()
            appendLine("## Violations")
            appendLine()
            if (result.violations.isEmpty()) {
                appendLine("None.")
            } else {
                result.violations.forEach { violation -> appendLine("- $violation") }
            }
        }

    private fun readableLines(
        logFile: File,
        source: BuildWarningSource,
        violations: MutableList<String>,
    ): List<String> {
        if (!logFile.isFile) {
            violations += "`${source.name}` log is missing: `${logFile.path}`"
            return emptyList()
        }
        if (logFile.length() == 0L) {
            violations += "`${source.name}` log is empty: `${logFile.path}`"
            return emptyList()
        }
        return logFile.readLines()
    }

    private fun classifyLine(
        source: BuildWarningSource,
        lineNumber: Int,
        line: String,
        allowedCounts: MutableMap<String, Int>,
        violations: MutableList<String>,
    ) {
        val normalizedLine = line.replace(ansiEscape, "")
        if (normalizedLine == DETEKT_DEPRECATION) {
            allowedCounts.increment("detekt-plugin-configuration")
            return
        }
        if (source == BuildWarningSource.KTLINT && isAllowedKtlintUnsafeLine(normalizedLine)) {
            allowedCounts.increment("ktlint-kotlin-compiler-unsafe")
            return
        }
        if (warningMarkers.any { marker -> normalizedLine.contains(marker, ignoreCase = true) }) {
            violations += "`${source.name}` line $lineNumber: `${normalizedLine.trim().replace("`", "'")}`"
        }
    }

    private fun isAllowedKtlintUnsafeLine(line: String): Boolean =
        allowedKtlintUnsafePatterns.any { pattern -> pattern.matches(line) }

    private fun MutableMap<String, Int>.increment(key: String) {
        this[key] = getOrDefault(key, 0) + 1
    }
}
