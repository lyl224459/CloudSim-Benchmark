package datacenter

import broker.RealtimeBroker
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import util.mapCloudletsToVmIndexes
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val P95_PERCENTILE = 0.95
private const val P99_PERCENTILE = 0.99

private typealias MetricPair = Pair<RealtimeMetricKey, Number>

internal data class RealtimeMetricCollectionRequest(
    val algorithmName: String,
    val cloudletList: List<Cloudlet>,
    val finishedCloudlets: List<Cloudlet>,
    val vmList: List<Vm>,
    val broker: RealtimeBroker,
)

internal class RealtimeMetricsCollector(
    private val scheduling: config.RealtimeSchedulingConfig,
    private val objectiveWeights: config.ObjectiveWeightsConfig,
) {
    fun collect(request: RealtimeMetricCollectionRequest): RealtimeAlgorithmResult {
        val vmCountForMetrics = maxOf(request.vmList.size, request.broker.getActiveVmPeak())
        val runtimeMetrics =
            calculateRealtimeMetricValues(
                cloudletList = request.finishedCloudlets,
                vmList = request.vmList,
                vmNum = vmCountForMetrics,
                broker = request.broker,
            )
        val cloudletToVm = mapCloudletsToVmIndexes(request.cloudletList, request.finishedCloudlets, request.vmList)
        val objFunc = SchedulerObjectiveFunction(request.cloudletList, request.vmList, objectiveWeights)
        val totalTime = objFunc.estimateTotalTime(cloudletToVm)
        val fitness = objFunc.calculate(cloudletToVm)
        val metrics =
            RealtimeMetricValues(
                runtimeMetrics.values +
                    mapOf(
                        RealtimeMetricKey.TOTAL_TIME to totalTime,
                        RealtimeMetricKey.FITNESS to fitness,
                    ),
            )

        return RealtimeAlgorithmResult(
            algorithmName = request.algorithmName,
            metrics = metrics,
            candidateScores = request.broker.getCandidateScoreRecords(),
        )
    }

    private fun calculateRealtimeMetricValues(
        cloudletList: List<Cloudlet>,
        vmList: List<Vm>,
        vmNum: Int,
        broker: RealtimeBroker,
    ): RealtimeMetricValues {
        val execution = summarizeExecution(cloudletList, vmList, vmNum, broker)
        val slaViolationCount = broker.getSlaViolationCount(cloudletList)
        val topologyMetrics = broker.getTopologyMetrics(cloudletList)
        val tenantSlaPenalty = broker.getTenantSlaPenalty(cloudletList)
        val totalSlaPenalty = execution.slaPenalty + broker.getPreemptionPenalty() + tenantSlaPenalty
        val metricPairs =
            executionMetricPairs(execution) +
                brokerMetricPairs(
                    BrokerMetricContext(
                        taskTimeout = scheduling.taskTimeout,
                        broker = broker,
                        slaViolationCount = slaViolationCount,
                        slaViolationRate = slaViolationRate(slaViolationCount, execution.completedCount),
                        totalSlaPenalty = totalSlaPenalty,
                        cost = execution.cost,
                    ),
                ) +
                tenantMetricPairs(broker, cloudletList, tenantSlaPenalty) +
                topologyMetricPairs(broker, topologyMetrics)

        return RealtimeMetricValues(metricPairs.associate { (key, value) -> key to value.toDouble() })
    }

    private fun summarizeExecution(
        cloudletList: List<Cloudlet>,
        vmList: List<Vm>,
        vmNum: Int,
        broker: RealtimeBroker,
    ): RealtimeExecutionSummary {
        val accumulator =
            RealtimeExecutionAccumulator(
                executionByVm = DoubleArray(vmNum),
                vmIndexById = vmList.mapIndexed { index, vm -> vm.id to index }.toMap(),
                broker = broker,
            )
        cloudletList.forEach(accumulator::record)
        return accumulator.toSummary()
    }

    private inner class RealtimeExecutionAccumulator(
        private val executionByVm: DoubleArray,
        private val vmIndexById: Map<Long, Int>,
        private val broker: RealtimeBroker,
    ) {
        private val completed = mutableListOf<CompletedRealtimeCloudlet>()
        private var makespan = 0.0
        private var cost = 0.0
        private var failedCount = 0
        private var slaPenalty = 0.0

        fun record(cloudlet: Cloudlet) {
            when (cloudlet.status) {
                Cloudlet.Status.SUCCESS -> recordSuccess(cloudlet)
                Cloudlet.Status.FAILED -> failedCount++
                else -> Unit
            }
        }

        fun toSummary(): RealtimeExecutionSummary {
            val responseTimes = completed.map { it.responseTime }
            return RealtimeExecutionSummary(
                makespan = makespan,
                loadBalance = executionByVm.loadBalance(),
                cost = cost,
                averageWaitingTime = completed.map { it.waitingTime }.averageOrZero(),
                averageResponseTime = completed.map { it.responseTime }.averageOrZero(),
                failedCount = failedCount,
                completedCount = completed.size,
                p95ResponseTime = responseTimes.percentile(P95_PERCENTILE),
                p99ResponseTime = responseTimes.percentile(P99_PERCENTILE),
                slaPenalty = slaPenalty,
            )
        }

        private fun recordSuccess(cloudlet: Cloudlet) {
            val finishTime = cloudlet.finishTime
            val actualCpuTime = cloudlet.getTotalExecutionTime()
            val vmIndex = (vmIndexById[cloudlet.vm.id] ?: 0).coerceIn(executionByVm.indices)

            makespan = maxOf(makespan, finishTime)
            executionByVm[vmIndex] += actualCpuTime
            cost += actualCpuTime * cloudlet.costPerSecond()
            completed += completedCloudlet(cloudlet, finishTime)
            slaPenalty += slaPenaltyFor(cloudlet, finishTime)
        }

        private fun completedCloudlet(
            cloudlet: Cloudlet,
            finishTime: Double,
        ): CompletedRealtimeCloudlet {
            val arrivalTime = broker.getArrivalTime(cloudlet)
            val startTime = cloudlet.getStartTime()
            val waitingTime = if (startTime > 0) startTime - arrivalTime else 0.0
            return CompletedRealtimeCloudlet(waitingTime, finishTime - arrivalTime)
        }

        private fun slaPenaltyFor(
            cloudlet: Cloudlet,
            finishTime: Double,
        ): Double {
            val deadline = broker.getTaskMetadata(cloudlet)?.deadline ?: return 0.0
            return if (finishTime > deadline) finishTime - deadline else 0.0
        }
    }
}

