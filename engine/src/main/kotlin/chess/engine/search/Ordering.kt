package chess.engine.search

import chess.engine.board.Board
import chess.engine.board.Move
import chess.engine.board.Piece

/**
 * Move ordering (design §4.6): the transposition-table move first, then captures and promotions by
 * MVV-LVA (most valuable victim, then least valuable attacker), then two killer moves per ply, then
 * the remaining quiet moves by the history heuristic.
 *
 * Killers and history are per [Engine]: killers are reset for every search; history is halved
 * between searches and cleared by [clear] (a new game).
 */
internal class Ordering(private val maxPly: Int) {
    private val killers = IntArray(maxPly * 2)

    /** Indexed by `(side * 64 + from) * 64 + to`. */
    private val history = IntArray(2 * 64 * 64)

    fun clear() {
        killers.fill(0)
        history.fill(0)
    }

    /** Called at the start of every search: old killers are meaningless, old history fades. */
    fun newSearch() {
        killers.fill(0)
        for (i in history.indices) history[i] = history[i] shr 1
    }

    /** The ordering score of [move] for the side to move on [board], [ply] plies from the root. */
    fun score(board: Board, move: Move, ttMove: Move, ply: Int): Int {
        if (move == ttMove) return TT_MOVE
        if (move.isCapture || move.isPromotion) return tacticalScore(board, move)
        val bits = move.bits
        if (bits == killers[ply * 2]) return KILLER_1
        if (bits == killers[ply * 2 + 1]) return KILLER_2
        return history[historyIndex(board.sideToMove, move)]
    }

    /** MVV-LVA for captures (en passant takes a pawn), plus the promoted piece's value for promotions. */
    fun tacticalScore(board: Board, move: Move): Int {
        var score = TACTICAL
        if (move.isCapture) {
            val victim = if (move.isEnPassant) Piece.PAWN else Piece.typeOf(board.pieceAt(move.to))
            val attacker = Piece.typeOf(board.pieceAt(move.from))
            score += victim * 16 - attacker
        }
        if (move.isPromotion) score += move.promotion * 16
        return score
    }

    /** Records a quiet move that caused a beta cut-off at [ply], searched to [depth]. */
    fun onQuietCutoff(side: Int, move: Move, ply: Int, depth: Int) {
        val bits = move.bits
        if (killers[ply * 2] != bits) {
            killers[ply * 2 + 1] = killers[ply * 2]
            killers[ply * 2] = bits
        }
        val index = historyIndex(side, move)
        history[index] = (history[index] + depth * depth).coerceAtMost(HISTORY_MAX)
    }

    private fun historyIndex(side: Int, move: Move): Int = ((side shl 6) or move.from) shl 6 or move.to

    companion object {
        private const val TT_MOVE = 1_000_000
        private const val TACTICAL = 100_000
        private const val KILLER_1 = 90_000
        private const val KILLER_2 = 89_000

        /** History stays below the killers. */
        private const val HISTORY_MAX = 80_000
    }
}
