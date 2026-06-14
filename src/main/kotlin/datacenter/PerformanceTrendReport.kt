package datacenter

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

private const val ZERO_SCORE_EPSILON = 1e-12
private const val PERCENT_MULTIPLIER = 100.0

data class PerformanceTrendEntry(
    val key: String,
    val label: String,
    val mode: String,
    val score: Double,
    val scoreUnit: String,
    val allocationBytesPerOp: Double?,
    val jvm: String,
    val jdkVersion: String,
    val jvmArgs: List<String>,
)

object PerformanceTrendReportGenerator {
    fun render(
        currentJson: String,
        baselineJson: String? = null,
        generatedAt: Instant = Instant.now(),
    ): String {
        val currentEntries = parse(currentJson)
        val baselineEntries = baselineJson?.let(::parse).orEmpty().associateBy { it.key }
        val metadata = currentEntries.firstOrNull()

        return buildString {
            appendLine("# Performance Trend Report")
            appendLine()
            appendLine("- Generated: `$generatedAt`")
            appendLine("- JVM: `${metadata?.jvm.orEmpty()}`")
            appendLine("- JDK: `${metadata?.jdkVersion.orEmpty()}`")
            appendLine("- JVM args: `${metadata?.jvmArgs?.joinToString(" ").orEmpty()}`")
            appendLine("- GC profiler: `gc`")
            appendLine()
            appendLine("| Benchmark | Mode | Score | Unit | Allocation/op | Delta vs baseline |")
            appendLine("| :--- | :--- | ---: | :--- | ---: | ---: |")
            currentEntries.forEach { entry ->
                val baseline = baselineEntries[entry.key]
                appendLine(
                    "| ${entry.label} | ${entry.mode} | ${entry.score.format()} | ${entry.scoreUnit} | " +
                        "${entry.allocationBytesPerOp?.format() ?: "n/a"} | ${delta(entry, baseline)} |",
                )
            }
        }
    }

    fun parse(input: String): List<PerformanceTrendEntry> {
        val root = Json.parseToJsonElement(input)
        require(root is JsonArray) { "JMH result must be a JSON array" }
        return root
            .map { element ->
                val result = element.jsonObject
                val benchmark = result.string("benchmark")
                val params = result["params"]?.jsonObject.orEmpty()
                val primaryMetric = result.objectValue("primaryMetric")
                val secondaryMetrics = result["secondaryMetrics"]?.jsonObject.orEmpty()
                val label = labelFor(benchmark, params)
                val key = keyFor(benchmark, params)

                PerformanceTrendEntry(
                    key = key,
                    label = label,
                    mode = result.string("mode"),
                    score = primaryMetric.double("score"),
                    scoreUnit = primaryMetric.string("scoreUnit"),
                    allocationBytesPerOp = allocationBytesPerOp(secondaryMetrics),
                    jvm = result.string("vmName", result.string("jvm", "")),
                    jdkVersion = result.string("jdkVersion", ""),
                    jvmArgs = result["jvmArgs"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
                )
            }.sortedWith(compareBy({ it.label }, { it.mode }))
    }

    private fun labelFor(
        benchmark: String,
        params: JsonObject,
    ): String {
        val shortName = benchmark.substringAfterLast('.')
        if (params.isEmpty()) return shortName
        val renderedParams =
            params.entries
                .sortedBy { it.key }
                .joinToString(", ") { (key, value) -> "$key=${value.jsonPrimitive.content}" }
        return "$shortName [$renderedParams]"
    }

    private fun keyFor(
        benchmark: String,
        params: JsonObject,
    ): String =
        buildString {
            append(benchmark)
            params.entries.sortedBy { it.key }.forEach { (key, value) ->
                append('|').append(key).append('=').append(value.jsonPrimitive.content)
            }
        }

    private fun allocationBytesPerOp(metrics: JsonObject): Double? {
        val allocationMetric =
            metrics.entries
                .firstOrNull { (name, _) ->
                    name.contains("alloc.rate.norm", ignoreCase = true)
                }?.value
        return allocationMetric?.jsonObject?.double("score")
    }

    private fun delta(
        entry: PerformanceTrendEntry,
        baseline: PerformanceTrendEntry?,
    ): String {
        if (baseline == null || abs(baseline.score) < ZERO_SCORE_EPSILON) return "n/a"
        val percent = (entry.score - baseline.score) / baseline.score * PERCENT_MULTIPLIER
        return "${percent.format()}%"
    }
}

fun main(args: Array<String>) {
    val options = args.toPerformanceTrendOptions()
    val input = File(options.required("input")).readText()
    val baseline = options["baseline"]?.let { File(it).readText() }
    val output = File(options.required("output"))
    output.parentFile?.mkdirs()
    output.writeText(PerformanceTrendReportGenerator.render(input, baseline))
    println("Performance trend report written to ${output.absolutePath}")
}

private fun Array<String>.toPerformanceTrendOptions(): Map<String, String> =
    asList().chunked(2).associate { chunk ->
        require(chunk.size == 2 && chunk[0].startsWith("--")) {
            "Invalid performance trend argument list: ${joinToString(" ")}"
        }
        chunk[0].removePrefix("--") to chunk[1]
    }

private fun Map<String, String>.required(name: String): String =
    this[name] ?: throw IllegalArgumentException("Missing required argument --$name")

private fun JsonObject.objectValue(name: String): JsonObject =
    this[name]?.jsonObject ?: throw IllegalArgumentException("Missing JMH object field: $name")

private fun JsonObject.string(
    name: String,
    default: String? = null,
): String =
    this[name]?.jsonPrimitive?.content
        ?: default
        ?: throw IllegalArgumentException("Missing JMH string field: $name")

private fun JsonObject.double(name: String): Double =
    this[name]?.jsonPrimitive?.doubleOrNull ?: throw IllegalArgumentException("Missing JMH numeric field: $name")

private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())

private fun Double.format(): String = String.format(Locale.US, "%.3f", this)
