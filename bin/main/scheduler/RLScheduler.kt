package scheduler

import datacenter.ObjectiveFunction
import datacenter.SchedulerObjectiveFunction
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import util.Logger
import kotlin.math.min
import kotlin.random.Random

/**
 * 强化学习调度器 (Reinforcement Learning Scheduler)
 * 使用Q-learning算法进行智能任务调度决策
 *
 * 状态空间：各个VM的负载情况 + 调度进度
 * 动作空间：选择哪个VM执行任务
 * 奖励函数：基于负载均衡和任务完成效率
 */
class RLScheduler(
    cloudletList: List<Cloudlet>,
    vmList: List<Vm>,
    objectiveWeights: config.ObjectiveWeightsConfig = config.ObjectiveWeightsConfig(),
    private val learningRate: Double = 0.1,      // 学习率 α
    private val discountFactor: Double = 0.9,    // 折扣因子 γ
    private val explorationRate: Double = 0.1,   // 探索率 ε
    private val episodes: Int = 100,             // 训练轮数
    private val random: kotlin.random.Random = kotlin.random.Random(config.DatacenterConfig.DEFAULT_RANDOM_SEED)
) : Scheduler(cloudletList, vmList, objectiveWeights) {

    // Q-table: 状态 -> 动作 -> Q值
    private val qTable = mutableMapOf<State, MutableMap<Action, Double>>()

    // 动作空间：选择哪个VM
    private val actions = (0 until vmNum).map { Action(it) }

    init {
        Logger.info("初始化强化学习调度器:")
        Logger.info("  - 状态空间大小: {}", calculateStateSpaceSize())
        Logger.info("  - 动作空间大小: {}", vmNum)
        Logger.info("  - 学习率: {}", learningRate)
        Logger.info("  - 折扣因子: {}", discountFactor)
        Logger.info("  - 探索率: {}", explorationRate)
        Logger.info("  - 训练轮数: {}", episodes)

        // 预训练Q-table
        preTrainQTable()
    }

    /**
     * 预训练Q-table，建立基本的决策知识
     */
    private fun preTrainQTable() {
        Logger.info("开始预训练Q-table...")

        repeat(episodes) { episode ->
            if (episode % 20 == 0) {
                Logger.debug("预训练进度: {}/{}", episode, episodes)
            }

            // 模拟一次完整的调度过程
            trainOneEpisode()
        }

        Logger.info("Q-table预训练完成，包含 {} 个状态", qTable.size)
    }

    /**
     * 训练一个episode（完整的调度过程）
     */
    private fun trainOneEpisode() {
        val schedule = IntArray(cloudletNum)

        // 模拟调度过程
        for (taskIndex in 0 until cloudletNum) {
            val currentState = getStateForSchedule(schedule, taskIndex)

            // ε-greedy策略选择动作
            val action = if (random.nextDouble() < explorationRate) {
                // 探索：随机选择
                actions.random(random)
            } else {
                // 利用：选择Q值最大的动作
                getBestAction(currentState)
            }

            // 执行动作
            schedule[taskIndex] = action.vmIndex

            // 计算奖励和Q-learning更新
            if (taskIndex < cloudletNum - 1) {
                val reward = calculateReward(currentState, action)
                val nextState = getStateForSchedule(schedule, taskIndex + 1)
                val nextBestQ = getMaxQValue(nextState)

                val currentQ = getQValue(currentState, action)
                val newQ = currentQ + learningRate * (reward + discountFactor * nextBestQ - currentQ)

                setQValue(currentState, action, newQ)
            }
        }

        // 最后一步的奖励（完成调度）
        val finalState = getStateForSchedule(schedule, cloudletNum)
        val finalAction = Action(schedule[cloudletNum - 1])
        val completionReward = 100.0 // 完成调度的奖励

        val currentQ = getQValue(finalState, finalAction)
        val newQ = currentQ + learningRate * completionReward
        setQValue(finalState, finalAction, newQ)
    }

    /**
     * 执行任务调度
     */
    override fun allocate(): IntArray {
        Logger.info("开始强化学习调度...")

        val schedule = IntArray(cloudletNum)

        // 使用训练好的Q-table进行调度
        for (taskIndex in 0 until cloudletNum) {
            val currentState = getStateForSchedule(schedule, taskIndex)

            // 总是选择最优动作（利用阶段）
            val bestAction = getBestAction(currentState)

            schedule[taskIndex] = bestAction.vmIndex
            Logger.debug("任务 {} 分配到VM {}", taskIndex, bestAction.vmIndex)
        }

        Logger.info("强化学习调度完成")
        return schedule
    }

    /**
     * 根据当前调度状态获取状态表示
     */
    private fun getStateForSchedule(schedule: IntArray, currentTaskIndex: Int): State {
        // 计算各个VM的当前负载
        val vmLoads = DoubleArray(vmNum)

        for (i in 0 until currentTaskIndex) {
            val vmIndex = schedule[i]
            if (vmIndex >= 0 && vmIndex < vmNum) {
                vmLoads[vmIndex] += 1.0 // 每个任务增加1单位负载
            }
        }

        // 归一化负载（0.0-1.0）
        val maxLoad = vmLoads.maxOrNull() ?: 1.0
        if (maxLoad > 0) {
            for (i in vmLoads.indices) {
                vmLoads[i] /= maxLoad
            }
        }

        // 调度进度（0.0-1.0）
        val progress = currentTaskIndex.toDouble() / cloudletNum

        return State(vmLoads, progress)
    }

    /**
     * 计算奖励函数
     */
    private fun calculateReward(state: State, action: Action): Double {
        // 模拟执行动作后的负载变化
        val newVmLoads = state.vmLoads.copyOf()
        newVmLoads[action.vmIndex] = minOf(1.0, newVmLoads[action.vmIndex] + 0.2) // 增加负载（归一化）

        // 计算负载均衡奖励（越均衡奖励越高）
        val meanLoad = newVmLoads.average()
        val variance = newVmLoads.map { (it - meanLoad) * (it - meanLoad) }.average()
        val balanceReward = (1.0 - variance) * 10.0 // 0-10的奖励

        // 避免过载惩罚
        val overloadPenalty = if (newVmLoads[action.vmIndex] > 0.8) -5.0 else 0.0

        // 轻微的动作多样性奖励（避免总是选同一VM）
        val actionDiversityBonus = if (newVmLoads.count { it > 0.1 } > 1) 1.0 else 0.0

        return balanceReward + overloadPenalty + actionDiversityBonus
    }

    /**
     * 获取最优动作
     */
    private fun getBestAction(state: State): Action {
        return actions.maxByOrNull { getQValue(state, it) } ?: actions.first()
    }

    /**
     * 获取最大Q值
     */
    private fun getMaxQValue(state: State): Double {
        return actions.maxOf { getQValue(state, it) }
    }

    /**
     * 获取Q值
     */
    private fun getQValue(state: State, action: Action): Double {
        return qTable.getOrPut(state) { mutableMapOf() }.getOrDefault(action, 0.0)
    }

    /**
     * 设置Q值
     */
    private fun setQValue(state: State, action: Action, value: Double) {
        qTable.getOrPut(state) { mutableMapOf() }[action] = value
    }

    /**
     * 计算状态空间大小（用于信息显示）
     */
    private fun calculateStateSpaceSize(): Int {
        // 简化的估算：离散化负载状态
        val loadStates = 5 // 每个VM的负载分为5个等级
        val progressStates = 10 // 进度分为10个阶段
        return loadStates * vmNum * progressStates
    }

    /**
     * 状态表示
     */
    private data class State(
        val vmLoads: DoubleArray,
        val progress: Double // 调度进度 (0.0-1.0)
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as State

            if (!vmLoads.contentEquals(other.vmLoads)) return false
            if (progress != other.progress) return false

            return true
        }

        override fun hashCode(): Int {
            var result = vmLoads.contentHashCode()
            result = 31 * result + progress.hashCode()
            return result
        }

        override fun toString(): String {
            return "State(vmLoads=${vmLoads.contentToString()}, progress=%.2f)".format(progress)
        }
    }

    /**
     * 动作表示
     */
    private data class Action(val vmIndex: Int) {
        override fun toString(): String = "Action(vm=$vmIndex)"
    }
}