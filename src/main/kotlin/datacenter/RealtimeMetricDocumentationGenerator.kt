package datacenter

import java.io.File

object RealtimeMetricDocumentationGenerator {
    fun render(): String =
        buildString {
            appendLine("# Realtime 指标定义")
            appendLine()
            appendLine(
                "实时调度 CSV 的指标列由 `RealtimeMetricSchema` 统一维护。" +
                    "`*_Mean` 表示成功 run 的平均值，`*_StdDev` 表示成功 run 的标准差；" +
                    "失败 run 的指标单元格为空，并通过 `Status`、`ErrorType`、`ErrorMessage` 记录失败信息。",
            )
            appendLine()
            appendLine("| CSV 指标 | 单位 | 趋势 | 定义 |")
            appendLine("| :--- | :--- | :--- | :--- |")
            RealtimeMetricSchema.metrics.forEach { metric ->
                appendLine("| ${metric.csvName} | ${metric.unit} | ${metric.direction.label} | ${metric.description} |")
            }
        }

    fun writeTo(file: File) {
        file.parentFile?.mkdirs()
        file.writeText(render())
    }
}

fun main(args: Array<String>) {
    val output = File(args.firstOrNull() ?: "docs/realtime-metrics.md")
    RealtimeMetricDocumentationGenerator.writeTo(output)
    println("Realtime metric documentation written to ${output.absolutePath}")
}
