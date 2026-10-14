package chess.engine.board

/**
 * Move generation on the 0x88 board (ADR-2, design §4.2): pseudo-legal moves into a caller-owned
 * `IntArray` of [Move] bits, then a legality filter that makes each move, rejects it if the
 * mover's king is attacked, and unmakes it. The filter handles pins and moving into check without
 * any special cases.
 *
 * Generates every kind of move: pawn pushes, double pushes and captures, promotions to Q/R/B/N
 * (with and without a capture), en passant, knight and king steps, bishop, rook and queen rays, and
 * castling.
 *
 * Castling is generated only when the right is set, the squares between king and rook are empty,
 * the king is not in check and the square it crosses is not attacked. Whether the destination is
 * attacked is left to the legality filter, as for any other king move.
 */
internal object MoveGen {
    /** Move buffer size. The most legal moves any position has is 218; this leaves room for pseudo-legal ones. */
    const val MAX_MOVES: Int = 256

    /**
     * Writes the pseudo-legal moves of the side to move into [moves], starting at index 0, and
     * returns how many it wrote. [moves] must hold at least [MAX_MOVES] entries. The board is not
     * changed.
     */
    fun generatePseudoLegal(board: Board, moves: IntArray): Int {
        val us = board.sideToMove
        var count = 0
        for (from in 0 until 64) {
            val from88 = Square.to0x88(from)
            val piece = board.piece88(from88)
            if (piece == Piece.NONE || Piece.colorOf(piece) != us) continue
            count = when (Piece.typeOf(piece)) {
                Piece.PAWN -> addPawnMoves(board, from88, us, moves, count)
                Piece.KNIGHT -> addSteps(board, from88, us, Attacks.KNIGHT_STEPS, moves, count)
                Piece.BISHOP -> addRays(board, from88, us, Attacks.DIAGONALS, moves, count)
                Piece.ROOK -> addRays(board, from88, us, Attacks.ORTHOGONALS, moves, count)
                Piece.QUEEN -> addRays(board, from88, us, Attacks.KING_STEPS, moves, count)
                else -> addCastling(board, us, moves, addSteps(board, from88, us, Attacks.KING_STEPS, moves, count))
            }
        }
        return count
    }

    /**
     * Writes the legal moves of the side to move into [moves] (at least [MAX_MOVES] entries) and
     * returns how many. The board is made and unmade along the way and ends unchanged.
     */
    fun generateLegal(board: Board, moves: IntArray): Int {
        val pseudo = generatePseudoLegal(board, moves)
        val us = board.sideToMove
        val them = 1 - us
        var legal = 0
        for (i in 0 until pseudo) {
            val move = Move(moves[i])
            board.make(move)
            if (!Attacks.isAttacked(board, board.kingSquare(us), them)) moves[legal++] = move.bits
            board.unmake(move)
        }
        return legal
    }

    /** The legal moves as a list. Allocates; for tests and callers off the search's hot path. */
    fun legalMoves(board: Board): List<Move> {
        val buffer = IntArray(MAX_MOVES)
        val count = generateLegal(board, buffer)
        return List(count) { Move(buffer[it]) }
    }

    private fun addPawnMoves(board: Board, from88: Int, us: Int, moves: IntArray, start: Int): Int {
        var count = start
        val forward = if (us == Piece.WHITE) 16 else -16
        val startRank = if (us == Piece.WHITE) 1 else 6
        val from = Square.from0x88(from88)
        // A pawn one step from the last rank promotes with every push or capture.
        val promotes = Square.rank(from) == (if (us == Piece.WHITE) 6 else 1)

        val one = from88 + forward
        // Pawns never stand on the last rank (FEN validation and promotion), so this is a guard only.
        if (!Square.isOnBoard0x88(one)) return count
        if (board.piece88(one) == Piece.NONE) {
            count = addPawnMove(from, Square.from0x88(one), promotes, 0, moves, count)
            val two = one + forward
            if (Square.rank(from) == startRank && board.piece88(two) == Piece.NONE) {
                moves[count++] = Move.of(from, Square.from0x88(two), flags = Move.DOUBLE_PUSH).bits
            }
        }
        count = addPawnCapture(board, from, one - 1, us, promotes, moves, count)
        return addPawnCapture(board, from, one + 1, us, promotes, moves, count)
    }

