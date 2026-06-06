package cli

import config.ExperimentConfig
import config.ExperimentMode
import config.ProfileConfig

internal object RunModeResolver {
    fun resolve(
        command: CliParser.RunCommand,
        profile: ProfileConfig?,
        config: ExperimentConfig,
    ): String {
        val commandMode = command.mode?.let(::normalizeMode)
        if (commandMode != null) {
            requireSupported(commandMode)
            return commandMode
        }

        val profileMode = profile?.mode?.takeIf { it.isNotBlank() }?.let(::normalizeMode)
        if (profileMode != null) {
            requireSupported(profileMode)
            return profileMode
        }

        require(config.profiles.isEmpty()) {
            "run 命令缺少 --mode，且所选 profile 未定义 mode"
        }
        throw IllegalArgumentException("run 命令需要 --mode batch|realtime|batch-multi|realtime-multi，或使用包含 profiles 的配置文件")
    }

    fun isBatch(mode: String): Boolean = mode.startsWith("batch")

    fun isMulti(mode: String): Boolean = mode.endsWith("multi")

    fun toExperimentMode(mode: String): ExperimentMode = ExperimentMode.valueOf(mode.uppercase().replace("-", "_"))

    private fun requireSupported(mode: String) {
        require(mode in supportedModes()) {
            "无效运行模式: $mode。可用模式: ${supportedModes().joinToString(", ")}"
        }
    }
}
