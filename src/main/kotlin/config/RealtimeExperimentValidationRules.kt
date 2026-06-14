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
    private val distributions = setOf("poisson", "uniform", "burst")

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
        if (arrival.burstIntensity <= 0.0) {
            context.addError("realtime.arrival.burstIntensity", arrival.burstIntensity, "突发强度必须大于0")
        }
        if (arrival.burstDuration <= 0.0) {
            context.addError("realtime.arrival.burstDuration", arrival.burstDuration, "突发持续时间必须大于0")
        }
    }
}
