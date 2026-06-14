package datacenter.generator

import config.GoogleTraceConfig
import datacenter.RealtimeCloudletBatch
import datacenter.RealtimeCloudletSpec
import org.cloudsimplus.cloudlets.Cloudlet
import util.Logger
import java.util.Random
import kotlin.math.min

/**
 * Google Trace 数据集云任务生成器。
 */
@Suppress("UnusedParameter")
class GoogleTraceCloudletGenerator(
    private val traceFilePath: String = DEFAULT_GOOGLE_TRACE_FILE_PATH,
    private val maxTasks: Int = DEFAULT_GOOGLE_TRACE_MAX_TASKS,
    private val timeWindowStart: Long = 0L,
    private val timeWindowEnd: Long = Long.MAX_VALUE,
    config: GoogleTraceConfig? = null,
) : CloudletGeneratorStrategy {
    constructor(config: GoogleTraceConfig) : this(
        traceFilePath = config.filePath,
        maxTasks = config.maxTasks,
        timeWindowStart = config.timeWindowStart,
        timeWindowEnd = config.timeWindowEnd,
        config = config,
    )

    private val traceData: List<GoogleTraceRecord> =
        GoogleTraceDataLoader.load(
            GoogleTraceLoadRequest(
                traceFilePath = traceFilePath,
                maxTasks = maxTasks,
                timeWindowStart = timeWindowStart,
                timeWindowEnd = timeWindowEnd,
            ),
        )

    override fun createCloudlets(
        userId: Int,
        count: Int,
        random: Random,
    ): List<Cloudlet> =
        createCloudletSpecs(userId, count, random)
            .map { it.cloudlet }

    @Suppress("UnusedParameter")
    fun createCloudletBatch(
        userId: Int,
        count: Int,
        random: Random,
    ): RealtimeCloudletBatch = RealtimeCloudletBatch(createCloudletSpecs(userId, count, random))

    @Suppress("UnusedParameter")
    fun createCloudletSpecs(
        userId: Int,
        count: Int,
        random: Random,
    ): List<RealtimeCloudletSpec> {
        val availableTasks = traceData.filter { it.eventType == GOOGLE_TRACE_SCHEDULE_EVENT }
        val specs =
            buildList {
                for (index in 0 until min(count, availableTasks.size)) {
                    val traceRecord = availableTasks[index % availableTasks.size]
                    addSpec(traceRecord)
                }
            }
        Logger.info("从Google Trace数据创建了 ${specs.size} 个云任务")
        return specs
    }

    private fun MutableList<RealtimeCloudletSpec>.addSpec(traceRecord: GoogleTraceRecord) {
        runCatching { GoogleTraceCloudletSpecFactory.create(traceRecord) }
            .onSuccess(::add)
            .onFailure { Logger.warn("创建云任务失败: ${it.message}") }
    }
}
