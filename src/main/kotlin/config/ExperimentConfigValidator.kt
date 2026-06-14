package config

import util.Logger
import java.util.Locale
import kotlin.math.abs

private const val BATCH_CLOUDLET_WARNING_LIMIT = 10_000
private const val BATCH_POPULATION_WARNING_LIMIT = 1_000
private const val BATCH_ITERATION_WARNING_LIMIT = 10_000
private const val BATCH_RUN_WARNING_LIMIT = 100
private const val OPTIMIZER_POPULATION_WARNING_LIMIT = 500
private const val OPTIMIZER_ITERATION_WARNING_LIMIT = 5_000
private const val OBJECTIVE_WEIGHT_TARGET = 1.0
private const val OBJECTIVE_WEIGHT_TOLERANCE = 0.01

internal object ExperimentConfigValidator {
    fun validate(
        config: ExperimentConfig,
        requireProfiles: Boolean = false,
    ) {
        val errors =
            buildList {
                BatchConfigValidator.validate(config.batch, this)
                RealtimeConfigValidator.validate(config.realtime, this)
                OptimizerConfigValidator.validate(config.optimizer, this)
                AlgorithmConfigValidator.validate(config.algorithmConfigs, this)
                PresetConfigValidator.validate(config.presets, this)
                ProfileConfigValidator.validate(config, requireProfiles, this)
                RandomConfigValidator.validate(config.randomSeed, this)
                ObjectiveWeightsValidator.validate(config.batch.objectiveWeights, "batch", this)
                ObjectiveWeightsValidator.validate(config.realtime.objectiveWeights, "realtime", this)
            }

        if (errors.isNotEmpty()) {
            val exception = ConfigValidationException("配置验证失败，共发现 ${errors.size} 个错误", errors)
            Logger.error("配置验证失败: ${exception.message}")
            throw exception
        }
        Logger.debug("配置验证通过")
    }
}

private object BatchConfigValidator {
    fun validate(
        batch: BatchConfig,
        errors: MutableList<ValidationError>,
    ) {
        if (batch.cloudletCount <= 0) {
            errors.addError("batch.cloudletCount", batch.cloudletCount, "批处理任务数必须大于0")
        }
        if (batch.cloudletCount > BATCH_CLOUDLET_WARNING_LIMIT) {
            errors.addError(
                "batch.cloudletCount",
                batch.cloudletCount,
                "批处理任务数过大，可能影响性能，建议不超过$BATCH_CLOUDLET_WARNING_LIMIT",
            )
        }
        if (batch.population <= 0) {
            errors.addError("batch.population", batch.population, "批处理种群大小必须大于0")
        }
        if (batch.population > BATCH_POPULATION_WARNING_LIMIT) {
            errors.addError(
                "batch.population",
                batch.population,
                "批处理种群大小过大，可能影响性能，建议不超过$BATCH_POPULATION_WARNING_LIMIT",
            )
        }
        if (batch.maxIter <= 0) {
            errors.addError("batch.maxIter", batch.maxIter, "批处理最大迭代次数必须大于0")
        }
        if (batch.maxIter > BATCH_ITERATION_WARNING_LIMIT) {
            errors.addError(
                "batch.maxIter",
                batch.maxIter,
                "批处理最大迭代次数过大，可能影响性能，建议不超过$BATCH_ITERATION_WARNING_LIMIT",
            )
        }
        if (batch.runs <= 0) {
            errors.addError("batch.runs", batch.runs, "批处理运行次数必须大于0")
        }
        if (batch.runs > BATCH_RUN_WARNING_LIMIT) {
            errors.addError(
                "batch.runs",
                batch.runs,
                "批处理运行次数过多，可能耗时过长，建议不超过$BATCH_RUN_WARNING_LIMIT",
            )
        }
        batch.cloudletCounts.forEachIndexed { index, count ->
            if (count <= 0) {
                errors.addError("batch.cloudletCounts[$index]", count, "批量任务数必须大于0")
            }
        }
    }
}

private object AlgorithmConfigValidator {
    fun validate(
        algorithmConfigs: Map<String, AlgorithmConfig>,
        errors: MutableList<ValidationError>,
    ) {
        algorithmConfigs.forEach { (name, config) ->
            config.population?.let { population ->
                if (population <= 0) {
                    errors +=
                        ValidationError(
                            "algorithms.$name.population",
                            population.toString(),
                            "算法级种群大小必须大于0",
                        )
                }
            }
            config.maxIter?.let { maxIter ->
                if (maxIter <= 0) {
                    errors +=
                        ValidationError(
                            "algorithms.$name.maxIter",
                            maxIter.toString(),
                            "算法级最大迭代次数必须大于0",
                        )
                }
            }
        }
    }
}

private object PresetConfigValidator {
    fun validate(
        presets: Map<String, PresetConfig>,
        errors: MutableList<ValidationError>,
    ) {
        presets.forEach { (name, preset) ->
            if (preset.algorithms.isEmpty()) {
                errors +=
                    ValidationError(
                        "presets.$name.algorithms",
                        "[]",
                        "预设算法列表不能为空",
                    )
            }
        }
    }
}

private object ProfileConfigValidator {
    private val validModes = setOf("batch", "realtime", "batch-multi", "realtime-multi")

