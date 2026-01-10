package broker

import org.cloudsimplus.brokers.DatacenterBrokerSimple
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.core.CloudSimPlus
import org.cloudsimplus.vms.Vm
import scheduler.RealtimeScheduler
import java.util.*

/**
 * 实时调度代理
 * 监听任务到达事件并触发实时调度
 */
class RealtimeBroker(
    simulation: CloudSimPlus,
    private val scheduler: RealtimeScheduler,
    private val vmList: List<Vm>
) : DatacenterBrokerSimple(simulation) {
    
    private val waitingCloudlets = mutableListOf<Cloudlet>()
    
    /**
     * 提交单个任务并触发实时调度
     */
    fun submitCloudletRealtime(cloudlet: Cloudlet) {
        // 执行实时调度
        val vmId = scheduler.scheduleOnArrival(cloudlet, waitingCloudlets, vmList)
        
        // 分配任务到VM
        cloudlet.setVm(vmList[vmId])
        
        // 添加到等待列表
        waitingCloudlets.add(cloudlet)
        
        // 提交到broker
        submitCloudlet(cloudlet)
    }
    
    /**
     * 批量提交任务（用于初始化）
     */
    fun submitCloudletListRealtime(cloudletList: List<Cloudlet>) {
        // 按到达时间排序
        val sortedCloudlets = cloudletList.sortedBy { it.submissionDelay }
        
        for (cloudlet in sortedCloudlets) {
            submitCloudletRealtime(cloudlet)
        }
    }
    
    /**
     * 获取等待调度的任务列表
     */
    fun getWaitingCloudlets(): List<Cloudlet> {
        return waitingCloudlets.filter { 
            it.status != Cloudlet.Status.SUCCESS && 
            it.status != Cloudlet.Status.FAILED 
        }
    }
}

