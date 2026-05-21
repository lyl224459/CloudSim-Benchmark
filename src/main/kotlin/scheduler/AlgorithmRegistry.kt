package scheduler

import config.BatchAlgorithmType
import config.ObjectiveWeightsConfig
import config.RealtimeAlgorithmType
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import java.util.Random

enum class AlgorithmMode {
    BATCH,
    REALTIME
}

data class ResolvedAlgorithmSettings(
    val population: Int,
    val maxIter: Int
)

typealias BatchSchedulerFactory = (
    cloudlets: List<Cloudlet>,
    vms: List<Vm>,
    objectiveWeights: ObjectiveWeightsConfig,
    settings: ResolvedAlgorithmSettings,
    seed: Long
) -> Scheduler

typealias RealtimeSchedulerFactory = (
    vms: List<Vm>,
    objectiveWeights: ObjectiveWeightsConfig,
    settings: ResolvedAlgorithmSettings,
    seed: Long
) -> RealtimeScheduler

data class AlgorithmDefinition(
    val name: String,
    val mode: AlgorithmMode,
    val displayName: String,
    val aliases: Set<String> = emptySet(),
    val defaultEnabled: Boolean = true,
    val supportsPopulation: Boolean = false,
    val supportsMaxIter: Boolean = false,
    val legacyBatchType: BatchAlgorithmType? = null,
    val legacyRealtimeType: RealtimeAlgorithmType? = null,
    val batchFactory: BatchSchedulerFactory? = null,
    val realtimeFactory: RealtimeSchedulerFactory? = null
) {
    init {
        require(name == normalizeName(name)) { "Algorithm canonical name must already be normalized: $name" }
        require((mode == AlgorithmMode.BATCH) == (batchFactory != null)) {
            "Batch algorithms must provide only a batch factory: $name"
        }
        require((mode == AlgorithmMode.REALTIME) == (realtimeFactory != null)) {
            "Realtime algorithms must provide only a realtime factory: $name"
        }
    }

    fun matches(candidate: String): Boolean {
        val normalized = normalizeName(candidate)
        return normalized == name || aliases.any { normalizeName(it) == normalized }
    }
}

data class ResolvedAlgorithm(
    val definition: AlgorithmDefinition,
    val settings: ResolvedAlgorithmSettings
) {
    val name: String get() = definition.name
    val displayName: String get() = definition.displayName
}

