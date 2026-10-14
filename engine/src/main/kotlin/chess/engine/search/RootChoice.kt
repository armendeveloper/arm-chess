package chess.engine.search

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * One search's level randomisation at the root (design ADR-3), in three steps:
 *
 * 1. [draw], before the search: which kind of move this decision plays ([planned]). Level 1 decides
 *    here between a uniformly random move and the noisy best, level 2 whether to pick from the top 5.
 *    Deciding first means the root window is only widened when the pick needs exact scores.
 * 2. [alpha], for each root move of each iteration: the root window's lower bound. Every move the
 *    pick could choose gets a score above it, which is exact because the root's beta is infinite.
 *    A move that fails low returns an upper bound below the bound, and the pick can never choose it.
 * 3. [choose], after the search: the index of the move to play among the scored root moves.
 *
 * Every draw comes from the search's `random`, in a fixed order, so a seed replays the same choices.
 */
internal class RootChoice private constructor(
    /** What this decision plays, drawn before the search. */
    val planned: MoveChoice,
    private val noiseCp: Int,
    private val topCount: Int,
    private val marginCp: Int,
    /** Level 3 plays the best move once the best score is this far from 0 (always for a mate score). */
    private val decidedCp: Int,
) {
    /** What [choose] actually played: [planned], or [MoveChoice.Best] when level 3's game is decided. */
    var applied: MoveChoice = planned
        private set

    // The window state for one iteration: the best score so far, and (for FromTop) the best scores, highest first.
    private var best = -Score.INFINITY
    private val top = IntArray(if (planned == MoveChoice.FromTop) topCount else 0)
    private var topSize = 0

    /** Starts a new iteration's root window. */
    fun newIteration() {
        best = -Score.INFINITY
        topSize = 0
    }

    /** Records a root move's score in this iteration. */
    fun add(score: Int) {
        if (score > best) best = score
        if (planned == MoveChoice.FromTop) insertTop(score)
    }

    /** The root window's lower bound for the next root move: alpha in `[alpha, +infinity)`. */
    fun alpha(): Int = when (planned) {
        MoveChoice.Best, MoveChoice.RandomMove -> best
        MoveChoice.NoisyBest -> lowered(2L * noiseCp)
        MoveChoice.WithinMargin -> lowered(marginCp.toLong())
        MoveChoice.FromTop -> if (topSize < topCount) -Score.INFINITY else top[topCount - 1] - 1
    }

    /** `best − margin − 1`, but never below −infinity (a full window). */
    private fun lowered(margin: Long): Int =
        if (best == -Score.INFINITY) -Score.INFINITY else max(-Score.INFINITY.toLong(), best - margin - 1).toInt()

    private fun insertTop(score: Int) {
        if (topSize == topCount && score <= top[topCount - 1]) return
        var i = min(topSize, topCount - 1)
        while (i > 0 && top[i - 1] < score) {
            top[i] = top[i - 1]
            i--
        }
        top[i] = score
        if (topSize < topCount) topSize++
    }

    /**
     * Picks the move to play among the first [count] root moves of the iteration the search used,
     * whose scores are [scores] (exact for every move the pick can choose) and whose best is at
     * [bestIndex]. Returns the index of the chosen move.
     */
    fun choose(scores: IntArray, count: Int, bestIndex: Int, random: Random): Int {
        applied = planned
        return when (planned) {
            MoveChoice.Best -> bestIndex
            MoveChoice.RandomMove -> random.nextInt(count)
            MoveChoice.NoisyBest -> {
                var pick = bestIndex
                var pickScore = Int.MIN_VALUE
                for (i in 0 until count) {
                    val noisy = scores[i] + random.nextInt(-noiseCp, noiseCp + 1)
                    if (noisy > pickScore) {
                        pickScore = noisy
                        pick = i
                    }
                }
                pick
            }
            MoveChoice.FromTop -> {
                // Highest score first; equal scores keep their root order (a stable sort).
                val ranked = (0 until count).sortedByDescending { scores[it] }
                ranked[random.nextInt(min(topCount, count))]
            }
            MoveChoice.WithinMargin -> {
                val bestScore = scores[bestIndex]
                if (abs(bestScore) >= min(decidedCp, Score.MATE_BOUND)) {
                    applied = MoveChoice.Best
                    bestIndex
                } else {
                    val floor = bestScore - marginCp
                    var candidates = 0
                    for (i in 0 until count) if (scores[i] >= floor) candidates++
                    var n = random.nextInt(candidates)
                    var pick = bestIndex
                    for (i in 0 until count) {
                        if (scores[i] >= floor && n-- == 0) {
                            pick = i
                            break
                        }
                    }
                    pick
                }
            }
        }
    }

    companion object {
        /** Draws this decision's kind of move from [randomisation], before the search. */
        fun draw(randomisation: Randomisation, random: Random): RootChoice = when (randomisation) {
            Randomisation.None -> RootChoice(MoveChoice.Best, 0, 0, 0, 0)
            is Randomisation.RandomMoveOrNoise ->
                if (random.nextDouble() < randomisation.randomMoveChance) {
                    RootChoice(MoveChoice.RandomMove, 0, 0, 0, 0)
                } else {
                    RootChoice(MoveChoice.NoisyBest, randomisation.noiseCp, 0, 0, 0)
                }
            is Randomisation.PickFromTop ->
                if (random.nextDouble() < randomisation.chance) {
                    RootChoice(MoveChoice.FromTop, 0, randomisation.count, 0, 0)
                } else {
                    RootChoice(MoveChoice.Best, 0, 0, 0, 0)
                }
            is Randomisation.WithinMargin -> RootChoice(MoveChoice.WithinMargin, 0, 0, randomisation.marginCp, randomisation.decidedCp)
        }
    }
}
