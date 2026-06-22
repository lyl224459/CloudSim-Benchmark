package cli

import config.ExperimentConfig
import config.ObjectiveWeightsConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File

private val dryRunPrettyJson = Json { prettyPrint = true }

internal fun renderResolvedJson(
    resolved: ResolvedExperimentConfig,
    experimentDir: File?,
    timestamp: String,
): String {
    val json =
        buildJsonObject {
            putResolvedMetadata(resolved, experimentDir, timestamp)
            putAlgorithmSettings(resolved)
            putBatchConfig(resolved.experimentConfig)
            putRealtimeConfig(resolved.experimentConfig)
            putCsvConfig(resolved)
            putIntArray("taskCounts", resolved.taskCounts)
        }
    return dryRunPrettyJson.encodeToString(JsonObject.serializer(), json)
}

private fun JsonObjectBuilder.putResolvedMetadata(
    resolved: ResolvedExperimentConfig,
    experimentDir: File?,
    timestamp: String,
) {
    put("mode", resolved.mode)
    put("profile", resolved.profileName ?: "")
    put("timestamp", timestamp)
    experimentDir?.let { put("experimentDir", it.absolutePath) }
    put("outputDir", resolved.output.resultsDir)
    put("randomSeed", resolved.randomSeed)
    put("runs", resolved.runs)
    put("preset", resolved.presetName ?: "")
    putStringArray("algorithms", resolved.selectedAlgorithmNames)
}

private fun JsonObjectBuilder.putAlgorithmSettings(resolved: ResolvedExperimentConfig) {
    putJsonObject("algorithmSettings") {
        resolved.algorithms.forEach { algorithm ->
            putJsonObject(algorithm.name) {
                put("population", algorithm.settings.population)
                put("maxIter", algorithm.settings.maxIter)
            }
        }
    }
}

private fun JsonObjectBuilder.putBatchConfig(config: ExperimentConfig) {
    putJsonObject("batch") {
        put("cloudletCount", config.batch.cloudletCount)
        putIntArray("cloudletCounts", config.batch.cloudletCounts)
        put("generatorType", config.batch.generatorType.name)
        putObjective("objective", config.batch.objectiveWeights)
    }
}

private fun JsonObjectBuilder.putRealtimeConfig(config: ExperimentConfig) {
    putJsonObject("realtime") {
        put("cloudletCount", config.realtime.cloudletCount)
        putIntArray("cloudletCounts", config.realtime.cloudletCounts)
        put("simulationDuration", config.realtime.simulationDuration)
        put("arrivalRate", config.realtime.arrivalRate)
        put("generatorType", config.realtime.generatorType.name)
        putObjective("objective", config.realtime.objectiveWeights)
        putJsonObject("arrival") {
            put("distribution", config.realtime.arrival.distribution)
            put("burstIntensity", config.realtime.arrival.burstIntensity)
            put("burstDuration", config.realtime.arrival.burstDuration)
            put("workloadPattern", config.realtime.arrival.workloadPattern)
            put("periodSeconds", config.realtime.arrival.periodSeconds)
            put("arrivalJitter", config.realtime.arrival.arrivalJitter)
            put("sporadicMinInterArrival", config.realtime.arrival.sporadicMinInterArrival)
            put("sporadicMaxInterArrival", config.realtime.arrival.sporadicMaxInterArrival)
            put("diurnalPeakMultiplier", config.realtime.arrival.diurnalPeakMultiplier)
            put("diurnalOffPeakMultiplier", config.realtime.arrival.diurnalOffPeakMultiplier)
            put("shortTaskRatio", config.realtime.arrival.shortTaskRatio)
            put("shortTaskLengthMultiplier", config.realtime.arrival.shortTaskLengthMultiplier)
            put("longTaskLengthMultiplier", config.realtime.arrival.longTaskLengthMultiplier)
            put("runtimeReferenceMips", config.realtime.arrival.runtimeReferenceMips)
            put("dagDepth", config.realtime.arrival.dagDepth)
            put("dagWidth", config.realtime.arrival.dagWidth)
            put("dagFanOut", config.realtime.arrival.dagFanOut)
        }
        putRealtimeScheduling(config.realtime.scheduling)
    }
}

private fun JsonObjectBuilder.putCsvConfig(resolved: ResolvedExperimentConfig) {
    putJsonObject("csv") {
        put("enabled", resolved.output.csvEnabled)
        put("delimiter", resolved.output.csvDelimiter)
    }
}

private fun JsonObjectBuilder.putObjective(
    name: String,
    weights: ObjectiveWeightsConfig,
) {
    putJsonObject(name) {
        put("cost", weights.cost)
        put("totalTime", weights.totalTime)
        put("loadBalance", weights.loadBalance)
        put("makespan", weights.makespan)
    }
}

internal fun JsonObjectBuilder.putIntArray(
    name: String,
    values: Iterable<Int>,
) {
    putJsonArray(name) { values.forEach { add(JsonPrimitive(it)) } }
}

internal fun JsonObjectBuilder.putDoubleArray(
    name: String,
    values: Iterable<Double>,
) {
    putJsonArray(name) { values.forEach { add(JsonPrimitive(it)) } }
}

private fun JsonObjectBuilder.putStringArray(
    name: String,
    values: Iterable<String>,
) {
    putJsonArray(name) { values.forEach { add(JsonPrimitive(it)) } }
}
