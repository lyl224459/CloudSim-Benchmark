package util

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class ExperimentConcurrencyTest {
    @Test
    fun `max concurrency one serializes mapped work`() = runBlocking {
        val concurrency = ExperimentConcurrency(useCoroutines = true, maxConcurrency = 1)
        val active = AtomicInteger(0)
        val maxObserved = AtomicInteger(0)

        concurrency.map(1..20) {
            val current = active.incrementAndGet()
            maxObserved.updateAndGet { existing -> maxOf(existing, current) }
            delay(5)
            active.decrementAndGet()
        }

        assertThat(maxObserved.get()).isEqualTo(1)
    }

    @Test
    fun `sequential mode does not dispatch mapped work concurrently`() = runBlocking {
        val concurrency = ExperimentConcurrency(useCoroutines = false, maxConcurrency = 8)
        val order = mutableListOf<Int>()

        concurrency.map(1..5) {
            order.add(it)
        }

        assertThat(order).containsExactly(1, 2, 3, 4, 5)
    }
}
