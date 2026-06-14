package cli

import config.ExperimentConfig
import config.ProfileConfig

internal data class SelectedProfile(
    val name: String,
    val profile: ProfileConfig,
)

internal object RunProfileResolver {
    fun select(
        config: ExperimentConfig,
        requestedProfile: String?,
        configFile: String?,
    ): SelectedProfile? {
        if (config.profiles.isEmpty()) {
            require(requestedProfile == null) {
                "配置文件未定义 profiles，不能使用 --profile $requestedProfile"
            }
            return null
        }

        val availableProfiles =
            config.profiles.keys
                .sorted()
                .joinToString(", ")
        val profileName =
            requestedProfile?.takeIf { it.isNotBlank() }
                ?: config.defaultProfile?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException(missingProfileSelectionMessage(configFile, availableProfiles))

        val profileEntry =
            config.profiles.entries.firstOrNull { (name, _) ->
                name.equals(profileName, ignoreCase = true) || hasSameNormalizedName(name, profileName)
            } ?: throw IllegalArgumentException("未知 profile: $profileName。可用 profiles: $availableProfiles")

        return SelectedProfile(profileEntry.key, profileEntry.value)
    }

    private fun hasSameNormalizedName(
        left: String,
        right: String,
    ): Boolean = ExperimentConfig.normalizeAlgorithmName(left) == ExperimentConfig.normalizeAlgorithmName(right)

    private fun missingProfileSelectionMessage(
        configFile: String?,
        availableProfiles: String,
    ): String =
        "配置文件 ${configFile ?: "(未指定)"} 包含 profiles，" +
            "但未指定 --profile，也没有 defaultProfile。可用 profiles: $availableProfiles"
}
