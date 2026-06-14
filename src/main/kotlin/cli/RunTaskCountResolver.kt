package cli

import config.ExperimentConfig
import config.ProfileConfig

private const val SMALL_MULTI_TASK_COUNT = 50
private const val MEDIUM_MULTI_TASK_COUNT = 100
private const val LARGE_MULTI_TASK_COUNT = 200
private const val EXTRA_LARGE_MULTI_TASK_COUNT = 500

private val defaultMultiTaskCounts =
    listOf(
        SMALL_MULTI_TASK_COUNT,
        MEDIUM_MULTI_TASK_COUNT,
        LARGE_MULTI_TASK_COUNT,
        EXTRA_LARGE_MULTI_TASK_COUNT,
    )

internal object RunTaskCountResolver {
    fun resolveOverrides(
        command: CliParser.RunCommand,
        profile: ProfileConfig?,
        mode: String,
        experiment: ExperimentConfig,
    ): List<Int> {
        if (!RunModeResolver.isMulti(mode)) return emptyList()
        return command.taskCounts.ifEmpty {
            profile?.tasks ?: if (RunModeResolver.isBatch(mode)) {
                experiment.batch.cloudletCounts
            } else {
                experiment.realtime.cloudletCounts
            }
        }
    }

    fun resolveFinal(
        mode: String,
        command: CliParser.RunCommand,
        config: ExperimentConfig,
        profile: ProfileConfig?,
    ): List<Int> {
        if (!RunModeResolver.isMulti(mode)) return emptyList()
        val baseCounts =
            when (mode) {
                "batch-multi" -> profile?.tasks ?: config.batch.cloudletCounts
                "realtime-multi" -> profile?.tasks ?: config.realtime.cloudletCounts
                else -> emptyList()
            }
        return command.taskCounts.ifEmpty { baseCounts }.ifEmpty { defaultMultiTaskCounts }
    }
}
