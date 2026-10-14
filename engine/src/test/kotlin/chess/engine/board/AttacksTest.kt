package chess.engine.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttacksTest {
    private fun sq(name: String) = Square.parse(name)

    /** The squares of the whole board attacked by [by], as sorted names. */
    private fun attackedSquares(board: Board, by: Int): List<String> =
        (0 until 64).filter { Attacks.isAttacked(board, it, by) }.map { Square.name(it) }.sorted()

    /** A board with only the two kings (out of the way on a1/h8 unless overridden) plus [pieces]. */
    private fun board(vararg pieces: Pair<String, Int>, whiteKing: String = "a1", blackKing: String = "h8"): Board {
        val board = Board()
        board.setPiece(sq(whiteKing), Piece.WHITE_KING)
        board.setPiece(sq(blackKing), Piece.BLACK_KING)
        for ((name, piece) in pieces) board.setPiece(sq(name), piece)
        return board
    }

    @Test
    fun startPositionAttacksMatchTheDiagram() {
        val board = Fen.parse(Fen.START)
        // White's pawns and knights cover rank 3...
        assertTrue(Attacks.isAttacked(board, sq("f3"), Piece.WHITE), "g1 knight, e2 and g2 pawns")
        assertTrue(Attacks.isAttacked(board, sq("e3"), Piece.WHITE), "d2 and f2 pawns")
        assertTrue(Attacks.isAttacked(board, sq("a3"), Piece.WHITE), "b1 knight, b2 pawn")
        assertTrue(Attacks.isAttacked(board, sq("d2"), Piece.WHITE), "own pieces defend")
        assertFalse(Attacks.isAttacked(board, sq("a1"), Piece.WHITE))
        // ...and nothing beyond rank 3.
        for (rank in 3..7) for (file in 0 until 8) {
            assertFalse(Attacks.isAttacked(board, Square.of(file, rank), Piece.WHITE), Square.name(Square.of(file, rank)))
        }
        assertTrue(Attacks.isAttacked(board, sq("f6"), Piece.BLACK))
        assertFalse(Attacks.isAttacked(board, sq("e4"), Piece.BLACK))
        assertFalse(Attacks.isAttacked(board, sq("e5"), Piece.WHITE))
    }

    @Test
    fun whitePawnAttacksDiagonallyForwardOnly() {
        val board = board("e4" to Piece.WHITE_PAWN)
        assertEquals(listOf("a2", "b1", "b2", "d5", "f5"), attackedSquares(board, Piece.WHITE))
    }

    @Test
    fun blackPawnAttacksDiagonallyDownwardOnly() {
        val board = board("e4" to Piece.BLACK_PAWN)
        assertEquals(listOf("d3", "f3", "g7", "g8", "h7"), attackedSquares(board, Piece.BLACK))
    }

    @Test
    fun edgePawnsDoNotWrapAroundTheBoard() {
        val board = board("a4" to Piece.WHITE_PAWN, "h5" to Piece.BLACK_PAWN, whiteKing = "e1", blackKing = "e8")
        assertTrue(Attacks.isAttacked(board, sq("b5"), Piece.WHITE))
        assertFalse(Attacks.isAttacked(board, sq("h4"), Piece.WHITE), "a4 must not attack across the edge")
        assertFalse(Attacks.isAttacked(board, sq("h5"), Piece.WHITE))
        assertTrue(Attacks.isAttacked(board, sq("g4"), Piece.BLACK))
        assertFalse(Attacks.isAttacked(board, sq("a4"), Piece.BLACK), "h5 must not attack across the edge")
    }

    @Test
    fun knightAttacksItsEightLeapsAndNoWrap() {
        val board = board("d4" to Piece.WHITE_KNIGHT, whiteKing = "h1", blackKing = "h8")
        assertEquals(
            listOf("b3", "b5", "c2", "c6", "e2", "e6", "f3", "f5", "g1", "g2", "h2"),
            attackedSquares(board, Piece.WHITE),
        )
        val corner = board("a1" to Piece.BLACK_KNIGHT, whiteKing = "e1", blackKing = "h8")
        assertEquals(listOf("b3", "c2", "g7", "g8", "h7"), attackedSquares(corner, Piece.BLACK))
    }

    @Test
    fun kingAttacksAdjacentSquares() {
        val board = board(whiteKing = "e4", blackKing = "a8")
        assertEquals(
            listOf("d3", "d4", "d5", "e3", "e5", "f3", "f4", "f5"),
            attackedSquares(board, Piece.WHITE),
        )
        assertEquals(listOf("a7", "b7", "b8"), attackedSquares(board, Piece.BLACK))
    }

    @Test
    fun rookAttacksAlongRanksAndFilesUntilBlocked() {
        val board = board(
            "d4" to Piece.WHITE_ROOK,
            "d6" to Piece.BLACK_PAWN,
            "f4" to Piece.WHITE_PAWN,
            whiteKing = "h1",
            blackKing = "h8",
        )
        val rookTargets = listOf("a4", "b4", "c4", "d1", "d2", "d3", "d5", "d6", "e4", "f4")
        for (name in rookTargets) assertTrue(Attacks.isAttacked(board, sq(name), Piece.WHITE), name)
        assertFalse(Attacks.isAttacked(board, sq("d7"), Piece.WHITE), "blocked by the d6 pawn")
        assertFalse(Attacks.isAttacked(board, sq("h4"), Piece.WHITE), "blocked by the f4 pawn")
        assertFalse(Attacks.isAttacked(board, sq("c5"), Piece.WHITE), "rooks do not attack diagonally")
    }

    @Test
    fun bishopAttacksDiagonalsUntilBlockedAndDoesNotWrap() {
        val board = board("c1" to Piece.BLACK_BISHOP, "e3" to Piece.WHITE_KNIGHT, whiteKing = "h1", blackKing = "h8")
        assertTrue(Attacks.isAttacked(board, sq("a3"), Piece.BLACK))
        assertTrue(Attacks.isAttacked(board, sq("d2"), Piece.BLACK))
        assertTrue(Attacks.isAttacked(board, sq("e3"), Piece.BLACK), "the blocker itself is attacked")
        assertFalse(Attacks.isAttacked(board, sq("f4"), Piece.BLACK), "behind the blocker")
        assertFalse(Attacks.isAttacked(board, sq("h3"), Piece.BLACK), "the ray must stop at a3, not wrap to the h-file")
        assertFalse(Attacks.isAttacked(board, sq("c2"), Piece.BLACK), "bishops do not attack along files")
    }

    @Test
    fun queenAttacksBothRayKinds() {
        val board = board("d1" to Piece.WHITE_QUEEN, whiteKing = "h1", blackKing = "h8")
        for (name in listOf("d8", "a1", "g1", "a4", "h5")) assertTrue(Attacks.isAttacked(board, sq(name), Piece.WHITE), name)
        assertFalse(Attacks.isAttacked(board, sq("e3"), Piece.WHITE))
    }

    @Test
    fun attacksOnlyCountPiecesOfTheGivenColour() {
        val board = board("d4" to Piece.WHITE_ROOK, whiteKing = "h1", blackKing = "h8")
        assertTrue(Attacks.isAttacked(board, sq("d8"), Piece.WHITE))
        assertFalse(Attacks.isAttacked(board, sq("d8"), Piece.BLACK))
    }

    @Test
    fun isInCheckLooksAtTheKingOfTheGivenColour() {
        val board = board("e1" to Piece.BLACK_ROOK, whiteKing = "a1", blackKing = "h8")
        assertTrue(Attacks.isInCheck(board, Piece.WHITE))
        assertFalse(Attacks.isInCheck(board, Piece.BLACK))
        board.setPiece(sq("c1"), Piece.WHITE_BISHOP)
        assertFalse(Attacks.isInCheck(board, Piece.WHITE), "the bishop blocks the rook")
    }
}
