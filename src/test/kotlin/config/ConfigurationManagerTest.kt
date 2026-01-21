package config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption

class ConfigurationManagerTest {

    @Test
    fun `test loadFromSingleFile with valid mixed config`() {
        val configFile = createTempTomlFile("""
            mode = "batch"
            
            [output]
            resultsDir = "runs"
            
            [logging]
            level = "INFO"
            
            [batch]
            cloudletCount = 100
            population = 30
            runs = 1
        """.trimIndent())
        
        try {
            val configs = ConfigurationManager.loadFromSingleFile(configFile.absolutePath)
            
            assertNotNull(configs.systemConfig)
            assertNotNull(configs.experimentConfig)
            assertEquals("runs", configs.systemConfig.output.resultsDir)
            assertEquals("INFO", configs.systemConfig.logging.level)
            assertEquals(100, configs.experimentConfig.batch.cloudletCount)
        } finally {
            configFile.delete()
        }
    }

    @Test
    fun `test loadFromSingleFile with valid experiment-only config`() {
        val configFile = createTempTomlFile("""
            mode = "batch"
            
            [batch]
            cloudletCount = 200
            population = 50
            runs = 2
        """.trimIndent())
        
        try {
            val configs = ConfigurationManager.loadFromSingleFile(configFile.absolutePath)
            
            assertNotNull(configs.systemConfig)
            assertNotNull(configs.experimentConfig)
            assertEquals(200, configs.experimentConfig.batch.cloudletCount)
            assertEquals(50, configs.experimentConfig.batch.population)
            assertEquals(2, configs.experimentConfig.batch.runs)
        } finally {
            configFile.delete()
        }
    }

    @Test
    fun `test loadFromSingleFile with non-existent file throws exception`() {
        assertThrows<IllegalArgumentException> {
            ConfigurationManager.loadFromSingleFile("non-existent-file.toml")
        }
    }

    @Test
    fun `test loadFromSingleFile with empty file path throws exception`() {
        assertThrows<IllegalArgumentException> {
            ConfigurationManager.loadFromSingleFile("")
        }
    }

    @Test
    fun `test loadFromSingleFile with blank file path throws exception`() {
        assertThrows<IllegalArgumentException> {
            ConfigurationManager.loadFromSingleFile("   ")
        }
    }

    @Test
    fun `test loadFromSingleFile with empty file throws exception`() {
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
    fun `test loadFromSingleFile with invalid toml format throws exception`() {
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

    @Test
    fun `test loadFromSeparateFiles with valid files`() {
        val systemConfigFile = createTempTomlFile("""
            [output]
            resultsDir = "custom-runs"
            
            [logging]
            level = "DEBUG"
        """.trimIndent())

        val experimentConfigFile = createTempTomlFile("""
            mode = "batch"
            
            [batch]
            cloudletCount = 150
            runs = 3
        """.trimIndent())
        
        try {
            val configs = ConfigurationManager.loadFromSeparateFiles(
                systemConfigPath = systemConfigFile.absolutePath,
                experimentConfigPath = experimentConfigFile.absolutePath
            )
            
            assertEquals("custom-runs", configs.systemConfig.output.resultsDir)
            assertEquals("DEBUG", configs.systemConfig.logging.level)
            assertEquals(150, configs.experimentConfig.batch.cloudletCount)
            assertEquals(3, configs.experimentConfig.batch.runs)
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
    fun `test loadFromSeparateFiles with empty experiment config path throws exception`() {
        assertThrows<IllegalArgumentException> {
            ConfigurationManager.loadFromSeparateFiles(
                systemConfigPath = null,
                experimentConfigPath = ""
            )
        }
    }

    private fun createTempTomlFile(content: String): File {
        val tempFile = Files.createTempFile("test-config-", ".toml").toFile()
        Files.write(tempFile.toPath(), content.toByteArray(), StandardOpenOption.WRITE)
        return tempFile
    }
}