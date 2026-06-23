package config

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * 配置验证测试
 */
class ConfigValidationTest {
    @Test
    fun `should create valid default config`() {
        // Given
        val config = ExperimentConfig.createDefault()

        // When - Then
        assertThat(config.batch.cloudletCount).isGreaterThan(0)
        assertThat(config.batch.population).isGreaterThan(0)
        assertThat(config.batch.maxIter).isGreaterThan(0)
        assertThat(config.realtime.cloudletCount).isGreaterThan(0)
        assertThat(config.realtime.simulationDuration).isGreaterThan(0.0)
        assertThat(config.optimizer.population).isGreaterThan(0)
    }

    @Test
    fun `should validate batch config parameters`() {
        // Given
        val invalidConfig =
            ExperimentConfig.createDefault().copy(
                batch =
                    ExperimentConfig.createDefault().batch.copy(
                        cloudletCount = -1,
                        population = 0,
                        maxIter = -5,
                        runs = 0,
                    ),
            )

        // When - Then
        try {
            ExperimentConfig.validate(invalidConfig)
            fail("Expected ConfigValidationException")
        } catch (e: ConfigValidationException) {
            assertThat(e.errors.any { it.message.contains("批处理任务数必须大于0") }).isTrue()
            assertThat(e.errors.any { it.message.contains("批处理种群大小必须大于0") }).isTrue()
            assertThat(e.errors.any { it.message.contains("批处理最大迭代次数必须大于0") }).isTrue()
            assertThat(e.errors.any { it.message.contains("批处理运行次数必须大于0") }).isTrue()
        }
    }

    @Test
    fun `should validate realtime config parameters`() {
        // Given
        val invalidConfig =
            ExperimentConfig.createDefault().copy(
                realtime =
                    ExperimentConfig.createDefault().realtime.copy(
                        cloudletCount = 0,
                        simulationDuration = -1.0,
                        arrivalRate = 0.0,
                        runs = -1,
                    ),
            )

        // When - Then
        try {
            ExperimentConfig.validate(invalidConfig)
            fail("Expected ConfigValidationException")
        } catch (e: ConfigValidationException) {
            assertThat(e.errors.any { it.message.contains("实时调度任务数必须大于0") }).isTrue()
            assertThat(e.errors.any { it.message.contains("仿真持续时间必须大于0") }).isTrue()
            assertThat(e.errors.any { it.message.contains("到达率必须大于0") }).isTrue()
            assertThat(e.errors.any { it.message.contains("实时调度运行次数必须大于0") }).isTrue()
        }
    }

    @Test
    fun `realtime validation keeps field path and message keyword`() {
        val invalidConfig =
            ExperimentConfig.createDefault().copy(
                realtime =
                    ExperimentConfig.createDefault().realtime.copy(
                        scheduling = RealtimeSchedulingConfig(decisionDelay = -1.0),
                    ),
            )

        val error =
            org.junit.jupiter.api.assertThrows<ConfigValidationException> {
                ExperimentConfig.validate(invalidConfig)
            }
        val decisionDelayError = error.errors.single { it.field == "realtime.scheduling.decisionDelay" }

        assertThat(decisionDelayError.message).contains("调度决策延迟")
        assertThat(decisionDelayError.message).contains("不能为负数")
    }

    @Test
    @Suppress("LongMethod") // This snapshot-style test intentionally lists all realtime core validation paths.
    fun `should validate realtime core and resource scheduling parameters`() {
        assertInvalidRealtimeFields(
            scheduling =
                RealtimeSchedulingConfig(
                    decisionDelay = -1.0,
                    decisionJitter = -0.1,
                    failureRate = 1.2,
                    retryLimit = -1,
                    retryDelay = -2.0,
                    retryBackoffMultiplier = 0.5,
                    queuePolicy = "fastest",
                    priorityLevels = 0,
                    highPriorityRatio = 1.1,
                    deadlineFactor = -0.1,
                    deadlineType = "urgent",
                    deadlineMissAction = "wait",
                    vmQueueCapacity = -1,
                    overloadFailureMultiplier = -0.2,
                    scaleOutQueueThreshold = -1,
                    scaleInIdleTime = -1.0,
                    maxDynamicVms = -1,
                    vmColdStartDelay = -1.0,
                    scaleOutCost = -1.0,
                    scaleInProtectionTime = -1.0,
                    networkLatency = -0.1,
                    imagePullDelay = -0.1,
                    ioWeight = -0.1,
                    ramWeight = -0.1,
                    bwWeight = -0.1,
                ),
            expectedFields =
                listOf(
                    "decisionDelay",
                    "decisionJitter",
                    "failureRate",
                    "retryLimit",
                    "retryDelay",
                    "retryBackoffMultiplier",
                    "queuePolicy",
                    "priorityLevels",
                    "highPriorityRatio",
                    "deadlineFactor",
                    "deadlineType",
                    "deadlineMissAction",
                    "vmQueueCapacity",
                    "overloadFailureMultiplier",
                    "scaleOutQueueThreshold",
                    "scaleInIdleTime",
                    "maxDynamicVms",
                    "vmColdStartDelay",
                    "scaleOutCost",
                    "scaleInProtectionTime",
                    "networkLatency",
                    "imagePullDelay",
                    "ioWeight",
                    "ramWeight",
                    "bwWeight",
                ),
        )
    }

