package scheduler

import config.BatchAlgorithmType
import config.ObjectiveWeightsConfig
import config.RealtimeAlgorithmType
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import java.util.Random

enum class AlgorithmMode {
    BATCH,
    REALTIME,
}

data class ResolvedAlgorithmSettings(
    val population: Int,
    val maxIter: Int,
)

typealias BatchSchedulerFactory = (
    cloudlets: List<Cloudlet>,
    vms: List<Vm>,
    objectiveWeights: ObjectiveWeightsConfig,
    settings: ResolvedAlgorithmSettings,
    seed: Long,
) -> Scheduler

typealias RealtimeSchedulerFactory = (
    vms: List<Vm>,
    objectiveWeights: ObjectiveWeightsConfig,
    settings: ResolvedAlgorithmSettings,
    seed: Long,
) -> RealtimeScheduler

data class AlgorithmMetadata(
    val name: String,
    val displayName: String,
    val aliases: Set<String> = emptySet(),
    val defaultEnabled: Boolean = true,
    val supportsPopulation: Boolean = false,
    val supportsMaxIter: Boolean = false,
)

sealed class AlgorithmDefinition(
    private val metadata: AlgorithmMetadata,
    val mode: AlgorithmMode,
) {
    val name: String get() = metadata.name
    val displayName: String get() = metadata.displayName
    val aliases: Set<String> get() = metadata.aliases
    val defaultEnabled: Boolean get() = metadata.defaultEnabled
    val supportsPopulation: Boolean get() = metadata.supportsPopulation
    val supportsMaxIter: Boolean get() = metadata.supportsMaxIter

    open val legacyBatchType: BatchAlgorithmType? = null
    open val legacyRealtimeType: RealtimeAlgorithmType? = null

    init {
        require(name == normalizeName(name)) { "Algorithm canonical name must already be normalized: $name" }
    }

    fun matches(candidate: String): Boolean {
        val normalized = normalizeName(candidate)
        return normalized == name || aliases.any { normalizeName(it) == normalized }
    }
}

class BatchAlgorithmDefinition(
    metadata: AlgorithmMetadata,
    override val legacyBatchType: BatchAlgorithmType,
    private val factory: BatchSchedulerFactory,
) : AlgorithmDefinition(
        metadata = metadata,
        mode = AlgorithmMode.BATCH,
    ) {
    fun createBatchScheduler(
        cloudlets: List<Cloudlet>,
        vms: List<Vm>,
        objectiveWeights: ObjectiveWeightsConfig,
        settings: ResolvedAlgorithmSettings,
        seed: Long,
    ): Scheduler = factory(cloudlets, vms, objectiveWeights, settings, seed)
}

class RealtimeAlgorithmDefinition(
    metadata: AlgorithmMetadata,
    override val legacyRealtimeType: RealtimeAlgorithmType,
    private val factory: RealtimeSchedulerFactory,
) : AlgorithmDefinition(
        metadata = metadata,
        mode = AlgorithmMode.REALTIME,
    ) {
    fun createRealtimeScheduler(
        vms: List<Vm>,
        objectiveWeights: ObjectiveWeightsConfig,
        settings: ResolvedAlgorithmSettings,
        seed: Long,
    ): RealtimeScheduler = factory(vms, objectiveWeights, settings, seed)
}

data class ResolvedAlgorithm(
    val definition: AlgorithmDefinition,
    val settings: ResolvedAlgorithmSettings,
) {
    val name: String get() = definition.name
    val displayName: String get() = definition.displayName

    fun createBatchScheduler(
        cloudlets: List<Cloudlet>,
        vms: List<Vm>,
        objectiveWeights: ObjectiveWeightsConfig,
        seed: Long,
    ): Scheduler =
        batchDefinition().createBatchScheduler(
            cloudlets = cloudlets,
            vms = vms,
            objectiveWeights = objectiveWeights,
            settings = settings,
            seed = seed,
        )

    fun createRealtimeScheduler(
        vms: List<Vm>,
        objectiveWeights: ObjectiveWeightsConfig,
        seed: Long,
    ): RealtimeScheduler =
        realtimeDefinition().createRealtimeScheduler(
            vms = vms,
            objectiveWeights = objectiveWeights,
            settings = settings,
            seed = seed,
        )

    private fun batchDefinition(): BatchAlgorithmDefinition =
        definition as? BatchAlgorithmDefinition
            ?: throw IllegalArgumentException("算法 ${definition.name} 不是批处理算法")

    private fun realtimeDefinition(): RealtimeAlgorithmDefinition =
        definition as? RealtimeAlgorithmDefinition
            ?: throw IllegalArgumentException("算法 ${definition.name} 不是实时调度算法")
}

