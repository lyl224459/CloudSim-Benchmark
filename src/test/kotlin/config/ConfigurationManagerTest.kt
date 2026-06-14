package config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption

class ConfigurationManagerTest {
    @Test
    fun `loadFromSingleFile reads profiles and system config`() {
        val configFile =
            createTempTomlFile(
                """
                defaultProfile = "quick"

                [output]
                resultsDir = "runs/test"

                [output.csv]
                enabled = false
                delimiter = ";"

                [logging]
                level = "DEBUG"
                console = false

                [profiles.quick]
                mode = "batch"
                algorithms = ["PSO", "WOA"]
                runs = 2
                outputDir = "runs/quick"

                [profiles.quick.batch]
                cloudletCount = 120
                population = 40
                maxIter = 80
                """.trimIndent(),
            )

        try {
            val configs = ConfigurationManager.loadFromSingleFile(configFile.absolutePath)

            assertEquals("runs/test", configs.systemConfig.output.resultsDir)
            assertFalse(configs.systemConfig.output.csv.enabled)
            assertEquals("DEBUG", configs.systemConfig.logging.level)
            assertEquals("quick", configs.experimentConfig.defaultProfile)
            assertTrue(configs.experimentConfig.profiles.containsKey("quick"))
            assertEquals(
                120,
                configs.experimentConfig.profiles["quick"]
                    ?.batch
                    ?.cloudletCount,
            )
            assertEquals(2, configs.experimentConfig.profiles["quick"]?.runs)
            assertEquals(listOf("PSO", "WOA"), configs.experimentConfig.profiles["quick"]?.algorithms)
        } finally {
            configFile.delete()
        }
    }

    @Test
    fun `loadFromSingleFile reads nested profile batch objective and realtime scheduling`() {
        val configFile = File("src/test/resources/config/nested-profile-config.toml")
        val configs = ConfigurationManager.loadFromSingleFile(configFile.absolutePath)

        assertNestedBatchObjective(configs)
        assertNestedRealtimeCoreAndResource(configs)
        assertNestedRealtimeReliabilityAndTenant(configs)
        assertNestedRealtimeTopology(configs)
    }

    @Test
    fun `loadFromSingleFile supports standard toml dynamic maps and multiline arrays`() {
        val configFile =
            createTempTomlFile(
                """
                defaultProfile = "batch smoke"

                [profiles."batch smoke"]
                mode = "batch"
                algorithms = [
                    "PSO",
                    "WOA",
                ]
                tasks = [
                    50,
                    100,
                ]

                [profiles."batch smoke".batch]
                cloudletCounts = [50, 100, 150]
                population = 12

                [algorithms."pso realtime"]
                enabled = true
                description = "quoted algorithm profile"
                population = 8
                maxIter = 9

                [presets."fast set"]
                algorithms = ["PSO", "WOA"]
                """.trimIndent(),
            )

        try {
            val configs = ConfigurationManager.loadFromSingleFile(configFile.absolutePath)
            val profile = configs.experimentConfig.profiles["batch smoke"]

            assertEquals("batch smoke", configs.experimentConfig.defaultProfile)
            assertEquals(listOf("PSO", "WOA"), profile?.algorithms)
            assertEquals(listOf(50, 100), profile?.tasks)
            assertEquals(listOf(50, 100, 150), profile?.batch?.cloudletCounts)
            assertEquals(12, profile?.batch?.population)
            assertEquals(8, configs.experimentConfig.algorithmConfigs["PSO_REALTIME"]?.population)
            assertEquals(9, configs.experimentConfig.algorithmConfigs["PSO_REALTIME"]?.maxIter)
            assertEquals(listOf("PSO", "WOA"), configs.experimentConfig.presets["fast set"]?.algorithms)
        } finally {
            configFile.delete()
        }
    }

    @Test
    fun `loadFromSingleFile rejects unknown profile field`() {
        val configFile =
            createTempTomlFile(
                """
                defaultProfile = "bad"

                [profiles.bad]
                mode = "batch"
                unsupported = true

                [profiles.bad.batch]
                cloudletCount = 10
                """.trimIndent(),
            )

        try {
            val error =
                assertThrows<IllegalArgumentException> {
                    ConfigurationManager.loadFromSingleFile(configFile.absolutePath)
                }
            assertTrue(error.message?.contains("未知字段") == true)
        } finally {
            configFile.delete()
        }
    }

