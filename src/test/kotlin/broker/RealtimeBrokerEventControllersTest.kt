package broker

import config.RealtimeSchedulingConfig
import org.assertj.core.api.Assertions.assertThat
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.cloudlets.CloudletSimple
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared
import org.cloudsimplus.utilizationmodels.UtilizationModelFull
import org.cloudsimplus.vms.Vm
import org.cloudsimplus.vms.VmSimple
import org.junit.jupiter.api.Test
import scheduler.CloudletId
import scheduler.RealtimeTaskLifecycle
import scheduler.RealtimeTaskRecord
import scheduler.RealtimeTopologyModel
import scheduler.RealtimeVmLifecycle
import scheduler.RealtimeVmLifecycleManager
import scheduler.VmIndex

class RealtimeBrokerEventControllersTest {
    @Test
    fun `runtime controller plans timeout runtime failure and topology accounting`() {
        val scheduling =
            RealtimeSchedulingConfig(
                taskTimeout = 5.0,
                runtimeFailureRate = 1.0,
                topologyEnabled = true,
                hostFailureRate = 1.0,
            )
        val controller =
            RealtimeRuntimeEventController(
                scheduling = scheduling,
                topologyModel = RealtimeTopologyModel.fromConfig(scheduling, initialVmCount = 1),
                deterministicUnit = { _, _, _ -> 0.0 },
                runtimeEstimator = { 10.0 },
            )

        val cloudlet = createCloudlet(id = 7)
        val plan =
            controller.planRuntimeEvents(
                cloudlet = cloudlet,
                attempt = 0,
                runtimeToken = 7,
                timing = RealtimeRuntimeEventTiming(arrivalTime = 0.0, currentTime = 1.0),
                assignment = RealtimeRuntimeEventAssignment(vmIndex = 0, nodeFailurePressure = 0.0),
            )

        assertThat(plan.topologyFailureDomain).isEqualTo(RealtimeFailureDomain.HOST)
        assertThat(plan.commands).hasSize(2)
        assertThat((plan.commands[0] as RealtimeBrokerCommand.ScheduleTimeout).delay).isEqualTo(4.0)
        assertThat((plan.commands[1] as RealtimeBrokerCommand.ScheduleRuntimeFailure).delay).isEqualTo(2.5)
    }

    @Test
    fun `timeout retry action returns retry command and records metrics`() {
        val scheduling =
            RealtimeSchedulingConfig(
                retryLimit = 1,
                retryDelay = 0.25,
                migrationDelay = 0.1,
                timeoutAction = "retry",
            )
        val arrivalState = RealtimeArrivalState()
        val reservationState = RealtimeReservationState()
        val metrics = RealtimeBrokerMetrics()
        val cloudlet = createCloudlet(id = 9)
        var metadata = taskRecord(cloudlet)
        arrivalState.recordArrival(cloudlet)
        arrivalState.addWaiting(cloudlet)
        val runtimeToken = arrivalState.issueRuntimeToken(cloudlet)

        val controller =
            RealtimeTaskInterruptionController(
                scheduling = scheduling,
                state = RealtimeTaskInterruptionState(arrivalState, reservationState, metrics),
                services =
                    RealtimeTaskInterruptionServices(
                        failure = RealtimeFailureController(scheduling) { _, _, _ -> 0.0 },
                        timeout = RealtimeTimeoutController(scheduling),
                        recovery = RealtimeCloudletRecoveryEstimator(scheduling, { 1.0 }, { listOf(createVm()) }),
                        updateMetadata = { _, transform -> metadata = transform(metadata) },
                    ),
            )

        val commands = controller.onTimeout(cloudlet, attempt = 0, runtimeToken = runtimeToken)

        assertThat(metrics.snapshot().timeoutCancelledCount).isEqualTo(1)
        assertThat(metrics.snapshot().retryCount).isEqualTo(1)
        assertThat(metadata.lifecycle).isEqualTo(RealtimeTaskLifecycle.RETRYING)
        assertThat(commands).containsExactly(
            RealtimeBrokerCommand.ScheduleArrival(delay = 0.35, cloudlet = cloudlet),
        )
    }

