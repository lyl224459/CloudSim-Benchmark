package scheduler

import config.DataLocalityPolicy
import config.RealtimeQueuePolicy
import config.TenantSchedulingPolicy
import config.RealtimeTopologyPolicy
import datacenter.RealtimeTraceMetadataRegistry
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

@JvmInline
value class DatacenterId(val value: Int)

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
    val tenantId: TenantId = TenantId(0),
    val tenantKey: String? = null,
    val requestedCpu: Double? = null,
    val requestedRam: Double? = null,
    val requestedBw: Double? = null,
    val requestedIo: Double? = null,
    val dataRegion: RegionId? = null,
    val inputDataSizeGb: Double = 0.0,
    val imageId: String? = null,
    val imageSizeGb: Double = 0.0,
    val traceRetryHint: Int? = null
) {
    val id: CloudletId get() = CloudletId(cloudletId)
    val assignedVm: VmIndex? get() = assignedVmIndex?.let(::VmIndex)
    val state: RealtimeTaskState get() = RealtimeTaskState.fromLifecycle(lifecycle)

    fun workloadDescriptor(defaultDataRegion: RegionId = RegionId(0)): RealtimeWorkloadDescriptor =
        RealtimeWorkloadDescriptor(
            cloudletId = id,
            tenantId = tenantId,
            priority = priority,
            deadline = deadline,
            requestedCpu = requestedCpu ?: 1.0,
            requestedRam = requestedRam ?: 0.0,
            requestedBw = requestedBw ?: 0.0,
            requestedIo = requestedIo ?: 0.0,
            dataRegion = dataRegion ?: defaultDataRegion,
            inputDataSizeGb = inputDataSizeGb.coerceAtLeast(0.0),
            imageId = imageId,
            imageSizeGb = imageSizeGb.coerceAtLeast(0.0)
        )
}

data class RealtimeWorkloadDescriptor(
    val cloudletId: CloudletId,
    val tenantId: TenantId,
    val priority: Int,
    val deadline: Double?,
    val requestedCpu: Double,
    val requestedRam: Double,
    val requestedBw: Double,
    val requestedIo: Double,
    val dataRegion: RegionId,
    val inputDataSizeGb: Double,
    val imageId: String?,
    val imageSizeGb: Double
)

data class RealtimeTenantFairnessSnapshot(
    val tenantId: TenantId,
    val activeCount: Int,
    val completedCount: Int,
    val quota: Int?,
    val weight: Double,
    val fairnessScore: Double,
    val dominantResourceShare: Double,
    val budgetUsed: Double,
    val budgetLimit: Double?,
    val slaPenalty: Double,
    val fairnessPressure: Double
)

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
    val topologyFailurePressure: Double = 0.0,
    val physicalHostUtilization: Double = 0.0,
    val hostResourceFragmentation: Double = 0.0,
    val networkTransferDelay: Double = 0.0,
    val dataLocalityHit: Boolean = true,
    val imageCacheHit: Boolean = false,
    val placementFailureReason: String? = null
)

data class RealtimeHostState(
    val datacenterId: DatacenterId = DatacenterId(0),
    val location: RealtimeTopologyLocation,
    val cpuCapacity: Double,
    val ramCapacity: Double,
    val bwCapacity: Double,
    val ioCapacity: Double,
    val allocatedCpu: Double = 0.0,
    val allocatedRam: Double = 0.0,
    val allocatedBw: Double = 0.0,
    val allocatedIo: Double = 0.0,
    val cachedImages: Set<String> = emptySet()
) {
    val utilization: Double
        get() = listOf(
            allocatedCpu.ratio(cpuCapacity),
            allocatedRam.ratio(ramCapacity),
            allocatedBw.ratio(bwCapacity),
            allocatedIo.ratio(ioCapacity)
        ).maxOrNull() ?: 0.0

    val fragmentation: Double
        get() {
            val capacities = listOf(cpuCapacity, ramCapacity, bwCapacity, ioCapacity)
            val allocations = listOf(allocatedCpu, allocatedRam, allocatedBw, allocatedIo)
            val usableRatios = capacities.zip(allocations).mapNotNull { (capacity, used) ->
                if (capacity <= 0.0) null else ((capacity - used).coerceAtLeast(0.0) / capacity)
            }
            if (usableRatios.isEmpty()) return 0.0
            return (usableRatios.maxOrNull() ?: 0.0) - (usableRatios.minOrNull() ?: 0.0)
        }

    private fun Double.ratio(capacity: Double): Double =
        if (capacity <= 0.0) 0.0 else (this / capacity).coerceAtLeast(0.0)
}

