package datacenter

import broker.RealtimeBroker
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.core.CloudSimPlus
import org.cloudsimplus.vms.Vm
import scheduler.RealtimeScheduler
import util.Logger
import java.text.DecimalFormat
import java.util.Random

internal data class RealtimeExperimentConfigSnapshot(
    val cloudletCount: Int,
    val simulationDuration: Double,
    val arrivalRate: Double,
    val generatorType: config.CloudletGeneratorType,
    val googleTraceConfig: config.GoogleTraceConfig?,
    val arrival: config.RealtimeArrivalConfig,
    val scheduling: config.RealtimeSchedulingConfig,
)

internal data class RealtimeExperimentRunRequest(
    val algorithmName: String,
    val runSeed: Long,
    val schedulerFactory: (List<Vm>) -> RealtimeScheduler,
)

internal data class RealtimeExperimentRunContext(
    val simulation: CloudSimPlus,
    val vmList: List<Vm>,
    val broker: RealtimeBroker,
    val cloudletBatch: RealtimeCloudletBatch,
) {
    val cloudletList: List<Cloudlet> = cloudletBatch.cloudlets
}

internal class RealtimeExperimentRunner(
    private val config: RealtimeExperimentConfigSnapshot,
    private val metricsCollector: RealtimeMetricsCollector,
    private val dft: DecimalFormat = DecimalFormat("###.##"),
) {
    fun run(request: RealtimeExperimentRunRequest): RealtimeAlgorithmResult {
        Logger.info("\n${"=".repeat(60)}")
        Logger.info("运行实时调度算法: {}", request.algorithmName)
        Logger.info("${"=".repeat(60)}")

        val context = createRunContext(request)
        Logger.info("已生成 {} 个实时任务", context.cloudletList.size)
        Logger.info("仿真持续时间: {} 秒", config.simulationDuration)
        context.simulation.start()

        val finishedCloudlets = context.broker.getCloudletFinishedList<Cloudlet>()
        val result =
            metricsCollector.collect(
                RealtimeMetricCollectionRequest(
                    algorithmName = request.algorithmName,
                    cloudletList = context.cloudletList,
                    finishedCloudlets = finishedCloudlets,
                    vmList = context.vmList,
                    broker = context.broker,
                ),
            )

        logResult(result)
        return result
    }

    private fun createRunContext(request: RealtimeExperimentRunRequest): RealtimeExperimentRunContext {
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "Datacenter0", DatacenterType.LOW)
        DatacenterCreator.createDatacenter(simulation, "Datacenter1", DatacenterType.MEDIUM)
        DatacenterCreator.createDatacenter(simulation, "Datacenter2", DatacenterType.HIGH)

        val vmList = DatacenterCreator.createVms()
        val scheduler = request.schedulerFactory(vmList)
        val broker = RealtimeBroker(simulation, scheduler, vmList, config.scheduling)
        broker.submitVmList(vmList)

        val random = Random(request.runSeed)
        val cloudletGenerator =
            RealtimeCloudletGenerator(
                random = random,
                arrivalRate = config.arrivalRate,
                generatorType = config.generatorType,
                arrivalConfig = config.arrival,
                googleTraceConfig = config.googleTraceConfig,
            )
        val cloudletBatch = cloudletGenerator.createRealtimeCloudletBatch(0, config.cloudletCount, config.simulationDuration)
        broker.submitCloudletBatchRealtime(cloudletBatch)

        return RealtimeExperimentRunContext(
            simulation = simulation,
            vmList = vmList,
            broker = broker,
            cloudletBatch = cloudletBatch,
        )
    }

    private fun logResult(result: RealtimeAlgorithmResult) {
        Logger.info("\n结果:")
        Logger.info("  最大完成时间 (Makespan): {}", dft.format(result.makespan))
        Logger.info("  负载均衡度 (LB): {}", dft.format(result.loadBalance))
        Logger.info("  总成本 (Cost): {}", dft.format(result.cost))
        Logger.info("  平均等待时间: {}", dft.format(result.averageWaitingTime))
        Logger.info("  平均响应时间: {}", dft.format(result.averageResponseTime))
        Logger.info(
            "  Reject/Timeout/Failed/Retry/PermanentFailed: {}/{}/{}/{}/{}",
            result.rejectedCount,
            result.timeoutCount,
            result.failedCount,
            result.retryCount,
            result.permanentFailedCount,
        )
        Logger.info("  平均调度决策延迟: {}", dft.format(result.averageDecisionDelay))
        Logger.info(
            "  SLA/容量/队列: violation={} ({}), capacityRejected={}, avgQueueDepth={}, maxQueueDepth={}, p95Response={}",
            result.slaViolationCount,
            dft.format(result.slaViolationRate),
            result.capacityRejectedCount,
            dft.format(result.averageQueueDepth),
            result.maxQueueDepth,
            dft.format(result.p95ResponseTime),
        )
        Logger.info("  适应度 (Fitness): {}", dft.format(result.fitness))
    }
}
