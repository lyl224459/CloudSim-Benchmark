package util

import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm

fun mapCloudletsToVmIndexes(
    originalCloudlets: List<Cloudlet>,
    finishedCloudlets: List<Cloudlet>,
    vmList: List<Vm>,
): IntArray {
    val finishedById = finishedCloudlets.associateBy { it.id }
    val vmIndexById = vmList.mapIndexed { index, vm -> vm.id to index }.toMap()
    return IntArray(originalCloudlets.size) { index ->
        val vmId = finishedById[originalCloudlets[index].id]?.vm?.id
        vmIndexById[vmId] ?: 0
    }
}
