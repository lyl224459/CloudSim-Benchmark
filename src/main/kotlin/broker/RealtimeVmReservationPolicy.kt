package broker

import config.RealtimeSchedulingConfig
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm

internal class RealtimeVmReservationPolicy(
    private val schedulingConfig: RealtimeSchedulingConfig,
) {
    fun select(
        selectedVmId: Int,
        cloudlet: Cloudlet,
        activeCloudlets: List<Cloudlet>,
        vmList: List<Vm>,
    ): Int =
        when (schedulingConfig.resourceReservation.lowercase()) {
            "partial" -> applyPartialReservation(selectedVmId, activeCloudlets, vmList)
            "full" -> applyFullReservation(cloudlet, selectedVmId, activeCloudlets, vmList)
            else -> selectedVmId
        }

    private fun applyPartialReservation(
        selectedVmId: Int,
        activeCloudlets: List<Cloudlet>,
        vmList: List<Vm>,
    ): Int {
        val selectedVm = vmList[selectedVmId.coerceIn(vmList.indices)]
        val highestMips = vmList.maxOf { it.mips }
        val hasIdleNonHigh =
            vmList.any { vm ->
                vm.mips < highestMips && activeCloudlets.none { cloudlet -> cloudlet.vm?.id == vm.id }
            }
        if (selectedVm.mips == highestMips && hasIdleNonHigh) {
            return vmList
                .indexOfFirst { it.mips < highestMips }
                .takeIf { it >= 0 } ?: selectedVmId
        }
        return selectedVmId
    }

    private fun applyFullReservation(
        cloudlet: Cloudlet,
        selectedVmId: Int,
        activeCloudlets: List<Cloudlet>,
        vmList: List<Vm>,
    ): Int =
        desiredFullReservationVmIds(cloudlet, selectedVmId, vmList)
            .minByOrNull { vmId ->
                activeCloudlets.count { it.vm?.id?.toInt() == vmId }
            } ?: selectedVmId

    private fun desiredFullReservationVmIds(
        cloudlet: Cloudlet,
        selectedVmId: Int,
        vmList: List<Vm>,
    ): List<Int> {
        val groups = vmList.groupBy { it.mips }.toSortedMap()
        val desiredGroup =
            when {
                cloudlet.length < SHORT_TASK_LENGTH -> groups.keys.firstOrNull()
                cloudlet.length < MEDIUM_TASK_LENGTH -> groups.keys.elementAtOrNull(1) ?: groups.keys.firstOrNull()
                else -> groups.keys.lastOrNull()
            } ?: return listOf(selectedVmId)

        return groups[desiredGroup]
            .orEmpty()
            .map { it.id.toInt() }
            .ifEmpty { listOf(selectedVmId) }
    }

    private companion object {
        const val SHORT_TASK_LENGTH = 20_000L
        const val MEDIUM_TASK_LENGTH = 40_000L
    }
}
