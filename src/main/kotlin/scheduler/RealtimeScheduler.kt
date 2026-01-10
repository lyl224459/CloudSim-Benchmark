package scheduler

import datacenter.SchedulerObjectiveFunction
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import java.util.*

/**
 * 实时调度器接口
 * 处理动态到达的任务
 */
interface RealtimeScheduler {
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
    ): Int
}

/**
 * 实时调度器基类
 */
abstract class RealtimeSchedulerBase(
    protected val vmList: List<Vm>
) : RealtimeScheduler {
    
    protected val vmNum = vmList.size
    
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
}

/**
 * 实时随机调度器
 */
class RealtimeRandomScheduler(
    vmList: List<Vm>, 
    private val random: Random = Random(config.DatacenterConfig.DEFAULT_RANDOM_SEED)
) : RealtimeSchedulerBase(vmList) {
    
    override fun scheduleOnArrival(
        newCloudlet: Cloudlet,
        waitingCloudlets: List<Cloudlet>,
        vmList: List<Vm>
    ): Int {
        return random.nextInt(vmNum)
    }
}

/**
 * 实时最小负载调度器
 */
class RealtimeMinLoadScheduler(vmList: List<Vm>) 
    : RealtimeSchedulerBase(vmList) {
    
    override fun scheduleOnArrival(
        newCloudlet: Cloudlet,
        waitingCloudlets: List<Cloudlet>,
        vmList: List<Vm>
    ): Int {
        return findLeastLoadedVm(waitingCloudlets)
    }
}

/**
 * 实时PSO调度器（增量调度）
 */
class RealtimePSOScheduler(
    vmList: List<Vm>,
    private val population: Int = 20,
    private val maxIter: Int = 20,
    private val random: Random = Random(config.DatacenterConfig.DEFAULT_RANDOM_SEED)
) : RealtimeSchedulerBase(vmList) {
    
    override fun scheduleOnArrival(
        newCloudlet: Cloudlet,
        waitingCloudlets: List<Cloudlet>,
        vmList: List<Vm>
    ): Int {
        // 如果有等待任务，使用PSO进行批量调度
        if (waitingCloudlets.isNotEmpty()) {
            val allCloudlets = waitingCloudlets + newCloudlet
            val objFunc = datacenter.SchedulerObjectiveFunction(allCloudlets, vmList, config.ObjectiveWeightsConfig())
            val pso = PSO(objFunc, population, 0.0, (vmNum - 1).toDouble(), 
                allCloudlets.size, maxIter, random)
            val allocation = pso.execute()
            return allocation[allCloudlets.size - 1]  // 返回新任务的分配
        }
        
        // 如果没有等待任务，使用最小负载策略
        return findLeastLoadedVm(listOf(newCloudlet))
    }
}

/**
 * 实时WOA调度器（增量调度）
 */
class RealtimeWOAScheduler(
    vmList: List<Vm>,
    private val population: Int = 20,
    private val maxIter: Int = 20,
    private val random: Random = Random(config.DatacenterConfig.DEFAULT_RANDOM_SEED)
) : RealtimeSchedulerBase(vmList) {
    
    override fun scheduleOnArrival(
        newCloudlet: Cloudlet,
        waitingCloudlets: List<Cloudlet>,
        vmList: List<Vm>
    ): Int {
        if (waitingCloudlets.isNotEmpty()) {
            val allCloudlets = waitingCloudlets + newCloudlet
            val objFunc = datacenter.SchedulerObjectiveFunction(allCloudlets, vmList, config.ObjectiveWeightsConfig())
            val woa = WOA(objFunc, population, 0.0, (vmNum - 1).toDouble(), 
                allCloudlets.size, maxIter, random)
            val allocation = woa.execute()
            return allocation[allCloudlets.size - 1]
        }
        return findLeastLoadedVm(listOf(newCloudlet))
    }
}

