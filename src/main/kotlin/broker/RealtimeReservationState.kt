package broker

import org.cloudsimplus.cloudlets.Cloudlet
import scheduler.CloudletId
import scheduler.RealtimeTaskLifecycle
import scheduler.VmIndex

data class RealtimeReservationSnapshot(
    val reservations: Map<CloudletId, VmIndex>,
)

class RealtimeReservationState {
    private val reservations = mutableMapOf<CloudletId, VmIndex>()

    fun reserve(
        cloudlet: Cloudlet,
        vmIndex: Int,
    ) {
        reservations[cloudlet.cloudletId] = VmIndex(vmIndex)
    }

    fun remove(cloudlet: Cloudlet) {
        reservations.remove(cloudlet.cloudletId)
    }

    fun assignedVmIndexOf(cloudlet: Cloudlet): Int? = reservations[cloudlet.cloudletId]?.value

    fun rawReservations(): Map<Long, Int> = reservations.mapKeys { it.key.value }.mapValues { it.value.value }

    fun prune(lifecycleOf: (CloudletId) -> RealtimeTaskLifecycle?) {
        reservations.keys.removeIf { cloudletId ->
            lifecycleOf(cloudletId) in setOf(RealtimeTaskLifecycle.REJECTED, RealtimeTaskLifecycle.FAILED)
        }
    }

    fun snapshot(): RealtimeReservationSnapshot = RealtimeReservationSnapshot(reservations = reservations.toMap())

    private val Cloudlet.cloudletId: CloudletId get() = CloudletId(id)
}
