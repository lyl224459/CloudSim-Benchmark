package scheduler

import datacenter.SchedulerObjectiveFunction
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import util.Logger
import java.util.Random

/**
 * 鲸鱼优化算法 (Whale Optimization Algorithm) - 优化版本
 * 使用一维数组存储所有鲸鱼位置，提高内存访问效率
 */
internal class WOA(
    private val runtime: OptimizerRuntime,
    private val searchSpace: AssignmentSearchSpace,
) {
    private val optFunction = runtime.optFunction
    private val population = runtime.population
    private val lb = searchSpace.lb
    private val ub = searchSpace.ub
    private val dim = searchSpace.dim
    private val maxIter = runtime.maxIter
    private val random = runtime.random

    // 使用一维数组存储所有鲸鱼位置，提高内存局部性
    private val positions = DoubleArray(population * dim)
    private val optimalPos = DoubleArray(dim)
    private val codec = AssignmentVectorCodec(dim, lb.toInt(), ub.toInt())
    private var optimalScore = Double.POSITIVE_INFINITY

    companion object {
        private const val LINEAR_COEFFICIENT = 2.0
        private const val SPIRAL_SHAPE = 1.0
        private const val EXPLOITATION_PROBABILITY = 0.5
        private const val SPIRAL_MIN = -1.0
    }

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

    private fun evaluate(whale: Int): Double = codec.evaluate(positions, whale * dim, optFunction)

    fun execute(): IntArray {
        for (t in 0 until maxIter) {
            val a = LINEAR_COEFFICIENT - t * (LINEAR_COEFFICIENT / maxIter.toDouble())
            for (i in 0 until population) {
                updateWhale(i, a)
            }
        }

        return codec.toAllocation(optimalPos)
    }

    private fun updateWhale(
        whale: Int,
        a: Double,
    ) {
        val motion =
            WhaleMotion(
                coefficientA = LINEAR_COEFFICIENT * a * random.nextDouble() - a,
                coefficientC = LINEAR_COEFFICIENT * random.nextDouble(),
                spiralDistance = random.nextDouble() * LINEAR_COEFFICIENT + SPIRAL_MIN,
                probability = random.nextDouble(),
            )
        val baseIndex = whale * dim
        for (j in 0 until dim) {
            updateWhaleDimension(baseIndex, j, motion)
        }
        adjustPositions(whale)
        updateOptimalPosition(whale)
    }

    private fun updateWhaleDimension(
        baseIndex: Int,
        dimension: Int,
        motion: WhaleMotion,
    ) {
        val index = baseIndex + dimension
        positions[index] =
            if (motion.probability < EXPLOITATION_PROBABILITY) {
                encirclingPosition(index, dimension, motion.coefficientA, motion.coefficientC)
            } else {
                spiralPosition(index, dimension, motion.spiralDistance)
            }
    }

    private fun encirclingPosition(
        index: Int,
        dimension: Int,
        coefficientA: Double,
        coefficientC: Double,
    ): Double =
        if (Math.abs(coefficientA) >= SPIRAL_SHAPE) {
            val randomLeaderPosition = positions[random.nextInt(population) * dim + dimension]
            randomLeaderPosition - coefficientA * Math.abs(coefficientC * randomLeaderPosition - positions[index])
        } else {
            optimalPos[dimension] - coefficientA * Math.abs(coefficientC * optimalPos[dimension] - positions[index])
        }

    private fun spiralPosition(
        index: Int,
        dimension: Int,
        spiralDistance: Double,
    ): Double {
        val distance2Leader = Math.abs(optimalPos[dimension] - positions[index])
        return distance2Leader *
            Math.exp(SPIRAL_SHAPE * spiralDistance) *
            Math.cos(spiralDistance * LINEAR_COEFFICIENT * Math.PI) +
            optimalPos[dimension]
    }

    private fun updateOptimalPosition(whale: Int) {
        val fitness = evaluate(whale)
        if (fitness < optimalScore) {
            optimalScore = fitness
            val baseIndex = whale * dim
            for (j in 0 until dim) {
                optimalPos[j] = positions[baseIndex + j]
            }
        }
    }
}

private data class WhaleMotion(
    val coefficientA: Double,
    val coefficientC: Double,
    val spiralDistance: Double,
    val probability: Double,
)

/**
 * WOA 调度器
 */
class WOAScheduler(
    cloudletList: List<Cloudlet>,
    vmList: List<Vm>,
    objectiveWeights: config.ObjectiveWeightsConfig = config.ObjectiveWeightsConfig(),
    private val population: Int = 30,
    private val maxIter: Int = 100,
    private val random: Random = Random(config.DatacenterConfig.DEFAULT_RANDOM_SEED),
) : Scheduler(cloudletList, vmList, objectiveWeights) {
    private val woa: WOA

    init {
        val objFunc = objectiveFunction as SchedulerObjectiveFunction
        woa =
            WOA(
                runtime = OptimizerRuntime(objFunc, population, maxIter, random),
                searchSpace = AssignmentSearchSpace(0.0, (vmNum - 1).toDouble(), cloudletNum),
            )
        Logger.debug("使用 WOA (鲸鱼优化) 调度器")
    }

    override fun allocate(): IntArray = woa.execute()
}
