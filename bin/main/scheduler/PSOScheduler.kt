package scheduler

import datacenter.ObjectiveFunction
import datacenter.SchedulerObjectiveFunction
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import util.Logger
import java.util.*

/**
 * 粒子群优化算法 (Particle Swarm Optimization) - 优化版本
 * 使用一维数组存储所有粒子数据，提高内存访问效率
 */
internal class PSO(
    private val optFunction: ObjectiveFunction,
    private val population: Int,
    private val lb: Double,
    private val ub: Double,
    private val dim: Int,
    private val maxIter: Int,
    private val random: Random
) {

    // 使用一维数组存储所有数据，提高内存局部性
    private val positions = DoubleArray(population * dim)
    private val velocities = DoubleArray(population * dim)
    private val pBest = DoubleArray(population * dim)
    private val pBestScore = DoubleArray(population) { Double.POSITIVE_INFINITY }
    private val gBest = DoubleArray(dim)
    private var gBestScore = Double.POSITIVE_INFINITY

    companion object {
        private const val W_MAX = 0.9
        private const val W_MIN = 0.2
        private const val C1 = 2.0
        private const val C2 = 2.0
    }
    
    init {
        initPopulation()
    }
    
    private fun initPopulation() {
        for (i in 0 until population) {
            for (j in 0 until dim) {
                val index = i * dim + j
                positions[index] = lb + (ub - lb) * random.nextDouble()
                velocities[index] = random.nextDouble()
                pBest[index] = positions[index]  // 初始化个体最优位置
            }
            adjustPositions(i)
        }
    }

    // 获取粒子i的第j维位置
    private fun getPosition(particle: Int, dimension: Int): Double {
        return positions[particle * dim + dimension]
    }

    // 设置粒子i的第j维位置
    private fun setPosition(particle: Int, dimension: Int, value: Double) {
        positions[particle * dim + dimension] = value
    }

    // 获取粒子i的第j维速度
    private fun getVelocity(particle: Int, dimension: Int): Double {
        return velocities[particle * dim + dimension]
    }

    // 设置粒子i的第j维速度
    private fun setVelocity(particle: Int, dimension: Int, value: Double) {
        velocities[particle * dim + dimension] = value
    }

    // 获取粒子i的第j维个体最优位置
    private fun getPBest(particle: Int, dimension: Int): Double {
        return pBest[particle * dim + dimension]
    }

    // 设置粒子i的第j维个体最优位置
    private fun setPBest(particle: Int, dimension: Int, value: Double) {
        pBest[particle * dim + dimension] = value
    }
    
    private fun adjustPositions(agentIndex: Int) {
        for (j in 0 until dim) {
            val index = agentIndex * dim + j
            positions[index] = positions[index].round()
            when {
                positions[index] < lb -> positions[index] = lb
                positions[index] > ub -> positions[index] = ub
            }
        }
    }
    
    private fun evaluate(particle: Int): Double {
        // 直接使用一维数组，避免创建临时数组
        val params = IntArray(dim)
        val baseIndex = particle * dim
        for (j in 0 until dim) {
            params[j] = positions[baseIndex + j].round().toInt()
        }
        return optFunction.calculate(params)
    }

    // 评估指定位置的适应度值
    private fun evaluatePosition(position: DoubleArray): Double {
        val params = IntArray(dim)
        for (j in 0 until dim) {
            params[j] = position[j].round().toInt()
        }
        return optFunction.calculate(params)
    }
    
    fun execute(): IntArray {
        val vMax = (ub - lb) * 0.2  // 速度最大值是固定的

        for (t in 0 until maxIter) {
            val w = W_MAX - t * (W_MAX - W_MIN) / maxIter.toDouble()

            // 评估并更新最优解
            for (i in 0 until population) {
                // 边界处理
                for (j in 0 until dim) {
                    val posIndex = i * dim + j
                    when {
                        positions[posIndex] > ub -> positions[posIndex] = ub
                        positions[posIndex] < lb -> positions[posIndex] = lb
                    }
                }

                val fitness = evaluate(i)

                // 更新个体最优
                if (fitness < pBestScore[i]) {
                    pBestScore[i] = fitness
                    // 复制当前粒子位置到个体最优
                    val baseIndex = i * dim
                    for (j in 0 until dim) {
                        pBest[baseIndex + j] = positions[baseIndex + j]
                    }
                }

                // 更新全局最优
                if (fitness < gBestScore) {
                    gBestScore = fitness
                    // 复制当前粒子位置到全局最优
                    val baseIndex = i * dim
                    for (j in 0 until dim) {
                        gBest[j] = positions[baseIndex + j]
                    }
                }
            }

            // 更新速度和位置
            for (i in 0 until population) {
                val baseIndex = i * dim
                for (j in 0 until dim) {
                    val index = baseIndex + j
                    val r1 = random.nextDouble()
                    val r2 = random.nextDouble()

                    velocities[index] = w * velocities[index] +
                            C1 * r1 * (pBest[index] - positions[index]) +
                            C2 * r2 * (gBest[j] - positions[index])

                    // 速度钳制
                    when {
                        velocities[index] > vMax -> velocities[index] = vMax
                        velocities[index] < -vMax -> velocities[index] = -vMax
                    }

                    positions[index] += velocities[index]
                }
                adjustPositions(i)
            }
        }

        return gBest.map { it.round().toInt() }.toIntArray()
    }
}

/**
 * PSO 调度器
 */
class PSOScheduler(
    cloudletList: List<Cloudlet>,
    vmList: List<Vm>,
    objectiveWeights: config.ObjectiveWeightsConfig = config.ObjectiveWeightsConfig(),
    private val population: Int = 30,
    private val maxIter: Int = 100,
    private val random: Random = Random(config.DatacenterConfig.DEFAULT_RANDOM_SEED)
) : Scheduler(cloudletList, vmList, objectiveWeights) {
    
    private val pso: PSO
    
    init {
        val objFunc = objectiveFunction as SchedulerObjectiveFunction
        pso = PSO(
            optFunction = objFunc,
            population = population,
            lb = 0.0,
            ub = (vmNum - 1).toDouble(),
            dim = cloudletNum,
            maxIter = maxIter,
            random = random
        )
        Logger.debug("使用 PSO (粒子群优化) 调度器")
    }
    
    override fun allocate(): IntArray {
        return pso.execute()
    }
}

private fun Double.round() = kotlin.math.round(this)