    @Test
    fun `loadFromSingleFile rejects old top level schema`() {
        val configFile =
            createTempTomlFile(
                """
                mode = "batch"

                [batch]
                cloudletCount = 100
                """.trimIndent(),
            )

        try {
            assertThrows<IllegalArgumentException> {
                ConfigurationManager.loadFromSingleFile(configFile.absolutePath)
            }
        } finally {
            configFile.delete()
        }
    }

    @Test
    fun `loadFromSingleFile with non existent file throws exception`() {
        assertThrows<IllegalArgumentException> {
            ConfigurationManager.loadFromSingleFile("non-existent-file.toml")
        }
    }

    @Test
    fun `loadFromSingleFile with blank file path throws exception`() {
        assertThrows<IllegalArgumentException> {
            ConfigurationManager.loadFromSingleFile("   ")
        }
    }

    @Test
    fun `loadFromSeparateFiles uses system defaults and profile config`() {
        val systemConfigFile =
            createTempTomlFile(
                """
                [output]
                resultsDir = "custom-runs"

                [logging]
                level = "INFO"
                """.trimIndent(),
            )

        val experimentConfigFile =
            createTempTomlFile(
                """
                defaultProfile = "realtime"

                [profiles.realtime]
                mode = "realtime"
                algorithms = ["MIN_LOAD"]
                runs = 1

                [profiles.realtime.realtime]
                cloudletCount = 300
                simulationDuration = 1000.0
                """.trimIndent(),
            )

        try {
            val configs =
                ConfigurationManager.loadFromSeparateFiles(
                    systemConfigPath = systemConfigFile.absolutePath,
                    experimentConfigPath = experimentConfigFile.absolutePath,
                )

            assertEquals("custom-runs", configs.systemConfig.output.resultsDir)
            assertEquals("INFO", configs.systemConfig.logging.level)
            assertEquals(
                300,
                configs.experimentConfig.profiles["realtime"]
                    ?.realtime
                    ?.cloudletCount,
            )
            assertEquals("realtime", configs.experimentConfig.defaultProfile)
        } finally {
            systemConfigFile.delete()
            experimentConfigFile.delete()
        }
    }

    @Test
    fun `loadFromSingleFile with empty file throws exception`() {
        val configFile = createTempTomlFile("")

        try {
            assertThrows<IllegalArgumentException> {
                ConfigurationManager.loadFromSingleFile(configFile.absolutePath)
            }
        } finally {
            configFile.delete()
        }
    }

    @Test
    fun `loadFromSingleFile with invalid toml format throws exception`() {
        val configFile =
            createTempTomlFile(
                """
                This is not valid TOML content
                [
                """.trimIndent(),
            )

        try {
            assertThrows<IllegalArgumentException> {
                ConfigurationManager.loadFromSingleFile(configFile.absolutePath)
            }
        } finally {
            configFile.delete()
        }
    }

    private fun assertNestedBatchObjective(configs: ConfigurationManager.LoadedConfigs) {
        val batch = requireNotNull(configs.experimentConfig.profiles["batch_nested"]?.batch)

        assertEquals(0.2, batch.objective.cost)
        assertEquals(0.3, batch.objective.totalTime)
        assertEquals(0.4, batch.objective.loadBalance)
        assertEquals(0.1, batch.objective.makespan)
    }

    private fun assertNestedRealtimeCoreAndResource(configs: ConfigurationManager.LoadedConfigs) {
        val realtime = requireNotNull(configs.experimentConfig.profiles["realtime_nested"]?.realtime)
        val scheduling = realtime.scheduling

        assertEquals("burst", realtime.arrival.distribution)
        assertEquals(3.5, realtime.arrival.burstIntensity)
        assertEquals(25.0, realtime.arrival.burstDuration)
        assertEquals("static", scheduling.strategy)
        assertEquals(10, scheduling.maxQueueSize)
        assertEquals(15.0, scheduling.taskTimeout)
        assertEquals("partial", scheduling.resourceReservation)
        assertEquals(0.5, scheduling.decisionDelay)
        assertEquals(0.2, scheduling.decisionJitter)
        assertEquals(0.1, scheduling.failureRate)
        assertEquals(2, scheduling.retryLimit)
        assertEquals(1.5, scheduling.retryDelay)
        assertEquals(2.0, scheduling.retryBackoffMultiplier)
        assertEquals("priority", scheduling.queuePolicy)
        assertEquals(4, scheduling.priorityLevels)
        assertEquals(0.25, scheduling.highPriorityRatio)
        assertEquals(1.5, scheduling.deadlineFactor)
        assertEquals(3, scheduling.vmQueueCapacity)
        assertEquals(0.2, scheduling.overloadFailureMultiplier)
        assertTrue(scheduling.autoscalingEnabled)
        assertEquals(2, scheduling.scaleOutQueueThreshold)
        assertEquals(10.0, scheduling.scaleInIdleTime)
        assertEquals(3, scheduling.maxDynamicVms)
        assertEquals(4.0, scheduling.vmColdStartDelay)
        assertEquals(0.25, scheduling.scaleOutCost)
        assertEquals(8.0, scheduling.scaleInProtectionTime)
        assertTrue(scheduling.resourceModelEnabled)
        assertEquals(0.05, scheduling.networkLatency)
        assertEquals(0.5, scheduling.imagePullDelay)
        assertEquals(1.0, scheduling.ioWeight)
        assertEquals(0.5, scheduling.ramWeight)
        assertEquals(0.25, scheduling.bwWeight)
    }

