package config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption

class ConfigurationManagerIntegrationTest {

    @Test
    fun `test loadFromSingleFile with valid mixed config`() {
        val configFile = createTempTomlFile("""
            mode = "batch"
            
            [output]
            resultsDir = "integration-test-runs"
            
            [logging]
            level = "DEBUG"
            
            [batch]
            cloudletCount = 100
            population = 30
            runs = 1
        """.trimIndent())
        
        try {
            val configs = ConfigurationManager.loadFromSingleFile(configFile.absolutePath)
            
            assertNotNull(configs.systemConfig)
            assertNotNull(configs.experimentConfig)
            assertEquals("integration-test-runs", configs.systemConfig.output.resultsDir)
            assertEquals("DEBUG", configs.systemConfig.logging.level)
            assertEquals(100, configs.experimentConfig.batch.cloudletCount)
        } finally {
            configFile.delete()
        }
    }

    @Test
    fun `test loadFromSingleFile with valid experiment-only config`() {
        val configFile = createTempTomlFile("""
            mode = "realtime"
            
            [realtime]
            cloudletCount = 200
            simulationDuration = 1000.0
            runs = 3
        """.trimIndent())
        
        try {
            val configs = ConfigurationManager.loadFromSingleFile(configFile.absolutePath)
            
            assertNotNull(configs.systemConfig)
            assertNotNull(configs.experimentConfig)
            assertEquals(200, configs.experimentConfig.realtime.cloudletCount)
            assertEquals(1000.0, configs.experimentConfig.realtime.simulationDuration)
            assertEquals(3, configs.experimentConfig.realtime.runs)
        } finally {
            configFile.delete()
        }
    }

    @Test
    fun `test loadFromSingleFile with empty config file throws exception`() {
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
    fun `test loadFromSingleFile with invalid config format throws exception`() {
        val configFile = createTempTomlFile("""
            This is not valid TOML
            [[[
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
    fun `test loadFromSeparateFiles with valid files`() {
        val systemConfigFile = createTempTomlFile("""
            [output]
            resultsDir = "separate-test-runs"
            
            [jvm]
            maxHeapSize = "4g"
            gcAlgorithm = "ZGC"
        """.trimIndent())

        val experimentConfigFile = createTempTomlFile("""
            mode = "batch"
            
            [batch]
            cloudletCount = 150
            runs = 2
        """.trimIndent())
        
        try {
            val configs = ConfigurationManager.loadFromSeparateFiles(
                systemConfigPath = systemConfigFile.absolutePath,
                experimentConfigPath = experimentConfigFile.absolutePath
            )
            
            assertEquals("separate-test-runs", configs.systemConfig.output.resultsDir)
            assertEquals("ZGC", configs.systemConfig.jvm.gcAlgorithm)
            assertEquals(150, configs.experimentConfig.batch.cloudletCount)
            assertEquals(2, configs.experimentConfig.batch.runs)
        } finally {
            systemConfigFile.delete()
            experimentConfigFile.delete()
        }
    }

    @Test
    fun `test loadFromSeparateFiles with null system config uses defaults`() {
        val experimentConfigFile = createTempTomlFile("""
            mode = "realtime"
            
            [realtime]
            cloudletCount = 300
            runs = 1
        """.trimIndent())
        
        try {
            val configs = ConfigurationManager.loadFromSeparateFiles(
                systemConfigPath = null,
                experimentConfigPath = experimentConfigFile.absolutePath
            )
            
            // Should use default system config
            assertNotNull(configs.systemConfig)
            assertEquals(300, configs.experimentConfig.realtime.cloudletCount)
        } finally {
            experimentConfigFile.delete()
        }
    }

    @Test
    fun `test loadFromSingleFile with blank path throws exception`() {
        assertThrows<IllegalArgumentException> {
            ConfigurationManager.loadFromSingleFile("   ")
        }
    }

    @Test
    fun `test loadFromSingleFile with non-existent file throws exception`() {
        assertThrows<IllegalArgumentException> {
            ConfigurationManager.loadFromSingleFile("non-existent-file.toml")
        }
    }

    private fun createTempTomlFile(content: String): File {
        val tempFile = Files.createTempFile("test-config-", ".toml").toFile()
        Files.write(tempFile.toPath(), content.toByteArray(), StandardOpenOption.WRITE)
        return tempFile
    }
}