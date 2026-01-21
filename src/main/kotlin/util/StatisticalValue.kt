package util

import kotlin.math.sqrt

/**
 * 统计值（平均值和标准差）
 * 提供统计计算功能和数据验证
 */
data class StatisticalValue(
    val mean: Double,
    val stdDev: Double,
    val min: Double,
    val max: Double
) {
    init {
        // 数据验证
        require(!mean.isNaN() && !mean.isInfinite()) { "平均值必须是有效的有限数字" }
        require(!stdDev.isNaN() && stdDev >= 0.0) { "标准差必须是非负的有限数字" }
        require(!min.isNaN() && !min.isInfinite()) { "最小值必须是有效的有限数字" }
        require(!max.isNaN() && !max.isInfinite()) { "最大值必须是有效的有限数字" }
        require(min <= max) { "最小值不能大于最大值" }
        require(min <= mean && mean <= max) { "平均值必须在最小值和最大值之间" }
    }

    override fun toString(): String {
        return String.format("%.2f ± %.2f", mean, stdDev)
    }

    fun toStringWithRange(): String {
        return String.format("%.2f ± %.2f [%.2f, %.2f]", mean, stdDev, min, max)
    }

    companion object {
        /**
         * 计算数组的平均值
         * @param values 输入数组
         * @return 平均值
         * @throws IllegalArgumentException 当数组为空时
         */
        fun calculateMean(values: DoubleArray): Double {
            require(values.isNotEmpty()) { "数组不能为空" }
            validateArray(values, "计算平均值")
            return values.average()
        }

        /**
         * 计算数组的标准差（样本标准差）
         * @param values 输入数组
         * @return 标准差
         * @throws IllegalArgumentException 当数组为空或只有一个元素时
         */
        fun calculateStdDev(values: DoubleArray): Double {
            if (values.isEmpty()) throw IllegalArgumentException("数组不能为空")
            if (values.size < 2) return 0.0
            validateArray(values, "计算标准差")

            val mean = values.average()
            var sumSquaredDiff = 0.0
            for (value in values) {
                val diff = value - mean
                sumSquaredDiff += diff * diff
            }
            return sqrt(sumSquaredDiff / (values.size - 1))
        }

        /**
         * 计算数组的最小值
         * @param values 输入数组
         * @return 最小值
         * @throws IllegalArgumentException 当数组为空时
         */
        fun calculateMin(values: DoubleArray): Double {
            require(values.isNotEmpty()) { "数组不能为空" }
            validateArray(values, "计算最小值")
            return values.minOrNull() ?: throw IllegalStateException("无法计算最小值")
        }

        /**
         * 计算数组的最大值
         * @param values 输入数组
         * @return 最大值
         * @throws IllegalArgumentException 当数组为空时
         */
        fun calculateMax(values: DoubleArray): Double {
            require(values.isNotEmpty()) { "数组不能为空" }
            validateArray(values, "计算最大值")
            return values.maxOrNull() ?: throw IllegalStateException("无法计算最大值")
        }

        /**
         * 从DoubleArray创建StatisticalValue
         * @param values 输入数组
         * @return StatisticalValue实例
         * @throws IllegalArgumentException 当数组无效时
         */
        fun fromArray(values: DoubleArray): StatisticalValue {
            require(values.isNotEmpty()) { "数组不能为空" }
            validateArray(values, "创建统计值")

            val mean = values.average()
            var sumSquaredDiff = 0.0
            var min = Double.MAX_VALUE
            var max = Double.MIN_VALUE

            for (value in values) {
                val diff = value - mean
                sumSquaredDiff += diff * diff
                if (value < min) min = value
                if (value > max) max = value
            }

            val stdDev = if (values.size >= 2) {
                sqrt(sumSquaredDiff / (values.size - 1))
            } else 0.0

            return StatisticalValue(mean, stdDev, min, max)
        }

        /**
         * 验证数组数据的有效性
         * @param values 要验证的数组
         * @param operation 操作名称（用于错误消息）
         * @throws IllegalArgumentException 当数据无效时
         */
        private fun validateArray(values: DoubleArray, operation: String) {
            for ((index, value) in values.withIndex()) {
                if (value.isNaN()) {
                    throw IllegalArgumentException("$operation: 数组中第${index}个元素是NaN")
                }
                if (value.isInfinite()) {
                    throw IllegalArgumentException("$operation: 数组中第${index}个元素是无穷大")
                }
            }
        }
    }
}

