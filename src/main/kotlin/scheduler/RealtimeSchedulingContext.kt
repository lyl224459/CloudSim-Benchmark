package scheduler

import config.RealtimeQueuePolicy
import config.RealtimeTopologyPolicy
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import kotlin.math.max

@JvmInline
value class CloudletId(val value: Long)

@JvmInline
value class VmIndex(val value: Int)

@JvmInline
value class TenantId(val value: Int)

@JvmInline
value class RegionId(val value: Int)

@JvmInline
value class RackId(val value: Int)

@JvmInline
value class HostId(val value: Int)

@JvmInline
value class FailureDomainId(val value: Int)

enum class RealtimeTaskLifecycle {
    ARRIVED,
    PENDING_DECISION,
    SUBMITTED,
    RUNNING,
    PREEMPTED,
    MIGRATING,
    RETRYING,
    COMPLETED,
    REJECTED,
    FAILED,
    CANCELLED,
    TIMED_OUT
}

sealed interface RealtimeTaskState {
    val lifecycle: RealtimeTaskLifecycle

    data object Arrived : RealtimeTaskState { override val lifecycle = RealtimeTaskLifecycle.ARRIVED }
    data object PendingDecision : RealtimeTaskState { override val lifecycle = RealtimeTaskLifecycle.PENDING_DECISION }
    data object Submitted : RealtimeTaskState { override val lifecycle = RealtimeTaskLifecycle.SUBMITTED }
    data object Running : RealtimeTaskState { override val lifecycle = RealtimeTaskLifecycle.RUNNING }
    data object Preempted : RealtimeTaskState { override val lifecycle = RealtimeTaskLifecycle.PREEMPTED }
    data object Migrating : RealtimeTaskState { override val lifecycle = RealtimeTaskLifecycle.MIGRATING }
    data object Retrying : RealtimeTaskState { override val lifecycle = RealtimeTaskLifecycle.RETRYING }
    data object Completed : RealtimeTaskState { override val lifecycle = RealtimeTaskLifecycle.COMPLETED }
    data class Rejected(val reason: String? = null) : RealtimeTaskState { override val lifecycle = RealtimeTaskLifecycle.REJECTED }
    data class Failed(val reason: String? = null) : RealtimeTaskState { override val lifecycle = RealtimeTaskLifecycle.FAILED }
    data object Cancelled : RealtimeTaskState { override val lifecycle = RealtimeTaskLifecycle.CANCELLED }
    data object TimedOut : RealtimeTaskState { override val lifecycle = RealtimeTaskLifecycle.TIMED_OUT }

    companion object {
        fun fromLifecycle(lifecycle: RealtimeTaskLifecycle): RealtimeTaskState = when (lifecycle) {
            RealtimeTaskLifecycle.ARRIVED -> Arrived
            RealtimeTaskLifecycle.PENDING_DECISION -> PendingDecision
            RealtimeTaskLifecycle.SUBMITTED -> Submitted
            RealtimeTaskLifecycle.RUNNING -> Running
            RealtimeTaskLifecycle.PREEMPTED -> Preempted
            RealtimeTaskLifecycle.MIGRATING -> Migrating
            RealtimeTaskLifecycle.RETRYING -> Retrying
            RealtimeTaskLifecycle.COMPLETED -> Completed
            RealtimeTaskLifecycle.REJECTED -> Rejected()
            RealtimeTaskLifecycle.FAILED -> Failed()
            RealtimeTaskLifecycle.CANCELLED -> Cancelled
            RealtimeTaskLifecycle.TIMED_OUT -> TimedOut
        }
    }
}

data class RealtimeTaskRecord(
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
    val migratedCount: Int = 0,
    val preemptedCount: Int = 0,
    val preemptionDelayTotal: Double = 0.0,
    val checkpointLossTotal: Long = 0L,
    val tenantId: TenantId = TenantId(0)
) {
    val id: CloudletId get() = CloudletId(cloudletId)
    val assignedVm: VmIndex? get() = assignedVmIndex?.let(::VmIndex)
    val state: RealtimeTaskState get() = RealtimeTaskState.fromLifecycle(lifecycle)
}

typealias RealtimeTaskMetadata = RealtimeTaskRecord

data class RealtimePreemptionCandidate(
    val victimCloudletId: CloudletId,
    val victimVmIndex: VmIndex,
    val victimPriority: Int,
    val victimDeadline: Double?,
    val preemptedCount: Int
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
    val rejectionReason: String? = null,
    val regionId: RegionId = RegionId(0),
    val rackId: RackId = RackId(0),
    val hostId: HostId = HostId(0),
    val failureDomainId: FailureDomainId = FailureDomainId(0),
    val topologyLatency: Double = 0.0,
    val topologyCost: Double = 0.0,
    val failureDomainLoad: Int = 0,
    val topologyFailurePressure: Double = 0.0
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
    val queuePolicy: RealtimeQueuePolicy = RealtimeQueuePolicy.FIFO,
    val topologyPolicy: RealtimeTopologyPolicy = RealtimeTopologyPolicy.LATENCY_AWARE,
    val preemptionCandidates: List<RealtimePreemptionCandidate> = emptyList()
) {
    val candidateNodeStates: List<RealtimeNodeState> = nodeStates.filter { it.acceptingWork }

    val hasCapacityLimit: Boolean = nodeStates.any { it.availableSlots != Int.MAX_VALUE }
}

