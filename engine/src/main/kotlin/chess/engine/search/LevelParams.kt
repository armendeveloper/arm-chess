package chess.engine.search

/**
 * How far one search may go (design ADR-3). Strength comes from [nodeBudget] and [depthCap], which
 * are the same on every device; [timeCapMs] is the PRD think-time cap, and the search stops at
 * [deadlineMs] (the cap minus [DEADLINE_MARGIN_MS]) as a safety net on slow devices.
 *
 * Whichever limit is reached first ends the search, but depth 1 always completes, so a search
 * always returns a real move. Level randomisation (T-9) is not part of these parameters yet.
 *
 * @property depthCap the deepest iteration of iterative deepening, 1..[MAX_DEPTH].
 * @property nodeBudget stop after this many nodes (checked every [Engine.CHECK_INTERVAL] nodes).
 * @property timeCapMs the PRD think-time cap in milliseconds, or null for no deadline (strength
 *   matches and tests, so that results do not depend on machine load).
 * @property quiescence how leaf positions are resolved.
 */
data class LevelParams(
    val depthCap: Int,
    val nodeBudget: Long,
    val timeCapMs: Long?,
    val quiescence: Quiescence = Quiescence.Full,
) {
    init {
        require(depthCap in 1..MAX_DEPTH) { "depthCap must be in 1..$MAX_DEPTH, was $depthCap" }
        require(nodeBudget > 0) { "nodeBudget must be positive, was $nodeBudget" }
        require(timeCapMs == null || timeCapMs > DEADLINE_MARGIN_MS) {
            "timeCapMs must be more than the $DEADLINE_MARGIN_MS ms margin, was $timeCapMs"
        }
    }

    /** When the search stops, in milliseconds after it started: [timeCapMs] minus the margin, or null. */
    val deadlineMs: Long? get() = timeCapMs?.let { it - DEADLINE_MARGIN_MS }

    /** The same limits without the wall-clock deadline, for deterministic matches (design §4.7). */
    fun withoutDeadline(): LevelParams = copy(timeCapMs = null)

    companion object {
        /** The deepest iteration any level may ask for (level 4's cap, design ADR-3). */
        const val MAX_DEPTH: Int = 64

        /** The deadline is this much before the time cap, for the result hand-off and the next stop check. */
        const val DEADLINE_MARGIN_MS: Long = 100
    }
}

/** How the search resolves captures at its leaves (design ADR-3, "Quiescence" column). */
sealed interface Quiescence {
    /** No quiescence: leaves are scored as they stand, so pieces can be left hanging (level 1). */
    data object Off : Quiescence

    /** Captures only (including capture-promotions), at most [maxPlies] plies deep (level 2). */
    data class CapturesOnly(val maxPlies: Int) : Quiescence {
        init {
            require(maxPlies > 0) { "maxPlies must be positive, was $maxPlies" }
        }
    }

    /** All captures and promotions, to any depth, with stand-pat and delta pruning (levels 3 and 4). */
    data object Full : Quiescence
}
