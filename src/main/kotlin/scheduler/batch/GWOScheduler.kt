package scheduler.batch

import datacenter.SchedulerObjectiveFunction
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import scheduler.AssignmentSearchSpace
import scheduler.AssignmentVectorCodec
import scheduler.OptimizerRuntime
import util.Logger
import java.util.Random

/**
 * 灰狼优化算法 (Grey Wolf Optimizer) - 优化版本
 * 使用一维数组存储所有狼的位置，提高内存访问效率
 */
private class GWO(
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

    // 使用一维数组存储所有狼的位置，提高内存局部性
    private val positions = DoubleArray(population * dim)
    private val codec = AssignmentVectorCodec(dim, lb.toInt(), ub.toInt())

    private val alphaPos = DoubleArray(dim)
    private var alphaScore = Double.POSITIVE_INFINITY

    private val betaPos = DoubleArray(dim)
    private var betaScore = Double.POSITIVE_INFINITY

    private val deltaPos = DoubleArray(dim)
    private var deltaScore = Double.POSITIVE_INFINITY

    companion object {
        private const val LINEAR_COEFFICIENT = 2.0
        private const val LEADER_COUNT = 3.0
    }

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

    private fun adjustAndEvaluate(agentIndex: Int) {
        val baseIndex = agentIndex * dim
        codec.clampRoundInPlace(positions, baseIndex)
        val fitness = codec.evaluate(positions, baseIndex, optFunction)

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
            val a = LINEAR_COEFFICIENT - t * (LINEAR_COEFFICIENT / maxIter.toDouble())

            for (i in 0 until population) {
                val baseIndex = i * dim
                for (j in 0 until dim) {
                    val index = baseIndex + j
                    val r1 = random.nextDouble()
                    val r2 = random.nextDouble()

                    val a1 = LINEAR_COEFFICIENT * a * r1 - a
                    val c1 = LINEAR_COEFFICIENT * r2
                    val dAlpha = Math.abs(c1 * alphaPos[j] - positions[index])
                    val x1 = alphaPos[j] - a1 * dAlpha

                    val r1Beta = random.nextDouble()
                    val r2Beta = random.nextDouble()
                    val a2 = LINEAR_COEFFICIENT * a * r1Beta - a
                    val c2 = LINEAR_COEFFICIENT * r2Beta
                    val dBeta = Math.abs(c2 * betaPos[j] - positions[index])
                    val x2 = betaPos[j] - a2 * dBeta

                    val r1Delta = random.nextDouble()
                    val r2Delta = random.nextDouble()
                    val a3 = LINEAR_COEFFICIENT * a * r1Delta - a
                    val c3 = LINEAR_COEFFICIENT * r2Delta
                    val dDelta = Math.abs(c3 * deltaPos[j] - positions[index])
                    val x3 = deltaPos[j] - a3 * dDelta

                    positions[index] = (x1 + x2 + x3) / LEADER_COUNT
                }

                adjustAndEvaluate(i)
            }
        }

        return codec.toAllocation(alphaPos)
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
    private val random: Random = Random(config.DatacenterConfig.DEFAULT_RANDOM_SEED),
) : Scheduler(cloudletList, vmList, objectiveWeights) {
    private val gwo: GWO

    init {
        val objFunc = objectiveFunction as SchedulerObjectiveFunction
        gwo =
            GWO(
                runtime = OptimizerRuntime(objFunc, population, maxIter, random),
                searchSpace = AssignmentSearchSpace(0.0, (vmNum - 1).toDouble(), cloudletNum),
            )
        Logger.debug("使用 GWO (灰狼优化) 调度器")
    }

    override fun allocate(): IntArray = gwo.execute()
}