object AlgorithmRegistry {
    private val definitions =
        listOf(
            BatchAlgorithmDefinition(
                metadata = AlgorithmMetadata(name = "RANDOM", displayName = "Random", aliases = setOf("RAND")),
                legacyBatchType = BatchAlgorithmType.RANDOM,
                factory = { cloudlets, vms, objectiveWeights, _, seed ->
                    RandomScheduler(cloudlets, vms, objectiveWeights, Random(seed))
                },
            ),
            BatchAlgorithmDefinition(
                metadata = optimizerMetadata("PSO", "PSO"),
                legacyBatchType = BatchAlgorithmType.PSO,
                factory = { cloudlets, vms, objectiveWeights, settings, seed ->
                    PSOScheduler(cloudlets, vms, objectiveWeights, settings.population, settings.maxIter, Random(seed))
                },
            ),
            BatchAlgorithmDefinition(
                metadata = optimizerMetadata("WOA", "WOA"),
                legacyBatchType = BatchAlgorithmType.WOA,
                factory = { cloudlets, vms, objectiveWeights, settings, seed ->
                    WOAScheduler(cloudlets, vms, objectiveWeights, settings.population, settings.maxIter, Random(seed))
                },
            ),
            BatchAlgorithmDefinition(
                metadata = optimizerMetadata("GWO", "GWO"),
                legacyBatchType = BatchAlgorithmType.GWO,
                factory = { cloudlets, vms, objectiveWeights, settings, seed ->
                    GWOScheduler(cloudlets, vms, objectiveWeights, settings.population, settings.maxIter, Random(seed))
                },
            ),
            BatchAlgorithmDefinition(
                metadata = optimizerMetadata("HHO", "HHO"),
                legacyBatchType = BatchAlgorithmType.HHO,
                factory = { cloudlets, vms, objectiveWeights, settings, seed ->
                    HHOScheduler(cloudlets, vms, objectiveWeights, settings.population, settings.maxIter, Random(seed))
                },
            ),
            BatchAlgorithmDefinition(
                metadata = AlgorithmMetadata(name = "RL", displayName = "RL"),
                legacyBatchType = BatchAlgorithmType.RL,
                factory = { cloudlets, vms, objectiveWeights, _, seed ->
                    RLScheduler(cloudlets, vms, objectiveWeights, random = kotlin.random.Random(seed))
                },
            ),
            BatchAlgorithmDefinition(
                metadata =
                    AlgorithmMetadata(
                        name = "IMPROVED_RL",
                        displayName = "Improved-RL",
                        aliases = setOf("Improved-RL", "Improved RL"),
                    ),
                legacyBatchType = BatchAlgorithmType.IMPROVED_RL,
                factory = { cloudlets, vms, objectiveWeights, _, seed ->
                    ImprovedRLScheduler(cloudlets, vms, objectiveWeights, random = kotlin.random.Random(seed))
                },
            ),
            RealtimeAlgorithmDefinition(
                metadata =
                    AlgorithmMetadata(
                        name = "MIN_LOAD",
                        displayName = "MinLoad",
                        aliases = setOf("MINLOAD", "MinLoad", "MIN-LOAD"),
                    ),
                legacyRealtimeType = RealtimeAlgorithmType.MIN_LOAD,
                factory = { vms, _, _, _ -> RealtimeMinLoadScheduler(vms) },
            ),
            RealtimeAlgorithmDefinition(
                metadata = AlgorithmMetadata(name = "RANDOM", displayName = "Random", aliases = setOf("RAND")),
                legacyRealtimeType = RealtimeAlgorithmType.RANDOM,
                factory = { vms, _, _, seed -> RealtimeRandomScheduler(vms, Random(seed)) },
            ),
            RealtimeAlgorithmDefinition(
                metadata =
                    optimizerMetadata(
                        name = "PSO_REALTIME",
                        displayName = "PSO-Realtime",
                        aliases = setOf("PSO", "PSO-Realtime", "PSO Realtime"),
                    ),
                legacyRealtimeType = RealtimeAlgorithmType.PSO_REALTIME,
                factory = { vms, objectiveWeights, settings, seed ->
                    RealtimePSOScheduler(vms, settings.population, settings.maxIter, objectiveWeights, Random(seed))
                },
            ),
            RealtimeAlgorithmDefinition(
                metadata =
                    optimizerMetadata(
                        name = "WOA_REALTIME",
                        displayName = "WOA-Realtime",
                        aliases = setOf("WOA", "WOA-Realtime", "WOA Realtime"),
                    ),
                legacyRealtimeType = RealtimeAlgorithmType.WOA_REALTIME,
                factory = { vms, objectiveWeights, settings, seed ->
                    RealtimeWOAScheduler(vms, settings.population, settings.maxIter, objectiveWeights, Random(seed))
                },
            ),
        )

