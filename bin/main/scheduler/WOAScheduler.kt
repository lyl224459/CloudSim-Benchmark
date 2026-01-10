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

    // 获取鲸鱼i的第j维位置
    private fun getPosition(whale: Int, dimension: Int): Double {
        return positions[whale * dim + dimension]
    }

    // 设置鲸鱼i的第j维位置
    private fun setPosition(whale: Int, dimension: Int, value: Double) {
        positions[whale * dim + dimension] = value
    }
    
    private fun adjustPositions(agentIndex: Int) {
        val baseIndex = agentIndex * dim
        for (j in 0 until dim) {
            val index = baseIndex + j
            positions[index] = positions[index].round()
            when {
                positions[index] < lb -> positions[index] = lb
                positions[index] > ub -> positions[index] = ub
            }
        }
    }

    private fun evaluate(whale: Int): Double {
        // 直接使用一维数组，避免创建临时数组
        val params = IntArray(dim)
        val baseIndex = whale * dim
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

        return optimalPos.map { it.round().toInt() }.toIntArray()
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

private fun Double.round() = kotlin.math.round(this)
