package broker

import config.RealtimeSchedulingConfig
import org.cloudsimplus.cloudlets.Cloudlet
import scheduler.CloudletId
import scheduler.RealtimeTopologyModel

private const val RUNTIME_FAILURE_SALT = 61
private const val RUNTIME_FAILURE_DELAY_SALT = 67
private const val TOPOLOGY_FAILURE_DOMAIN_SALT = 71
private const val FAILURE_DELAY_MIN_RATIO = 0.25
private const val FAILURE_DELAY_JITTER_RATIO = 0.5
private const val MIN_RUNTIME_FAILURE_DELAY = 0.001

internal data class RealtimeRuntimeEventPlan(
    val commands: List<RealtimeBrokerCommand>,
    val topologyFailureDomain: RealtimeFailureDomain?,
)

internal data class RealtimeRuntimeEventTiming(
    val arrivalTime: Double,
    val currentTime: Double,
)

internal data class RealtimeRuntimeEventAssignment(
    val vmIndex: Int,
    val nodeFailurePressure: Double,
)

internal class RealtimeRuntimeEventController(
    private val scheduling: RealtimeSchedulingConfig,
    private val topologyModel: RealtimeTopologyModel,
    private val deterministicUnit: (CloudletId, Int, Int) -> Double,
    private val runtimeEstimator: (Cloudlet) -> Double,
) {
    fun planRuntimeEvents(
        cloudlet: Cloudlet,
        attempt: Int,
        runtimeToken: Int,
        timing: RealtimeRuntimeEventTiming,
        assignment: RealtimeRuntimeEventAssignment,
    ): RealtimeRuntimeEventPlan {
        var topologyFailureDomain: RealtimeFailureDomain? = null
        val commands =
            buildList {
                timeoutCommand(cloudlet, attempt, runtimeToken, timing)?.let(::add)
                if (shouldScheduleRuntimeFailure(cloudlet, attempt, assignment)) {
                    topologyFailureDomain = topologyFailureDomain(cloudlet, attempt, assignment.vmIndex)
                    add(runtimeFailureCommand(cloudlet, attempt, runtimeToken))
                }
            }
        return RealtimeRuntimeEventPlan(commands, topologyFailureDomain)
    }

    fun effectiveRuntimeFailureRate(
        assignedVmIndex: Int,
        nodeFailurePressure: Double,
    ): Double =
        (
            scheduling.runtimeFailureRate +
                scheduling.nodeFailureRate +
                nodeFailurePressure * scheduling.overloadFailureMultiplier +
                topologyModel.failurePressure(topologyModel.locationOf(assignedVmIndex))
        ).coerceIn(0.0, 1.0)

    private fun timeoutCommand(
        cloudlet: Cloudlet,
        attempt: Int,
        runtimeToken: Int,
        timing: RealtimeRuntimeEventTiming,
    ): RealtimeBrokerCommand.ScheduleTimeout? =
        if (scheduling.taskTimeout > 0.0) {
            RealtimeBrokerCommand.ScheduleTimeout(
                delay = (timing.arrivalTime + scheduling.taskTimeout - timing.currentTime).coerceAtLeast(0.0),
                payload = RealtimeCloudletEventPayload(cloudlet, attempt, runtimeToken),
            )
        } else {
            null
        }

    private fun shouldScheduleRuntimeFailure(
        cloudlet: Cloudlet,
        attempt: Int,
        assignment: RealtimeRuntimeEventAssignment,
    ): Boolean {
        val runtimeFailureRate = effectiveRuntimeFailureRate(assignment.vmIndex, assignment.nodeFailurePressure)
        val unit = deterministicUnit(CloudletId(cloudlet.id), attempt, RUNTIME_FAILURE_SALT)
        return runtimeFailureRate > 0.0 && unit < runtimeFailureRate
    }

    private fun runtimeFailureCommand(
        cloudlet: Cloudlet,
        attempt: Int,
        runtimeToken: Int,
    ): RealtimeBrokerCommand.ScheduleRuntimeFailure {
        val runtime = runtimeEstimator(cloudlet)
        val delayRatio =
            FAILURE_DELAY_MIN_RATIO +
                deterministicUnit(CloudletId(cloudlet.id), attempt, RUNTIME_FAILURE_DELAY_SALT) *
                FAILURE_DELAY_JITTER_RATIO
        return RealtimeBrokerCommand.ScheduleRuntimeFailure(
            delay = (runtime * delayRatio).coerceAtLeast(MIN_RUNTIME_FAILURE_DELAY),
            payload = RealtimeCloudletEventPayload(cloudlet, attempt, runtimeToken),
        )
    }

    private fun topologyFailureDomain(
        cloudlet: Cloudlet,
        attempt: Int,
        assignedVmIndex: Int,
    ): RealtimeFailureDomain? =
        if (scheduling.topologyEnabled || scheduling.physicalTopologyEnabled) {
            classifyTopologyFailure(cloudlet, attempt, assignedVmIndex)
        } else {
            null
        }

    private fun classifyTopologyFailure(
        cloudlet: Cloudlet,
        attempt: Int,
        assignedVmIndex: Int,
    ): RealtimeFailureDomain? {
        val location = topologyModel.locationOf(assignedVmIndex)
        val unit = deterministicUnit(CloudletId(cloudlet.id), attempt, TOPOLOGY_FAILURE_DOMAIN_SALT)
        val hostCutoff = scheduling.hostFailureRate
        val rackCutoff = hostCutoff + scheduling.rackFailureRate
        val regionCutoff =
            rackCutoff +
                if (location.regionId.value != scheduling.localRegion) {
                    scheduling.regionFailureRate
                } else {
                    0.0
                }
        return when {
            unit < hostCutoff -> RealtimeFailureDomain.HOST
            unit < rackCutoff -> RealtimeFailureDomain.RACK
            unit < regionCutoff -> RealtimeFailureDomain.REGION
            else -> null
        }
    }
}
