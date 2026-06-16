package datacenter

import config.BatchConfig
import config.DatacenterConfig
import org.cloudsimplus.brokers.DatacenterBrokerSimple
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.core.CloudSimPlus
import org.cloudsimplus.vms.Vm
import scheduler.Scheduler
import util.Logger
import util.mapCloudletsToVmIndexes
import java.text.DecimalFormat
import java.util.Random

internal class BatchAlgorithmExecutor(
    private val config: BatchConfig,
) : BatchExecutionService {
    private val decimalFormat = DecimalFormat("###.##")

    override fun run(
        algorithmName: String,
        runSeed: Long,
        schedulerFactory: (List<Cloudlet>, List<Vm>) -> Scheduler,
    ): AlgorithmResult {
        logAlgorithmHeader(algorithmName)
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "Datacenter0", DatacenterType.LOW)
        DatacenterCreator.createDatacenter(simulation, "Datacenter1", DatacenterType.MEDIUM)
        DatacenterCreator.createDatacenter(simulation, "Datacenter2", DatacenterType.HIGH)

        val broker = DatacenterBrokerSimple(simulation)
        val vmList = DatacenterCreator.createVms()
        broker.submitVmList(vmList)
        val cloudletList =
            CloudletGenerator(Random(runSeed), config.generatorType, config.googleTraceConfig)
                .createCloudlets(0, config.cloudletCount)
        broker.submitCloudletList(cloudletList)
        schedulerFactory(cloudletList, vmList).schedule()
        simulation.start()

        val finishedCloudlets = broker.getCloudletFinishedList<Cloudlet>()
        val metrics = BatchExecutionMetricsCalculator.calculate(finishedCloudlets, vmList)
        val allocation = mapCloudletsToVmIndexes(cloudletList, finishedCloudlets, vmList)
        val objective = SchedulerObjectiveFunction(cloudletList, vmList, config.objectiveWeights)
        return AlgorithmResult(
            algorithmName = algorithmName,
            makespan = metrics.makespan,
            loadBalance = metrics.loadBalance,
            cost = metrics.cost,
            totalTime = objective.estimateTotalTime(allocation),
            fitness = objective.calculate(allocation),
        ).also(::logResult)
    }

    private fun logAlgorithmHeader(algorithmName: String) {
        Logger.info("\n${"=".repeat(SECTION_SEPARATOR_WIDTH)}")
        Logger.info("运行算法: {}", algorithmName)
        Logger.info("${"=".repeat(SECTION_SEPARATOR_WIDTH)}")
    }

    private fun logResult(result: AlgorithmResult) {
        Logger.info("\n结果:")
        Logger.info("  最大完成时间 (Makespan): {}", decimalFormat.format(result.makespan))
        Logger.info("  负载均衡度 (LB): {}", decimalFormat.format(result.loadBalance))
        Logger.info("  总成本 (Cost): {}", decimalFormat.format(result.cost))
        Logger.info("  总时间 (TotalTime): {}", decimalFormat.format(result.totalTime))
        Logger.info("  适应度 (Fitness): {}", decimalFormat.format(result.fitness))
    }

    private companion object {
        const val SECTION_SEPARATOR_WIDTH = 60
    }
}

internal object BatchExecutionMetricsCalculator {
    fun calculate(
        cloudlets: List<Cloudlet>,
        vms: List<Vm>,
    ): BatchExecutionMetrics {
        var makespan = 0.0
        var cost = 0.0
        val executionTimes = DoubleArray(vms.size)
        val vmIndexById = vms.mapIndexed { index, vm -> vm.id to index }.toMap()
        cloudlets.filter { it.status == Cloudlet.Status.SUCCESS }.forEach { cloudlet ->
            makespan = maxOf(makespan, cloudlet.finishTime)
            val executionTime = cloudlet.getTotalExecutionTime()
            executionTimes[vmIndexById[cloudlet.vm.id] ?: 0] += executionTime
            cost += executionTime * cloudlet.vm.costPerSecond()
        }
        val average = executionTimes.average()
        val loadBalance = kotlin.math.sqrt(executionTimes.sumOf { (it - average) * (it - average) } / vms.size)
        return BatchExecutionMetrics(makespan, loadBalance, cost)
    }

    private fun Vm.costPerSecond(): Double =
        when (mips) {
            DatacenterConfig.L_MIPS.toDouble() -> DatacenterConfig.L_PRICE
            DatacenterConfig.M_MIPS.toDouble() -> DatacenterConfig.M_PRICE
            DatacenterConfig.H_MIPS.toDouble() -> DatacenterConfig.H_PRICE
            else -> DatacenterConfig.L_PRICE
        }
}

internal data class BatchExecutionMetrics(
    val makespan: Double,
    val loadBalance: Double,
    val cost: Double,
)