sealed interface RealtimePlacementDecision {
    val vmIndex: VmIndex

    data class Accepted(
        override val vmIndex: VmIndex,
        val location: RealtimeTopologyLocation,
        val hostState: RealtimeHostState,
        val dataLocal: Boolean,
        val imageCacheHit: Boolean,
        val networkTransferDelay: Double,
        val networkTransferGb: Double,
        val imagePullDelay: Double,
        val topologyCost: Double,
        val score: Double
    ) : RealtimePlacementDecision {
        val placementDelay: Double get() = networkTransferDelay + imagePullDelay
    }

    data class Rejected(
        override val vmIndex: VmIndex,
        val location: RealtimeTopologyLocation,
        val reason: String
    ) : RealtimePlacementDecision
}

data class NodeCandidate(
    val nodeState: RealtimeNodeState,
    val placement: RealtimePlacementDecision,
    val score: Double
) {
    val vmIndex: Int get() = nodeState.vmIndex
    val acceptedPlacement: RealtimePlacementDecision.Accepted? get() = placement as? RealtimePlacementDecision.Accepted
    val isAccepted: Boolean get() = acceptedPlacement != null && nodeState.acceptingWork
}

fun interface CandidateFilter {
    fun accepts(candidate: NodeCandidate): Boolean
}

fun interface CandidateScorer {
    fun score(candidate: NodeCandidate): Double
}

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
    val preemptionCandidates: List<RealtimePreemptionCandidate> = emptyList(),
    val tenantSchedulingPolicy: TenantSchedulingPolicy = TenantSchedulingPolicy.QUOTA_FIRST,
    val tenantSnapshots: List<RealtimeTenantFairnessSnapshot> = emptyList(),
    val nodeCandidates: List<NodeCandidate> = emptyList()
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

data class RealtimeResourceDemand(
    val cpu: Double = 0.0,
    val ram: Double = 0.0,
    val bw: Double = 0.0,
    val io: Double = 0.0
) {
    operator fun plus(other: RealtimeResourceDemand): RealtimeResourceDemand =
        RealtimeResourceDemand(
            cpu = cpu + other.cpu,
            ram = ram + other.ram,
            bw = bw + other.bw,
            io = io + other.io
        )
}

