package chess.engine.search

import chess.engine.board.Move
import chess.engine.rules.Game
import chess.engine.rules.GameStatus
import kotlin.random.Random

/**
 * The computer player (design §4.6): iterative-deepening alpha-beta over a 0x88 board with
 * quiescence, check extension, MVV-LVA, killers, history and a transposition table.
 *
 * One `Engine` holds a 4 MiB transposition table (2^[ttEntriesLog2] entries) and the move-ordering
 * history, which carry over from one search to the next within a game. It is **not thread-safe**:
 * the caller serialises calls (design §6.5). It has no coroutine or Android dependency; cancellation
 * is the cooperative [search] `shouldStop` callback.
 *
 * Call [newGame] when a new game starts and after an undo, so that nothing from a line that is no
 * longer on the board affects the next search.
 */
class Engine(ttEntriesLog2: Int = TranspositionTable.DEFAULT_ENTRIES_LOG2) {
    private val search = Search(TranspositionTable(ttEntriesLog2))

    /** Clears the transposition table, killer moves and history. */
    fun newGame() {
        search.clear()
    }

    /**
     * Chooses a move for the side to move in [game]'s current position. The game's earlier positions
     * since the last pawn move or capture count for repetition: a line that repeats one of them
     * scores as a draw.
     *
     * Stops at the first of: [LevelParams.depthCap] completed, [LevelParams.nodeBudget] nodes,
     * [LevelParams.deadlineMs] by [nanoTime], or [shouldStop] returning true. The node budget,
     * the deadline and [shouldStop] are checked every [CHECK_INTERVAL] nodes. Depth 1 always
     * completes unless the search is cancelled, so the result is never a random or illegal move;
     * the result of a cancelled search ([StopReason.Cancelled]) is still legal but should be
     * discarded.
     *
     * The level's [LevelParams.randomisation] then picks the move from the root scores of the
     * iteration the search used ([SearchResult.choice] says how). Only an only move and a cancelled
     * search are never randomised.
     *
     * The search itself is deterministic, and the randomisation draws only from [random]. So with the
     * same engine state (a fresh engine, or [newGame] before the game's first move and the same moves
     * since), the same position, parameters without a deadline and the same seed give the same move.
     *
     * @param random the source of every random choice the level makes; seed it for repeatable play.
     * @param nanoTime the clock, injectable for tests.
     * @param shouldStop polled every [CHECK_INTERVAL] nodes; return true to cancel.
     * @throws IllegalArgumentException when the game is over.
     */
    fun search(
        game: Game,
        params: LevelParams,
        random: Random,
        nanoTime: () -> Long = System::nanoTime,
        shouldStop: () -> Boolean = { false },
    ): SearchResult {
        require(game.status == GameStatus.Ongoing) { "Cannot search a finished game (${game.status})" }
        return search.run(game, params, random, nanoTime, shouldStop)
    }

    companion object {
        /** How often, in nodes, the search checks its node budget, deadline and `shouldStop`. */
        const val CHECK_INTERVAL: Int = 1_024
    }
}

/**
 * What a search found.
 *
 * @property move the move to play: always legal in the searched position, never [Move.NONE].
 * @property scoreCp the score in centipawns for the side to move. A forced mate is
 *   `±(MATE_SCORE - plies to mate)`; see [mateInPlies]. For a randomised [choice] it is the score
 *   the search gave [move]: exact for the moves the level picks among, and an upper bound for a
 *   uniformly random move ([MoveChoice.RandomMove]) that the search found worse than the best.
 * @property depth the deepest completed iteration (0 for [StopReason.OnlyMove]).
 * @property nodes positions visited, quiescence included.
 * @property elapsedMs wall-clock time by the search's clock.
 * @property choice how the level chose [move] (design ADR-3). The move can still be the search's best.
 */
data class SearchResult(
    val move: Move,
    val scoreCp: Int,
    val depth: Int,
    val nodes: Long,
    val elapsedMs: Long,
    val stopReason: StopReason,
    val choice: MoveChoice = MoveChoice.Best,
) {
    /** Plies to a forced mate: positive when the side to move mates, negative when it is mated, else null. */
    val mateInPlies: Int?
        get() = when {
            scoreCp >= Score.MATE_BOUND -> MATE_SCORE - scoreCp
            scoreCp <= -Score.MATE_BOUND -> -(MATE_SCORE + scoreCp)
            else -> null
        }

    companion object {
        /** The score of mate on the board; mate in n plies scores `MATE_SCORE - n`. */
        const val MATE_SCORE: Int = Score.MATE
    }
}

/** Why a search stopped. */
enum class StopReason {
    /** The depth cap was completed, or a forced mate was proven and deeper search could not change it. */
    DepthCap,

    /** The node budget ran out. */
    NodeBudget,

    /** The wall-clock deadline passed. */
    Deadline,

    /** `shouldStop` returned true; the caller should discard the result. */
    Cancelled,

    /** There was only one legal move, so nothing was searched. */
    OnlyMove,
}

/**
 * How a level chose its move from the root scores ([Randomisation]). It names the rule that was
 * applied, so the chosen move can still be the search's best move.
 */
enum class MoveChoice {
    /**
     * The search's best move: level 4; level 2 four times in five; level 3 once the game is decided;
     * an only move; or a cancelled search.
     */
    Best,

    /** A uniformly random legal move (level 1, half the time). */
    RandomMove,

    /** The best score after root noise (level 1, the other half). */
    NoisyBest,

    /** A uniform pick among the best-scoring root moves (level 2, one time in five). */
    FromTop,

    /** A uniform pick among root moves within the margin of the best (level 3, while the game is undecided). */
    WithinMargin,
}
