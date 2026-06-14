package config

internal object RealtimeRuntimeReliabilityValidator {
    fun validate(
        scheduling: RealtimeSchedulingConfig,
        context: RealtimeValidationContext,
    ) {
        boundedUnit(
            context,
            "realtime.scheduling.runtimeFailureRate",
            scheduling.runtimeFailureRate,
            "运行中失败率必须在 [0,1] 范围内",
        )
        boundedUnit(
            context,
            "realtime.scheduling.nodeFailureRate",
            scheduling.nodeFailureRate,
            "节点失败率必须在 [0,1] 范围内",
        )
        nonNegative(
            context,
            "realtime.scheduling.checkpointInterval",
            scheduling.checkpointInterval,
            "checkpoint 间隔不能为负数",
        )
        nonNegative(context, "realtime.scheduling.migrationDelay", scheduling.migrationDelay, "迁移延迟不能为负数")
        enumValue(
            context,
            "realtime.scheduling.timeoutAction",
            scheduling.timeoutAction,
            RealtimeTimeoutAction.valuesForConfig(),
            "超时动作",
        )
        enumValue(
            context,
            "realtime.scheduling.preemptionPolicy",
            scheduling.preemptionPolicy,
            RealtimePreemptionPolicy.valuesForConfig(),
            "抢占策略",
        )
        if (scheduling.preemptionMinPriorityGap < 0) {
            context.addError(
                "realtime.scheduling.preemptionMinPriorityGap",
                scheduling.preemptionMinPriorityGap,
                "抢占优先级差不能为负数",
            )
        }
        if (scheduling.preemptionMaxPerTask < 0) {
            context.addError(
                "realtime.scheduling.preemptionMaxPerTask",
                scheduling.preemptionMaxPerTask,
                "单任务最大抢占次数不能为负数",
            )
        }
        nonNegative(context, "realtime.scheduling.preemptionDelay", scheduling.preemptionDelay, "抢占延迟不能为负数")
        nonNegative(context, "realtime.scheduling.preemptionPenalty", scheduling.preemptionPenalty, "抢占惩罚不能为负数")
    }
}
