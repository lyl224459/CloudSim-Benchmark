package datacenter

import config.ObjectiveWeightsConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import scheduler.AlgorithmRegistry
import scheduler.RealtimeScheduler
import scheduler.ResolvedAlgorithmSettings
import java.io.File
import java.time.Instant
import java.util.Random

private const val SMALL_BENCHMARK_CLOUDLETS = 100
private const val MEDIUM_BENCHMARK_CLOUDLETS = 1_000
private const val LARGE_BENCHMARK_CLOUDLETS = 10_000
private const val NANOS_PER_MILLISECOND = 1_000_000L
private val DEFAULT_BENCHMARK_CLOUDLET_COUNTS =
    listOf(
        SMALL_BENCHMARK_CLOUDLETS,
        MEDIUM_BENCHMARK_CLOUDLETS,
        LARGE_BENCHMARK_CLOUDLETS,
    )

@Serializable
data class RealtimePerformanceBenchmarkConfig(
    val cloudletCounts: List<Int> = DEFAULT_BENCHMARK_CLOUDLET_COUNTS,
    val algorithms: List<RealtimePerformanceBenchmarkAlgorithm> = RealtimePerformanceBenchmarkAlgorithm.entries,
    val measuredRuns: Int = 3,
    val randomSeed: Long = 0L,
    val population: Int = 10,
    val maxIter: Int = 10,
    val outputFile: String = "build/reports/realtime-performance/benchmark-results.json",
)

@Serializable
enum class RealtimePerformanceBenchmarkAlgorithm(
    val mode: RealtimePerformanceBenchmarkMode,
    val registryName: String,
    val displayName: String,
) {
    PSO(RealtimePerformanceBenchmarkMode.BATCH, "PSO", "PSO"),
    WOA(RealtimePerformanceBenchmarkMode.BATCH, "WOA", "WOA"),
    GWO(RealtimePerformanceBenchmarkMode.BATCH, "GWO", "GWO"),
    HHO(RealtimePerformanceBenchmarkMode.BATCH, "HHO", "HHO"),
    REALTIME_MIN_LOAD(RealtimePerformanceBenchmarkMode.REALTIME, "MIN_LOAD", "Realtime MinLoad"),
    ;

    companion object {
        fun parseList(value: String): List<RealtimePerformanceBenchmarkAlgorithm> {
            if (value.isBlank()) return entries
            return value
                .split(",")
                .map { it.trim().replace("-", "_").uppercase() }
                .map { normalized ->
                    entries.firstOrNull { it.name == normalized || it.registryName == normalized }
                        ?: throw IllegalArgumentException("Unknown benchmark algorithm: $normalized")
                }.distinct()
        }
    }
}

@Serializable
enum class RealtimePerformanceBenchmarkMode {
    BATCH,
    REALTIME,
}

@Serializable
enum class RealtimePerformanceBenchmarkStatus {
    SUCCESS,
    FAILED,
}

@Serializable
data class RealtimePerformanceBenchmarkResult(
    val algorithm: String,
    val mode: RealtimePerformanceBenchmarkMode,
    val cloudletCount: Int,
    val runIndex: Int,
    val status: RealtimePerformanceBenchmarkStatus,
    val elapsedMillis: Long,
    val memoryDeltaBytes: Long,
    val errorType: String = "",
    val errorMessage: String = "",
)

@Serializable
data class RealtimePerformanceBenchmarkReport(
    val generatedAt: String,
    val config: RealtimePerformanceBenchmarkConfig,
    val results: List<RealtimePerformanceBenchmarkResult>,
)

