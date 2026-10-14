package chess.engine.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame

class BoardTest {
    @Test
    fun newBoardIsEmptyWithWhiteToMoveAndDefaultClocks() {
        val board = Board()
        for (sq in 0 until 64) assertEquals(Piece.NONE, board.pieceAt(sq))
        assertEquals(Piece.WHITE, board.sideToMove)
        assertEquals(CastlingRights.NONE, board.castlingRights)
        assertEquals(Square.NONE, board.epSquare)
        assertEquals(0, board.halfmoveClock)
        assertEquals(1, board.fullmoveNumber)
        assertEquals(Square.NONE, board.kingSquare(Piece.WHITE))
        assertEquals(Square.NONE, board.kingSquare(Piece.BLACK))
    }

    @Test
    fun setPieceStoresAt0x88IndexAndReadsBackBySquare() {
        val board = Board()
        val e4 = Square.parse("e4")
        board.setPiece(e4, Piece.WHITE_KNIGHT)
        assertEquals(Piece.WHITE_KNIGHT, board.pieceAt(e4))
        assertEquals(Piece.WHITE_KNIGHT, board.piece88(0x34))
        for (sq88 in 0 until 128) {
            if (sq88 != 0x34) assertEquals(Piece.NONE, board.piece88(sq88), "index $sq88")
        }
    }

    @Test
    fun kingSquaresFollowSetPiece() {
        val board = Board()
        board.setPiece(Square.E1, Piece.WHITE_KING)
        board.setPiece(Square.E8, Piece.BLACK_KING)
        assertEquals(Square.E1, board.kingSquare(Piece.WHITE))
        assertEquals(Square.E8, board.kingSquare(Piece.BLACK))

        board.setPiece(Square.E1, Piece.NONE)
        board.setPiece(Square.F1, Piece.WHITE_KING)
        assertEquals(Square.F1, board.kingSquare(Piece.WHITE))

        board.setPiece(Square.E8, Piece.NONE)
        assertEquals(Square.NONE, board.kingSquare(Piece.BLACK))
    }

    @Test
    fun copyIsDeepAndEqualUntilChanged() {
        val board = Fen.parse(Fen.START)
        val copy = board.copy()
        assertNotSame(board, copy)
        assertEquals(board, copy)
        assertEquals(board.hashCode(), copy.hashCode())

        copy.setPiece(Square.parse("e4"), Piece.WHITE_PAWN)
        assertEquals(Piece.NONE, board.pieceAt(Square.parse("e4")), "copy must not share the square array")
        assertNotEquals(board, copy)
    }

    @Test
    fun equalsComparesEveryScalarField() {
        val base = Fen.parse(Fen.START)
        val changes: List<(Board) -> Unit> = listOf(
            { it.sideToMove = Piece.BLACK },
            { it.castlingRights = CastlingRights.WHITE_KINGSIDE },
            { it.epSquare = Square.parse("e3") },
            { it.halfmoveClock = 1 },
            { it.fullmoveNumber = 2 },
        )
        for (change in changes) {
            val other = base.copy().also(change)
            assertNotEquals(base, other)
        }
    }

    @Test
    fun clearResetsEverything() {
        val board = Fen.parse("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 3 17")
        board.clear()
        assertEquals(Board(), board)
    }

    @Test
    fun toStringDrawsRank8First() {
        val text = Fen.parse(Fen.START).toString()
        val lines = text.lines()
        assertEquals("8 r n b q k b n r", lines[0])
        assertEquals("1 R N B Q K B N R", lines[7])
        assertEquals("  a b c d e f g h", lines[8])
    }
}
