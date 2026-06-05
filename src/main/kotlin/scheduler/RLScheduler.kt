package scheduler

import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import util.Logger
import kotlin.random.Random

private const val DEFAULT_RL_LEARNING_RATE = 0.1
private const val DEFAULT_RL_DISCOUNT_FACTOR = 0.9
private const val DEFAULT_RL_EXPLORATION_RATE = 0.1
private const val DEFAULT_RL_EPISODES = 100
private const val PRETRAIN_LOG_INTERVAL = 20
private const val TASK_LOAD_INCREMENT = 1.0
private const val NORMALIZED_TASK_LOAD_INCREMENT = 0.2
private const val COMPLETION_REWARD = 100.0
private const val BALANCE_REWARD_SCALE = 10.0
private const val OVERLOAD_THRESHOLD = 0.8
private const val OVERLOAD_PENALTY = -5.0
private const val NO_REWARD = 0.0
private const val ACTION_DIVERSITY_THRESHOLD = 0.1
private const val MIN_BUSY_VM_COUNT_FOR_DIVERSITY = 1
private const val ACTION_DIVERSITY_BONUS = 1.0
private const val LOAD_STATE_LEVELS = 5
private const val PROGRESS_STATE_LEVELS = 10

/**
 * 强化学习调度器 (Reinforcement Learning Scheduler)
 * 使用Q-learning算法进行智能任务调度决策
 *
 * 状态空间：各个VM的负载情况 + 调度进度
 * 动作空间：选择哪个VM执行任务
 * 奖励函数：基于负载均衡和任务完成效率
 */
@Suppress("LongParameterList")
class RLScheduler(
    cloudletList: List<Cloudlet>,
    vmList: List<Vm>,
    objectiveWeights: config.ObjectiveWeightsConfig = config.ObjectiveWeightsConfig(),
    private val learningRate: Double = DEFAULT_RL_LEARNING_RATE, // 学习率 α
    private val discountFactor: Double = DEFAULT_RL_DISCOUNT_FACTOR, // 折扣因子 γ
    private val explorationRate: Double = DEFAULT_RL_EXPLORATION_RATE, // 探索率 ε
    private val episodes: Int = DEFAULT_RL_EPISODES, // 训练轮数
    private val random: kotlin.random.Random = kotlin.random.Random(config.DatacenterConfig.DEFAULT_RANDOM_SEED),
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
            if (episode % PRETRAIN_LOG_INTERVAL == 0) {
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
            val action =
                if (random.nextDouble() < explorationRate) {
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
        val currentQ = getQValue(finalState, finalAction)
        val newQ = currentQ + learningRate * COMPLETION_REWARD
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
    private fun getStateForSchedule(
        schedule: IntArray,
        currentTaskIndex: Int,
    ): State {
        // 计算各个VM的当前负载
        val vmLoads = DoubleArray(vmNum)

        for (i in 0 until currentTaskIndex) {
            val vmIndex = schedule[i]
            if (vmIndex >= 0 && vmIndex < vmNum) {
                vmLoads[vmIndex] += TASK_LOAD_INCREMENT
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
    private fun calculateReward(
        state: State,
        action: Action,
    ): Double {
        // 模拟执行动作后的负载变化
        val newVmLoads = state.vmLoads.copyOf()
        newVmLoads[action.vmIndex] =
            minOf(
                TASK_LOAD_INCREMENT,
                newVmLoads[action.vmIndex] + NORMALIZED_TASK_LOAD_INCREMENT,
            )

        // 计算负载均衡奖励（越均衡奖励越高）
        val meanLoad = newVmLoads.average()
        val variance = newVmLoads.map { (it - meanLoad) * (it - meanLoad) }.average()
        val balanceReward = (TASK_LOAD_INCREMENT - variance) * BALANCE_REWARD_SCALE

        // 避免过载惩罚
        val overloadPenalty =
            if (newVmLoads[action.vmIndex] > OVERLOAD_THRESHOLD) {
                OVERLOAD_PENALTY
            } else {
                NO_REWARD
            }

        // 轻微的动作多样性奖励（避免总是选同一VM）
        val actionDiversityBonus =
            if (newVmLoads.count { it > ACTION_DIVERSITY_THRESHOLD } > MIN_BUSY_VM_COUNT_FOR_DIVERSITY) {
                ACTION_DIVERSITY_BONUS
            } else {
                NO_REWARD
            }

        return balanceReward + overloadPenalty + actionDiversityBonus
    }

    /**
     * 获取最优动作
     */
    private fun getBestAction(state: State): Action = actions.maxByOrNull { getQValue(state, it) } ?: actions.first()

    /**
     * 获取最大Q值
     */
    private fun getMaxQValue(state: State): Double = actions.maxOf { getQValue(state, it) }

    /**
     * 获取Q值
     */
    private fun getQValue(
        state: State,
        action: Action,
    ): Double = qTable.getOrPut(state) { mutableMapOf() }.getOrDefault(action, 0.0)

    /**
     * 设置Q值
     */
    private fun setQValue(
        state: State,
        action: Action,
        value: Double,
    ) {
        qTable.getOrPut(state) { mutableMapOf() }[action] = value
    }

    /**
     * 计算状态空间大小（用于信息显示）
     */
    private fun calculateStateSpaceSize(): Int {
        // 简化的估算：离散化负载状态
        return LOAD_STATE_LEVELS * vmNum * PROGRESS_STATE_LEVELS
    }

    /**
     * 状态表示
     */
    private data class State(
        val vmLoads: DoubleArray,
        val progress: Double, // 调度进度 (0.0-1.0)
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

        override fun toString(): String = "State(vmLoads=${vmLoads.contentToString()}, progress=%.2f)".format(progress)
    }

    /**
     * 动作表示
     */
    private data class Action(
        val vmIndex: Int,
    ) {
        override fun toString(): String = "Action(vm=$vmIndex)"
    }
}