    @Test
    fun `autoscaling controller emits submit and follow up tick commands`() {
        val scheduling =
            RealtimeSchedulingConfig(
                autoscalingEnabled = true,
                scaleOutQueueThreshold = 1,
                maxDynamicVms = 1,
                vmColdStartDelay = 0.5,
                scaleInIdleTime = 2.0,
            )
        val manager =
            RealtimeVmLifecycleManager(
                initialVms = listOf(createVm()),
                scheduling = scheduling,
                topologyModel = RealtimeTopologyModel.Disabled,
            )
        val controller = RealtimeAutoscalingController(scheduling, manager)

        val commands = controller.scaleOutCommands(queueDepth = 1, currentTime = 0.0, activeVmIndexes = emptySet())

        assertThat(commands).hasSize(2)
        assertThat((commands[0] as RealtimeBrokerCommand.SubmitVms).vms).hasSize(1)
        assertThat(commands[1]).isEqualTo(RealtimeBrokerCommand.ScheduleAutoscaleTick(delay = 2.0))
    }

    @Test
    fun `autoscaling controller covers disabled empty and dynamic tick branches`() {
        val disabledScheduling = RealtimeSchedulingConfig(autoscalingEnabled = false)
        val disabledManager =
            RealtimeVmLifecycleManager(
                initialVms = listOf(createVm()),
                scheduling = disabledScheduling,
                topologyModel = RealtimeTopologyModel.Disabled,
            )
        val disabled = RealtimeAutoscalingController(disabledScheduling, disabledManager)
        disabled.refresh(currentTime = 1.0, activeVmIndexes = emptySet())

        assertThat(
            disabled.scaleOutCommands(queueDepth = 100, currentTime = 1.0, activeVmIndexes = emptySet()),
        ).isEmpty()
        assertThat(disabled.tickCommands(currentTime = 2.0, activeVmIndexes = emptySet())).isEmpty()

        val scaling =
            RealtimeSchedulingConfig(
                autoscalingEnabled = true,
                scaleOutQueueThreshold = 1,
                maxDynamicVms = 1,
                scaleInIdleTime = 2.0,
            )
        val manager =
            RealtimeVmLifecycleManager(
                initialVms = listOf(createVm()),
                scheduling = scaling,
                topologyModel = RealtimeTopologyModel.Disabled,
            )
        val controller = RealtimeAutoscalingController(scaling, manager)
        controller.scaleOutCommands(queueDepth = 1, currentTime = 0.0, activeVmIndexes = emptySet())

        assertThat(controller.tickCommands(currentTime = 0.5, activeVmIndexes = emptySet()))
            .containsExactly(RealtimeBrokerCommand.ScheduleAutoscaleTick(2.0))
    }

    @Test
    fun `autoscaling predictive policy batches scale out and records cooldown skips`() {
        val scheduling =
            RealtimeSchedulingConfig(
                autoscalingEnabled = true,
                autoscalingPolicy = "deadline_predictive",
                autoscalingEvaluationInterval = 1.0,
                scaleOutQueueThreshold = 1,
                maxDynamicVms = 5,
                scaleOutBatchSize = 2,
                scaleCooldown = 2.0,
            )
        val manager = RealtimeVmLifecycleManager(listOf(createVm()), scheduling, RealtimeTopologyModel.Disabled)
        val controller = RealtimeAutoscalingController(scheduling, manager)

        val first = controller.scaleOutCommands(queueDepth = 3, currentTime = 0.0, activeVmIndexes = emptySet())
        val second = controller.scaleOutCommands(queueDepth = 3, currentTime = 0.5, activeVmIndexes = emptySet())

        assertThat(first.filterIsInstance<RealtimeBrokerCommand.SubmitVms>().single().vms).hasSize(2)
        assertThat(second.filterIsInstance<RealtimeBrokerCommand.SubmitVms>()).isEmpty()
        assertThat(manager.getScaleCooldownSkippedCount()).isEqualTo(1)
        assertThat(manager.getAverageAutoscalingPressure()).isEqualTo(3.0)
    }

    @Test
    fun `autoscaling predictive tick stops when realtime work is complete`() {
        val scheduling =
            RealtimeSchedulingConfig(
                autoscalingEnabled = true,
                autoscalingPolicy = "deadline_predictive",
                autoscalingEvaluationInterval = 1.0,
                maxDynamicVms = 1,
            )
        val manager = RealtimeVmLifecycleManager(listOf(createVm()), scheduling, RealtimeTopologyModel.Disabled)
        val controller = RealtimeAutoscalingController(scheduling, manager)

        val commands =
            controller.tickCommands(
                currentTime = 10.0,
                activeVmIndexes = emptySet(),
                queueDepth = 0,
                continueEvaluating = false,
            )

        assertThat(commands).isEmpty()
    }