object AlgorithmRegistry {
    private val definitions = listOf(
        AlgorithmDefinition(
            name = "RANDOM",
            mode = AlgorithmMode.BATCH,
            displayName = "Random",
            aliases = setOf("RAND"),
            legacyBatchType = BatchAlgorithmType.RANDOM,
            batchFactory = { cloudlets, vms, objectiveWeights, _, seed ->
                RandomScheduler(cloudlets, vms, objectiveWeights, Random(seed))
            }
        ),
        AlgorithmDefinition(
            name = "PSO",
            mode = AlgorithmMode.BATCH,
            displayName = "PSO",
            supportsPopulation = true,
            supportsMaxIter = true,
            legacyBatchType = BatchAlgorithmType.PSO,
            batchFactory = { cloudlets, vms, objectiveWeights, settings, seed ->
                PSOScheduler(cloudlets, vms, objectiveWeights, settings.population, settings.maxIter, Random(seed))
            }
        ),
        AlgorithmDefinition(
            name = "WOA",
            mode = AlgorithmMode.BATCH,
            displayName = "WOA",
            supportsPopulation = true,
            supportsMaxIter = true,
            legacyBatchType = BatchAlgorithmType.WOA,
            batchFactory = { cloudlets, vms, objectiveWeights, settings, seed ->
                WOAScheduler(cloudlets, vms, objectiveWeights, settings.population, settings.maxIter, Random(seed))
            }
        ),
        AlgorithmDefinition(
            name = "GWO",
            mode = AlgorithmMode.BATCH,
            displayName = "GWO",
            supportsPopulation = true,
            supportsMaxIter = true,
            legacyBatchType = BatchAlgorithmType.GWO,
            batchFactory = { cloudlets, vms, objectiveWeights, settings, seed ->
                GWOScheduler(cloudlets, vms, objectiveWeights, settings.population, settings.maxIter, Random(seed))
            }
        ),
        AlgorithmDefinition(
            name = "HHO",
            mode = AlgorithmMode.BATCH,
            displayName = "HHO",
            supportsPopulation = true,
            supportsMaxIter = true,
            legacyBatchType = BatchAlgorithmType.HHO,
            batchFactory = { cloudlets, vms, objectiveWeights, settings, seed ->
                HHOScheduler(cloudlets, vms, objectiveWeights, settings.population, settings.maxIter, Random(seed))
            }
        ),
        AlgorithmDefinition(
            name = "RL",
            mode = AlgorithmMode.BATCH,
            displayName = "RL",
            legacyBatchType = BatchAlgorithmType.RL,
            batchFactory = { cloudlets, vms, objectiveWeights, _, seed ->
                RLScheduler(cloudlets, vms, objectiveWeights, random = kotlin.random.Random(seed))
            }
        ),
        AlgorithmDefinition(
            name = "IMPROVED_RL",
            mode = AlgorithmMode.BATCH,
            displayName = "Improved-RL",
            aliases = setOf("Improved-RL", "Improved RL"),
            legacyBatchType = BatchAlgorithmType.IMPROVED_RL,
            batchFactory = { cloudlets, vms, objectiveWeights, _, seed ->
                ImprovedRLScheduler(cloudlets, vms, objectiveWeights, random = kotlin.random.Random(seed))
            }
        ),
        AlgorithmDefinition(
            name = "MIN_LOAD",
            mode = AlgorithmMode.REALTIME,
            displayName = "MinLoad",
            aliases = setOf("MINLOAD", "MinLoad", "MIN-LOAD"),
            legacyRealtimeType = RealtimeAlgorithmType.MIN_LOAD,
            realtimeFactory = { vms, _, _, _ -> RealtimeMinLoadScheduler(vms) }
        ),
        AlgorithmDefinition(
            name = "RANDOM",
            mode = AlgorithmMode.REALTIME,
            displayName = "Random",
            aliases = setOf("RAND"),
            legacyRealtimeType = RealtimeAlgorithmType.RANDOM,
            realtimeFactory = { vms, _, _, seed -> RealtimeRandomScheduler(vms, Random(seed)) }
        ),
        AlgorithmDefinition(
            name = "PSO_REALTIME",
            mode = AlgorithmMode.REALTIME,
            displayName = "PSO-Realtime",
            aliases = setOf("PSO", "PSO-Realtime", "PSO Realtime"),
            supportsPopulation = true,
            supportsMaxIter = true,
            legacyRealtimeType = RealtimeAlgorithmType.PSO_REALTIME,
            realtimeFactory = { vms, objectiveWeights, settings, seed ->
                RealtimePSOScheduler(vms, settings.population, settings.maxIter, objectiveWeights, Random(seed))
            }
        ),
        AlgorithmDefinition(
            name = "WOA_REALTIME",
            mode = AlgorithmMode.REALTIME,
            displayName = "WOA-Realtime",
            aliases = setOf("WOA", "WOA-Realtime", "WOA Realtime"),
            supportsPopulation = true,
            supportsMaxIter = true,
            legacyRealtimeType = RealtimeAlgorithmType.WOA_REALTIME,
            realtimeFactory = { vms, objectiveWeights, settings, seed ->
                RealtimeWOAScheduler(vms, settings.population, settings.maxIter, objectiveWeights, Random(seed))
            }
        )
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

    fun forMode(mode: AlgorithmMode): List<AlgorithmDefinition> =
        definitionsByMode[mode].orEmpty()

    fun resolve(mode: AlgorithmMode, name: String): AlgorithmDefinition {
        val match = lookupByMode[mode].orEmpty()[normalizeName(name)]
        return match ?: throw IllegalArgumentException(
            "未知的${mode.displayLabel()}算法: $name。可用算法: ${forMode(mode).joinToString(", ") { it.name }}"
        )
    }

    fun resolveAll(mode: AlgorithmMode, names: List<String>): List<AlgorithmDefinition> {
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

    fun isCompatible(mode: AlgorithmMode, name: String): Boolean =
        runCatching { resolve(mode, name) }.isSuccess

    private fun registerLookupName(
        lookup: MutableMap<String, AlgorithmDefinition>,
        mode: AlgorithmMode,
        candidate: String,
        definition: AlgorithmDefinition
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
    name.trim().replace("-", "_").replace(" ", "_").uppercase()

private fun AlgorithmMode.displayLabel(): String =
    when (this) {
        AlgorithmMode.BATCH -> "批处理"
        AlgorithmMode.REALTIME -> "实时调度"
    }
