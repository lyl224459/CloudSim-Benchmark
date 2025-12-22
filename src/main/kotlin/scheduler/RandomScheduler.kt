package scheduler

import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import java.util.*

/**
 * 随机调度器
 * 随机分配任务到虚拟机
 */
class RandomScheduler(
    cloudletList: List<Cloudlet>,
    vmList: List<Vm>,
    private val random: Random = Random(0L)
) : Scheduler(cloudletList, vmList) {
    
    override fun allocate(): IntArray {
        val cloudletToVm = IntArray(cloudletNum)
        for (i in 0 until cloudletNum) {
            cloudletToVm[i] = random.nextInt(vmNum)
        }
        return cloudletToVm
    }
}