private data class RealtimeExecutionSummary(
    val makespan: Double,
    val loadBalance: Double,
    val cost: Double,
    val averageWaitingTime: Double,
    val averageResponseTime: Double,
    val failedCount: Int,
    val completedCount: Int,
    val p95ResponseTime: Double,
    val p99ResponseTime: Double,
    val slaPenalty: Double,
)

private data class CompletedRealtimeCloudlet(
    val waitingTime: Double,
    val responseTime: Double,
)

private data class BrokerMetricContext(
    val taskTimeout: Double,
    val broker: RealtimeBroker,
    val slaViolationCount: Int,
    val slaViolationRate: Double,
    val totalSlaPenalty: Double,
    val cost: Double,
)

private fun executionMetricPairs(execution: RealtimeExecutionSummary): List<MetricPair> =
    listOf(
        RealtimeMetricKey.MAKESPAN to execution.makespan,
        RealtimeMetricKey.LOAD_BALANCE to execution.loadBalance,
        RealtimeMetricKey.COST to execution.cost,
        RealtimeMetricKey.AVERAGE_WAITING_TIME to execution.averageWaitingTime,
        RealtimeMetricKey.AVERAGE_RESPONSE_TIME to execution.averageResponseTime,
        RealtimeMetricKey.FAILED_COUNT to execution.failedCount,
        RealtimeMetricKey.COMPLETED_COUNT to execution.completedCount,
        RealtimeMetricKey.P95_RESPONSE_TIME to execution.p95ResponseTime,
        RealtimeMetricKey.P99_RESPONSE_TIME to execution.p99ResponseTime,
    )

