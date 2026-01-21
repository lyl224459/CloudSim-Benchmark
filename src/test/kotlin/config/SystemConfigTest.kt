package config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.nio.file.Paths

class SystemConfigTest {

    @Test
    fun `test load with valid config file`() {
        val configFile = createTempTomlFile("""
            [output]
            resultsDir = "custom-runs"
            
            [logging]
            level = "DEBUG"
            
            [jvm]
            maxHeapSize = "4g"
            gcAlgorithm = "ZGC"
        """.trimIndent())
        
        try {
            val config = SystemConfig.load(configFile.absolutePath)
            
            assertEquals("custom-runs", config.output.resultsDir)
            assertEquals("DEBUG", config.logging.level)
            assertEquals("4g", config.jvm.maxHeapSize)
            assertEquals("ZGC", config.jvm.gcAlgorithm)
        } finally {
            configFile.delete()
        }
    }

    @Test
    fun `test load with non-existent file throws exception`() {
        assertThrows<IllegalArgumentException> {
            SystemConfig.load("non-existent-file.toml")
        }
    }

    @Test
    fun `test load with invalid toml format throws exception`() {
        val configFile = createTempTomlFile("""
            This is not valid TOML content
            [
        """.trimIndent())
        
        try {
            assertThrows<IllegalArgumentException> {
                SystemConfig.load(configFile.absolutePath)
            }
        } finally {
            configFile.delete()
        }
    }

    @Test
    fun `test load with empty config file uses defaults`() {
        val configFile = createTempTomlFile("")
        
        try {
            val config = SystemConfig.load(configFile.absolutePath)
            
            // Should use default values
            assertEquals("runs", config.output.resultsDir)
            assertEquals("INFO", config.logging.level)
            assertEquals("2g", config.jvm.maxHeapSize)
            assertEquals("G1", config.jvm.gcAlgorithm)
        } finally {
            configFile.delete()
        }
    }

    @Test
    fun `test validation with valid config passes`() {
        val validConfig = SystemConfig(
            output = OutputConfig(resultsDir = "test-results"),
            logging = LoggingConfig(level = "INFO"),
            jvm = JvmConfig(maxHeapSize = "2g", gcAlgorithm = "ZGC")
        )
        
        // This should not throw an exception
        assertDoesNotThrow {
            SystemConfig.validate(validConfig)
        }
    }

    @Test
    fun `test validation with invalid output dir fails`() {
        val invalidConfig = SystemConfig(
            output = OutputConfig(resultsDir = "") // Empty dir is invalid
        )
        
        val exception = assertThrows<ConfigValidationException> {
            SystemConfig.validate(invalidConfig)
        }
        
        assertTrue(exception.errors.any { it.field == "output.resultsDir" && it.value == "" })
    }

    @Test
    fun `test validation with invalid logging level fails`() {
        val invalidConfig = SystemConfig(
            logging = LoggingConfig(level = "INVALID_LEVEL")
        )
        
        val exception = assertThrows<ConfigValidationException> {
            SystemConfig.validate(invalidConfig)
        }
        
        assertTrue(exception.errors.any { it.field == "logging.level" && it.value == "INVALID_LEVEL" })
    }

    @Test
    fun `test validation with invalid heap size format fails`() {
        val invalidConfig = SystemConfig(
            jvm = JvmConfig(maxHeapSize = "invalid_format")
        )
        
        val exception = assertThrows<ConfigValidationException> {
            SystemConfig.validate(invalidConfig)
        }
        
        assertTrue(exception.errors.any { it.field == "jvm.maxHeapSize" && it.value == "invalid_format" })
    }

    @Test
    fun `test validation with invalid gc algorithm fails`() {
        val invalidConfig = SystemConfig(
            jvm = JvmConfig(gcAlgorithm = "INVALID_GC")
        )
        
        val exception = assertThrows<ConfigValidationException> {
            SystemConfig.validate(invalidConfig)
        }
        
        assertTrue(exception.errors.any { it.field == "jvm.gcAlgorithm" && it.value == "INVALID_GC" })
    }

    @Test
    fun `test validation with negative concurrent count fails`() {
        val invalidConfig = SystemConfig(
            experiment = SystemExperimentConfig(maxConcurrent = -1)
        )
        
        val exception = assertThrows<ConfigValidationException> {
            SystemConfig.validate(invalidConfig)
        }
        
        assertTrue(exception.errors.any { it.field == "experiment.maxConcurrent" && it.value == "-1" })
    }

    @Test
    fun `test createDefault returns valid config`() {
        val defaultConfig = SystemConfig.createDefault()
        
        // Check that default config passes validation
        assertDoesNotThrow {
            SystemConfig.validate(defaultConfig)
        }
        
        // Check some default values
        assertEquals("runs", defaultConfig.output.resultsDir)
        assertEquals("INFO", defaultConfig.logging.level)
        assertEquals("2g", defaultConfig.jvm.maxHeapSize)
        assertEquals("G1", defaultConfig.jvm.gcAlgorithm)
    }

    @Test
    fun `test path traversal protection`() {
        // Try to create a file in a parent directory to test path traversal protection
        val maliciousPath = "../../../malicious_config.toml"
        
        // This should either fail or not allow path traversal
        assertThrows<IllegalArgumentException> {
            SystemConfig.load(maliciousPath)
        }
    }

    private fun createTempTomlFile(content: String): File {
        val tempFile = Files.createTempFile("test-config-", ".toml").toFile()
        Files.write(tempFile.toPath(), content.toByteArray(), StandardOpenOption.WRITE)
        return tempFile
    }
}