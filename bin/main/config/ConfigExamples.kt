package config

/**
 * 配置示例
 * 展示如何配置算法选择和其他参数
 */
object ConfigExamples {
    
    /**
     * 示例1: 批处理模式 - 只运行 PSO 和 WOA
     */
    val batchPSOAndWOA = ExperimentConfig(
        batch = BatchConfig(
            cloudletCount = 100,
            population = 30,
            maxIter = 50,
            algorithms = listOf(BatchAlgorithmType.PSO, BatchAlgorithmType.WOA)
        ),
        randomSeed = 42L
    )
    
    /**
     * 示例2: 批处理模式 - 运行所有算法
     */
    val batchAllAlgorithms = ExperimentConfig(
        batch = BatchConfig(
            cloudletCount = 100,
            population = 30,
            maxIter = 50,
            algorithms = emptyList()  // 空列表 = 运行所有算法
        ),
        randomSeed = 0L
    )
    
    /**
     * 示例3: 实时模式 - 只运行 PSO 和 WOA
     */
    val realtimePSOAndWOA = ExperimentConfig(
        realtime = RealtimeConfig(
            cloudletCount = 200,
            simulationDuration = 500.0,
            arrivalRate = 5.0,
            algorithms = listOf(
                RealtimeAlgorithmType.PSO_REALTIME,
                RealtimeAlgorithmType.WOA_REALTIME
            )
        ),
        optimizer = OptimizerConfig(
            population = 20,
            maxIter = 20
        ),
        randomSeed = 123L
    )
    
    /**
     * 示例4: 实时模式 - 只运行最小负载和随机调度（快速对比）
     */
    val realtimeQuickComparison = ExperimentConfig(
        realtime = RealtimeConfig(
            cloudletCount = 100,
            simulationDuration = 300.0,
            arrivalRate = 3.0,
            algorithms = listOf(
                RealtimeAlgorithmType.MIN_LOAD,
                RealtimeAlgorithmType.RANDOM
            )
        ),
        optimizer = OptimizerConfig(
            population = 10,
            maxIter = 10
        ),
        randomSeed = 0L
    )
    
    /**
     * 示例5: 批处理模式 - 只运行群体智能算法（排除随机调度）
     */
    val batchOnlySwarmIntelligence = ExperimentConfig(
        batch = BatchConfig(
            cloudletCount = 100,
            population = 30,
            maxIter = 50,
            algorithms = listOf(
                BatchAlgorithmType.PSO,
                BatchAlgorithmType.WOA,
                BatchAlgorithmType.GWO,
                BatchAlgorithmType.HHO
            )
        ),
        randomSeed = 0L
    )
}

