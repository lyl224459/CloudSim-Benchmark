package broker

import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm

internal data class RealtimePendingSubmission(
    val cloudlet: Cloudlet,
    val vmIndex: Int,
    val decisionDelay: Double,
    val failurePressure: Double,
    val decisionToken: Int,
)

internal data class RealtimeCloudletEventPayload(
    val cloudlet: Cloudlet,
    val attempt: Int,
    val runtimeToken: Int,
)

internal sealed interface RealtimeBrokerCommand {
    data class ScheduleArrival(
        val delay: Double,
        val cloudlet: Cloudlet,
    ) : RealtimeBrokerCommand

    data class ScheduleSubmit(
        val delay: Double,
        val submission: RealtimePendingSubmission,
    ) : RealtimeBrokerCommand

    data class ScheduleTimeout(
        val delay: Double,
        val payload: RealtimeCloudletEventPayload,
    ) : RealtimeBrokerCommand

    data class ScheduleRuntimeFailure(
        val delay: Double,
        val payload: RealtimeCloudletEventPayload,
    ) : RealtimeBrokerCommand

    data class ScheduleAutoscaleTick(
        val delay: Double,
    ) : RealtimeBrokerCommand

    data class ScheduleRescheduleTick(
        val delay: Double,
    ) : RealtimeBrokerCommand

    data class SubmitVms(
        val vms: List<Vm>,
        val delay: Double,
    ) : RealtimeBrokerCommand
}
