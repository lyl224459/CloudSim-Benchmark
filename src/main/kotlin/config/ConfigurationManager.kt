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
            
            // 首先尝试解析为混合配置（同时包含系统配置和实验配置）
            val mixedConfigResult = tryParseMixedConfig(content)
            if (mixedConfigResult != null) {
                return mixedConfigResult
            }
            
            // 如果不是混合配置，尝试作为纯实验配置加载
            val experimentConfig = tryParseExperimentConfig(content)
            if (experimentConfig != null) {
                return LoadedConfigs(
                    systemConfig = SystemConfig.createDefault(),
                    experimentConfig = experimentConfig
                )
            }
            
            throw IllegalArgumentException("配置文件格式无效，既不是有效的实验配置也不是混合配置: $configPath")
        }
        
        /**
         * 尝试解析混合配置（同时包含系统配置和实验配置的文件）
         *
         * @param content 配置文件内容
         * @return 解析成功的配置对象，如果解析失败则返回null
         */
        private fun tryParseMixedConfig(content: String): LoadedConfigs? {
            return try {
                // 验证内容是否为空
                if (content.isBlank()) {
                    Logger.debug("配置内容为空，无法解析为混合配置")
                    return null
                }
                
                // 尝试解析整个文件为TOML格式，检查是否存在系统配置部分
                // 我们只是简单地检查文本内容，而不实际解析整个文件
                val hasSystemPart = content.contains("[output]") || 
                                  content.contains("[logging]") || 
                                  content.contains("[experiment]") ||
                                  content.contains("[jvm]")
                
                if (hasSystemPart) {
                    // 创建临时文件分别加载系统配置和实验配置
                    val tempFile = File.createTempFile("temp_mixed_config", ".toml")
                    tempFile.writeText(content)
                    try {
                        val systemConfig = SystemConfig.load(tempFile.absolutePath)
                        
                        // 为实验配置创建单独的临时文件
                        val expTempFile = File.createTempFile("temp_exp_config", ".toml")
                        expTempFile.writeText(content)
                        try {
                            val experimentConfig = ExperimentConfig.load(expTempFile.absolutePath)
                            
                            LoadedConfigs(
                                systemConfig = systemConfig,
                                experimentConfig = experimentConfig
                            )
                        } catch (e: Exception) {
                            Logger.debug("解析实验配置失败: ${e.message}")
                            null
                        } finally {
                            expTempFile.delete()
                        }
                    } catch (e: Exception) {
                        Logger.debug("解析系统配置失败: ${e.message}")
                        null
                    } finally {
                        tempFile.delete()
                    }
                } else {
                    // 如果没有系统配置部分，尝试解析为纯实验配置
                    val expTempFile = File.createTempFile("temp_exp_config", ".toml")
                    expTempFile.writeText(content)
                    try {
                        val experimentConfig = ExperimentConfig.load(expTempFile.absolutePath)
                        
                        // 返回默认系统配置和加载的实验配置
                        LoadedConfigs(
                            systemConfig = SystemConfig.createDefault(),
                            experimentConfig = experimentConfig
                        )
                    } catch (e: Exception) {
                        Logger.debug("解析实验配置失败: ${e.message}")
                        null
                    } finally {
                        expTempFile.delete()
                    }
                }
            } catch (e: Exception) {
                Logger.debug("解析混合配置失败: ${e.message}")
                null
            }
        }
        
        /**
         * 尝试解析为纯实验配置
         *
         * @param content 配置文件内容
         * @return 解析成功的实验配置对象，如果解析失败则返回null
         */
        private fun tryParseExperimentConfig(content: String): ExperimentConfig? {
            return try {
                // 验证内容是否为空
                if (content.isBlank()) {
                    Logger.debug("配置内容为空，无法解析为实验配置")
                    return null
                }
                
                // 创建临时文件进行实验配置解析
                val tempFile = File.createTempFile("temp_exp_config", ".toml")
                tempFile.writeText(content)
                try {
                    ExperimentConfig.load(tempFile.absolutePath)
                } finally {
                    tempFile.delete()
                }
            } catch (e: Exception) {
                Logger.debug("解析实验配置失败: ${e.message}")
                null
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