    @Test
    fun `should require retry limit for deadline retry later action`() {
        assertInvalidRealtimeFields(
            scheduling = RealtimeSchedulingConfig(deadlineMissAction = "retry_later", retryLimit = 0),
            expectedFields = listOf("retryLimit"),
        )
    }

    @Test
    fun `should validate realtime rescheduling parameters`() {
        assertInvalidRealtimeFields(
            scheduling =
                RealtimeSchedulingConfig(
                    reschedulingEnabled = true,
                    reschedulingInterval = 0.0,
                    reschedulingPolicy = "aggressive",
                    maxReschedulesPerTask = 0,
                ),
            expectedFields =
                listOf(
                    "reschedulingPolicy",
                    "reschedulingInterval",
                    "maxReschedulesPerTask",
                ),
        )
    }

    @Test
    fun `should validate realtime workload parameters`() {
        assertInvalidRealtimeArrivalFields(
            arrival =
                RealtimeArrivalConfig(
                    distribution = "calendar",
                    workloadPattern = "mesh",
                    periodSeconds = 0.0,
                    arrivalJitter = -0.1,
                    sporadicMinInterArrival = 0.0,
                    sporadicMaxInterArrival = -1.0,
                    diurnalPeakMultiplier = 0.0,
                    diurnalOffPeakMultiplier = 0.0,
                    shortTaskRatio = 1.5,
                    shortTaskLengthMultiplier = 0.0,
                    longTaskLengthMultiplier = 0.0,
                    runtimeReferenceMips = 0.0,
                    dagDepth = 0,
                    dagWidth = 0,
                    dagFanOut = 0,
                ),
            expectedFields =
                listOf(
                    "distribution",
                    "workloadPattern",
                    "periodSeconds",
                    "arrivalJitter",
                    "sporadicMinInterArrival",
                    "sporadicMaxInterArrival",
                    "diurnalPeakMultiplier",
                    "diurnalOffPeakMultiplier",
                    "shortTaskRatio",
                    "shortTaskLengthMultiplier",
                    "longTaskLengthMultiplier",
                    "runtimeReferenceMips",
                    "dagDepth",
                    "dagWidth",
                    "dagFanOut",
                ),
        )
    }

    @Test
    fun `should accept new realtime workload distributions and patterns`() {
        val defaults = ExperimentConfig.createDefault()
        val config =
            defaults.copy(
                realtime =
                    defaults.realtime.copy(
                        arrival =
                            RealtimeArrivalConfig(
                                distribution = "diurnal_burst",
                                workloadPattern = "dag_layered",
                            ),
                    ),
            )

        ExperimentConfig.validate(config)
    }

    @Test
    fun `should validate realtime reliability parameters`() {
        assertInvalidRealtimeFields(
            scheduling =
                RealtimeSchedulingConfig(
                    runtimeFailureRate = 1.5,
                    nodeFailureRate = -0.1,
                    checkpointInterval = -1.0,
                    migrationDelay = -1.0,
                    timeoutAction = "pause",
                    preemptionPolicy = "random",
                    preemptionMinPriorityGap = -1,
                    preemptionMaxPerTask = -1,
                    preemptionDelay = -0.1,
                    preemptionPenalty = -0.1,
                ),
            expectedFields =
                listOf(
                    "runtimeFailureRate",
                    "nodeFailureRate",
                    "checkpointInterval",
                    "migrationDelay",
                    "timeoutAction",
                    "preemptionPolicy",
                    "preemptionMinPriorityGap",
                    "preemptionMaxPerTask",
                    "preemptionDelay",
                    "preemptionPenalty",
                ),
        )
    }

