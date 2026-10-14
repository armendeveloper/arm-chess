package chess.engine.board

/**
 * Move generation on the 0x88 board (ADR-2, design §4.2): pseudo-legal moves into a caller-owned
 * `IntArray` of [Move] bits, then a legality filter that makes each move, rejects it if the
 * mover's king is attacked, and unmakes it. The filter handles pins and moving into check without
 * any special cases.
 *
 * Covered so far (T-4): pawn pushes, double pushes and captures; knight and king steps; bishop,
 * rook and queen rays. Castling, en passant and promotions are added in T-5; until then a pawn
 * move onto the last rank is not generated at all.
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
                // T-5: castling is generated with the king's steps.
                else -> addSteps(board, from88, us, Attacks.KING_STEPS, moves, count)
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
        val lastRank = if (us == Piece.WHITE) 7 else 0
        val from = Square.from0x88(from88)

        val one = from88 + forward
        // T-5: a push or capture onto the last rank becomes four promotions.
        if (Square.isOnBoard0x88(one) && Square.rank(Square.from0x88(one)) != lastRank) {
            if (board.piece88(one) == Piece.NONE) {
                moves[count++] = Move.of(from, Square.from0x88(one)).bits
                val two = one + forward
                if (Square.rank(from) == startRank && board.piece88(two) == Piece.NONE) {
                    moves[count++] = Move.of(from, Square.from0x88(two), flags = Move.DOUBLE_PUSH).bits
                }
            }
            count = addPawnCapture(board, from, one - 1, us, moves, count)
            count = addPawnCapture(board, from, one + 1, us, moves, count)
        }
        return count
    }

    private fun addPawnCapture(board: Board, from: Int, to88: Int, us: Int, moves: IntArray, start: Int): Int {
        if (!Square.isOnBoard0x88(to88)) return start
        val target = board.piece88(to88)
        // T-5: en passant captures onto the empty ep square.
        if (target == Piece.NONE || Piece.colorOf(target) == us) return start
        moves[start] = Move.of(from, Square.from0x88(to88), flags = Move.CAPTURE).bits
        return start + 1
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
