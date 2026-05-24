package scheduler

import config.RealtimeQueuePolicy
import config.RealtimeTopologyPolicy
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import java.util.*

internal const val REALTIME_OPTIMIZATION_THRESHOLD = 3

/**
 * 实时调度器接口
 * 处理动态到达的任务
 */
interface RealtimeScheduler {
    fun scheduleOnArrival(context: RealtimeSchedulingContext): Int

    /**
     * 当新任务到达时调用此方法进行调度
     * @param newCloudlet 新到达的任务
     * @param waitingCloudlets 等待调度的任务列表
     * @param vmList 可用的虚拟机列表
     * @return 分配结果（任务ID到VM ID的映射）
     */
    fun scheduleOnArrival(
        newCloudlet: Cloudlet,
        waitingCloudlets: List<Cloudlet>,
        vmList: List<Vm>
    ): Int {
        val nodeStates = RealtimeNodeStateTracker(vmList).snapshot(waitingCloudlets, 0.0)
        return scheduleOnArrival(
            RealtimeSchedulingContext(
                newCloudlet = newCloudlet,
                activeCloudlets = waitingCloudlets,
                vmList = vmList,
                currentTime = 0.0,
                nodeStates = nodeStates
            )
        )
    }
}

/**
 * 实时调度器基类
 */
abstract class RealtimeSchedulerBase(
    protected val vmList: List<Vm>
) : RealtimeScheduler {
    
    protected val vmNum = vmList.size

    private val schedulerName: String
        get() = javaClass.simpleName.ifBlank { "RealtimeScheduler" }

    init {
        SchedulerAllocationValidator.requireAvailableVms(vmNum, schedulerName)
    }
    
    /**
     * 快速分配策略：将任务分配到当前负载最小的VM
     */
    protected fun findLeastLoadedVm(waitingCloudlets: List<Cloudlet>): Int {
        val vmLoads = DoubleArray(vmNum)
        
        // 计算每个VM的当前负载
        for (cloudlet in waitingCloudlets) {
            val vmId = cloudlet.vm.id.toInt()
            if (vmId >= 0 && vmId < vmNum) {
                val length = cloudlet.length.toDouble()
                vmLoads[vmId] += length / vmList[vmId].mips
            }
        }
        
        // 找到负载最小的VM
        return vmLoads.indices.minByOrNull { vmLoads[it] } ?: 0
    }

    protected fun findLeastLoadedVm(context: RealtimeSchedulingContext): Int {
        val base = selectableNodeStates(context)
        return base.minWithOrNull(
            compareBy<RealtimeNodeState> { !it.acceptingWork }
                .thenBy { it.availableTime }
                .thenBy { it.estimatedLoad }
                .thenBy { it.queueDepth }
                .thenBy { it.vmIndex }
        )?.vmIndex ?: 0
    }

    protected fun orderedCandidateStates(context: RealtimeSchedulingContext): List<RealtimeNodeState> {
        val base = selectableNodeStates(context)
        val preemptableVmIndexes = context.preemptionCandidates.map { it.victimVmIndex.value }.toSet()
        val queueComparator = when (context.queuePolicy) {
            RealtimeQueuePolicy.PRIORITY -> compareByDescending<RealtimeNodeState> { it.availableSlots }
                .thenBy { it.availableTime }
                .thenBy { it.estimatedLoad }
            RealtimeQueuePolicy.DEADLINE -> compareBy<RealtimeNodeState> { projectedFinishTime(context, it) }
                .thenBy { it.availableTime }
                .thenBy { it.queueDepth }
            RealtimeQueuePolicy.FIFO -> compareBy<RealtimeNodeState> { it.availableTime }
                .thenBy { it.estimatedLoad }
                .thenBy { it.queueDepth }
        }
        val topologyComparator = when (context.topologyPolicy) {
            RealtimeTopologyPolicy.SPREAD_FAULT_DOMAINS -> compareBy<RealtimeNodeState> { it.failureDomainLoad }
                .thenBy { it.topologyLatency }
                .thenBy { it.topologyCost }
            RealtimeTopologyPolicy.LATENCY_AWARE -> compareBy<RealtimeNodeState> { it.availableTime }
                .thenBy { it.topologyLatency }
                .thenBy { it.topologyCost }
                .thenBy { it.failureDomainLoad }
        }
        return base.sortedWith(
            compareByDescending<RealtimeNodeState> { it.vmIndex in preemptableVmIndexes }
                .thenBy { tenantAdjustedCost(context, it) }
                .then(topologyComparator)
                .then(queueComparator)
                .thenBy { it.vmIndex }
        )
    }

    protected fun selectableNodeStates(context: RealtimeSchedulingContext): List<RealtimeNodeState> =
        if (context.nodeCandidates.isNotEmpty()) {
            context.candidateNodeStates
        } else {
            context.candidateNodeStates.ifEmpty { context.nodeStates }
        }

    private fun tenantAdjustedCost(context: RealtimeSchedulingContext, state: RealtimeNodeState): Double {
        val pressure = context.tenantFairnessPressure
        return when (context.tenantSchedulingPolicy) {
            config.TenantSchedulingPolicy.QUOTA_FIRST -> 0.0
            config.TenantSchedulingPolicy.WEIGHTED_FAIR -> state.queueDepth * pressure
            config.TenantSchedulingPolicy.DOMINANT_RESOURCE_FAIRNESS ->
                state.resourcePressure + state.topologyCost * (1.0 + pressure)
        }
    }

    private fun projectedFinishTime(context: RealtimeSchedulingContext, state: RealtimeNodeState): Double {
        val vm = context.vmList[state.vmIndex]
        return state.availableTime + context.newCloudlet.length.toDouble() / vm.mips
    }
}

