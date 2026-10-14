package chess.engine.rules

import chess.engine.board.Move
import chess.engine.board.Piece
import chess.engine.board.Square

/**
 * UCI long algebraic notation (design §4.4): from square, to square and a lowercase promotion letter,
 * e.g. "e2e4", "e1g1" (castling is the king's move), "e5d6" (en passant), "e7e8q". The save format
 * stores moves this way (ADR-5).
 */
object Uci {
    /** The UCI text of [move], e.g. "e7e8q". */
    fun format(move: Move): String = move.uci()

    /**
     * The legal move of [position] written [text]. The text is strict: exactly four or five lowercase
     * characters, with a promotion letter (q, r, b or n) exactly when the move promotes. Castling and
     * en passant flags come from the matching legal move.
     *
     * @throws IllegalMoveException when [text] is malformed or names no legal move.
     */
    fun parse(position: Position, text: String): Move =
        parseOrNull(position, text) ?: throw IllegalMoveException("'$text' is not a legal UCI move in ${position.toFen()}")

    /** Like [parse], but null instead of an exception. */
    fun parseOrNull(position: Position, text: String): Move? {
        if (text.length != 4 && text.length != 5) return null
        val from = Square.parseOrNull(text.substring(0, 2)) ?: return null
        val to = Square.parseOrNull(text.substring(2, 4)) ?: return null
        val promotion = if (text.length == 5) promotionCode(text[4]) ?: return null else Piece.NONE
        return position.legalMoves().firstOrNull { it.from == from && it.to == to && it.promotion == promotion }
    }

    private fun promotionCode(ch: Char): Int? = when (ch) {
        'q' -> Piece.QUEEN
        'r' -> Piece.ROOK
        'b' -> Piece.BISHOP
        'n' -> Piece.KNIGHT
        else -> null
    }
}
