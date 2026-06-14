package config

private const val REALTIME_CLOUDLET_WARNING_LIMIT = 10_000
private const val SIMULATION_DURATION_WARNING_LIMIT = 10_000.0
private const val ARRIVAL_RATE_WARNING_LIMIT = 1_000.0
private const val REALTIME_RUN_WARNING_LIMIT = 50
private const val MIN_RETRY_BACKOFF_MULTIPLIER = 1.0

internal object RealtimeConfigValidator {
    fun validate(
        realtime: RealtimeConfig,
        errors: MutableList<ValidationError>,
    ) {
        RealtimeExperimentShapeValidator.validate(realtime, errors)
        RealtimeArrivalValidator.validate(realtime.arrival, errors)
        RealtimeSchedulingValidator.validate(realtime.scheduling, errors)
        validateCloudletCounts(realtime.cloudletCounts, errors)
    }

    private fun validateCloudletCounts(
        cloudletCounts: List<Int>,
        errors: MutableList<ValidationError>,
    ) {
        cloudletCounts.forEachIndexed { index, count ->
            if (count <= 0) {
                errors.addError("realtime.cloudletCounts[$index]", count, "批量任务数必须大于0")
            }
        }
    }
}

private object RealtimeExperimentShapeValidator {
    fun validate(
        realtime: RealtimeConfig,
        errors: MutableList<ValidationError>,
    ) {
        if (realtime.cloudletCount <= 0) {
            errors.addError("realtime.cloudletCount", realtime.cloudletCount, "实时调度任务数必须大于0")
        }
        if (realtime.cloudletCount > REALTIME_CLOUDLET_WARNING_LIMIT) {
            errors.addError(
                "realtime.cloudletCount",
                realtime.cloudletCount,
                "实时调度任务数过大，可能影响性能，建议不超过$REALTIME_CLOUDLET_WARNING_LIMIT",
            )
        }
        if (realtime.simulationDuration <= 0) {
            errors.addError(
                "realtime.simulationDuration",
                realtime.simulationDuration,
                "仿真持续时间必须大于0",
            )
        }
        if (realtime.simulationDuration > SIMULATION_DURATION_WARNING_LIMIT) {
            errors.addError(
                "realtime.simulationDuration",
                realtime.simulationDuration,
                "仿真持续时间过长，可能影响性能，建议不超过${SIMULATION_DURATION_WARNING_LIMIT.toInt()}秒",
            )
        }
        if (realtime.arrivalRate <= 0) {
            errors.addError("realtime.arrivalRate", realtime.arrivalRate, "到达率必须大于0")
        }
        if (realtime.arrivalRate > ARRIVAL_RATE_WARNING_LIMIT) {
            errors.addError(
                "realtime.arrivalRate",
                realtime.arrivalRate,
                "到达率过高，可能导致系统过载，建议不超过${ARRIVAL_RATE_WARNING_LIMIT.toInt()}个/秒",
            )
        }
        if (realtime.runs <= 0) {
            errors.addError("realtime.runs", realtime.runs, "实时调度运行次数必须大于0")
        }
        if (realtime.runs > REALTIME_RUN_WARNING_LIMIT) {
            errors.addError(
                "realtime.runs",
                realtime.runs,
                "实时调度运行次数过多，可能耗时过长，建议不超过$REALTIME_RUN_WARNING_LIMIT",
            )
        }
    }
}

private object RealtimeArrivalValidator {
    private val distributions = setOf("poisson", "uniform", "burst")

    fun validate(
        arrival: RealtimeArrivalConfig,
        errors: MutableList<ValidationError>,
    ) {
        if (arrival.distribution.lowercase() !in distributions) {
            errors.addError(
                "realtime.arrival.distribution",
                arrival.distribution,
                "到达分布必须是以下值之一: ${distributions.joinToString(", ")}",
            )
        }
        if (arrival.burstIntensity <= 0.0) {
            errors.addError("realtime.arrival.burstIntensity", arrival.burstIntensity, "突发强度必须大于0")
        }
        if (arrival.burstDuration <= 0.0) {
            errors.addError("realtime.arrival.burstDuration", arrival.burstDuration, "突发持续时间必须大于0")
        }
    }
}

