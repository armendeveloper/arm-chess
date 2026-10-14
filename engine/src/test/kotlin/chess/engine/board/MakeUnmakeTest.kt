package chess.engine.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MakeUnmakeTest {
    /** The start position plus the seven perft positions in design §4.2 (chessprogramming.org "Perft Results"). */
    private val perftFens = listOf(
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq -",
        "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
        "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
        "r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1",
        "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
        "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
    )

    private fun sq(name: String) = Square.parse(name)

    private fun move(board: Board, uci: String): Move =
        MoveGen.legalMoves(board).singleOrNull { it.uci() == uci }
            ?: throw AssertionError("$uci is not a legal move in ${Fen.format(board)}")

    private fun play(board: Board, vararg ucis: String): List<Move> =
        ucis.map { uci -> move(board, uci).also { board.make(it) } }

    /** Makes and unmakes every legal move down to [depth], checking the board after each unmake. */
    private fun checkRoundTrips(board: Board, depth: Int, fen: String): Int {
        if (depth == 0) return 0
        var checked = 0
        for (move in MoveGen.legalMoves(board)) {
            val before = board.copy()
            board.make(move)
            assertNotEquals(before, board, "$fen: ${move.uci()} must change the board")
            checked += 1 + checkRoundTrips(board, depth - 1, fen)
            board.unmake(move)
            assertEquals(before, board, "$fen: unmake ${move.uci()} must restore every field")
        }
        return checked
    }

    @Test
    fun makeThenUnmakeRestoresTheBoardForEveryLegalMoveAndReplyInThePerftPositions() {
        for (fen in perftFens) {
            val board = Fen.parse(fen)
            val original = board.copy()
            val checked = checkRoundTrips(board, depth = 2, fen = fen)
            assertEquals(original, board, fen)
            assertTrue(checked > 0, "$fen has no legal moves")
        }
    }

    @Test
    fun quietMoveMovesThePieceAndPassesTheTurn() {
        val board = Fen.parse(Fen.START)
        play(board, "g1f3")
        assertEquals(Piece.NONE, board.pieceAt(sq("g1")))
        assertEquals(Piece.WHITE_KNIGHT, board.pieceAt(sq("f3")))
        assertEquals(Piece.BLACK, board.sideToMove)
        assertEquals(Square.NONE, board.epSquare)
        assertEquals(1, board.halfmoveClock)
        assertEquals(1, board.fullmoveNumber)
        assertEquals("rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq - 1 1", Fen.format(board))
    }

    @Test
    fun doublePushSetsTheEnPassantSquareAndTheNextMoveClearsIt() {
        val board = Fen.parse(Fen.START)
        play(board, "e2e4")
        assertEquals(sq("e3"), board.epSquare)
        assertEquals(0, board.halfmoveClock)
        play(board, "e7e5")
        assertEquals(sq("e6"), board.epSquare)
        assertEquals(2, board.fullmoveNumber, "the fullmove number goes up after Black moves")
        play(board, "g1f3")
        assertEquals(Square.NONE, board.epSquare)
        assertEquals("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2", Fen.format(board))
    }

    @Test
    fun captureRemovesThePieceResetsTheClockAndUnmakeRestoresIt() {
        val board = Fen.parse("rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 3 2")
        val before = board.copy()
        val capture = move(board, "e4d5")
        board.make(capture)
        assertEquals(Piece.WHITE_PAWN, board.pieceAt(sq("d5")))
        assertEquals(Piece.NONE, board.pieceAt(sq("e4")))
        assertEquals(0, board.halfmoveClock)
        board.unmake(capture)
        assertEquals(Piece.BLACK_PAWN, board.pieceAt(sq("d5")))
        assertEquals(before, board)
    }

    @Test
    fun pieceMovesIncrementTheHalfmoveClockAndPawnMovesResetIt() {
        val board = Fen.parse("4k3/4p3/8/8/8/8/8/R3K3 w - - 7 30")
        play(board, "a1a5")
        assertEquals(8, board.halfmoveClock)
        play(board, "e7e6")
        assertEquals(0, board.halfmoveClock)
        assertEquals(31, board.fullmoveNumber)
    }

    @Test
    fun kingMovesUpdateTheKingSquareAndUnmakeRestoresIt() {
        val board = Fen.parse("4k3/8/8/8/8/8/8/4K3 w - - 0 1")
        val kingMove = move(board, "e1d2")
        board.make(kingMove)
        assertEquals(sq("d2"), board.kingSquare(Piece.WHITE))
        board.unmake(kingMove)
        assertEquals(Square.E1, board.kingSquare(Piece.WHITE))
    }

    @Test
    fun unmakingASequenceInReverseRestoresTheStart() {
        val board = Fen.parse(Fen.START)
        val start = board.copy()
        val moves = play(board, "e2e4", "d7d5", "e4d5", "g8f6", "f1b5", "c7c6", "d5c6", "d8d2", "b1d2", "b7c6")
        assertEquals("rnb1kb1r/p3pppp/2p2n2/1B6/8/8/PPPN1PPP/R1BQK1NR w KQkq - 0 6", Fen.format(board))
        for (m in moves.asReversed()) board.unmake(m)
        assertEquals(start, board)
    }

    @Test
    fun theUndoStackGrowsForLongGames() {
        val board = Fen.parse(Fen.START)
        val start = board.copy()
        val made = ArrayList<Move>()
        repeat(150) { made += play(board, "g1f3", "g8f6", "f3g1", "f6g8") }
        assertEquals(600, made.size)
        assertEquals(600, board.halfmoveClock)
        assertEquals(301, board.fullmoveNumber)
        for (m in made.asReversed()) board.unmake(m)
        assertEquals(start, board)
    }

    @Test
    fun unmakeWithoutAMadeMoveFails() {
        val board = Fen.parse(Fen.START)
        assertFailsWith<IllegalStateException> { board.unmake(Move.of(Square.parse("e2"), Square.parse("e4"))) }

        play(board, "e2e4")
        val copy = board.copy()
        assertFailsWith<IllegalStateException>("a copy starts with an empty history") {
            copy.unmake(Move.of(Square.parse("e2"), Square.parse("e4"), flags = Move.DOUBLE_PUSH))
        }
    }

    @Test
    fun clearDropsTheHistory() {
        val board = Fen.parse(Fen.START)
        play(board, "e2e4")
        board.clear()
        assertFailsWith<IllegalStateException> { board.unmake(Move.of(Square.parse("e2"), Square.parse("e4"))) }
    }

    @Test
    fun castlingEnPassantAndPromotionAreRejectedUntilT5() {
        val castle = Fen.parse("4k3/8/8/8/8/8/8/4K2R w K - 0 1")
        assertFailsWith<UnsupportedOperationException> { castle.make(Move.of(Square.E1, Square.G1, flags = Move.CASTLE)) }
        assertEquals(Fen.parse("4k3/8/8/8/8/8/8/4K2R w K - 0 1"), castle, "a rejected move leaves the board alone")

        val ep = Fen.parse("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1")
        assertFailsWith<UnsupportedOperationException> {
            ep.make(Move.of(sq("e5"), sq("d6"), flags = Move.EN_PASSANT or Move.CAPTURE))
        }

        val promo = Fen.parse("4k3/P7/8/8/8/8/8/4K3 w - - 0 1")
        assertFailsWith<UnsupportedOperationException> { promo.make(Move.of(sq("a7"), sq("a8"), promotion = Piece.QUEEN)) }
    }
}
