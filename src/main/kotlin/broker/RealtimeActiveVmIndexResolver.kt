package broker

import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm

internal class RealtimeActiveVmIndexResolver(
    vmList: List<Vm>,
    private val reservationState: RealtimeReservationState,
) {
    private val vmIndexById: Map<Long, Int> = vmList.mapIndexed { index, vm -> vm.id to index }.toMap()

    fun indexesFor(cloudlets: List<Cloudlet>): Set<Int> =
        cloudlets
            .mapNotNull { cloudlet ->
                reservationState.assignedVmIndexOf(cloudlet)
                    ?: cloudlet.vm?.let { vmIndexById[it.id] }
            }.toSet()
}
