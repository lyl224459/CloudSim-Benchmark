package broker

import config.RealtimeSchedulingConfig
import org.cloudsimplus.brokers.DatacenterBrokerSimple
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.core.CloudSimPlus
import org.cloudsimplus.core.events.SimEvent
import org.cloudsimplus.vms.Vm
import scheduler.RealtimeScheduler

/**
 * 实时调度代理。
 *
 * 通过 CloudSim 事件在任务到达时提交 cloudlet，并在到达时调用调度器。
 */
class RealtimeBroker(
    simulation: CloudSimPlus,
    private val scheduler: RealtimeScheduler,
    private val vmList: List<Vm>,
    private val schedulingConfig: RealtimeSchedulingConfig = RealtimeSchedulingConfig()
) : DatacenterBrokerSimple(simulation) {

    companion object {
        private const val ARRIVAL_EVENT_TAG = 9001
    }

    private val waitingCloudlets = mutableListOf<Cloudlet>()
    private val arrivalTimes = mutableMapOf<Long, Double>()
    private val preassignedVmIds = mutableMapOf<Long, Int>()
    private var rejectedCount = 0

    fun submitCloudletListRealtime(cloudletList: List<Cloudlet>) {
        val sortedCloudlets = cloudletList.sortedBy { it.submissionDelay }
        if (schedulingConfig.strategy.equals("static", ignoreCase = true)) {
            val previewWaiting = mutableListOf<Cloudlet>()
            for (cloudlet in sortedCloudlets) {
                preassignedVmIds[cloudlet.id] = scheduler.scheduleOnArrival(cloudlet, previewWaiting.toList(), vmList)
                previewWaiting.add(cloudlet)
            }
        }

        for (cloudlet in sortedCloudlets) {
            arrivalTimes[cloudlet.id] = cloudlet.submissionDelay
            schedule(cloudlet.submissionDelay, ARRIVAL_EVENT_TAG, cloudlet)
        }
    }

    fun getWaitingCloudlets(): List<Cloudlet> = waitingCloudlets.filter {
        it.status != Cloudlet.Status.SUCCESS && it.status != Cloudlet.Status.FAILED
    }

    fun getRejectedCount(): Int = rejectedCount

    fun getArrivalTime(cloudlet: Cloudlet): Double = arrivalTimes[cloudlet.id] ?: cloudlet.submissionDelay

    fun getTimeoutCount(timeoutSeconds: Double): Int {
        if (timeoutSeconds <= 0.0) return 0
        return waitingCloudlets.count { cloudlet ->
            val arrivalTime = getArrivalTime(cloudlet)
            val finishTime = cloudlet.finishTime
            val elapsed = if (cloudlet.status == Cloudlet.Status.SUCCESS) {
                finishTime - arrivalTime
            } else {
                Double.POSITIVE_INFINITY
            }
            elapsed > timeoutSeconds
        }
    }

    override fun processEvent(event: SimEvent) {
        if (event.tag == ARRIVAL_EVENT_TAG) {
            val cloudlet = event.data as Cloudlet
            onCloudletArrival(cloudlet, event.time)
            return
        }
        super.processEvent(event)
    }

    private fun onCloudletArrival(cloudlet: Cloudlet, arrivalTime: Double) {
        val activeCloudlets = getWaitingCloudlets()
        if (activeCloudlets.size >= schedulingConfig.maxQueueSize) {
            rejectedCount++
            return
        }

        val selectedVmId = selectVmId(cloudlet, activeCloudlets)
        cloudlet.setVm(vmList[selectedVmId.coerceIn(vmList.indices)])
        cloudlet.setSubmissionDelay(0.0)
        waitingCloudlets.add(cloudlet)
        submitCloudlet(cloudlet)
    }

    private fun selectVmId(cloudlet: Cloudlet, activeCloudlets: List<Cloudlet>): Int {
        val strategy = schedulingConfig.strategy.lowercase()
        val selected = if (strategy == "static") {
            preassignedVmIds[cloudlet.id] ?: scheduler.scheduleOnArrival(cloudlet, activeCloudlets, vmList)
        } else {
            scheduler.scheduleOnArrival(cloudlet, activeCloudlets, vmList)
        }

        return applyReservationPolicy(selected, cloudlet, activeCloudlets)
    }

    private fun applyReservationPolicy(selectedVmId: Int, cloudlet: Cloudlet, activeCloudlets: List<Cloudlet>): Int {
        return when (schedulingConfig.resourceReservation.lowercase()) {
            "partial" -> applyPartialReservation(selectedVmId, activeCloudlets)
            "full" -> applyFullReservation(cloudlet, selectedVmId, activeCloudlets)
            else -> selectedVmId
        }
    }

    private fun applyPartialReservation(selectedVmId: Int, activeCloudlets: List<Cloudlet>): Int {
        val selectedVm = vmList[selectedVmId.coerceIn(vmList.indices)]
        val highestMips = vmList.maxOf { it.mips }
        val hasIdleNonHigh = vmList.any { it.mips < highestMips && activeCloudlets.none { cloudlet -> cloudlet.vm?.id == it.id } }
        if (selectedVm.mips == highestMips && hasIdleNonHigh) {
            return vmList.indexOfFirst { it.mips < highestMips }
                .takeIf { it >= 0 } ?: selectedVmId
        }
        return selectedVmId
    }

    private fun applyFullReservation(cloudlet: Cloudlet, selectedVmId: Int, activeCloudlets: List<Cloudlet>): Int {
        val length = cloudlet.length
        val groups = vmList.groupBy { it.mips }.toSortedMap()
        val desiredGroup = when {
            length < 20000 -> groups.keys.firstOrNull()
            length < 40000 -> groups.keys.elementAtOrNull(1) ?: groups.keys.firstOrNull()
            else -> groups.keys.lastOrNull()
        } ?: return selectedVmId

        val candidateVmIds = groups[desiredGroup].orEmpty().map { it.id.toInt() }
        if (candidateVmIds.isEmpty()) return selectedVmId

        val leastLoaded = candidateVmIds.minByOrNull { vmId ->
            activeCloudlets.count { it.vm?.id?.toInt() == vmId }
        }
        return leastLoaded ?: selectedVmId
    }
}
