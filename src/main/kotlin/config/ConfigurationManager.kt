package config

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.TomlOutputConfig
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.serializer
import util.Logger
import java.io.File

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
            // 输入验证
            if (configPath.isBlank()) {
                throw IllegalArgumentException("配置文件路径不能为空")
            }

            val file = File(configPath)
            if (!file.exists()) {
                throw IllegalArgumentException("配置文件不存在: $configPath")
            }

            if (!file.canRead()) {
                throw IllegalArgumentException("配置文件无法读取: $configPath")
            }

            if (file.length() == 0L) {
                throw IllegalArgumentException("配置文件为空: $configPath")
            }

            val content =
                try {
                    file.readText()
                } catch (e: Exception) {
                    throw IllegalArgumentException("无法读取配置文件内容: ${e.message}", e)
                }

            val rootConfig = parseRootConfig(content, configPath)
            ExperimentConfig.validateDynamicTomlSections(content, rootConfig.toExperimentTomlConfig())
            val systemConfig = SystemConfig.fromTomlConfig(rootConfig.toSystemTomlConfig())
            val experimentConfig = ExperimentConfig.fromTomlConfig(rootConfig.toExperimentTomlConfig())

            return LoadedConfigs(systemConfig, experimentConfig)
        }

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
            } catch (e: Exception) {
                Logger.debug("解析统一 TOML 配置失败: ${e.message}")
                throw IllegalArgumentException("配置文件格式错误，无法解析 TOML，可能包含未知字段: $configPath: ${e.message}", e)
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
            if (experimentConfigPath.isBlank()) {
                throw IllegalArgumentException("实验配置文件路径不能为空")
            }

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
