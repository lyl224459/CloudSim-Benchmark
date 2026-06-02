package scheduler

import datacenter.RealtimeTraceMetadataProvider
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm

data class RealtimeResourceDemand(
    val cpu: Double = 0.0,
    val ram: Double = 0.0,
    val bw: Double = 0.0,
    val io: Double = 0.0,
) {
    operator fun plus(other: RealtimeResourceDemand): RealtimeResourceDemand =
        RealtimeResourceDemand(
            cpu = cpu + other.cpu,
            ram = ram + other.ram,
            bw = bw + other.bw,
            io = io + other.io,
        )
}

data class RealtimePhysicalHostMetrics(
    val averageUtilization: Double,
    val averageFragmentation: Double,
)

data class RealtimeResourceModel(
    val enabled: Boolean,
    val networkLatency: Double,
    val imagePullDelay: Double,
    val ioWeight: Double,
    val ramWeight: Double,
    val bwWeight: Double,
    val traceMetadataProvider: RealtimeTraceMetadataProvider = RealtimeTraceMetadataProvider.Empty,
) {
    companion object {
        val Disabled =
            RealtimeResourceModel(
                enabled = false,
                networkLatency = 0.0,
                imagePullDelay = 0.0,
                ioWeight = 0.0,
                ramWeight = 0.0,
                bwWeight = 0.0,
            )
    }

    fun resourceDelay(
        cloudlet: Cloudlet,
        vm: Vm,
    ): Double {
        if (!enabled) return 0.0
        val ioDelay =
            ioDemand(cloudlet) /
                vm.storage.capacity
                    .coerceAtLeast(1)
                    .toDouble() * ioWeight
        val ramDelay =
            ramDemand(cloudlet) /
                vm.ram.capacity
                    .coerceAtLeast(1)
                    .toDouble() * ramWeight
        val bwDelay =
            bwDemand(cloudlet) /
                vm.bw.capacity
                    .coerceAtLeast(1)
                    .toDouble() * bwWeight
        return ioDelay + ramDelay + bwDelay + networkLatency + imagePullDelay
    }

    fun ramDemand(cloudlet: Cloudlet): Double =
        if (enabled) {
            traceMetadataProvider.metadataFor(cloudlet)?.requestedRam
                ?: (cloudlet.pesNumber.toDouble() * 256.0 + cloudlet.fileSize.toDouble() * ramWeight)
        } else {
            0.0
        }

    fun bwDemand(cloudlet: Cloudlet): Double =
        if (enabled) {
            traceMetadataProvider.metadataFor(cloudlet)?.requestedBw
                ?: (cloudlet.fileSize + cloudlet.outputSize).toDouble()
        } else {
            0.0
        }

    fun ioDemand(cloudlet: Cloudlet): Double =
        if (enabled) {
            traceMetadataProvider.metadataFor(cloudlet)?.requestedIo
                ?: ((cloudlet.fileSize + cloudlet.outputSize).toDouble() * ioWeight)
        } else {
            0.0
        }

    fun ramPressure(
        demand: Double,
        vm: Vm,
    ): Double = pressure(demand, vm.ram.capacity)

    fun bwPressure(
        demand: Double,
        vm: Vm,
    ): Double = pressure(demand, vm.bw.capacity)

    fun ioPressure(
        demand: Double,
        vm: Vm,
    ): Double = pressure(demand, vm.storage.capacity)

    fun accepts(
        ramDemand: Double,
        bwDemand: Double,
        ioDemand: Double,
        vm: Vm,
    ): Boolean {
        if (!enabled) return true
        return ramDemand <= vm.ram.capacity && bwDemand <= vm.bw.capacity && ioDemand <= vm.storage.capacity
    }

    private fun pressure(
        demand: Double,
        capacity: Long,
    ): Double {
        if (!enabled) return 0.0
        return demand / capacity.coerceAtLeast(1).toDouble()
    }
}
