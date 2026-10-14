package chess.engine.rules

import chess.engine.board.Board
import chess.engine.board.Piece
import chess.engine.board.Square

/**
 * The automatic draw rules of US-5 that do not depend on the side to move having no moves:
 * insufficient material, threefold repetition and the 50-move rule. [GameStatus] decides the order.
 */
internal object Draws {
    /** Halfmoves without a pawn move or capture after which the game is drawn (50 moves each). */
    const val FIFTY_MOVE_HALFMOVES: Int = 100

    /** How many times the same position must occur for the automatic repetition draw. */
    const val REPETITIONS: Int = 3

    fun isInsufficientMaterial(position: Position): Boolean = isInsufficientMaterial { position.pieceAt(it) }

    /** As for a [Position]; for the search (T-8), which works on a [Board]. */
    fun isInsufficientMaterial(board: Board): Boolean = isInsufficientMaterial { board.pieceAt(it) }

    /**
     * True when the material is exactly one of the PRD's four cases: K v K, K+B v K, K+N v K, or K+B v
     * K+B with the two bishops (one per side) on the same square colour. This is the PRD list, not
     * FIDE's wider "dead position" rule, so for example K+B+B v K with same-coloured bishops is not a
     * draw here.
     */
    private inline fun isInsufficientMaterial(pieceAt: (Int) -> Int): Boolean {
        var minors = 0
        var knights = 0
        // Square colour ((file + rank) and 1) of each side's bishop, or -1.
        val bishopSquareColor = intArrayOf(-1, -1)
        for (sq in 0 until 64) {
            val piece = pieceAt(sq)
            when (Piece.typeOf(piece)) {
                Piece.NONE, Piece.KING -> continue
                Piece.KNIGHT -> knights++
                Piece.BISHOP -> {
                    val color = Piece.colorOf(piece)
                    if (bishopSquareColor[color] != -1) return false // two bishops on one side
                    bishopSquareColor[color] = (Square.file(sq) + Square.rank(sq)) and 1
                }
                else -> return false // a pawn, rook or queen
            }
            if (++minors > 2) return false
        }
        return when (minors) {
            0, 1 -> true
            else -> knights == 0 && bishopSquareColor[Piece.WHITE] == bishopSquareColor[Piece.BLACK]
        }
    }

    /**
     * True when the last of [positions] has occurred at least [REPETITIONS] times. Only positions
     * since the last pawn move or capture can match (the halfmove clock counts them), and of those
     * only the ones with the same side to move. Lost castling rights and en passant availability are
     * part of [Position.samePositionAs].
     */
    fun isThreefoldRepetition(positions: List<Position>): Boolean {
        val last = positions.lastIndex
        val current = positions[last]
        val earliest = maxOf(0, last - current.halfmoveClock)
        var occurrences = 1
        var i = last - 2
        while (i >= earliest) {
            if (positions[i].samePositionAs(current) && ++occurrences >= REPETITIONS) return true
            i -= 2
        }
        return false
    }

    fun isFiftyMoveRule(position: Position): Boolean = position.halfmoveClock >= FIFTY_MOVE_HALFMOVES
}
