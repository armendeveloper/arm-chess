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
     * Special moves: castling (the king's move, flagged [Move.CASTLE]) also moves the rook; en
     * passant removes the pawn behind the target square; a promotion puts the promoted piece on the
     * target square instead of the pawn.
     *
     * Updates the side to move, the castling rights (lost when the king or a rook leaves its home
     * square, or a rook is captured on it), the en passant square (set behind every double push),
     * the halfmove clock (reset by pawn moves and captures) and the fullmove number (after Black's
     * move).
     */
    fun make(move: Move) {
        val from = move.from
        val to = move.to
        val from88 = Square.to0x88(from)
        val to88 = Square.to0x88(to)
        val piece = cells[from88]
        val us = sideToMove
        val victim88 = if (move.isEnPassant) enPassantVictim88(to88, us) else to88
        val captured = cells[victim88]

        if (undoCount * UNDO_STRIDE == undo.size) undo = undo.copyOf(undo.size * 2)
        val base = undoCount * UNDO_STRIDE
        undo[base] = captured
        undo[base + 1] = castlingRights
        undo[base + 2] = epSquare
        undo[base + 3] = halfmoveClock
        undoCount++

        cells[victim88] = Piece.NONE
        cells[to88] = if (move.isPromotion) Piece.of(us, move.promotion) else piece
        cells[from88] = Piece.NONE
        val type = Piece.typeOf(piece)
        if (type == Piece.KING) {
            kingSquares[us] = to
            if (move.isCastle) moveCastlingRook(to88, toCastled = true)
        }

        castlingRights = castlingRights and CASTLING_MASK[from] and CASTLING_MASK[to]
        epSquare = if (move.isDoublePush) (from + to) shr 1 else Square.NONE
        halfmoveClock = if (type == Piece.PAWN || captured != Piece.NONE) 0 else halfmoveClock + 1
        if (us == Piece.BLACK) fullmoveNumber++
        sideToMove = 1 - us
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

        val us = 1 - sideToMove
        sideToMove = us
        if (us == Piece.BLACK) fullmoveNumber--
        val from88 = Square.to0x88(move.from)
        val to88 = Square.to0x88(move.to)
        val piece = if (move.isPromotion) Piece.of(us, Piece.PAWN) else cells[to88]
        cells[from88] = piece
        if (move.isEnPassant) {
            cells[to88] = Piece.NONE
            cells[enPassantVictim88(to88, us)] = undo[base]
        } else {
            cells[to88] = undo[base]
        }
        if (Piece.typeOf(piece) == Piece.KING) {
            kingSquares[us] = move.from
            if (move.isCastle) moveCastlingRook(to88, toCastled = false)
        }

        castlingRights = undo[base + 1]
        epSquare = undo[base + 2]
        halfmoveClock = undo[base + 3]
    }

    /** The 0x88 square of the pawn an en passant capture by [us] onto [to88] removes: one rank behind. */
    private fun enPassantVictim88(to88: Int, us: Int): Int = if (us == Piece.WHITE) to88 - 16 else to88 + 16

    /**
     * Moves the castling rook for a king that castled onto [kingTo88] (g- or c-file): from its corner
     * to the square the king crossed when [toCastled], and back again otherwise.
     */
    private fun moveCastlingRook(kingTo88: Int, toCastled: Boolean) {
        val kingside = (kingTo88 and 7) == 6
        val corner88 = if (kingside) kingTo88 + 1 else kingTo88 - 2
        val crossed88 = if (kingside) kingTo88 - 1 else kingTo88 + 1
        val from88 = if (toCastled) corner88 else crossed88
        val to88 = if (toCastled) crossed88 else corner88
        cells[to88] = cells[from88]
        cells[from88] = Piece.NONE
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

        /**
         * Castling rights that survive a move from or to each square (0..63): a move touching a
         * king or rook home square clears the rights that depend on it, every other square keeps
         * them all. [make] ands the entries for both squares, which covers king moves, rook moves
         * and rooks captured at home.
         */
        val CASTLING_MASK = IntArray(64) { CastlingRights.ALL }.also {
            it[Square.A1] = CastlingRights.ALL and CastlingRights.WHITE_QUEENSIDE.inv()
            it[Square.E1] = CastlingRights.ALL and (CastlingRights.WHITE_KINGSIDE or CastlingRights.WHITE_QUEENSIDE).inv()
            it[Square.H1] = CastlingRights.ALL and CastlingRights.WHITE_KINGSIDE.inv()
            it[Square.A8] = CastlingRights.ALL and CastlingRights.BLACK_QUEENSIDE.inv()
            it[Square.E8] = CastlingRights.ALL and (CastlingRights.BLACK_KINGSIDE or CastlingRights.BLACK_QUEENSIDE).inv()
            it[Square.H8] = CastlingRights.ALL and CastlingRights.BLACK_KINGSIDE.inv()
        }
    }
}
