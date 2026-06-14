package config

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.TomlOutputConfig
import kotlinx.serialization.SerializationException
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.serializer
import util.Logger
import java.io.File
import java.io.IOException

/**
 * 配置管理器
 * 统一管理系统的配置加载和验证
 */
class ConfigurationManager {
    /**
     * 加载系统配置和实验配置
     */
    data class LoadedConfigs(
        val systemConfig: SystemConfig,
        val experimentConfig: ExperimentConfig,
    )

    companion object {
        /**
         * 从单一配置文件加载系统配置和实验配置
         * 配置文件可以只包含实验配置，也可以包含系统配置和实验配置两部分
         *
         * @param configPath 配置文件路径
         * @return 包含系统配置和实验配置的数据对象
         * @throws IllegalArgumentException 当配置文件不存在或格式无效时抛出
         */
        fun loadFromSingleFile(configPath: String): LoadedConfigs {
            val file = validatedConfigFile(configPath)
            val content = readConfigFile(file)

            val rootConfig = parseRootConfig(content, configPath)
            ExperimentConfig.validateDynamicTomlSections(content, rootConfig.toExperimentTomlConfig())
            val systemConfig = SystemConfig.fromTomlConfig(rootConfig.toSystemTomlConfig())
            val experimentConfig = ExperimentConfig.fromTomlConfig(rootConfig.toExperimentTomlConfig())

            return LoadedConfigs(systemConfig, experimentConfig)
        }

        private fun validatedConfigFile(configPath: String): File {
            require(configPath.isNotBlank()) { "配置文件路径不能为空" }
            return File(configPath).also { file ->
                require(file.exists()) { "配置文件不存在: $configPath" }
                require(file.canRead()) { "配置文件无法读取: $configPath" }
                require(file.length() != 0L) { "配置文件为空: $configPath" }
            }
        }

        private fun readConfigFile(file: File): String =
            try {
                file.readText()
            } catch (e: IOException) {
                throw configReadError(e)
            } catch (e: SecurityException) {
                throw configReadError(e)
            }

        private fun configReadError(cause: Exception): IllegalArgumentException =
            IllegalArgumentException(
                "无法读取配置文件内容: ${cause.message}",
                cause,
            )

        private fun parseRootConfig(
            content: String,
            configPath: String,
        ): CloudSimBenchmarkTomlConfig =
            try {
                Toml(
                    TomlInputConfig(ignoreUnknownNames = false),
                    TomlOutputConfig(),
                    EmptySerializersModule(),
                ).decodeFromString(serializer<CloudSimBenchmarkTomlConfig>(), content)
            } catch (e: SerializationException) {
                throwTomlParseError(configPath, e)
            } catch (e: IllegalArgumentException) {
                throwTomlParseError(configPath, e)
            }

        private fun throwTomlParseError(
            configPath: String,
            cause: RuntimeException,
        ): Nothing {
            Logger.debug("解析统一 TOML 配置失败: ${cause.message}")
            throw IllegalArgumentException(
                "配置文件格式错误，无法解析 TOML，可能包含未知字段: $configPath: ${cause.message}",
                cause,
            )
        }

        /**
         * 从两个独立的配置文件加载配置
         *
         * @param systemConfigPath 系统配置文件路径，可选
         * @param experimentConfigPath 实验配置文件路径，必需
         * @return 包含系统配置和实验配置的数据对象
         * @throws IllegalArgumentException 当实验配置文件不存在或格式无效时抛出
         */
        fun loadFromSeparateFiles(
            systemConfigPath: String? = null,
            experimentConfigPath: String,
        ): LoadedConfigs {
            // 验证实验配置路径
            require(experimentConfigPath.isNotBlank()) { "实验配置文件路径不能为空" }

            val systemConfig =
                if (!systemConfigPath.isNullOrEmpty()) {
                    SystemConfig.load(systemConfigPath)
                } else {
                    SystemConfig.createDefault()
                }

            val experimentConfig = ExperimentConfig.load(experimentConfigPath)

            return LoadedConfigs(
                systemConfig = systemConfig,
                experimentConfig = experimentConfig,
            )
        }
    }
}
