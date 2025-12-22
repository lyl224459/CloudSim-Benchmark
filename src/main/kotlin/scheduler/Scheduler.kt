package scheduler

import datacenter.ObjectiveFunction
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import util.Logger

/**
 * 调度器抽象基类
 */
abstract class Scheduler(
    protected val cloudletList: List<Cloudlet>,
    protected val vmList: List<Vm>
) {
    protected val cloudletNum = cloudletList.size
    protected val vmNum = vmList.size
    
    protected val objectiveFunction: ObjectiveFunction = 
        datacenter.SchedulerObjectiveFunction(cloudletList, vmList)
    
    /**
     * 分配任务到虚拟机
     * @return 任务到虚拟机的映射数组
     */
    abstract fun allocate(): IntArray
    
    /**
     * 执行调度
     */
    fun schedule() {
        val cloudletToVm = allocate()
        
        // 更新每个任务的虚拟机ID
        for (i in 0 until cloudletNum) {
            cloudletList[i].setVm(vmList[cloudletToVm[i]])
        }
        
        // 打印估计值
        val objFunc = objectiveFunction as datacenter.SchedulerObjectiveFunction
        Logger.debug("估计最大完成时间: {}", objFunc.estimateMakespan(cloudletToVm))
        Logger.debug("估计负载均衡度: {}", objFunc.estimateLB(cloudletToVm))
        Logger.debug("估计成本: {}", objFunc.estimateCost(cloudletToVm))
        Logger.debug("估计总时间: {}", objFunc.estimateTotalTime(cloudletToVm))
        Logger.debug("估计适应度: {}", objFunc.calculate(cloudletToVm))
    }
}

