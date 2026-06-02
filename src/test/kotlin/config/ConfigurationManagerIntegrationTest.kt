package config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption

class ConfigurationManagerIntegrationTest {
    @Test
    fun `load profiles only config successfully`() {
        val configFile =
            createTempTomlFile(
                """
                defaultProfile = "batch_profile"

                [profiles.batch_profile]
                mode = "batch"
                algorithms = ["RANDOM", "PSO"]
                runs = 1

                [profiles.batch_profile.batch]
                cloudletCount = 100
                population = 30
                maxIter = 50
                """.trimIndent(),
            )

        try {
            val configs = ConfigurationManager.loadFromSingleFile(configFile.absolutePath)

            assertEquals("batch_profile", configs.experimentConfig.defaultProfile)
            assertEquals(
                100,
                configs.experimentConfig.profiles["batch_profile"]
                    ?.batch
                    ?.cloudletCount,
            )
            assertEquals(
                2,
                configs.experimentConfig.profiles["batch_profile"]
                    ?.algorithms
                    ?.size,
            )
        } finally {
            configFile.delete()
        }
    }

    @Test
    fun `load system plus profiles config successfully`() {
        val configFile =
            createTempTomlFile(
                """
                defaultProfile = "realtime_profile"

                [output]
                resultsDir = "integration-test-runs"

                [logging]
                level = "DEBUG"

                [profiles.realtime_profile]
                mode = "realtime"
                algorithms = ["MIN_LOAD"]
                runs = 3

                [profiles.realtime_profile.realtime]
                cloudletCount = 200
                simulationDuration = 1000.0
                """.trimIndent(),
            )

        try {
            val configs = ConfigurationManager.loadFromSingleFile(configFile.absolutePath)

            assertEquals("integration-test-runs", configs.systemConfig.output.resultsDir)
            assertEquals("DEBUG", configs.systemConfig.logging.level)
            assertEquals(
                200,
                configs.experimentConfig.profiles["realtime_profile"]
                    ?.realtime
                    ?.cloudletCount,
            )
            assertEquals(3, configs.experimentConfig.profiles["realtime_profile"]?.runs)
        } finally {
            configFile.delete()
        }
    }

    @Test
    fun `loadFromSingleFile with invalid config throws exception`() {
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
    fun `loadFromSingleFile with blank path throws exception`() {
        assertThrows<IllegalArgumentException> {
            ConfigurationManager.loadFromSingleFile("   ")
        }
    }

    @Test
    fun `loadFromSingleFile with non existent file throws exception`() {
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
