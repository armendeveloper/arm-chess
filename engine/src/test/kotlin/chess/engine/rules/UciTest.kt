package chess.engine.rules

import chess.engine.board.Move
import chess.engine.board.Piece
import chess.engine.board.Square
import chess.engine.perft.PerftPositions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UciTest {
    private fun sq(name: String) = Square.parse(name)

    @Test
    fun formatWritesFromSquareToSquareAndALowercasePromotionLetter() {
        assertEquals("e2e4", Uci.format(Move.of(sq("e2"), sq("e4"), flags = Move.DOUBLE_PUSH)))
        assertEquals("g1f3", Uci.format(Move.of(sq("g1"), sq("f3"))))
        assertEquals("e7e8q", Uci.format(Move.of(sq("e7"), sq("e8"), Piece.QUEEN)))
        assertEquals("b2a1n", Uci.format(Move.of(sq("b2"), sq("a1"), Piece.KNIGHT, Move.CAPTURE)))
        // Castling is the king's move; en passant is the pawn's move to the target square.
        assertEquals("e1g1", Uci.format(Move.of(sq("e1"), sq("g1"), flags = Move.CASTLE)))
        assertEquals("e8c8", Uci.format(Move.of(sq("e8"), sq("c8"), flags = Move.CASTLE)))
        assertEquals("e5d6", Uci.format(Move.of(sq("e5"), sq("d6"), flags = Move.CAPTURE or Move.EN_PASSANT)))
    }

    @Test
    fun parseResolvesQuietMovesDoublePushesAndCaptures() {
        val start = Position.START
        val e4 = Uci.parse(start, "e2e4")
        assertEquals(Move.of(sq("e2"), sq("e4"), flags = Move.DOUBLE_PUSH), e4)
        assertEquals(Move.of(sq("g1"), sq("f3")), Uci.parse(start, "g1f3"))

        val capture = Position.fromFen("4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1")
        assertEquals(Move.of(sq("e4"), sq("d5"), flags = Move.CAPTURE), Uci.parse(capture, "e4d5"))
    }

    @Test
    fun parseInfersCastlingFromTheKingMove() {
        val white = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        assertEquals(Move.of(sq("e1"), sq("g1"), flags = Move.CASTLE), Uci.parse(white, "e1g1"))
        assertEquals(Move.of(sq("e1"), sq("c1"), flags = Move.CASTLE), Uci.parse(white, "e1c1"))
        val black = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1")
        assertEquals(Move.of(sq("e8"), sq("g8"), flags = Move.CASTLE), Uci.parse(black, "e8g8"))
        assertEquals(Move.of(sq("e8"), sq("c8"), flags = Move.CASTLE), Uci.parse(black, "e8c8"))
        // Without the right, the same text is just an illegal king move.
        val noRights = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w - - 0 1")
        assertFailsWith<IllegalMoveException> { Uci.parse(noRights, "e1g1") }
    }

    @Test
    fun parseInfersEnPassant() {
        val position = Position.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1")
        val move = Uci.parse(position, "e5d6")
        assertEquals(Move.of(sq("e5"), sq("d6"), flags = Move.CAPTURE or Move.EN_PASSANT), move)
        assertTrue(move.isEnPassant)
        assertEquals(Piece.NONE, position.play(move).pieceAt(sq("d5")), "the captured pawn is removed")
    }

    @Test
    fun parseReadsEveryPromotionPiece() {
        val position = Position.fromFen("1r2k3/P7/8/8/8/8/8/4K3 w - - 0 1")
        for ((letter, piece) in listOf('q' to Piece.QUEEN, 'r' to Piece.ROOK, 'b' to Piece.BISHOP, 'n' to Piece.KNIGHT)) {
            assertEquals(Move.of(sq("a7"), sq("a8"), piece), Uci.parse(position, "a7a8$letter"))
            assertEquals(Move.of(sq("a7"), sq("b8"), piece, Move.CAPTURE), Uci.parse(position, "a7b8$letter"))
        }
    }

    @Test
    fun parseRejectsMalformedAndIllegalText() {
        val start = Position.START
        val rejected = listOf(
            "", "e2", "e2e", "e2e4 ", " e2e4", "E2E4", "e2e4q", "e2e9", "i2i4", "e2-e4", "e2e4e5", "0000",
            "e2e5", // not a legal pawn move
            "e7e5", // the other side's move
            "e1g1", // castling through pieces
        )
        for (text in rejected) {
            assertFailsWith<IllegalMoveException>("'$text'") { Uci.parse(start, text) }
            assertNull(Uci.parseOrNull(start, text), "'$text'")
        }
    }

    @Test
    fun parseRejectsAPromotionWithoutAPieceOrWithAKingOrPawn() {
        val position = Position.fromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1")
        for (text in listOf("a7a8", "a7a8k", "a7a8p", "a7a8Q", "a7a8x")) {
            assertFailsWith<IllegalMoveException>("'$text'") { Uci.parse(position, text) }
        }
    }

    @Test
    fun parseRejectsAnyMoveInAFinishedPosition() {
        val mated = Position.START.play("f2f3", "e7e5", "g2g4", "d8h4")
        assertTrue(mated.legalMoves().isEmpty())
        assertFailsWith<IllegalMoveException> { Uci.parse(mated, "e1f2") }
    }

    @Test
    fun everyLegalMoveOfThePerftPositionsRoundTrips() {
        for (fen in PerftPositions.ALL) {
            val position = Position.fromFen(fen)
            for (move in position.legalMoves()) {
                val text = Uci.format(move)
                assertEquals(move, Uci.parse(position, text), "$text in $fen")
            }
        }
    }

    @Test
    fun everyMoveOfRandomGamesRoundTrips() {
        for (seed in 1..20) {
            for ((position, move) in randomGame(seed)) {
                for (legal in position.legalMoves()) assertEquals(legal, Uci.parse(position, Uci.format(legal)))
                assertEquals(move, Uci.parse(position, Uci.format(move)), "seed $seed")
            }
        }
    }
}
