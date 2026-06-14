package broker

import scheduler.RealtimeTaskRecord
import scheduler.RealtimeTenantFairnessSnapshot

internal class TenantFairnessContextBuilder(
    private val tenantController: RealtimeTenantController,
) {
    fun snapshots(records: List<RealtimeTaskRecord>): List<RealtimeTenantFairnessSnapshot> {
        val snapshots = tenantController.snapshots(records)
        return snapshots
    }
}
