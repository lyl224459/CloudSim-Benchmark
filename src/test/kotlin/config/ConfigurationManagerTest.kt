package config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption

class ConfigurationManagerTest {

    @Test
    fun `loadFromSingleFile reads profiles and system config`() {
        val configFile = createTempTomlFile("""
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
        """.trimIndent())

        try {
            val configs = ConfigurationManager.loadFromSingleFile(configFile.absolutePath)

            assertEquals("runs/test", configs.systemConfig.output.resultsDir)
            assertFalse(configs.systemConfig.output.csv.enabled)
            assertEquals("DEBUG", configs.systemConfig.logging.level)
            assertEquals("quick", configs.experimentConfig.defaultProfile)
            assertTrue(configs.experimentConfig.profiles.containsKey("quick"))
            assertEquals(120, configs.experimentConfig.profiles["quick"]?.batch?.cloudletCount)
            assertEquals(2, configs.experimentConfig.profiles["quick"]?.runs)
            assertEquals(listOf("PSO", "WOA"), configs.experimentConfig.profiles["quick"]?.algorithms)
        } finally {
            configFile.delete()
        }
    }

    @Test
    fun `loadFromSingleFile reads nested profile batch objective and realtime scheduling`() {
        val configFile = createTempTomlFile("""
            defaultProfile = "realtime_nested"

            [profiles.batch_nested]
            mode = "batch"
            algorithms = ["PSO"]

            [profiles.batch_nested.batch]
            cloudletCount = 64

            [profiles.batch_nested.batch.objective]
            cost = 0.2
            totalTime = 0.3
            loadBalance = 0.4
            makespan = 0.1

            [profiles.realtime_nested]
            mode = "realtime"
            algorithms = ["MIN_LOAD"]

            [profiles.realtime_nested.realtime]
            cloudletCount = 80
            simulationDuration = 200.0
            arrivalRate = 2.0

            [profiles.realtime_nested.realtime.arrival]
            distribution = "burst"
            burstIntensity = 3.5
            burstDuration = 25.0

            [profiles.realtime_nested.realtime.scheduling]
            strategy = "static"
            maxQueueSize = 10
            taskTimeout = 15.0
            resourceReservation = "partial"
            decisionDelay = 0.5
            decisionJitter = 0.2
            failureRate = 0.1
            retryLimit = 2
            retryDelay = 1.5
            retryBackoffMultiplier = 2.0
        """.trimIndent())

        try {
            val configs = ConfigurationManager.loadFromSingleFile(configFile.absolutePath)
            val batch = configs.experimentConfig.profiles["batch_nested"]?.batch
            val realtime = configs.experimentConfig.profiles["realtime_nested"]?.realtime

            assertEquals(0.2, batch?.objective?.cost)
            assertEquals(0.3, batch?.objective?.totalTime)
            assertEquals(0.4, batch?.objective?.loadBalance)
            assertEquals(0.1, batch?.objective?.makespan)
            assertEquals("burst", realtime?.arrival?.distribution)
            assertEquals(3.5, realtime?.arrival?.burstIntensity)
            assertEquals(25.0, realtime?.arrival?.burstDuration)
            assertEquals("static", realtime?.scheduling?.strategy)
            assertEquals(10, realtime?.scheduling?.maxQueueSize)
            assertEquals(15.0, realtime?.scheduling?.taskTimeout)
            assertEquals("partial", realtime?.scheduling?.resourceReservation)
            assertEquals(0.5, realtime?.scheduling?.decisionDelay)
            assertEquals(0.2, realtime?.scheduling?.decisionJitter)
            assertEquals(0.1, realtime?.scheduling?.failureRate)
            assertEquals(2, realtime?.scheduling?.retryLimit)
            assertEquals(1.5, realtime?.scheduling?.retryDelay)
            assertEquals(2.0, realtime?.scheduling?.retryBackoffMultiplier)
        } finally {
            configFile.delete()
        }
    }

    @Test
    fun `loadFromSingleFile rejects unknown profile field`() {
        val configFile = createTempTomlFile("""
            defaultProfile = "bad"

            [profiles.bad]
            mode = "batch"
            unsupported = true

            [profiles.bad.batch]
            cloudletCount = 10
        """.trimIndent())

        try {
            val error = assertThrows<IllegalArgumentException> {
                ConfigurationManager.loadFromSingleFile(configFile.absolutePath)
            }
            assertTrue(error.message?.contains("未知字段") == true)
        } finally {
            configFile.delete()
        }
    }

    @Test
    fun `loadFromSingleFile rejects old top level schema`() {
        val configFile = createTempTomlFile("""
            mode = "batch"

            [batch]
            cloudletCount = 100
        """.trimIndent())

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
        val systemConfigFile = createTempTomlFile("""
            [output]
            resultsDir = "custom-runs"

            [logging]
            level = "INFO"
        """.trimIndent())

        val experimentConfigFile = createTempTomlFile("""
            defaultProfile = "realtime"

            [profiles.realtime]
            mode = "realtime"
            algorithms = ["MIN_LOAD"]
            runs = 1

            [profiles.realtime.realtime]
            cloudletCount = 300
            simulationDuration = 1000.0
        """.trimIndent())

        try {
            val configs = ConfigurationManager.loadFromSeparateFiles(
                systemConfigPath = systemConfigFile.absolutePath,
                experimentConfigPath = experimentConfigFile.absolutePath
            )

            assertEquals("custom-runs", configs.systemConfig.output.resultsDir)
            assertEquals("INFO", configs.systemConfig.logging.level)
            assertEquals(300, configs.experimentConfig.profiles["realtime"]?.realtime?.cloudletCount)
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
        val configFile = createTempTomlFile("""
            This is not valid TOML content
            [
        """.trimIndent())

        try {
            assertThrows<IllegalArgumentException> {
                ConfigurationManager.loadFromSingleFile(configFile.absolutePath)
            }
        } finally {
            configFile.delete()
        }
    }

    private fun createTempTomlFile(content: String): File {
        val tempFile = Files.createTempFile("test-config-", ".toml").toFile()
        Files.write(tempFile.toPath(), content.toByteArray(), StandardOpenOption.WRITE)
        return tempFile
    }
}
