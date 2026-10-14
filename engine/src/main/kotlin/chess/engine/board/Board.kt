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
 *
 * [key] is the [Zobrist] key, kept up to date by [make] and [unmake]. Writing the fields directly
 * ([setPiece], [sideToMove] and so on) does not update it: call [resetKey] afterwards, as
 * [Fen.parse] does.
 *
 * [undoPlies] is the initial undo stack depth. It grows on demand; snapshots that make at most one
 * move (`Position`) pass 1 to save memory.
 */
internal class Board(undoPlies: Int = INITIAL_UNDO_PLIES) {
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

    /** The [Zobrist] key of the position: placement, side to move, castling rights and legal en passant. */
    var key: Long = 0L
        private set

    /**
     * The file whose en passant key is in [key] (a legal en passant capture exists), or -1. Kept so
     * that [make] can take it out again without repeating the legality test.
     */
    private var hashedEpFile = -1

    /** True when [key] includes an en passant file, that is, the side to move has a legal en passant capture. */
    val hasHashedEnPassant: Boolean get() = hashedEpFile >= 0

    /**
     * What [make] cannot recompute in [unmake], [UNDO_STRIDE] ints per made move: captured piece,
     * castling rights, en passant square, halfmove clock and hashed en passant file; plus the key in
     * [undoKeys]. Grows by doubling when a long game outruns it; never part of [equals] or [copy].
     */
    private var undo = IntArray(maxOf(1, undoPlies) * UNDO_STRIDE)
    private var undoKeys = LongArray(maxOf(1, undoPlies))

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
     * the halfmove clock (reset by pawn moves and captures), the fullmove number (after Black's
     * move) and the [key]. The key gets the en passant file only when the opponent has a legal en
     * passant capture after a double push.
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

        if (undoCount == undoKeys.size) {
            undo = undo.copyOf(undo.size * 2)
            undoKeys = undoKeys.copyOf(undoKeys.size * 2)
        }
        val base = undoCount * UNDO_STRIDE
        undo[base] = captured
        undo[base + 1] = castlingRights
        undo[base + 2] = epSquare
        undo[base + 3] = halfmoveClock
        undo[base + 4] = hashedEpFile
        undoKeys[undoCount] = key
        undoCount++

        val placed = if (move.isPromotion) Piece.of(us, move.promotion) else piece
        var k = key xor Zobrist.SIDE xor
            Zobrist.piece(piece, from) xor
            Zobrist.piece(captured, Square.from0x88(victim88)) xor
            Zobrist.piece(placed, to)
        if (hashedEpFile >= 0) k = k xor Zobrist.enPassant(hashedEpFile)

        cells[victim88] = Piece.NONE
        cells[to88] = placed
        cells[from88] = Piece.NONE
        val type = Piece.typeOf(piece)
        if (type == Piece.KING) {
            kingSquares[us] = to
            if (move.isCastle) k = k xor moveCastlingRook(to88, toCastled = true)
        }

        val rights = castlingRights and CASTLING_MASK[from] and CASTLING_MASK[to]
        k = k xor Zobrist.castling(castlingRights) xor Zobrist.castling(rights)
        castlingRights = rights
        halfmoveClock = if (type == Piece.PAWN || captured != Piece.NONE) 0 else halfmoveClock + 1
        if (us == Piece.BLACK) fullmoveNumber++
        sideToMove = 1 - us

