package broker

import org.cloudsimplus.cloudlets.Cloudlet
import scheduler.RealtimeTaskRecord

internal class RealtimeRuntimeEventPlanner(
    private val state: RealtimeBrokerStateBundle,
    private val environment: RealtimeBrokerEnvironment,
    private val runtimeEventController: RealtimeRuntimeEventController,
    private val topologyAccountingController: RealtimeTopologyAccountingController,
    private val callbacks: RealtimeBrokerCallbacks,
) {
    fun recordSubmission(
        vmIndex: Int,
        record: RealtimeTaskRecord,
    ) {
        topologyAccountingController.recordSubmission(vmIndex, record)
    }

    fun planRuntimeEvents(
        cloudlet: Cloudlet,
        submissionFailurePressure: Double,
    ): List<RealtimeBrokerCommand> {
        val attempt = state.arrival.attemptOf(cloudlet)
        val runtimeToken = state.arrival.issueRuntimeToken(cloudlet)
        val assignedVmIndex = state.lifecycleStore.get(cloudlet.id)?.assignedVmIndex ?: 0
        val pressure = runtimeFailurePressure(assignedVmIndex, submissionFailurePressure)
        val plan =
            runtimeEventController.planRuntimeEvents(
                cloudlet = cloudlet,
                attempt = attempt,
                runtimeToken = runtimeToken,
                timing =
                    RealtimeRuntimeEventTiming(
                        arrivalTime = state.arrival.arrivalTimeOf(cloudlet),
                        currentTime = callbacks.clock(),
                    ),
                assignment =
                    RealtimeRuntimeEventAssignment(
                        vmIndex = assignedVmIndex,
                        nodeFailurePressure = pressure,
                    ),
            )
        topologyAccountingController.recordFailure(plan.topologyFailureDomain)
        return plan.commands
    }

    private fun runtimeFailurePressure(
        assignedVmIndex: Int,
        submissionFailurePressure: Double,
    ): Double {
        val states =
            environment.nodeStateTracker.snapshot(
                activeCloudlets(),
                callbacks.clock(),
                state.reservation.rawReservations(),
                environment.vmLifecycleManager.snapshots(),
            )
        return states.getOrNull(assignedVmIndex)?.failurePressure ?: submissionFailurePressure
    }

    private fun activeCloudlets(): List<Cloudlet> =
        state.arrival
            .queuedCloudletsSnapshot()
            .filterNot(Cloudlet::isTerminalRealtimeCloudlet)
}