private fun brokerMetricPairs(context: BrokerMetricContext): List<MetricPair> =
    listOf(
        RealtimeMetricKey.REJECTED_COUNT to context.broker.getRejectedCount(),
        RealtimeMetricKey.TIMEOUT_COUNT to context.broker.getTimeoutCount(context.taskTimeout),
        RealtimeMetricKey.RETRY_COUNT to context.broker.getRetryCount(),
        RealtimeMetricKey.PERMANENT_FAILED_COUNT to context.broker.getPermanentFailedCount(),
        RealtimeMetricKey.AVERAGE_DECISION_DELAY to context.broker.getAverageDecisionDelay(),
        RealtimeMetricKey.SUBMITTED_COUNT to context.broker.getSubmittedCount(),
        RealtimeMetricKey.SLA_VIOLATION_COUNT to context.slaViolationCount,
        RealtimeMetricKey.SLA_VIOLATION_RATE to context.slaViolationRate,
        RealtimeMetricKey.CAPACITY_REJECTED_COUNT to context.broker.getCapacityRejectedCount(),
        RealtimeMetricKey.DEADLINE_REJECTED_COUNT to context.broker.getDeadlineRejectedCount(),
        RealtimeMetricKey.DEADLINE_DEGRADED_COUNT to context.broker.getDeadlineDegradedCount(),
        RealtimeMetricKey.DEADLINE_RETRY_LATER_COUNT to context.broker.getDeadlineRetryLaterCount(),
        RealtimeMetricKey.DEADLINE_MISS_ACCEPTED_COUNT to context.broker.getDeadlineMissAcceptedCount(),
        RealtimeMetricKey.AVERAGE_QUEUE_DEPTH to context.broker.getAverageQueueDepth(),
        RealtimeMetricKey.MAX_QUEUE_DEPTH to context.broker.getMaxQueueDepth(),
        RealtimeMetricKey.AVERAGE_REALTIME_SCORE to context.broker.getAverageRealtimeScore(),
        RealtimeMetricKey.AVERAGE_SELECTED_LATENESS_PENALTY to context.broker.getAverageSelectedLatenessPenalty(),
        RealtimeMetricKey.AVERAGE_SELECTED_DEADLINE_SLACK to context.broker.getAverageSelectedDeadlineSlack(),
        RealtimeMetricKey.AVERAGE_CANDIDATE_SCORE_SPREAD to context.broker.getAverageCandidateScoreSpread(),
        RealtimeMetricKey.SCALE_OUT_COUNT to context.broker.getScaleOutCount(),
        RealtimeMetricKey.SCALE_IN_COUNT to context.broker.getScaleInCount(),
        RealtimeMetricKey.ACTIVE_VM_PEAK to context.broker.getActiveVmPeak(),
        RealtimeMetricKey.AUTOSCALING_COST to context.broker.getAutoscalingCost(),
        RealtimeMetricKey.COLD_START_DELAY_TOTAL to context.broker.getColdStartDelayTotal(),
        RealtimeMetricKey.RESOURCE_REJECTED_COUNT to context.broker.getResourceRejectedCount(),
        RealtimeMetricKey.RUNTIME_FAILURE_COUNT to context.broker.getRuntimeFailureCount(),
        RealtimeMetricKey.TIMEOUT_CANCELLED_COUNT to context.broker.getTimeoutCancelledCount(),
        RealtimeMetricKey.MIGRATION_COUNT to context.broker.getMigrationCount(),
        RealtimeMetricKey.CHECKPOINT_RECOVERY_COUNT to context.broker.getCheckpointRecoveryCount(),
        RealtimeMetricKey.RETRY_SUCCESS_RATE to context.broker.getRetrySuccessRate(),
        RealtimeMetricKey.SLA_PENALTY to context.totalSlaPenalty,
        RealtimeMetricKey.PREEMPTED_COUNT to context.broker.getPreemptedCount(),
        RealtimeMetricKey.PREEMPTION_SUCCESS_COUNT to context.broker.getPreemptionSuccessCount(),
        RealtimeMetricKey.PREEMPTION_FAILED_COUNT to context.broker.getPreemptionFailedCount(),
        RealtimeMetricKey.AVERAGE_PREEMPTION_DELAY to context.broker.getAveragePreemptionDelay(),
        RealtimeMetricKey.PREEMPTION_PENALTY to context.broker.getPreemptionPenalty(),
        RealtimeMetricKey.CHECKPOINT_LOSS_TOTAL to context.broker.getCheckpointLossTotal(),
        RealtimeMetricKey.COST_SLA_TRADEOFF_SCORE to
            context.broker.getCostSlaTradeoffScore(
                context.cost,
                context.totalSlaPenalty,
            ),
    )

