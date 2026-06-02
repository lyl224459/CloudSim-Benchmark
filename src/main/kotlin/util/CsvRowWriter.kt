package util

import java.io.BufferedWriter
import java.io.File
import java.util.Locale

data class CsvTableSchema(
    val headers: List<String>,
) {
    init {
        require(headers.isNotEmpty()) { "CSV headers must not be empty" }
        require(headers.distinct().size == headers.size) { "CSV headers must be unique: $headers" }
    }

    fun validate(row: List<Any?>): List<Any?> {
        require(row.size == headers.size) {
            "CSV row has ${row.size} cells but schema has ${headers.size} headers: ${headers.joinToString()}"
        }
        return row
    }

    fun rowFrom(valuesByHeader: Map<String, Any?>): List<Any?> = headers.map { header -> valuesByHeader[header] }
}

object CsvCellFormatter {
    fun format(value: Any?): String =
        when (value) {
            null -> ""
            is Double -> value.formatFloatingPoint()
            is Float -> value.toDouble().formatFloatingPoint()
            is Enum<*> -> value.name
            else -> value.toString()
        }

    private fun Double.formatFloatingPoint(): String = if (isFinite()) String.format(Locale.US, "%.6f", this) else toString()
}

class CsvRowWriter(
    private val delimiter: String = ",",
) {
    fun line(values: Iterable<Any?>): String = values.joinToString(delimiter) { value -> escape(CsvCellFormatter.format(value)) }

    fun writeHeader(
        writer: BufferedWriter,
        schema: CsvTableSchema,
    ) {
        writer.write(line(schema.headers))
        writer.newLine()
    }

    fun writeRow(
        writer: BufferedWriter,
        schema: CsvTableSchema,
        row: List<Any?>,
    ) {
        writer.write(line(schema.validate(row)))
        writer.newLine()
    }

    fun writeTable(
        file: File,
        schema: CsvTableSchema,
        rows: Iterable<List<Any?>>,
    ) {
        file.parentFile?.mkdirs()
        file.bufferedWriter().use { writer ->
            writeHeader(writer, schema)
            rows.forEach { row -> writeRow(writer, schema, row) }
        }
    }

    private fun escape(value: String): String {
        val needsQuotes = value.contains(delimiter) || value.contains('"') || value.contains('\n') || value.contains('\r')
        if (!needsQuotes) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}
