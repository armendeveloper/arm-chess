package chess.engine.rules

import chess.engine.board.Attacks
import chess.engine.board.Board
import chess.engine.board.Fen
import chess.engine.board.FenException
import chess.engine.board.Move
import chess.engine.board.MoveGen
import chess.engine.board.PieceColor
import chess.engine.board.Square
import java.util.Collections

/**
 * A move that is not legal where it was played: not in the position's legal moves, not a valid UCI
 * move, or played after the game ended. The message is for logs, not for players.
 */
class IllegalMoveException(message: String) : IllegalArgumentException(message)

/**
 * An immutable chess position (design §4.1): placement, side to move, castling rights, en passant
 * square and clocks. Safe to share between threads; [play] returns a new position.
 *
 * Squares are 0..63 ([Square]) and pieces are [chess.engine.board.Piece] codes.
 *
 * **Repetition identity (US-5).** Two positions are the same for threefold repetition when they have
 * the same placement, side to move, castling rights and en passant capture availability; the clocks
 * do not count, and an en passant square counts only when a legal en passant capture exists.
 * [repetitionKey] is a Zobrist hash of exactly that, and [samePositionAs] is the exact comparison, so
 * a hash collision can never end a game. [equals] is stricter: it compares every FEN field.
 */
class Position private constructor(private val board: Board) {
    val sideToMove: PieceColor = PieceColor.fromCode(board.sideToMove)

    /** A combination of [chess.engine.board.CastlingRights] bits. */
    val castlingRights: Int = board.castlingRights

    /** The en passant target square as written in FEN (after every double push), or [Square.NONE]. */
    val enPassantSquare: Int = board.epSquare

    val halfmoveClock: Int = board.halfmoveClock
    val fullmoveNumber: Int = board.fullmoveNumber

    /** True when the side to move is in check. */
    val isCheck: Boolean = Attacks.isInCheck(board, board.sideToMove)

    /** Zobrist key of the repetition identity (see the class comment). Equal for [samePositionAs] positions. */
    val repetitionKey: Long = board.key

    /** The en passant square if a legal en passant capture exists, else [Square.NONE]. */
    private val enPassantCaptureSquare: Int = if (board.hasHashedEnPassant) board.epSquare else Square.NONE

    // Move generation makes and unmakes moves, so it runs on a copy: [board] is never changed after
    // construction, which is what makes a Position safe to read from several threads.
    private val legalMoves: List<Move> by lazy {
        Collections.unmodifiableList(MoveGen.legalMoves(board.copy(undoPlies = 1)))
    }

    /** The piece code on [sq] (0..63), or [chess.engine.board.Piece.NONE]. */
    fun pieceAt(sq: Int): Int {
        require(sq in 0..63) { "Square index must be in 0..63, was $sq" }
        return board.pieceAt(sq)
    }

    /** The square (0..63) of the king of [color]. */
    fun kingSquare(color: PieceColor): Int = board.kingSquare(color.code)

    /** The legal moves of the side to move, generated once. Empty for checkmate and stalemate. */
    fun legalMoves(): List<Move> = legalMoves

    /** True when [move] is one of [legalMoves] (all bits, flags included, must match). */
    fun isLegal(move: Move): Boolean = move in legalMoves

    /**
     * The position after [move], which must be one of [legalMoves].
     *
     * @throws IllegalMoveException when it is not.
     */
    fun play(move: Move): Position {
        if (!isLegal(move)) throw IllegalMoveException("${move.uci()} is not legal in ${toFen()}")
        val next = board.copy(undoPlies = 1)
        next.make(move)
        return Position(next)
    }

    /** True when this and [other] are the same position for threefold repetition (see the class comment). */
    fun samePositionAs(other: Position): Boolean =
        repetitionKey == other.repetitionKey &&
            sideToMove == other.sideToMove &&
            castlingRights == other.castlingRights &&
            enPassantCaptureSquare == other.enPassantCaptureSquare &&
            board.samePlacementAs(other.board)

    fun toFen(): String = Fen.format(board)

    /** A mutable copy of the board, for the search. */
    internal fun toBoard(): Board = board.copy()

    /** Equal when every FEN field is equal, clocks included. */
    override fun equals(other: Any?): Boolean = this === other || (other is Position && board == other.board)

    override fun hashCode(): Int = board.hashCode()

    override fun toString(): String = toFen()

    companion object {
        const val START_FEN: String = Fen.START

        /** The standard start position. */
        val START: Position = fromFen(START_FEN)

        /**
         * Parses [fen] (6 fields, or 4 without clocks, which then default to `0 1`).
         *
         * @throws FenException when the FEN is malformed or the position is impossible, including when
         *   the side not to move is in check.
         */
        fun fromFen(fen: String): Position {
            val board = Fen.parse(fen)
            if (Attacks.isInCheck(board, 1 - board.sideToMove)) {
                val color = if (board.sideToMove == PieceColor.WHITE.code) "Black" else "White"
                throw FenException("$color is in check but it is not $color's turn")
            }
            return of(board)
        }

        /** Wraps a board the caller no longer changes. */
        internal fun of(board: Board): Position = Position(board.copy(undoPlies = 1))
    }
}
