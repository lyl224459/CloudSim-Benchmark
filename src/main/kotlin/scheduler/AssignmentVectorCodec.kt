package scheduler

import datacenter.ObjectiveFunction
import kotlin.math.round

internal object SchedulerAllocationValidator {
    fun requireAvailableVms(
        vmNum: Int,
        schedulerName: String,
    ) {
        require(vmNum > 0) { "$schedulerName 需要至少 1 台可用 VM" }
    }

    fun validateAllocation(
        allocation: IntArray,
        cloudletNum: Int,
        vmNum: Int,
        schedulerName: String,
    ) {
        require(allocation.size == cloudletNum) {
            "$schedulerName 返回的分配数量 ${allocation.size} 与任务数量 $cloudletNum 不一致"
        }
        for (i in allocation.indices) {
            val vmIndex = allocation[i]
            require(vmIndex in 0 until vmNum) {
                "$schedulerName 返回非法 VM 下标: cloudlet=$i, vm=$vmIndex, 可用范围=0..${vmNum - 1}"
            }
        }
    }
}

internal class AssignmentVectorCodec(
    private val dim: Int,
    private val lowerBound: Int,
    private val upperBound: Int,
) {
    private val buffer = IntArray(dim)

    fun clampRound(value: Double): Double = round(value).toInt().coerceIn(lowerBound, upperBound).toDouble()

    fun clampRoundInPlace(
        values: DoubleArray,
        offset: Int = 0,
    ) {
        for (j in 0 until dim) {
            values[offset + j] = clampRound(values[offset + j])
        }
    }

    fun evaluate(
        values: DoubleArray,
        offset: Int,
        objectiveFunction: ObjectiveFunction,
    ): Double {
        fillBuffer(values, offset)
        return objectiveFunction.calculate(buffer)
    }

    fun toAllocation(
        values: DoubleArray,
        offset: Int = 0,
    ): IntArray {
        fillBuffer(values, offset)
        return buffer.copyOf()
    }

    private fun fillBuffer(
        values: DoubleArray,
        offset: Int,
    ) {
        for (j in 0 until dim) {
            buffer[j] = round(values[offset + j]).toInt().coerceIn(lowerBound, upperBound)
        }
    }
}
