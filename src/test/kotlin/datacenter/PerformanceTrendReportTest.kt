package datacenter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

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

    private fun jmhSample(
        score: Double,
        allocation: Double,
        benchmark: String,
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
            "vmName": "OpenJDK 64-Bit Server VM",
            "jdkVersion": "23",
            "jvmArgs": ["-Xms1g", "-Xmx1g", "-XX:+UseG1GC"],
            "primaryMetric": {
              "score": $score,
              "scoreUnit": "ms/op"
            },
            "secondaryMetrics": {
              "gc.alloc.rate.norm": {
                "score": $allocation,
                "scoreUnit": "B/op"
              }
            }
          }
        ]
        """.trimIndent()
}
