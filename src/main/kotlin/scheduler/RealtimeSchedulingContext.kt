package scheduler

import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm

data class RealtimeNodeState(
    val vmIndex: Int,
    val vmId: Long,
    val queueDepth: Int,
    val estimatedLoad: Double,
    val availableTime: Double,
    val failurePressure: Double
)

data class RealtimeSchedulingContext(
    val newCloudlet: Cloudlet,
    val activeCloudlets: List<Cloudlet>,
    val vmList: List<Vm>,
    val currentTime: Double,
    val nodeStates: List<RealtimeNodeState>
)

class RealtimeNodeStateTracker(
    private val vmList: List<Vm>
) {
    fun snapshot(activeCloudlets: List<Cloudlet>, currentTime: Double): List<RealtimeNodeState> {
        val loads = DoubleArray(vmList.size)
        val queueDepths = IntArray(vmList.size)

        for (cloudlet in activeCloudlets) {
            val vmIndex = vmList.indexOfFirst { it.id == cloudlet.vm?.id }
            if (vmIndex < 0) continue

            val vm = vmList[vmIndex]
            val remainingLength = cloudlet.length.toDouble()
            val estimatedTime = remainingLength / vm.mips
            loads[vmIndex] += estimatedTime
            queueDepths[vmIndex]++
        }

        val maxQueueDepth = queueDepths.maxOrNull()?.coerceAtLeast(1) ?: 1
        return vmList.mapIndexed { index, vm ->
            RealtimeNodeState(
                vmIndex = index,
                vmId = vm.id,
                queueDepth = queueDepths[index],
                estimatedLoad = loads[index],
                availableTime = currentTime + loads[index],
                failurePressure = queueDepths[index].toDouble() / maxQueueDepth.toDouble()
            )
        }
    }
}
