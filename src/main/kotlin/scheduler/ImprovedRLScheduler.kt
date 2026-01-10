package scheduler

import datacenter.SchedulerObjectiveFunction
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import util.Logger
import kotlin.math.min
import kotlin.random.Random

/**
 * 改进版强化学习调度器 (Improved RL Scheduler)
 * 
 * 改进点：
 * 1. 状态空间离散化：解决连续空间导致的 Q 表稀疏问题。
 * 2. 任务感知：状态中包含当前任务的特征。
 * 3. 目标对齐奖励：奖励函数与全局优化目标（负载、Makespan）直接挂钩。
 * 4. 训练优化：增加训练轮数，采用 epsilon 衰减策略。
 */
class ImprovedRLScheduler(
    cloudletList: List<Cloudlet>,
    vmList: List<Vm>,
    objectiveWeights: config.ObjectiveWeightsConfig = config.ObjectiveWeightsConfig(),
    private val learningRate: Double = 0.1,
    private val discountFactor: Double = 0.95,
    private val initialExplorationRate: Double = 0.8,
    private val minExplorationRate: Double = 0.05,
    private val episodes: Int = 1000,
    private val random: kotlin.random.Random = kotlin.random.Random(config.DatacenterConfig.DEFAULT_RANDOM_SEED)
) : Scheduler(cloudletList, vmList, objectiveWeights) {

    private val qTable = mutableMapOf<DiscretizedState, DoubleArray>()
    private val avgCloudletLength = cloudletList.map { it.length }.average()

    init {
        Logger.info("初始化改进版强化学习调度器:")
        Logger.info("  - 训练轮数: {}", episodes)
        Logger.info("  - 学习率: {}, 折扣因子: {}", learningRate, discountFactor)
        
        preTrainQTable()
    }

    private fun preTrainQTable() {
        Logger.info("开始改进版 RL 预训练 ({} episodes)...", episodes)
        var currentEpsilon = initialExplorationRate
        val epsilonDecay = (initialExplorationRate - minExplorationRate) / episodes

        repeat(episodes) { episode ->
            trainOneEpisode(currentEpsilon)
            currentEpsilon = maxOf(minExplorationRate, currentEpsilon - epsilonDecay)
            
            if (episode % (episodes / 5) == 0) {
                Logger.debug("进度: {}/{} | Epsilon: %.2f | Q表大小: {}", episode, episodes, currentEpsilon, qTable.size)
            }
        }
        Logger.info("预训练完成，共探索 {} 个离散状态", qTable.size)
    }

    private fun trainOneEpisode(epsilon: Double) {
        val schedule = IntArray(cloudletNum) { -1 }
        val vmCurrentLoads = DoubleArray(vmNum) { 0.0 }

        for (taskIndex in 0 until cloudletNum) {
            val state = getDiscretizedState(vmCurrentLoads, taskIndex)
            
            // epsilon-greedy
            val action = if (random.nextDouble() < epsilon) {
                random.nextInt(vmNum)
            } else {
                getBestAction(state)
            }

            // 执行动作并观察结果
            val task = cloudletList[taskIndex]
            val vm = vmList[action]
            val taskDuration = task.length.toDouble() / vm.mips
            
            val loadBefore = vmCurrentLoads.copyOf()
            vmCurrentLoads[action] += taskDuration
            schedule[taskIndex] = action

            // 计算奖励
            val reward = calculateReward(loadBefore, action, taskDuration)
            
            // 更新 Q 值
            val nextState = if (taskIndex < cloudletNum - 1) getDiscretizedState(vmCurrentLoads, taskIndex + 1) else null
            val nextMaxQ = nextState?.let { getMaxQValue(it) } ?: 0.0
            
            val currentQs = qTable.getOrPut(state) { DoubleArray(vmNum) { 0.0 } }
            currentQs[action] += learningRate * (reward + discountFactor * nextMaxQ - currentQs[action])
        }
    }

    override fun allocate(): IntArray {
        Logger.info("执行改进版 RL 调度方案生成...")
        val schedule = IntArray(cloudletNum)
        val vmCurrentLoads = DoubleArray(vmNum) { 0.0 }

        for (taskIndex in 0 until cloudletNum) {
            val state = getDiscretizedState(vmCurrentLoads, taskIndex)
            val action = getBestAction(state)
            
            schedule[taskIndex] = action
            vmCurrentLoads[action] += cloudletList[taskIndex].length.toDouble() / vmList[action].mips
        }
        return schedule
    }

    /**
     * 状态离散化：将连续的负载和进度转换为有限的组合
     */
    private fun getDiscretizedState(vmLoads: DoubleArray, taskIndex: Int): DiscretizedState {
        // 1. 负载离散化 (0-4级)
        val maxLoad = vmLoads.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val loadLevels = vmLoads.map { (it / maxLoad * 4).toInt() }
        
        // 2. 当前任务长度等级 (0-2级: 短, 中, 长)
        val taskLength = cloudletList.getOrNull(taskIndex)?.length?.toDouble() ?: avgCloudletLength
        val lengthLevel = when {
            taskLength < avgCloudletLength * 0.8 -> 0
            taskLength > avgCloudletLength * 1.2 -> 2
            else -> 1
        }

        // 3. 进度离散化 (0-4级)
        val progressLevel = (taskIndex.toDouble() / cloudletNum * 4).toInt()

        return DiscretizedState(loadLevels, lengthLevel, progressLevel)
    }

    private fun calculateReward(oldLoads: DoubleArray, action: Int, duration: Double): Double {
        val newLoads = oldLoads.copyOf()
        newLoads[action] += duration
        
        val oldVar = calculateVariance(oldLoads)
        val newVar = calculateVariance(newLoads)
        
        // 1. 负载均衡增量奖励 (减少方差则为正)
        val balanceReward = (oldVar - newVar) * 100.0
        
        // 2. 效率奖励：避免将任务分配给负载已经是最高等级的 VM
        val efficiencyReward = if (newLoads[action] > newLoads.average() * 1.5) -5.0 else 2.0
        
        return balanceReward + efficiencyReward
    }

    private fun calculateVariance(loads: DoubleArray): Double {
        if (loads.all { it == 0.0 }) return 0.0
        val avg = loads.average()
        return loads.map { (it - avg) * (it - avg) }.average()
    }

    private fun getBestAction(state: DiscretizedState): Int {
        val qs = qTable[state] ?: return random.nextInt(vmNum)
        var bestAction = 0
        var maxQ = qs[0]
        for (i in 1 until vmNum) {
            if (qs[i] > maxQ) {
                maxQ = qs[i]
                bestAction = i
            }
        }
        return bestAction
    }

    private fun getMaxQValue(state: DiscretizedState): Double {
        return qTable[state]?.maxOrNull() ?: 0.0
    }

    private data class DiscretizedState(
        val loadLevels: List<Int>,
        val taskLengthLevel: Int,
        val progressLevel: Int
    )
}
