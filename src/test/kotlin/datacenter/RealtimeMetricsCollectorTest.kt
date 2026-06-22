package datacenter

import broker.RealtimeBroker
import config.DatacenterConfig
import config.ObjectiveWeightsConfig
import config.RealtimeSchedulingConfig
import org.assertj.core.api.Assertions.assertThat
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import scheduler.RealtimeCandidateScoreRecord
import scheduler.RealtimeScoreBreakdown
import scheduler.RealtimeTaskRecord
import scheduler.RealtimeTopologyMetrics

class RealtimeMetricsCollectorTest {
    private val scheduling = RealtimeSchedulingConfig(taskTimeout = 12.0)
    private val collector = RealtimeMetricsCollector(scheduling, ObjectiveWeightsConfig())

    @Test
    fun `collector projects empty task and broker metrics`() {
        val broker = brokerWithMetrics()
        val result =
            collector.collect(
                RealtimeMetricCollectionRequest(
                    algorithmName = "EMPTY",
                    cloudletList = emptyList(),
                    finishedCloudlets = emptyList(),
                    vmList = listOf(vm(10, 1_000.0)),
                    broker = broker,
                ),
            )

        assertThat(result.completedCount).isZero()
        assertThat(result.failedCount).isZero()
        assertThat(result.p95ResponseTime).isZero()
        assertThat(result.p99ResponseTime).isZero()
        assertProjectedBrokerMetrics(result)
    }

    @Test
    fun `collector handles success failed other status and unknown vm fallback`() {
        val vmList = listOf(vm(10, 1_000.0), vm(11, 2_000.0))
        val success =
            cloudlet(
                CloudletSpec(
                    id = 1,
                    status = Cloudlet.Status.SUCCESS,
                    vm = vm(99, 1_000.0),
                    finish = 20.0,
                    start = 8.0,
                    cpu = 5.0,
                ),
            )
        val failed = cloudlet(CloudletSpec(2, Cloudlet.Status.FAILED, vmList[1]))
        val waiting = cloudlet(CloudletSpec(3, Cloudlet.Status.INEXEC, vmList[0]))
        val broker = brokerWithMetrics()
        whenever(broker.getArrivalTime(success)).thenReturn(3.0)

        val result =
            collector.collect(
                RealtimeMetricCollectionRequest(
                    algorithmName = "MIXED",
                    cloudletList = listOf(success, failed, waiting),
                    finishedCloudlets = listOf(success, failed, waiting),
                    vmList = vmList,
                    broker = broker,
                ),
            )

        assertThat(result.completedCount).isEqualTo(1)
        assertThat(result.failedCount).isEqualTo(1)
        assertThat(result.averageWaitingTime).isEqualTo(5.0)
        assertThat(result.averageResponseTime).isEqualTo(17.0)
        assertThat(result.p95ResponseTime).isEqualTo(17.0)
        assertThat(result.p99ResponseTime).isEqualTo(17.0)
        assertThat(result.cost).isEqualTo(0.5)
        assertThat(result.fitness.isFinite()).isTrue()
        assertProjectedBrokerMetrics(result)
    }

    @Test
    fun `collector applies deadline sla penalty percentiles and vm cost tiers`() {
        val vmList =
            listOf(
                vm(10, DatacenterConfig.L_MIPS.toDouble()),
                vm(11, DatacenterConfig.M_MIPS.toDouble()),
                vm(12, DatacenterConfig.H_MIPS.toDouble()),
                vm(13, 1234.0),
            )
        val cloudlets = costTierCloudlets(vmList)
        val broker = brokerWithMetrics()
        stubCostTierArrivalsAndDeadlines(broker, cloudlets)

        val result =
            collector.collect(
                RealtimeMetricCollectionRequest(
                    algorithmName = "TIERS",
                    cloudletList = cloudlets,
                    finishedCloudlets = cloudlets,
                    vmList = vmList,
                    broker = broker,
                ),
            )

        assertThat(result.completedCount).isEqualTo(4)
        assertThat(result.failedCount).isZero()
        assertThat(result.averageWaitingTime).isEqualTo(1.75)
        assertThat(result.p95ResponseTime).isEqualTo(10.0)
        assertThat(result.p99ResponseTime).isEqualTo(10.0)
        assertThat(result.cost).isEqualTo(4.5)
        assertThat(result.slaViolationRate).isEqualTo(0.25)
        assertThat(result.slaPenalty).isEqualTo(13.5)
    }

