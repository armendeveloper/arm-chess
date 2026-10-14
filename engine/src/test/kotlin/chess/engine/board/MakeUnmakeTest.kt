package chess.engine.board

import chess.engine.perft.PerftPositions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MakeUnmakeTest {
    /** The seven perft positions in design §4.2 (chessprogramming.org "Perft Results"). */
    private val perftFens = PerftPositions.ALL

    private fun sq(name: String) = Square.parse(name)

    private fun move(board: Board, uci: String): Move =
        MoveGen.legalMoves(board).singleOrNull { it.uci() == uci }
            ?: throw AssertionError("$uci is not a legal move in ${Fen.format(board)}")

    private fun play(board: Board, vararg ucis: String): List<Move> =
        ucis.map { uci -> move(board, uci).also { board.make(it) } }

    /**
     * Makes and unmakes every legal move down to [depth], checking the board after each unmake. After
     * every make and every unmake, the incrementally updated Zobrist key must equal the key computed
     * from scratch (`Board.equals` also compares the key, so the unmake check covers its restore).
     */
    private fun checkRoundTrips(board: Board, depth: Int, fen: String): Int {
        if (depth == 0) return 0
        var checked = 0
        for (move in MoveGen.legalMoves(board)) {
            val before = board.copy()
            board.make(move)
            assertNotEquals(before, board, "$fen: ${move.uci()} must change the board")
            assertEquals(Zobrist.compute(board), board.key, "$fen: key after ${move.uci()}")
            checked += 1 + checkRoundTrips(board, depth - 1, fen)
            board.unmake(move)
            assertEquals(before, board, "$fen: unmake ${move.uci()} must restore every field")
            assertEquals(before.key, board.key, "$fen: unmake ${move.uci()} must restore the key")
            assertEquals(Zobrist.compute(board), board.key, "$fen: key after unmaking ${move.uci()}")
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

    /** Plays [uci] on [fen], checks the resulting FEN, then unmakes it and checks the board is back. */
    private fun assertMakeUnmake(fen: String, uci: String, expectedFen: String) {
        val board = Fen.parse(fen)
        val before = board.copy()
        val m = move(board, uci)
        board.make(m)
        assertEquals(expectedFen, Fen.format(board), "$fen after $uci")
        board.unmake(m)
        assertEquals(before, board, "$fen: unmake $uci")
    }

    @Test
    fun castlingMovesTheRookAndUnmakeMovesItBack() {
        val castling = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 3 1"
        assertMakeUnmake(castling, "e1g1", "r3k2r/8/8/8/8/8/8/R4RK1 b kq - 4 1")
        assertMakeUnmake(castling, "e1c1", "r3k2r/8/8/8/8/8/8/2KR3R b kq - 4 1")
        val black = "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 3 9"
        assertMakeUnmake(black, "e8g8", "r4rk1/8/8/8/8/8/8/R3K2R w KQ - 4 10")
        assertMakeUnmake(black, "e8c8", "2kr3r/8/8/8/8/8/8/R3K2R w KQ - 4 10")
    }

    @Test
    fun castlingUpdatesTheKingSquare() {
        val board = Fen.parse("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val castle = move(board, "e1c1")
        board.make(castle)
        assertEquals(Square.C1, board.kingSquare(Piece.WHITE))
        board.unmake(castle)
        assertEquals(Square.E1, board.kingSquare(Piece.WHITE))
    }

    @Test
    fun enPassantRemovesThePawnBehindTheTargetSquareAndUnmakePutsItBack() {
        assertMakeUnmake("4k3/8/8/3pP3/8/8/8/4K3 w - d6 5 20", "e5d6", "4k3/8/3P4/8/8/8/8/4K3 b - - 0 20")
        assertMakeUnmake("4k3/8/8/8/3pP3/8/8/4K3 b - e3 5 20", "d4e3", "4k3/8/8/8/8/4p3/8/4K3 w - - 0 21")
    }

    @Test
    fun enPassantIsOnlyAvailableOnTheMoveRightAfterTheDoublePush() {
        val board = Fen.parse("4k3/3p4/8/4P3/8/8/8/4K3 b - - 0 1")
        play(board, "d7d5")
        assertTrue("e5d6" in MoveGen.legalMoves(board).map { it.uci() })
        play(board, "e1e2", "e8e7")
        assertTrue("e5d6" !in MoveGen.legalMoves(board).map { it.uci() })
    }

    @Test
    fun promotionReplacesThePawnAndUnmakeRestoresIt() {
        val fen = "4k3/P7/8/8/8/8/8/4K3 w - - 9 40"
        assertMakeUnmake(fen, "a7a8q", "Q3k3/8/8/8/8/8/8/4K3 b - - 0 40")
        assertMakeUnmake(fen, "a7a8r", "R3k3/8/8/8/8/8/8/4K3 b - - 0 40")
        assertMakeUnmake(fen, "a7a8b", "B3k3/8/8/8/8/8/8/4K3 b - - 0 40")
        assertMakeUnmake(fen, "a7a8n", "N3k3/8/8/8/8/8/8/4K3 b - - 0 40")
        assertMakeUnmake("4k3/8/8/8/8/8/7p/K7 b - - 0 40", "h2h1n", "4k3/8/8/8/8/8/8/K6n w - - 0 41")
    }

    @Test
    fun capturePromotionReplacesBothPiecesAndUnmakeRestoresThem() {
        assertMakeUnmake("nr2k3/P7/8/8/8/8/8/4K3 w - - 0 1", "a7b8q", "nQ2k3/8/8/8/8/8/8/4K3 b - - 0 1")
        assertMakeUnmake("4k3/8/8/8/8/8/6p1/4K2R b K - 0 1", "g2h1r", "4k3/8/8/8/8/8/8/4K2r w - - 0 2")
    }

    @Test
    fun aKingMoveClearsBothOfItsSidesCastlingRights() {
        assertMakeUnmake("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", "e1e2", "r3k2r/8/8/8/8/8/4K3/R6R b kq - 1 1")
        assertMakeUnmake("r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1", "e8d8", "r2k3r/8/8/8/8/8/8/R3K2R w KQ - 1 2")
    }

    @Test
    fun aRookMoveFromItsHomeSquareClearsThatWingsRight() {
        val fen = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"
        assertMakeUnmake(fen, "h1h4", "r3k2r/8/8/8/7R/8/8/R3K3 b Qkq - 1 1")
        assertMakeUnmake(fen, "a1a4", "r3k2r/8/8/8/R7/8/8/4K2R b Kkq - 1 1")
        assertMakeUnmake("r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1", "h8h5", "r3k3/8/8/7r/8/8/8/R3K2R w KQq - 1 2")
    }

    @Test
    fun capturingARookOnItsHomeSquareClearsTheOpponentsRight() {
        // The bishop takes on a8 along the long diagonal: only Black's queenside right goes.
        assertMakeUnmake("r3k2r/8/8/8/8/8/6B1/R3K2R w KQkq - 0 1", "g2a8", "B3k2r/8/8/8/8/8/8/R3K2R b KQk - 0 1")
        // Rook takes rook on h8: White loses kingside (rook left h1), Black loses kingside (rook taken).
        assertMakeUnmake("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", "h1h8", "r3k2R/8/8/8/8/8/8/R3K3 b Qq - 0 1")
    }

    @Test
    fun aRookReturningHomeDoesNotRegainTheRight() {
        val board = Fen.parse("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        play(board, "h1h2", "a8a7", "h2h1", "a7a8")
        assertEquals("r3k2r/8/8/8/8/8/8/R3K2R w Qk - 4 3", Fen.format(board))
        assertEquals(setOf("e1c1"), MoveGen.legalMoves(board).filter { it.isCastle }.map { it.uci() }.toSet())
    }

    @Test
    fun aRookMoveAwayFromTheHomeSquaresLeavesTheRightsAlone() {
        assertMakeUnmake("r3k2r/8/8/8/8/8/R7/4K2R w Kkq - 0 1", "a2b2", "r3k2r/8/8/8/8/8/1R6/4K2R b Kkq - 1 1")    }

    @Test
    fun makeThenUnmakeRestoresTheBoardThroughSpecialMoves() {
        // Positions where castling, en passant and promotions all occur within two plies.
        val fens = listOf(
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1",
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
            "4k3/8/8/2PpP3/8/8/8/4K3 w - d6 0 1",
            "n1n4k/PPPP4/8/8/8/8/4pppp/K5N1 w - - 0 1",
        )
        for (fen in fens) {
            val board = Fen.parse(fen)
            val original = board.copy()
            checkRoundTrips(board, depth = 3, fen = fen)
            assertEquals(original, board, fen)
        }
    }
}
