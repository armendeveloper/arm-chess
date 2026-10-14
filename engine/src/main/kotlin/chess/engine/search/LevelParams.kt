package chess.engine.search

/**
 * How far one search may go (design ADR-3). Strength comes from [nodeBudget] and [depthCap], which
 * are the same on every device; [timeCapMs] is the PRD think-time cap, and the search stops at
 * [deadlineMs] (the cap minus [DEADLINE_MARGIN_MS]) as a safety net on slow devices.
 *
 * Whichever limit is reached first ends the search, but depth 1 always completes, so a search
 * always returns a real move. [randomisation] then decides whether the level plays the search's best
 * move or a deliberately weaker one. [Levels.params] gives each level's values.
 *
 * @property depthCap the deepest iteration of iterative deepening, 1..[MAX_DEPTH].
 * @property nodeBudget stop after this many nodes (checked every [Engine.CHECK_INTERVAL] nodes).
 * @property timeCapMs the PRD think-time cap in milliseconds, or null for no deadline (strength
 *   matches and tests, so that results do not depend on machine load).
 * @property quiescence how leaf positions are resolved.
 * @property randomisation how the level picks its move from the root scores (none by default).
 */
data class LevelParams(
    val depthCap: Int,
    val nodeBudget: Long,
    val timeCapMs: Long?,
    val quiescence: Quiescence = Quiescence.Full,
    val randomisation: Randomisation = Randomisation.None,
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

/**
 * How a level departs from the search's best move (design ADR-3, "Randomisation" column). Every draw
 * comes from the `random` passed to [Engine.search], so the same seed and the same engine state give
 * the same move.
 *
 * The levels that pick among several moves need exact scores for them. At the root, each move is
 * searched with alpha lowered just far enough below the best score so far that every move the level
 * could pick gets an exact score. A move that fails low could never have been picked.
 */
sealed interface Randomisation {
    /** Always the search's best move (level 4). */
    data object None : Randomisation

    /**
     * Level 1: with probability [randomMoveChance], a uniformly random legal move. Otherwise, the
     * move with the best score after adding uniform integer noise in `-noiseCp..noiseCp` to each
     * root move's score.
     */
    data class RandomMoveOrNoise(val randomMoveChance: Double, val noiseCp: Int) : Randomisation {
        init {
            require(randomMoveChance in 0.0..1.0) { "randomMoveChance must be in 0..1, was $randomMoveChance" }
            require(noiseCp >= 0) { "noiseCp must not be negative, was $noiseCp" }
        }
    }

    /** Level 2: with probability [chance], a uniform pick among the [count] best-scoring root moves; otherwise the best. */
    data class PickFromTop(val chance: Double, val count: Int) : Randomisation {
        init {
            require(chance in 0.0..1.0) { "chance must be in 0..1, was $chance" }
            require(count > 0) { "count must be positive, was $count" }
        }
    }

    /**
     * Level 3: a uniform pick among the root moves scoring at least the best minus [marginCp], while
     * the game is undecided.
     *
     * Once the best score is [decidedCp] or more from 0, for either side, the best move is played.
     * That includes every mate score, so a found mate is never given up and a mated side takes the
     * longest defence. It also covers won endings such as K+Q v K. There the moves that make progress
     * are only a few centipawns better than those that don't, and random picks among them wander
     * until the 50-move rule (US-6 requires mate, 10/10).
     */
    data class WithinMargin(val marginCp: Int, val decidedCp: Int) : Randomisation {
        init {
            require(marginCp >= 0) { "marginCp must not be negative, was $marginCp" }
            require(decidedCp > 0) { "decidedCp must be positive, was $decidedCp" }
        }
    }
}
