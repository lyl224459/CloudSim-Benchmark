package broker

import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.core.events.SimEvent
import org.cloudsimplus.vms.Vm

internal object RealtimeBrokerEventTags {
    const val ARRIVAL = 9001
    const val SUBMIT = 9002
    const val TIMEOUT = 9003
    const val RUNTIME_FAILURE = 9004
    const val AUTOSCALE_TICK = 9005
}

internal sealed interface RealtimeBrokerEvent {
    data class Arrival(
        val cloudlet: Cloudlet,
        val time: Double,
    ) : RealtimeBrokerEvent

    data class Submit(
        val submission: RealtimePendingSubmission,
    ) : RealtimeBrokerEvent

    data class Timeout(
        val payload: RealtimeCloudletEventPayload,
    ) : RealtimeBrokerEvent

    data class RuntimeFailure(
        val payload: RealtimeCloudletEventPayload,
    ) : RealtimeBrokerEvent

    data class AutoscaleTick(
        val time: Double,
    ) : RealtimeBrokerEvent

    data class Unknown(
        val event: SimEvent,
    ) : RealtimeBrokerEvent
}

internal class RealtimeBrokerEventRouter {
    fun route(event: SimEvent): RealtimeBrokerEvent =
        when (event.tag) {
            RealtimeBrokerEventTags.ARRIVAL -> RealtimeBrokerEvent.Arrival(event.data as Cloudlet, event.time)
            RealtimeBrokerEventTags.SUBMIT -> RealtimeBrokerEvent.Submit(event.data as RealtimePendingSubmission)
            RealtimeBrokerEventTags.TIMEOUT -> RealtimeBrokerEvent.Timeout(event.data as RealtimeCloudletEventPayload)
            RealtimeBrokerEventTags.RUNTIME_FAILURE ->
                RealtimeBrokerEvent.RuntimeFailure(event.data as RealtimeCloudletEventPayload)
            RealtimeBrokerEventTags.AUTOSCALE_TICK -> RealtimeBrokerEvent.AutoscaleTick(event.time)
            else -> RealtimeBrokerEvent.Unknown(event)
        }
}

internal class RealtimeBrokerCommandExecutor(
    private val schedule: (delay: Double, tag: Int, data: Any) -> Unit,
    private val submitVms: (vms: List<Vm>, delay: Double) -> Unit,
) {
    fun applyAll(commands: Iterable<RealtimeBrokerCommand>) {
        commands.forEach(::apply)
    }

    fun apply(command: RealtimeBrokerCommand) {
        when (command) {
            is RealtimeBrokerCommand.ScheduleArrival ->
                schedule(command.delay, RealtimeBrokerEventTags.ARRIVAL, command.cloudlet)
            is RealtimeBrokerCommand.ScheduleSubmit ->
                schedule(command.delay, RealtimeBrokerEventTags.SUBMIT, command.submission)
            is RealtimeBrokerCommand.ScheduleTimeout ->
                schedule(command.delay, RealtimeBrokerEventTags.TIMEOUT, command.payload)
            is RealtimeBrokerCommand.ScheduleRuntimeFailure ->
                schedule(command.delay, RealtimeBrokerEventTags.RUNTIME_FAILURE, command.payload)
            is RealtimeBrokerCommand.ScheduleAutoscaleTick ->
                schedule(command.delay, RealtimeBrokerEventTags.AUTOSCALE_TICK, Unit)
            is RealtimeBrokerCommand.SubmitVms ->
                submitVms(command.vms, command.delay)
        }
    }
}