    private fun costTierCloudlets(vmList: List<Vm>): List<Cloudlet> =
        listOf(
            cloudlet(CloudletSpec(id = 10, status = Cloudlet.Status.SUCCESS, vm = vmList[0], finish = 5.0, cpu = 1.0)),
            cloudlet(
                CloudletSpec(
                    id = 11,
                    status = Cloudlet.Status.SUCCESS,
                    vm = vmList[1],
                    finish = 11.0,
                    start = 9.0,
                    cpu = 2.0,
                ),
            ),
            cloudlet(
                CloudletSpec(
                    id = 12,
                    status = Cloudlet.Status.SUCCESS,
                    vm = vmList[2],
                    finish = 20.0,
                    start = 12.0,
                    cpu = 3.0,
                ),
            ),
            cloudlet(
                CloudletSpec(
                    id = 13,
                    status = Cloudlet.Status.SUCCESS,
                    vm = vmList[3],
                    finish = 8.0,
                    start = 1.0,
                    cpu = 4.0,
                ),
            ),
        )

    private fun stubCostTierArrivalsAndDeadlines(
        broker: RealtimeBroker,
        cloudlets: List<Cloudlet>,
    ) {
        whenever(broker.getArrivalTime(cloudlets[0])).thenReturn(2.0)
        whenever(broker.getArrivalTime(cloudlets[1])).thenReturn(5.0)
        whenever(broker.getArrivalTime(cloudlets[2])).thenReturn(10.0)
        whenever(broker.getArrivalTime(cloudlets[3])).thenReturn(0.0)
        whenever(broker.getTaskMetadata(cloudlets[1])).thenReturn(RealtimeTaskRecord(11, 5.0, deadline = 15.0))
        whenever(broker.getTaskMetadata(cloudlets[2])).thenReturn(RealtimeTaskRecord(12, 10.0, deadline = 18.0))
        whenever(broker.getTaskMetadata(cloudlets[3])).thenReturn(RealtimeTaskRecord(13, 0.0, deadline = 5.0))
    }