    /**
     * A diagonal pawn step onto [to88]: a capture of an enemy piece (four of them when the pawn
     * [promotes]), or en passant onto the empty en passant square. The en passant square always
     * belongs to the side to move, so it needs no colour check.
     */
    private fun addPawnCapture(board: Board, from: Int, to88: Int, us: Int, promotes: Boolean, moves: IntArray, start: Int): Int {
        if (!Square.isOnBoard0x88(to88)) return start
        val target = board.piece88(to88)
        val to = Square.from0x88(to88)
        if (target == Piece.NONE) {
            if (to != board.epSquare) return start
            moves[start] = Move.of(from, to, flags = Move.EN_PASSANT or Move.CAPTURE).bits
            return start + 1
        }
        if (Piece.colorOf(target) == us) return start
        return addPawnMove(from, to, promotes, Move.CAPTURE, moves, start)
    }

    /** One pawn move, or the four promotions (queen first) when the pawn [promotes]. */
    private fun addPawnMove(from: Int, to: Int, promotes: Boolean, flags: Int, moves: IntArray, start: Int): Int {
        if (!promotes) {
            moves[start] = Move.of(from, to, flags = flags).bits
            return start + 1
        }
        moves[start] = Move.of(from, to, Piece.QUEEN, flags).bits
        moves[start + 1] = Move.of(from, to, Piece.ROOK, flags).bits
        moves[start + 2] = Move.of(from, to, Piece.BISHOP, flags).bits
        moves[start + 3] = Move.of(from, to, Piece.KNIGHT, flags).bits
        return start + 4
    }

    /**
     * Castling for [us], encoded as the king's two-square move with [Move.CASTLE]. The board keeps a
     * right only while the king and that rook are on their home squares ([Board.make] clears it
     * otherwise), so a set right is enough to know where they stand.
     */
    private fun addCastling(board: Board, us: Int, moves: IntArray, start: Int): Int {
        val kingside = if (us == Piece.WHITE) CastlingRights.WHITE_KINGSIDE else CastlingRights.BLACK_KINGSIDE
        val queenside = if (us == Piece.WHITE) CastlingRights.WHITE_QUEENSIDE else CastlingRights.BLACK_QUEENSIDE
        val rights = board.castlingRights
        if (rights and (kingside or queenside) == 0) return start

        val them = 1 - us
        val king = if (us == Piece.WHITE) Square.E1 else Square.E8
        if (Attacks.isAttacked(board, king, them)) return start
        var count = start
        if (rights and kingside != 0 &&
            board.pieceAt(king + 1) == Piece.NONE &&
            board.pieceAt(king + 2) == Piece.NONE &&
            !Attacks.isAttacked(board, king + 1, them)
        ) {
            moves[count++] = Move.of(king, king + 2, flags = Move.CASTLE).bits
        }
        if (rights and queenside != 0 &&
            board.pieceAt(king - 1) == Piece.NONE &&
            board.pieceAt(king - 2) == Piece.NONE &&
            board.pieceAt(king - 3) == Piece.NONE &&
            !Attacks.isAttacked(board, king - 1, them)
        ) {
            moves[count++] = Move.of(king, king - 2, flags = Move.CASTLE).bits
        }
        return count
    }

    /** Single steps (knight, king) onto empty or enemy squares. */
    private fun addSteps(board: Board, from88: Int, us: Int, steps: IntArray, moves: IntArray, start: Int): Int {
        var count = start
        val from = Square.from0x88(from88)
        for (step in steps) {
            val to88 = from88 + step
            if (!Square.isOnBoard0x88(to88)) continue
            val target = board.piece88(to88)
            if (target == Piece.NONE) {
                moves[count++] = Move.of(from, Square.from0x88(to88)).bits
            } else if (Piece.colorOf(target) != us) {
                moves[count++] = Move.of(from, Square.from0x88(to88), flags = Move.CAPTURE).bits
            }
        }
        return count
    }

    /** Slides along each direction until the edge or the first piece, capturing it if it is an enemy. */
    private fun addRays(board: Board, from88: Int, us: Int, directions: IntArray, moves: IntArray, start: Int): Int {
        var count = start
        val from = Square.from0x88(from88)
        for (dir in directions) {
            var to88 = from88 + dir
            while (Square.isOnBoard0x88(to88)) {
                val target = board.piece88(to88)
                if (target == Piece.NONE) {
                    moves[count++] = Move.of(from, Square.from0x88(to88)).bits
                } else {
                    if (Piece.colorOf(target) != us) {
                        moves[count++] = Move.of(from, Square.from0x88(to88), flags = Move.CAPTURE).bits
                    }
                    break
                }
                to88 += dir
            }
        }
        return count
    }
}
