package config

import com.akuleshov7.ktoml.Toml
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
        val experimentConfig: ExperimentConfig
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
            
            val content = try {
                file.readText()
            } catch (e: Exception) {
                throw IllegalArgumentException("无法读取配置文件内容: ${e.message}", e)
            }
            
            val (systemContent, experimentContent) = splitMixedConfig(content)
            val systemConfig = parseSystemContent(systemContent) ?: SystemConfig.createDefault()
            val experimentConfig = parseExperimentContent(experimentContent)
                ?: throw IllegalArgumentException("配置文件格式无效，无法解析实验配置: $configPath")

            return LoadedConfigs(systemConfig, experimentConfig)
        }

        private fun splitMixedConfig(content: String): Pair<String, String> {
            val systemSections = setOf("output", "output.csv", "output.logging", "logging", "experiment", "jvm")
            val systemLines = mutableListOf<String>()
            val experimentLines = mutableListOf<String>()
            var currentTarget = experimentLines

            for (line in content.lineSequence()) {
                val trimmed = line.trim()
                val sectionName = if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    trimmed.trim('[', ']').trim()
                } else {
                    null
                }

                if (sectionName != null) {
                    currentTarget = when {
                        sectionName in systemSections -> systemLines
                        else -> experimentLines
                    }
                }
                currentTarget.add(line)
            }

            return systemLines.joinToString("\n") to experimentLines.joinToString("\n")
        }

        private fun parseSystemContent(content: String): SystemConfig? {
            if (content.isBlank()) return SystemConfig.createDefault()
            val tempFile = File.createTempFile("temp_system_config", ".toml")
            tempFile.writeText(content)
            return try {
                SystemConfig.load(tempFile.absolutePath)
            } catch (e: Exception) {
                Logger.debug("解析系统配置失败: ${e.message}")
                null
            } finally {
                tempFile.delete()
            }
        }

        private fun parseExperimentContent(content: String): ExperimentConfig? {
            if (content.isBlank()) {
                return null
            }

            val tempFile = File.createTempFile("temp_exp_config", ".toml")
            tempFile.writeText(content)
            return try {
                ExperimentConfig.load(tempFile.absolutePath)
            } finally {
                tempFile.delete()
            }
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
            experimentConfigPath: String
        ): LoadedConfigs {
            // 验证实验配置路径
            if (experimentConfigPath.isBlank()) {
                throw IllegalArgumentException("实验配置文件路径不能为空")
            }
            
            val systemConfig = if (!systemConfigPath.isNullOrEmpty()) {
                SystemConfig.load(systemConfigPath)
            } else {
                SystemConfig.createDefault()
            }
            
            val experimentConfig = ExperimentConfig.load(experimentConfigPath)
            
            return LoadedConfigs(
                systemConfig = systemConfig,
                experimentConfig = experimentConfig
            )
        }
    }
}