private object RealtimeSchedulingValidator {
    fun validate(
        scheduling: RealtimeSchedulingConfig,
        errors: MutableList<ValidationError>,
    ) {
        RealtimeCoreSchedulingValidator.validate(scheduling, errors)
        RealtimeRuntimeReliabilityValidator.validate(scheduling, errors)
        RealtimeTenantSchedulingValidator.validate(scheduling, errors)
        RealtimeTopologySchedulingValidator.validate(scheduling, errors)
        validateReservation(scheduling, errors)
    }

    private fun validateReservation(
        scheduling: RealtimeSchedulingConfig,
        errors: MutableList<ValidationError>,
    ) {
        val reservations = setOf("none", "partial", "full")
        enumValue(
            errors,
            "realtime.scheduling.resourceReservation",
            scheduling.resourceReservation,
            reservations,
            "资源预留策略",
        )
    }
}

private object RealtimeCoreSchedulingValidator {
    private val strategies = setOf("dynamic", "static")

    fun validate(
        scheduling: RealtimeSchedulingConfig,
        errors: MutableList<ValidationError>,
    ) {
        enumValue(errors, "realtime.scheduling.strategy", scheduling.strategy, strategies, "实时调度策略")
        if (scheduling.maxQueueSize <= 0) {
            errors.addError("realtime.scheduling.maxQueueSize", scheduling.maxQueueSize, "最大队列长度必须大于0")
        }
        nonNegative(errors, "realtime.scheduling.taskTimeout", scheduling.taskTimeout, "任务超时时间不能为负数")
        nonNegative(errors, "realtime.scheduling.decisionDelay", scheduling.decisionDelay, "调度决策延迟不能为负数")
        nonNegative(errors, "realtime.scheduling.decisionJitter", scheduling.decisionJitter, "调度决策抖动不能为负数")
        boundedUnit(errors, "realtime.scheduling.failureRate", scheduling.failureRate, "任务失败率必须在 [0,1] 范围内")
        if (scheduling.retryLimit < 0) {
            errors.addError("realtime.scheduling.retryLimit", scheduling.retryLimit, "重试次数不能为负数")
        }
        nonNegative(errors, "realtime.scheduling.retryDelay", scheduling.retryDelay, "重试延迟不能为负数")
        if (scheduling.retryBackoffMultiplier < MIN_RETRY_BACKOFF_MULTIPLIER) {
            errors.addError(
                "realtime.scheduling.retryBackoffMultiplier",
                scheduling.retryBackoffMultiplier,
                "重试退避倍数必须大于等于 1",
            )
        }
        validateQueueAndPriority(scheduling, errors)
        validateScalingAndResourceWeights(scheduling, errors)
    }

    private fun validateQueueAndPriority(
        scheduling: RealtimeSchedulingConfig,
        errors: MutableList<ValidationError>,
    ) {
        enumValue(
            errors,
            "realtime.scheduling.queuePolicy",
            scheduling.queuePolicy,
            RealtimeQueuePolicy.valuesForConfig(),
            "实时队列策略",
        )
        if (scheduling.priorityLevels < 1) {
            errors.addError("realtime.scheduling.priorityLevels", scheduling.priorityLevels, "优先级层级必须大于等于 1")
        }
        boundedUnit(
            errors,
            "realtime.scheduling.highPriorityRatio",
            scheduling.highPriorityRatio,
            "高优先级任务比例必须在 [0,1] 范围内",
        )
        nonNegative(errors, "realtime.scheduling.deadlineFactor", scheduling.deadlineFactor, "SLA deadline 系数不能为负数")
        if (scheduling.vmQueueCapacity < 0) {
            errors.addError("realtime.scheduling.vmQueueCapacity", scheduling.vmQueueCapacity, "单 VM 队列容量不能为负数")
        }
        nonNegative(
            errors,
            "realtime.scheduling.overloadFailureMultiplier",
            scheduling.overloadFailureMultiplier,
            "过载失败倍率不能为负数",
        )
    }

