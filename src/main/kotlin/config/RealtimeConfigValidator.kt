package config

internal object RealtimeConfigValidator {
    fun validate(
        realtime: RealtimeConfig,
        errors: MutableList<ValidationError>,
    ) {
        val context = RealtimeValidationContext(errors)
        RealtimeExperimentShapeValidator.validate(realtime, context)
        RealtimeArrivalValidator.validate(realtime.arrival, context)
        RealtimeSchedulingValidator.validate(realtime.scheduling, context)
        realtime.googleTraceConfig?.let { validateGoogleTrace(it, context) }
        validateCloudletCounts(realtime.cloudletCounts, context)
    }

    private fun validateGoogleTrace(
        googleTrace: GoogleTraceConfig,
        context: RealtimeValidationContext,
    ) {
        if (googleTrace.timestampDivisor <= 0.0) {
            context.addError(
                "realtime.googleTrace.timestampDivisor",
                googleTrace.timestampDivisor,
                "Google Trace timestampDivisor 必须大于0",
            )
        }
    }

    private fun validateCloudletCounts(
        cloudletCounts: List<Int>,
        context: RealtimeValidationContext,
    ) {
        cloudletCounts.forEachIndexed { index, count ->
            if (count <= 0) {
                context.addError("realtime.cloudletCounts[$index]", count, "批量任务数必须大于0")
            }
        }
    }
}