    @Test
    fun `should validate realtime tenant parameters`() {
        assertInvalidRealtimeFields(
            scheduling =
                RealtimeSchedulingConfig(
                    tenantCount = 0,
                    tenantQuota = listOf(1, -1),
                    tenantWeights = listOf(1.0, 0.0),
                    tenantFairnessPolicy = "lottery",
                    tenantSchedulingPolicy = "lottery",
                    tenantBurstAllowance = -1,
                    tenantSlaPenaltyWeight = -0.1,
                    tenantCostBudget = listOf(10.0, -1.0),
                ),
            expectedFields =
                listOf(
                    "tenantCount",
                    "tenantQuota",
                    "tenantQuota[1]",
                    "tenantWeights",
                    "tenantWeights[1]",
                    "tenantFairnessPolicy",
                    "tenantSchedulingPolicy",
                    "tenantBurstAllowance",
                    "tenantSlaPenaltyWeight",
                    "tenantCostBudget",
                    "tenantCostBudget[1]",
                ),
        )
    }

    @Test
    fun `should validate realtime topology parameters`() {
        assertInvalidRealtimeFields(
            scheduling =
                RealtimeSchedulingConfig(
                    topologyPolicy = "random",
                    regionCount = 0,
                    racksPerRegion = 0,
                    hostsPerRack = 0,
                    localRegion = 2,
                    crossRackLatency = -0.1,
                    crossRegionLatency = -0.1,
                    crossRegionCost = -0.1,
                    hostFailureRate = 1.1,
                    rackFailureRate = -0.1,
                    regionFailureRate = 1.2,
                    hostCountPerRack = 0,
                    hostCpuCapacity = -1.0,
                    cpuOvercommitRatio = 0.0,
                    hostRamCapacity = -1.0,
                    hostBwCapacity = -1.0,
                    hostIoCapacity = -1.0,
                    noisyNeighborPenaltyWeight = -0.1,
                    crossRackBandwidth = -1.0,
                    crossRegionBandwidth = -1.0,
                    dataLocalityPolicy = "nearest",
                    imageCacheCapacity = -1,
                ),
            expectedFields =
                listOf(
                    "topologyPolicy",
                    "regionCount",
                    "racksPerRegion",
                    "hostsPerRack",
                    "localRegion",
                    "crossRackLatency",
                    "crossRegionLatency",
                    "crossRegionCost",
                    "hostFailureRate",
                    "rackFailureRate",
                    "regionFailureRate",
                    "hostCountPerRack",
                    "hostCpuCapacity",
                    "cpuOvercommitRatio",
                    "hostRamCapacity",
                    "hostBwCapacity",
                    "hostIoCapacity",
                    "noisyNeighborPenaltyWeight",
                    "crossRackBandwidth",
                    "crossRegionBandwidth",
                    "dataLocalityPolicy",
                    "imageCacheCapacity",
                ),
        )
    }

