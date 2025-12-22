package datacenter

import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm

/**
 * 目标函数接口
 */
interface ObjectiveFunction {
    /**
     * 计算适应度值
     * @param cloudletToVm 任务到虚拟机的映射数组
     * @return 适应度值
     */
    fun calculate(params: IntArray): Double
}

/**
 * 调度器目标函数实现
 * 包含成本、总时间、负载均衡等指标的计算
 */
class SchedulerObjectiveFunction(
    private val cloudletList: List<Cloudlet>,
    private val vmList: List<Vm>
) : ObjectiveFunction {
    
    companion object {
        // 使用统一配置
        private val ALPHA = config.ObjectiveConfig.ALPHA
        private val BETA = config.ObjectiveConfig.BETA
        private val GAMMA = config.ObjectiveConfig.GAMMA
    }
    
    private val cloudletNum = cloudletList.size
    private val vmNum = vmList.size
    
    /**
     * 估算最大完成时间（Makespan）
     */
    fun estimateMakespan(cloudletToVm: IntArray): Double {
        val executeTimeOfVM = DoubleArray(vmNum)
        
        for (i in 0 until cloudletNum) {
            val length = cloudletList[i].length.toDouble()
            val vmId = cloudletToVm[i]
            executeTimeOfVM[vmId] += length / vmList[vmId].mips
        }
        
        return executeTimeOfVM.maxOrNull() ?: 0.0
    }
    
    /**
     * 估算负载均衡度（Load Balance）
     */
    fun estimateLB(cloudletToVm: IntArray): Double {
        val executeTimeOfVM = DoubleArray(vmNum)
        var avgExecuteTime = 0.0
        
        for (i in 0 until cloudletNum) {
            val length = cloudletList[i].length.toDouble()
            val vmId = cloudletToVm[i]
            val execTime = length / vmList[vmId].mips
            executeTimeOfVM[vmId] += execTime
            avgExecuteTime += execTime
        }
        
        avgExecuteTime /= vmNum
        
        var LB = 0.0
        for (i in 0 until vmNum) {
            LB += Math.pow(executeTimeOfVM[i] - avgExecuteTime, 2.0)
        }
        LB = Math.sqrt(LB / vmNum)
        
        return LB
    }
    
    /**
     * 估算总成本
     */
    fun estimateCost(cloudletToVm: IntArray): Double {
        var cost = 0.0
        
        for (i in 0 until cloudletNum) {
            val length = cloudletList[i].length.toDouble()
            val vmId = cloudletToVm[i]
            val mips = vmList[vmId].mips
            
            val costPerSec = when {
                mips == Constants.L_MIPS.toDouble() -> Constants.L_PRICE
                mips == Constants.M_MIPS.toDouble() -> Constants.M_PRICE
                mips == Constants.H_MIPS.toDouble() -> Constants.H_PRICE
                else -> Constants.L_PRICE
            }
            
            cost += length / mips * costPerSec
        }
        
        return cost
    }
    
    /**
     * 估算总时间
     */
    fun estimateTotalTime(cloudletToVm: IntArray): Double {
        var totalTime = 0.0
        
        for (i in 0 until cloudletNum) {
            val length = cloudletList[i].length.toDouble()
            val vmId = cloudletToVm[i]
            totalTime += length / vmList[vmId].mips
        }
        
        return totalTime
    }
    
    /**
     * 估算最小成本
     */
    private fun estimateMinCost(): Double {
        val cloudletToVm = IntArray(cloudletNum) { vmNum - 1 }
        return estimateCost(cloudletToVm)
    }
    
    /**
     * 估算最大成本
     */
    private fun estimateMaxCost(): Double {
        val cloudletToVm = IntArray(cloudletNum) { 0 }
        return estimateCost(cloudletToVm)
    }
    
    /**
     * 估算最小总时间
     */
    private fun estimateMinTotalTime(): Double {
        val cloudletToVm = IntArray(cloudletNum) { vmNum - 1 }
        return estimateTotalTime(cloudletToVm)
    }
    
    /**
     * 估算最大总时间
     */
    private fun estimateMaxTotalTime(): Double {
        val cloudletToVm = IntArray(cloudletNum) { 0 }
        return estimateTotalTime(cloudletToVm)
    }
    
    /**
     * 估算最小负载均衡度
     */
    private fun estimateMinLB(): Double {
        return 0.0
    }
    
    /**
     * 估算最大负载均衡度（使用随机分配）
     */
    private fun estimateMaxLB(): Double {
        val random = java.util.Random(Constants.DEFAULT_RANDOM_SEED)
        val cloudletToVm = IntArray(cloudletNum) { random.nextInt(vmNum) }
        return estimateLB(cloudletToVm)
    }
    
    /**
     * 计算适应度值
     * 适应度值是成本、总时间和负载均衡的加权和
     */
    override fun calculate(params: IntArray): Double {
        val costRatio = (estimateCost(params) - estimateMinCost()) / 
                       (estimateMaxCost() - estimateMinCost())
        
        val timeRatio = (estimateTotalTime(params) - estimateMinTotalTime()) / 
                       (estimateMaxTotalTime() - estimateMinTotalTime())
        
        val maxLB = estimateMaxLB()
        val minLB = estimateMinLB()
        val lbRatio = if (maxLB > minLB) {
            (estimateLB(params) - minLB) / (maxLB - minLB)
        } else {
            0.0
        }
        
        return ALPHA * costRatio + BETA * timeRatio + GAMMA * lbRatio
    }
}

