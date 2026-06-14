package config

internal fun MutableList<ValidationError>.addError(
    field: String,
    value: Any,
    message: String,
) {
    this += ValidationError(field, value.toString(), message)
}
