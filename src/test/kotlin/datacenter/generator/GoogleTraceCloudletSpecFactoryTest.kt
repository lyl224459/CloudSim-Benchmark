package datacenter.generator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GoogleTraceCloudletSpecFactoryTest {
    @Test
    fun `missing resource fields use default metadata fallbacks`() {
        val spec =
            GoogleTraceCloudletSpecFactory.create(
                record {
                    userName = null
                    cpuRequest = null
                    memoryRequest = null
                    diskSpaceRequest = null
                },
            )

        assertThat(spec.cloudlet.length).isEqualTo(50_000L)
        assertThat(spec.traceMetadata?.tenantKey).isNull()
        assertThat(spec.traceMetadata?.tenantId).isNull()
        assertThat(spec.traceMetadata?.requestedCpu).isNull()
        assertThat(spec.traceMetadata?.requestedRam).isNull()
        assertThat(spec.traceMetadata?.requestedBw).isNull()
        assertThat(spec.traceMetadata?.requestedIo).isNull()
        assertThat(spec.traceMetadata?.dataRegion).isNull()
        assertThat(spec.traceMetadata?.inputDataSize).isEqualTo(1.0)
        assertThat(spec.traceMetadata?.imageSize).isEqualTo(1.0)
        assertThat(spec.traceMetadata?.retryHint).isZero()
    }

    @Test
    fun `negative memory and disk requests are clamped for data and image metadata`() {
        val spec =
            GoogleTraceCloudletSpecFactory.create(
                record {
                    cpuRequest = 0.25
                    memoryRequest = -2.0
                    diskSpaceRequest = -4.0
                },
            )

        assertThat(spec.traceMetadata?.inputDataSize).isZero()
        assertThat(spec.traceMetadata?.imageSize).isZero()
        assertThat(spec.traceMetadata?.requestedBw).isEqualTo(250.0)
        assertThat(spec.traceMetadata?.requestedIo).isEqualTo(-4.0)
    }

    @Test
    fun `tenant region image and retry metadata are deterministic`() {
        val first =
            GoogleTraceCloudletSpecFactory.create(
                record {
                    jobId = 31L
                    eventType = GOOGLE_TRACE_EVICT_EVENT
                    userName = "tenant-alpha"
                    priority = 3
                    cpuRequest = 0.75
                    memoryRequest = 2.0
                    diskSpaceRequest = 5.0
                },
            )
        val second =
            GoogleTraceCloudletSpecFactory.create(
                record {
                    jobId = 31L
                    eventType = GOOGLE_TRACE_EVICT_EVENT
                    userName = "tenant-alpha"
                    priority = 3
                    cpuRequest = 0.75
                    memoryRequest = 2.0
                    diskSpaceRequest = 5.0
                },
            )

        assertThat(first.cloudlet.length).isEqualTo(97_500L)
        assertThat(first.traceMetadata?.tenantKey).isEqualTo("tenant-alpha")
        assertThat(first.traceMetadata?.tenantId).isEqualTo(second.traceMetadata?.tenantId)
        assertThat(first.traceMetadata?.dataRegion).isBetween(0, 2)
        assertThat(first.traceMetadata?.imageId).isEqualTo("trace-image-15")
        assertThat(first.traceMetadata?.imageSize).isEqualTo(2048.0)
        assertThat(first.traceMetadata?.inputDataSize).isEqualTo(5.0)
        assertThat(first.traceMetadata?.retryHint).isEqualTo(1)
    }

    @Test
    fun `non retry trace events keep default retry hint`() {
        val spec =
            GoogleTraceCloudletSpecFactory.create(
                record { eventType = GOOGLE_TRACE_SCHEDULE_EVENT },
            )

        assertThat(spec.traceMetadata?.retryHint).isZero()
    }

    @Test
    fun `trace metadata includes normalized arrival timestamp and expected duration`() {
        val spec =
            GoogleTraceCloudletSpecFactory.create(
                record {
                    cpuRequest = 0.5
                    priority = 2
                },
                arrivalTimestamp = 12.5,
            )

        assertThat(spec.traceMetadata?.arrivalTimestamp).isEqualTo(12.5)
        assertThat(spec.traceMetadata?.expectedDuration).isEqualTo(spec.cloudlet.length / 1000.0)
    }

    private fun record(configure: TraceRecordFixture.() -> Unit = {}): GoogleTraceRecord {
        val fixture = TraceRecordFixture().apply(configure)
        return GoogleTraceRecord(
            timestamp = fixture.timestamp,
            jobId = fixture.jobId,
            taskIndex = fixture.taskIndex,
            machineId = fixture.machineId,
            eventType = fixture.eventType,
            userName = fixture.userName,
            schedulingClass = fixture.schedulingClass,
            priority = fixture.priority,
            cpuRequest = fixture.cpuRequest,
            memoryRequest = fixture.memoryRequest,
            diskSpaceRequest = fixture.diskSpaceRequest,
            differentMachinesRestriction = fixture.differentMachinesRestriction,
        )
    }

    private class TraceRecordFixture {
        var timestamp: Long = 1000L
        var jobId: Long = 7L
        var taskIndex: Int = 0
        var machineId: Long? = 3L
        var eventType: Int = GOOGLE_TRACE_SCHEDULE_EVENT
        var userName: String? = "tenant-a"
        var schedulingClass: Int = 1
        var priority: Int = 0
        var cpuRequest: Double? = 0.5
        var memoryRequest: Double? = 1.0
        var diskSpaceRequest: Double? = 1.0
        var differentMachinesRestriction: Boolean = false
    }
}
