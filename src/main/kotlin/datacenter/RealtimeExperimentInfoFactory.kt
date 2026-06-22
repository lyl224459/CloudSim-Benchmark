package datacenter

internal object RealtimeExperimentInfoFactory {
    fun create(
        config: RealtimeExperimentConfigSnapshot,
        randomSeed: Long,
        runs: Int,
        population: Int,
        maxIter: Int,
    ): Map<String, Any> =
        linkedMapOf<String, Any>()
            .apply { putBasics(config, randomSeed, runs, population, maxIter) }
            .apply { putSchedulingBasics(config.scheduling) }
            .apply { putSchedulingReliability(config.scheduling) }
            .apply { putTenantSettings(config.scheduling) }
            .apply { putTopologySettings(config.scheduling) }

    private fun MutableMap<String, Any>.putBasics(
        config: RealtimeExperimentConfigSnapshot,
        randomSeed: Long,
        runs: Int,
        population: Int,
        maxIter: Int,
    ) {
        this["运行模式"] = "实时调度 (Realtime)"
        this["任务数量"] = config.cloudletCount
        this["仿真持续时间"] = config.simulationDuration
        this["到达率"] = config.arrivalRate
        this["到达分布"] = config.arrival.distribution
        this["负载模式"] = config.arrival.workloadPattern
        this["调度策略"] = config.scheduling.strategy
        this["随机数种子"] = randomSeed
        this["运行次数"] = runs
        this["任务生成器"] = config.generatorType.name
        this["种群规模"] = population
        this["最大迭代次数"] = maxIter
    }

    private fun MutableMap<String, Any>.putSchedulingBasics(scheduling: config.RealtimeSchedulingConfig) {
        this["最大队列"] = scheduling.maxQueueSize
        this["任务超时"] = scheduling.taskTimeout
        this["资源预留"] = scheduling.resourceReservation
        this["调度决策延迟"] = scheduling.decisionDelay
        this["调度决策抖动"] = scheduling.decisionJitter
        this["任务失败率"] = scheduling.failureRate
        this["重试次数上限"] = scheduling.retryLimit
        this["重试延迟"] = scheduling.retryDelay
        this["重试退避倍数"] = scheduling.retryBackoffMultiplier
        this["队列策略"] = scheduling.queuePolicy
        this["优先级层级"] = scheduling.priorityLevels
        this["高优先级比例"] = scheduling.highPriorityRatio
        this["SLA deadline 系数"] = scheduling.deadlineFactor
        this["依赖强约束"] = scheduling.dependencyEnforcementEnabled
        this["单 VM 队列容量"] = scheduling.vmQueueCapacity
        this["过载失败倍率"] = scheduling.overloadFailureMultiplier
    }

    private fun MutableMap<String, Any>.putSchedulingReliability(scheduling: config.RealtimeSchedulingConfig) {
        this["弹性伸缩"] = scheduling.autoscalingEnabled
        this["扩容队列阈值"] = scheduling.scaleOutQueueThreshold
        this["缩容空闲时间"] = scheduling.scaleInIdleTime
        this["最大动态 VM 数"] = scheduling.maxDynamicVms
        this["VM 冷启动延迟"] = scheduling.vmColdStartDelay
        this["扩容成本"] = scheduling.scaleOutCost
        this["缩容保护时间"] = scheduling.scaleInProtectionTime
        this["资源模型"] = scheduling.resourceModelEnabled
        this["网络延迟"] = scheduling.networkLatency
        this["镜像拉取延迟"] = scheduling.imagePullDelay
        this["I/O 权重"] = scheduling.ioWeight
        this["RAM 权重"] = scheduling.ramWeight
        this["带宽权重"] = scheduling.bwWeight
        this["运行中失败率"] = scheduling.runtimeFailureRate
        this["节点失败率"] = scheduling.nodeFailureRate
        this["checkpoint 间隔"] = scheduling.checkpointInterval
        this["迁移延迟"] = scheduling.migrationDelay
        this["超时动作"] = scheduling.timeoutAction
        this["抢占启用"] = scheduling.preemptionEnabled
        this["抢占策略"] = scheduling.preemptionPolicy
        this["抢占最小优先级差"] = scheduling.preemptionMinPriorityGap
        this["单任务最大抢占次数"] = scheduling.preemptionMaxPerTask
        this["抢占延迟"] = scheduling.preemptionDelay
        this["抢占惩罚"] = scheduling.preemptionPenalty
    }

    private fun MutableMap<String, Any>.putTenantSettings(scheduling: config.RealtimeSchedulingConfig) {
        this["多租户隔离"] = scheduling.multiTenantEnabled
        this["租户数量"] = scheduling.tenantCount
        this["租户配额"] = scheduling.tenantQuota.joinToString(", ")
        this["租户权重"] = scheduling.tenantWeights.joinToString(", ")
        this["租户公平策略"] = scheduling.tenantFairnessPolicy
        this["租户调度策略"] = scheduling.tenantSchedulingPolicy
        this["租户突发额度"] = scheduling.tenantBurstAllowance
        this["租户 SLA 惩罚权重"] = scheduling.tenantSlaPenaltyWeight
        this["租户成本预算"] = scheduling.tenantCostBudget.joinToString(", ")
    }

    private fun MutableMap<String, Any>.putTopologySettings(scheduling: config.RealtimeSchedulingConfig) {
        this["拓扑模型"] = scheduling.topologyEnabled
        this["拓扑策略"] = scheduling.topologyPolicy
        this["Region 数量"] = scheduling.regionCount
        this["每 Region Rack 数"] = scheduling.racksPerRegion
        this["每 Rack Host 数"] = scheduling.hostsPerRack
        this["本地 Region"] = scheduling.localRegion
        this["跨 Rack 延迟"] = scheduling.crossRackLatency
        this["跨 Region 延迟"] = scheduling.crossRegionLatency
        this["跨 Region 成本"] = scheduling.crossRegionCost
        this["Host 失败率"] = scheduling.hostFailureRate
        this["Rack 失败率"] = scheduling.rackFailureRate
        this["Region 失败率"] = scheduling.regionFailureRate
        this["物理拓扑模型"] = scheduling.physicalTopologyEnabled
        this["数据本地性"] = scheduling.dataLocalityEnabled
        this["镜像缓存"] = scheduling.imageCacheEnabled
        this["物理每 Rack Host 数"] = scheduling.hostCountPerRack
        this["Host CPU 容量"] = scheduling.hostCpuCapacity
        this["Host RAM 容量"] = scheduling.hostRamCapacity
        this["Host 带宽容量"] = scheduling.hostBwCapacity
        this["Host I/O 容量"] = scheduling.hostIoCapacity
        this["跨 Rack 带宽"] = scheduling.crossRackBandwidth
        this["跨 Region 带宽"] = scheduling.crossRegionBandwidth
        this["数据本地性策略"] = scheduling.dataLocalityPolicy
        this["镜像缓存容量"] = scheduling.imageCacheCapacity
    }
}
