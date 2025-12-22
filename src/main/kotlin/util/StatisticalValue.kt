package util

/**
 * 统计值（平均值和标准差）
 */
data class StatisticalValue(
    val mean: Double,
    val stdDev: Double,
    val min: Double,
    val max: Double
) {
    override fun toString(): String {
        return String.format("%.2f ± %.2f", mean, stdDev)
    }
    
    fun toStringWithRange(): String {
        return String.format("%.2f ± %.2f [%.2f, %.2f]", mean, stdDev, min, max)
    }
}