    private fun assertProjectedBrokerMetrics(result: RealtimeAlgorithmResult) {
        assertThat(result.rejectedCount).isEqualTo(1)
        assertThat(result.timeoutCount).isEqualTo(2)
        assertThat(result.retryCount).isEqualTo(3)
        assertThat(result.permanentFailedCount).isEqualTo(4)
        assertThat(result.averageDecisionDelay).isEqualTo(0.25)
        assertThat(result.averageRealtimeScore).isEqualTo(6.0)
        assertThat(result.averageSelectedLatenessPenalty).isEqualTo(0.0)
        assertThat(result.averageSelectedDeadlineSlack).isEqualTo(2.0)
        assertThat(result.averageCandidateScoreSpread).isEqualTo(3.0)
        assertThat(result.candidateScores).hasSize(1)
        assertThat(result.submittedCount).isEqualTo(9)
        assertThat(result.capacityRejectedCount).isEqualTo(10)
        assertThat(result.averageQueueDepth).isEqualTo(2.5)
        assertThat(result.maxQueueDepth).isEqualTo(11)
        assertThat(result.activeVmPeak).isEqualTo(5)
        assertThat(result.scaleOutCount).isEqualTo(12)
        assertThat(result.scaleInCount).isEqualTo(13)
        assertThat(result.autoscalingCost).isEqualTo(14.0)
        assertThat(result.coldStartDelayTotal).isEqualTo(15.0)
        assertThat(result.resourceRejectedCount).isEqualTo(16)
        assertThat(result.runtimeFailureCount).isEqualTo(17)
        assertThat(result.timeoutCancelledCount).isEqualTo(18)
        assertThat(result.migrationCount).isEqualTo(19)
        assertThat(result.checkpointRecoveryCount).isEqualTo(20)
        assertThat(result.retrySuccessRate).isEqualTo(0.5)
        assertThat(result.preemptedCount).isEqualTo(21)
        assertThat(result.preemptionSuccessCount).isEqualTo(22)
        assertThat(result.preemptionFailedCount).isEqualTo(23)
        assertThat(result.averagePreemptionDelay).isEqualTo(1.25)
        assertThat(result.checkpointLossTotal).isEqualTo(24)
        assertThat(result.costSlaTradeoffScore).isEqualTo(25.0)
        assertThat(result.tenantQuotaRejectedCount).isEqualTo(26)
        assertThat(result.tenantBudgetRejectedCount).isEqualTo(27)
        assertThat(result.tenantFairnessIndex).isEqualTo(0.75)
        assertThat(result.fairnessViolationCount).isEqualTo(28)
        assertThat(result.dominantResourceFairnessIndex).isEqualTo(0.8)
        assertThat(result.retrySuccessByTenant).isEqualTo(0.9)
        assertThat(result.crossRackAssignmentCount).isEqualTo(6)
        assertThat(result.hostFailureCount).isEqualTo(7)
        assertThat(result.rackFailureCount).isEqualTo(29)
        assertThat(result.regionFailureCount).isEqualTo(30)
        assertThat(result.slaPenalty).isEqualTo(8.5)
        assertThat(result.metrics.values.keys).containsAll(RealtimeMetricKey.entries)
    }

    private fun brokerWithMetrics(): RealtimeBroker {
        val broker = mock<RealtimeBroker>()
        stubCoreMetrics(broker)
        stubQueueAutoscalingMetrics(broker)
        stubReliabilityMetrics(broker)
        stubTenantMetrics(broker)
        stubTopologyMetrics(broker)
        return broker
    }

    private fun stubCoreMetrics(broker: RealtimeBroker) {
        whenever(broker.getActiveVmPeak()).thenReturn(5)
        whenever(broker.getSlaViolationCount(any())).thenReturn(1)
        whenever(broker.getTenantSlaPenalty(any())).thenReturn(1.5)
        whenever(broker.getPreemptionPenalty()).thenReturn(7.0)
        whenever(broker.getRejectedCount()).thenReturn(1)
        whenever(broker.getTimeoutCount(12.0)).thenReturn(2)
        whenever(broker.getRetryCount()).thenReturn(3)
        whenever(broker.getPermanentFailedCount()).thenReturn(4)
        whenever(broker.getAverageDecisionDelay()).thenReturn(0.25)
        whenever(broker.getAverageRealtimeScore()).thenReturn(6.0)
        whenever(broker.getAverageSelectedLatenessPenalty()).thenReturn(0.0)
        whenever(broker.getAverageSelectedDeadlineSlack()).thenReturn(2.0)
        whenever(broker.getAverageCandidateScoreSpread()).thenReturn(3.0)
        whenever(broker.getCandidateScoreRecords()).thenReturn(listOf(candidateScore()))
        whenever(broker.getCostSlaTradeoffScore(any(), any())).thenReturn(25.0)
    }

    private fun stubQueueAutoscalingMetrics(broker: RealtimeBroker) {
        whenever(broker.getSubmittedCount()).thenReturn(9)
        whenever(broker.getCapacityRejectedCount()).thenReturn(10)
        whenever(broker.getAverageQueueDepth()).thenReturn(2.5)
        whenever(broker.getMaxQueueDepth()).thenReturn(11)
        whenever(broker.getScaleOutCount()).thenReturn(12)
        whenever(broker.getScaleInCount()).thenReturn(13)
        whenever(broker.getAutoscalingCost()).thenReturn(14.0)
        whenever(broker.getColdStartDelayTotal()).thenReturn(15.0)
        whenever(broker.getResourceRejectedCount()).thenReturn(16)
    }

