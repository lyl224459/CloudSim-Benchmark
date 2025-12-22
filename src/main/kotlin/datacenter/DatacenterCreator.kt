package datacenter

import org.cloudsimplus.brokers.DatacenterBroker
import org.cloudsimplus.brokers.DatacenterBrokerSimple
import org.cloudsimplus.core.CloudSimPlus
import org.cloudsimplus.datacenters.Datacenter
import org.cloudsimplus.datacenters.DatacenterSimple
import org.cloudsimplus.hosts.Host
import org.cloudsimplus.hosts.HostSimple
import org.cloudsimplus.resources.Pe
import org.cloudsimplus.resources.PeSimple
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared
import org.cloudsimplus.vms.Vm
import org.cloudsimplus.vms.VmSimple
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared

/**
 * 数据中心创建器
 */
object DatacenterCreator {
    
    /**
     * 创建数据中心
     */
    fun createDatacenter(simulation: CloudSimPlus, name: String, type: DatacenterType): Datacenter {
        val (ram, bw, mips, storage, costPerSec) = when (type) {
            DatacenterType.LOW -> {
                Tuple5(
                    Constants.RAM * Constants.L_VM_N,
                    Constants.BW * Constants.L_VM_N,
                    Constants.L_MIPS * Constants.L_VM_N,
                    Constants.STORAGE * Constants.L_VM_N,
                    Constants.L_PRICE
                )
            }
            DatacenterType.MEDIUM -> {
                Tuple5(
                    Constants.RAM * Constants.M_VM_N,
                    Constants.BW * Constants.M_VM_N,
                    Constants.M_MIPS * Constants.M_VM_N,
                    Constants.STORAGE * Constants.M_VM_N,
                    Constants.M_PRICE
                )
            }
            DatacenterType.HIGH -> {
                Tuple5(
                    Constants.RAM * Constants.H_VM_N,
                    Constants.BW * Constants.H_VM_N,
                    Constants.H_MIPS * Constants.H_VM_N,
                    Constants.STORAGE * Constants.H_VM_N,
                    Constants.H_PRICE
                )
            }
        }
        
        val peList = listOf(PeSimple(mips.toDouble()))
        val hostList = listOf(
            HostSimple(ram.toLong(), bw.toLong(), storage, peList)
                .setVmScheduler(VmSchedulerTimeShared())
        )
        
        return DatacenterSimple(simulation, hostList)
            .setSchedulingInterval(1.0)
    }
    
    /**
     * 创建虚拟机列表
     */
    fun createVms(): List<Vm> {
        val vmList = mutableListOf<Vm>()
        val pesNumber: Long = 1L
        
        val ram: Long = Constants.RAM.toLong()
        val bw: Long = Constants.BW.toLong()
        
        // 创建低配置虚拟机
        repeat(Constants.L_VM_N) {
            vmList.add(
                VmSimple(Constants.L_MIPS.toDouble(), pesNumber)
                    .setRam(ram)
                    .setBw(bw)
                    .setSize(Constants.IMAGE_SIZE)
                    .setCloudletScheduler(CloudletSchedulerSpaceShared())
            )
        }
        
        // 创建中配置虚拟机
        repeat(Constants.M_VM_N) {
            vmList.add(
                VmSimple(Constants.M_MIPS.toDouble(), pesNumber)
                    .setRam(ram)
                    .setBw(bw)
                    .setSize(Constants.IMAGE_SIZE)
                    .setCloudletScheduler(CloudletSchedulerSpaceShared())
            )
        }
        
        // 创建高配置虚拟机
        repeat(Constants.H_VM_N) {
            vmList.add(
                VmSimple(Constants.H_MIPS.toDouble(), pesNumber)
                    .setRam(ram)
                    .setBw(bw)
                    .setSize(Constants.IMAGE_SIZE)
                    .setCloudletScheduler(CloudletSchedulerSpaceShared())
            )
        }
        
        return vmList
    }
}

/**
 * 简单的元组类
 */
private data class Tuple5<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