    private fun validateScalingAndResourceWeights(
        scheduling: RealtimeSchedulingConfig,
        errors: MutableList<ValidationError>,
    ) {
        if (scheduling.scaleOutQueueThreshold < 0) {
            errors.addError(
                "realtime.scheduling.scaleOutQueueThreshold",
                scheduling.scaleOutQueueThreshold,
                "扩容队列阈值不能为负数",
            )
        }
        nonNegative(errors, "realtime.scheduling.scaleInIdleTime", scheduling.scaleInIdleTime, "缩容空闲时间不能为负数")
        if (scheduling.maxDynamicVms < 0) {
            errors.addError("realtime.scheduling.maxDynamicVms", scheduling.maxDynamicVms, "最大动态 VM 数不能为负数")
        }
        nonNegative(errors, "realtime.scheduling.vmColdStartDelay", scheduling.vmColdStartDelay, "VM 冷启动延迟不能为负数")
        nonNegative(errors, "realtime.scheduling.scaleOutCost", scheduling.scaleOutCost, "扩容成本不能为负数")
        nonNegative(
            errors,
            "realtime.scheduling.scaleInProtectionTime",
            scheduling.scaleInProtectionTime,
            "缩容保护时间不能为负数",
        )
        nonNegative(errors, "realtime.scheduling.networkLatency", scheduling.networkLatency, "网络延迟不能为负数")
        nonNegative(errors, "realtime.scheduling.imagePullDelay", scheduling.imagePullDelay, "镜像拉取延迟不能为负数")
        nonNegative(errors, "realtime.scheduling.ioWeight", scheduling.ioWeight, "I/O 权重不能为负数")
        nonNegative(errors, "realtime.scheduling.ramWeight", scheduling.ramWeight, "RAM 权重不能为负数")
        nonNegative(errors, "realtime.scheduling.bwWeight", scheduling.bwWeight, "带宽权重不能为负数")
    }
}

private object RealtimeRuntimeReliabilityValidator {
    fun validate(
        scheduling: RealtimeSchedulingConfig,
        errors: MutableList<ValidationError>,
    ) {
        boundedUnit(
            errors,
            "realtime.scheduling.runtimeFailureRate",
            scheduling.runtimeFailureRate,
            "运行中失败率必须在 [0,1] 范围内",
        )
        boundedUnit(
            errors,
            "realtime.scheduling.nodeFailureRate",
            scheduling.nodeFailureRate,
            "节点失败率必须在 [0,1] 范围内",
        )
        nonNegative(
            errors,
            "realtime.scheduling.checkpointInterval",
            scheduling.checkpointInterval,
            "checkpoint 间隔不能为负数",
        )
        nonNegative(errors, "realtime.scheduling.migrationDelay", scheduling.migrationDelay, "迁移延迟不能为负数")
        enumValue(
            errors,
            "realtime.scheduling.timeoutAction",
            scheduling.timeoutAction,
            RealtimeTimeoutAction.valuesForConfig(),
            "超时动作",
        )
        enumValue(
            errors,
            "realtime.scheduling.preemptionPolicy",
            scheduling.preemptionPolicy,
            RealtimePreemptionPolicy.valuesForConfig(),
            "抢占策略",
        )
        if (scheduling.preemptionMinPriorityGap < 0) {
            errors.addError(
                "realtime.scheduling.preemptionMinPriorityGap",
                scheduling.preemptionMinPriorityGap,
                "抢占优先级差不能为负数",
            )
        }
        if (scheduling.preemptionMaxPerTask < 0) {
            errors.addError(
                "realtime.scheduling.preemptionMaxPerTask",
                scheduling.preemptionMaxPerTask,
                "单任务最大抢占次数不能为负数",
            )
        }
        nonNegative(errors, "realtime.scheduling.preemptionDelay", scheduling.preemptionDelay, "抢占延迟不能为负数")
        nonNegative(errors, "realtime.scheduling.preemptionPenalty", scheduling.preemptionPenalty, "抢占惩罚不能为负数")
    }
}

