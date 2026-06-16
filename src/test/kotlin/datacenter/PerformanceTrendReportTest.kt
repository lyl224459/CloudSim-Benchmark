package datacenter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertFailsWith

class PerformanceTrendReportTest {
    @Test
    fun `renders jmh json with allocation and baseline delta`() {
        val current =
            jmhSample(
                score = 12.0,
                allocation = 256.0,
                benchmark = "datacenter.benchmark.CloudSimPerformanceBenchmarks.batchMetaheuristicSchedule",
            )
        val baseline =
            jmhSample(
                score = 10.0,
                allocation = 200.0,
                benchmark = "datacenter.benchmark.CloudSimPerformanceBenchmarks.batchMetaheuristicSchedule",
            )

        val report =
            PerformanceTrendReportGenerator.render(
                currentJson = current,
                baselineJson = baseline,
                generatedAt = Instant.parse("2026-05-26T00:00:00Z"),
            )

        assertThat(report).contains("Performance Trend Report")
        assertThat(report).contains("batchMetaheuristicSchedule [algorithm=PSO, cloudletCount=100]")
        assertThat(report).contains("256.000")
        assertThat(report).contains("20.000%")
        assertThat(report).contains("JVM args")
        assertThat(report).contains("GC profiler")
    }

    @Test
    fun `render handles empty input missing baseline allocation and zero baseline`() {
        val empty = PerformanceTrendReportGenerator.render("[]", generatedAt = Instant.EPOCH)
        val current = jmhSample(score = 12.0, allocation = null, vmNameField = "\"jvm\":\"Fallback JVM\"")
        val missingBaseline =
            jmhSample(score = 8.0, allocation = 10.0, benchmark = "other.Benchmark")
        val zeroBaseline = jmhSample(score = 0.0, allocation = 1.0)

        assertThat(empty).contains("- JVM: ``")
        assertThat(PerformanceTrendReportGenerator.render(current, missingBaseline)).contains("n/a")
        assertThat(PerformanceTrendReportGenerator.render(current, zeroBaseline)).contains("n/a")
        assertThat(PerformanceTrendReportGenerator.render(current)).contains("n/a").contains("Fallback JVM")
    }

    @Test
    fun `parse rejects malformed or incomplete JMH results`() {
        assertFailsWith<IllegalArgumentException> {
            PerformanceTrendReportGenerator.parse("{}")
        }
        assertFailsWith<IllegalArgumentException> {
            PerformanceTrendReportGenerator.parse("""[{"benchmark":"x"}]""")
        }
        assertFailsWith<IllegalArgumentException> {
            PerformanceTrendReportGenerator.parse(
                """[{"benchmark":"x","mode":"avgt","primaryMetric":{"score":"bad","scoreUnit":"ms/op"}}]""",
            )
        }
    }

    private fun jmhSample(
        score: Double,
        allocation: Double? = 256.0,
        benchmark: String = "datacenter.benchmark.CloudSimPerformanceBenchmarks.batchMetaheuristicSchedule",
        vmNameField: String = "\"vmName\":\"OpenJDK 64-Bit Server VM\"",
    ): String =
        """
        [
          {
            "benchmark": "$benchmark",
            "mode": "avgt",
            "params": {
              "algorithm": "PSO",
              "cloudletCount": "100"
            },
            $vmNameField,
            "jdkVersion": "23",
            "jvmArgs": ["-Xms1g", "-Xmx1g", "-XX:+UseG1GC"],
            "primaryMetric": {
              "score": $score,
              "scoreUnit": "ms/op"
            },
            "secondaryMetrics": ${secondaryMetrics(allocation)}
          }
        ]
        """.trimIndent()

    private fun secondaryMetrics(allocation: Double?): String =
        if (allocation == null) {
            "{}"
        } else {
            """{"gc.alloc.rate.norm":{"score":$allocation,"scoreUnit":"B/op"}}"""
        }
}
