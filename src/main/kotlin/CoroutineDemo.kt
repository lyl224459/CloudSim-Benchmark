package datacenter

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import util.Logger
import kotlin.system.measureTimeMillis

/**
 * 协程优化演示
 * 展示协程在算法并行执行中的性能优势
 */
object CoroutineDemo {

    /**
     * 演示协程并行执行的优势
     */
    suspend fun demonstrateParallelExecution() = coroutineScope {
        Logger.info("\n${"=".repeat(60)}")
        Logger.info("协程并行执行性能演示")
        Logger.info("${"=".repeat(60)}")

        // 模拟5个算法，每个算法执行时间为1秒
        val algorithmCount = 5
        val executionTimePerAlgorithm = 1000L // 1秒

        Logger.info("模拟 {} 个算法，每个算法执行 {}ms", algorithmCount, executionTimePerAlgorithm)

        // 顺序执行基准测试
        val sequentialTime = measureTimeMillis {
            Logger.info("开始顺序执行...")
            for (i in 1..algorithmCount) {
                simulateAlgorithmExecution("算法$i", executionTimePerAlgorithm)
            }
            Logger.info("顺序执行完成")
        }

        // 并行执行测试
        val parallelTime = measureTimeMillis {
            Logger.info("开始并行执行...")
            val jobs = (1..algorithmCount).map { i ->
                async(Dispatchers.Default) {
                    simulateAlgorithmExecution("算法$i", executionTimePerAlgorithm)
                }
            }
            jobs.forEach { it.await() }
            Logger.info("并行执行完成")
        }

        // 计算性能提升
        val speedup = sequentialTime.toDouble() / parallelTime.toDouble()
        val efficiency = speedup / algorithmCount * 100

        Logger.info("\n性能对比结果:")
        Logger.info("顺序执行时间: {}ms", sequentialTime)
        Logger.info("并行执行时间: {}ms", parallelTime)
        Logger.info("加速比: {}x", String.format("%.2f", speedup))
        Logger.info("并行效率: {}%", String.format("%.1f", efficiency))

        if (speedup > 1.5) {
            Logger.info("✅ 协程并行执行显著提升性能！")
        } else {
            Logger.info("⚠️ 并行效果有限，可能受CPU核心数或任务特性影响")
        }
    }

    /**
     * 模拟算法执行
     */
    private suspend fun simulateAlgorithmExecution(algorithmName: String, executionTime: Long) {
        Logger.debug("开始执行 {}...", algorithmName)
        delay(executionTime) // 模拟计算时间
        Logger.debug("{} 执行完成", algorithmName)
    }

    /**
     * 演示通道（Channel）用于结果收集
     */
    suspend fun demonstrateChannelUsage() = coroutineScope {
        Logger.info("\n${"=".repeat(60)}")
        Logger.info("协程Channel演示 - 结果收集模式")
        Logger.info("${"=".repeat(60)}")

        val algorithmCount = 3
        val resultsChannel = Channel<String>(algorithmCount)

        // 并行执行算法并发送结果到通道
        val producerJobs = (1..algorithmCount).map { i ->
            async(Dispatchers.Default) {
                val result = simulateAlgorithmWithResult("算法$i")
                resultsChannel.send(result)
                Logger.debug("算法$i 结果已发送到通道")
            }
        }

        // 从通道收集结果
        val results = mutableListOf<String>()
        Logger.info("开始收集结果...")
        repeat(algorithmCount) {
            val result = resultsChannel.receive()
            results.add(result)
            Logger.debug("收到结果: {}", result)
        }

        // 等待所有生产者完成
        producerJobs.forEach { it.join() }
        resultsChannel.close()

        Logger.info("收集完成，结果列表: {}", results.sorted())
    }

    /**
     * 模拟有返回结果的算法执行
     */
    private suspend fun simulateAlgorithmWithResult(algorithmName: String): String {
        Logger.debug("开始执行 {}...", algorithmName)
        delay((500..1500).random().toLong()) // 随机执行时间 0.5-1.5秒
        val fitness = (0.1..1.0).random()
        val result = "$algorithmName(适应度=${String.format("%.3f", fitness)})"
        Logger.debug("{} 执行完成: {}", algorithmName, result)
        return result
    }

    /**
     * 运行协程演示
     */
    fun runDemo() {
        Logger.info("🚀 开始协程优化功能演示")

        runBlocking {
            demonstrateParallelExecution()
            demonstrateChannelUsage()
        }

        Logger.info("\n✨ 协程演示完成!")
        Logger.info("协程优化已在 ComparisonRunner 中实现:")
        Logger.info("- 并行执行多个算法")
        Logger.info("- 并行执行多次运行")
        Logger.info("- 使用Channel进行结果收集")
        Logger.info("- 异常处理和SupervisorJob")
    }
}

/**
 * 扩展函数：随机数生成
 */
private fun ClosedRange<Double>.random(): Double {
    return (Math.random() * (endInclusive - start) + start)
}