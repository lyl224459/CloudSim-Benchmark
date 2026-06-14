package broker

import config.RealtimeSchedulingConfig
import scheduler.RealtimeVmLifecycleManager

internal class RealtimeAutoscalingController(
    private val scheduling: RealtimeSchedulingConfig,
    private val vmLifecycleManager: RealtimeVmLifecycleManager,
) {
    fun refresh(
        currentTime: Double,
        activeVmIndexes: Set<Int>,
    ) {
        vmLifecycleManager.refresh(currentTime, activeVmIndexes)
    }

    fun scaleOutCommands(
        queueDepth: Int,
        currentTime: Double,
        activeVmIndexes: Set<Int>,
    ): List<RealtimeBrokerCommand> {
        val newVms = vmLifecycleManager.maybeScaleOut(queueDepth, currentTime, activeVmIndexes)
        return if (newVms.isEmpty()) {
            emptyList()
        } else {
            scaleOutCommandsFor(newVms)
        }
    }

    fun tickCommands(
        currentTime: Double,
        activeVmIndexes: Set<Int>,
    ): List<RealtimeBrokerCommand> {
        vmLifecycleManager.refresh(currentTime, activeVmIndexes)
        vmLifecycleManager.maybeScaleIn(currentTime, activeVmIndexes)
        return if (shouldScheduleScaleInTick()) {
            listOf(RealtimeBrokerCommand.ScheduleAutoscaleTick(scheduling.scaleInIdleTime))
        } else {
            emptyList()
        }
    }

    private fun scaleOutCommandsFor(newVms: List<org.cloudsimplus.vms.Vm>): List<RealtimeBrokerCommand> =
        buildList {
            add(RealtimeBrokerCommand.SubmitVms(newVms, scheduling.vmColdStartDelay))
            if (scheduling.scaleInIdleTime > 0.0) {
                add(RealtimeBrokerCommand.ScheduleAutoscaleTick(scheduling.scaleInIdleTime))
            }
        }

    private fun shouldScheduleScaleInTick(): Boolean =
        scheduling.autoscalingEnabled &&
            scheduling.scaleInIdleTime > 0.0 &&
            vmLifecycleManager.hasLiveDynamicVms()
}
