package datacenter

import config.DatacenterConfig
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
                    DatacenterConfig.RAM * DatacenterConfig.L_VM_N,
                    DatacenterConfig.BW * DatacenterConfig.L_VM_N,
                    DatacenterConfig.L_MIPS * DatacenterConfig.L_VM_N,
                    DatacenterConfig.STORAGE * DatacenterConfig.L_VM_N,
                    DatacenterConfig.L_PRICE
                )
            }
            DatacenterType.MEDIUM -> {
                Tuple5(
                    DatacenterConfig.RAM * DatacenterConfig.M_VM_N,
                    DatacenterConfig.BW * DatacenterConfig.M_VM_N,
                    DatacenterConfig.M_MIPS * DatacenterConfig.M_VM_N,
                    DatacenterConfig.STORAGE * DatacenterConfig.M_VM_N,
                    DatacenterConfig.M_PRICE
                )
            }
            DatacenterType.HIGH -> {
                Tuple5(
                    DatacenterConfig.RAM * DatacenterConfig.H_VM_N,
                    DatacenterConfig.BW * DatacenterConfig.H_VM_N,
                    DatacenterConfig.H_MIPS * DatacenterConfig.H_VM_N,
                    DatacenterConfig.STORAGE * DatacenterConfig.H_VM_N,
                    DatacenterConfig.H_PRICE
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
        
        val ram: Long = DatacenterConfig.RAM.toLong()
        val bw: Long = DatacenterConfig.BW.toLong()
        
        // 创建低配置虚拟机
        repeat(DatacenterConfig.L_VM_N) {
            vmList.add(
                VmSimple(DatacenterConfig.L_MIPS.toDouble(), pesNumber)
                    .setRam(ram)
                    .setBw(bw)
                    .setSize(DatacenterConfig.IMAGE_SIZE)
                    .setCloudletScheduler(CloudletSchedulerSpaceShared())
            )
        }
        
        // 创建中配置虚拟机
        repeat(DatacenterConfig.M_VM_N) {
            vmList.add(
                VmSimple(DatacenterConfig.M_MIPS.toDouble(), pesNumber)
                    .setRam(ram)
                    .setBw(bw)
                    .setSize(DatacenterConfig.IMAGE_SIZE)
                    .setCloudletScheduler(CloudletSchedulerSpaceShared())
            )
        }
        
        // 创建高配置虚拟机
        repeat(DatacenterConfig.H_VM_N) {
            vmList.add(
                VmSimple(DatacenterConfig.H_MIPS.toDouble(), pesNumber)
                    .setRam(ram)
                    .setBw(bw)
                    .setSize(DatacenterConfig.IMAGE_SIZE)
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

