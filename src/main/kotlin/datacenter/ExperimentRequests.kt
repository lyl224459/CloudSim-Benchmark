package datacenter

import config.BatchConfig
import config.OptimizerConfig
import config.RealtimeConfig
import scheduler.ResolvedAlgorithm
import util.ExperimentConcurrency
import util.ExperimentOutputContext

data class ExperimentExecutionRequest(
    val randomSeed: Long = 0L,
    val resolvedAlgorithms: List<ResolvedAlgorithm> = emptyList(),
    val outputContext: ExperimentOutputContext = ExperimentOutputContext(null),
    val concurrency: ExperimentConcurrency = ExperimentConcurrency(),
)

data class BatchExperimentRequest(
    val batch: BatchConfig = BatchConfig(),
    val execution: ExperimentExecutionRequest = ExperimentExecutionRequest(),
)

data class RealtimeExperimentRequest(
    val realtime: RealtimeConfig = RealtimeConfig(),
    val optimizer: OptimizerConfig = OptimizerConfig(),
    val execution: ExperimentExecutionRequest = ExperimentExecutionRequest(),
)
