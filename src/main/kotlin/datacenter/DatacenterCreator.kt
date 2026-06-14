package datacenter

import config.DatacenterConfig
import org.cloudsimplus.core.CloudSimPlus
import org.cloudsimplus.datacenters.Datacenter
import org.cloudsimplus.datacenters.DatacenterSimple
import org.cloudsimplus.hosts.HostSimple
import org.cloudsimplus.resources.PeSimple
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared
import org.cloudsimplus.vms.Vm
import org.cloudsimplus.vms.VmSimple

/**
 * 数据中心创建器
 */
object DatacenterCreator {
    /**
     * 创建数据中心
     */
    fun createDatacenter(
        simulation: CloudSimPlus,
        name: String,
        type: DatacenterType,
    ): Datacenter {
        val capacity = capacityFor(type)

        val peList = listOf(PeSimple(capacity.mips.toDouble()))
        val hostList =
            listOf(
                HostSimple(capacity.ram.toLong(), capacity.bw.toLong(), capacity.storage, peList)
                    .setVmScheduler(VmSchedulerTimeShared()),
            )

        val datacenter = DatacenterSimple(simulation, hostList).setSchedulingInterval(1.0)
        datacenter.name = name
        return datacenter
    }

    /**
     * 创建虚拟机列表
     */
    fun createVms(): List<Vm> {
        val vmList = mutableListOf<Vm>()
        val pesNumber: Long = 1L

        val ram: Long = DatacenterConfig.RAM.toLong()
        val bw: Long = DatacenterConfig.BW.toLong()

        // 创建低配置虚拟机
        repeat(DatacenterConfig.L_VM_N) {
            vmList.add(
                VmSimple(DatacenterConfig.L_MIPS.toDouble(), pesNumber)
                    .setRam(ram)
                    .setBw(bw)
                    .setSize(DatacenterConfig.IMAGE_SIZE)
                    .setCloudletScheduler(CloudletSchedulerSpaceShared()),
            )
        }

        // 创建中配置虚拟机
        repeat(DatacenterConfig.M_VM_N) {
            vmList.add(
                VmSimple(DatacenterConfig.M_MIPS.toDouble(), pesNumber)
                    .setRam(ram)
                    .setBw(bw)
                    .setSize(DatacenterConfig.IMAGE_SIZE)
                    .setCloudletScheduler(CloudletSchedulerSpaceShared()),
            )
        }

        // 创建高配置虚拟机
        repeat(DatacenterConfig.H_VM_N) {
            vmList.add(
                VmSimple(DatacenterConfig.H_MIPS.toDouble(), pesNumber)
                    .setRam(ram)
                    .setBw(bw)
                    .setSize(DatacenterConfig.IMAGE_SIZE)
                    .setCloudletScheduler(CloudletSchedulerSpaceShared()),
            )
        }

        return vmList
    }

    private fun capacityFor(type: DatacenterType): DatacenterCapacity =
        when (type) {
            DatacenterType.LOW -> capacityFor(DatacenterConfig.L_VM_N, DatacenterConfig.L_MIPS)
            DatacenterType.MEDIUM -> capacityFor(DatacenterConfig.M_VM_N, DatacenterConfig.M_MIPS)
            DatacenterType.HIGH -> capacityFor(DatacenterConfig.H_VM_N, DatacenterConfig.H_MIPS)
        }

    private fun capacityFor(
        vmCount: Int,
        mipsPerVm: Int,
    ): DatacenterCapacity =
        DatacenterCapacity(
            ram = DatacenterConfig.RAM * vmCount,
            bw = DatacenterConfig.BW * vmCount,
            mips = mipsPerVm * vmCount,
            storage = DatacenterConfig.STORAGE * vmCount,
        )
}

private data class DatacenterCapacity(
    val ram: Int,
    val bw: Int,
    val mips: Int,
    val storage: Long,
)
