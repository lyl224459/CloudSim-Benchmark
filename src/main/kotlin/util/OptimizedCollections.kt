package util

import it.unimi.dsi.fastutil.doubles.DoubleArrayList
import it.unimi.dsi.fastutil.doubles.DoubleList
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import it.unimi.dsi.fastutil.objects.ObjectArrayList

/**
 * 优化的集合操作工具类
 * 使用高性能集合库提升数据处理效率
 */
object OptimizedCollections {

    /**
     * 创建优化的Double数组列表
     */
    fun createDoubleList(): DoubleArrayList = DoubleArrayList()

    /**
     * 创建优化的Double数组列表（指定容量）
     */
    fun createDoubleList(capacity: Int): DoubleArrayList = DoubleArrayList(capacity)

    /**
     * 创建优化的Int数组列表
     */
    fun createIntList(): IntArrayList = IntArrayList()

    /**
     * 创建优化的Int数组列表（指定容量）
     */
    fun createIntList(capacity: Int): IntArrayList = IntArrayList(capacity)

    /**
     * 批量添加Double数组到列表
     */
    fun addAllDoubles(list: DoubleArrayList, values: DoubleArray) {
        for (value in values) {
            list.add(value)
        }
    }

    /**
     * 批量添加Int数组到列表
     */
    fun addAllInts(list: IntArrayList, values: IntArray) {
        for (value in values) {
            list.add(value)
        }
    }

    /**
     * 计算Double列表的统计信息
     */
    fun calculateStats(values: DoubleArrayList): StatisticalValue {
        if (values.isEmpty()) {
            throw IllegalArgumentException("数组不能为空")
        }

        val array = values.toDoubleArray()
        return StatisticalValue.fromArray(array)
    }

    /**
     * 计算Int列表的统计信息
     */
    fun calculateStats(values: IntArrayList): StatisticalValue {
        if (values.isEmpty()) {
            throw IllegalArgumentException("数组不能为空")
        }

        // 转换为Double数组进行计算
        val doubleArray = DoubleArray(values.size) { values.getInt(it).toDouble() }
        return StatisticalValue.fromArray(doubleArray)
    }

    /**
     * 高效的数组求和
     */
    fun sum(values: DoubleArrayList): Double {
        var total = 0.0
        for (i in 0 until values.size) {
            total += values.getDouble(i)
        }
        return total
    }

    /**
     * 高效的数组求和
     */
    fun sum(values: IntArrayList): Long {
        var total = 0L
        for (i in 0 until values.size) {
            total += values.getInt(i)
        }
        return total
    }

    /**
     * 查找数组中的最小值
     */
    fun min(values: DoubleArrayList): Double {
        if (values.isEmpty()) throw NoSuchElementException()
        var min = Double.MAX_VALUE
        for (i in 0 until values.size) {
            val value = values.getDouble(i)
            if (value < min) min = value
        }
        return min
    }

    /**
     * 查找数组中的最大值
     */
    fun max(values: DoubleArrayList): Double {
        if (values.isEmpty()) throw NoSuchElementException()
        var max = Double.MIN_VALUE
        for (i in 0 until values.size) {
            val value = values.getDouble(i)
            if (value > max) max = value
        }
        return max
    }

    /**
     * 计算数组平均值
     */
    fun average(values: DoubleArrayList): Double {
        if (values.isEmpty()) throw NoSuchElementException()
        return sum(values) / values.size
    }

    /**
     * 计算数组平均值
     */
    fun average(values: IntArrayList): Double {
        if (values.isEmpty()) throw NoSuchElementException()
        return sum(values).toDouble() / values.size
    }
}