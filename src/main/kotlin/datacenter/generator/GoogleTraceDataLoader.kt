package datacenter.generator

import util.Logger
import java.io.File
import java.io.IOException

private const val BYTES_PER_MEBIBYTE = 1024 * 1024

internal object GoogleTraceDataLoader {
    fun load(request: GoogleTraceLoadRequest): List<GoogleTraceRecord> {
        val file = File(request.traceFilePath)
        val fileProblem = fileProblem(file)
        if (fileProblem != null) {
            return fallback(fileProblem)
        }
        return loadReadableFile(file, request)
    }

    private fun fileProblem(file: File): String? =
        when {
            !file.exists() -> "Google Trace文件不存在: ${file.path}"
            !file.canRead() -> "Google Trace文件无法读取: ${file.path}"
            file.length() == 0L -> "Google Trace文件为空: ${file.path}"
            else -> null
        }

    private fun loadReadableFile(
        file: File,
        request: GoogleTraceLoadRequest,
    ): List<GoogleTraceRecord> =
        try {
            loadRecords(file, request)
        } catch (exception: SecurityException) {
            fallback("访问Google Trace文件时发生安全异常: ${exception.message}")
        } catch (exception: IOException) {
            fallback("读取Google Trace文件时发生IO异常: ${exception.message}")
        } catch (exception: OutOfMemoryError) {
            fallback("加载Google Trace数据时内存不足: ${exception.message}")
        }

    private fun loadRecords(
        file: File,
        request: GoogleTraceLoadRequest,
    ): List<GoogleTraceRecord> {
        Logger.info("加载Google Trace数据: ${file.path} (大小: ${file.length() / BYTES_PER_MEBIBYTE}MB)")
        val records = mutableListOf<GoogleTraceRecord>()
        val stats = collectRecords(file, request, records)
        logStats(records.size, stats)
        return if (records.isEmpty()) {
            fallback("未加载到任何有效的Trace记录")
        } else {
            records
        }
    }

    private fun collectRecords(
        file: File,
        request: GoogleTraceLoadRequest,
        records: MutableList<GoogleTraceRecord>,
    ): GoogleTraceLoadStats {
        var skippedWindowCount = 0
        var malformedCount = 0
        file.forEachLine { line ->
            if (records.size < request.maxTasks) {
                val record = GoogleTraceRecordParser.parse(line)
                when {
                    record == null -> malformedCount++
                    record.timestamp in request.timeWindowStart..request.timeWindowEnd -> records.add(record)
                    else -> skippedWindowCount++
                }
            }
        }
        return GoogleTraceLoadStats(records.size, skippedWindowCount, malformedCount)
    }

    private fun logStats(
        loadedCount: Int,
        stats: GoogleTraceLoadStats,
    ) {
        Logger.info("Google Trace数据加载完成:")
        Logger.info("  - 成功加载: $loadedCount 条记录")
        Logger.info("  - 跳过的时间窗口外记录: ${stats.skippedWindowCount} 条")
        Logger.info("  - 跳过的格式错误记录: ${stats.malformedCount} 条")
    }

    private fun fallback(reason: String): List<GoogleTraceRecord> {
        Logger.warn(reason)
        Logger.info("将使用模拟数据代替")
        return GoogleTraceMockDataFactory.create()
    }
}
