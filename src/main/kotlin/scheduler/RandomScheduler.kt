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
    objectiveWeights: config.ObjectiveWeightsConfig = config.ObjectiveWeightsConfig(),
    private val random: Random = Random(config.DatacenterConfig.DEFAULT_RANDOM_SEED)
) : Scheduler(cloudletList, vmList, objectiveWeights) {
    
    override fun allocate(): IntArray {
        val cloudletToVm = IntArray(cloudletNum)
        for (i in 0 until cloudletNum) {
            cloudletToVm[i] = random.nextInt(vmNum)
        }
        return cloudletToVm
    }
}

