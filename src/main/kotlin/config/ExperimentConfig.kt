package config

/**
 * 实验配置类
 * 统一管理所有实验参数
 */
data class ExperimentConfig(
    // ========== 批处理模式配置 ==========
    val batch: BatchConfig = BatchConfig(),
    
    // ========== 实时调度模式配置 ==========
    val realtime: RealtimeConfig = RealtimeConfig(),
    
    // ========== 通用配置 ==========
    val randomSeed: Long = 0L,
    
    // ========== 优化算法配置 ==========
    val optimizer: OptimizerConfig = OptimizerConfig()
)

/**
 * 批处理模式配置
 */
data class BatchConfig(
    val cloudletCount: Int = 100,
    val population: Int = 30,
    val maxIter: Int = 50,
    /**
     * 要运行的算法列表（空列表表示运行所有算法）
     * 示例: listOf(BatchAlgorithmType.PSO, BatchAlgorithmType.WOA)
     */
    val algorithms: List<BatchAlgorithmType> = emptyList(),  // 空列表 = 运行所有算法
    /**
     * 实验运行次数（用于计算平均值）
     * 默认: 1（单次运行）
     */
    val runs: Int = 1,
    /**
     * 任务生成器类型
     * 默认: LOG_NORMAL（对数正态分布）
     */
    val generatorType: CloudletGeneratorType = CloudletGenConfig.GENERATOR_TYPE
)

/**
 * 实时调度模式配置
 */
data class RealtimeConfig(
    val cloudletCount: Int = 200,
    val simulationDuration: Double = 500.0,  // 仿真持续时间（秒）
    val arrivalRate: Double = 5.0,            // 平均每秒到达的任务数
    /**
     * 要运行的算法列表（空列表表示运行所有算法）
     * 示例: listOf(RealtimeAlgorithmType.PSO_REALTIME, RealtimeAlgorithmType.WOA_REALTIME)
     */
    val algorithms: List<RealtimeAlgorithmType> = emptyList(),  // 空列表 = 运行所有算法
    /**
     * 实验运行次数（用于计算平均值）
     * 默认: 1（单次运行）
     */
    val runs: Int = 1,
    /**
     * 任务生成器类型
     * 默认: LOG_NORMAL（对数正态分布）
     */
    val generatorType: CloudletGeneratorType = CloudletGenConfig.GENERATOR_TYPE
)

/**
 * 优化算法配置
 */
data class OptimizerConfig(
    val population: Int = 20,      // 实时调度使用的种群大小
    val maxIter: Int = 20           // 实时调度使用的最大迭代次数
)

/**
 * 数据中心配置
 */
object DatacenterConfig {
    // 虚拟机配置
    const val L_MIPS = 1000
    const val M_MIPS = 2000
    const val H_MIPS = 4000
    
    const val L_PRICE = 0.1
    const val M_PRICE = 0.5
    const val H_PRICE = 1.0
    
    const val L_VM_N = 4
    const val M_VM_N = 3
    const val H_VM_N = 2
    
    // 资源配置
    const val RAM = 2048              // MB
    const val STORAGE = 100000L       // MB
    const val IMAGE_SIZE = 10000L     // MB
    const val BW = 1024               // Mbps
    
    // 默认任务数量
    const val DEFAULT_CLOUDLET_N = 1000
    
    // 默认随机数种子
    const val DEFAULT_RANDOM_SEED = 0L
}

/**
 * 任务生成器类型
 */
enum class CloudletGeneratorType {
    LOG_NORMAL,      // 对数正态分布（默认，对应 createCloudlets）
    UNIFORM,         // 均匀分布（对应 createCloudlets1）
    LOG_NORMAL_SCI   // 对数正态分布 SCI（对应 createCloudletsSCI，输出文件大小独立参数）
}

/**
 * 任务生成配置
 */
object CloudletGenConfig {
    // 任务生成器类型
    val GENERATOR_TYPE: CloudletGeneratorType = CloudletGeneratorType.LOG_NORMAL
    
    // 任务执行时间分布参数（对数正态分布）
    const val MEAN_EXEC_TIME = 30000.0
    const val VARIANCE_EXEC_TIME = 1.5
    
    // 文件大小分布参数（正态分布）
    const val MEAN_FILE_SIZE = 100.0
    const val VARIANCE_FILE_SIZE = 100.0
    
    // 输出文件大小分布参数（正态分布，用于 LOG_NORMAL_SCI）
    const val MEAN_OUTPUT_SIZE = 100.0
    const val VARIANCE_OUTPUT_SIZE = 20.0
    
    // 均匀分布参数（用于 UNIFORM）
    const val MIN_LENGTH = 10000L
    const val MAX_LENGTH = 50000L
    const val MIN_FILE_SIZE = 10L
    const val MAX_FILE_SIZE = 200L
    const val MIN_OUTPUT_SIZE = 10L
    const val MAX_OUTPUT_SIZE = 200L
}

/**
 * 目标函数配置
 */
object ObjectiveConfig {
    // 适应度函数权重
    const val ALPHA = 1.0 / 3  // Cost权重
    const val BETA = 1.0 / 3    // TotalTime权重
    const val GAMMA = 1.0 / 3   // LoadBalance权重
}

