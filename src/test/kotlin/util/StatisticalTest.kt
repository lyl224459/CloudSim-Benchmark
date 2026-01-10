package util

import org.junit.jupiter.api.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.assertj.core.data.Offset

/**
 * 统计计算测试
 */
class StatisticalTest {

    private lateinit var values: DoubleArray
    private lateinit var intValues: IntArray

    @BeforeEach
    fun setUp() {
        values = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)
        intValues = intArrayOf(10, 20, 30, 40, 50)
    }

    @Test
    fun `should calculate mean correctly`() {
        // Given - When - Then
        assertThat(StatisticalValue.calculateMean(values)).isEqualTo(3.0)
        assertThat(StatisticalValue.calculateMean(intValues.map { it.toDouble() }.toDoubleArray())).isEqualTo(30.0)
    }

    @Test
    fun `should calculate standard deviation correctly`() {
        // Given - When
        val stdDev = StatisticalValue.calculateStdDev(values)

        // Then
        // 对于样本 [1,2,3,4,5]，标准差应该大约是1.581
        assertThat(stdDev).isCloseTo(1.581, within(0.001))
    }

    @Test
    fun `should calculate minimum correctly`() {
        // Given - When - Then
        assertThat(StatisticalValue.calculateMin(values)).isEqualTo(1.0)
        assertThat(StatisticalValue.calculateMin(intValues.map { it.toDouble() }.toDoubleArray())).isEqualTo(10.0)
    }

    @Test
    fun `should calculate maximum correctly`() {
        // Given - When - Then
        assertThat(StatisticalValue.calculateMax(values)).isEqualTo(5.0)
        assertThat(StatisticalValue.calculateMax(intValues.map { it.toDouble() }.toDoubleArray())).isEqualTo(50.0)
    }

    @Test
    fun `should handle empty array gracefully`() {
        // Given
        val emptyArray = doubleArrayOf()

        // When - Then
        assertThatThrownBy { StatisticalValue.calculateMean(emptyArray) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("数组不能为空")

        assertThatThrownBy { StatisticalValue.calculateStdDev(emptyArray) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("数组不能为空")

        assertThatThrownBy { StatisticalValue.calculateMin(emptyArray) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("数组不能为空")

        assertThatThrownBy { StatisticalValue.calculateMax(emptyArray) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("数组不能为空")
    }

    @Test
    fun `should handle single element array`() {
        // Given
        val singleElement = doubleArrayOf(42.0)

        // When - Then
        assertThat(StatisticalValue.calculateMean(singleElement)).isEqualTo(42.0)
        assertThat(StatisticalValue.calculateStdDev(singleElement)).isEqualTo(0.0) // 单元素标准差为0
        assertThat(StatisticalValue.calculateMin(singleElement)).isEqualTo(42.0)
        assertThat(StatisticalValue.calculateMax(singleElement)).isEqualTo(42.0)
    }

    @Test
    fun `should handle constant values array`() {
        // Given
        val constantValues = doubleArrayOf(5.0, 5.0, 5.0, 5.0, 5.0)

        // When - Then
        assertThat(StatisticalValue.calculateMean(constantValues)).isEqualTo(5.0)
        assertThat(StatisticalValue.calculateStdDev(constantValues)).isEqualTo(0.0) // 常量数组标准差为0
        assertThat(StatisticalValue.calculateMin(constantValues)).isEqualTo(5.0)
        assertThat(StatisticalValue.calculateMax(constantValues)).isEqualTo(5.0)
    }

    @Test
    fun `should handle negative values correctly`() {
        // Given
        val negativeValues = doubleArrayOf(-5.0, -3.0, -1.0, 1.0, 3.0, 5.0)

        // When - Then
        assertThat(StatisticalValue.calculateMean(negativeValues)).isEqualTo(0.0)
        assertThat(StatisticalValue.calculateMin(negativeValues)).isEqualTo(-5.0)
        assertThat(StatisticalValue.calculateMax(negativeValues)).isEqualTo(5.0)

        val stdDev = StatisticalValue.calculateStdDev(negativeValues)
        assertThat(stdDev).isGreaterThan(0.0)
    }

    @Test
    fun `should handle decimal values correctly`() {
        // Given
        val decimalValues = doubleArrayOf(1.5, 2.7, 3.9, 4.2, 5.1)

        // When
        val mean = StatisticalValue.calculateMean(decimalValues)
        val stdDev = StatisticalValue.calculateStdDev(decimalValues)

        // Then
        assertThat(mean).isCloseTo(3.48, within(0.01))
        assertThat(stdDev).isGreaterThan(0.0)
        assertThat(StatisticalValue.calculateMin(decimalValues)).isEqualTo(1.5)
        assertThat(StatisticalValue.calculateMax(decimalValues)).isEqualTo(5.1)
    }

    @Test
    fun `should create statistical value from array correctly`() {
        // Given - When
        val statValue = StatisticalValue.fromArray(values)

        // Then
        assertThat(statValue.mean).isEqualTo(3.0)
        assertThat(statValue.stdDev).isCloseTo(1.581, within(0.001))
        assertThat(statValue.min).isEqualTo(1.0)
        assertThat(statValue.max).isEqualTo(5.0)
    }

    @Test
    fun `should format statistical value correctly`() {
        // Given
        val statValue = StatisticalValue.fromArray(doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0))

        // When
        val formatted = statValue.toString()

        // Then
        assertThat(formatted).contains("3.00") // mean
        assertThat(formatted).contains("1.58") // stdDev
    }

    @Test
    fun `should handle precision correctly in formatting`() {
        // Given
        val preciseValues = doubleArrayOf(1.23456789, 2.34567890, 3.45678901)

        // When
        val statValue = StatisticalValue.fromArray(preciseValues)
        val formatted = statValue.toString()

        // Then
        // 应该保留两位小数
        assertThat(formatted).contains("2.35") // 平均值约2.35
    }
}