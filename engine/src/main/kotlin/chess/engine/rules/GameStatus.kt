package chess.engine.rules

import chess.engine.board.PieceColor

/**
 * Whether a game is still going and, if not, why it ended (US-5, design §4.3). Draws by threefold
 * repetition and the 50-move rule are automatic (plan decision OQ-3): no claim is needed.
 *
 * These are reasons, not text. The app maps each one to its own localized title and explanation
 * (design 4.3.7). Resignation is not here, because it cannot be derived from the moves; it belongs
 * to the app's `GameResult`.
 */
sealed interface GameStatus {
    /** False only for [Ongoing]. */
    val isOver: Boolean get() = this != Ongoing

    /** The game goes on. */
    data object Ongoing : GameStatus

    /** The side to move is in check and has no legal move; [winner] gave the mate. */
    data class Checkmate(val winner: PieceColor) : GameStatus

    /** Every way a game can end in a draw. */
    sealed interface Draw : GameStatus

    /** The side to move is not in check and has no legal move. */
    data object Stalemate : Draw

    /** Exactly K v K, K+B v K, K+N v K, or K+B v K+B with both bishops on the same square colour. */
    data object InsufficientMaterial : Draw

    /** The current position has occurred for the third time (see [Position.samePositionAs]). */
    data object ThreefoldRepetition : Draw

    /** 100 halfmoves in a row without a pawn move or a capture, and the last one was not mate. */
    data object FiftyMoveRule : Draw

    companion object {
        /**
         * The status of a game whose positions so far are [positions] (oldest first; the last one is
         * current), checked in the order of design §4.3, so that a mate on the 100th halfmove wins.
         */
        internal fun of(positions: List<Position>): GameStatus {
            val position = positions.last()
            return when {
                position.legalMoves().isEmpty() ->
                    if (position.isCheck) Checkmate(winner = position.sideToMove.opposite) else Stalemate
                Draws.isInsufficientMaterial(position) -> InsufficientMaterial
                Draws.isThreefoldRepetition(positions) -> ThreefoldRepetition
                Draws.isFiftyMoveRule(position) -> FiftyMoveRule
                else -> Ongoing
            }
        }
    }
}
