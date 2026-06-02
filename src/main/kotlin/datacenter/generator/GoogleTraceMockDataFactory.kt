package datacenter.generator

import util.Logger
import java.util.Random
import kotlin.math.max

private const val MOCK_RANDOM_SEED = 42L
private const val MOCK_RECORD_COUNT = 1000
private const val MOCK_TASK_INTERVAL_SECONDS = 60L
private const val MOCK_TASKS_PER_JOB = 10
private const val MOCK_MACHINE_COUNT = 10_000
private const val MOCK_USER_COUNT = 100
private const val MOCK_SCHEDULING_CLASS_COUNT = 4
private const val MOCK_PRIORITY_BOUND = 12
private const val MIN_RESOURCE_REQUEST = 0.1
private const val CPU_GAUSSIAN_SCALE = 0.5
private const val CPU_GAUSSIAN_OFFSET = 0.5
private const val MEMORY_GAUSSIAN_SCALE = 0.3
private const val MEMORY_GAUSSIAN_OFFSET = 0.3
private const val DISK_REQUEST_SCALE = 100.0

internal object GoogleTraceMockDataFactory {
    fun create(): List<GoogleTraceRecord> {
        Logger.info("创建Google Trace模拟数据")
        val random = Random(MOCK_RANDOM_SEED)
        val records =
            List(MOCK_RECORD_COUNT) { index ->
                GoogleTraceRecord(
                    timestamp = index * MOCK_TASK_INTERVAL_SECONDS,
                    jobId = (index / MOCK_TASKS_PER_JOB).toLong(),
                    taskIndex = index % MOCK_TASKS_PER_JOB,
                    machineId = random.nextInt(MOCK_MACHINE_COUNT).toLong(),
                    eventType = GOOGLE_TRACE_SCHEDULE_EVENT,
                    userName = "user_${random.nextInt(MOCK_USER_COUNT)}",
                    schedulingClass = random.nextInt(MOCK_SCHEDULING_CLASS_COUNT),
                    priority = random.nextInt(MOCK_PRIORITY_BOUND),
                    cpuRequest = random.gaussianResource(CPU_GAUSSIAN_SCALE, CPU_GAUSSIAN_OFFSET),
                    memoryRequest = random.gaussianResource(MEMORY_GAUSSIAN_SCALE, MEMORY_GAUSSIAN_OFFSET),
                    diskSpaceRequest = random.nextDouble() * DISK_REQUEST_SCALE,
                    differentMachinesRestriction = random.nextBoolean(),
                )
            }
        Logger.info("模拟数据创建完成，共 ${records.size} 条记录")
        return records
    }

    private fun Random.gaussianResource(
        scale: Double,
        offset: Double,
    ): Double = max(MIN_RESOURCE_REQUEST, nextGaussian() * scale + offset)
}
