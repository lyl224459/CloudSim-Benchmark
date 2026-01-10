package scheduler

import datacenter.ObjectiveFunction
import datacenter.SchedulerObjectiveFunction
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import util.Logger
import java.util.*

/**
 * 灰狼优化算法 (Grey Wolf Optimizer) - 优化版本
 * 使用一维数组存储所有狼的位置，提高内存访问效率
 */
private class GWO(
    private val optFunction: ObjectiveFunction,
    private val population: Int,
    private val lb: Double,
    private val ub: Double,
    private val dim: Int,
    private val maxIter: Int,
    private val random: Random
) {
    // 使用一维数组存储所有狼的位置，提高内存局部性
    private val positions = DoubleArray(population * dim)
    
    private val alphaPos = DoubleArray(dim)
    private var alphaScore = Double.POSITIVE_INFINITY
    
    private val betaPos = DoubleArray(dim)
    private var betaScore = Double.POSITIVE_INFINITY
    
    private val deltaPos = DoubleArray(dim)
    private var deltaScore = Double.POSITIVE_INFINITY
    
    init {
        initializePositions()
    }
    
    private fun initializePositions() {
        for (i in 0 until population) {
            val baseIndex = i * dim
            for (j in 0 until dim) {
                positions[baseIndex + j] = lb + (ub - lb) * random.nextDouble()
            }
            adjustAndEvaluate(i)
        }
    }

    // 获取狼i的第j维位置
    private fun getPosition(wolf: Int, dimension: Int): Double {
        return positions[wolf * dim + dimension]
    }

    // 设置狼i的第j维位置
    private fun setPosition(wolf: Int, dimension: Int, value: Double) {
        positions[wolf * dim + dimension] = value
    }
    
    private fun adjustAndEvaluate(agentIndex: Int) {
        // 离散化为整数
        val baseIndex = agentIndex * dim
        for (j in 0 until dim) {
            val index = baseIndex + j
            positions[index] = positions[index].round()
            when {
                positions[index] < lb -> positions[index] = lb
                positions[index] > ub -> positions[index] = ub
            }
        }

        val params = IntArray(dim)
        for (j in 0 until dim) {
            params[j] = positions[baseIndex + j].toInt()
        }
        val fitness = optFunction.calculate(params)

        // 更新 Alpha, Beta, Delta
        when {
            fitness < alphaScore -> {
                deltaScore = betaScore
                betaPos.copyInto(deltaPos)
                betaScore = alphaScore
                alphaPos.copyInto(betaPos)
                alphaScore = fitness
                // 复制当前狼位置到alpha位置
                for (j in 0 until dim) {
                    alphaPos[j] = positions[baseIndex + j]
                }
            }
            fitness < betaScore -> {
                deltaScore = betaScore
                betaPos.copyInto(deltaPos)
                betaScore = fitness
                // 复制当前狼位置到beta位置
                for (j in 0 until dim) {
                    betaPos[j] = positions[baseIndex + j]
                }
            }
            fitness < deltaScore -> {
                deltaScore = fitness
                // 复制当前狼位置到delta位置
                for (j in 0 until dim) {
                    deltaPos[j] = positions[baseIndex + j]
                }
            }
        }
    }
    
    fun execute(): IntArray {
        for (t in 0 until maxIter) {
            val a = 2.0 - t * (2.0 / maxIter.toDouble()) // a 从 2 线性递减到 0

            for (i in 0 until population) {
                val baseIndex = i * dim
                for (j in 0 until dim) {
                    val index = baseIndex + j
                    val r1 = random.nextDouble()
                    val r2 = random.nextDouble()

                    val A1 = 2.0 * a * r1 - a
                    val C1 = 2.0 * r2
                    val D_alpha = Math.abs(C1 * alphaPos[j] - positions[index])
                    val X1 = alphaPos[j] - A1 * D_alpha

                    val r1_beta = random.nextDouble()
                    val r2_beta = random.nextDouble()
                    val A2 = 2.0 * a * r1_beta - a
                    val C2 = 2.0 * r2_beta
                    val D_beta = Math.abs(C2 * betaPos[j] - positions[index])
                    val X2 = betaPos[j] - A2 * D_beta

                    val r1_delta = random.nextDouble()
                    val r2_delta = random.nextDouble()
                    val A3 = 2.0 * a * r1_delta - a
                    val C3 = 2.0 * r2_delta
                    val D_delta = Math.abs(C3 * deltaPos[j] - positions[index])
                    val X3 = deltaPos[j] - A3 * D_delta

                    positions[index] = (X1 + X2 + X3) / 3.0
                }

                adjustAndEvaluate(i)
            }
        }

        return alphaPos.map { it.round().toInt() }.toIntArray()
    }
}

/**
 * GWO 调度器
 */
class GWOScheduler(
    cloudletList: List<Cloudlet>,
    vmList: List<Vm>,
    objectiveWeights: config.ObjectiveWeightsConfig = config.ObjectiveWeightsConfig(),
    private val population: Int = 30,
    private val maxIter: Int = 100,
    private val random: Random = Random(config.DatacenterConfig.DEFAULT_RANDOM_SEED)
) : Scheduler(cloudletList, vmList, objectiveWeights) {
    
    private val gwo: GWO
    
    init {
        val objFunc = objectiveFunction as SchedulerObjectiveFunction
        gwo = GWO(
            optFunction = objFunc,
            population = population,
            lb = 0.0,
            ub = (vmNum - 1).toDouble(),
            dim = cloudletNum,
            maxIter = maxIter,
            random = random
        )
        Logger.debug("使用 GWO (灰狼优化) 调度器")
    }
    
    override fun allocate(): IntArray {
        return gwo.execute()
    }
}

private fun Double.round() = kotlin.math.round(this)
