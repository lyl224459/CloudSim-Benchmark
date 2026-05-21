package broker

import config.RealtimeSchedulingConfig
import org.assertj.core.api.Assertions.assertThat
import org.cloudsimplus.cloudlets.CloudletSimple
import org.junit.jupiter.api.Test
import scheduler.CloudletId
import scheduler.RealtimeTaskLifecycle
import scheduler.RealtimeTaskRecord
import scheduler.TenantId

class RealtimeTenantControllerTest {

    @Test
    fun `quota decision rejects when tenant active count reaches quota`() {
        val controller = RealtimeTenantController(
            RealtimeSchedulingConfig(
                multiTenantEnabled = true,
                tenantCount = 1,
                tenantQuota = listOf(1)
            )
        )
        val incoming = record(2)
        val active = listOf(record(1, lifecycle = RealtimeTaskLifecycle.RUNNING))

        val decision = controller.decide(incoming, active)

        assertThat(decision).isInstanceOf(TenantAdmissionDecision.Rejected::class.java)
    }

    @Test
    fun `future arrivals do not consume tenant quota before admission`() {
        val controller = RealtimeTenantController(
            RealtimeSchedulingConfig(
                multiTenantEnabled = true,
                tenantCount = 1,
                tenantQuota = listOf(1)
            )
        )

        val decision = controller.decide(record(2), listOf(record(1, lifecycle = RealtimeTaskLifecycle.ARRIVED)))

        assertThat(decision).isEqualTo(TenantAdmissionDecision.Accepted)
    }

    @Test
    fun `fairness index returns one for balanced tenants`() {
        val controller = RealtimeTenantController(
            RealtimeSchedulingConfig(
                multiTenantEnabled = true,
                tenantCount = 2,
                tenantWeights = listOf(1.0, 1.0)
            )
        )
        val cloudlets = listOf(CloudletSimple(1, 1), CloudletSimple(2, 1))
        cloudlets.forEach { it.setStatus(org.cloudsimplus.cloudlets.Cloudlet.Status.SUCCESS) }
        val records = listOf(record(1, TenantId(0)), record(2, TenantId(1)))

        val fairness = controller.fairnessIndex(cloudlets, records)

        assertThat(fairness).isEqualTo(1.0)
    }

    @Test
    fun `tenant assignment stays deterministic for same cloudlet id`() {
        val controller = RealtimeTenantController(
            RealtimeSchedulingConfig(
                multiTenantEnabled = true,
                tenantCount = 3
            )
        )
        val sampler: (CloudletId, Int, Int) -> Double = { cloudletId, _, _ ->
            ((cloudletId.value % 3).toDouble() + 0.1) / 3.0
        }

        assertThat(controller.tenantFor(CloudletId(7), sampler)).isEqualTo(controller.tenantFor(CloudletId(7), sampler))
    }

    private fun record(
        cloudletId: Long,
        tenantId: TenantId = TenantId(0),
        lifecycle: RealtimeTaskLifecycle = RealtimeTaskLifecycle.ARRIVED
    ): RealtimeTaskRecord =
        RealtimeTaskRecord(
            cloudletId = cloudletId,
            originalArrivalTime = 0.0,
            tenantId = tenantId,
            lifecycle = lifecycle
        )
}