private object RealtimeTenantSchedulingValidator {
    fun validate(
        scheduling: RealtimeSchedulingConfig,
        errors: MutableList<ValidationError>,
    ) {
        if (scheduling.tenantCount < 1) {
            errors.addError("realtime.scheduling.tenantCount", scheduling.tenantCount, "租户数量必须大于等于 1")
        }
        validateTenantQuota(scheduling, errors)
        validateTenantWeights(scheduling, errors)
        enumValue(
            errors,
            "realtime.scheduling.tenantFairnessPolicy",
            scheduling.tenantFairnessPolicy,
            RealtimeTenantFairnessPolicy.valuesForConfig(),
            "租户公平策略",
        )
        enumValue(
            errors,
            "realtime.scheduling.tenantSchedulingPolicy",
            scheduling.tenantSchedulingPolicy,
            TenantSchedulingPolicy.valuesForConfig(),
            "租户调度策略",
        )
        if (scheduling.tenantBurstAllowance < 0) {
            errors.addError(
                "realtime.scheduling.tenantBurstAllowance",
                scheduling.tenantBurstAllowance,
                "租户突发额度不能为负数",
            )
        }
        nonNegative(
            errors,
            "realtime.scheduling.tenantSlaPenaltyWeight",
            scheduling.tenantSlaPenaltyWeight,
            "租户 SLA 惩罚权重不能为负数",
        )
        validateTenantCostBudget(scheduling, errors)
    }

    private fun validateTenantQuota(
        scheduling: RealtimeSchedulingConfig,
        errors: MutableList<ValidationError>,
    ) {
        if (scheduling.tenantQuota.isNotEmpty() && scheduling.tenantQuota.size != scheduling.tenantCount) {
            errors.addError(
                "realtime.scheduling.tenantQuota",
                scheduling.tenantQuota.joinToString(","),
                "租户配额数量必须等于 tenantCount",
            )
        }
        scheduling.tenantQuota.forEachIndexed { index, quota ->
            if (quota < 0) {
                errors.addError("realtime.scheduling.tenantQuota[$index]", quota, "租户配额不能为负数")
            }
        }
    }

    private fun validateTenantWeights(
        scheduling: RealtimeSchedulingConfig,
        errors: MutableList<ValidationError>,
    ) {
        if (scheduling.tenantWeights.isNotEmpty() && scheduling.tenantWeights.size != scheduling.tenantCount) {
            errors.addError(
                "realtime.scheduling.tenantWeights",
                scheduling.tenantWeights.joinToString(","),
                "租户权重数量必须等于 tenantCount",
            )
        }
        scheduling.tenantWeights.forEachIndexed { index, weight ->
            if (weight <= 0.0) {
                errors.addError("realtime.scheduling.tenantWeights[$index]", weight, "租户权重必须大于 0")
            }
        }
    }

    private fun validateTenantCostBudget(
        scheduling: RealtimeSchedulingConfig,
        errors: MutableList<ValidationError>,
    ) {
        if (scheduling.tenantCostBudget.isNotEmpty() && scheduling.tenantCostBudget.size != scheduling.tenantCount) {
            errors.addError(
                "realtime.scheduling.tenantCostBudget",
                scheduling.tenantCostBudget.joinToString(","),
                "租户成本预算数量必须等于 tenantCount",
            )
        }
        scheduling.tenantCostBudget.forEachIndexed { index, budget ->
            if (budget < 0.0) {
                errors.addError("realtime.scheduling.tenantCostBudget[$index]", budget, "租户成本预算不能为负数")
            }
        }
    }
}

private object RealtimeTopologySchedulingValidator {
    fun validate(
        scheduling: RealtimeSchedulingConfig,
        errors: MutableList<ValidationError>,
    ) {
        enumValue(
            errors,
            "realtime.scheduling.topologyPolicy",
            scheduling.topologyPolicy,
            RealtimeTopologyPolicy.valuesForConfig(),
            "拓扑策略",
        )
        validateTopologyShape(scheduling, errors)
        validateTopologyLatencyAndFailure(scheduling, errors)
        validatePhysicalTopology(scheduling, errors)
        validateDataLocalityAndImageCache(scheduling, errors)
    }

    private fun validateTopologyShape(
        scheduling: RealtimeSchedulingConfig,
        errors: MutableList<ValidationError>,
    ) {
        if (scheduling.regionCount < 1) {
            errors.addError("realtime.scheduling.regionCount", scheduling.regionCount, "Region 数量必须大于等于 1")
        }
        if (scheduling.racksPerRegion < 1) {
            errors.addError(
                "realtime.scheduling.racksPerRegion",
                scheduling.racksPerRegion,
                "每个 Region 的 Rack 数量必须大于等于 1",
            )
        }
        if (scheduling.hostsPerRack < 1) {
            errors.addError(
                "realtime.scheduling.hostsPerRack",
                scheduling.hostsPerRack,
                "每个 Rack 的 Host 数量必须大于等于 1",
            )
        }
        if (scheduling.localRegion !in 0 until scheduling.regionCount.coerceAtLeast(1)) {
            errors.addError(
                "realtime.scheduling.localRegion",
                scheduling.localRegion,
                "本地 Region 必须在 [0, regionCount) 范围内",
            )
        }
    }

