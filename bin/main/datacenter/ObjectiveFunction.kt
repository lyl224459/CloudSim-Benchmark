package datacenter

import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j
import kotlin.math.pow
import kotlin.math.sqrt

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
    private val vmList: List<Vm>,
    private val weights: config.ObjectiveWeightsConfig = config.ObjectiveWeightsConfig()
) : ObjectiveFunction {

    companion object {
        // 默认权重（向后兼容）
        private val DEFAULT_ALPHA = config.ObjectiveConfig.ALPHA
        private val DEFAULT_BETA = config.ObjectiveConfig.BETA
        private val DEFAULT_GAMMA = config.ObjectiveConfig.GAMMA
    }
    
    private val cloudletNum = cloudletList.size
    private val vmNum = vmList.size
    
    /**
     * 估算最大完成时间（Makespan）- 高性能版本
     * 使用ND4J进行向量化计算
     */
    fun estimateMakespan(cloudletToVm: IntArray): Double {
        val executeTimes = Nd4j.zeros(vmNum)

        // 累加每个VM的执行时间
        for (i in 0 until cloudletNum) {
            val length = cloudletList[i].length.toDouble()
            val vmId = cloudletToVm[i]
            val execTime = length / vmList[vmId].mips
            val currentTime = executeTimes.getDouble(vmId.toLong())
            executeTimes.putScalar(vmId.toLong(), currentTime + execTime)
        }

        return executeTimes.maxNumber().toDouble()
    }
    
    /**
     * 估算负载均衡度（Load Balance）- 高性能版本
     * 使用ND4J进行向量化计算，提升性能
     */
    fun estimateLB(cloudletToVm: IntArray): Double {
        // 使用ND4J进行向量化计算
        val executeTimes = Nd4j.zeros(vmNum)

        // 累加每个VM的执行时间
        for (i in 0 until cloudletNum) {
            val length = cloudletList[i].length.toDouble()
            val vmId = cloudletToVm[i]
            val execTime = length / vmList[vmId].mips
            val currentTime = executeTimes.getDouble(vmId.toLong())
            executeTimes.putScalar(vmId.toLong(), currentTime + execTime)
        }

        // 计算平均执行时间
        val avgExecuteTime = executeTimes.meanNumber().toDouble()

        // 计算方差的平方根（标准差）
        val diff = executeTimes.sub(avgExecuteTime)
        val squaredDiff = diff.mul(diff)
        val variance = squaredDiff.meanNumber().toDouble()

        return sqrt(variance)
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
                mips == config.DatacenterConfig.L_MIPS.toDouble() -> config.DatacenterConfig.L_PRICE
                mips == config.DatacenterConfig.M_MIPS.toDouble() -> config.DatacenterConfig.M_PRICE
                mips == config.DatacenterConfig.H_MIPS.toDouble() -> config.DatacenterConfig.H_PRICE
                else -> config.DatacenterConfig.L_PRICE
            }
            
            cost += length / mips * costPerSec
        }
        
        return cost
    }
    
    /**
     * 估算总时间 - 高性能版本
     * 使用ND4J进行向量化计算
     */
    fun estimateTotalTime(cloudletToVm: IntArray): Double {
        var totalTime = 0.0

        // 向量化累加计算
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
        val random = java.util.Random(config.DatacenterConfig.DEFAULT_RANDOM_SEED)
        val cloudletToVm = IntArray(cloudletNum) { random.nextInt(vmNum) }
        return estimateLB(cloudletToVm)
    }
    
    /**
     * 计算适应度值
     * 适应度值是成本、总时间、负载均衡和Makespan的加权和
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

        // 计算Makespan比例（可选）
        val makespanRatio = if (weights.makespan > 0.0) {
            val makespan = estimateMakespan(params)
            val minMakespan = estimateMinMakespan()
            val maxMakespan = estimateMaxMakespan()
            if (maxMakespan > minMakespan) {
                (makespan - minMakespan) / (maxMakespan - minMakespan)
            } else {
                0.0
            }
        } else {
            0.0
        }

        return weights.cost * costRatio +
               weights.totalTime * timeRatio +
               weights.loadBalance * lbRatio +
               weights.makespan * makespanRatio
    }

    /**
     * 估算最小Makespan
     */
    private fun estimateMinMakespan(): Double {
        val cloudletToVm = IntArray(cloudletNum) { vmNum - 1 } // 分配给最快的VM
        return estimateMakespan(cloudletToVm)
    }

    /**
     * 估算最大Makespan
     */
    private fun estimateMaxMakespan(): Double {
        val cloudletToVm = IntArray(cloudletNum) { 0 } // 分配给最慢的VM
        return estimateMakespan(cloudletToVm)
    }
}

