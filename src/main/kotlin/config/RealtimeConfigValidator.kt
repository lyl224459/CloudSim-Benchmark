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
        validateCloudletCounts(realtime.cloudletCounts, context)
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
