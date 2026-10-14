package chess.engine.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FenTest {
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

    @Test
    fun startConstantIsTheStandardStartPosition() {
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", Fen.START)
    }

    @Test
    fun startPositionParsesIntoTheExpectedBoard() {
        val board = Fen.parse(Fen.START)
        val backRank = listOf(Piece.ROOK, Piece.KNIGHT, Piece.BISHOP, Piece.QUEEN, Piece.KING, Piece.BISHOP, Piece.KNIGHT, Piece.ROOK)
        for (file in 0 until 8) {
            assertEquals(Piece.of(Piece.WHITE, backRank[file]), board.pieceAt(Square.of(file, 0)))
            assertEquals(Piece.WHITE_PAWN, board.pieceAt(Square.of(file, 1)))
            for (rank in 2..5) assertEquals(Piece.NONE, board.pieceAt(Square.of(file, rank)))
            assertEquals(Piece.BLACK_PAWN, board.pieceAt(Square.of(file, 6)))
            assertEquals(Piece.of(Piece.BLACK, backRank[file]), board.pieceAt(Square.of(file, 7)))
        }
        assertEquals(Piece.WHITE, board.sideToMove)
        assertEquals(CastlingRights.ALL, board.castlingRights)
        assertEquals(Square.NONE, board.epSquare)
        assertEquals(0, board.halfmoveClock)
        assertEquals(1, board.fullmoveNumber)
        assertEquals(Square.E1, board.kingSquare(Piece.WHITE))
        assertEquals(Square.E8, board.kingSquare(Piece.BLACK))
    }

    @Test
    fun perftPositionsRoundTrip() {
        for (fen in perftFens) {
            val expected = if (fen.split(' ').size == 4) "$fen 0 1" else fen
            assertEquals(expected, Fen.format(Fen.parse(fen)), "round-trip of $fen")
            assertEquals(Fen.parse(fen), Fen.parse(expected), "4-field and 6-field forms parse to the same board")
        }
    }

    @Test
    fun scalarFieldsAreParsed() {
        val pos5 = Fen.parse(perftFens[5])
        assertEquals(Piece.WHITE, pos5.sideToMove)
        assertEquals(CastlingRights.WHITE_KINGSIDE or CastlingRights.WHITE_QUEENSIDE, pos5.castlingRights)
        assertEquals(1, pos5.halfmoveClock)
        assertEquals(8, pos5.fullmoveNumber)

        val mirrored = Fen.parse(perftFens[4])
        assertEquals(Piece.BLACK, mirrored.sideToMove)
        assertEquals(CastlingRights.WHITE_KINGSIDE or CastlingRights.WHITE_QUEENSIDE, mirrored.castlingRights)
        assertEquals(Square.E1, mirrored.kingSquare(Piece.WHITE))
        assertEquals(Square.G8, mirrored.kingSquare(Piece.BLACK))
        assertEquals(Piece.WHITE_ROOK, mirrored.pieceAt(Square.A1))
        assertEquals(Piece.BLACK_PAWN, mirrored.pieceAt(Square.parse("a2")))
        assertEquals(Piece.WHITE_QUEEN, mirrored.pieceAt(Square.parse("a6")))
    }

    @Test
    fun enPassantSquaresRoundTripForBothSides() {
        val afterE4 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
        val afterE4E5 = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2"
        assertEquals(Square.parse("e3"), Fen.parse(afterE4).epSquare)
        assertEquals(Square.parse("e6"), Fen.parse(afterE4E5).epSquare)
        assertEquals(afterE4, Fen.format(Fen.parse(afterE4)))
        assertEquals(afterE4E5, Fen.format(Fen.parse(afterE4E5)))
    }

    @Test
    fun castlingFieldIsWrittenInCanonicalOrder() {
        val board = Fen.parse("r3k2r/8/8/8/8/8/8/R3K2R w qkQK - 0 1")
        assertEquals(CastlingRights.ALL, board.castlingRights)
        assertEquals("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", Fen.format(board))
    }

    @Test
    fun surroundingAndRepeatedWhitespaceIsTolerated() {
        assertEquals(Fen.START, Fen.format(Fen.parse("  rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR   w KQkq -  0 1 \n")))
    }

    @Test
    fun largeClocksRoundTrip() {
        val fen = "4k3/8/8/8/8/8/8/4K3 b - - 99 250"
        assertEquals(fen, Fen.format(Fen.parse(fen)))
    }

    private fun assertRejected(fen: String, expectedMessagePart: String) {
        val e = assertFailsWith<FenException>("expected rejection of '$fen'") { Fen.parse(fen) }
        assertIs<IllegalArgumentException>(e)
        assertTrue(
            e.message!!.contains(expectedMessagePart),
            "message for '$fen' was '${e.message}', expected it to contain '$expectedMessagePart'",
        )
    }

    @Test
    fun emptyFenIsRejected() = assertRejected("   ", "FEN is empty")

    @Test
    fun wrongFieldCountIsRejected() {
        assertRejected("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0", "found 5")
        assertRejected("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w", "found 2")
        assertRejected("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 extra", "found 7")
    }

    @Test
    fun wrongRankCountIsRejected() {
        assertRejected("rnbqkbnr/pppppppp/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "must have 8 ranks, found 7")
        assertRejected("rnbqkbnr/pppppppp/8/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "must have 8 ranks, found 9")
    }

    @Test
    fun rankWithWrongFileCountIsRejected() {
        assertRejected("rnbqkbnrr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "Rank 8 has 9 files, expected 8")
        assertRejected("rnbqkbnr/pppppppp/8/8/7/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "Rank 4 has 7 files, expected 8")
        assertRejected("rnbqkbnr/pppppppp/8/8/44P/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "Rank 4 has 9 files, expected 8")
        assertRejected("rnbqkbnr/pppppppp/8//8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "Rank 5 has 0 files, expected 8")
    }

    @Test
    fun invalidPlacementCharacterIsRejected() {
        assertRejected("rnbqkbnr/pppppppp/8/8/3x4/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "Invalid character 'x' in rank 4")
        assertRejected("rnbqkbnr/pppppppp/8/8/08/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "Invalid character '0' in rank 4")
        assertRejected("rnbqkbnr/pppppppp/9/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "Invalid character '9' in rank 6")
    }

    @Test
    fun missingOrExtraKingsAreRejected() {
        assertRejected("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQ1BNR w kq - 0 1", "White must have exactly one king, found 0")
        assertRejected("rnbqkbnr/pppppppp/8/8/4k3/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "Black must have exactly one king, found 2")
    }

    @Test
    fun pawnsOnTheBackRanksAreRejected() {
        assertRejected("rnbqkbnP/pppppppp/8/8/8/8/PPPPPPP1/RNBQKBNR w KQq - 0 1", "White pawn on h8")
        assertRejected("rnbqkbnr/ppppppp1/8/8/8/8/PPPPPPPP/RNBQKBNp w Qkq - 0 1", "Black pawn on h1")
    }

    @Test
    fun badSideToMoveIsRejected() {
        assertRejected("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR W KQkq - 0 1", "Side to move must be 'w' or 'b', found 'W'")
    }

    @Test
    fun malformedCastlingFieldIsRejected() {
        assertRejected("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQxq - 0 1", "Invalid castling field 'KQxq'")
        assertRejected("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KK - 0 1", "Castling field 'KK' repeats 'K'")
        assertRejected("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w -K - 0 1", "Invalid castling field '-K'")
    }

    @Test
    fun castlingRightsInconsistentWithKingAndRooksAreRejected() {
        assertRejected("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBN1 w KQkq - 0 1", "Castling right 'K' needs a white rook on h1")
        assertRejected("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/1NBQKBNR w KQkq - 0 1", "Castling right 'Q' needs a white rook on a1")
        assertRejected("rnbqkbn1/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "Castling right 'k' needs a black rook on h8")
        assertRejected("1nbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "Castling right 'q' needs a black rook on a8")
        assertRejected("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQ1KNR w KQkq - 0 1", "Castling right 'K' needs the white king on e1")
        assertRejected("rnbq1knr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "Castling right 'k' needs the black king on e8")
    }

    @Test
    fun malformedEnPassantSquareIsRejected() {
        assertRejected("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e9 0 1", "Invalid en passant square 'e9'")
        assertRejected("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq E3 0 1", "Invalid en passant square 'E3'")
    }

    @Test
    fun enPassantSquareOnTheWrongRankIsRejected() {
        assertRejected("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e4 0 1", "En passant square e4 must be on rank 3 when Black is to move")
        assertRejected("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e3 0 1", "En passant square e3 must be on rank 6 when White is to move")
    }

    @Test
    fun enPassantSquareWithoutThePushedPawnIsRejected() {
        assertRejected("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq e3 0 1", "En passant square e3 needs a white pawn on e4")
        assertRejected("rnbqkbnr/pppp1ppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2", "En passant square e6 needs a black pawn on e5")
    }

    @Test
    fun enPassantSquareThatIsOccupiedIsRejected() {
        assertRejected("rnbqkbnr/pppppppp/8/8/4P3/4N3/PPPP1PPP/RNBQKB1R b KQkq e3 0 1", "En passant squares e3 and e2 must be empty")
        assertRejected("rnbqkbnr/pppppppp/8/8/4P3/8/PPPPBPPP/RNBQK1NR b KQkq e3 0 1", "En passant squares e3 and e2 must be empty")
        assertRejected("rnbqkbnr/pppp1ppp/4b3/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2", "En passant squares e6 and e7 must be empty")
    }

    @Test
    fun nonNumericOrOutOfRangeClocksAreRejected() {
        assertRejected("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - x 1", "Halfmove clock must be a non-negative integer, found 'x'")
        assertRejected("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - -1 1", "Halfmove clock must be a non-negative integer, found '-1'")
        assertRejected("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 0", "Fullmove number must be a positive integer, found '0'")
        assertRejected("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1.5", "Fullmove number must be a positive integer, found '1.5'")
        assertRejected("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - +3 1", "Halfmove clock must be a non-negative integer, found '+3'")
        assertRejected("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 99999999999", "Fullmove number must be a positive integer, found '99999999999'")
    }
}
