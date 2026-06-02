package util

import datacenter.BatchRunStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CsvRowWriterTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `formats null numbers and enums consistently`() {
        val line = CsvRowWriter().line(listOf(null, 1.23456789, 2.5f, BatchRunStatus.SUCCESS))

        assertThat(line).isEqualTo(",1.234568,2.500000,SUCCESS")
    }

    @Test
    fun `escapes delimiter quotes and newlines`() {
        val line = CsvRowWriter().line(listOf("a,b", "x\"y", "a\nb"))

        assertThat(line).isEqualTo("\"a,b\",\"x\"\"y\",\"a\nb\"")
    }

    @Test
    fun `writes schema checked table`() {
        val file = File(tempDir, "table.csv")
        val schema = CsvTableSchema(listOf("A", "B"))

        CsvRowWriter().writeTable(file, schema, listOf(listOf("left", null)))

        assertThat(file.readLines()).containsExactly("A,B", "left,")
    }

    @Test
    fun `rejects rows that do not match schema width`() {
        val schema = CsvTableSchema(listOf("A", "B"))

        assertThatThrownBy { schema.validate(listOf("only one")) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("CSV row has 1 cells")
    }
}
