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
    val lifecycle: RealtimeTaskLifecycle = RealtimeTaskLifecycle.ARRIVED,
    val interruptedCount: Int = 0,
    val checkpointRecoveredLength: Long = 0L,
    val timeoutActionTaken: String? = null,
    val migratedCount: Int = 0
)

data class RealtimeNodeState(
    val vmIndex: Int,
    val vmId: Long,
    val lifecycle: RealtimeVmLifecycle = RealtimeVmLifecycle.ACTIVE,
    val runningCount: Int,
    val pendingCount: Int,
    val queueDepth: Int,
    val availableSlots: Int,
    val acceptingWork: Boolean,
    val estimatedLoad: Double,
    val availableTime: Double,
    val failurePressure: Double,
    val ramPressure: Double = 0.0,
    val bwPressure: Double = 0.0,
    val ioPressure: Double = 0.0,
    val networkLatency: Double = 0.0,
    val imagePullDelay: Double = 0.0,
    val resourcePressure: Double = 0.0,
    val resourceAcceptingWork: Boolean = true,
    val rejectionReason: String? = null
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
    private val vmQueueCapacity: Int = 0,
    private val resourceModel: RealtimeResourceModel = RealtimeResourceModel.Disabled
) {
    private val vmIndexById: Map<Long, Int> = vmList.mapIndexed { index, vm -> vm.id to index }.toMap()

    fun snapshot(
        activeCloudlets: List<Cloudlet>,
        currentTime: Double,
        reservedVmIndexes: Map<Long, Int> = emptyMap(),
        lifecycleSnapshots: Map<Int, RealtimeVmLifecycleSnapshot> = emptyMap(),
        incomingCloudlet: Cloudlet? = null
    ): List<RealtimeNodeState> {
        val loads = DoubleArray(vmList.size)
        val ramDemand = DoubleArray(vmList.size)
        val bwDemand = DoubleArray(vmList.size)
        val ioDemand = DoubleArray(vmList.size)
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
            val resourceDelay = resourceModel.resourceDelay(cloudlet, vm)
            loads[vmIndex] += estimatedTime + resourceDelay
            ramDemand[vmIndex] += resourceModel.ramDemand(cloudlet)
            bwDemand[vmIndex] += resourceModel.bwDemand(cloudlet)
            ioDemand[vmIndex] += resourceModel.ioDemand(cloudlet)
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
            val lifecycle = lifecycleSnapshots[index]?.lifecycle ?: RealtimeVmLifecycle.ACTIVE
            val lifecycleAccepting = lifecycle == RealtimeVmLifecycle.ACTIVE
            val availableSlots = if (vmQueueCapacity <= 0) {
                Int.MAX_VALUE
            } else {
                max(0, vmQueueCapacity - queueDepths[index])
            }
            val ramPressure = resourceModel.ramPressure(ramDemand[index], vm)
            val bwPressure = resourceModel.bwPressure(bwDemand[index], vm)
            val ioPressure = resourceModel.ioPressure(ioDemand[index], vm)
            val resourcePressure = maxOf(ramPressure, bwPressure, ioPressure)
            val extraRam = incomingCloudlet?.let { resourceModel.ramDemand(it) } ?: 0.0
            val extraBw = incomingCloudlet?.let { resourceModel.bwDemand(it) } ?: 0.0
            val extraIo = incomingCloudlet?.let { resourceModel.ioDemand(it) } ?: 0.0
            val resourceAcceptingWork = resourceModel.accepts(
                ramDemand[index] + extraRam,
                bwDemand[index] + extraBw,
                ioDemand[index] + extraIo,
                vm
            )
            val capacityAccepting = vmQueueCapacity <= 0 || availableSlots > 0
            val rejectionReason = when {
                !lifecycleAccepting -> "vm_lifecycle_$lifecycle"
                !capacityAccepting -> "vm_queue_capacity"
                !resourceAcceptingWork -> "resource_capacity"
                else -> null
            }
            RealtimeNodeState(
                vmIndex = index,
                vmId = vm.id,
                lifecycle = lifecycle,
                runningCount = runningCounts[index],
                pendingCount = pendingCounts[index],
                queueDepth = queueDepths[index],
                availableSlots = availableSlots,
                acceptingWork = lifecycleAccepting && capacityAccepting && resourceAcceptingWork,
                estimatedLoad = loads[index],
                availableTime = currentTime + loads[index] + resourceModel.networkLatency + resourceModel.imagePullDelay,
                failurePressure = max(queueDepths[index].toDouble() / maxQueueDepth.toDouble(), resourcePressure),
                ramPressure = ramPressure,
                bwPressure = bwPressure,
                ioPressure = ioPressure,
                networkLatency = resourceModel.networkLatency,
                imagePullDelay = resourceModel.imagePullDelay,
                resourcePressure = resourcePressure,
                resourceAcceptingWork = resourceAcceptingWork,
                rejectionReason = rejectionReason
            )
        }
    }
}

data class RealtimeResourceModel(
    val enabled: Boolean,
    val networkLatency: Double,
    val imagePullDelay: Double,
    val ioWeight: Double,
    val ramWeight: Double,
    val bwWeight: Double
) {
    companion object {
        val Disabled = RealtimeResourceModel(
            enabled = false,
            networkLatency = 0.0,
            imagePullDelay = 0.0,
            ioWeight = 0.0,
            ramWeight = 0.0,
            bwWeight = 0.0
        )
    }

    fun resourceDelay(cloudlet: Cloudlet, vm: Vm): Double {
        if (!enabled) return 0.0
        val ioDelay = ioDemand(cloudlet) / vm.storage.capacity.coerceAtLeast(1).toDouble() * ioWeight
        val ramDelay = ramDemand(cloudlet) / vm.ram.capacity.coerceAtLeast(1).toDouble() * ramWeight
        val bwDelay = bwDemand(cloudlet) / vm.bw.capacity.coerceAtLeast(1).toDouble() * bwWeight
        return ioDelay + ramDelay + bwDelay + networkLatency + imagePullDelay
    }

    fun ramDemand(cloudlet: Cloudlet): Double =
        if (enabled) cloudlet.pesNumber.toDouble() * 256.0 + cloudlet.fileSize.toDouble() * ramWeight else 0.0

    fun bwDemand(cloudlet: Cloudlet): Double =
        if (enabled) (cloudlet.fileSize + cloudlet.outputSize).toDouble() else 0.0

    fun ioDemand(cloudlet: Cloudlet): Double =
        if (enabled) (cloudlet.fileSize + cloudlet.outputSize).toDouble() * ioWeight else 0.0

    fun ramPressure(demand: Double, vm: Vm): Double = pressure(demand, vm.ram.capacity)

    fun bwPressure(demand: Double, vm: Vm): Double = pressure(demand, vm.bw.capacity)

    fun ioPressure(demand: Double, vm: Vm): Double = pressure(demand, vm.storage.capacity)

    fun accepts(ramDemand: Double, bwDemand: Double, ioDemand: Double, vm: Vm): Boolean {
        if (!enabled) return true
        return ramDemand <= vm.ram.capacity && bwDemand <= vm.bw.capacity && ioDemand <= vm.storage.capacity
    }

    private fun pressure(demand: Double, capacity: Long): Double {
        if (!enabled) return 0.0
        return demand / capacity.coerceAtLeast(1).toDouble()
    }
}
