package scheduler

import datacenter.ObjectiveFunction
import datacenter.SchedulerObjectiveFunction
import org.apache.commons.math3.special.Gamma
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import util.Logger
import java.util.*

/**
 * 哈里斯鹰优化算法 (Harris Hawks Optimization) - 优化版本
 * 使用一维数组存储所有鹰的位置，提高内存访问效率
 */
private class HHO(
    private val optFunction: ObjectiveFunction,
    private val population: Int,
    private val lb: Double,
    private val ub: Double,
    private val dim: Int,
    private val maxIter: Int,
    private val random: Random
) {
    // 使用一维数组存储所有鹰的位置，提高内存局部性
    private val positions = DoubleArray(population * dim)
    private val rabbitLocation = DoubleArray(dim)
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
            adjustPositions(i)
        }
        updateRabbit()
    }

    // 获取鹰i的第j维位置
    private fun getPosition(hawk: Int, dimension: Int): Double {
        return positions[hawk * dim + dimension]
    }

    // 设置鹰i的第j维位置
    private fun setPosition(hawk: Int, dimension: Int, value: Double) {
        positions[hawk * dim + dimension] = value
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

    private fun evaluate(hawk: Int): Double {
        // 直接使用一维数组，避免创建临时数组
        val params = IntArray(dim)
        val baseIndex = hawk * dim
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
    
    private fun levyFlight(d: Int): DoubleArray {
        val beta = 1.5
        val sigma = Math.pow(
            (Gamma.gamma(1 + beta) * Math.sin(Math.PI * beta / 2)) /
                    (Gamma.gamma((1 + beta) / 2) * beta * Math.pow(2.0, (beta - 1) / 2)),
            1.0 / beta
        )
        val u = DoubleArray(d) { random.nextGaussian() * sigma }
        val v = DoubleArray(d) { random.nextGaussian() }
        return DoubleArray(d) { u[it] / Math.pow(Math.abs(v[it]), 1.0 / beta) }
    }
    
    fun execute(): IntArray {
        for (t in 0 until maxIter) {
            val E0 = 2 * random.nextDouble() - 1
            val E = 2 * E0 * (1 - t.toDouble() / maxIter)

            for (i in 0 until population) {
                val q = random.nextDouble()
                val r = random.nextDouble()
                val r1 = random.nextDouble()
                val r2 = random.nextDouble()
                val r3 = random.nextDouble()
                val r4 = random.nextDouble()
                val baseIndex = i * dim

                for (j in 0 until dim) {
                    val index = baseIndex + j
                    when {
                        Math.abs(E) >= 1 -> {
                            // 探索阶段
                            if (q >= 0.5) {
                                positions[index] = rabbitLocation[j] - positions[index] - r1 * (ub - lb) * random.nextDouble()
                            } else {
                                positions[index] = (rabbitLocation[j] - positions[index]) + r2 * (ub - lb) * random.nextDouble()
                            }
                        }
                        Math.abs(E) < 1 -> {
                            // 开发阶段
                            if (r >= 0.5 && Math.abs(E) >= 0.5) {
                                // 软包围
                                positions[index] = (rabbitLocation[j] - positions[index]) - E * Math.abs(rabbitLocation[j] - positions[index])
                            } else if (r >= 0.5 && Math.abs(E) < 0.5) {
                                // 硬包围
                                positions[index] = rabbitLocation[j] - E * Math.abs(rabbitLocation[j] - positions[index])
                            } else if (r < 0.5 && Math.abs(E) >= 0.5) {
                                // 渐进式快速俯冲软包围
                                val Y = rabbitLocation[j] - E * Math.abs(rabbitLocation[j] - positions[index])
                                val levyStep = levyFlight(dim)
                                val Z = Y + r3 * levyStep[j] * (ub - lb)
                                positions[index] = Y  // 先更新为 Y
                                adjustPositions(i)
                                val fitnessY = evaluate(i)
                                positions[index] = Z  // 再更新为 Z
                                adjustPositions(i)
                                val fitnessZ = evaluate(i)
                                positions[index] = if (fitnessY < fitnessZ) Y else Z
                            } else {
                                // 渐进式快速俯冲硬包围
                                val Y = rabbitLocation[j] - E * Math.abs(rabbitLocation[j] - positions[index])
                                val levyStep = levyFlight(dim)
                                val Z = Y + r4 * levyStep[j] * (ub - lb)
                                positions[index] = Y  // 先更新为 Y
                                adjustPositions(i)
                                val fitnessY = evaluate(i)
                                positions[index] = Z  // 再更新为 Z
                                adjustPositions(i)
                                val fitnessZ = evaluate(i)
                                positions[index] = if (fitnessY < fitnessZ) Y else Z
                            }
                        }
                    }
                }

                adjustPositions(i)
            }

            updateRabbit()
        }

        return rabbitLocation.map { it.round().toInt() }.toIntArray()
    }
}

/**
 * HHO 调度器
 */
class HHOScheduler(
    cloudletList: List<Cloudlet>,
    vmList: List<Vm>,
    objectiveWeights: config.ObjectiveWeightsConfig = config.ObjectiveWeightsConfig(),
    private val population: Int = 30,
    private val maxIter: Int = 100,
    private val random: Random = Random(config.DatacenterConfig.DEFAULT_RANDOM_SEED)
) : Scheduler(cloudletList, vmList, objectiveWeights) {
    
    private val hho: HHO
    
    init {
        val objFunc = objectiveFunction as SchedulerObjectiveFunction
        hho = HHO(
            optFunction = objFunc,
            population = population,
            lb = 0.0,
            ub = (vmNum - 1).toDouble(),
            dim = cloudletNum,
            maxIter = maxIter,
            random = random
        )
        Logger.debug("使用 HHO (哈里斯鹰优化) 调度器")
    }
    
    override fun allocate(): IntArray {
        return hho.execute()
    }
}

private fun Double.round() = kotlin.math.round(this)