    @Test
    fun `should validate optimizer config parameters`() {
        // Given
        val invalidConfig =
            ExperimentConfig.createDefault().copy(
                optimizer =
                    ExperimentConfig.createDefault().optimizer.copy(
                        population = -1,
                        maxIter = 0,
                    ),
            )

        // When - Then
        try {
            ExperimentConfig.validate(invalidConfig)
            fail("Expected ConfigValidationException")
        } catch (e: ConfigValidationException) {
            assertThat(e.errors.any { it.message.contains("优化算法种群大小必须大于0") }).isTrue()
            assertThat(e.errors.any { it.message.contains("优化算法最大迭代次数必须大于0") }).isTrue()
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = [-0.1, 1.1, 2.0])
    fun `should validate objective weights range`(invalidWeight: Double) {
        // Given - When - Then
        assertThatThrownBy {
            ObjectiveWeightsConfig(cost = invalidWeight)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("权重必须在[0,1]范围内")

        assertThatThrownBy {
            ObjectiveWeightsConfig(totalTime = invalidWeight)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("权重必须在[0,1]范围内")
    }

    @Test
    fun `should validate objective weights sum greater than zero`() {
        // Given - When - Then
        assertThatThrownBy {
            ObjectiveWeightsConfig(cost = 0.0, totalTime = 0.0, loadBalance = 0.0, makespan = 0.0)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("权重总和必须大于0")
    }

    @Test
    fun `should create valid objective weights config`() {
        // Given
        val weights =
            ObjectiveWeightsConfig(
                cost = 0.3,
                totalTime = 0.4,
                loadBalance = 0.2,
                makespan = 0.1,
            )

        // When - Then
        assertThat(weights.cost).isEqualTo(0.3)
        assertThat(weights.totalTime).isEqualTo(0.4)
        assertThat(weights.loadBalance).isEqualTo(0.2)
        assertThat(weights.makespan).isEqualTo(0.1)
    }

    @Test
    fun `should parse generator types correctly`() {
        // Given - When - Then
        assertThat(ExperimentConfig.parseGeneratorType("LOG_NORMAL")).isEqualTo(CloudletGeneratorType.LOG_NORMAL)
        assertThat(ExperimentConfig.parseGeneratorType("UNIFORM")).isEqualTo(CloudletGeneratorType.UNIFORM)
        assertThat(ExperimentConfig.parseGeneratorType("GOOGLE_TRACE")).isEqualTo(CloudletGeneratorType.GOOGLE_TRACE)

        // Case insensitive
        assertThat(ExperimentConfig.parseGeneratorType("log_normal")).isEqualTo(CloudletGeneratorType.LOG_NORMAL)
        assertThat(ExperimentConfig.parseGeneratorType("uniform")).isEqualTo(CloudletGeneratorType.UNIFORM)
    }

    @Test
    fun `should handle invalid generator type gracefully`() {
        // Given - When
        val type = ExperimentConfig.parseGeneratorType("INVALID_TYPE")

        // Then
        assertThat(type).isEqualTo(CloudletGeneratorType.LOG_NORMAL)
    }

    @Test
    fun `should create valid generator configs`() {
        // Given - When - Then
        assertThat(GeneratorConfig.LOG_NORMAL.type).isEqualTo("LOG_NORMAL")
        assertThat(GeneratorConfig.UNIFORM.type).isEqualTo("UNIFORM")
        assertThat(GeneratorConfig.GOOGLE_TRACE.type).isEqualTo("GOOGLE_TRACE")
    }

    @Test
    fun `should validate google trace config parameters`() {
        // Given
        val config =
            GoogleTraceConfig(
                filePath = "test/path.csv",
                maxTasks = 100,
                timeWindowStart = 1000L,
                timeWindowEnd = 2000L,
                normalizeTimestamps = false,
                timestampDivisor = 1000.0,
            )

        // When - Then
        assertThat(config.filePath).isEqualTo("test/path.csv")
        assertThat(config.maxTasks).isEqualTo(100)
        assertThat(config.timeWindowStart).isEqualTo(1000L)
        assertThat(config.timeWindowEnd).isEqualTo(2000L)
        assertThat(config.normalizeTimestamps).isFalse()
        assertThat(config.timestampDivisor).isEqualTo(1000.0)
    }

    @Test
    fun `should validate google trace timestamp divisor`() {
        val defaults = ExperimentConfig.createDefault()
        val invalidConfig =
            defaults.copy(
                realtime =
                    defaults.realtime.copy(
                        googleTraceConfig = GoogleTraceConfig(timestampDivisor = 0.0),
                    ),
            )
        val error =
            org.junit.jupiter.api.assertThrows<ConfigValidationException> {
                ExperimentConfig.validate(invalidConfig)
            }

        assertThat(error.errors.map { it.field }).contains("realtime.googleTrace.timestampDivisor")
    }

    private fun assertInvalidRealtimeFields(
        scheduling: RealtimeSchedulingConfig,
        expectedFields: List<String>,
    ) {
        val defaults = ExperimentConfig.createDefault()
        val invalidConfig = defaults.copy(realtime = defaults.realtime.copy(scheduling = scheduling))
        val error =
            org.junit.jupiter.api.assertThrows<ConfigValidationException> {
                ExperimentConfig.validate(invalidConfig)
            }
        val fieldPrefix = "realtime.scheduling."

        assertThat(error.errors.map { it.field })
            .containsExactlyElementsOf(expectedFields.map { "$fieldPrefix$it" })
    }

    private fun assertInvalidRealtimeArrivalFields(
        arrival: RealtimeArrivalConfig,
        expectedFields: List<String>,
    ) {
        val defaults = ExperimentConfig.createDefault()
        val invalidConfig = defaults.copy(realtime = defaults.realtime.copy(arrival = arrival))
        val error =
            org.junit.jupiter.api.assertThrows<ConfigValidationException> {
                ExperimentConfig.validate(invalidConfig)
            }
        val fieldPrefix = "realtime.arrival."

        assertThat(error.errors.map { it.field })
            .containsExactlyElementsOf(expectedFields.map { "$fieldPrefix$it" })
    }
}
