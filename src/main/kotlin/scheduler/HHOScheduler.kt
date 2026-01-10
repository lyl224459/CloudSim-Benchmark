package scheduler

import datacenter.ObjectiveFunction
import datacenter.SchedulerObjectiveFunction
import org.apache.commons.math3.special.Gamma
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import util.Logger
import java.util.*
import kotlin.math.min

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
    private val rabbitLocation = DoubleArray(dim) { (lb + ub) / 2 } // 初始化为中间值
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
        val levy = DoubleArray(d) { 
            val step = u[it] / Math.pow(Math.abs(v[it]) + 1e-10, 1.0 / beta)
            // 限制步长范围，避免过大的跳跃
            step.coerceIn(-10.0, 10.0)
        }
        return levy
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
                
                // 保存旧位置用于回滚
                val oldPositions = DoubleArray(dim) { j -> positions[baseIndex + j] }

                for (j in 0 until dim) {
                    val index = baseIndex + j
                    when {
                        Math.abs(E) >= 1 -> {
                            // 探索阶段（修复：使用正确的位置更新公式）
                            if (q >= 0.5) {
                                // 基于随机鹰的位置
                                val randomHawk = random.nextInt(population)
                                val randomHawkPos = getPosition(randomHawk, j)
                                positions[index] = randomHawkPos - r1 * Math.abs(randomHawkPos - positions[index])
                            } else {
                                // 基于兔子位置
                                positions[index] = rabbitLocation[j] - r2 * Math.abs(rabbitLocation[j] - 2 * r3 * positions[index])
                            }
                        }
                        Math.abs(E) < 1 -> {
                            // 开发阶段（修复：使用正确的围捕公式）
                            if (r >= 0.5 && Math.abs(E) >= 0.5) {
                                // 软包围
                                val deltaX = rabbitLocation[j] - positions[index]
                                positions[index] = deltaX - E * Math.abs(deltaX)
                            } else if (r >= 0.5 && Math.abs(E) < 0.5) {
                                // 硬包围
                                val deltaX = rabbitLocation[j] - positions[index]
                                positions[index] = rabbitLocation[j] - E * Math.abs(deltaX)
                            } else if (r < 0.5 && Math.abs(E) >= 0.5) {
                                // 渐进式快速俯冲软包围（延迟评估）
                                val deltaX = rabbitLocation[j] - positions[index]
                                positions[index] = deltaX - E * Math.abs(deltaX)
                            } else {
                                // 渐进式快速俯冲硬包围（延迟评估）
                                val deltaX = rabbitLocation[j] - positions[index]
                                positions[index] = rabbitLocation[j] - E * Math.abs(deltaX)
                            }
                        }
                    }
                }

                // 整体调整位置（避免逐维度调整导致的问题）
                adjustPositions(i)
                
                // 评估新位置，如果变差则恢复
                val newFitness = evaluate(i)
                if (newFitness.isNaN() || newFitness.isInfinite()) {
                    // 恢复旧位置
                    for (j in 0 until dim) {
                        positions[baseIndex + j] = oldPositions[j]
                    }
                }
            }

            updateRabbit()
        }

        // 确保返回的解在有效范围内
        val result = rabbitLocation.map { it.round().toInt().coerceIn(lb.toInt(), ub.toInt()) }.toIntArray()
        Logger.debug("HHO 最终解: {}", result.take(min(10, dim)))
        return result
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
