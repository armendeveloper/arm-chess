package chess.engine.board

/** Castling-right bits stored in [Board.castlingRights]. */
object CastlingRights {
    const val NONE: Int = 0
    const val WHITE_KINGSIDE: Int = 1
    const val WHITE_QUEENSIDE: Int = 2
    const val BLACK_KINGSIDE: Int = 4
    const val BLACK_QUEENSIDE: Int = 8
    const val ALL: Int = 15
}

/**
 * The mutable 0x88 board (ADR-2, design §4.1). Internal to the engine; the app sees immutable
 * snapshots (`Position`, T-6).
 *
 * Every accessor takes and returns 0..63 squares ([Square]); only the piece array is 0x88-indexed,
 * for move generation's ray and leap loops ([piece88]). Move generation, make/unmake with its
 * undo stack (T-4) and the Zobrist key (T-6) are added on top of these fields.
 */
internal class Board {
    /** Piece codes indexed by 0x88 square. Off-board slots always hold [Piece.NONE]. */
    private val cells = IntArray(128)

    /** King square (0..63) per colour code, or [Square.NONE]. Kept in step by [setPiece]. */
    private val kingSquares = intArrayOf(Square.NONE, Square.NONE)

    /** [Piece.WHITE] or [Piece.BLACK]. */
    var sideToMove: Int = Piece.WHITE

    /** A combination of [CastlingRights] bits. */
    var castlingRights: Int = CastlingRights.NONE

    /** The en passant target square (0..63) as written in FEN, or [Square.NONE]. */
    var epSquare: Int = Square.NONE

    var halfmoveClock: Int = 0
    var fullmoveNumber: Int = 1

    fun pieceAt(sq: Int): Int = cells[Square.to0x88(sq)]

    /** Piece at a 0x88 index; [sq88] must be in 0..127. */
    fun piece88(sq88: Int): Int = cells[sq88]

    fun kingSquare(color: Int): Int = kingSquares[color]

    /** Puts [piece] (or [Piece.NONE]) on [sq], keeping the king squares in step. */
    fun setPiece(sq: Int, piece: Int) {
        val sq88 = Square.to0x88(sq)
        val old = cells[sq88]
        if (Piece.typeOf(old) == Piece.KING && kingSquares[Piece.colorOf(old)] == sq) {
            kingSquares[Piece.colorOf(old)] = Square.NONE
        }
        cells[sq88] = piece
        if (Piece.typeOf(piece) == Piece.KING) kingSquares[Piece.colorOf(piece)] = sq
    }

    /** Empties the board and resets every field to its default. */
    fun clear() {
        cells.fill(Piece.NONE)
        kingSquares.fill(Square.NONE)
        sideToMove = Piece.WHITE
        castlingRights = CastlingRights.NONE
        epSquare = Square.NONE
        halfmoveClock = 0
        fullmoveNumber = 1
    }

    fun copy(): Board {
        val other = Board()
        cells.copyInto(other.cells)
        kingSquares.copyInto(other.kingSquares)
        other.sideToMove = sideToMove
        other.castlingRights = castlingRights
        other.epSquare = epSquare
        other.halfmoveClock = halfmoveClock
        other.fullmoveNumber = fullmoveNumber
        return other
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Board) return false
        return sideToMove == other.sideToMove &&
            castlingRights == other.castlingRights &&
            epSquare == other.epSquare &&
            halfmoveClock == other.halfmoveClock &&
            fullmoveNumber == other.fullmoveNumber &&
            kingSquares.contentEquals(other.kingSquares) &&
            cells.contentEquals(other.cells)
    }

    override fun hashCode(): Int {
        var h = cells.contentHashCode()
        h = 31 * h + sideToMove
        h = 31 * h + castlingRights
        h = 31 * h + epSquare
        h = 31 * h + halfmoveClock
        h = 31 * h + fullmoveNumber
        return h
    }

    /** An 8x8 diagram, rank 8 first, for test failures and debugging. */
    override fun toString(): String = buildString {
        for (rank in 7 downTo 0) {
            append(rank + 1)
            for (file in 0 until 8) {
                val piece = pieceAt(Square.of(file, rank))
                append(' ').append(if (piece == Piece.NONE) '.' else Piece.toFenChar(piece))
            }
            append('\n')
        }
        append("  a b c d e f g h")
    }
}
