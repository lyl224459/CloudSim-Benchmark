package broker

import config.TenantSchedulingPolicy
import org.assertj.core.api.Assertions.assertThat
import org.cloudsimplus.cloudlets.Cloudlet
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import scheduler.RealtimeTaskLifecycle
import scheduler.RealtimeTaskRecord
import scheduler.TenantId

class RealtimeTenantMetricsTest {
    @Test
    fun `aggregates are empty for empty tenant inputs`() {
        val aggregates = RealtimeTenantAggregates.build(emptyList(), emptyList(), penaltyWeight = 2.0)

        assertThat(aggregates.completedByTenant).isEmpty()
        assertThat(aggregates.activeByTenant).isEmpty()
        assertThat(aggregates.resourceByTenant).isEmpty()
        assertThat(aggregates.budgetByTenant).isEmpty()
        assertThat(aggregates.slaPenaltyByTenant).isEmpty()
    }

    @Test
    fun `completed tenant counts ignore failed cloudlets for success only metric`() {
        val success = cloudlet(id = 1L, status = Cloudlet.Status.SUCCESS)
        val failed = cloudlet(id = 2L, status = Cloudlet.Status.FAILED)
        val records =
            mapOf(
                1L to record(1L) { tenantId = TenantId(0) },
                2L to record(2L) { tenantId = TenantId(1) },
            )

        val completed = RealtimeTenantAggregates.successfulCompletedByTenant(listOf(success, failed), records)

        assertThat(completed).containsExactlyEntriesOf(mapOf(0 to 1))
    }

    @Test
    fun `retry success rates are calculated per tenant and ignore first attempts`() {
        val records =
            listOf(
                record(1L) {
                    tenantId = TenantId(0)
                    attempt = 1
                },
                record(2L) {
                    tenantId = TenantId(0)
                    attempt = 2
                },
                record(3L) {
                    tenantId = TenantId(1)
                    attempt = 1
                },
                record(4L) {
                    tenantId = TenantId(1)
                    attempt = 0
                },
            )

        val rates = RealtimeTenantAggregates.retrySuccessRates(records, successById = setOf(1L))

        assertThat(rates).containsExactly(0.5, 0.0)
    }

    @Test
    fun `fairness pressure handles budget limit boundaries`() {
        val activity = TenantActivityPressure(activeCount = 2, completedCount = 3, weight = 2.0)
        val withoutLimit =
            TenantFairnessPressureInput(
                activity = activity,
                resource = TenantResourcePressure(0.25, budgetUsed = 10.0, budgetLimit = null, slaPenalty = 2.0),
            )
        val withZeroLimit = withoutLimit.copy(resource = withoutLimit.resource.copy(budgetLimit = 0.0))
        val withLimit = withoutLimit.copy(resource = withoutLimit.resource.copy(budgetLimit = 20.0))

        assertThat(RealtimeTenantFairness.pressure(TenantSchedulingPolicy.WEIGHTED_FAIR, 1.0, withoutLimit))
            .isEqualTo(2.5)
        val policy = TenantSchedulingPolicy.DOMINANT_RESOURCE_FAIRNESS
        assertThat(RealtimeTenantFairness.pressure(policy, 1.0, withoutLimit))
            .isEqualTo(2.25)
        assertThat(RealtimeTenantFairness.pressure(policy, 1.0, withZeroLimit))
            .isEqualTo(2.25)
        assertThat(RealtimeTenantFairness.pressure(policy, 1.0, withLimit))
            .isEqualTo(2.75)
    }

    @Test
    fun `resource and fairness helpers fall back for empty or zero inputs`() {
        assertThat(RealtimeTenantFairness.dominantResourceShare(emptyList())).isZero()
        assertThat(RealtimeTenantFairness.dominantResourceShare(listOf(record(1L), record(2L)))).isEqualTo(2.0)
        assertThat(RealtimeTenantFairness.estimatedTaskCost(record(3L))).isEqualTo(1.0)
        assertThat(RealtimeTenantFairness.jainsIndex(emptyList())).isEqualTo(1.0)
        assertThat(RealtimeTenantFairness.jainsIndex(listOf(0.0, 0.0))).isEqualTo(1.0)
        assertThat(RealtimeTenantFairness.jainsIndex(listOf(1.0, 3.0))).isLessThan(1.0)
    }

    private fun record(
        cloudletId: Long,
        configure: TaskRecordFixture.() -> Unit = {},
    ): RealtimeTaskRecord {
        val fixture = TaskRecordFixture().apply(configure)
        return RealtimeTaskRecord(
            cloudletId = cloudletId,
            originalArrivalTime = 0.0,
            attempt = fixture.attempt,
            lifecycle = fixture.lifecycle,
            tenantId = fixture.tenantId,
            requestedCpu = fixture.requestedCpu,
            requestedRam = fixture.requestedRam,
            requestedBw = fixture.requestedBw,
            requestedIo = fixture.requestedIo,
        )
    }

    private class TaskRecordFixture {
        var tenantId: TenantId = TenantId(0)
        var attempt: Int = 0
        var lifecycle: RealtimeTaskLifecycle = RealtimeTaskLifecycle.RUNNING
        var requestedCpu: Double? = null
        var requestedRam: Double? = null
        var requestedBw: Double? = null
        var requestedIo: Double? = null
    }

    private fun cloudlet(
        id: Long,
        status: Cloudlet.Status,
    ): Cloudlet =
        mock {
            on { this.id } doReturn id
            on { this.status } doReturn status
        }
}
