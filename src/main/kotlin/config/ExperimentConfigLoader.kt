package config

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.TomlOutputConfig
import kotlinx.serialization.SerializationException
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.serializer
import java.io.File
import java.io.IOException

internal object ExperimentConfigLoader {
    fun load(
        configPath: String,
        requireProfiles: Boolean,
    ): ExperimentConfig {
        val config = loadWithoutValidation(configPath, requireProfiles)
        ExperimentConfigValidator.validate(config, requireProfiles)
        return config
    }

    fun fromTomlConfig(
        tomlConfig: ExperimentTomlConfig,
        requireProfiles: Boolean = true,
    ): ExperimentConfig {
        validateProfileRoot(tomlConfig, requireProfiles)
        return ExperimentConfig(
            defaultProfile = tomlConfig.defaultProfile,
            profiles = tomlConfig.profiles,
            randomSeed = tomlConfig.random?.seed ?: 0L,
            optimizer = mergeOptimizerConfig(OptimizerConfig(), tomlConfig.optimizer),
            algorithmConfigs =
                tomlConfig.algorithms.mapKeys { (name, _) ->
                    ExperimentConfigParsers.normalizeAlgorithmName(name)
                },
            presets = tomlConfig.presets,
        )
    }

    fun validateDynamicTomlSections(
        content: String,
        tomlConfig: ExperimentTomlConfig,
    ) = ExperimentDynamicTomlValidator.validate(content, tomlConfig)

    private fun loadWithoutValidation(
        configPath: String,
        requireProfiles: Boolean,
    ): ExperimentConfig {
        val file = File(configPath)
        require(file.exists()) { "配置文件不存在: $configPath" }

        return try {
            val content = file.readText()
            val tomlConfig = strictToml().decodeFromString(serializer<ExperimentTomlConfig>(), content)
            validateDynamicTomlSections(content, tomlConfig)
            fromTomlConfig(tomlConfig, requireProfiles)
        } catch (exception: IllegalArgumentException) {
            logAndRethrow(exception)
        } catch (exception: IllegalStateException) {
            logAndRethrow(exception)
        } catch (exception: SecurityException) {
            logAndRethrow(exception)
        } catch (exception: SerializationException) {
            logAndRethrow(exception)
        } catch (exception: IOException) {
            logAndRethrow(exception)
        }
    }

    private fun validateProfileRoot(
        tomlConfig: ExperimentTomlConfig,
        requireProfiles: Boolean,
    ) {
        if (tomlConfig.profiles.isNotEmpty()) {
            return
        }
        require(!tomlConfig.usesLegacyTopLevelSchema()) {
            "旧顶层实验 schema 已废弃，请迁移到 [profiles.NAME]"
        }
        require(!requireProfiles) {
            "未找到 profiles 配置，请至少定义一个 [profiles.NAME]"
        }
    }

    private fun mergeOptimizerConfig(
        base: OptimizerConfig,
        toml: TomlOptimizerConfig?,
    ): OptimizerConfig {
        if (toml == null) return base
        return base.copy(
            population = toml.population,
            maxIter = toml.maxIter,
        )
    }

    private fun ExperimentTomlConfig.usesLegacyTopLevelSchema(): Boolean =
        mode != null ||
            batch != null ||
            batchMulti != null ||
            realtime != null ||
            realtimeMulti != null

    private fun strictToml(): Toml =
        Toml(
            TomlInputConfig(ignoreUnknownNames = false),
            TomlOutputConfig(),
            EmptySerializersModule(),
        )

    private fun logAndRethrow(exception: Exception): Nothing {
        util.Logger.error("加载实验配置时发生错误: ${exception.message}", exception)
        throw exception
    }
}

private object ExperimentDynamicTomlValidator {
    fun validate(
        content: String,
        tomlConfig: ExperimentTomlConfig,
    ) {
        val toml = strictToml()
        tomlConfig.profiles.keys.forEach { name ->
            toml.decodeDynamicSection<ProfileConfig>(content, "profiles", name)
        }
        tomlConfig.algorithms.keys.forEach { name ->
            toml.decodeDynamicSection<AlgorithmConfig>(content, "algorithms", name)
        }
        tomlConfig.presets.keys.forEach { name ->
            toml.decodeDynamicSection<PresetConfig>(content, "presets", name)
        }
    }

    private inline fun <reified T> Toml.decodeDynamicSection(
        content: String,
        section: String,
        name: String,
    ): T {
        val paths = dynamicSectionPaths(section, name)
        val firstResult = decodeDynamicSectionAtPath<T>(content, paths.first())
        firstResult.getOrNull()?.let { return it }
        paths.drop(1).forEach { path ->
            decodeDynamicSectionAtPath<T>(content, path).getOrNull()?.let { return it }
        }
        val firstError = firstResult.exceptionOrNull()
        throw IllegalArgumentException("$section.$name 包含未知字段或无法解析: ${firstError?.message}", firstError)
    }

    private inline fun <reified T> Toml.decodeDynamicSectionAtPath(
        content: String,
        path: String,
    ): Result<T> =
        runCatching {
            partiallyDecodeFromString(
                serializer<T>(),
                content,
                path,
                TomlInputConfig(ignoreUnknownNames = false),
            )
        }

    private fun dynamicSectionPaths(
        section: String,
        name: String,
    ): List<String> =
        if (name.any { !it.isLetterOrDigit() && it != '_' && it != '-' }) {
            listOf("$section.$name", "$section.\"${name.replace("\"", "\\\"")}\"")
        } else {
            listOf("$section.$name")
        }

    private fun strictToml(): Toml =
        Toml(
            TomlInputConfig(ignoreUnknownNames = false),
            TomlOutputConfig(),
            EmptySerializersModule(),
        )
}
