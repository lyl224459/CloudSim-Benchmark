package util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class ExperimentConcurrency(
    private val useCoroutines: Boolean = true,
    private val maxConcurrency: Int = 0,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher: CoroutineContext =
        if (maxConcurrency > 0) Dispatchers.Default.limitedParallelism(maxConcurrency) else Dispatchers.Default
    private val semaphore: Semaphore? = maxConcurrency.takeIf { it > 0 }?.let(::Semaphore)

    val description: String
        get() =
            when {
                !useCoroutines -> "顺序执行"
                maxConcurrency > 0 -> "协程并行（最大并发 $maxConcurrency）"
                else -> "协程并行（默认调度器）"
            }

    suspend fun <T, R> map(
        items: Iterable<T>,
        block: suspend (T) -> R,
    ): List<R> {
        val itemList = items.toList()
        if (!useCoroutines || itemList.size <= 1) {
            return itemList.map { block(it) }
        }

        return coroutineScope {
            itemList
                .map { item ->
                    async(dispatcher) {
                        semaphore?.withPermit { block(item) } ?: block(item)
                    }
                }.awaitAll()
        }
    }

    suspend fun <T> run(block: suspend () -> T): T {
        if (!useCoroutines) {
            return block()
        }
        return withContext(dispatcher) { block() }
    }
}
