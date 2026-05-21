package scheduler

import config.RealtimeQueuePolicy
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import kotlin.math.max

enum class RealtimeTaskLifecycle {
    ARRIVED,
    PENDING_DECISION,
    SUBMITTED,
    COMPLETED,
    REJECTED,
    FAILED
}

data class RealtimeTaskMetadata(
    val cloudletId: Long,
    val originalArrivalTime: Double,
    val attempt: Int = 0,
    val priority: Int = 0,
    val deadline: Double? = null,
    val assignedVmIndex: Int? = null,
    val lastDecisionDelay: Double = 0.0,
    val lifecycle: RealtimeTaskLifecycle = RealtimeTaskLifecycle.ARRIVED
)

data class RealtimeNodeState(
    val vmIndex: Int,
    val vmId: Long,
    val runningCount: Int,
    val pendingCount: Int,
    val queueDepth: Int,
    val availableSlots: Int,
    val acceptingWork: Boolean,
    val estimatedLoad: Double,
    val availableTime: Double,
    val failurePressure: Double
)

data class RealtimeSchedulingContext(
    val newCloudlet: Cloudlet,
    val activeCloudlets: List<Cloudlet>,
    val vmList: List<Vm>,
    val currentTime: Double,
    val nodeStates: List<RealtimeNodeState>,
    val taskMetadata: RealtimeTaskMetadata = RealtimeTaskMetadata(
        cloudletId = newCloudlet.id,
        originalArrivalTime = newCloudlet.submissionDelay
    ),
    val queuePolicy: RealtimeQueuePolicy = RealtimeQueuePolicy.FIFO
) {
    val candidateNodeStates: List<RealtimeNodeState> = nodeStates.filter { it.acceptingWork }

    val hasCapacityLimit: Boolean = nodeStates.any { it.availableSlots != Int.MAX_VALUE }
}

class RealtimeNodeStateTracker(
    private val vmList: List<Vm>,
    private val vmQueueCapacity: Int = 0
) {
    private val vmIndexById: Map<Long, Int> = vmList.mapIndexed { index, vm -> vm.id to index }.toMap()

    fun snapshot(
        activeCloudlets: List<Cloudlet>,
        currentTime: Double,
        reservedVmIndexes: Map<Long, Int> = emptyMap()
    ): List<RealtimeNodeState> {
        val loads = DoubleArray(vmList.size)
        val runningCounts = IntArray(vmList.size)
        val pendingCounts = IntArray(vmList.size)
        val queueDepths = IntArray(vmList.size)
        val activeCloudletIds = mutableSetOf<Long>()

        for (cloudlet in activeCloudlets) {
            activeCloudletIds.add(cloudlet.id)
            val vmIndex = reservedVmIndexes[cloudlet.id] ?: vmIndexById[cloudlet.vm?.id] ?: -1
            if (vmIndex < 0) continue

            val vm = vmList[vmIndex]
            val remainingLength = cloudlet.length.toDouble()
            val estimatedTime = remainingLength / vm.mips
            loads[vmIndex] += estimatedTime
            if (cloudlet.status == Cloudlet.Status.INEXEC) {
                runningCounts[vmIndex]++
            }
            queueDepths[vmIndex]++
        }
        for ((cloudletId, vmIndex) in reservedVmIndexes) {
            if (cloudletId in activeCloudletIds) continue
            if (vmIndex !in vmList.indices) continue
            pendingCounts[vmIndex]++
            queueDepths[vmIndex]++
        }

        val maxQueueDepth = queueDepths.maxOrNull()?.coerceAtLeast(1) ?: 1
        return vmList.mapIndexed { index, vm ->
            val availableSlots = if (vmQueueCapacity <= 0) {
                Int.MAX_VALUE
            } else {
                max(0, vmQueueCapacity - queueDepths[index])
            }
            RealtimeNodeState(
                vmIndex = index,
                vmId = vm.id,
                runningCount = runningCounts[index],
                pendingCount = pendingCounts[index],
                queueDepth = queueDepths[index],
                availableSlots = availableSlots,
                acceptingWork = vmQueueCapacity <= 0 || availableSlots > 0,
                estimatedLoad = loads[index],
                availableTime = currentTime + loads[index],
                failurePressure = queueDepths[index].toDouble() / maxQueueDepth.toDouble()
            )
        }
    }
}