class RealtimePerformanceBenchmarkRunner(
    private val config: RealtimePerformanceBenchmarkConfig,
) {
    fun run(): RealtimePerformanceBenchmarkReport {
        val results =
            buildList {
                config.cloudletCounts.forEach { cloudletCount ->
                    config.algorithms.forEach { algorithm ->
                        repeat(config.measuredRuns.coerceAtLeast(1)) { runIndex ->
                            add(measure(algorithm, cloudletCount, runIndex + 1))
                        }
                    }
                }
            }
        return RealtimePerformanceBenchmarkReport(
            generatedAt = Instant.now().toString(),
            config = config,
            results = results,
        )
    }

    fun write(report: RealtimePerformanceBenchmarkReport) {
        write(report, File(config.outputFile))
    }

    fun write(
        report: RealtimePerformanceBenchmarkReport,
        outputFile: File,
    ) {
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(json.encodeToString(report))
    }

    private fun measure(
        algorithm: RealtimePerformanceBenchmarkAlgorithm,
        cloudletCount: Int,
        runIndex: Int,
    ): RealtimePerformanceBenchmarkResult {
        val seed = config.randomSeed + cloudletCount + runIndex
        requestMemoryBaseline()
        val before = usedMemoryBytes()
        val started = System.nanoTime()
        val outcome =
            runCatching {
                when (algorithm.mode) {
                    RealtimePerformanceBenchmarkMode.BATCH -> runBatchBenchmark(algorithm, cloudletCount, seed)
                    RealtimePerformanceBenchmarkMode.REALTIME -> runRealtimeBenchmark(algorithm, cloudletCount, seed)
                }
            }
        val elapsedMs = (System.nanoTime() - started) / NANOS_PER_MILLISECOND
        val after = usedMemoryBytes()
        val memoryDeltaBytes = (after - before).coerceAtLeast(0L)

        return outcome.fold(
            onSuccess = {
                RealtimePerformanceBenchmarkResult(
                    algorithm = algorithm.displayName,
                    mode = algorithm.mode,
                    cloudletCount = cloudletCount,
                    runIndex = runIndex,
                    status = RealtimePerformanceBenchmarkStatus.SUCCESS,
                    elapsedMillis = elapsedMs,
                    memoryDeltaBytes = memoryDeltaBytes,
                )
            },
            onFailure = { error ->
                RealtimePerformanceBenchmarkResult(
                    algorithm = algorithm.displayName,
                    mode = algorithm.mode,
                    cloudletCount = cloudletCount,
                    runIndex = runIndex,
                    status = RealtimePerformanceBenchmarkStatus.FAILED,
                    elapsedMillis = elapsedMs,
                    memoryDeltaBytes = memoryDeltaBytes,
                    errorType = error::class.simpleName ?: error::class.java.simpleName,
                    errorMessage = error.message.orEmpty(),
                )
            },
        )
    }

    private fun runBatchBenchmark(
        algorithm: RealtimePerformanceBenchmarkAlgorithm,
        cloudletCount: Int,
        seed: Long,
    ) {
        val cloudlets = CloudletGenerator(Random(seed)).createCloudlets(userId = 0, count = cloudletCount)
        val vms = DatacenterCreator.createVms()
        val definition = AlgorithmRegistry.resolveBatch(algorithm.registryName)
        val scheduler =
            definition.createBatchScheduler(
                cloudlets,
                vms,
                ObjectiveWeightsConfig(),
                ResolvedAlgorithmSettings(config.population, config.maxIter),
                seed,
            )
        scheduler.schedule()
    }

    private fun runRealtimeBenchmark(
        algorithm: RealtimePerformanceBenchmarkAlgorithm,
        cloudletCount: Int,
        seed: Long,
    ) {
        val vms = DatacenterCreator.createVms()
        val scheduler =
            AlgorithmRegistry
                .resolveRealtime(algorithm.registryName)
                .createRealtimeScheduler(
                    vms,
                    ObjectiveWeightsConfig(),
                    ResolvedAlgorithmSettings(config.population, config.maxIter),
                    seed,
                )
        val cloudlets =
            RealtimeCloudletGenerator(
                random = Random(seed),
                arrivalRate = cloudletCount.toDouble().coerceAtLeast(1.0),
            ).createRealtimeCloudletBatch(userId = 0, count = cloudletCount, simulationDuration = 1.0).cloudlets

        val assignedCloudlets = ArrayList<Cloudlet>(cloudlets.size)
        cloudlets.forEach { cloudlet ->
            val vmIndex = scheduler.safeSchedule(cloudlet, assignedCloudlets, vms)
            cloudlet.setVm(vms[vmIndex])
            assignedCloudlets.add(cloudlet)
        }
    }

    private fun RealtimeScheduler.safeSchedule(
        cloudlet: Cloudlet,
        assignedCloudlets: List<Cloudlet>,
        vms: List<Vm>,
    ): Int = scheduleOnArrival(cloudlet, assignedCloudlets, vms).coerceIn(vms.indices)

    private fun usedMemoryBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    @Suppress("ExplicitGarbageCollectionCall")
    private fun requestMemoryBaseline() {
        // This smoke benchmark records memory delta, so it keeps the previous explicit GC behavior.
        Runtime.getRuntime().gc()
    }

    companion object {
        internal val json =
            Json {
                prettyPrint = true
                encodeDefaults = true
            }
    }
}

fun main(args: Array<String>) {
    val options = args.toBenchmarkOptions()
    val config = options.toBenchmarkConfig()
    val runner = RealtimePerformanceBenchmarkRunner(config)
    val report = runner.run()
    runner.write(report)
    println("Realtime performance benchmark written to ${File(config.outputFile).absolutePath}")
}

private fun Array<String>.toBenchmarkOptions(): Map<String, String> =
    asList().chunked(2).associate { chunk ->
        require(chunk.size == 2 && chunk[0].startsWith("--")) {
            "Invalid benchmark argument list: ${joinToString(" ")}"
        }
        chunk[0].removePrefix("--") to chunk[1]
    }

private fun Map<String, String>.toBenchmarkConfig(): RealtimePerformanceBenchmarkConfig =
    RealtimePerformanceBenchmarkConfig(
        cloudletCounts = this["sizes"]?.toIntList() ?: DEFAULT_BENCHMARK_CLOUDLET_COUNTS,
        algorithms =
            this["algorithms"]?.let(RealtimePerformanceBenchmarkAlgorithm::parseList)
                ?: RealtimePerformanceBenchmarkAlgorithm.entries,
        measuredRuns = this["runs"]?.toIntOrNull()?.coerceAtLeast(1) ?: 3,
        randomSeed = this["seed"]?.toLongOrNull() ?: 0L,
        population = this["population"]?.toIntOrNull()?.coerceAtLeast(1) ?: 10,
        maxIter = this["maxIter"]?.toIntOrNull()?.coerceAtLeast(1) ?: 10,
        outputFile = this["output"] ?: "build/reports/realtime-performance/benchmark-results.json",
    )

private fun String.toIntList(): List<Int> =
    split(",")
        .mapNotNull { it.trim().toIntOrNull()?.takeIf { value -> value > 0 } }
        .ifEmpty { DEFAULT_BENCHMARK_CLOUDLET_COUNTS }
