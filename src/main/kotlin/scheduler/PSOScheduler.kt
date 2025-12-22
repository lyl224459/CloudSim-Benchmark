package scheduler

import datacenter.ObjectiveFunction
import datacenter.SchedulerObjectiveFunction
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import util.Logger
import java.util.*

/**
 * 粒子群优化算法 (Particle Swarm Optimization)
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
    
    private val positions = Array(population) { DoubleArray(dim) }
    private val velocities = Array(population) { DoubleArray(dim) }
    private val pBest = Array(population) { DoubleArray(dim) }
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
                positions[i][j] = lb + (ub - lb) * random.nextDouble()
                velocities[i][j] = random.nextDouble()
            }
            adjustPositions(i)
        }
    }
    
    private fun adjustPositions(agentIndex: Int) {
        for (j in 0 until dim) {
            positions[agentIndex][j] = positions[agentIndex][j].round()
            when {
                positions[agentIndex][j] < lb -> positions[agentIndex][j] = lb
                positions[agentIndex][j] > ub -> positions[agentIndex][j] = ub
            }
        }
    }
    
    private fun evaluate(sol: DoubleArray): Double {
        val params = sol.map { it.round().toInt() }.toIntArray()
        return optFunction.calculate(params)
    }
    
    fun execute(): IntArray {
        for (t in 0 until maxIter) {
            val w = W_MAX - t * (W_MAX - W_MIN) / maxIter
            val vMax = DoubleArray(dim) { (ub - lb) * 0.2 }
            
            // 评估并更新最优解
            for (i in 0 until population) {
                // 边界处理
                for (j in 0 until dim) {
                    when {
                        positions[i][j] > ub -> positions[i][j] = ub
                        positions[i][j] < lb -> positions[i][j] = lb
                    }
                }
                
                val fitness = evaluate(positions[i])
                
                // 更新个体最优
                if (fitness < pBestScore[i]) {
                    pBestScore[i] = fitness
                    positions[i].copyInto(pBest[i])
                }
                
                // 更新全局最优
                if (fitness < gBestScore) {
                    gBestScore = fitness
                    positions[i].copyInto(gBest)
                }
            }
            
            // 更新速度和位置
            for (i in 0 until population) {
                for (j in 0 until dim) {
                    val r1 = random.nextDouble()
                    val r2 = random.nextDouble()
                    
                    velocities[i][j] = w * velocities[i][j] +
                            C1 * r1 * (pBest[i][j] - positions[i][j]) +
                            C2 * r2 * (gBest[j] - positions[i][j])
                    
                    // 速度钳制
                    when {
                        velocities[i][j] > vMax[j] -> velocities[i][j] = vMax[j]
                        velocities[i][j] < -vMax[j] -> velocities[i][j] = -vMax[j]
                    }
                    
                    positions[i][j] += velocities[i][j]
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
    private val population: Int = 30,
    private val maxIter: Int = 100,
    private val random: Random = Random(config.DatacenterConfig.DEFAULT_RANDOM_SEED)
) : Scheduler(cloudletList, vmList) {
    
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
