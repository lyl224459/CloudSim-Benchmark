package config

data class SystemConfig(
    val output: OutputConfig = OutputConfig(),
    val logging: LoggingConfig = LoggingConfig(),
    val experiment: SystemExperimentConfig = SystemExperimentConfig(),
    val jvm: JvmConfig = JvmConfig(),
) {
    companion object {
        fun load(configPath: String?): SystemConfig = SystemConfigLoader.load(configPath)

        fun createDefault(): SystemConfig = SystemConfig()

        fun validate(config: SystemConfig) = SystemConfigValidator.validate(config)

        internal fun fromTomlConfig(tomlConfig: SystemTomlConfig): SystemConfig {
            val mapper = SystemConfigMapper
            return mapper.fromTomlConfig(tomlConfig)
        }
    }
}
