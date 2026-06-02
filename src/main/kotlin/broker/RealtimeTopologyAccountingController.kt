package broker

import scheduler.RealtimeTaskRecord
import scheduler.RealtimeTopologyModel

internal class RealtimeTopologyAccountingController(
    private val topologyModel: RealtimeTopologyModel,
    private val metrics: RealtimeBrokerMetrics,
) {
    fun recordSubmission(
        vmIndex: Int,
        record: RealtimeTaskRecord,
    ) {
        topologyModel.recordSubmission(vmIndex, record.workloadDescriptor())
    }

    fun recordFailure(domain: RealtimeFailureDomain?) {
        domain?.let(metrics::recordTopologyFailure)
    }
}