    @Test
    fun `autoscaling predictive tick does not scale out from stale arrival rate without queue`() {
        val scheduling =
            RealtimeSchedulingConfig(
                autoscalingEnabled = true,
                autoscalingPolicy = "deadline_predictive",
                autoscalingEvaluationInterval = 1.0,
                scaleOutQueueThreshold = 1,
                arrivalRateWindow = 10.0,
                predictiveLookahead = 10.0,
                maxDynamicVms = 1,
            )
        val manager = RealtimeVmLifecycleManager(listOf(createVm()), scheduling, RealtimeTopologyModel.Disabled)
        val controller = RealtimeAutoscalingController(scheduling, manager)
        repeat(5) { index -> controller.recordArrival(index.toDouble()) }

        val commands = controller.tickCommands(currentTime = 5.0, activeVmIndexes = emptySet(), queueDepth = 0)

        assertThat(commands.filterIsInstance<RealtimeBrokerCommand.SubmitVms>()).isEmpty()
        assertThat(manager.getScaleOutCount()).isZero()
    }

    @Test
    fun `autoscaling tick fills warm pool and records warm availability`() {
        val scheduling =
            RealtimeSchedulingConfig(
                autoscalingEnabled = true,
                autoscalingPolicy = "queue_threshold",
                autoscalingEvaluationInterval = 1.0,
                maxDynamicVms = 2,
                warmPoolSize = 1,
            )
        val manager = RealtimeVmLifecycleManager(listOf(createVm()), scheduling, RealtimeTopologyModel.Disabled)
        val controller = RealtimeAutoscalingController(scheduling, manager)

        val first = controller.tickCommands(currentTime = 1.0, activeVmIndexes = emptySet(), queueDepth = 0)
        manager.refresh(currentTime = 1.0, activeVmIndexes = emptySet())
        val second = controller.tickCommands(currentTime = 2.0, activeVmIndexes = emptySet(), queueDepth = 0)

        assertThat(first.filterIsInstance<RealtimeBrokerCommand.SubmitVms>().single().vms).hasSize(1)
        assertThat(second.filterIsInstance<RealtimeBrokerCommand.SubmitVms>()).isEmpty()
        assertThat(manager.getWarmPoolHitRate()).isEqualTo(0.5)
    }

    @Test
    fun `autoscaling min active bypasses batch limit`() {
        val scheduling =
            RealtimeSchedulingConfig(
                autoscalingEnabled = true,
                autoscalingPolicy = "queue_threshold",
                autoscalingEvaluationInterval = 1.0,
                maxDynamicVms = 4,
                minActiveVms = 3,
                scaleOutBatchSize = 1,
            )
        val manager = RealtimeVmLifecycleManager(listOf(createVm()), scheduling, RealtimeTopologyModel.Disabled)
        val controller = RealtimeAutoscalingController(scheduling, manager)

        val commands = controller.tickCommands(currentTime = 1.0, activeVmIndexes = emptySet(), queueDepth = 0)

        assertThat(commands.filterIsInstance<RealtimeBrokerCommand.SubmitVms>().single().vms).hasSize(2)
    }

    @Test
    fun `autoscaling scale in drain stops accepting before termination`() {
        val scheduling =
            RealtimeSchedulingConfig(
                autoscalingEnabled = true,
                scaleInIdleTime = 1.0,
                maxDynamicVms = 1,
                scaleInDrainEnabled = true,
            )
        val manager = RealtimeVmLifecycleManager(listOf(createVm()), scheduling, RealtimeTopologyModel.Disabled)
        manager.scaleOut(count = 1, currentTime = 0.0, activeVmIndexes = emptySet())
        val controller = RealtimeAutoscalingController(scheduling, manager)

        controller.tickCommands(currentTime = 2.0, activeVmIndexes = emptySet())
        val draining = manager.snapshots().values.single { it.dynamic }
        controller.tickCommands(currentTime = 3.0, activeVmIndexes = emptySet())
        val terminated = manager.snapshots().values.single { it.dynamic }

        assertThat(draining.lifecycle).isEqualTo(RealtimeVmLifecycle.DRAINING)
        assertThat(draining.acceptingWork).isFalse()
        assertThat(manager.getScaleInDrainCount()).isEqualTo(1)
        assertThat(terminated.lifecycle).isEqualTo(RealtimeVmLifecycle.TERMINATED)
        assertThat(manager.getScaleInCount()).isEqualTo(1)
    }

