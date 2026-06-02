package datacenter

import org.cloudsimplus.cloudlets.Cloudlet

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
    val retryHint: Int? = null,
)

data class RealtimeCloudletSpec(
    val cloudlet: Cloudlet,
    val traceMetadata: RealtimeTraceMetadata? = null,
)

data class RealtimeCloudletBatch(
    val specs: List<RealtimeCloudletSpec>,
) {
    val cloudlets: List<Cloudlet> = specs.map { it.cloudlet }

    fun metadataProvider(): RealtimeTraceMetadataProvider =
        MapBackedRealtimeTraceMetadataProvider(
            specs
                .mapNotNull { spec ->
                    spec.traceMetadata?.let { spec.cloudlet.id to it }
                }.toMap(),
        )
}

fun interface RealtimeTraceMetadataProvider {
    fun metadataFor(cloudlet: Cloudlet): RealtimeTraceMetadata?

    companion object {
        val Empty: RealtimeTraceMetadataProvider = RealtimeTraceMetadataProvider { null }

        fun fromSpecs(specs: Iterable<RealtimeCloudletSpec>): RealtimeTraceMetadataProvider =
            MapBackedRealtimeTraceMetadataProvider(
                specs
                    .mapNotNull { spec ->
                        spec.traceMetadata?.let { spec.cloudlet.id to it }
                    }.toMap(),
            )
    }
}

class MapBackedRealtimeTraceMetadataProvider(
    metadataByCloudletId: Map<Long, RealtimeTraceMetadata>,
) : RealtimeTraceMetadataProvider {
    private val metadataByCloudletId = metadataByCloudletId.toMap()

    override fun metadataFor(cloudlet: Cloudlet): RealtimeTraceMetadata? = metadataByCloudletId[cloudlet.id]
}

class MutableRealtimeTraceMetadataProvider : RealtimeTraceMetadataProvider {
    private val metadataByCloudletId = linkedMapOf<Long, RealtimeTraceMetadata>()

    fun put(
        cloudlet: Cloudlet,
        metadata: RealtimeTraceMetadata,
    ) {
        metadataByCloudletId[cloudlet.id] = metadata
    }

    fun put(spec: RealtimeCloudletSpec) {
        spec.traceMetadata?.let { put(spec.cloudlet, it) }
    }

    fun putAll(specs: Iterable<RealtimeCloudletSpec>) {
        specs.forEach(::put)
    }

    override fun metadataFor(cloudlet: Cloudlet): RealtimeTraceMetadata? = metadataByCloudletId[cloudlet.id]

    fun snapshot(): Map<Long, RealtimeTraceMetadata> = metadataByCloudletId.toMap()
}
