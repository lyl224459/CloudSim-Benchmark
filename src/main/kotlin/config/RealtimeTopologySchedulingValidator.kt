package config

internal object RealtimeTopologySchedulingValidator {
    fun validate(
        scheduling: RealtimeSchedulingConfig,
        context: RealtimeValidationContext,
    ) {
        enumValue(
            context,
            "realtime.scheduling.topologyPolicy",
            scheduling.topologyPolicy,
            RealtimeTopologyPolicy.valuesForConfig(),
            "拓扑策略",
        )
        validateTopologyShape(scheduling, context)
        validateTopologyLatencyAndFailure(scheduling, context)
        validatePhysicalTopology(scheduling, context)
        validateDataLocalityAndImageCache(scheduling, context)
    }

    private fun validateTopologyShape(
        scheduling: RealtimeSchedulingConfig,
        context: RealtimeValidationContext,
    ) {
        if (scheduling.regionCount < 1) {
            context.addError("realtime.scheduling.regionCount", scheduling.regionCount, "Region 数量必须大于等于 1")
        }
        if (scheduling.racksPerRegion < 1) {
            context.addError(
                "realtime.scheduling.racksPerRegion",
                scheduling.racksPerRegion,
                "每个 Region 的 Rack 数量必须大于等于 1",
            )
        }
        if (scheduling.hostsPerRack < 1) {
            context.addError(
                "realtime.scheduling.hostsPerRack",
                scheduling.hostsPerRack,
                "每个 Rack 的 Host 数量必须大于等于 1",
            )
        }
        if (scheduling.localRegion !in 0 until scheduling.regionCount.coerceAtLeast(1)) {
            context.addError(
                "realtime.scheduling.localRegion",
                scheduling.localRegion,
                "本地 Region 必须在 [0, regionCount) 范围内",
            )
        }
    }

    private fun validateTopologyLatencyAndFailure(
        scheduling: RealtimeSchedulingConfig,
        context: RealtimeValidationContext,
    ) {
        nonNegative(
            context,
            "realtime.scheduling.crossRackLatency",
            scheduling.crossRackLatency,
            "跨 Rack 延迟不能为负数",
        )
        nonNegative(
            context,
            "realtime.scheduling.crossRegionLatency",
            scheduling.crossRegionLatency,
            "跨 Region 延迟不能为负数",
        )
        nonNegative(context, "realtime.scheduling.crossRegionCost", scheduling.crossRegionCost, "跨 Region 成本不能为负数")
        boundedUnit(context, "realtime.scheduling.hostFailureRate", scheduling.hostFailureRate, "Host 失败率必须在 [0,1] 范围内")
        boundedUnit(context, "realtime.scheduling.rackFailureRate", scheduling.rackFailureRate, "Rack 失败率必须在 [0,1] 范围内")
        boundedUnit(
            context,
            "realtime.scheduling.regionFailureRate",
            scheduling.regionFailureRate,
            "Region 失败率必须在 [0,1] 范围内",
        )
    }

    private fun validatePhysicalTopology(
        scheduling: RealtimeSchedulingConfig,
        context: RealtimeValidationContext,
    ) {
        if (scheduling.hostCountPerRack < 1) {
            context.addError(
                "realtime.scheduling.hostCountPerRack",
                scheduling.hostCountPerRack,
                "物理拓扑中每个 Rack 的 Host 数量必须大于等于 1",
            )
        }
        nonNegative(context, "realtime.scheduling.hostCpuCapacity", scheduling.hostCpuCapacity, "Host CPU 容量不能为负数")
        if (scheduling.cpuOvercommitRatio <= 0.0) {
            context.addError(
                "realtime.scheduling.cpuOvercommitRatio",
                scheduling.cpuOvercommitRatio,
                "CPU overcommit 比例必须大于 0",
            )
        }
        nonNegative(context, "realtime.scheduling.hostRamCapacity", scheduling.hostRamCapacity, "Host RAM 容量不能为负数")
        nonNegative(context, "realtime.scheduling.hostBwCapacity", scheduling.hostBwCapacity, "Host 带宽容量不能为负数")
        nonNegative(context, "realtime.scheduling.hostIoCapacity", scheduling.hostIoCapacity, "Host I/O 容量不能为负数")
        nonNegative(
            context,
            "realtime.scheduling.noisyNeighborPenaltyWeight",
            scheduling.noisyNeighborPenaltyWeight,
            "Noisy-neighbor 惩罚权重不能为负数",
        )
        nonNegative(
            context,
            "realtime.scheduling.crossRackBandwidth",
            scheduling.crossRackBandwidth,
            "跨 Rack 带宽不能为负数",
        )
        nonNegative(
            context,
            "realtime.scheduling.crossRegionBandwidth",
            scheduling.crossRegionBandwidth,
            "跨 Region 带宽不能为负数",
        )
    }

    private fun validateDataLocalityAndImageCache(
        scheduling: RealtimeSchedulingConfig,
        context: RealtimeValidationContext,
    ) {
        enumValue(
            context,
            "realtime.scheduling.dataLocalityPolicy",
            scheduling.dataLocalityPolicy,
            DataLocalityPolicy.valuesForConfig(),
            "数据本地性策略",
        )
        if (scheduling.imageCacheCapacity < 0) {
            context.addError(
                "realtime.scheduling.imageCacheCapacity",
                scheduling.imageCacheCapacity,
                "镜像缓存容量不能为负数",
            )
        }
    }
}
