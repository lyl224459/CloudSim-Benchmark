package datacenter.generator

import config.GoogleTraceConfig
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.cloudlets.CloudletSimple
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic
import util.Logger
import java.io.File
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * Google Trace 数据集云任务生成器
 * 从Kaggle Google Trace Day 1 CSV文件生成云任务
 */
class GoogleTraceCloudletGenerator(
    private val traceFilePath: String = "data/google_trace/task_events.csv",
    private val maxTasks: Int = 1000,  // 限制读取的最大任务数，避免内存溢出
    private val timeWindowStart: Long = 0L,  // 时间窗口开始时间（秒）
    private val timeWindowEnd: Long = Long.MAX_VALUE,  // 时间窗口结束时间（秒）
    private val config: GoogleTraceConfig? = null
) : CloudletGeneratorStrategy {

    constructor(config: GoogleTraceConfig) : this(
        traceFilePath = config.filePath,
        maxTasks = config.maxTasks,
        timeWindowStart = config.timeWindowStart,
        timeWindowEnd = config.timeWindowEnd,
        config = config
    )

    private val traceData = mutableListOf<TraceRecord>()
    private var isLoaded = false

    init {
        loadTraceData()
    }

    /**
     * Google Trace 记录数据类
     */
    data class TraceRecord(
        val timestamp: Long,           // 时间戳
        val jobId: Long,               // 作业ID
        val taskIndex: Int,            // 任务索引
        val machineId: Long?,          // 机器ID
        val eventType: Int,            // 事件类型 (0=SCHEDULE, 1=EVICT, 2=FAIL, 3=FINISH, 4=KILL, 5=LOST, 6=UPDATE_PENDING, 7=UPDATE_RUNNING)
        val userName: String?,         // 用户名
        val schedulingClass: Int,      // 调度类别 (0=free, 1=production, 2=batch, 3=unknown)
        val priority: Int,             // 优先级
        val cpuRequest: Double?,       // CPU请求量
        val memoryRequest: Double?,    // 内存请求量
        val diskSpaceRequest: Double?, // 磁盘空间请求量
        val differentMachinesRestriction: Boolean // 不同机器限制
    )

    /**
     * 加载Google Trace数据
     */
    private fun loadTraceData() {
        try {
            val file = File(traceFilePath)

            // 检查文件是否存在
            if (!file.exists()) {
                Logger.warn("Google Trace文件不存在: $traceFilePath")
                Logger.info("将使用模拟数据代替，请下载Google Trace数据并放置到正确位置")
                createMockData()
                return
            }

            // 检查文件是否可读
            if (!file.canRead()) {
                Logger.error("Google Trace文件无法读取: $traceFilePath")
                Logger.info("将使用模拟数据代替，请检查文件权限")
                createMockData()
                return
            }

            // 检查文件大小
            val fileSize = file.length()
            if (fileSize == 0L) {
                Logger.warn("Google Trace文件为空: $traceFilePath")
                Logger.info("将使用模拟数据代替")
                createMockData()
                return
            }

            Logger.info("加载Google Trace数据: $traceFilePath (大小: ${fileSize / 1024 / 1024}MB)")

            var loadedCount = 0
            var skippedLines = 0
            var errorLines = 0

            file.forEachLine { line ->
                if (loadedCount >= maxTasks) return@forEachLine

                try {
                    val record = parseTraceRecord(line)
                    if (record != null) {
                        if (record.timestamp >= timeWindowStart && record.timestamp <= timeWindowEnd) {
                            traceData.add(record)
                            loadedCount++
                        } else {
                            skippedLines++
                        }
                    } else {
                        errorLines++
                    }
                } catch (e: Exception) {
                    errorLines++
                    Logger.debug("跳过格式错误的行: ${e.message}")
                }
            }

            Logger.info("Google Trace数据加载完成:")
            Logger.info("  - 成功加载: ${traceData.size} 条记录")
            Logger.info("  - 跳过的时间窗口外记录: $skippedLines 条")
            Logger.info("  - 跳过的格式错误记录: $errorLines 条")

            if (traceData.isEmpty()) {
                Logger.warn("未加载到任何有效的Trace记录，将使用模拟数据")
                createMockData()
            } else {
                isLoaded = true
            }

        } catch (e: SecurityException) {
            Logger.error("访问Google Trace文件时发生安全异常: ${e.message}")
            Logger.info("将使用模拟数据代替，请检查文件权限")
            createMockData()
        } catch (e: OutOfMemoryError) {
            Logger.error("加载Google Trace数据时内存不足: ${e.message}")
            Logger.info("将使用模拟数据代替，请考虑减小maxTasks参数")
            createMockData()
        } catch (e: Exception) {
            Logger.error("加载Google Trace数据时发生意外错误: ${e.message}", e)
            Logger.info("将使用模拟数据代替")
            createMockData()
        }
    }

    /**
     * 解析单行Trace记录
     */
    private fun parseTraceRecord(line: String): TraceRecord? {
        val fields = line.split(",")
        if (fields.size < 13) return null

        return try {
            TraceRecord(
                timestamp = fields[0].toLong(),
                jobId = fields[1].toLong(),
                taskIndex = fields[2].toInt(),
                machineId = fields[3].takeIf { it.isNotEmpty() }?.toLong(),
                eventType = fields[4].toInt(),
                userName = fields[5].takeIf { it.isNotEmpty() },
                schedulingClass = fields[6].toInt(),
                priority = fields[7].toInt(),
                cpuRequest = fields[8].takeIf { it.isNotEmpty() }?.toDouble(),
                memoryRequest = fields[9].takeIf { it.isNotEmpty() }?.toDouble(),
                diskSpaceRequest = fields[10].takeIf { it.isNotEmpty() }?.toDouble(),
                differentMachinesRestriction = fields[11].toBoolean()
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 创建模拟数据（当真实数据不可用时）
     */
    private fun createMockData() {
        Logger.info("创建Google Trace模拟数据")

        // 创建一些典型的Google数据中心任务模式
        val random = Random(42)

        for (i in 0 until 1000) {
            val record = TraceRecord(
                timestamp = i * 60L, // 每分钟一个任务
                jobId = (i / 10).toLong(), // 每10个任务一个作业
                taskIndex = i % 10,
                machineId = random.nextInt(10000).toLong(),
                eventType = 0, // SCHEDULE
                userName = "user_${random.nextInt(100)}",
                schedulingClass = random.nextInt(4), // 0-3
                priority = random.nextInt(12), // 0-11
                cpuRequest = max(0.1, random.nextGaussian() * 0.5 + 0.5), // 正态分布CPU需求
                memoryRequest = max(0.1, random.nextGaussian() * 0.3 + 0.3), // 内存需求
                diskSpaceRequest = random.nextDouble() * 100.0, // 磁盘需求
                differentMachinesRestriction = random.nextBoolean()
            )
            traceData.add(record)
        }

        isLoaded = true
        Logger.info("模拟数据创建完成，共 ${traceData.size} 条记录")
    }

    /**
     * 创建云任务列表
     */
    override fun createCloudlets(userId: Int, count: Int, random: Random): List<Cloudlet> {
        if (!isLoaded) {
            Logger.error("Google Trace数据未加载")
            return emptyList()
        }

        val cloudlets = mutableListOf<Cloudlet>()

        // 从trace数据中选择任务
        val availableTasks = traceData.filter { it.eventType == 0 } // 只选择SCHEDULE事件

        for (i in 0 until min(count, availableTasks.size)) {
            val traceRecord = availableTasks[i % availableTasks.size]

            try {
                val cloudlet = createCloudletFromTrace(traceRecord, i, userId)
                cloudlets.add(cloudlet)
            } catch (e: Exception) {
                Logger.warn("创建云任务失败: ${e.message}")
            }
        }

        Logger.info("从Google Trace数据创建了 ${cloudlets.size} 个云任务")
        return cloudlets
    }

    /**
     * 从Trace记录创建Cloudlet
     */
    private fun createCloudletFromTrace(record: TraceRecord, index: Int, userId: Int): Cloudlet {
        // 计算任务长度（基于CPU需求和执行时间）
        val cpuRequest = record.cpuRequest ?: 0.5
        val baseLength = 100000L // 基础MI数
        val length = (baseLength * cpuRequest * (1 + record.priority * 0.1)).toLong()

        // 创建利用率模型
        val utilizationModel = UtilizationModelDynamic()

        // 创建Cloudlet
        val cloudlet = CloudletSimple(length, 1)
            .setFileSize(1000L) // 输入文件大小
            .setOutputSize(1000L) // 输出文件大小
            .setUtilizationModel(utilizationModel)
            .setPriority(record.priority)

        return cloudlet
    }
}