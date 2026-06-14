package broker

import org.cloudsimplus.cloudlets.Cloudlet
import scheduler.RealtimeTaskLifecycle
import scheduler.RealtimeTaskRecord

internal class RealtimeBrokerLifecycleService(
    private val state: RealtimeBrokerStateBundle,
    private val metadataFactory: RealtimeTaskMetadataFactory,
    private val environment: RealtimeBrokerEnvironment,
) {
    fun createMetadata(cloudlet: Cloudlet): RealtimeTaskRecord =
        metadataFactory.create(
            RealtimeTaskMetadataRequest(
                cloudlet = cloudlet,
                arrivalTime = state.arrival.arrivalTimeOf(cloudlet),
                attempt = state.arrival.attemptOf(cloudlet),
                fastestVmMips = environment.vmList.maxOfOrNull { it.mips },
            ),
        )

    fun updateMetadata(
        cloudlet: Cloudlet,
        transform: (RealtimeTaskRecord) -> RealtimeTaskRecord,
    ) {
        state.lifecycleStore.updateOrPut(createMetadata(cloudlet), transform)
    }

    fun lifecycleOf(cloudlet: Cloudlet): RealtimeTaskLifecycle? {
        val record = state.lifecycleStore.get(cloudlet.id)
        return record?.lifecycle
    }

    fun markArrivedAfterInterruption(cloudlet: Cloudlet) {
        updateMetadata(cloudlet) { it.copy(lifecycle = RealtimeTaskLifecycle.ARRIVED) }
    }

    fun taskRecord(cloudlet: Cloudlet): RealtimeTaskRecord {
        val existing = state.lifecycleStore.get(cloudlet.id)
        return existing ?: createMetadata(cloudlet)
    }

    fun activeTenantRecords(): List<RealtimeTaskRecord> {
        val records = state.lifecycleStore.snapshot()
        return records.filter(RealtimeTaskRecord::isActiveForBrokerAdmission)
    }
}

private fun RealtimeTaskRecord.isActiveForBrokerAdmission(): Boolean =
    lifecycle == RealtimeTaskLifecycle.PENDING_DECISION ||
        lifecycle == RealtimeTaskLifecycle.SUBMITTED ||
        lifecycle == RealtimeTaskLifecycle.RUNNING ||
        lifecycle == RealtimeTaskLifecycle.PREEMPTED ||
        lifecycle == RealtimeTaskLifecycle.MIGRATING ||
        lifecycle == RealtimeTaskLifecycle.RETRYING