    private fun stubReliabilityMetrics(broker: RealtimeBroker) {
        whenever(broker.getRuntimeFailureCount()).thenReturn(17)
        whenever(broker.getTimeoutCancelledCount()).thenReturn(18)
        whenever(broker.getMigrationCount()).thenReturn(19)
        whenever(broker.getCheckpointRecoveryCount()).thenReturn(20)
        whenever(broker.getRetrySuccessRate()).thenReturn(0.5)
        whenever(broker.getPreemptedCount()).thenReturn(21)
        whenever(broker.getPreemptionSuccessCount()).thenReturn(22)
        whenever(broker.getPreemptionFailedCount()).thenReturn(23)
        whenever(broker.getAveragePreemptionDelay()).thenReturn(1.25)
        whenever(broker.getCheckpointLossTotal()).thenReturn(24)
    }

    private fun stubTenantMetrics(broker: RealtimeBroker) {
        whenever(broker.getTenantQuotaRejectedCount()).thenReturn(26)
        whenever(broker.getTenantBudgetRejectedCount()).thenReturn(27)
        whenever(broker.getTenantFairnessIndex(any())).thenReturn(0.75)
        whenever(broker.getFairnessViolationCount()).thenReturn(28)
        whenever(broker.getDominantResourceFairnessIndex()).thenReturn(0.8)
        whenever(broker.getRetrySuccessByTenant(any())).thenReturn(0.9)
    }

    private fun stubTopologyMetrics(broker: RealtimeBroker) {
        whenever(broker.getTopologyMetrics(any())).thenReturn(RealtimeTopologyMetrics(6, 2, 1.5, 2.5, 0.5))
        whenever(broker.getHostFailureCount()).thenReturn(7)
        whenever(broker.getRackFailureCount()).thenReturn(29)
        whenever(broker.getRegionFailureCount()).thenReturn(30)
    }

    private fun vm(
        id: Long,
        mips: Double,
    ): Vm =
        mock {
            on { getId() } doReturn id
            on { getMips() } doReturn mips
        }

    private data class CloudletSpec(
        val id: Long,
        val status: Cloudlet.Status,
        val vm: Vm,
        val finish: Double = 0.0,
        val start: Double = 0.0,
        val cpu: Double = 0.0,
    )

    private fun cloudlet(spec: CloudletSpec): Cloudlet =
        mock {
            on { getId() } doReturn spec.id
            on { getStatus() } doReturn spec.status
            on { getVm() } doReturn spec.vm
            on { getLength() } doReturn 1_000L
            on { getFinishTime() } doReturn spec.finish
            on { getStartTime() } doReturn spec.start
            on { getTotalExecutionTime() } doReturn spec.cpu
        }

    private fun candidateScore(): RealtimeCandidateScoreRecord =
        RealtimeCandidateScoreRecord(
            cloudletId = 1L,
            arrivalTime = 0.0,
            selectedVmIndex = 0,
            candidateVmIndex = 0,
            accepted = true,
            selected = true,
            breakdown =
                RealtimeScoreBreakdown(
                    totalScore = 6.0,
                    projectedFinishTime = 4.0,
                    estimatedRuntime = 1.0,
                    deadlineSlack = 2.0,
                    latenessPenalty = 0.0,
                    priorityPressure = 0.5,
                    preemptionCost = 0.0,
                    resourcePressure = 0.0,
                    topologyLatency = 0.0,
                    topologyCost = 0.0,
                    tenantFairnessPressure = 0.0,
                    queuePressure = 0.0,
                ),
        )
}
