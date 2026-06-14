package scheduler

import datacenter.ObjectiveFunction
import java.util.Random

internal data class AssignmentSearchSpace(
    val lb: Double,
    val ub: Double,
    val dim: Int,
)

internal data class OptimizerRuntime(
    val optFunction: ObjectiveFunction,
    val population: Int,
    val maxIter: Int,
    val random: Random,
)
