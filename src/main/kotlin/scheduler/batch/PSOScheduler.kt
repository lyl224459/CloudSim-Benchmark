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
 * 粒子群优化算法 (Particle Swarm Optimization) - 优化版本
 * 使用一维数组存储所有粒子数据，提高内存访问效率
 */
internal class PSO(
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
        private const val VELOCITY_RANGE_SCALE = 0.2
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
                pBest[index] = positions[index] // 初始化个体最优位置
            }
            adjustPositions(i)
        }
    }

    private fun adjustPositions(agentIndex: Int) {
        codec.clampRoundInPlace(positions, agentIndex * dim)
    }

    private fun evaluate(particle: Int): Double = codec.evaluate(positions, particle * dim, optFunction)

    fun execute(): IntArray {
        val vMax = (ub - lb) * VELOCITY_RANGE_SCALE

        for (t in 0 until maxIter) {
            val w = W_MAX - t * (W_MAX - W_MIN) / maxIter.toDouble()
            evaluatePopulation()
            updateParticles(w, vMax)
        }

        return codec.toAllocation(gBest)
    }

    private fun evaluatePopulation() {
        for (i in 0 until population) {
            clampParticle(i)
            val fitness = evaluate(i)
            updateParticleBest(i, fitness)
            updateGlobalBest(i, fitness)
        }
    }

    private fun clampParticle(particle: Int) {
        val baseIndex = particle * dim
        for (j in 0 until dim) {
            val posIndex = baseIndex + j
            when {
                positions[posIndex] > ub -> positions[posIndex] = ub
                positions[posIndex] < lb -> positions[posIndex] = lb
            }
        }
    }

    private fun updateParticleBest(
        particle: Int,
        fitness: Double,
    ) {
        if (fitness < pBestScore[particle]) {
            pBestScore[particle] = fitness
            val baseIndex = particle * dim
            for (j in 0 until dim) {
                pBest[baseIndex + j] = positions[baseIndex + j]
            }
        }
    }

    private fun updateGlobalBest(
        particle: Int,
        fitness: Double,
    ) {
        if (fitness < gBestScore) {
            gBestScore = fitness
            val baseIndex = particle * dim
            for (j in 0 until dim) {
                gBest[j] = positions[baseIndex + j]
            }
        }
    }

    private fun updateParticles(
        inertia: Double,
        vMax: Double,
    ) {
        for (i in 0 until population) {
            val baseIndex = i * dim
            for (j in 0 until dim) {
                updateParticlePosition(baseIndex, j, inertia, vMax)
            }
            adjustPositions(i)
        }
    }

    private fun updateParticlePosition(
        baseIndex: Int,
        dimension: Int,
        inertia: Double,
        vMax: Double,
    ) {
        val index = baseIndex + dimension
        val r1 = random.nextDouble()
        val r2 = random.nextDouble()
        velocities[index] = inertia * velocities[index] +
            C1 * r1 * (pBest[index] - positions[index]) +
            C2 * r2 * (gBest[dimension] - positions[index])
        when {
            velocities[index] > vMax -> velocities[index] = vMax
            velocities[index] < -vMax -> velocities[index] = -vMax
        }
        positions[index] += velocities[index]
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
    private val random: Random = Random(config.DatacenterConfig.DEFAULT_RANDOM_SEED),
) : Scheduler(cloudletList, vmList, objectiveWeights) {
    private val pso: PSO

    init {
        val objFunc = objectiveFunction as SchedulerObjectiveFunction
        pso =
            PSO(
                runtime = OptimizerRuntime(objFunc, population, maxIter, random),
                searchSpace = AssignmentSearchSpace(0.0, (vmNum - 1).toDouble(), cloudletNum),
            )
        Logger.debug("使用 PSO (粒子群优化) 调度器")
    }

    override fun allocate(): IntArray = pso.execute()
}
