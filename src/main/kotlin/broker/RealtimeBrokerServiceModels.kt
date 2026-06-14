package broker

import config.RealtimeSchedulingConfig
import datacenter.MutableRealtimeTraceMetadataProvider
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import scheduler.CloudletId
import scheduler.RealtimeNodeStateTracker
import scheduler.RealtimeResourceModel
import scheduler.RealtimeTopologyModel
import scheduler.RealtimeVmLifecycleManager
import java.util.Random

private const val FAILURE_RANDOM_CLOUDLET_MULTIPLIER = 1_000_003L
private const val FAILURE_RANDOM_ATTEMPT_MULTIPLIER = 9_176L

internal class RealtimeDeterministicSampler {
    private val random = Random(0L)

    fun sample(
        cloudletId: CloudletId,
        attempt: Int,
        salt: Int,
    ): Double {
        random.setSeed(
            cloudletId.value * FAILURE_RANDOM_CLOUDLET_MULTIPLIER +
                attempt * FAILURE_RANDOM_ATTEMPT_MULTIPLIER +
                salt,
        )
        return random.nextDouble()
    }

    fun sample(
        cloudletId: Long,
        attempt: Int,
        salt: Int,
    ): Double = sample(CloudletId(cloudletId), attempt, salt)
}

internal data class RealtimeBrokerStateBundle(
    val arrival: RealtimeArrivalState,
    val lifecycleStore: RealtimeTaskLifecycleStore,
    val reservation: RealtimeReservationState,
    val metrics: RealtimeBrokerMetrics,
)

internal class RealtimeBrokerEnvironment(
    val topologyModel: RealtimeTopologyModel,
    val vmLifecycleManager: RealtimeVmLifecycleManager,
    val traceMetadataProvider: MutableRealtimeTraceMetadataProvider,
    val nodeStateTracker: RealtimeNodeStateTracker,
) {
    val vmList: List<Vm>
        get() = vmLifecycleManager.vmList
}

internal data class RealtimeBrokerCallbacks(
    val clock: () -> Double,
    val applyCommands: (Iterable<RealtimeBrokerCommand>) -> Unit,
    val submitCloudlet: (Cloudlet) -> Unit,
)

internal fun realtimeBrokerEnvironment(
    schedulingConfig: RealtimeSchedulingConfig,
    initialVmList: List<Vm>,
    traceMetadataProvider: MutableRealtimeTraceMetadataProvider,
): RealtimeBrokerEnvironment {
    val topologyModel = RealtimeTopologyModel.fromConfig(schedulingConfig, initialVmList.size)
    val vmLifecycleManager = RealtimeVmLifecycleManager(initialVmList, schedulingConfig, topologyModel)
    return RealtimeBrokerEnvironment(
        topologyModel = topologyModel,
        vmLifecycleManager = vmLifecycleManager,
        traceMetadataProvider = traceMetadataProvider,
        nodeStateTracker =
            RealtimeNodeStateTracker(
                vmLifecycleManager.vmList,
                schedulingConfig.vmQueueCapacity,
                RealtimeResourceModel(
                    enabled = schedulingConfig.resourceModelEnabled,
                    networkLatency = schedulingConfig.networkLatency,
                    imagePullDelay = schedulingConfig.imagePullDelay,
                    ioWeight = schedulingConfig.ioWeight,
                    ramWeight = schedulingConfig.ramWeight,
                    bwWeight = schedulingConfig.bwWeight,
                    traceMetadataProvider = traceMetadataProvider,
                ),
                topologyModel,
            ),
    )
}
