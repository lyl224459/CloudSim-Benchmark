package config

internal class RealtimeValidationContext(
    private val errors: MutableList<ValidationError>,
) {
    fun addError(
        field: String,
        value: Any,
        message: String,
    ) {
        errors.addError(field, value, message)
    }
}

internal fun nonNegative(
    context: RealtimeValidationContext,
    field: String,
    value: Double,
    message: String,
) {
    if (value < 0.0) {
        context.addError(field, value, message)
    }
}

internal fun boundedUnit(
    context: RealtimeValidationContext,
    field: String,
    value: Double,
    message: String,
) {
    if (value < 0.0 || value > 1.0) {
        context.addError(field, value, message)
    }
}

internal fun enumValue(
    context: RealtimeValidationContext,
    field: String,
    value: String,
    allowedValues: Set<String>,
    label: String,
) {
    if (value.lowercase() !in allowedValues) {
        context.addError(field, value, "$label 必须是以下值之一: ${allowedValues.joinToString(", ")}")
    }
}
