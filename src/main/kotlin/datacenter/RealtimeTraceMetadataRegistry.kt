package datacenter

import org.cloudsimplus.cloudlets.Cloudlet
import java.util.Collections
import java.util.WeakHashMap

data class RealtimeTraceMetadata(
    val tenantKey: String? = null,
    val tenantId: Int? = null,
    val priority: Int? = null,
    val deadline: Double? = null,
    val requestedCpu: Double? = null,
    val requestedRam: Double? = null,
    val requestedBw: Double? = null,
    val requestedIo: Double? = null,
    val dataRegion: Int? = null,
    val inputDataSize: Double? = null,
    val imageId: String? = null,
    val imageSize: Double? = null,
    val retryHint: Int? = null
)

object RealtimeTraceMetadataRegistry {
    private val metadataByCloudlet = Collections.synchronizedMap(WeakHashMap<Cloudlet, RealtimeTraceMetadata>())

    fun put(cloudlet: Cloudlet, metadata: RealtimeTraceMetadata) {
        metadataByCloudlet[cloudlet] = metadata
    }

    fun get(cloudlet: Cloudlet): RealtimeTraceMetadata? =
        metadataByCloudlet[cloudlet]

    fun clear() {
        metadataByCloudlet.clear()
    }
}
