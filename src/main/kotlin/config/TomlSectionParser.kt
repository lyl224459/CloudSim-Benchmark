package config

/**
 * Small TOML section reader for dynamic maps such as [profiles.NAME].
 *
 * ktoml handles fixed-schema data classes well, but dynamic named sections are
 * easier to consume as section paths plus raw scalar values. This parser only
 * implements the subset used by project configs: section headers and
 * single-line key/value assignments.
 */
object TomlSectionParser {
    data class Section(
        val path: List<String>,
        val values: Map<String, String>
    ) {
        val name: String get() = path.joinToString(".")
    }

    fun parse(content: String): List<Section> {
        val sections = mutableListOf<Section>()
        var currentPath: List<String>? = null
        var currentValues = linkedMapOf<String, String>()

        fun flush() {
            val path = currentPath ?: return
            sections.add(Section(path, currentValues.toMap()))
            currentValues = linkedMapOf()
        }

        for (line in content.lineSequence()) {
            val trimmed = stripComment(line).trim()
            if (trimmed.isBlank()) continue

            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                flush()
                currentPath = trimmed.removePrefix("[").removeSuffix("]")
                    .split(".")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                continue
            }

            if (currentPath != null && "=" in trimmed) {
                val key = trimmed.substringBefore("=").trim()
                val value = trimmed.substringAfter("=").trim()
                if (key.isNotEmpty()) {
                    currentValues[key] = value
                }
            }
        }
        flush()

        return sections
    }

    fun unquote(value: String): String =
        value.trim().trim('"', '\'')

    fun stringList(value: String): List<String> {
        val raw = value.trim()
        val body = raw.removePrefix("[").removeSuffix("]").trim()
        if (body.isBlank()) return emptyList()
        return splitArray(body).map { unquote(it.trim()) }.filter { it.isNotEmpty() }
    }

    fun intList(value: String): List<Int> =
        stringList(value).map { item ->
            item.toIntOrNull()
                ?: throw IllegalArgumentException("数组值必须是整数: $item")
        }

    fun doubleList(value: String): List<Double> =
        stringList(value).map { item ->
            item.toDoubleOrNull()
                ?: throw IllegalArgumentException("数组值必须是数字: $item")
        }

    private fun splitArray(body: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaped = false

        for (char in body) {
            when {
                escaped -> {
                    current.append(char)
                    escaped = false
                }
                char == '\\' && quote != null -> {
                    current.append(char)
                    escaped = true
                }
                quote != null && char == quote -> {
                    current.append(char)
                    quote = null
                }
                quote == null && (char == '"' || char == '\'') -> {
                    current.append(char)
                    quote = char
                }
                quote == null && char == ',' -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString())
        return result
    }

    private fun stripComment(line: String): String {
        var quote: Char? = null
        var escaped = false
        val result = StringBuilder()

        for (char in line) {
            when {
                escaped -> {
                    result.append(char)
                    escaped = false
                }
                char == '\\' && quote != null -> {
                    result.append(char)
                    escaped = true
                }
                quote != null && char == quote -> {
                    result.append(char)
                    quote = null
                }
                quote == null && (char == '"' || char == '\'') -> {
                    result.append(char)
                    quote = char
                }
                quote == null && char == '#' -> return result.toString()
                else -> result.append(char)
            }
        }

        return result.toString()
    }
}
