package util

import org.cloudsimplus.cloudlets.Cloudlet

fun mapCloudletsToVmIds(
    originalCloudlets: List<Cloudlet>,
    finishedCloudlets: List<Cloudlet>
): IntArray {
    val finishedById = finishedCloudlets.associateBy { it.id }
    return IntArray(originalCloudlets.size) { index ->
        finishedById[originalCloudlets[index].id]?.vm?.id?.toInt() ?: 0
    }
}
