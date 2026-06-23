package broker

import config.RealtimeSchedulingConfig
import org.cloudsimplus.cloudlets.Cloudlet
import scheduler.CloudletId
import scheduler.RealtimeObservationEventType
import scheduler.RealtimeTaskLifecycle

internal sealed interface RealtimeDependencyArrivalDecision {
    data object Ready : RealtimeDependencyArrivalDecision

    data object Blocked : RealtimeDependencyArrivalDecision

    data class Rejected(
        val commands: List<RealtimeBrokerCommand>,
    ) : RealtimeDependencyArrivalDecision
}

@Suppress("ReturnCount") // Dependency controller uses guard returns for DAG lifecycle state transitions.
internal class RealtimeDependencyController(
    private val scheduling: RealtimeSchedulingConfig,
    private val state: RealtimeBrokerStateBundle,
    private val lifecycleService: RealtimeBrokerLifecycleService,
    private val clock: () -> Double = { 0.0 },
) {
    private val cloudletsById = linkedMapOf<CloudletId, Cloudlet>()
    private val dependenciesByTask = linkedMapOf<CloudletId, Set<CloudletId>>()
    private val dependentsByTask = linkedMapOf<CloudletId, MutableSet<CloudletId>>()
    private val blockedTasks = linkedSetOf<CloudletId>()
    private val completedTasks = linkedSetOf<CloudletId>()
    private val failedTasks = linkedSetOf<CloudletId>()

    fun register(cloudlet: Cloudlet) {
        val id = CloudletId(cloudlet.id)
        val dependencies =
            lifecycleService
                .taskRecord(cloudlet)
                .dependencyIds
                .map(::CloudletId)
                .filterNot { it == id }
                .toSet()
        cloudletsById[id] = cloudlet
        dependenciesByTask[id] = dependencies
        dependencies.forEach { dependencyId ->
            dependentsByTask.getOrPut(dependencyId) { linkedSetOf() } += id
        }
    }

    fun onArrival(cloudlet: Cloudlet): RealtimeDependencyArrivalDecision {
        if (!scheduling.dependencyEnforcementEnabled) return RealtimeDependencyArrivalDecision.Ready
        val id = CloudletId(cloudlet.id)
        val dependencies = dependenciesByTask[id].orEmpty()
        if (dependencies.isEmpty()) return RealtimeDependencyArrivalDecision.Ready
        return when {
            dependencies.any { it in failedTasks } ->
                RealtimeDependencyArrivalDecision.Rejected(rejectDueToDependency(id))
            dependencies.all { it in completedTasks } -> RealtimeDependencyArrivalDecision.Ready
            else -> {
                block(id, cloudlet)
                RealtimeDependencyArrivalDecision.Blocked
            }
        }
    }

    fun onSucceeded(cloudlet: Cloudlet): List<RealtimeBrokerCommand> {
        val id = CloudletId(cloudlet.id)
        if (!completedTasks.add(id)) return emptyList()
        failedTasks.remove(id)
        blockedTasks.remove(id)
        state.reservation.remove(cloudlet)
        state.arrival.removeWaiting(id)
        val before = lifecycleService.taskRecord(cloudlet)
        lifecycleService.updateMetadata(cloudlet) { it.copy(lifecycle = RealtimeTaskLifecycle.COMPLETED) }
        state.metrics.recordTaskObservation(
            eventTime = clock(),
            eventType = RealtimeObservationEventType.COMPLETED,
            record = before.copy(lifecycle = RealtimeTaskLifecycle.COMPLETED),
            lifecycleFrom = before.lifecycle,
            lifecycleTo = RealtimeTaskLifecycle.COMPLETED,
            vmIndex = before.assignedVmIndex,
        )
        if (!scheduling.dependencyEnforcementEnabled) return emptyList()
        return dependentsByTask[id].orEmpty().flatMap(::resolveDependent)
    }

    fun onTerminalFailure(cloudlet: Cloudlet): List<RealtimeBrokerCommand> {
        val id = CloudletId(cloudlet.id)
        if (!failedTasks.add(id)) return emptyList()
        completedTasks.remove(id)
        blockedTasks.remove(id)
        if (!scheduling.dependencyEnforcementEnabled) return emptyList()
        return dependentsByTask[id].orEmpty().flatMap(::rejectDueToDependency)
    }

    private fun block(
        id: CloudletId,
        cloudlet: Cloudlet,
    ) {
        if (blockedTasks.add(id)) {
            state.metrics.recordDependencyBlocked()
        }
        val before = lifecycleService.taskRecord(cloudlet)
        val after = before.copy(lifecycle = RealtimeTaskLifecycle.DEPENDENCY_BLOCKED)
        lifecycleService.updateMetadata(cloudlet) { after }
        state.metrics.recordTaskObservation(
            eventTime = clock(),
            eventType = RealtimeObservationEventType.DEPENDENCY_BLOCKED,
            record = after,
            lifecycleFrom = before.lifecycle,
            lifecycleTo = after.lifecycle,
            reason = "waiting_for_dependencies",
        )
    }

    private fun resolveDependent(id: CloudletId): List<RealtimeBrokerCommand> {
        val cloudlet = cloudletsById[id] ?: return emptyList()
        val dependencies = dependenciesByTask[id].orEmpty()
        return when {
            lifecycleService.lifecycleOf(cloudlet).isTerminalLifecycle() -> emptyList()
            dependencies.any { it in failedTasks } -> rejectDueToDependency(id)
            dependencies.all { it in completedTasks } && id in blockedTasks -> releaseBlockedDependent(id, cloudlet)
            dependencies.all { it in completedTasks } -> emptyList()
            else -> emptyList()
        }
    }

    private fun releaseBlockedDependent(
        id: CloudletId,
        cloudlet: Cloudlet,
    ): List<RealtimeBrokerCommand> {
        blockedTasks.remove(id)
        val before = lifecycleService.taskRecord(cloudlet)
        val after = before.copy(lifecycle = RealtimeTaskLifecycle.ARRIVED)
        lifecycleService.updateMetadata(cloudlet) { after }
        state.metrics.recordDependencyReleased()
        state.metrics.recordTaskObservation(
            eventTime = clock(),
            eventType = RealtimeObservationEventType.DEPENDENCY_RELEASED,
            record = after,
            lifecycleFrom = before.lifecycle,
            lifecycleTo = after.lifecycle,
            decision = "schedule_arrival",
        )
        return listOf(RealtimeBrokerCommand.ScheduleArrival(delay = 0.0, cloudlet = cloudlet))
    }

    private fun rejectDueToDependency(id: CloudletId): List<RealtimeBrokerCommand> {
        val cloudlet = cloudletsById[id] ?: return emptyList()
        if (lifecycleService.lifecycleOf(cloudlet).isTerminalLifecycle()) return emptyList()
        failedTasks += id
        completedTasks.remove(id)
        blockedTasks.remove(id)
        cloudlet.setStatus(Cloudlet.Status.FAILED)
        state.arrival.removePending(id)
        state.arrival.removeWaiting(id)
        state.reservation.remove(cloudlet)
        state.metrics.recordRejected(RealtimeRejectReason.DEPENDENCY)
        state.metrics.recordDependencyRejected()
        val before = lifecycleService.taskRecord(cloudlet)
        val after = before.copy(lifecycle = RealtimeTaskLifecycle.REJECTED)
        lifecycleService.updateMetadata(cloudlet) { after }
        state.metrics.recordTaskObservation(
            eventTime = clock(),
            eventType = RealtimeObservationEventType.DEPENDENCY_REJECTED,
            record = after,
            lifecycleFrom = before.lifecycle,
            lifecycleTo = after.lifecycle,
            reason = "dependency_failed",
        )
        return dependentsByTask[id].orEmpty().flatMap(::rejectDueToDependency)
    }

    private fun RealtimeTaskLifecycle?.isTerminalLifecycle(): Boolean =
        this == RealtimeTaskLifecycle.COMPLETED ||
            this == RealtimeTaskLifecycle.REJECTED ||
            this == RealtimeTaskLifecycle.FAILED ||
            this == RealtimeTaskLifecycle.CANCELLED ||
            this == RealtimeTaskLifecycle.TIMED_OUT
}
