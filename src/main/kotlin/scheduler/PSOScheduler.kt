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
    private val codec = AssignmentVectorCodec(dim, lb.toInt(), ub.toInt())
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

    private fun adjustPositions(agentIndex: Int) {
        codec.clampRoundInPlace(positions, agentIndex * dim)
    }
    
    private fun evaluate(particle: Int): Double {
        return codec.evaluate(positions, particle * dim, optFunction)
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

        return codec.toAllocation(gBest)
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
