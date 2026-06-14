package config

import kotlinx.serialization.Serializable

private const val DEFAULT_OBJECTIVE_WEIGHT_PARTS = 3.0
private const val DEFAULT_OBJECTIVE_WEIGHT = 1.0 / DEFAULT_OBJECTIVE_WEIGHT_PARTS

data class BatchConfig(
    val cloudletCount: Int = 100,
    val cloudletCounts: List<Int> = emptyList(),
    val population: Int = 30,
    val maxIter: Int = 50,
    val algorithms: List<BatchAlgorithmType> = emptyList(),
    val runs: Int = 1,
    val generatorType: CloudletGeneratorType = CloudletGenConfig.GENERATOR_TYPE,
    val googleTraceConfig: GoogleTraceConfig? = null,
    val objectiveWeights: ObjectiveWeightsConfig = ObjectiveWeightsConfig(),
)

data class RealtimeConfig(
    val cloudletCount: Int = 200,
    val cloudletCounts: List<Int> = emptyList(),
    val simulationDuration: Double = 500.0,
    val arrivalRate: Double = 5.0,
    val algorithms: List<RealtimeAlgorithmType> = emptyList(),
    val runs: Int = 1,
    val generatorType: CloudletGeneratorType = CloudletGenConfig.GENERATOR_TYPE,
    val googleTraceConfig: GoogleTraceConfig? = null,
    val objectiveWeights: ObjectiveWeightsConfig = ObjectiveWeightsConfig(),
    val arrival: RealtimeArrivalConfig = RealtimeArrivalConfig(),
    val scheduling: RealtimeSchedulingConfig = RealtimeSchedulingConfig(),
)

data class OptimizerConfig(
    val population: Int = 20,
    val maxIter: Int = 20,
)

object DatacenterConfig {
    const val L_MIPS = 1000
    const val M_MIPS = 2000
    const val H_MIPS = 4000

    const val L_PRICE = 0.1
    const val M_PRICE = 0.5
    const val H_PRICE = 1.0

    const val L_VM_N = 4
    const val M_VM_N = 3
    const val H_VM_N = 2

    const val RAM = 2048
    const val STORAGE = 100000L
    const val IMAGE_SIZE = 10000L
    const val BW = 1024

    const val DEFAULT_CLOUDLET_N = 1000
    const val DEFAULT_RANDOM_SEED = 0L
}

enum class CloudletGeneratorType {
    LOG_NORMAL,
    UNIFORM,
    LOG_NORMAL_SCI,
    GOOGLE_TRACE,
}

object CloudletGenConfig {
    val GENERATOR_TYPE: CloudletGeneratorType = CloudletGeneratorType.LOG_NORMAL

    const val MEAN_EXEC_TIME = 30000.0
    const val VARIANCE_EXEC_TIME = 1.5

    const val MEAN_FILE_SIZE = 100.0
    const val VARIANCE_FILE_SIZE = 100.0

    const val MEAN_OUTPUT_SIZE = 100.0
    const val VARIANCE_OUTPUT_SIZE = 20.0

    const val MIN_LENGTH = 10000L
    const val MAX_LENGTH = 50000L
    const val MIN_FILE_SIZE = 10L
    const val MAX_FILE_SIZE = 200L
    const val MIN_OUTPUT_SIZE = 10L
    const val MAX_OUTPUT_SIZE = 200L
}

@Serializable
data class ObjectiveWeightsConfig(
    val cost: Double = DEFAULT_OBJECTIVE_WEIGHT,
    val totalTime: Double = DEFAULT_OBJECTIVE_WEIGHT,
    val loadBalance: Double = DEFAULT_OBJECTIVE_WEIGHT,
    val makespan: Double = 0.0,
) {
    init {
        require(cost in 0.0..1.0) { "成本权重必须在[0,1]范围内" }
        require(totalTime in 0.0..1.0) { "总时间权重必须在[0,1]范围内" }
        require(loadBalance in 0.0..1.0) { "负载均衡权重必须在[0,1]范围内" }
        require(makespan in 0.0..1.0) { "Makespan权重必须在[0,1]范围内" }

        val sum = cost + totalTime + loadBalance + makespan
        require(sum > 0.0) { "权重总和必须大于0" }
    }
}

object ObjectiveConfig {
    const val ALPHA = DEFAULT_OBJECTIVE_WEIGHT
    const val BETA = DEFAULT_OBJECTIVE_WEIGHT
    const val GAMMA = DEFAULT_OBJECTIVE_WEIGHT
}