/**
 * 实时随机调度器
 */
class RealtimeRandomScheduler(
    vmList: List<Vm>, 
    private val random: Random = Random(config.DatacenterConfig.DEFAULT_RANDOM_SEED)
) : RealtimeSchedulerBase(vmList) {
    
    override fun scheduleOnArrival(context: RealtimeSchedulingContext): Int {
        val candidates = selectableNodeStates(context)
        if (candidates.isEmpty()) return 0
        return candidates[random.nextInt(candidates.size)].vmIndex
    }
}

/**
 * 实时最小负载调度器
 */
class RealtimeMinLoadScheduler(vmList: List<Vm>) 
    : RealtimeSchedulerBase(vmList) {
    
    override fun scheduleOnArrival(context: RealtimeSchedulingContext): Int {
        return orderedCandidateStates(context).firstOrNull()?.vmIndex ?: findLeastLoadedVm(context)
    }
}

/**
 * 实时PSO调度器（增量调度）
 */
class RealtimePSOScheduler(
    vmList: List<Vm>,
    private val population: Int = 20,
    private val maxIter: Int = 20,
    internal val objectiveWeights: config.ObjectiveWeightsConfig,
    private val random: Random = Random(config.DatacenterConfig.DEFAULT_RANDOM_SEED)
) : RealtimeSchedulerBase(vmList) {
    override fun scheduleOnArrival(context: RealtimeSchedulingContext): Int {
        // 如果有等待任务，使用PSO进行批量调度
        if (context.activeCloudlets.size + 1 >= REALTIME_OPTIMIZATION_THRESHOLD) {
            val allCloudlets = context.activeCloudlets + context.newCloudlet
            val objFunc = datacenter.SchedulerObjectiveFunction(allCloudlets, context.vmList, objectiveWeights)
            val pso = PSO(objFunc, population, 0.0, (context.vmList.size - 1).toDouble(),
                allCloudlets.size, maxIter, random)
            val allocation = pso.execute()
            return allocation[allCloudlets.size - 1]  // 返回新任务的分配
        }
        
        // 如果没有等待任务，使用最小负载策略
        return orderedCandidateStates(context).firstOrNull()?.vmIndex ?: findLeastLoadedVm(context)
    }
}

/**
 * 实时WOA调度器（增量调度）
 */
class RealtimeWOAScheduler(
    vmList: List<Vm>,
    private val population: Int = 20,
    private val maxIter: Int = 20,
    internal val objectiveWeights: config.ObjectiveWeightsConfig,
    private val random: Random = Random(config.DatacenterConfig.DEFAULT_RANDOM_SEED)
) : RealtimeSchedulerBase(vmList) {
    override fun scheduleOnArrival(context: RealtimeSchedulingContext): Int {
        if (context.activeCloudlets.size + 1 >= REALTIME_OPTIMIZATION_THRESHOLD) {
            val allCloudlets = context.activeCloudlets + context.newCloudlet
            val objFunc = datacenter.SchedulerObjectiveFunction(allCloudlets, context.vmList, objectiveWeights)
            val woa = WOA(objFunc, population, 0.0, (context.vmList.size - 1).toDouble(),
                allCloudlets.size, maxIter, random)
            val allocation = woa.execute()
            return allocation[allCloudlets.size - 1]
        }
        return orderedCandidateStates(context).firstOrNull()?.vmIndex ?: findLeastLoadedVm(context)
    }
}