    fun validate(
        config: ExperimentConfig,
        requireProfiles: Boolean,
        errors: MutableList<ValidationError>,
    ) {
        if (requireProfiles && config.profiles.isEmpty()) {
            errors +=
                ValidationError(
                    "profiles",
                    "[]",
                    "profiles 配置不能为空",
                )
        }

        config.defaultProfile?.let { defaultProfile ->
            if (config.profiles.isNotEmpty() && defaultProfile !in config.profiles) {
                errors +=
                    ValidationError(
                        "defaultProfile",
                        defaultProfile,
                        "defaultProfile 必须引用已定义的 profile",
                    )
            }
        }

        config.profiles.forEach { (name, profile) ->
            validateProfile(name, profile, errors)
        }
    }

    private fun validateProfile(
        name: String,
        profile: ProfileConfig,
        errors: MutableList<ValidationError>,
    ) {
        if (profile.mode.isBlank()) {
            errors +=
                ValidationError(
                    "profiles.$name.mode",
                    profile.mode,
                    "profile 必须指定 mode",
                )
        } else {
            val normalizedMode = profile.mode.lowercase().replace("_", "-")
            if (normalizedMode !in validModes) {
                errors +=
                    ValidationError(
                        "profiles.$name.mode",
                        profile.mode,
                        "profile.mode 必须是 batch, realtime, batch-multi, realtime-multi 之一",
                    )
            }
        }
        if (profile.algorithms.isNotEmpty() && !profile.preset.isNullOrBlank()) {
            errors +=
                ValidationError(
                    "profiles.$name",
                    profile.algorithms.joinToString(","),
                    "profile 的 algorithms 与 preset 互斥",
                )
        }
        if (profile.runs != null && profile.runs <= 0) {
            errors +=
                ValidationError(
                    "profiles.$name.runs",
                    profile.runs.toString(),
                    "runs 必须大于 0",
                )
        }
        if (profile.tasks.any { it <= 0 }) {
            errors +=
                ValidationError(
                    "profiles.$name.tasks",
                    profile.tasks.joinToString(","),
                    "tasks 必须全部大于 0",
                )
        }
    }
}

private object OptimizerConfigValidator {
    fun validate(
        optimizer: OptimizerConfig,
        errors: MutableList<ValidationError>,
    ) {
        if (optimizer.population <= 0) {
            errors +=
                ValidationError(
                    "optimizer.population",
                    optimizer.population.toString(),
                    "优化算法种群大小必须大于0",
                )
        }
        if (optimizer.population > OPTIMIZER_POPULATION_WARNING_LIMIT) {
            errors +=
                ValidationError(
                    "optimizer.population",
                    optimizer.population.toString(),
                    "优化算法种群大小过大，可能影响性能，建议不超过$OPTIMIZER_POPULATION_WARNING_LIMIT",
                )
        }
        if (optimizer.maxIter <= 0) {
            errors +=
                ValidationError(
                    "optimizer.maxIter",
                    optimizer.maxIter.toString(),
                    "优化算法最大迭代次数必须大于0",
                )
        }
        if (optimizer.maxIter > OPTIMIZER_ITERATION_WARNING_LIMIT) {
            errors +=
                ValidationError(
                    "optimizer.maxIter",
                    optimizer.maxIter.toString(),
                    "优化算法最大迭代次数过大，可能影响性能，建议不超过$OPTIMIZER_ITERATION_WARNING_LIMIT",
                )
        }
    }
}

private object RandomConfigValidator {
    fun validate(
        randomSeed: Long,
        errors: MutableList<ValidationError>,
    ) {
        if (randomSeed == Long.MIN_VALUE) {
            errors +=
                ValidationError(
                    "randomSeed",
                    randomSeed.toString(),
                    "随机种子值无效，请使用其他值",
                )
        }
    }
}

private object ObjectiveWeightsValidator {
    fun validate(
        weights: ObjectiveWeightsConfig,
        context: String,
        errors: MutableList<ValidationError>,
    ) {
        listOf(
            "cost" to weights.cost,
            "totalTime" to weights.totalTime,
            "loadBalance" to weights.loadBalance,
            "makespan" to weights.makespan,
        ).forEach { (name, value) ->
            if (value < 0.0 || value > OBJECTIVE_WEIGHT_TARGET) {
                errors +=
                    ValidationError(
                        "$context.objective.$name",
                        value.toString(),
                        "$context 模式中 $name 权重必须在 [0,1] 范围内",
                    )
            }
        }

        val totalWeight = weights.cost + weights.totalTime + weights.loadBalance + weights.makespan
        if (totalWeight <= 0.0) {
            errors +=
                ValidationError(
                    "$context.objective",
                    totalWeight.toString(),
                    "$context 模式中目标函数权重总和必须大于 0",
                )
        }
        if (abs(totalWeight - OBJECTIVE_WEIGHT_TARGET) > OBJECTIVE_WEIGHT_TOLERANCE) {
            errors +=
                ValidationError(
                    "$context.objective",
                    totalWeight.toString(),
                    "$context 模式中目标函数权重总和应为 1.0（当前: ${
                        String.format(
                            Locale.US,
                            "%.3f",
                            totalWeight,
                        )
                    })",
                )
        }
    }
}