    private fun assertNestedRealtimeReliabilityAndTenant(configs: ConfigurationManager.LoadedConfigs) {
        val scheduling = requireNotNull(configs.experimentConfig.profiles["realtime_nested"]?.realtime).scheduling

        assertEquals(0.03, scheduling.runtimeFailureRate)
        assertEquals(0.02, scheduling.nodeFailureRate)
        assertEquals(5.0, scheduling.checkpointInterval)
        assertEquals(1.5, scheduling.migrationDelay)
        assertEquals("retry", scheduling.timeoutAction)
        assertTrue(scheduling.preemptionEnabled)
        assertEquals("deadline_then_priority", scheduling.preemptionPolicy)
        assertEquals(2, scheduling.preemptionMinPriorityGap)
        assertEquals(3, scheduling.preemptionMaxPerTask)
        assertEquals(0.4, scheduling.preemptionDelay)
        assertEquals(0.7, scheduling.preemptionPenalty)
        assertTrue(scheduling.multiTenantEnabled)
        assertEquals(3, scheduling.tenantCount)
        assertEquals(listOf(2, 1, 1), scheduling.tenantQuota)
        assertEquals(listOf(1.0, 2.0, 1.0), scheduling.tenantWeights)
        assertEquals("weighted_fair", scheduling.tenantFairnessPolicy)
        assertEquals("dominant_resource_fairness", scheduling.tenantSchedulingPolicy)
        assertEquals(2, scheduling.tenantBurstAllowance)
        assertEquals(1.5, scheduling.tenantSlaPenaltyWeight)
        assertEquals(listOf(10.0, 20.0, 15.0), scheduling.tenantCostBudget)
    }

    private fun assertNestedRealtimeTopology(configs: ConfigurationManager.LoadedConfigs) {
        val scheduling = requireNotNull(configs.experimentConfig.profiles["realtime_nested"]?.realtime).scheduling

        assertTrue(scheduling.topologyEnabled)
        assertEquals("spread_fault_domains", scheduling.topologyPolicy)
        assertEquals(4, scheduling.regionCount)
        assertEquals(3, scheduling.racksPerRegion)
        assertEquals(2, scheduling.hostsPerRack)
        assertEquals(1, scheduling.localRegion)
        assertEquals(0.15, scheduling.crossRackLatency)
        assertEquals(2.5, scheduling.crossRegionLatency)
        assertEquals(0.8, scheduling.crossRegionCost)
        assertEquals(0.01, scheduling.hostFailureRate)
        assertEquals(0.02, scheduling.rackFailureRate)
        assertEquals(0.03, scheduling.regionFailureRate)
        assertTrue(scheduling.physicalTopologyEnabled)
        assertTrue(scheduling.dataLocalityEnabled)
        assertTrue(scheduling.imageCacheEnabled)
        assertEquals(4, scheduling.hostCountPerRack)
        assertEquals(8.0, scheduling.hostCpuCapacity)
        assertEquals(32768.0, scheduling.hostRamCapacity)
        assertEquals(10000.0, scheduling.hostBwCapacity)
        assertEquals(5000.0, scheduling.hostIoCapacity)
        assertEquals(20.0, scheduling.crossRackBandwidth)
        assertEquals(5.0, scheduling.crossRegionBandwidth)
        assertEquals("balanced", scheduling.dataLocalityPolicy)
        assertEquals(3, scheduling.imageCacheCapacity)
    }

    private fun createTempTomlFile(content: String): File {
        val tempFile = Files.createTempFile("test-config-", ".toml").toFile()
        Files.write(tempFile.toPath(), content.toByteArray(), StandardOpenOption.WRITE)
        return tempFile
    }
}
