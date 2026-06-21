package scheduler.realtime

class VmCandidateScorer(
    private val filters: List<CandidateFilter> = defaultFilters(),
    private val scorers: List<CandidateScorer> = defaultScorers(),
) {
    fun score(candidates: List<NodeCandidate>): List<NodeCandidate> =
        candidates
            .map { candidate -> candidate.copy(score = scorers.sumOf { scorer -> scorer.score(candidate) }) }
            .filter { candidate ->
                filters.all { filter -> filter.accepts(candidate) } ||
                    candidate.placement is RealtimePlacementDecision.Rejected
            }

    companion object {
        fun defaultFilters(): List<CandidateFilter> =
            listOf(
                CandidateFilter { it.nodeState.acceptingWork },
                CandidateFilter { it.placement is RealtimePlacementDecision.Accepted },
            )

        fun defaultScorers(): List<CandidateScorer> =
            listOf(
                CandidateScorer { it.nodeState.availableTime },
                CandidateScorer(::score),
            )
    }
}

private fun score(candidate: NodeCandidate): Double = candidate.acceptedPlacement?.score ?: Double.POSITIVE_INFINITY
