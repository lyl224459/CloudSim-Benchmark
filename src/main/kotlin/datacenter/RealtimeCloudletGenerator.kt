package datacenter

import config.CloudletGenConfig
import config.GoogleTraceConfig
import config.RealtimeArrivalConfig
import datacenter.generator.CloudletGeneratorFactory
import datacenter.generator.GoogleTraceCloudletGenerator
import org.cloudsimplus.cloudlets.Cloudlet
import java.util.Random

private const val MIN_ACTIVE_BURST_RATE = 0.1
private const val MIN_POISSON_RATE = 0.0001

/**
 * 实时云任务生成器
 * 生成带有到达时间的任务，模拟实时任务调度场景
 */
class RealtimeCloudletGenerator(
    private val random: Random = Random(config.DatacenterConfig.DEFAULT_RANDOM_SEED),
    private val arrivalRate: Double = 10.0, // 平均每秒到达的任务数（泊松分布）
    private val generatorType: config.CloudletGeneratorType = CloudletGenConfig.GENERATOR_TYPE,
    private val arrivalConfig: RealtimeArrivalConfig = RealtimeArrivalConfig(),
    private val googleTraceConfig: GoogleTraceConfig? = null,
) {
    private val strategy = CloudletGeneratorFactory.createGenerator(generatorType, googleTraceConfig)

    /**
     * 创建实时云任务列表（带到达时间）
     *
     * @param userId 用户ID
     * @param count 任务数量
     * @param simulationDuration 仿真持续时间（秒）
     * @return 云任务列表，已设置到达时间
     */
    fun createRealtimeCloudlets(
        userId: Int,
        count: Int = config.DatacenterConfig.DEFAULT_CLOUDLET_N,
        simulationDuration: Double = 1000.0,
    ): List<Cloudlet> = createRealtimeCloudletBatch(userId, count, simulationDuration).cloudlets

    fun createRealtimeCloudletBatch(
        userId: Int,
        count: Int = config.DatacenterConfig.DEFAULT_CLOUDLET_N,
        simulationDuration: Double = 1000.0,
    ): RealtimeCloudletBatch {
        val baseSpecs =
            when (strategy) {
                is GoogleTraceCloudletGenerator -> strategy.createCloudletSpecs(userId, count, random)
                else -> strategy.createCloudlets(userId, count, random).map(::RealtimeCloudletSpec)
            }
        val arrivalTimes = generateArrivalTimes(count, simulationDuration)

        return RealtimeCloudletBatch(
            arrivalTimes.mapIndexedNotNull { index, arrivalTime ->
                baseSpecs.getOrNull(index)?.also { spec ->
                    spec.cloudlet.setSubmissionDelay(arrivalTime)
                }
            },
        )
    }

    internal fun generateArrivalTimes(
        count: Int,
        simulationDuration: Double,
    ): List<Double> {
        if (count <= 0 || simulationDuration <= 0.0) {
            return emptyList()
        }

        return when (arrivalConfig.distribution.lowercase()) {
            "uniform" -> generateUniformArrivalTimes(count, simulationDuration)
            "burst" -> generateBurstArrivalTimes(count, simulationDuration)
            else -> generatePoissonArrivalTimes(count, simulationDuration)
        }
    }

    private fun generatePoissonArrivalTimes(
        count: Int,
        simulationDuration: Double,
    ): List<Double> {
        val times = mutableListOf<Double>()
        var currentTime = 0.0

        repeat(count) {
            currentTime += exponentialInterArrival(arrivalRate)
            if (currentTime > simulationDuration) {
                return times
            }
            times.add(currentTime)
        }
        return times
    }

    private fun generateUniformArrivalTimes(
        count: Int,
        simulationDuration: Double,
    ): List<Double> {
        val interval =
            if (arrivalRate > 0.0) {
                1.0 / arrivalRate
            } else {
                simulationDuration / count.toDouble()
            }
        val times = mutableListOf<Double>()
        var currentTime = interval
        repeat(count) {
            if (currentTime > simulationDuration) {
                return times
            }
            times.add(currentTime)
            currentTime += interval
        }
        return times
    }

    private fun generateBurstArrivalTimes(
        count: Int,
        simulationDuration: Double,
    ): List<Double> {
        val times = mutableListOf<Double>()
        val burstWindow = arrivalConfig.burstDuration.coerceAtLeast(1.0)
        val cycleWindow = (burstWindow * 2.0).coerceAtLeast(burstWindow + 1.0)
        var currentTime = 0.0

        repeat(count) {
            val positionInCycle = currentTime % cycleWindow
            val activeRate =
                if (positionInCycle <= burstWindow) {
                    arrivalRate * arrivalConfig.burstIntensity
                } else {
                    (arrivalRate / arrivalConfig.burstIntensity).coerceAtLeast(MIN_ACTIVE_BURST_RATE)
                }

            currentTime += exponentialInterArrival(activeRate)
            if (currentTime > simulationDuration) {
                return times
            }
            times.add(currentTime)
        }
        return times
    }

    private fun exponentialInterArrival(rate: Double): Double {
        val safeRate = rate.coerceAtLeast(MIN_POISSON_RATE)
        return -Math.log(1.0 - random.nextDouble()) / safeRate
    }
}
