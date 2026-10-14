package chess.engine.board

/**
 * Attack detection on the 0x88 board (design §4.2). [isAttacked] scans outward from the target
 * square: pawn and leaper squares once, then each ray until the first piece. It needs no attack
 * tables and is the legality test used by [MoveGen] (make, test the mover's king, unmake).
 */
internal object Attacks {
    /** 0x88 steps of a knight. */
    val KNIGHT_STEPS: IntArray = intArrayOf(33, 31, 18, 14, -14, -18, -31, -33)

    /** 0x88 steps of a king, which are also the eight ray directions. */
    val KING_STEPS: IntArray = intArrayOf(1, 15, 16, 17, -1, -15, -16, -17)

    /** Diagonal ray directions (bishop, queen). */
    val DIAGONALS: IntArray = intArrayOf(15, 17, -15, -17)

    /** Orthogonal ray directions (rook, queen). */
    val ORTHOGONALS: IntArray = intArrayOf(1, 16, -1, -16)

    /**
     * True when any piece of colour [by] attacks square [sq] (0..63), whatever stands on [sq]. Pins
     * on the attacker are ignored, as the rules require for check. [sq] must be a real square.
     */
    fun isAttacked(board: Board, sq: Int, by: Int): Boolean {
        val target = Square.to0x88(sq)

        // Pawns attack diagonally forward, so an attacking pawn stands one rank behind the target
        // (from the attacker's point of view) on an adjacent file.
        val pawn = Piece.of(by, Piece.PAWN)
        val behind = if (by == Piece.WHITE) -16 else 16
        if (pieceOn(board, target + behind - 1) == pawn || pieceOn(board, target + behind + 1) == pawn) return true

        val knight = Piece.of(by, Piece.KNIGHT)
        for (step in KNIGHT_STEPS) if (pieceOn(board, target + step) == knight) return true

        val king = Piece.of(by, Piece.KING)
        for (step in KING_STEPS) if (pieceOn(board, target + step) == king) return true

        val queen = Piece.of(by, Piece.QUEEN)
        if (rayHits(board, target, DIAGONALS, Piece.of(by, Piece.BISHOP), queen)) return true
        return rayHits(board, target, ORTHOGONALS, Piece.of(by, Piece.ROOK), queen)
    }

    /** True when the king of [color] is attacked by the other side. The king must be on the board. */
    fun isInCheck(board: Board, color: Int): Boolean = isAttacked(board, board.kingSquare(color), 1 - color)

    /** The piece at a 0x88 index, or [Piece.NONE] when the index is off the board. */
    private fun pieceOn(board: Board, sq88: Int): Int =
        if (Square.isOnBoard0x88(sq88)) board.piece88(sq88) else Piece.NONE

    /** True when the first piece along any of [directions] from [target] is [slider] or [queen]. */
    private fun rayHits(board: Board, target: Int, directions: IntArray, slider: Int, queen: Int): Boolean {
        for (dir in directions) {
            var sq88 = target + dir
            while (Square.isOnBoard0x88(sq88)) {
                val piece = board.piece88(sq88)
                if (piece != Piece.NONE) {
                    if (piece == slider || piece == queen) return true
                    break
                }
                sq88 += dir
            }
        }
        return false
    }
}
