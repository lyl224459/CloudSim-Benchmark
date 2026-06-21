package scheduler.batch

import datacenter.ObjectiveFunction
import datacenter.SchedulerObjectiveFunction
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import scheduler.AssignmentVectorCodec
import util.Logger
import java.util.Random
import kotlin.math.min

private const val DEFAULT_HHO_POPULATION = 30
private const val DEFAULT_HHO_MAX_ITER = 100
private const val HHO_BOUND_LOWER = 0.0
private const val HHO_PROBABILITY_SWITCH = 0.5
private const val HHO_EXPLORATION_THRESHOLD = 1.0
private const val HHO_SOFT_BESIEGE_THRESHOLD = 0.5
private const val HHO_ENERGY_SCALE = 2.0
private const val DEBUG_ALLOCATION_PREVIEW_SIZE = 10

private data class HhoParameters(
    val population: Int,
    val lowerBound: Double,
    val upperBound: Double,
    val dimensions: Int,
    val maxIterations: Int,
)

private data class HhoRandomSamples(
    val q: Double,
    val r1: Double,
    val r2: Double,
    val r3: Double,
)

/**
 * 哈里斯鹰优化算法 (Harris Hawks Optimization) - 优化版本
 * 使用一维数组存储所有鹰的位置，提高内存访问效率
 */
private class HHO(
    private val optFunction: ObjectiveFunction,
    parameters: HhoParameters,
    private val random: Random,
) {
    private val population = parameters.population
    private val lb = parameters.lowerBound
    private val ub = parameters.upperBound
    private val dim = parameters.dimensions
    private val maxIter = parameters.maxIterations

    // 使用一维数组存储所有鹰的位置，提高内存局部性
    private val positions = DoubleArray(population * dim)
    private val rabbitLocation = DoubleArray(dim) { (lb + ub) / 2 } // 初始化为中间值
    private val codec = AssignmentVectorCodec(dim, lb.toInt(), ub.toInt())
    private var rabbitEnergy = Double.POSITIVE_INFINITY

    init {
        initPopulation()
    }

    private fun initPopulation() {
        for (i in 0 until population) {
            val baseIndex = i * dim
            for (j in 0 until dim) {
                positions[baseIndex + j] = lb + (ub - lb) * random.nextDouble()
            }
            codec.clampRoundInPlace(positions, baseIndex)
        }
        updateRabbit()
    }

    // 获取鹰i的第j维位置
    private fun getPosition(
        hawk: Int,
        dimension: Int,
    ): Double = positions[hawk * dim + dimension]

    private fun evaluate(hawk: Int): Double = codec.evaluate(positions, hawk * dim, optFunction)

    private fun updateRabbit() {
        for (i in 0 until population) {
            val fitness = evaluate(i)
            if (fitness < rabbitEnergy) {
                rabbitEnergy = fitness
                // 复制当前鹰位置到兔子位置
                val baseIndex = i * dim
                for (j in 0 until dim) {
                    rabbitLocation[j] = positions[baseIndex + j]
                }
            }
        }
    }

    fun execute(): IntArray {
        for (t in 0 until maxIter) {
            val initialEnergy = HHO_ENERGY_SCALE * random.nextDouble() - 1
            val escapeEnergy = HHO_ENERGY_SCALE * initialEnergy * (1 - t.toDouble() / maxIter)

            for (i in 0 until population) {
                val samples = nextRandomSamples()
                val baseIndex = i * dim

                // 保存旧位置用于回滚
                val oldPositions = DoubleArray(dim) { j -> positions[baseIndex + j] }

                for (j in 0 until dim) {
                    updatePosition(baseIndex + j, j, escapeEnergy, samples)
                }

                // 整体调整位置（避免逐维度调整导致的问题）
                codec.clampRoundInPlace(positions, baseIndex)

                // 评估新位置，如果变差则恢复
                val newFitness = evaluate(i)
                if (newFitness.isNaN() || newFitness.isInfinite()) {
                    restorePositions(baseIndex, oldPositions)
                }
            }

            updateRabbit()
        }

        // 确保返回的解在有效范围内
        val result = codec.toAllocation(rabbitLocation)
        Logger.debug("HHO 最终解: {}", result.take(min(DEBUG_ALLOCATION_PREVIEW_SIZE, dim)))
        return result
    }

    private fun nextRandomSamples(): HhoRandomSamples {
        val q = random.nextDouble()
        random.nextDouble()
        val r1 = random.nextDouble()
        val r2 = random.nextDouble()
        val r3 = random.nextDouble()
        random.nextDouble()
        return HhoRandomSamples(q, r1, r2, r3)
    }

    private fun updatePosition(
        index: Int,
        dimension: Int,
        escapeEnergy: Double,
        samples: HhoRandomSamples,
    ) {
        if (Math.abs(escapeEnergy) >= HHO_EXPLORATION_THRESHOLD) {
            explore(index, dimension, samples)
        } else {
            exploit(index, dimension, escapeEnergy)
        }
    }

    private fun explore(
        index: Int,
        dimension: Int,
        samples: HhoRandomSamples,
    ) {
        if (samples.q >= HHO_PROBABILITY_SWITCH) {
            val randomHawkPos = getPosition(random.nextInt(population), dimension)
            positions[index] = randomHawkPos - samples.r1 * Math.abs(randomHawkPos - positions[index])
        } else {
            val rabbitPosition = rabbitLocation[dimension]
            positions[index] =
                rabbitPosition -
                samples.r2 * Math.abs(rabbitPosition - HHO_ENERGY_SCALE * samples.r3 * positions[index])
        }
    }

    private fun exploit(
        index: Int,
        dimension: Int,
        escapeEnergy: Double,
    ) {
        val deltaX = rabbitLocation[dimension] - positions[index]
        positions[index] =
            if (Math.abs(escapeEnergy) >= HHO_SOFT_BESIEGE_THRESHOLD) {
                deltaX - escapeEnergy * Math.abs(deltaX)
            } else {
                rabbitLocation[dimension] - escapeEnergy * Math.abs(deltaX)
            }
    }

    private fun restorePositions(
        baseIndex: Int,
        oldPositions: DoubleArray,
    ) {
        for (j in 0 until dim) {
            positions[baseIndex + j] = oldPositions[j]
        }
    }
}

/**
 * HHO 调度器
 */
class HHOScheduler(
    cloudletList: List<Cloudlet>,
    vmList: List<Vm>,
    objectiveWeights: config.ObjectiveWeightsConfig = config.ObjectiveWeightsConfig(),
    private val population: Int = DEFAULT_HHO_POPULATION,
    private val maxIter: Int = DEFAULT_HHO_MAX_ITER,
    private val random: Random = Random(config.DatacenterConfig.DEFAULT_RANDOM_SEED),
) : Scheduler(cloudletList, vmList, objectiveWeights) {
    private val hho: HHO

    init {
        val objFunc = objectiveFunction as SchedulerObjectiveFunction
        hho =
            HHO(
                optFunction = objFunc,
                parameters =
                    HhoParameters(
                        population = population,
                        lowerBound = HHO_BOUND_LOWER,
                        upperBound = (vmNum - 1).toDouble(),
                        dimensions = cloudletNum,
                        maxIterations = maxIter,
                    ),
                random = random,
            )
        Logger.debug("使用 HHO (哈里斯鹰优化) 调度器")
    }

    override fun allocate(): IntArray = hho.execute()
}
