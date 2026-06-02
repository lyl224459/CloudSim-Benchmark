package datacenter

import scheduler.ResolvedAlgorithm

internal class RealtimeAlgorithmSelection(
    private val resolvedAlgorithms: List<ResolvedAlgorithm>,
) {
    fun required(): List<ResolvedAlgorithm> {
        require(resolvedAlgorithms.isNotEmpty()) {
            "RealtimeComparisonRunner 需要已解析的算法列表"
        }
        return resolvedAlgorithms
    }
}
