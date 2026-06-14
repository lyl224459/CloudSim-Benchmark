package broker

import org.cloudsimplus.cloudlets.Cloudlet

internal class RealtimeQueueDepthSampler(
    private val state: RealtimeBrokerStateBundle,
    private val environment: RealtimeBrokerEnvironment,
) {
    fun queueDepthFor(
        activeCloudlets: List<Cloudlet>,
        selectedVmIndex: Int,
        currentTime: Double,
    ): Int {
        val states =
            environment.nodeStateTracker.snapshot(
                activeCloudlets,
                currentTime,
                state.reservation.rawReservations(),
                environment.vmLifecycleManager.snapshots(),
            )
        return states.getOrNull(selectedVmIndex)?.queueDepth ?: 0
    }
}
