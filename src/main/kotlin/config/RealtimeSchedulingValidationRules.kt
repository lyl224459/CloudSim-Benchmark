package config

private const val MIN_RETRY_BACKOFF_MULTIPLIER = 1.0

internal object RealtimeSchedulingValidator {
    fun validate(
        scheduling: RealtimeSchedulingConfig,
        context: RealtimeValidationContext,
    ) {
        RealtimeCoreSchedulingValidator.validate(scheduling, context)
        RealtimeRuntimeReliabilityValidator.validate(scheduling, context)
        RealtimeTenantSchedulingValidator.validate(scheduling, context)
        RealtimeTopologySchedulingValidator.validate(scheduling, context)
        validateReservation(scheduling, context)
    }

    private fun validateReservation(
        scheduling: RealtimeSchedulingConfig,
        context: RealtimeValidationContext,
    ) {
        val reservations = setOf("none", "partial", "full")
        enumValue(
            context,
            "realtime.scheduling.resourceReservation",
            scheduling.resourceReservation,
            reservations,
            "资源预留策略",
        )
    }
}

internal object RealtimeCoreSchedulingValidator {
    private val strategies = setOf("dynamic", "static")

    fun validate(
        scheduling: RealtimeSchedulingConfig,
        context: RealtimeValidationContext,
    ) {
        enumValue(context, "realtime.scheduling.strategy", scheduling.strategy, strategies, "实时调度策略")
        if (scheduling.maxQueueSize <= 0) {
            context.addError("realtime.scheduling.maxQueueSize", scheduling.maxQueueSize, "最大队列长度必须大于0")
        }
        nonNegative(context, "realtime.scheduling.taskTimeout", scheduling.taskTimeout, "任务超时时间不能为负数")
        nonNegative(context, "realtime.scheduling.decisionDelay", scheduling.decisionDelay, "调度决策延迟不能为负数")
        nonNegative(context, "realtime.scheduling.decisionJitter", scheduling.decisionJitter, "调度决策抖动不能为负数")
        boundedUnit(context, "realtime.scheduling.failureRate", scheduling.failureRate, "任务失败率必须在 [0,1] 范围内")
        if (scheduling.retryLimit < 0) {
            context.addError("realtime.scheduling.retryLimit", scheduling.retryLimit, "重试次数不能为负数")
        }
        nonNegative(context, "realtime.scheduling.retryDelay", scheduling.retryDelay, "重试延迟不能为负数")
        if (scheduling.retryBackoffMultiplier < MIN_RETRY_BACKOFF_MULTIPLIER) {
            context.addError(
                "realtime.scheduling.retryBackoffMultiplier",
                scheduling.retryBackoffMultiplier,
                "重试退避倍数必须大于等于 1",
            )
        }
        validateQueueAndPriority(scheduling, context)
        validateRescheduling(scheduling, context)
        validateScalingAndResourceWeights(scheduling, context)
    }

    private fun validateQueueAndPriority(
        scheduling: RealtimeSchedulingConfig,
        context: RealtimeValidationContext,
    ) {
        enumValue(
            context,
            "realtime.scheduling.queuePolicy",
            scheduling.queuePolicy,
            RealtimeQueuePolicy.valuesForConfig(),
            "实时队列策略",
        )
        if (scheduling.priorityLevels < 1) {
            context.addError("realtime.scheduling.priorityLevels", scheduling.priorityLevels, "优先级层级必须大于等于 1")
        }
        boundedUnit(
            context,
            "realtime.scheduling.highPriorityRatio",
            scheduling.highPriorityRatio,
            "高优先级任务比例必须在 [0,1] 范围内",
        )
        nonNegative(context, "realtime.scheduling.deadlineFactor", scheduling.deadlineFactor, "SLA deadline 系数不能为负数")
        enumValue(
            context,
            "realtime.scheduling.deadlineType",
            scheduling.deadlineType,
            RealtimeDeadlineType.valuesForConfig(),
            "实时 deadline 类型",
        )
        enumValue(
            context,
            "realtime.scheduling.deadlineMissAction",
            scheduling.deadlineMissAction,
            RealtimeDeadlineMissAction.valuesForConfig(),
            "实时 deadline miss 动作",
        )
        val retriesDeadlineMissLater =
            scheduling.deadlineMissAction.equals(
                RealtimeDeadlineMissAction.RETRY_LATER.configValue,
                ignoreCase = true,
            )
        if (retriesDeadlineMissLater && scheduling.retryLimit <= 0) {
            context.addError(
                "realtime.scheduling.retryLimit",
                scheduling.retryLimit,
                "deadlineMissAction=retry_later 时 retryLimit 必须大于 0",
            )
        }
        if (scheduling.vmQueueCapacity < 0) {
            context.addError("realtime.scheduling.vmQueueCapacity", scheduling.vmQueueCapacity, "单 VM 队列容量不能为负数")
        }
        nonNegative(
            context,
            "realtime.scheduling.overloadFailureMultiplier",
            scheduling.overloadFailureMultiplier,
            "过载失败倍率不能为负数",
        )
    }

