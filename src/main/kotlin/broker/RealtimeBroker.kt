package broker

import config.RealtimeSchedulingConfig
import datacenter.MutableRealtimeTraceMetadataProvider
import datacenter.RealtimeCloudletBatch
import datacenter.RealtimeCloudletSpec
import org.cloudsimplus.brokers.DatacenterBrokerSimple
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.core.CloudSimPlus
import org.cloudsimplus.core.events.SimEvent
import org.cloudsimplus.vms.Vm
import scheduler.RealtimeScheduler
import scheduler.RealtimeCandidateScoreRecord
import scheduler.RealtimeTaskMetadata
import scheduler.RealtimeTopologyMetrics

/**
 * 实时调度代理。
 *
 * 通过 CloudSim 事件在任务到达时提交 cloudlet，并在到达时调用调度器。
 * Public CloudSim facade keeps the existing metric getter surface for compatibility.
 */
@Suppress("TooManyFunctions") // CloudSim broker facade intentionally exposes the stable public metrics API.
class RealtimeBroker(
    private val cloudSim: CloudSimPlus,
    scheduler: RealtimeScheduler,
    initialVmList: List<Vm>,
    private val schedulingConfig: RealtimeSchedulingConfig = RealtimeSchedulingConfig(),
) : DatacenterBrokerSimple(cloudSim) {
    private val arrivalState = RealtimeArrivalState()
    private val lifecycleStore = RealtimeTaskLifecycleStore()
    private val reservationState = RealtimeReservationState()
    private val brokerMetrics = RealtimeBrokerMetrics()
    private val brokerState =
        RealtimeBrokerStateBundle(
            arrival = arrivalState,
            lifecycleStore = lifecycleStore,
            reservation = reservationState,
            metrics = brokerMetrics,
        )
    private val traceMetadataProvider = MutableRealtimeTraceMetadataProvider()
    private val deterministicSampler = RealtimeDeterministicSampler()
    private val environment =
        realtimeBrokerEnvironment(
            schedulingConfig = schedulingConfig,
            initialVmList = initialVmList,
            traceMetadataProvider = traceMetadataProvider,
        )
    private val admissionController = RealtimeAdmissionController(schedulingConfig)
    private val failureController =
        RealtimeFailureController(
            schedulingConfig,
            deterministicSampler::sample,
        )
    private val timeoutController = RealtimeTimeoutController(schedulingConfig)
    private val preemptionController = RealtimePreemptionController(schedulingConfig)
    private val tenantController = RealtimeTenantController(schedulingConfig)
    private val metadataFactory =
        RealtimeTaskMetadataFactory(
            schedulingConfig,
            traceMetadataProvider,
            tenantController,
            deterministicSampler::sample,
        )
    private val lifecycleService =
        RealtimeBrokerLifecycleService(
            state = brokerState,
            metadataFactory = metadataFactory,
            environment = environment,
        )
    private val dependencyController =
        RealtimeDependencyController(
            scheduling = schedulingConfig,
            state = brokerState,
            lifecycleService = lifecycleService,
        )
    private val tenantFairnessContextBuilder = TenantFairnessContextBuilder(tenantController)
    private val vmReservationPolicy = RealtimeVmReservationPolicy(schedulingConfig)
    private val recoveryEstimator =
        RealtimeCloudletRecoveryEstimator(
            schedulingConfig,
            { cloudSim.clock() },
            { environment.vmList },
        )
    private val runtimeEventController =
        RealtimeRuntimeEventController(
            schedulingConfig,
            environment.topologyModel,
            deterministicSampler::sample,
            recoveryEstimator::estimatedRuntime,
        )
    private val topologyAccountingController =
        RealtimeTopologyAccountingController(environment.topologyModel, brokerMetrics)
    private val autoscalingController =
        RealtimeAutoscalingController(schedulingConfig, environment.vmLifecycleManager)
    private val interruptionController =
        RealtimeTaskInterruptionController(
            schedulingConfig,
            RealtimeTaskInterruptionState(arrivalState, reservationState, brokerMetrics),
            RealtimeTaskInterruptionServices(
                failureController,
                timeoutController,
                recoveryEstimator,
                lifecycleService::updateMetadata,
                dependencyController::onTerminalFailure,
            ),
        )
    private val preemptionExecutor =
        RealtimePreemptionExecutor(
            schedulingConfig,
            RealtimePreemptionState(arrivalState, reservationState, brokerMetrics),
            RealtimePreemptionServices(
                failureController,
                recoveryEstimator,
                lifecycleService::updateMetadata,
            ),
        )
    private val eventRouter = RealtimeBrokerEventRouter()
    private val cloudletOrdering = RealtimeCloudletOrdering(schedulingConfig, lifecycleStore)
    private val commandExecutor =
        RealtimeBrokerCommandExecutor(
            schedule = { delay, tag, data -> schedule(delay, tag, data) },
            submitVms = { vms, delay -> submitVmList(vms, delay) },
        )
    private val brokerCallbacks =
        RealtimeBrokerCallbacks(
            clock = { cloudSim.clock() },
            applyCommands = commandExecutor::applyAll,
            submitCloudlet = { cloudlet -> submitCloudlet(cloudlet) },
        )
    private val runtimeEventPlanner =
        RealtimeRuntimeEventPlanner(
            state = brokerState,
            environment = environment,
            runtimeEventController = runtimeEventController,
            topologyAccountingController = topologyAccountingController,
            callbacks = brokerCallbacks,
        )
    private val vmSelectionFacade =
        RealtimeVmSelectionFacade(
            schedulingConfig = schedulingConfig,
            state = brokerState,
            environment = environment,
            lifecycleService = lifecycleService,
            policies =
                RealtimeVmSelectionPolicies(
                    scheduler = scheduler,
                    tenantFairnessContextBuilder = tenantFairnessContextBuilder,
                    reservationPolicy = vmReservationPolicy,
                    preemption = RealtimeBrokerPreemptionComponents(preemptionController, preemptionExecutor),
                    deterministicSampler = deterministicSampler,
                ),
        )
    private val queueDepthSampler =
        RealtimeQueueDepthSampler(
            state = brokerState,
            environment = environment,
        )
    private val submissionService =
        RealtimeSubmissionService(
            submissionState =
                RealtimeSubmissionState(
                    state = brokerState,
                    environment = environment,
                    lifecycleService = lifecycleService,
                ),
            controllers =
                RealtimeSubmissionControllers(
                    failureController = failureController,
                    runtimePlanner = runtimeEventPlanner,
                    queueDepthSampler = queueDepthSampler,
                    dependencyController = dependencyController,
                ),
            callbacks = brokerCallbacks,
        )
    private val reschedulingController =
        RealtimeReschedulingController(
            scheduling = schedulingConfig,
            state = brokerState,
            environment = environment,
            lifecycleService = lifecycleService,
            vmSelectionFacade = vmSelectionFacade,
            submissionService = submissionService,
            recoveryEstimator = recoveryEstimator,
        )
    private val readModel =
        RealtimeBrokerReadModel(
            RealtimeBrokerReadDependencies(
                schedulingConfig,
                arrivalState,
                lifecycleStore,
                reservationState,
                brokerMetrics,
                environment.vmLifecycleManager,
                tenantController,
                environment.topologyModel,
            ),
        )
    private val arrivalWorkflow =
        RealtimeArrivalWorkflow(
            RealtimeArrivalWorkflowAdapter(
                core =
                    RealtimeArrivalCoreServices(
                        lifecycleService = lifecycleService,
                        readModel = readModel,
                        vmSelectionFacade = vmSelectionFacade,
                        submissionService = submissionService,
                        dependencyController = dependencyController,
                        metrics = brokerMetrics,
                    ),
                controls =
                    RealtimeArrivalControlServices(
                        autoscalingController = autoscalingController,
                        admissionServices = RealtimeAdmissionServices(admissionController, tenantController),
                        commandExecutor = commandExecutor,
                    ),
            ),
        )
    private val submissionWorkflow =
        RealtimeSubmissionWorkflow(RealtimeSubmissionWorkflowAdapter(submissionService))

    fun submitCloudletBatchRealtime(batch: RealtimeCloudletBatch) {
        submitCloudletSpecsRealtime(batch.specs)
    }

    fun submitCloudletSpecsRealtime(specs: List<RealtimeCloudletSpec>) {
        traceMetadataProvider.putAll(specs)
        submitCloudletListRealtime(specs.map { it.cloudlet })
    }

    fun submitCloudletListRealtime(cloudletList: List<Cloudlet>) {
        for (cloudlet in cloudletList) {
            arrivalState.recordArrival(cloudlet)
            lifecycleStore.put(lifecycleService.createMetadata(cloudlet))
            dependencyController.register(cloudlet)
            cloudlet.addOnFinishListener {
                applyCommands(dependencyController.onSucceeded(cloudlet))
            }
        }
        val sortedCloudlets = cloudletList.sortedWith(cloudletOrdering.arrivalComparator())
        if (schedulingConfig.strategy.equals("static", ignoreCase = true)) {
            val previewWaiting = mutableListOf<Cloudlet>()
            for (cloudlet in sortedCloudlets) {
                arrivalState.preassign(
                    cloudlet,
                    vmSelectionFacade.staticPreviewSelection(cloudlet, previewWaiting.toList()),
                )
                previewWaiting.add(cloudlet)
            }
        }

        arrivalState.addRealtimeCloudlets(sortedCloudlets)
    }

    fun getWaitingCloudlets(): List<Cloudlet> = readModel.waitingCloudlets()

    private fun getActiveCloudlets(): List<Cloudlet> = readModel.activeCloudlets()

    fun getRejectedCount(): Int = readModel.rejectedCount()

    fun getCapacityRejectedCount(): Int = readModel.capacityRejectedCount()

    fun getResourceRejectedCount(): Int = readModel.resourceRejectedCount()

    fun getDeadlineRejectedCount(): Int = readModel.deadlineRejectedCount()

    fun getDeadlineDegradedCount(): Int = readModel.deadlineDegradedCount()

    fun getDeadlineRetryLaterCount(): Int = readModel.deadlineRetryLaterCount()

    fun getDeadlineMissAcceptedCount(): Int = readModel.deadlineMissAcceptedCount()

    fun getDependencyBlockedCount(): Int = readModel.dependencyBlockedCount()

    fun getDependencyReleasedCount(): Int = readModel.dependencyReleasedCount()

    fun getDependencyRejectedCount(): Int = readModel.dependencyRejectedCount()

    fun getRescheduleAttemptCount(): Int = readModel.rescheduleAttemptCount()

    fun getRescheduleSuccessCount(): Int = readModel.rescheduleSuccessCount()

    fun getRescheduleFailureCount(): Int = readModel.rescheduleFailureCount()

    fun getAverageRescheduleDelay(): Double = readModel.averageRescheduleDelay()

    fun getTenantQuotaRejectedCount(): Int = readModel.tenantQuotaRejectedCount()

    fun getTenantBudgetRejectedCount(): Int = readModel.tenantBudgetRejectedCount()

    fun getSubmittedCount(): Int = readModel.submittedCount()

    fun getRetryCount(): Int = readModel.retryCount()

    fun getRetrySuccessCount(): Int = readModel.retrySuccessCount()

    fun getPermanentFailedCount(): Int = readModel.permanentFailedCount()

    fun getRuntimeFailureCount(): Int = readModel.runtimeFailureCount()

    fun getTimeoutCancelledCount(): Int = readModel.timeoutCancelledCount()

    fun getMigrationCount(): Int = readModel.migrationCount()

    fun getCheckpointRecoveryCount(): Int = readModel.checkpointRecoveryCount()

    fun getScaleOutCount(): Int = readModel.scaleOutCount()

    fun getScaleInCount(): Int = readModel.scaleInCount()

    fun getActiveVmPeak(): Int = readModel.activeVmPeak()

    fun getAutoscalingCost(): Double = readModel.autoscalingCost()

    fun getColdStartDelayTotal(): Double = readModel.coldStartDelayTotal()

    fun getAverageDecisionDelay(): Double = readModel.averageDecisionDelay()

    fun getAverageQueueDepth(): Double = readModel.averageQueueDepth()

    fun getMaxQueueDepth(): Int = readModel.maxQueueDepth()

    fun getAverageRealtimeScore(): Double = readModel.averageRealtimeScore()

    fun getAverageSelectedLatenessPenalty(): Double = readModel.averageSelectedLatenessPenalty()

    fun getAverageSelectedDeadlineSlack(): Double = readModel.averageSelectedDeadlineSlack()

    fun getAverageCandidateScoreSpread(): Double = readModel.averageCandidateScoreSpread()

    fun getAveragePhysicalHostUtilization(): Double = readModel.averagePhysicalHostUtilization()

    fun getAverageHostResourceFragmentation(): Double = readModel.averageHostResourceFragmentation()

    fun getAverageNetworkTransferDelay(): Double = readModel.averageNetworkTransferDelay()

    fun getImageCacheHitRate(): Double = readModel.imageCacheHitRate()

    fun getAverageNoisyNeighborPressure(): Double = readModel.averageNoisyNeighborPressure()

    fun getCandidateScoreRecords(): List<RealtimeCandidateScoreRecord> = readModel.candidateScoreRecords()

    fun getTaskMetadata(cloudlet: Cloudlet): RealtimeTaskMetadata? = readModel.taskMetadata(cloudlet)

    fun getSlaViolationCount(cloudlets: List<Cloudlet>): Int = readModel.slaViolationCount(cloudlets)

    fun getRetrySuccessRate(): Double = readModel.retrySuccessRate()

    fun getPreemptedCount(): Int = readModel.preemptedCount()

    fun getPreemptionSuccessCount(): Int = readModel.preemptionSuccessCount()

    fun getPreemptionFailedCount(): Int = readModel.preemptionFailedCount()

    fun getAveragePreemptionDelay(): Double = readModel.averagePreemptionDelay()

    fun getPreemptionPenalty(): Double = readModel.preemptionPenalty()

    fun getCheckpointLossTotal(): Long = readModel.checkpointLossTotal()

    fun getTenantFairnessIndex(cloudlets: List<Cloudlet>): Double = readModel.tenantFairnessIndex(cloudlets)

    fun getDominantResourceFairnessIndex(): Double = readModel.dominantResourceFairnessIndex()

    fun getFairnessViolationCount(): Int = readModel.fairnessViolationCount()

    fun getTenantSlaPenalty(cloudlets: List<Cloudlet>): Double = readModel.tenantSlaPenalty(cloudlets)

    fun getCostSlaTradeoffScore(
        cost: Double,
        tenantSlaPenalty: Double,
    ): Double = readModel.costSlaTradeoffScore(cost, tenantSlaPenalty)

    fun getRetrySuccessByTenant(cloudlets: List<Cloudlet>): Double = readModel.retrySuccessByTenant(cloudlets)

    fun getTopologyMetrics(cloudlets: List<Cloudlet>): RealtimeTopologyMetrics = readModel.topologyMetrics(cloudlets)

    fun getHostFailureCount(): Int = readModel.hostFailureCount()

    fun getRackFailureCount(): Int = readModel.rackFailureCount()

    fun getRegionFailureCount(): Int = readModel.regionFailureCount()

    fun getArrivalTime(cloudlet: Cloudlet): Double = readModel.arrivalTime(cloudlet)

    fun getTimeoutCount(timeoutSeconds: Double): Int = readModel.timeoutCount(timeoutSeconds)

    override fun processEvent(event: SimEvent) {
        when (val brokerEvent = eventRouter.route(event)) {
            is RealtimeBrokerEvent.Arrival ->
                applyCommands(arrivalWorkflow.onArrival(brokerEvent.cloudlet, brokerEvent.time))
            is RealtimeBrokerEvent.Submit ->
                applyCommands(submissionWorkflow.onSubmit(brokerEvent.submission))
            is RealtimeBrokerEvent.Timeout ->
                applyCommands(
                    interruptionController.onTimeout(
                        brokerEvent.payload.cloudlet,
                        brokerEvent.payload.attempt,
                        brokerEvent.payload.runtimeToken,
                    ),
                )
            is RealtimeBrokerEvent.RuntimeFailure ->
                applyCommands(
                    interruptionController.onRuntimeFailure(
                        brokerEvent.payload.cloudlet,
                        brokerEvent.payload.attempt,
                        brokerEvent.payload.runtimeToken,
                    ),
                )
            is RealtimeBrokerEvent.AutoscaleTick ->
                applyCommands(
                    autoscalingController.tickCommands(
                        brokerEvent.time,
                        vmSelectionFacade.activeVmIndexes(getActiveCloudlets()),
                    ),
                )
            is RealtimeBrokerEvent.RescheduleTick ->
                applyCommands(reschedulingController.tickCommands(brokerEvent.time))
            is RealtimeBrokerEvent.Unknown ->
                super.processEvent(brokerEvent.event)
        }
    }

    private fun applyCommands(commands: Iterable<RealtimeBrokerCommand>) {
        commandExecutor.applyAll(commands)
    }

    private fun applyCommand(command: RealtimeBrokerCommand) {
        commandExecutor.apply(command)
    }

    override fun startInternal() {
        super.startInternal()
        for (cloudlet in arrivalState.realtimeCloudletsSnapshot()) {
            schedule(cloudlet.submissionDelay, RealtimeBrokerEventTags.ARRIVAL, cloudlet)
        }
        if (schedulingConfig.autoscalingEnabled && schedulingConfig.scaleInIdleTime > 0.0) {
            applyCommand(RealtimeBrokerCommand.ScheduleAutoscaleTick(schedulingConfig.scaleInIdleTime))
        }
        if (schedulingConfig.reschedulingEnabled && schedulingConfig.reschedulingInterval > 0.0) {
            applyCommand(RealtimeBrokerCommand.ScheduleRescheduleTick(schedulingConfig.reschedulingInterval))
        }
    }
}
