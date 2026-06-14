package config

import cli.CliParser
import cli.RunResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class DocumentationDriftTest {
    @Test
    fun `readme toml snippets are standalone parseable configs`() {
        val snippets = markdownCodeBlocks(File("README.md").readText(), "toml")

        assertThat(snippets).isNotEmpty
        snippets.forEachIndexed { index, snippet ->
            val configFile = Files.createTempFile("readme-snippet-$index", ".toml").toFile()
            configFile.writeText(snippet)

            ConfigurationManager.loadFromSingleFile(configFile.absolutePath)
        }
    }

    @Test
    fun `all config toml files parse and profile configs resolve dry runs`() {
        val configFiles =
            File("configs")
                .walkTopDown()
                .filter { it.isFile && it.extension == "toml" }
                .sortedBy { it.invariantSeparatorsPath }
                .toList()

        assertThat(configFiles).isNotEmpty
        configFiles.forEach { configFile ->
            if (configFile.readText().contains("[profiles.")) {
                val loaded = ConfigurationManager.loadFromSingleFile(configFile.path)
                loaded.experimentConfig.profiles.keys.forEach { profile ->
                    val command =
                        CliParser(
                            arrayOf(
                                "run",
                                "--config",
                                configFile.path,
                                "--profile",
                                profile,
                                "--dry-run",
                            ),
                        ).parse() as CliParser.RunCommand
                    RunResolver.resolve(command)
                }
            } else {
                val libraryConfig = ExperimentConfig.loadLibrary(configFile.path)
                assertThat(libraryConfig.algorithmConfigs + libraryConfig.presets).isNotEmpty
            }
        }
    }

    @Test
    fun `release readiness documents real Gradle verification tasks`() {
        val build = File("build.gradle.kts").readText()
        val docs = File("docs/release-readiness.md").readText()
        val expectedTasks =
            listOf(
                "fullCheck",
                "benchmarkPerformanceSmoke",
                "benchmarkPerformanceTrend",
                "verifyReleasePackage",
                "containerImageSmoke",
                "verifyReleaseManifest",
                "verifyCloudSimPlusLock",
            )

        expectedTasks.forEach { taskName ->
            assertThat(build).contains(taskName)
            assertThat(docs).contains(taskName)
        }
    }

    @Test
    fun `detekt baseline remains removed`() {
        val docs = File("docs/release-readiness.md").readText()

        assertThat(File("detekt-baseline.xml")).doesNotExist()
        assertThat(File("build.gradle.kts").readText()).doesNotContain("baseline = file(")
        assertThat(docs).contains("Detekt baseline 已清零")
    }

    @Test
    fun `legacy compatibility wrappers have no remaining source references`() {
        val references =
            File("src")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name != "DocumentationDriftTest.kt" }
                .flatMap { file ->
                    file
                        .readLines()
                        .mapIndexedNotNull { index, line ->
                            if (line.contains("ResultsManager") || line.contains("Constants")) {
                                "${file.invariantSeparatorsPath}:${index + 1}:$line"
                            } else {
                                null
                            }
                        }
                }.toList()

        assertThat(references).isEmpty()
    }

    private fun markdownCodeBlocks(
        markdown: String,
        language: String,
    ): List<String> =
        Regex("```$language\\R([\\s\\S]*?)\\R```")
            .findAll(markdown)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()
}
