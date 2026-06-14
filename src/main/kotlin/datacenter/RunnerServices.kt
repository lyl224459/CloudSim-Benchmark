package datacenter

import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import scheduler.Scheduler

internal fun interface BatchExecutionService {
    fun run(
        algorithmName: String,
        runSeed: Long,
        schedulerFactory: (List<Cloudlet>, List<Vm>) -> Scheduler,
    ): AlgorithmResult
}

internal interface BatchExportService {
    fun printComparisonResults(summaries: List<BatchRunSummary>)

    fun exportToCsv(summaries: List<BatchRunSummary>)

    fun saveSummary(summaries: List<BatchRunSummary>)

    suspend fun saveTrial(outcome: BatchRunOutcome)
}

internal data class BatchRunnerServices(
    val executor: BatchExecutionService,
    val exporter: BatchExportService,
) {
    companion object {
        fun production(request: BatchExperimentRequest): BatchRunnerServices =
            BatchRunnerServices(
                executor = BatchAlgorithmExecutor(request.batch),
                exporter = BatchResultExporter(request.execution.outputContext, request.batch.runs),
            )
    }
}

internal fun interface RealtimeExperimentService {
    fun run(request: RealtimeExperimentRunRequest): RealtimeAlgorithmResult
}

internal interface RealtimeExportService {
    suspend fun saveTrialOutcome(outcome: RealtimeRunOutcome)

    fun printComparisonResults(summaries: List<RealtimeRunSummary>)

    fun exportRealtimeToCSV(summaries: List<RealtimeRunSummary>)

    fun saveSummaryResults(summaries: List<RealtimeRunSummary>)
}

internal data class RealtimeRunnerServices(
    val experimentRunner: RealtimeExperimentService,
    val exporter: RealtimeExportService,
) {
    companion object {
        fun production(request: RealtimeExperimentRequest): RealtimeRunnerServices {
            val config =
                RealtimeExperimentConfigSnapshot(
                    cloudletCount = request.realtime.cloudletCount,
                    simulationDuration = request.realtime.simulationDuration,
                    arrivalRate = request.realtime.arrivalRate,
                    generatorType = request.realtime.generatorType,
                    googleTraceConfig = request.realtime.googleTraceConfig,
                    arrival = request.realtime.arrival,
                    scheduling = request.realtime.scheduling,
                )
            return RealtimeRunnerServices(
                experimentRunner =
                    RealtimeExperimentRunner(
                        config = config,
                        metricsCollector =
                            RealtimeMetricsCollector(
                                request.realtime.scheduling,
                                request.realtime.objectiveWeights,
                            ),
                    ),
                exporter = RealtimeResultExporter(request.execution.outputContext),
            )
        }
    }
}

internal fun interface BatchSummaryRunnerFactory {
    suspend fun run(request: BatchExperimentRequest): List<BatchRunSummary>
}

internal fun interface RealtimeSummaryRunnerFactory {
    suspend fun run(request: RealtimeExperimentRequest): List<RealtimeRunSummary>
}
