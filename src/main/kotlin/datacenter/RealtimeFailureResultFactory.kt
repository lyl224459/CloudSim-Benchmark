package datacenter

internal object RealtimeFailureResultFactory {
    fun from(
        algorithmName: String,
        run: Int,
        throwable: Throwable,
    ): RealtimeRunOutcome.Failed {
        val errorType = throwable::class.simpleName ?: throwable::class.java.simpleName
        val message = throwable.message?.takeIf { it.isNotBlank() } ?: "No error message"
        return RealtimeRunOutcome.Failed(
            algorithmName = algorithmName,
            run = run,
            errorType = errorType,
            errorMessage = message,
        )
    }
}
