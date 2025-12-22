package scheduler

import datacenter.ObjectiveFunction
import datacenter.SchedulerObjectiveFunction
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import util.Logger
import java.util.*

/**
 * 鲸鱼优化算法 (Whale Optimization Algorithm)
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
    private val positions = Array(population) { DoubleArray(dim) }
    private val optimalPos = DoubleArray(dim)
    private var optimalScore = Double.POSITIVE_INFINITY
    
    init {
        initPopulation()
    }
    
    private fun initPopulation() {
        for (i in 0 until population) {
            for (j in 0 until dim) {
                positions[i][j] = lb + (ub - lb) * random.nextDouble()
            }
            adjustPositions(i)
            val fitness = evaluate(positions[i])
            if (fitness < optimalScore) {
                optimalScore = fitness
                positions[i].copyInto(optimalPos)
            }
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
            val a = 2.0 - t * (2.0 / maxIter) // a 从 2 线性递减到 0
            
            for (i in 0 until population) {
                val r1 = random.nextDouble()
                val r2 = random.nextDouble()
                val A = 2.0 * a * r1 - a
                val C = 2.0 * r2
                val b = 1.0
                val l = (random.nextDouble() * 2.0) - 1.0
                
                val p = random.nextDouble()
                
                for (j in 0 until dim) {
                    when {
                        p < 0.5 -> {
                            if (Math.abs(A) >= 1) {
                                // 随机搜索
                                val randLeaderIndex = random.nextInt(population)
                                val Xrand = positions[randLeaderIndex]
                                positions[i][j] = Xrand[j] - A * Math.abs(C * Xrand[j] - positions[i][j])
                            } else {
                                // 包围猎物
                                positions[i][j] = optimalPos[j] - A * Math.abs(C * optimalPos[j] - positions[i][j])
                            }
                        }
                        else -> {
                            // 螺旋更新位置
                            val distance2Leader = Math.abs(optimalPos[j] - positions[i][j])
                            positions[i][j] = distance2Leader * Math.exp(b * l) * Math.cos(l * 2 * Math.PI) + optimalPos[j]
                        }
                    }
                }
                
                adjustPositions(i)
                val fitness = evaluate(positions[i])
                
                if (fitness < optimalScore) {
                    optimalScore = fitness
                    positions[i].copyInto(optimalPos)
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
    private val population: Int = 30,
    private val maxIter: Int = 100,
    private val random: Random = Random(0L)
) : Scheduler(cloudletList, vmList) {
    
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
