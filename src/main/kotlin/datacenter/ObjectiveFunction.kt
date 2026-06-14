package datacenter

import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
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
@Suppress("TooManyFunctions") // ObjectiveFunction facade exposes the established metric estimation API.
class SchedulerObjectiveFunction(
    private val cloudletList: List<Cloudlet>,
    private val vmList: List<Vm>,
    private val weights: config.ObjectiveWeightsConfig = config.ObjectiveWeightsConfig(),
) : ObjectiveFunction {
    private val cloudletNum = cloudletList.size
    private val vmNum = vmList.size
    private val cloudletLengths = DoubleArray(cloudletNum) { index -> cloudletList[index].length.toDouble() }
    private val totalCloudletLength = cloudletLengths.sum()
    private val vmMips = DoubleArray(vmNum) { index -> vmList[index].mips.coerceAtLeast(1.0) }
    private val vmCostPerSecond = DoubleArray(vmNum) { index -> costPerSecond(vmMips[index]) }
    private val vmCostPerInstruction = DoubleArray(vmNum) { index -> vmCostPerSecond[index] / vmMips[index] }
    private val fastestVmIndex = vmMips.indices.maxByOrNull { vmMips[it] } ?: 0
    private val slowestVmIndex = vmMips.indices.minByOrNull { vmMips[it] } ?: 0
    private val cheapestVmIndex = vmCostPerInstruction.indices.minByOrNull { vmCostPerInstruction[it] } ?: 0
    private val mostExpensiveVmIndex = vmCostPerInstruction.indices.maxByOrNull { vmCostPerInstruction[it] } ?: 0
    private val minCost = if (vmNum > 0) estimateAllOnVmCost(cheapestVmIndex) else 0.0
    private val maxCost = if (vmNum > 0) estimateAllOnVmCost(mostExpensiveVmIndex) else 0.0
    private val minTotalTime = if (vmNum > 0) estimateAllOnVmTotalTime(fastestVmIndex) else 0.0
    private val maxTotalTime = if (vmNum > 0) estimateAllOnVmTotalTime(slowestVmIndex) else 0.0
    private val minMakespan = if (vmNum > 0) estimateAllOnVmMakespan(fastestVmIndex) else 0.0
    private val maxMakespan = if (vmNum > 0) estimateAllOnVmMakespan(slowestVmIndex) else 0.0
    private val minLoadBalance = 0.0
    private val maxLoadBalance = estimateRandomLoadBalance()

    init {
        require(vmNum > 0) { "目标函数需要至少 1 台可用 VM" }
    }

    /**
     * 估算最大完成时间（Makespan）- 高性能版本
     */
    fun estimateMakespan(cloudletToVm: IntArray): Double = metricsFor(cloudletToVm).makespan

    /**
     * 估算负载均衡度（Load Balance）- 高性能版本
     */
    fun estimateLB(cloudletToVm: IntArray): Double = metricsFor(cloudletToVm).loadBalance

    /**
     * 估算总成本
     */
    fun estimateCost(cloudletToVm: IntArray): Double = metricsFor(cloudletToVm).cost

    /**
     * 估算总时间 - 高性能版本
     */
    fun estimateTotalTime(cloudletToVm: IntArray): Double = metricsFor(cloudletToVm).totalTime

    /**
     * 计算适应度值
     * 适应度值是成本、总时间、负载均衡和Makespan的加权和
     */
    override fun calculate(params: IntArray): Double {
        val metrics = metricsFor(params)
        val costRatio = ratio(metrics.cost, minCost, maxCost)
        val timeRatio = ratio(metrics.totalTime, minTotalTime, maxTotalTime)
        val lbRatio = ratio(metrics.loadBalance, minLoadBalance, maxLoadBalance)
        val makespanRatio =
            if (weights.makespan > 0.0) {
                ratio(metrics.makespan, minMakespan, maxMakespan)
            } else {
                0.0
            }

        return weights.cost * costRatio +
            weights.totalTime * timeRatio +
            weights.loadBalance * lbRatio +
            weights.makespan * makespanRatio
    }

    private fun metricsFor(cloudletToVm: IntArray): ObjectiveMetrics {
        require(cloudletToVm.size == cloudletNum) {
            "任务到 VM 的映射数量 ${cloudletToVm.size} 与任务数量 $cloudletNum 不一致"
        }

        val executeTimes = DoubleArray(vmNum)
        var cost = 0.0
        var totalTime = 0.0

        for (i in 0 until cloudletNum) {
            val vmIndex = cloudletToVm[i]
            require(vmIndex in 0 until vmNum) {
                "非法 VM 下标: cloudlet=$i, vm=$vmIndex, 可用范围=0..${vmNum - 1}"
            }
            val execTime = cloudletLengths[i] / vmMips[vmIndex]
            executeTimes[vmIndex] += execTime
            totalTime += execTime
            cost += execTime * vmCostPerSecond[vmIndex]
        }

        val loadBalance = loadBalance(executeTimes, totalTime)
        val makespan = executeTimes.maxOrNull() ?: 0.0
        return ObjectiveMetrics(cost, totalTime, loadBalance, makespan)
    }

    private fun estimateAllOnVmCost(vmIndex: Int): Double = totalCloudletLength / vmMips[vmIndex] * vmCostPerSecond[vmIndex]

    private fun estimateAllOnVmTotalTime(vmIndex: Int): Double = totalCloudletLength / vmMips[vmIndex]

    private fun estimateAllOnVmMakespan(vmIndex: Int): Double = estimateAllOnVmTotalTime(vmIndex)

    private fun estimateRandomLoadBalance(): Double {
        if (vmNum <= 1 || cloudletNum == 0) return 0.0
        val random = java.util.Random(config.DatacenterConfig.DEFAULT_RANDOM_SEED)
        val executeTimes = DoubleArray(vmNum)
        var totalTime = 0.0
        for (i in 0 until cloudletNum) {
            val vmIndex = random.nextInt(vmNum)
            val execTime = cloudletLengths[i] / vmMips[vmIndex]
            executeTimes[vmIndex] += execTime
            totalTime += execTime
        }
        return loadBalance(executeTimes, totalTime)
    }

    private fun loadBalance(
        executeTimes: DoubleArray,
        totalTime: Double,
    ): Double {
        if (executeTimes.isEmpty()) return 0.0
        val avgExecuteTime = totalTime / executeTimes.size
        var sumSquaredDiff = 0.0
        for (time in executeTimes) {
            val diff = time - avgExecuteTime
            sumSquaredDiff += diff * diff
        }
        return sqrt(sumSquaredDiff / executeTimes.size)
    }

    private fun ratio(
        value: Double,
        min: Double,
        max: Double,
    ): Double {
        val denominator = max - min
        return if (denominator > 0.0) {
            ((value - min) / denominator).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
    }

    private fun costPerSecond(mips: Double): Double =
        when {
            mips == config.DatacenterConfig.L_MIPS.toDouble() -> config.DatacenterConfig.L_PRICE
            mips == config.DatacenterConfig.M_MIPS.toDouble() -> config.DatacenterConfig.M_PRICE
            mips == config.DatacenterConfig.H_MIPS.toDouble() -> config.DatacenterConfig.H_PRICE
            else -> config.DatacenterConfig.L_PRICE
        }
}

private data class ObjectiveMetrics(
    val cost: Double,
    val totalTime: Double,
    val loadBalance: Double,
    val makespan: Double,
)
