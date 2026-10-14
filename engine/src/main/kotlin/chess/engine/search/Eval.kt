package chess.engine.search

import chess.engine.board.Board
import chess.engine.board.Piece
import chess.engine.board.Square
import kotlin.math.abs
import kotlin.math.max

/**
 * Static evaluation (design §4.5), in centipawns:
 * - material (P 100, N 320, B 330, R 500, Q 900);
 * - piece-square tables ([Pst]), tapered from middlegame to endgame by the non-pawn material left;
 * - a bishop pair bonus and a tempo bonus for the side to move;
 * - a mop-up term when one side has only its king: drive it to the edge and bring the other king
 *   closer, so that K+Q v K and K+R v K are won at modest depth;
 * - no credit for material that cannot mate: a side with no pawns and at most one minor piece never
 *   scores above 0 (covers K+B v K, K+N v K and K+B v K+B, which the rules call drawn).
 *
 * Colour-symmetric: [evaluateWhite] of a position is minus that of its colour-flipped mirror.
 */
internal object Eval {
    val PIECE_VALUES: IntArray = intArrayOf(0, 100, 320, 330, 500, 900, 0)

    const val BISHOP_PAIR: Int = 30
    const val TEMPO: Int = 10

    /** Game phase weight of each piece type: 24 with all pieces on the board, 0 with none. */
    private val PHASE_WEIGHTS = intArrayOf(0, 0, 1, 1, 2, 4, 0)
    private const val MAX_PHASE = 24

    /** Mop-up applies when the attacker has at least this much non-pawn material (a rook). */
    private const val MOP_UP_MATERIAL = 500

    /** The score from the side to move's point of view: positive is good for the player to move. */
    fun evaluate(board: Board): Int {
        val white = evaluateWhite(board)
        return if (board.sideToMove == Piece.WHITE) white else -white
    }

    /** The score from White's point of view. */
    fun evaluateWhite(board: Board): Int {
        // Locals rather than arrays: this runs at every leaf and must not allocate.
        var mgWhite = 0
        var egWhite = 0
        var phase = 0
        var whiteNonPawn = 0
        var blackNonPawn = 0
        var whitePawns = 0
        var blackPawns = 0
        var whiteBishops = 0
        var blackBishops = 0
        for (sq in 0 until 64) {
            val piece = board.pieceAt(sq)
            if (piece == Piece.NONE) continue
            val type = Piece.typeOf(piece)
            val value = PIECE_VALUES[type]
            phase += PHASE_WEIGHTS[type]
            if (Piece.colorOf(piece) == Piece.WHITE) {
                mgWhite += value + Pst.mg(type, Piece.WHITE, sq)
                egWhite += value + Pst.eg(type, Piece.WHITE, sq)
                when (type) {
                    Piece.PAWN -> whitePawns++
                    Piece.BISHOP -> { whiteBishops++; whiteNonPawn += value }
                    else -> whiteNonPawn += value // the king's value is 0
                }
            } else {
                mgWhite -= value + Pst.mg(type, Piece.BLACK, sq)
                egWhite -= value + Pst.eg(type, Piece.BLACK, sq)
                when (type) {
                    Piece.PAWN -> blackPawns++
                    Piece.BISHOP -> { blackBishops++; blackNonPawn += value }
                    else -> blackNonPawn += value
                }
            }
        }
        if (phase > MAX_PHASE) phase = MAX_PHASE
        var score = (mgWhite * phase + egWhite * (MAX_PHASE - phase)) / MAX_PHASE

        if (whiteBishops >= 2) score += BISHOP_PAIR
        if (blackBishops >= 2) score -= BISHOP_PAIR
        if (blackNonPawn == 0 && blackPawns == 0 && whiteNonPawn >= MOP_UP_MATERIAL) {
            score += mopUp(board.kingSquare(Piece.BLACK), board.kingSquare(Piece.WHITE))
        }
        if (whiteNonPawn == 0 && whitePawns == 0 && blackNonPawn >= MOP_UP_MATERIAL) {
            score -= mopUp(board.kingSquare(Piece.WHITE), board.kingSquare(Piece.BLACK))
        }
        score += if (board.sideToMove == Piece.WHITE) TEMPO else -TEMPO

        // A side with no pawns and at most one minor piece cannot mate: no credit for being ahead.
        if (score > 0 && whitePawns == 0 && whiteNonPawn <= PIECE_VALUES[Piece.BISHOP]) score = 0
        if (score < 0 && blackPawns == 0 && blackNonPawn <= PIECE_VALUES[Piece.BISHOP]) score = 0
        return score
    }

    /**
     * The mop-up bonus for the side whose king is on [ownKing] against a lone king on [loneKing]:
     * the lone king's distance from the centre, and how close the two kings are.
     */
    fun mopUp(loneKing: Int, ownKing: Int): Int {
        if (loneKing == Square.NONE || ownKing == Square.NONE) return 0
        val file = Square.file(loneKing)
        val rank = Square.rank(loneKing)
        val centreDistance = max(3 - file, file - 4) + max(3 - rank, rank - 4)
        val kingDistance = abs(file - Square.file(ownKing)) + abs(rank - Square.rank(ownKing))
        return 10 * centreDistance + 4 * (14 - kingDistance)
    }
}
