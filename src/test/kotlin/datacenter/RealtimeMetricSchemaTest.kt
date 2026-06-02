package datacenter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import util.CsvRowWriter
import java.io.File

class RealtimeMetricSchemaTest {
    @Test
    fun `summary and trial headers come from one schema`() {
        assertThat(realtimeMetricHeaders).containsExactlyElementsOf(RealtimeMetricSchema.metricHeaders)
        assertThat(realtimeSummaryCsvHeaders).containsExactlyElementsOf(RealtimeMetricSchema.summaryHeaders)
        assertThat(realtimeTrialCsvHeaders).containsExactlyElementsOf(RealtimeMetricSchema.trialHeaders)
        assertThat(RealtimeMetricSchema.cloudletCountSummaryHeaders)
            .containsExactlyElementsOf(RealtimeMetricSchema.summaryHeaders(prefixHeaders = listOf("CloudletCount")))
    }

    @Test
    fun `summary rows match generated headers`() {
        val summary =
            RealtimeRunSummary(
                algorithmName = "MinLoad",
                status = RealtimeRunStatus.FAILED,
                average = null,
                statistics = null,
                outcomes =
                    listOf(
                        RealtimeRunOutcome.Failed(
                            algorithmName = "MinLoad",
                            run = 1,
                            errorType = "IllegalStateException",
                            errorMessage = "boom",
                        ),
                    ),
            )

        val row = summary.toCsvRow()
        val cloudletCountRow = summary.toCloudletCountCsvRow(cloudletCount = 100)

        assertThat(row).hasSize(realtimeSummaryCsvHeaders.size)
        assertThat(cloudletCountRow).hasSize(RealtimeMetricSchema.cloudletCountSummaryHeaders.size)
        assertThat(row[realtimeSummaryCsvHeaders.indexOf("Status")]).isEqualTo(RealtimeRunStatus.FAILED)
        assertThat(row.drop(RealtimeMetricSchema.summaryMetadataHeaders.size)).allSatisfy { value ->
            assertThat(value).isNull()
        }
        assertThat(CsvRowWriter().line(row)).doesNotContain("null")
    }

    @Test
    fun `metric documentation is generated from schema`() {
        val doc = File("docs/realtime-metrics.md").readText().normalizeLineEndings()

        assertThat(doc).isEqualTo(RealtimeMetricDocumentationGenerator.render().normalizeLineEndings())
    }

    private fun String.normalizeLineEndings(): String = replace("\r\n", "\n")
}
