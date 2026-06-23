package datacenter

import config.CloudletGenConfig
import config.GoogleTraceConfig
import config.RealtimeArrivalConfig
import datacenter.generator.CloudletGeneratorFactory
import datacenter.generator.GoogleTraceCloudletGenerator
import org.cloudsimplus.cloudlets.Cloudlet
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong

private const val MIN_ACTIVE_BURST_RATE = 0.1
private const val MIN_POISSON_RATE = 0.0001
private const val MIN_CLOUDLET_LENGTH = 1L
private const val CLOUDLET_ID_USER_STRIDE = 2_147_483_647L
private const val DAG_WORKFLOW_ID_PREFIX = "workflow"

/**
 * 实时云任务生成器
 * 生成带有到达时间的任务，模拟实时任务调度场景
 */
@Suppress("TooManyFunctions") // Workload generator keeps synthetic and trace workload variants behind one API.
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
        val workloadSpecs = applyWorkloadPattern(baseSpecs(userId, count).withStableCloudletIds(userId))
        val arrivalTimes = generateArrivalTimes(count, simulationDuration)

        return RealtimeCloudletBatch(
            workloadSpecs.mapIndexedNotNull { index, spec ->
                val arrivalTime = spec.traceMetadata?.arrivalTimestamp ?: arrivalTimes.getOrNull(index)
                arrivalTime
                    ?.takeIf { it <= simulationDuration }
                    ?.let {
                        spec.cloudlet.setSubmissionDelay(it)
                        spec
                    }
            },
        )
    }

    private fun baseSpecs(
        userId: Int,
        count: Int,
    ): List<RealtimeCloudletSpec> =
        when (strategy) {
            is GoogleTraceCloudletGenerator -> strategy.createCloudletSpecs(userId, count, random)
            else -> strategy.createCloudlets(userId, count, random).map(::RealtimeCloudletSpec)
        }

    private fun List<RealtimeCloudletSpec>.withStableCloudletIds(userId: Int): List<RealtimeCloudletSpec> =
        onEachIndexed { index, spec ->
            spec.cloudlet.setId(stableCloudletId(userId, index))
        }

    private fun stableCloudletId(
        userId: Int,
        index: Int,
    ): Long = userId.toLong().coerceAtLeast(0L) * CLOUDLET_ID_USER_STRIDE + index.toLong()

    private fun applyWorkloadPattern(specs: List<RealtimeCloudletSpec>): List<RealtimeCloudletSpec> {
        val withRuntime = specs.map(::ensureExpectedDuration)
        return when (arrivalConfig.workloadPattern.lowercase()) {
            "mixed_short_long" -> withRuntime.map(::applyMixedWorkload)
            "dag_chain" -> applyDagChain(withRuntime)
            "dag_layered" -> applyLayeredDag(withRuntime)
            else -> withRuntime
        }
    }

    private fun ensureExpectedDuration(spec: RealtimeCloudletSpec): RealtimeCloudletSpec {
        val expectedDuration = spec.traceMetadata?.expectedDuration
        if (expectedDuration != null) {
            spec.cloudlet.setLength(expectedDuration.toLength())
            return spec
        }
        return spec.withMetadata {
            copy(
                expectedDuration = spec.cloudlet.length.toDouble() / arrivalConfig.runtimeReferenceMips,
                workloadClass = workloadClass ?: "standard",
            )
        }
    }

    private fun applyMixedWorkload(spec: RealtimeCloudletSpec): RealtimeCloudletSpec {
        val isShortTask = random.nextDouble() < arrivalConfig.shortTaskRatio
        val multiplier =
            if (isShortTask) {
                arrivalConfig.shortTaskLengthMultiplier
            } else {
                arrivalConfig.longTaskLengthMultiplier
            }
        val workloadClass = if (isShortTask) "short" else "long"
        val length = (spec.cloudlet.length * multiplier).roundToLong().coerceAtLeast(MIN_CLOUDLET_LENGTH)
        spec.cloudlet.setLength(length)
        return spec.withMetadata {
            copy(
                expectedDuration = length.toDouble() / arrivalConfig.runtimeReferenceMips,
                workloadClass = workloadClass,
            )
        }
    }

    private fun applyDagChain(specs: List<RealtimeCloudletSpec>): List<RealtimeCloudletSpec> =
        specs.mapIndexed { index, spec ->
            val dependencyIds =
                specs
                    .getOrNull(index - 1)
                    ?.cloudlet
                    ?.id
                    ?.let(::listOf)
                    .orEmpty()
            spec.withMetadata {
                copy(
                    dependencyIds = dependencyIds,
                    workflowId = "$DAG_WORKFLOW_ID_PREFIX-chain-0",
                    stageIndex = index,
                    workloadClass = "dag_chain",
                )
            }
        }

    private fun applyLayeredDag(specs: List<RealtimeCloudletSpec>): List<RealtimeCloudletSpec> {
        val depth = arrivalConfig.dagDepth.coerceAtLeast(1)
        val width = arrivalConfig.dagWidth.coerceAtLeast(1)
        val workflowSize = depth * width
        return specs.mapIndexed { index, spec ->
            val indexInWorkflow = index % workflowSize
            val workflowIndex = index / workflowSize
            val stage = indexInWorkflow / width
            val position = indexInWorkflow % width
            spec.withMetadata {
                copy(
                    dependencyIds = layeredDependencyIds(specs, workflowIndex, stage, position, workflowSize, width),
                    workflowId = "$DAG_WORKFLOW_ID_PREFIX-layered-$workflowIndex",
                    stageIndex = stage,
                    workloadClass = "dag_layered",
                )
            }
        }
    }

    @Suppress("LongParameterList") // DAG dependency indexing needs all workflow coordinates for stable IDs.
    private fun layeredDependencyIds(
        specs: List<RealtimeCloudletSpec>,
        workflowIndex: Int,
        stage: Int,
        position: Int,
        workflowSize: Int,
        width: Int,
    ): List<Long> {
        if (stage == 0) return emptyList()
        val previousStageStart = workflowIndex * workflowSize + (stage - 1) * width
        val firstDependencyPosition = (position - arrivalConfig.dagFanOut + 1).coerceAtLeast(0)
        val lastDependencyPosition = position.coerceAtMost(width - 1)
        return (firstDependencyPosition..lastDependencyPosition)
            .mapNotNull { offset -> specs.getOrNull(previousStageStart + offset)?.cloudlet?.id }
    }

    @Suppress("MaxLineLength") // ktlint keeps this metadata copy helper as a body expression.
    private fun RealtimeCloudletSpec.withMetadata(transform: RealtimeTraceMetadata.() -> RealtimeTraceMetadata): RealtimeCloudletSpec =
        copy(traceMetadata = (traceMetadata ?: RealtimeTraceMetadata()).transform())

    private fun Double.toLength(): Long =
        (this * arrivalConfig.runtimeReferenceMips)
            .roundToLong()
            .coerceAtLeast(MIN_CLOUDLET_LENGTH)

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
            "periodic" -> generatePeriodicArrivalTimes(count, simulationDuration)
            "sporadic" -> generateSporadicArrivalTimes(count, simulationDuration)
            "diurnal_burst" -> generateDiurnalBurstArrivalTimes(count, simulationDuration)
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

    private fun generatePeriodicArrivalTimes(
        count: Int,
        simulationDuration: Double,
    ): List<Double> =
        List(count) { index ->
            val baseTime = (index + 1) * arrivalConfig.periodSeconds
            val jitter = random.symmetricJitter(arrivalConfig.arrivalJitter)
            (baseTime + jitter).coerceAtLeast(0.0)
        }.filter { it <= simulationDuration }
            .sorted()

    private fun generateSporadicArrivalTimes(
        count: Int,
        simulationDuration: Double,
    ): List<Double> {
        val times = mutableListOf<Double>()
        var currentTime = 0.0
        repeat(count) {
            currentTime +=
                random.nextDoubleIn(
                    arrivalConfig.sporadicMinInterArrival,
                    arrivalConfig.sporadicMaxInterArrival,
                )
            if (currentTime > simulationDuration) {
                return times
            }
            times.add(currentTime)
        }
        return times
    }

    private fun generateDiurnalBurstArrivalTimes(
        count: Int,
        simulationDuration: Double,
    ): List<Double> {
        val times = mutableListOf<Double>()
        var currentTime = 0.0
        repeat(count) {
            val phase = (currentTime / simulationDuration).coerceIn(0.0, 1.0)
            val peakWeight = (1.0 - cos(2.0 * PI * phase)) / 2.0
            val multiplier =
                arrivalConfig.diurnalOffPeakMultiplier +
                    (arrivalConfig.diurnalPeakMultiplier - arrivalConfig.diurnalOffPeakMultiplier) * peakWeight
            currentTime += exponentialInterArrival(arrivalRate * multiplier)
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

    private fun Random.nextDoubleIn(
        min: Double,
        max: Double,
    ): Double = min + nextDouble() * (max - min).coerceAtLeast(0.0)

    private fun Random.symmetricJitter(maxJitter: Double): Double =
        if (maxJitter <= 0.0) {
            0.0
        } else {
            nextDoubleIn(-maxJitter, maxJitter)
        }
}
