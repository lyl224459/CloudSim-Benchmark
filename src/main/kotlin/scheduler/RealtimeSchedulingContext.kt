package scheduler

import config.RealtimeQueuePolicy
import config.RealtimeTopologyPolicy
import config.TenantSchedulingPolicy
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm

data class RealtimeSchedulingContext(
    val newCloudlet: Cloudlet,
    val activeCloudlets: List<Cloudlet>,
    val vmList: List<Vm>,
    val currentTime: Double,
    val nodeStates: List<RealtimeNodeState>,
    val taskMetadata: RealtimeTaskMetadata =
        RealtimeTaskMetadata(
            cloudletId = newCloudlet.id,
            originalArrivalTime = newCloudlet.submissionDelay,
        ),
    val queuePolicy: RealtimeQueuePolicy = RealtimeQueuePolicy.FIFO,
    val topologyPolicy: RealtimeTopologyPolicy = RealtimeTopologyPolicy.LATENCY_AWARE,
    val preemptionCandidates: List<RealtimePreemptionCandidate> = emptyList(),
    val tenantSchedulingPolicy: TenantSchedulingPolicy = TenantSchedulingPolicy.QUOTA_FIRST,
    val tenantSnapshots: List<RealtimeTenantFairnessSnapshot> = emptyList(),
    val nodeCandidates: List<NodeCandidate> = emptyList(),
) {
    val acceptedCandidates: List<NodeCandidate> = nodeCandidates.filter { it.isAccepted }

    val candidateNodeStates: List<RealtimeNodeState> =
        if (nodeCandidates.isNotEmpty()) {
            acceptedCandidates.map { it.nodeState }
        } else {
            nodeStates.filter { it.acceptingWork }
        }

    val hasCapacityLimit: Boolean = nodeStates.any { it.availableSlots != Int.MAX_VALUE }

    val incomingTenantSnapshot: RealtimeTenantFairnessSnapshot? =
        tenantSnapshots.firstOrNull { it.tenantId == taskMetadata.tenantId }

    val tenantFairnessPressure: Double =
        incomingTenantSnapshot?.fairnessPressure ?: 0.0
}

class RealtimeNodeStateTracker(
    private val vmList: List<Vm>,
    private val vmQueueCapacity: Int = 0,
    private val resourceModel: RealtimeResourceModel = RealtimeResourceModel.Disabled,
    private val topologyModel: RealtimeTopologyModel = RealtimeTopologyModel.Disabled,
) {
    private val snapshotBuilder = ResourceSnapshotBuilder(vmList, vmQueueCapacity, resourceModel, topologyModel)

    fun snapshot(
        activeCloudlets: List<Cloudlet>,
        currentTime: Double,
        reservedVmIndexes: Map<Long, Int> = emptyMap(),
        lifecycleSnapshots: Map<Int, RealtimeVmLifecycleSnapshot> = emptyMap(),
        incomingCloudlet: Cloudlet? = null,
    ): List<RealtimeNodeState> =
        snapshotBuilder.build(
            ResourceSnapshotRequest(
                activeCloudlets = activeCloudlets,
                currentTime = currentTime,
                reservedVmIndexes = reservedVmIndexes,
                lifecycleSnapshots = lifecycleSnapshots,
                incomingCloudlet = incomingCloudlet,
            ),
        )
}