private fun tenantMetricPairs(
    broker: RealtimeBroker,
    cloudletList: List<Cloudlet>,
    tenantSlaPenalty: Double,
): List<MetricPair> =
    listOf(
        RealtimeMetricKey.TENANT_QUOTA_REJECTED_COUNT to broker.getTenantQuotaRejectedCount(),
        RealtimeMetricKey.TENANT_BUDGET_REJECTED_COUNT to broker.getTenantBudgetRejectedCount(),
        RealtimeMetricKey.TENANT_FAIRNESS_INDEX to broker.getTenantFairnessIndex(cloudletList),
        RealtimeMetricKey.FAIRNESS_VIOLATION_COUNT to broker.getFairnessViolationCount(),
        RealtimeMetricKey.TENANT_SLA_PENALTY to tenantSlaPenalty,
        RealtimeMetricKey.DOMINANT_RESOURCE_FAIRNESS_INDEX to broker.getDominantResourceFairnessIndex(),
        RealtimeMetricKey.RETRY_SUCCESS_BY_TENANT to broker.getRetrySuccessByTenant(cloudletList),
    )

private fun topologyMetricPairs(
    broker: RealtimeBroker,
    topologyMetrics: scheduler.RealtimeTopologyMetrics,
): List<MetricPair> =
    listOf(
        RealtimeMetricKey.CROSS_RACK_ASSIGNMENT_COUNT to topologyMetrics.crossRackAssignmentCount,
        RealtimeMetricKey.CROSS_REGION_ASSIGNMENT_COUNT to topologyMetrics.crossRegionAssignmentCount,
        RealtimeMetricKey.AVERAGE_TOPOLOGY_LATENCY to topologyMetrics.averageTopologyLatency,
        RealtimeMetricKey.TOPOLOGY_COST to topologyMetrics.topologyCost,
        RealtimeMetricKey.HOST_FAILURE_COUNT to broker.getHostFailureCount(),
        RealtimeMetricKey.RACK_FAILURE_COUNT to broker.getRackFailureCount(),
        RealtimeMetricKey.REGION_FAILURE_COUNT to broker.getRegionFailureCount(),
        RealtimeMetricKey.FAILURE_DOMAIN_SPREAD_SCORE to topologyMetrics.failureDomainSpreadScore,
    )

private fun slaViolationRate(
    violationCount: Int,
    completedCount: Int,
): Double =
    if (completedCount > 0) {
        violationCount.toDouble() / completedCount.toDouble()
    } else {
        0.0
    }

private fun Cloudlet.costPerSecond(): Double =
    when {
        vm.mips == config.DatacenterConfig.L_MIPS.toDouble() -> config.DatacenterConfig.L_PRICE
        vm.mips == config.DatacenterConfig.M_MIPS.toDouble() -> config.DatacenterConfig.M_PRICE
        vm.mips == config.DatacenterConfig.H_MIPS.toDouble() -> config.DatacenterConfig.H_PRICE
        else -> config.DatacenterConfig.L_PRICE
    }

private fun DoubleArray.loadBalance(): Double {
    if (isEmpty()) return 0.0
    val avgExecuteTime = average()
    return sqrt(fold(0.0) { acc, value -> acc + (value - avgExecuteTime).pow(2.0) } / size)
}

private fun List<Double>.averageOrZero(): Double = takeIf { it.isNotEmpty() }?.average() ?: 0.0

private fun List<Double>.percentile(percentile: Double): Double {
    if (isEmpty()) return 0.0
    val sorted = sorted()
    val index = ((sorted.size - 1) * percentile).roundToInt().coerceIn(sorted.indices)
    return sorted[index]
}