    @Test
    fun `preemption executor retries active victim and records recovery metrics`() {
        val scheduling =
            RealtimeSchedulingConfig(
                retryDelay = 0.25,
                migrationDelay = 0.1,
                checkpointInterval = 0.5,
            )
        val arrivalState = RealtimeArrivalState()
        val reservationState = RealtimeReservationState()
        val metrics = RealtimeBrokerMetrics()
        val victim = createCloudlet(id = 11)
        var metadata = taskRecord(victim)
        arrivalState.recordArrival(victim)
        arrivalState.addWaiting(victim)
        reservationState.reserve(victim, vmIndex = 0)

        val executor =
            RealtimePreemptionExecutor(
                scheduling = scheduling,
                state = RealtimePreemptionState(arrivalState, reservationState, metrics),
                services =
                    RealtimePreemptionServices(
                        failure = RealtimeFailureController(scheduling) { _, _, _ -> 0.0 },
                        recovery = RealtimeCloudletRecoveryEstimator(scheduling, { 1.0 }, { listOf(createVm()) }),
                        updateMetadata = { _, transform -> metadata = transform(metadata) },
                    ),
            )

        val result =
            executor.preempt(
                PreemptionDecision.Preempt(
                    victimCloudletId = CloudletId(victim.id),
                    victimVmIndex = VmIndex(0),
                    delay = 0.2,
                    penalty = 3.0,
                ),
            )

        assertThat(result.applied).isTrue()
        assertThat(result.commands).containsExactly(
            RealtimeBrokerCommand.ScheduleArrival(delay = 0.55, cloudlet = victim),
        )
        assertThat(metrics.snapshot().preemptionSuccessCount).isEqualTo(1)
        assertThat(metrics.snapshot().checkpointRecoveryCount).isEqualTo(1)
        assertThat(metrics.snapshot().migrationCount).isEqualTo(1)
        assertThat(metrics.snapshot().retryCount).isEqualTo(1)
        assertThat(metadata.lifecycle).isEqualTo(RealtimeTaskLifecycle.RETRYING)
        assertThat(metadata.preemptedCount).isEqualTo(1)
        assertThat(metadata.migratedCount).isEqualTo(1)
        assertThat(metadata.checkpointRecoveredLength).isGreaterThan(0L)
    }

    @Test
    fun `preemption executor rejects missing victims`() {
        val scheduling = RealtimeSchedulingConfig()
        val metrics = RealtimeBrokerMetrics()
        val executor =
            RealtimePreemptionExecutor(
                scheduling = scheduling,
                state = RealtimePreemptionState(RealtimeArrivalState(), RealtimeReservationState(), metrics),
                services =
                    RealtimePreemptionServices(
                        failure = RealtimeFailureController(scheduling) { _, _, _ -> 0.0 },
                        recovery = RealtimeCloudletRecoveryEstimator(scheduling, { 1.0 }, { listOf(createVm()) }),
                        updateMetadata = { _, _ -> },
                    ),
            )

        val result =
            executor.preempt(
                PreemptionDecision.Preempt(
                    victimCloudletId = CloudletId(99),
                    victimVmIndex = VmIndex(0),
                    delay = 0.2,
                    penalty = 3.0,
                ),
            )

        assertThat(result.applied).isFalse()
        assertThat(result.commands).isEmpty()
        assertThat(metrics.snapshot().preemptionSuccessCount).isZero()
    }

    private fun createCloudlet(id: Int): Cloudlet {
        val utilizationModel = UtilizationModelFull()
        return CloudletSimple(1000, 1).apply {
            setId(id.toLong())
            setFileSize(100)
            setOutputSize(100)
            setUtilizationModelCpu(utilizationModel)
            setUtilizationModelRam(utilizationModel)
            setUtilizationModelBw(utilizationModel)
        }
    }

    private fun createVm(): Vm =
        VmSimple(1000.0, 1)
            .setRam(1024)
            .setBw(1000)
            .setSize(10_000)
            .setCloudletScheduler(CloudletSchedulerSpaceShared())

    private fun taskRecord(cloudlet: Cloudlet): RealtimeTaskRecord =
        RealtimeTaskRecord(
            cloudletId = cloudlet.id,
            originalArrivalTime = 0.0,
            attempt = 0,
            priority = 0,
            deadline = null,
        )
}
