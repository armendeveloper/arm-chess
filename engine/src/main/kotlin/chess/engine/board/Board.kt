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
 * for move generation's ray and leap loops ([piece88]). [make] and [unmake] update the board in
 * place and keep what a move destroys on a growable undo stack, so the search does not allocate.
 * The Zobrist key (T-6) is added on top of these fields.
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

    /**
     * What [make] cannot recompute in [unmake], [UNDO_STRIDE] ints per made move: captured piece,
     * castling rights, en passant square and halfmove clock. Grows by doubling when a long game
     * outruns it; never part of [equals] or [copy].
     */
    private var undo = IntArray(INITIAL_UNDO_PLIES * UNDO_STRIDE)

    /** Number of made moves not yet unmade. */
    private var undoCount = 0

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

    /**
     * Plays [move], which must be pseudo-legal for this board (as produced by [MoveGen]). It is not
     * validated, because this is the search's hot path. Legality (not leaving the own king in
     * check) is the caller's business: [MoveGen.generateLegal] makes, tests and unmakes.
     *
     * Updates the side to move, the en passant square (set behind every double push), the halfmove
     * clock (reset by pawn moves and captures) and the fullmove number (after Black's move).
     *
     * @throws UnsupportedOperationException for castling, en passant and promotion, which T-5 adds.
     */
    fun make(move: Move) {
        if (move.bits and (Move.CASTLE or Move.EN_PASSANT) != 0 || move.isPromotion) {
            throw UnsupportedOperationException("Castling, en passant and promotion are not supported yet: ${move.uci()}")
        }
        val from88 = Square.to0x88(move.from)
        val to88 = Square.to0x88(move.to)
        val piece = cells[from88]
        val captured = cells[to88]

        if (undoCount * UNDO_STRIDE == undo.size) undo = undo.copyOf(undo.size * 2)
        val base = undoCount * UNDO_STRIDE
        undo[base] = captured
        undo[base + 1] = castlingRights
        undo[base + 2] = epSquare
        undo[base + 3] = halfmoveClock
        undoCount++

        cells[to88] = piece
        cells[from88] = Piece.NONE
        val type = Piece.typeOf(piece)
        if (type == Piece.KING) kingSquares[sideToMove] = move.to

        // T-5: castling (the rook's move), en passant (remove the pawn behind the target square),
        // promotion (replace the pawn) and castling-right updates go here.
        epSquare = if (move.isDoublePush) (move.from + move.to) shr 1 else Square.NONE
        halfmoveClock = if (type == Piece.PAWN || captured != Piece.NONE) 0 else halfmoveClock + 1
        if (sideToMove == Piece.BLACK) fullmoveNumber++
        sideToMove = 1 - sideToMove
    }

    /**
     * Takes back [move], which must be the last move [make] played and not yet unmade. Restores
     * every field, so the board [equals] its state before [make].
     *
     * @throws IllegalStateException when no move is left to unmake.
     */
    fun unmake(move: Move) {
        check(undoCount > 0) { "No move to unmake: ${move.uci()}" }
        undoCount--
        val base = undoCount * UNDO_STRIDE

        sideToMove = 1 - sideToMove
        if (sideToMove == Piece.BLACK) fullmoveNumber--
        val from88 = Square.to0x88(move.from)
        val to88 = Square.to0x88(move.to)
        val piece = cells[to88]
        cells[from88] = piece
        cells[to88] = undo[base]
        if (Piece.typeOf(piece) == Piece.KING) kingSquares[sideToMove] = move.from

        castlingRights = undo[base + 1]
        epSquare = undo[base + 2]
        halfmoveClock = undo[base + 3]
    }

    /** Empties the board, resets every field to its default and drops the undo history. */
    fun clear() {
        undoCount = 0
        cells.fill(Piece.NONE)
        kingSquares.fill(Square.NONE)
        sideToMove = Piece.WHITE
        castlingRights = CastlingRights.NONE
        epSquare = Square.NONE
        halfmoveClock = 0
        fullmoveNumber = 1
    }

    /** A deep copy of the position. The copy starts with an empty undo history. */
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

    private companion object {
        const val UNDO_STRIDE = 4

        /** Plenty for a search from any game position; long game replays grow the stack. */
        const val INITIAL_UNDO_PLIES = 256
    }
}
