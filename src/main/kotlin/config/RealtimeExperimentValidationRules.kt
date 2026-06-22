package config

private const val REALTIME_CLOUDLET_WARNING_LIMIT = 10_000
private const val SIMULATION_DURATION_WARNING_LIMIT = 10_000.0
private const val ARRIVAL_RATE_WARNING_LIMIT = 1_000.0
private const val REALTIME_RUN_WARNING_LIMIT = 50

internal object RealtimeExperimentShapeValidator {
    fun validate(
        realtime: RealtimeConfig,
        context: RealtimeValidationContext,
    ) {
        if (realtime.cloudletCount <= 0) {
            context.addError("realtime.cloudletCount", realtime.cloudletCount, "实时调度任务数必须大于0")
        }
        if (realtime.cloudletCount > REALTIME_CLOUDLET_WARNING_LIMIT) {
            context.addError(
                "realtime.cloudletCount",
                realtime.cloudletCount,
                "实时调度任务数过大，可能影响性能，建议不超过$REALTIME_CLOUDLET_WARNING_LIMIT",
            )
        }
        if (realtime.simulationDuration <= 0) {
            context.addError(
                "realtime.simulationDuration",
                realtime.simulationDuration,
                "仿真持续时间必须大于0",
            )
        }
        if (realtime.simulationDuration > SIMULATION_DURATION_WARNING_LIMIT) {
            context.addError(
                "realtime.simulationDuration",
                realtime.simulationDuration,
                "仿真持续时间过长，可能影响性能，建议不超过${SIMULATION_DURATION_WARNING_LIMIT.toInt()}秒",
            )
        }
        if (realtime.arrivalRate <= 0) {
            context.addError("realtime.arrivalRate", realtime.arrivalRate, "到达率必须大于0")
        }
        if (realtime.arrivalRate > ARRIVAL_RATE_WARNING_LIMIT) {
            context.addError(
                "realtime.arrivalRate",
                realtime.arrivalRate,
                "到达率过高，可能导致系统过载，建议不超过${ARRIVAL_RATE_WARNING_LIMIT.toInt()}个/秒",
            )
        }
        if (realtime.runs <= 0) {
            context.addError("realtime.runs", realtime.runs, "实时调度运行次数必须大于0")
        }
        if (realtime.runs > REALTIME_RUN_WARNING_LIMIT) {
            context.addError(
                "realtime.runs",
                realtime.runs,
                "实时调度运行次数过多，可能耗时过长，建议不超过$REALTIME_RUN_WARNING_LIMIT",
            )
        }
    }
}

internal object RealtimeArrivalValidator {
    private val distributions = setOf("poisson", "uniform", "burst", "periodic", "sporadic", "diurnal_burst")
    private val workloadPatterns = setOf("standard", "mixed_short_long", "dag_chain", "dag_layered")

    fun validate(
        arrival: RealtimeArrivalConfig,
        context: RealtimeValidationContext,
    ) {
        if (arrival.distribution.lowercase() !in distributions) {
            context.addError(
                "realtime.arrival.distribution",
                arrival.distribution,
                "到达分布必须是以下值之一: ${distributions.joinToString(", ")}",
            )
        }
        if (arrival.workloadPattern.lowercase() !in workloadPatterns) {
            context.addError(
                "realtime.arrival.workloadPattern",
                arrival.workloadPattern,
                "负载模式必须是以下值之一: ${workloadPatterns.joinToString(", ")}",
            )
        }
        if (arrival.burstIntensity <= 0.0) {
            context.addError("realtime.arrival.burstIntensity", arrival.burstIntensity, "突发强度必须大于0")
        }
        if (arrival.burstDuration <= 0.0) {
            context.addError("realtime.arrival.burstDuration", arrival.burstDuration, "突发持续时间必须大于0")
        }
        validateExtendedArrival(arrival, context)
        validateWorkloadPattern(arrival, context)
    }

    private fun validateExtendedArrival(
        arrival: RealtimeArrivalConfig,
        context: RealtimeValidationContext,
    ) {
        if (arrival.periodSeconds <= 0.0) {
            context.addError("realtime.arrival.periodSeconds", arrival.periodSeconds, "周期到达间隔必须大于0")
        }
        if (arrival.arrivalJitter < 0.0) {
            context.addError("realtime.arrival.arrivalJitter", arrival.arrivalJitter, "到达抖动不能为负数")
        }
        if (arrival.sporadicMinInterArrival <= 0.0) {
            context.addError(
                "realtime.arrival.sporadicMinInterArrival",
                arrival.sporadicMinInterArrival,
                "sporadic 最小到达间隔必须大于0",
            )
        }
        if (arrival.sporadicMaxInterArrival < arrival.sporadicMinInterArrival) {
            context.addError(
                "realtime.arrival.sporadicMaxInterArrival",
                arrival.sporadicMaxInterArrival,
                "sporadic 最大到达间隔不能小于最小到达间隔",
            )
        }
        if (arrival.diurnalPeakMultiplier <= 0.0) {
            context.addError(
                "realtime.arrival.diurnalPeakMultiplier",
                arrival.diurnalPeakMultiplier,
                "昼夜峰值倍率必须大于0",
            )
        }
        if (arrival.diurnalOffPeakMultiplier <= 0.0) {
            context.addError(
                "realtime.arrival.diurnalOffPeakMultiplier",
                arrival.diurnalOffPeakMultiplier,
                "昼夜低谷倍率必须大于0",
            )
        }
    }

    private fun validateWorkloadPattern(
        arrival: RealtimeArrivalConfig,
        context: RealtimeValidationContext,
    ) {
        if (arrival.shortTaskRatio !in 0.0..1.0) {
            context.addError("realtime.arrival.shortTaskRatio", arrival.shortTaskRatio, "短任务比例必须在0到1之间")
        }
        if (arrival.shortTaskLengthMultiplier <= 0.0) {
            context.addError(
                "realtime.arrival.shortTaskLengthMultiplier",
                arrival.shortTaskLengthMultiplier,
                "短任务长度倍率必须大于0",
            )
        }
        if (arrival.longTaskLengthMultiplier <= 0.0) {
            context.addError(
                "realtime.arrival.longTaskLengthMultiplier",
                arrival.longTaskLengthMultiplier,
                "长任务长度倍率必须大于0",
            )
        }
        if (arrival.runtimeReferenceMips <= 0.0) {
            context.addError(
                "realtime.arrival.runtimeReferenceMips",
                arrival.runtimeReferenceMips,
                "运行时参考 MIPS 必须大于0",
            )
        }
        if (arrival.dagDepth <= 0) {
            context.addError("realtime.arrival.dagDepth", arrival.dagDepth, "DAG 深度必须大于0")
        }
        if (arrival.dagWidth <= 0) {
            context.addError("realtime.arrival.dagWidth", arrival.dagWidth, "DAG 宽度必须大于0")
        }
        if (arrival.dagFanOut <= 0) {
            context.addError("realtime.arrival.dagFanOut", arrival.dagFanOut, "DAG fan-out 必须大于0")
        }
    }
}