    private val definitionsByMode: Map<AlgorithmMode, List<AlgorithmDefinition>> =
        definitions.groupBy { it.mode }

    private val lookupByMode: Map<AlgorithmMode, Map<String, AlgorithmDefinition>> =
        definitionsByMode.mapValues { (mode, modeDefinitions) ->
            val lookup = linkedMapOf<String, AlgorithmDefinition>()
            for (definition in modeDefinitions) {
                registerLookupName(lookup, mode, definition.name, definition)
                for (alias in definition.aliases) {
                    registerLookupName(lookup, mode, alias, definition)
                }
            }
            lookup.toMap()
        }

    fun all(): List<AlgorithmDefinition> = definitions.toList()

    fun forMode(mode: AlgorithmMode): List<AlgorithmDefinition> = definitionsByMode[mode].orEmpty()

    fun resolveBatch(name: String): BatchAlgorithmDefinition {
        val definition = resolve(AlgorithmMode.BATCH, name)
        return definition as BatchAlgorithmDefinition
    }

    fun resolveRealtime(name: String): RealtimeAlgorithmDefinition {
        val definition = resolve(AlgorithmMode.REALTIME, name)
        return definition as RealtimeAlgorithmDefinition
    }

    fun resolve(
        mode: AlgorithmMode,
        name: String,
    ): AlgorithmDefinition {
        val match = lookupByMode[mode].orEmpty()[normalizeName(name)]
        return match ?: throw IllegalArgumentException(
            "未知的${mode.displayLabel()}算法: $name。可用算法: ${forMode(mode).joinToString(", ") { it.name }}",
        )
    }

    fun resolveAll(
        mode: AlgorithmMode,
        names: List<String>,
    ): List<AlgorithmDefinition> {
        if (names.size == 1 && names[0].equals("ALL", ignoreCase = true)) {
            return forMode(mode).filter { it.defaultEnabled }
        }
        if (names.any { it.equals("ALL", ignoreCase = true) }) {
            throw IllegalArgumentException("ALL 不能与其他算法名混用")
        }

        return names
            .map { resolve(mode, it) }
            .distinctBy { it.name }
    }

    fun isCompatible(
        mode: AlgorithmMode,
        name: String,
    ): Boolean = runCatching { resolve(mode, name) }.isSuccess

    private fun registerLookupName(
        lookup: MutableMap<String, AlgorithmDefinition>,
        mode: AlgorithmMode,
        candidate: String,
        definition: AlgorithmDefinition,
    ) {
        val normalized = normalizeName(candidate)
        val existing = lookup[normalized]
        require(existing == null || existing.name == definition.name) {
            "算法名称或别名冲突: $normalized (${mode.displayLabel()}) 同时指向 ${existing?.name} 和 ${definition.name}"
        }
        lookup[normalized] = definition
    }
}

fun normalizeName(name: String): String =
    name
        .trim()
        .replace("-", "_")
        .replace(" ", "_")
        .uppercase()

private fun optimizerMetadata(
    name: String,
    displayName: String,
    aliases: Set<String> = emptySet(),
): AlgorithmMetadata =
    AlgorithmMetadata(
        name = name,
        displayName = displayName,
        aliases = aliases,
        supportsPopulation = true,
        supportsMaxIter = true,
    )

private fun AlgorithmMode.displayLabel(): String =
    when (this) {
        AlgorithmMode.BATCH -> "批处理"
        AlgorithmMode.REALTIME -> "实时调度"
    }
