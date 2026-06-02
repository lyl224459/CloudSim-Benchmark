package config

import com.akuleshov7.ktoml.Toml
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer
import util.Logger
import java.io.File
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Paths

internal object SystemConfigLoader {
    fun load(configPath: String?): SystemConfig {
        val config = loadInternal(configPath)
        SystemConfigValidator.validate(config)
        return config
    }

    private fun loadInternal(configPath: String?): SystemConfig =
        try {
            configPath
                ?.takeIf(String::isNotEmpty)
                ?.let(::loadFromFile)
                ?: loadFromEnv()
                ?: SystemConfig()
        } catch (exception: IllegalArgumentException) {
            logLoadFailure(exception)
            throw exception
        } catch (exception: ConfigValidationException) {
            logLoadFailure(exception)
            throw exception
        }

    private fun loadFromFile(configPath: String): SystemConfig {
        validateFilePath(configPath)
        val file = File(configPath)
        require(file.exists()) { "配置文件不存在: $configPath" }
        require(file.canRead()) { "配置文件无法读取: $configPath" }
        return SystemConfigMapper.fromTomlConfig(readTomlConfig(file))
    }

    private fun readTomlConfig(file: File): SystemTomlConfig = decodeTomlConfig(readTomlContent(file))

    private fun readTomlContent(file: File): String =
        try {
            file.readText()
        } catch (exception: IOException) {
            throw IllegalArgumentException("无法读取配置文件内容: ${exception.message}", exception)
        }

    private fun decodeTomlConfig(tomlContent: String): SystemTomlConfig =
        try {
            Toml.decodeFromString(serializer<SystemTomlConfig>(), tomlContent)
        } catch (exception: SerializationException) {
            throw IllegalArgumentException("配置文件格式错误，无法解析TOML: ${exception.message}", exception)
        } catch (exception: IllegalArgumentException) {
            throw IllegalArgumentException("配置文件格式错误，无法解析TOML: ${exception.message}", exception)
        }

    private fun validateFilePath(configPath: String) {
        try {
            val path = Paths.get(configPath).normalize()
            val canonicalPath = path.toAbsolutePath().toString()
            require(!hasUnsafePathPattern(canonicalPath)) {
                "配置文件路径包含非法字符，可能存在路径遍历风险: $configPath"
            }

            System.getProperty("user.dir")?.let { projectRoot ->
                path.toAbsolutePath().startsWith(Paths.get(projectRoot))
            }
        } catch (exception: InvalidPathException) {
            throw IllegalArgumentException("配置文件路径格式无效: $configPath", exception)
        }
    }

    private fun hasUnsafePathPattern(canonicalPath: String): Boolean =
        canonicalPath.contains("..") ||
            canonicalPath.contains("./") ||
            canonicalPath.contains("../")

    private fun loadFromEnv(): SystemConfig? = SystemEnvConfigLoader.load()

    private fun logLoadFailure(exception: RuntimeException) {
        Logger.error("加载系统配置时发生错误: ${exception.message}", exception)
    }
}
