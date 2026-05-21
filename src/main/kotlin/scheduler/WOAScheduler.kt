package scheduler

import datacenter.ObjectiveFunction
import datacenter.SchedulerObjectiveFunction
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import util.Logger
import java.util.*

/**
 * 鲸鱼优化算法 (Whale Optimization Algorithm) - 优化版本
 * 使用一维数组存储所有鲸鱼位置，提高内存访问效率
 */
internal class WOA(
    private val optFunction: ObjectiveFunction,
    private val population: Int,
    private val lb: Double,
    private val ub: Double,
    private val dim: Int,
    private val maxIter: Int,
    private val random: Random
) {
    // 使用一维数组存储所有鲸鱼位置，提高内存局部性
    private val positions = DoubleArray(population * dim)
    private val optimalPos = DoubleArray(dim)
    private val codec = AssignmentVectorCodec(dim, lb.toInt(), ub.toInt())
    private var optimalScore = Double.POSITIVE_INFINITY
    
    init {
        initPopulation()
    }
    
    private fun initPopulation() {
        for (i in 0 until population) {
            val baseIndex = i * dim
            for (j in 0 until dim) {
                positions[baseIndex + j] = lb + (ub - lb) * random.nextDouble()
            }
            adjustPositions(i)
            val fitness = evaluate(i)
            if (fitness < optimalScore) {
                optimalScore = fitness
                // 复制当前鲸鱼位置到最优位置
                for (j in 0 until dim) {
                    optimalPos[j] = positions[baseIndex + j]
                }
            }
        }
    }

    private fun adjustPositions(agentIndex: Int) {
        codec.clampRoundInPlace(positions, agentIndex * dim)
    }

    private fun evaluate(whale: Int): Double {
        return codec.evaluate(positions, whale * dim, optFunction)
    }
    
    fun execute(): IntArray {
        for (t in 0 until maxIter) {
            val a = 2.0 - t * (2.0 / maxIter.toDouble()) // a 从 2 线性递减到 0

            for (i in 0 until population) {
                val r1 = random.nextDouble()
                val r2 = random.nextDouble()
                val A = 2.0 * a * r1 - a
                val C = 2.0 * r2
                val b = 1.0
                val l = (random.nextDouble() * 2.0) - 1.0

                val p = random.nextDouble()
                val baseIndex = i * dim

                for (j in 0 until dim) {
                    val index = baseIndex + j
                    when {
                        p < 0.5 -> {
                            if (Math.abs(A) >= 1) {
                                // 随机搜索
                                val randLeaderIndex = random.nextInt(population) * dim + j
                                val Xrand = positions[randLeaderIndex]
                                positions[index] = Xrand - A * Math.abs(C * Xrand - positions[index])
                            } else {
                                // 包围猎物
                                positions[index] = optimalPos[j] - A * Math.abs(C * optimalPos[j] - positions[index])
                            }
                        }
                        else -> {
                            // 螺旋更新位置
                            val distance2Leader = Math.abs(optimalPos[j] - positions[index])
                            positions[index] = distance2Leader * Math.exp(b * l) * Math.cos(l * 2 * Math.PI) + optimalPos[j]
                        }
                    }
                }

                adjustPositions(i)
                val fitness = evaluate(i)

                if (fitness < optimalScore) {
                    optimalScore = fitness
                    // 复制当前鲸鱼位置到最优位置
                    for (j in 0 until dim) {
                        optimalPos[j] = positions[baseIndex + j]
                    }
                }
            }
        }

        return codec.toAllocation(optimalPos)
    }
}

/**
 * WOA 调度器
 */
class WOAScheduler(
    cloudletList: List<Cloudlet>,
    vmList: List<Vm>,
    objectiveWeights: config.ObjectiveWeightsConfig = config.ObjectiveWeightsConfig(),
    private val population: Int = 30,
    private val maxIter: Int = 100,
    private val random: Random = Random(config.DatacenterConfig.DEFAULT_RANDOM_SEED)
) : Scheduler(cloudletList, vmList, objectiveWeights) {
    
    private val woa: WOA
    
    init {
        val objFunc = objectiveFunction as SchedulerObjectiveFunction
        woa = WOA(
            optFunction = objFunc,
            population = population,
            lb = 0.0,
            ub = (vmNum - 1).toDouble(),
            dim = cloudletNum,
            maxIter = maxIter,
            random = random
        )
        Logger.debug("使用 WOA (鲸鱼优化) 调度器")
    }
    
    override fun allocate(): IntArray {
        return woa.execute()
    }
}