    private fun validateRescheduling(
        scheduling: RealtimeSchedulingConfig,
        context: RealtimeValidationContext,
    ) {
        enumValue(
            context,
            "realtime.scheduling.reschedulingPolicy",
            scheduling.reschedulingPolicy,
            RealtimeReschedulingPolicy.valuesForConfig(),
            "实时重调度策略",
        )
        nonNegative(
            context,
            "realtime.scheduling.reschedulingInterval",
            scheduling.reschedulingInterval,
            "重调度间隔不能为负数",
        )
        if (scheduling.maxReschedulesPerTask < 0) {
            context.addError(
                "realtime.scheduling.maxReschedulesPerTask",
                scheduling.maxReschedulesPerTask,
                "单任务最大重调度次数不能为负数",
            )
        }
        if (scheduling.reschedulingEnabled && scheduling.reschedulingInterval <= 0.0) {
            context.addError(
                "realtime.scheduling.reschedulingInterval",
                scheduling.reschedulingInterval,
                "启用重调度时重调度间隔必须大于 0",
            )
        }
        if (scheduling.reschedulingEnabled && scheduling.maxReschedulesPerTask <= 0) {
            context.addError(
                "realtime.scheduling.maxReschedulesPerTask",
                scheduling.maxReschedulesPerTask,
                "启用重调度时单任务最大重调度次数必须大于 0",
            )
        }
    }

    private fun validateScalingAndResourceWeights(
        scheduling: RealtimeSchedulingConfig,
        context: RealtimeValidationContext,
    ) {
        if (scheduling.scaleOutQueueThreshold < 0) {
            context.addError(
                "realtime.scheduling.scaleOutQueueThreshold",
                scheduling.scaleOutQueueThreshold,
                "扩容队列阈值不能为负数",
            )
        }
        nonNegative(context, "realtime.scheduling.scaleInIdleTime", scheduling.scaleInIdleTime, "缩容空闲时间不能为负数")
        if (scheduling.maxDynamicVms < 0) {
            context.addError("realtime.scheduling.maxDynamicVms", scheduling.maxDynamicVms, "最大动态 VM 数不能为负数")
        }
        nonNegative(context, "realtime.scheduling.vmColdStartDelay", scheduling.vmColdStartDelay, "VM 冷启动延迟不能为负数")
        nonNegative(context, "realtime.scheduling.scaleOutCost", scheduling.scaleOutCost, "扩容成本不能为负数")
        nonNegative(
            context,
            "realtime.scheduling.scaleInProtectionTime",
            scheduling.scaleInProtectionTime,
            "缩容保护时间不能为负数",
        )
        nonNegative(context, "realtime.scheduling.networkLatency", scheduling.networkLatency, "网络延迟不能为负数")
        nonNegative(context, "realtime.scheduling.imagePullDelay", scheduling.imagePullDelay, "镜像拉取延迟不能为负数")
        nonNegative(context, "realtime.scheduling.ioWeight", scheduling.ioWeight, "I/O 权重不能为负数")
        nonNegative(context, "realtime.scheduling.ramWeight", scheduling.ramWeight, "RAM 权重不能为负数")
        nonNegative(context, "realtime.scheduling.bwWeight", scheduling.bwWeight, "带宽权重不能为负数")
    }
}