class RealtimeNodeStateTracker(
    private val vmList: List<Vm>,
    private val vmQueueCapacity: Int = 0,
    private val resourceModel: RealtimeResourceModel = RealtimeResourceModel.Disabled,
    private val topologyModel: RealtimeTopologyModel = RealtimeTopologyModel.Disabled
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
        val failureDomainLoads = mutableMapOf<FailureDomainId, Int>()

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
            val failureDomain = topologyModel.locationOf(vmIndex).failureDomainId
            failureDomainLoads[failureDomain] = (failureDomainLoads[failureDomain] ?: 0) + 1
        }
        for ((cloudletId, vmIndex) in reservedVmIndexes) {
            if (cloudletId in activeCloudletIds) continue
            if (vmIndex !in vmList.indices) continue
            pendingCounts[vmIndex]++
            queueDepths[vmIndex]++
            val failureDomain = topologyModel.locationOf(vmIndex).failureDomainId
            failureDomainLoads[failureDomain] = (failureDomainLoads[failureDomain] ?: 0) + 1
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
            val topology = topologyModel.locationOf(index)
            val topologyLatency = topologyModel.latencyFor(topology)
            val topologyCost = topologyModel.costFor(topology)
            val topologyFailurePressure = topologyModel.failurePressure(topology)
            val failureDomainLoad = failureDomainLoads[topology.failureDomainId] ?: 0
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
                availableTime = currentTime + loads[index] + resourceModel.networkLatency + resourceModel.imagePullDelay + topologyLatency,
                failurePressure = maxOf(queueDepths[index].toDouble() / maxQueueDepth.toDouble(), resourcePressure, topologyFailurePressure),
                ramPressure = ramPressure,
                bwPressure = bwPressure,
                ioPressure = ioPressure,
                networkLatency = resourceModel.networkLatency,
                imagePullDelay = resourceModel.imagePullDelay,
                resourcePressure = resourcePressure,
                resourceAcceptingWork = resourceAcceptingWork,
                rejectionReason = rejectionReason,
                regionId = topology.regionId,
                rackId = topology.rackId,
                hostId = topology.hostId,
                failureDomainId = topology.failureDomainId,
                topologyLatency = topologyLatency,
                topologyCost = topologyCost,
                failureDomainLoad = failureDomainLoad,
                topologyFailurePressure = topologyFailurePressure
            )
        }
    }
}

data class RealtimeTopologyLocation(
    val regionId: RegionId,
    val rackId: RackId,
    val hostId: HostId,
    val failureDomainId: FailureDomainId
)

data class RealtimeTopologyMetrics(
    val crossRackAssignmentCount: Int,
    val crossRegionAssignmentCount: Int,
    val averageTopologyLatency: Double,
    val topologyCost: Double,
    val failureDomainSpreadScore: Double
)