        // The en passant square is always recorded (as in FEN); the key only counts a legal capture.
        hashedEpFile = -1
        if (move.isDoublePush) {
            epSquare = (from + to) shr 1
            if (hasLegalEnPassant()) {
                hashedEpFile = Square.file(epSquare)
                k = k xor Zobrist.enPassant(hashedEpFile)
            }
        } else {
            epSquare = Square.NONE
        }
        key = k
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
        hashedEpFile = undo[base + 4]
        key = undoKeys[undoCount]
    }

    /**
     * True when the side to move has a legal en passant capture: a pawn beside the pawn that just
     * double-pushed may take it without leaving its own king attacked. This catches the rank pin
     * where both pawns leave the king's rank, and allows the capture that removes a checking pawn.
     * The board is changed and restored during the test. Without a king on the board, any
     * pseudo-legal capture counts.
     */
    fun hasLegalEnPassant(): Boolean {
        val ep = epSquare
        if (ep == Square.NONE) return false
        val us = sideToMove
        val ep88 = Square.to0x88(ep)
        val victim88 = enPassantVictim88(ep88, us)
        return isLegalEnPassantFrom(victim88 - 1, victim88, ep88, us) ||
            isLegalEnPassantFrom(victim88 + 1, victim88, ep88, us)
    }

    /** True when a pawn of [us] stands on [from88] and may legally take the pawn on [victim88] by moving to [ep88]. */
    private fun isLegalEnPassantFrom(from88: Int, victim88: Int, ep88: Int, us: Int): Boolean {
        val pawn = Piece.of(us, Piece.PAWN)
        if (!Square.isOnBoard0x88(from88) || cells[from88] != pawn) return false
        val king = kingSquares[us]
        if (king == Square.NONE) return true
        val victim = cells[victim88]
        cells[from88] = Piece.NONE
        cells[victim88] = Piece.NONE
        cells[ep88] = pawn
        val legal = !Attacks.isAttacked(this, king, 1 - us)
        cells[ep88] = Piece.NONE
        cells[victim88] = victim
        cells[from88] = pawn
        return legal
    }

    /** Recomputes [key] from scratch, after the fields were written directly (FEN parsing, tests). */
    fun resetKey() {
        hashedEpFile = if (hasLegalEnPassant()) Square.file(epSquare) else -1
        key = Zobrist.compute(this)
    }

    /** True when both boards have the same piece on every square (the other fields are not compared). */
    fun samePlacementAs(other: Board): Boolean = cells.contentEquals(other.cells)

    /** The 0x88 square of the pawn an en passant capture by [us] onto [to88] removes: one rank behind. */
    private fun enPassantVictim88(to88: Int, us: Int): Int = if (us == Piece.WHITE) to88 - 16 else to88 + 16

    /**
     * Moves the castling rook for a king that castled onto [kingTo88] (g- or c-file): from its corner
     * to the square the king crossed when [toCastled], and back again otherwise. Returns the change
     * to the Zobrist key, which is the same in both directions.
     */
    private fun moveCastlingRook(kingTo88: Int, toCastled: Boolean): Long {
        val kingside = (kingTo88 and 7) == 6
        val corner88 = if (kingside) kingTo88 + 1 else kingTo88 - 2
        val crossed88 = if (kingside) kingTo88 - 1 else kingTo88 + 1
        val from88 = if (toCastled) corner88 else crossed88
        val to88 = if (toCastled) crossed88 else corner88
        val rook = cells[from88]
        cells[to88] = rook
        cells[from88] = Piece.NONE
        return Zobrist.piece(rook, Square.from0x88(corner88)) xor Zobrist.piece(rook, Square.from0x88(crossed88))
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
        hashedEpFile = -1
        key = 0L
    }

    /**
     * A deep copy of the position, key included. The copy starts with an empty undo history, sized
     * for [undoPlies] moves.
     */
    fun copy(undoPlies: Int = INITIAL_UNDO_PLIES): Board {
        val other = Board(undoPlies)
        cells.copyInto(other.cells)
        kingSquares.copyInto(other.kingSquares)
        other.sideToMove = sideToMove
        other.castlingRights = castlingRights
        other.epSquare = epSquare
        other.halfmoveClock = halfmoveClock
        other.fullmoveNumber = fullmoveNumber
        other.hashedEpFile = hashedEpFile
        other.key = key
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
            key == other.key &&
            hashedEpFile == other.hashedEpFile &&
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
        h = 31 * h + key.hashCode()
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
        const val UNDO_STRIDE = 5

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
