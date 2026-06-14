package broker

import config.RealtimeQueuePolicy
import config.RealtimeSchedulingConfig
import org.cloudsimplus.cloudlets.Cloudlet

internal class RealtimeCloudletOrdering(
    private val scheduling: RealtimeSchedulingConfig,
    private val lifecycleStore: RealtimeTaskLifecycleStore,
) {
    fun arrivalComparator(): Comparator<Cloudlet> {
        val base = compareBy<Cloudlet> { it.submissionDelay }
        return when (scheduling.normalizedQueuePolicy()) {
            RealtimeQueuePolicy.PRIORITY ->
                base
                    .thenBy { lifecycleStore.get(it.id)?.priority ?: Int.MAX_VALUE }
                    .thenBy { it.id }
            RealtimeQueuePolicy.DEADLINE ->
                base
                    .thenBy { lifecycleStore.get(it.id)?.deadline ?: Double.POSITIVE_INFINITY }
                    .thenBy { it.id }
            RealtimeQueuePolicy.FIFO -> base.thenBy { it.id }
        }
    }
}
