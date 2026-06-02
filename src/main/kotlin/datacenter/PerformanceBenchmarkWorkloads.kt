package datacenter

import config.ObjectiveWeightsConfig
import org.cloudsimplus.cloudlets.Cloudlet
import scheduler.AlgorithmRegistry
import scheduler.RealtimeScheduler
import scheduler.ResolvedAlgorithmSettings
import java.util.Random

class ObjectiveBenchmarkCase(
    private val objective: SchedulerObjectiveFunction,
    private val allocation: IntArray,
) {
    fun calculate(): Double = objective.calculate(allocation)
}

object PerformanceBenchmarkWorkloads {
    @JvmStatic
    fun objectiveCase(cloudletCount: Int): ObjectiveBenchmarkCase {
        val cloudlets = CloudletGenerator(Random(FIXED_SEED)).createCloudlets(userId = 0, count = cloudletCount)
        val vms = DatacenterCreator.createVms()
        val allocation = IntArray(cloudlets.size) { index -> index % vms.size }
        return ObjectiveBenchmarkCase(SchedulerObjectiveFunction(cloudlets, vms, ObjectiveWeightsConfig()), allocation)
    }

    @JvmStatic
    fun runBatchAlgorithm(
        algorithm: String,
        cloudletCount: Int,
        population: Int,
        maxIter: Int,
        seed: Long,
    ) {
        val cloudlets = CloudletGenerator(Random(seed)).createCloudlets(userId = 0, count = cloudletCount)
        val vms = DatacenterCreator.createVms()
        val scheduler =
            AlgorithmRegistry
                .resolveBatch(algorithm)
                .createBatchScheduler(
                    cloudlets = cloudlets,
                    vms = vms,
                    objectiveWeights = ObjectiveWeightsConfig(),
                    settings = ResolvedAlgorithmSettings(population, maxIter),
                    seed = seed,
                )
        scheduler.schedule()
    }

    @JvmStatic
    fun runRealtimeAlgorithm(
        algorithm: String,
        cloudletCount: Int,
        population: Int,
        maxIter: Int,
        seed: Long,
    ) {
        val vms = DatacenterCreator.createVms()
        val scheduler =
            AlgorithmRegistry
                .resolveRealtime(algorithm)
                .createRealtimeScheduler(
                    vms = vms,
                    objectiveWeights = ObjectiveWeightsConfig(),
                    settings = ResolvedAlgorithmSettings(population, maxIter),
                    seed = seed,
                )
        val cloudlets =
            RealtimeCloudletGenerator(
                random = Random(seed),
                arrivalRate = cloudletCount.toDouble().coerceAtLeast(1.0),
            ).createRealtimeCloudletBatch(userId = 0, count = cloudletCount, simulationDuration = 1.0).cloudlets
        scheduleRealtimeCloudlets(scheduler, cloudlets, vms)
    }

    private fun scheduleRealtimeCloudlets(
        scheduler: RealtimeScheduler,
        cloudlets: List<Cloudlet>,
        vms: List<org.cloudsimplus.vms.Vm>,
    ) {
        val assignedCloudlets = ArrayList<Cloudlet>(cloudlets.size)
        cloudlets.forEach { cloudlet ->
            val vmIndex = scheduler.scheduleOnArrival(cloudlet, assignedCloudlets, vms).coerceIn(vms.indices)
            cloudlet.setVm(vms[vmIndex])
            assignedCloudlets.add(cloudlet)
        }
    }

    private const val FIXED_SEED = 0L
}
