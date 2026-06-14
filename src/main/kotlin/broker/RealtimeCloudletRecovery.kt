package broker

import config.RealtimeSchedulingConfig
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.vms.Vm
import kotlin.math.floor

private const val MIN_RUNTIME_SECONDS = 0.001
private const val MIN_VM_MIPS = 1.0
private val terminalRealtimeStatuses = setOf(Cloudlet.Status.SUCCESS, Cloudlet.Status.FAILED)

internal data class RealtimeRecoveryEstimate(
    val recoveredLength: Long,
    val lostLength: Long,
)

internal class RealtimeCloudletRecoveryEstimator(
    private val scheduling: RealtimeSchedulingConfig,
    private val clock: () -> Double,
    private val vmList: () -> List<Vm>,
) {
    fun estimate(cloudlet: Cloudlet): RealtimeRecoveryEstimate {
        val recoveredLength = recoveredLength(cloudlet)
        return RealtimeRecoveryEstimate(
            recoveredLength = recoveredLength,
            lostLength = (cloudlet.length - recoveredLength).coerceAtLeast(0L),
        )
    }

    fun estimatedRuntime(cloudlet: Cloudlet): Double {
        val vm = cloudlet.vm ?: vmList().first()
        return cloudlet.length.toDouble() / vm.mips.coerceAtLeast(MIN_VM_MIPS)
    }

    private fun recoveredLength(cloudlet: Cloudlet): Long =
        if (scheduling.checkpointInterval <= 0.0) {
            0L
        } else {
            checkpointRecoveredLength(cloudlet)
        }

    private fun checkpointRecoveredLength(cloudlet: Cloudlet): Long {
        val elapsed = (clock() - cloudlet.getStartTime()).coerceAtLeast(0.0)
        val checkpoints = floor(elapsed / scheduling.checkpointInterval).toLong()
        return if (checkpoints <= 0L) {
            0L
        } else {
            val runtime = estimatedRuntime(cloudlet).coerceAtLeast(MIN_RUNTIME_SECONDS)
            (cloudlet.length * (elapsed / runtime).coerceIn(0.0, 1.0)).toLong()
        }
    }
}

internal fun Cloudlet.isTerminalRealtimeCloudlet(): Boolean = status in terminalRealtimeStatuses