    private fun validateTopologyLatencyAndFailure(
        scheduling: RealtimeSchedulingConfig,
        errors: MutableList<ValidationError>,
    ) {
        nonNegative(errors, "realtime.scheduling.crossRackLatency", scheduling.crossRackLatency, "跨 Rack 延迟不能为负数")
        nonNegative(errors, "realtime.scheduling.crossRegionLatency", scheduling.crossRegionLatency, "跨 Region 延迟不能为负数")
        nonNegative(errors, "realtime.scheduling.crossRegionCost", scheduling.crossRegionCost, "跨 Region 成本不能为负数")
        boundedUnit(errors, "realtime.scheduling.hostFailureRate", scheduling.hostFailureRate, "Host 失败率必须在 [0,1] 范围内")
        boundedUnit(errors, "realtime.scheduling.rackFailureRate", scheduling.rackFailureRate, "Rack 失败率必须在 [0,1] 范围内")
        boundedUnit(
            errors,
            "realtime.scheduling.regionFailureRate",
            scheduling.regionFailureRate,
            "Region 失败率必须在 [0,1] 范围内",
        )
    }

    private fun validatePhysicalTopology(
        scheduling: RealtimeSchedulingConfig,
        errors: MutableList<ValidationError>,
    ) {
        if (scheduling.hostCountPerRack < 1) {
            errors.addError(
                "realtime.scheduling.hostCountPerRack",
                scheduling.hostCountPerRack,
                "物理拓扑中每个 Rack 的 Host 数量必须大于等于 1",
            )
        }
        nonNegative(errors, "realtime.scheduling.hostCpuCapacity", scheduling.hostCpuCapacity, "Host CPU 容量不能为负数")
        nonNegative(errors, "realtime.scheduling.hostRamCapacity", scheduling.hostRamCapacity, "Host RAM 容量不能为负数")
        nonNegative(errors, "realtime.scheduling.hostBwCapacity", scheduling.hostBwCapacity, "Host 带宽容量不能为负数")
        nonNegative(errors, "realtime.scheduling.hostIoCapacity", scheduling.hostIoCapacity, "Host I/O 容量不能为负数")
        nonNegative(
            errors,
            "realtime.scheduling.crossRackBandwidth",
            scheduling.crossRackBandwidth,
            "跨 Rack 带宽不能为负数",
        )
        nonNegative(
            errors,
            "realtime.scheduling.crossRegionBandwidth",
            scheduling.crossRegionBandwidth,
            "跨 Region 带宽不能为负数",
        )
    }

    private fun validateDataLocalityAndImageCache(
        scheduling: RealtimeSchedulingConfig,
        errors: MutableList<ValidationError>,
    ) {
        enumValue(
            errors,
            "realtime.scheduling.dataLocalityPolicy",
            scheduling.dataLocalityPolicy,
            DataLocalityPolicy.valuesForConfig(),
            "数据本地性策略",
        )
        if (scheduling.imageCacheCapacity < 0) {
            errors.addError(
                "realtime.scheduling.imageCacheCapacity",
                scheduling.imageCacheCapacity,
                "镜像缓存容量不能为负数",
            )
        }
    }
}

private fun nonNegative(
    errors: MutableList<ValidationError>,
    field: String,
    value: Double,
    message: String,
) {
    if (value < 0.0) {
        errors.addError(field, value, message)
    }
}

private fun boundedUnit(
    errors: MutableList<ValidationError>,
    field: String,
    value: Double,
    message: String,
) {
    if (value < 0.0 || value > 1.0) {
        errors.addError(field, value, message)
    }
}

private fun enumValue(
    errors: MutableList<ValidationError>,
    field: String,
    value: String,
    allowedValues: Set<String>,
    label: String,
) {
    if (value.lowercase() !in allowedValues) {
        errors.addError(field, value, "$label 必须是以下值之一: ${allowedValues.joinToString(", ")}")
    }
}