data class RealtimePhysicalHostMetrics(
    val averageUtilization: Double,
    val averageFragmentation: Double
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
    private val physicalTopologyEnabled: Boolean,
    private val dataLocalityEnabled: Boolean,
    private val imageCacheEnabled: Boolean,
    private val hostCpuCapacity: Double,
    private val hostRamCapacity: Double,
    private val hostBwCapacity: Double,
    private val hostIoCapacity: Double,
    private val crossRackBandwidth: Double,
    private val crossRegionBandwidth: Double,
    private val dataLocalityPolicy: DataLocalityPolicy,
    private val imageCacheCapacity: Int,
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
            physicalTopologyEnabled = false,
            dataLocalityEnabled = false,
            imageCacheEnabled = false,
            hostCpuCapacity = 0.0,
            hostRamCapacity = 0.0,
            hostBwCapacity = 0.0,
            hostIoCapacity = 0.0,
            crossRackBandwidth = 0.0,
            crossRegionBandwidth = 0.0,
            dataLocalityPolicy = DataLocalityPolicy.PREFER_LOCAL,
            imageCacheCapacity = 0,
            initialVmCount = 0
        )

        fun fromConfig(scheduling: config.RealtimeSchedulingConfig, initialVmCount: Int): RealtimeTopologyModel =
            RealtimeTopologyModel(
                enabled = scheduling.topologyEnabled ||
                    scheduling.physicalTopologyEnabled ||
                    scheduling.dataLocalityEnabled ||
                    scheduling.imageCacheEnabled,
                regionCount = scheduling.regionCount.coerceAtLeast(1),
                racksPerRegion = scheduling.racksPerRegion.coerceAtLeast(1),
                hostsPerRack = if (scheduling.physicalTopologyEnabled) {
                    scheduling.hostCountPerRack.coerceAtLeast(1)
                } else {
                    scheduling.hostsPerRack.coerceAtLeast(1)
                },
                localRegion = RegionId(scheduling.localRegion.coerceIn(0, scheduling.regionCount.coerceAtLeast(1) - 1)),
                crossRackLatency = scheduling.crossRackLatency,
                crossRegionLatency = scheduling.crossRegionLatency,
                crossRegionCost = scheduling.crossRegionCost,
                hostFailureRate = scheduling.hostFailureRate,
                rackFailureRate = scheduling.rackFailureRate,
                regionFailureRate = scheduling.regionFailureRate,
                physicalTopologyEnabled = scheduling.physicalTopologyEnabled,
                dataLocalityEnabled = scheduling.dataLocalityEnabled,
                imageCacheEnabled = scheduling.imageCacheEnabled,
                hostCpuCapacity = scheduling.hostCpuCapacity,
                hostRamCapacity = scheduling.hostRamCapacity,
                hostBwCapacity = scheduling.hostBwCapacity,
                hostIoCapacity = scheduling.hostIoCapacity,
                crossRackBandwidth = scheduling.crossRackBandwidth,
                crossRegionBandwidth = scheduling.crossRegionBandwidth,
                dataLocalityPolicy = scheduling.normalizedDataLocalityPolicy(),
                imageCacheCapacity = scheduling.imageCacheCapacity,
                initialVmCount = initialVmCount
            )
    }

    private val locationsByVmIndex = linkedMapOf<Int, RealtimeTopologyLocation>()
    private val imageCacheByHost = linkedMapOf<RealtimeTopologyLocation, LinkedHashSet<String>>()

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

    fun candidatesFor(
        states: List<RealtimeNodeState>,
        vmList: List<Vm>,
        workload: RealtimeWorkloadDescriptor,
        records: List<RealtimeTaskRecord>
    ): List<NodeCandidate> {
        if (!physicalTopologyEnabled && !dataLocalityEnabled && !imageCacheEnabled) return emptyList()
        val activeDemandByHost = activeDemandByHost(records)
        val filters = listOf(
            CandidateFilter { it.nodeState.acceptingWork },
            CandidateFilter { candidate -> candidate.placement is RealtimePlacementDecision.Accepted }
        )
        val scorers = candidateScorers()
        return states.map { state ->
            val location = locationOf(state.vmIndex)
            val demand = activeDemandByHost[location] ?: RealtimeResourceDemand()
            val placement = placementFor(state, vmList.getOrNull(state.vmIndex), workload, demand)
            val candidate = NodeCandidate(
                nodeState = state.withPlacement(placement),
                placement = placement,
                score = scorers.sumOf { scorer -> scorer.score(NodeCandidate(state, placement, 0.0)) }
            )
            candidate.copy(score = scorers.sumOf { scorer -> scorer.score(candidate) })
        }.filter { candidate ->
            filters.all { filter -> filter.accepts(candidate) } || candidate.placement is RealtimePlacementDecision.Rejected
        }
    }

    fun recordSubmission(vmIndex: Int, workload: RealtimeWorkloadDescriptor) {
        if (!imageCacheEnabled) return
        val imageId = workload.imageId ?: return
        if (imageCacheCapacity <= 0) return
        val location = locationOf(vmIndex)
        val cache = imageCacheByHost.getOrPut(location) { LinkedHashSet() }
        if (cache.remove(imageId)) {
            cache.add(imageId)
            return
        }
        while (cache.size >= imageCacheCapacity) {
            val oldest = cache.firstOrNull() ?: break
            cache.remove(oldest)
        }
        cache.add(imageId)
    }

    fun physicalHostMetrics(records: List<RealtimeTaskRecord>): RealtimePhysicalHostMetrics {
        if (!physicalTopologyEnabled) return RealtimePhysicalHostMetrics(0.0, 0.0)
        val demandByHost = activeDemandByHost(records)
        val hosts = allLocations()
        if (hosts.isEmpty()) return RealtimePhysicalHostMetrics(0.0, 0.0)
        val states = hosts.map { location -> hostState(location, demandByHost[location] ?: RealtimeResourceDemand()) }
        return RealtimePhysicalHostMetrics(
            averageUtilization = states.map { it.utilization }.average(),
            averageFragmentation = states.map { it.fragmentation }.average()
        )
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

    private fun placementFor(
        state: RealtimeNodeState,
        vm: Vm?,
        workload: RealtimeWorkloadDescriptor,
        activeDemand: RealtimeResourceDemand
    ): RealtimePlacementDecision {
        val location = locationOf(state.vmIndex)
        val incomingDemand = workload.toDemand()
        val projectedDemand = activeDemand + incomingDemand
        val hostState = hostState(location, activeDemand)
        val capacityReason = capacityRejectionReason(projectedDemand)
        if (physicalTopologyEnabled && capacityReason != null) {
            return RealtimePlacementDecision.Rejected(VmIndex(state.vmIndex), location, capacityReason)
        }

        val dataLocal = !dataLocalityEnabled || workload.dataRegion == location.regionId
        val networkTransferGb = if (dataLocal || !dataLocalityEnabled) 0.0 else workload.inputDataSizeGb.coerceAtLeast(0.0)
        val bandwidth = when {
            !dataLocalityEnabled || networkTransferGb <= 0.0 -> 0.0
            workload.dataRegion != location.regionId -> crossRegionBandwidth
            location.rackId.value != 0 -> crossRackBandwidth
            else -> 0.0
        }
        val transferDelay = if (networkTransferGb > 0.0 && bandwidth > 0.0) {
            networkTransferGb / bandwidth
        } else {
            0.0
        }
        val networkDelay = if (dataLocalityEnabled) latencyFor(location) + transferDelay else 0.0
        val imageId = workload.imageId
        val imageHit = imageCacheEnabled && imageId != null && imageCacheByHost[location]?.contains(imageId) == true
        val pullDelay = if (imageCacheEnabled && imageId != null && !imageHit) {
            val imageSize = workload.imageSizeGb.coerceAtLeast(0.0)
            if (imageSize > 0.0) imageSize * imagePullUnitDelay(vm) else imagePullUnitDelay(vm)
        } else {
            0.0
        }
        val score = state.availableTime +
            networkDelay +
            pullDelay +
            costFor(location) +
            dataLocalityPenalty(dataLocal, networkTransferGb) +
            hostState.utilization +
            hostState.fragmentation
        return RealtimePlacementDecision.Accepted(
            vmIndex = VmIndex(state.vmIndex),
            location = location,
            hostState = hostState(projectedDemand = projectedDemand, location = location),
            dataLocal = dataLocal,
            imageCacheHit = imageHit,
            networkTransferDelay = networkDelay,
            networkTransferGb = networkTransferGb,
            imagePullDelay = pullDelay,
            topologyCost = costFor(location),
            score = score
        )
    }

    private fun candidateScorers(): List<CandidateScorer> =
        listOf(
            CandidateScorer { it.nodeState.availableTime },
            CandidateScorer { (it.placement as? RealtimePlacementDecision.Accepted)?.score ?: Double.POSITIVE_INFINITY }
        )

    private fun activeDemandByHost(records: List<RealtimeTaskRecord>): Map<RealtimeTopologyLocation, RealtimeResourceDemand> =
        records
            .filter { it.lifecycle.isActiveForPhysicalPlacement() }
            .mapNotNull { record ->
                val vmIndex = record.assignedVmIndex ?: return@mapNotNull null
                locationOf(vmIndex) to record.workloadDescriptor(localRegion).toDemand()
            }
            .groupingBy { it.first }
            .fold(RealtimeResourceDemand()) { demand, pair -> demand + pair.second }

    private fun hostState(location: RealtimeTopologyLocation, demand: RealtimeResourceDemand): RealtimeHostState =
        RealtimeHostState(
            location = location,
            cpuCapacity = hostCpuCapacity,
            ramCapacity = hostRamCapacity,
            bwCapacity = hostBwCapacity,
            ioCapacity = hostIoCapacity,
            allocatedCpu = demand.cpu,
            allocatedRam = demand.ram,
            allocatedBw = demand.bw,
            allocatedIo = demand.io,
            cachedImages = imageCacheByHost[location].orEmpty()
        )

    private fun hostState(projectedDemand: RealtimeResourceDemand, location: RealtimeTopologyLocation): RealtimeHostState =
        hostState(location, projectedDemand)

    private fun capacityRejectionReason(demand: RealtimeResourceDemand): String? =
        when {
            hostCpuCapacity > 0.0 && demand.cpu > hostCpuCapacity -> "physical_cpu_capacity"
            hostRamCapacity > 0.0 && demand.ram > hostRamCapacity -> "physical_ram_capacity"
            hostBwCapacity > 0.0 && demand.bw > hostBwCapacity -> "physical_bw_capacity"
            hostIoCapacity > 0.0 && demand.io > hostIoCapacity -> "physical_io_capacity"
            else -> null
        }

    private fun dataLocalityPenalty(dataLocal: Boolean, transferGb: Double): Double =
        when (dataLocalityPolicy) {
            DataLocalityPolicy.IGNORE -> 0.0
            DataLocalityPolicy.BALANCED -> if (dataLocal) 0.0 else transferGb * 0.25
            DataLocalityPolicy.PREFER_LOCAL -> if (dataLocal) 0.0 else transferGb
        }

    private fun imagePullUnitDelay(vm: Vm?): Double {
        val capacity = vm?.bw?.capacity?.toDouble()?.takeIf { it > 0.0 } ?: return 1.0
        return 1.0 / capacity
    }

    private fun RealtimeNodeState.withPlacement(placement: RealtimePlacementDecision): RealtimeNodeState {
        val accepted = placement as? RealtimePlacementDecision.Accepted
        return copy(
            acceptingWork = acceptingWork && accepted != null,
            availableTime = availableTime + (accepted?.placementDelay ?: 0.0),
            topologyLatency = accepted?.networkTransferDelay ?: topologyLatency,
            topologyCost = accepted?.topologyCost ?: topologyCost,
            physicalHostUtilization = accepted?.hostState?.utilization ?: physicalHostUtilization,
            hostResourceFragmentation = accepted?.hostState?.fragmentation ?: hostResourceFragmentation,
            networkTransferDelay = accepted?.networkTransferDelay ?: networkTransferDelay,
            imagePullDelay = imagePullDelay + (accepted?.imagePullDelay ?: 0.0),
            dataLocalityHit = accepted?.dataLocal ?: dataLocalityHit,
            imageCacheHit = accepted?.imageCacheHit ?: imageCacheHit,
            placementFailureReason = (placement as? RealtimePlacementDecision.Rejected)?.reason
        )
    }

    private fun RealtimeWorkloadDescriptor.toDemand(): RealtimeResourceDemand =
        RealtimeResourceDemand(
            cpu = requestedCpu.coerceAtLeast(0.0),
            ram = requestedRam.coerceAtLeast(0.0),
            bw = requestedBw.coerceAtLeast(0.0),
            io = requestedIo.coerceAtLeast(0.0)
        )

    private fun RealtimeTaskLifecycle.isActiveForPhysicalPlacement(): Boolean =
        this == RealtimeTaskLifecycle.PENDING_DECISION ||
            this == RealtimeTaskLifecycle.SUBMITTED ||
            this == RealtimeTaskLifecycle.RUNNING ||
            this == RealtimeTaskLifecycle.PREEMPTED ||
            this == RealtimeTaskLifecycle.MIGRATING ||
            this == RealtimeTaskLifecycle.RETRYING
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
        if (enabled) {
            RealtimeTraceMetadataRegistry.get(cloudlet)?.requestedRam
                ?: (cloudlet.pesNumber.toDouble() * 256.0 + cloudlet.fileSize.toDouble() * ramWeight)
        } else {
            0.0
        }

    fun bwDemand(cloudlet: Cloudlet): Double =
        if (enabled) {
            RealtimeTraceMetadataRegistry.get(cloudlet)?.requestedBw
                ?: (cloudlet.fileSize + cloudlet.outputSize).toDouble()
        } else {
            0.0
        }

    fun ioDemand(cloudlet: Cloudlet): Double =
        if (enabled) {
            RealtimeTraceMetadataRegistry.get(cloudlet)?.requestedIo
                ?: ((cloudlet.fileSize + cloudlet.outputSize).toDouble() * ioWeight)
        } else {
            0.0
        }

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