class RealtimeTopologyModel private constructor(
    private val enabled: Boolean,
    private val regionCount: Int,
    private val racksPerRegion: Int,
    private val hostsPerRack: Int,
    private val localRegion: RegionId,
    private val crossRackLatency: Double,
    private val crossRegionLatency: Double,
    private val crossRegionCost: Double,
    private val hostFailureRate: Double,
    private val rackFailureRate: Double,
    private val regionFailureRate: Double,
    initialVmCount: Int
) {
    companion object {
        val Disabled = RealtimeTopologyModel(
            enabled = false,
            regionCount = 1,
            racksPerRegion = 1,
            hostsPerRack = 1,
            localRegion = RegionId(0),
            crossRackLatency = 0.0,
            crossRegionLatency = 0.0,
            crossRegionCost = 0.0,
            hostFailureRate = 0.0,
            rackFailureRate = 0.0,
            regionFailureRate = 0.0,
            initialVmCount = 0
        )

        fun fromConfig(scheduling: config.RealtimeSchedulingConfig, initialVmCount: Int): RealtimeTopologyModel =
            RealtimeTopologyModel(
                enabled = scheduling.topologyEnabled,
                regionCount = scheduling.regionCount.coerceAtLeast(1),
                racksPerRegion = scheduling.racksPerRegion.coerceAtLeast(1),
                hostsPerRack = scheduling.hostsPerRack.coerceAtLeast(1),
                localRegion = RegionId(scheduling.localRegion.coerceIn(0, scheduling.regionCount.coerceAtLeast(1) - 1)),
                crossRackLatency = scheduling.crossRackLatency,
                crossRegionLatency = scheduling.crossRegionLatency,
                crossRegionCost = scheduling.crossRegionCost,
                hostFailureRate = scheduling.hostFailureRate,
                rackFailureRate = scheduling.rackFailureRate,
                regionFailureRate = scheduling.regionFailureRate,
                initialVmCount = initialVmCount
            )
    }

    private val locationsByVmIndex = linkedMapOf<Int, RealtimeTopologyLocation>()

    init {
        repeat(initialVmCount) { vmIndex ->
            locationsByVmIndex[vmIndex] = locationForOrdinal(vmIndex)
        }
    }

    fun locationOf(vmIndex: Int): RealtimeTopologyLocation =
        locationsByVmIndex.getOrPut(vmIndex) { locationForOrdinal(vmIndex) }

    fun registerDynamicVm(vmIndex: Int, activeVmIndexes: Set<Int> = emptySet()): RealtimeTopologyLocation {
        val location = if (!enabled) {
            locationForOrdinal(vmIndex)
        } else {
            val activeByDomain = activeVmIndexes
                .map { locationOf(it).failureDomainId }
                .groupingBy { it }
                .eachCount()
            allLocations().minWithOrNull(
                compareBy<RealtimeTopologyLocation> { activeByDomain[it.failureDomainId] ?: 0 }
                    .thenBy { latencyFor(it) }
                    .thenBy { it.failureDomainId.value }
            ) ?: locationForOrdinal(vmIndex)
        }
        locationsByVmIndex[vmIndex] = location
        return location
    }

    fun latencyFor(location: RealtimeTopologyLocation): Double {
        if (!enabled) return 0.0
        return when {
            location.regionId != localRegion -> crossRegionLatency
            location.rackId.value != 0 -> crossRackLatency
            else -> 0.0
        }
    }

    fun costFor(location: RealtimeTopologyLocation): Double =
        if (enabled && location.regionId != localRegion) crossRegionCost else 0.0

    fun failurePressure(location: RealtimeTopologyLocation): Double {
        if (!enabled) return 0.0
        val regionPressure = if (location.regionId != localRegion) regionFailureRate else 0.0
        return (hostFailureRate + rackFailureRate + regionPressure).coerceIn(0.0, 1.0)
    }

    fun metricsFor(vmIndexes: List<Int>): RealtimeTopologyMetrics {
        if (!enabled || vmIndexes.isEmpty()) {
            return RealtimeTopologyMetrics(0, 0, 0.0, 0.0, 1.0)
        }
        val locations = vmIndexes.map(::locationOf)
        val crossRegionCount = locations.count { it.regionId != localRegion }
        val crossRackCount = locations.count { it.regionId == localRegion && it.rackId.value != 0 } + crossRegionCount
        val averageLatency = locations.map(::latencyFor).average()
        val topologyCost = locations.sumOf(::costFor)
        val domainCounts = locations.groupingBy { it.failureDomainId }.eachCount().values.map { it.toDouble() }
        val sum = domainCounts.sum()
        val spread = if (sum <= 0.0) {
            1.0
        } else {
            val squareSum = domainCounts.sumOf { it * it }
            if (squareSum <= 0.0) 1.0 else (sum * sum) / (allLocations().size.toDouble() * squareSum)
        }
        return RealtimeTopologyMetrics(crossRackCount, crossRegionCount, averageLatency, topologyCost, spread)
    }

    private fun locationForOrdinal(ordinal: Int): RealtimeTopologyLocation {
        if (!enabled) {
            return RealtimeTopologyLocation(RegionId(0), RackId(0), HostId(0), FailureDomainId(0))
        }
        val region = ordinal.floorMod(regionCount)
        val rack = (ordinal / regionCount).floorMod(racksPerRegion)
        val host = (ordinal / (regionCount * racksPerRegion)).floorMod(hostsPerRack)
        val domain = ((region * racksPerRegion) + rack) * hostsPerRack + host
        return RealtimeTopologyLocation(RegionId(region), RackId(rack), HostId(host), FailureDomainId(domain))
    }

    private fun allLocations(): List<RealtimeTopologyLocation> =
        (0 until regionCount).flatMap { region ->
            (0 until racksPerRegion).flatMap { rack ->
                (0 until hostsPerRack).map { host ->
                    val domain = ((region * racksPerRegion) + rack) * hostsPerRack + host
                    RealtimeTopologyLocation(RegionId(region), RackId(rack), HostId(host), FailureDomainId(domain))
                }
            }
        }

    private fun Int.floorMod(divisor: Int): Int = Math.floorMod(this, divisor)
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
