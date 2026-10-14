package chess.engine.rules

import chess.engine.board.CastlingRights
import chess.engine.board.FenException
import chess.engine.board.Move
import chess.engine.board.Piece
import chess.engine.board.PieceColor
import chess.engine.board.Square
import chess.engine.perft.PerftPositions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PositionTest {
    private fun sq(name: String) = Square.parse(name)

    @Test
    fun startIsTheStandardStartPosition() {
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", Position.START_FEN)
        val start = Position.START
        assertEquals(Position.START_FEN, start.toFen())
        assertEquals(Position.fromFen(Position.START_FEN), start)
        assertEquals(PieceColor.WHITE, start.sideToMove)
        assertEquals(CastlingRights.ALL, start.castlingRights)
        assertEquals(Square.NONE, start.enPassantSquare)
        assertEquals(0, start.halfmoveClock)
        assertEquals(1, start.fullmoveNumber)
        assertEquals(20, start.legalMoves().size)
        assertFalse(start.isCheck)
        assertEquals(Piece.WHITE_KING, start.pieceAt(Square.E1))
        assertEquals(Piece.NONE, start.pieceAt(sq("e4")))
        assertEquals(Square.E1, start.kingSquare(PieceColor.WHITE))
        assertEquals(Square.E8, start.kingSquare(PieceColor.BLACK))
    }

    @Test
    fun fromFenRoundTripsThroughToFen() {
        for (fen in PerftPositions.ALL) {
            val normalized = if (fen.split(' ').size == 4) "$fen 0 1" else fen
            assertEquals(normalized, Position.fromFen(fen).toFen())
        }
    }

    @Test
    fun fromFenRejectsMalformedFen() {
        assertFailsWith<FenException> { Position.fromFen("") }
        assertFailsWith<FenException> { Position.fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP w KQkq - 0 1") }
        assertFailsWith<FenException> { Position.fromFen("8/8/8/8/8/8/8/4K3 w - - 0 1") }
    }

    @Test
    fun fromFenRejectsAPositionWhereTheSideNotToMoveIsInCheck() {
        // The white rook on e1 attacks the black king, but it is White's turn.
        val whiteToMove = assertFailsWith<FenException> { Position.fromFen("4k3/8/8/8/8/8/8/4R1K1 w - - 0 1") }
        assertTrue("check" in whiteToMove.message.orEmpty(), whiteToMove.message)
        assertFailsWith<FenException> { Position.fromFen("4r1k1/8/8/8/8/8/8/4K3 b - - 0 1") }
        // The same placements are fine with the checked side to move.
        assertTrue(Position.fromFen("4k3/8/8/8/8/8/8/4R1K1 b - - 0 1").isCheck)
        assertTrue(Position.fromFen("4r1k1/8/8/8/8/8/8/4K3 w - - 0 1").isCheck)
    }

    @Test
    fun playReturnsTheNextPositionAndLeavesTheOriginalUnchanged() {
        val start = Position.START
        val next = start.play(start.move("e2e4"))
        assertEquals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1", next.toFen())
        assertEquals(PieceColor.BLACK, next.sideToMove)
        assertEquals(sq("e3"), next.enPassantSquare)
        assertEquals(Position.START_FEN, start.toFen())
        assertEquals(20, start.legalMoves().size)
        assertEquals(Position.START, start)
    }

    @Test
    fun playRejectsAMoveThatIsNotLegal() {
        val start = Position.START
        assertFailsWith<IllegalMoveException> { start.play(Move.of(sq("e2"), sq("e5"))) }
        assertFailsWith<IllegalMoveException> { start.play(Move.of(sq("e7"), sq("e5"), flags = Move.DOUBLE_PUSH)) }
        // e2e4 without its double-push flag is not the move the generator produced.
        assertFailsWith<IllegalMoveException> { start.play(Move.of(sq("e2"), sq("e4"))) }
        // A pinned piece cannot leave its line.
        val pinned = Position.fromFen("4k3/4r3/8/8/8/8/4R3/4K3 w - - 0 1")
        assertFalse(pinned.isCheck)
        assertFailsWith<IllegalMoveException> { pinned.play(Move.of(sq("e2"), sq("a2"))) }
        pinned.play(pinned.move("e2e5"))
    }

    @Test
    fun isLegalMatchesTheLegalMoveList() {
        val start = Position.START
        assertTrue(start.isLegal(start.move("g1f3")))
        assertFalse(start.isLegal(Move.of(sq("g1"), sq("g3"))))
    }

    @Test
    fun legalMovesAreComputedOnceAndCannotBeChangedThroughTheList() {
        val start = Position.START
        assertSame(start.legalMoves(), start.legalMoves())
        @Suppress("UNCHECKED_CAST")
        val asMutable = start.legalMoves() as? MutableList<Move>
        if (asMutable != null) assertFailsWith<UnsupportedOperationException> { asMutable.clear() }
        assertEquals(20, start.legalMoves().size)
    }

    @Test
    fun isCheckIsTrueOnlyWhenTheSideToMoveIsAttacked() {
        val afterFoolsMate = Position.START.play("f2f3", "e7e5", "g2g4", "d8h4")
        assertTrue(afterFoolsMate.isCheck)
        assertTrue(afterFoolsMate.legalMoves().isEmpty())
        assertFalse(Position.START.play("e2e4").isCheck)
    }

    @Test
    fun equalityComparesEveryFenField() {
        val a = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        assertEquals(a, Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"))
        assertEquals(a.hashCode(), Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1").hashCode())
        assertNotEquals(a, Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 5 1"))
        assertNotEquals(a, Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQk - 0 1"))
    }

    // Repetition identity (US-5): placement, side to move, castling rights and en passant capture availability.

    @Test
    fun samePositionIgnoresTheClocks() {
        val a = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val b = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 12 40")
        assertTrue(a.samePositionAs(b))
        assertEquals(a.repetitionKey, b.repetitionKey)
    }

    @Test
    fun samePositionNeedsTheSameSideToMovePlacementAndCastlingRights() {
        val a = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val others = listOf(
            "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1",
            "r3k2r/8/8/8/8/8/8/R3K2R w Kkq - 0 1",
            "r3k2r/8/8/8/8/8/8/R3K2R w - - 0 1",
            "r3k2r/8/8/8/8/8/8/R3K1R1 w Qkq - 0 1",
        ).map(Position::fromFen)
        for (other in others) {
            assertFalse(a.samePositionAs(other), other.toFen())
            assertFalse(other.samePositionAs(a), other.toFen())
            assertNotEquals(a.repetitionKey, other.repetitionKey, other.toFen())
        }
        assertTrue(a.samePositionAs(a))
    }

    @Test
    fun anEnPassantSquareWithNoLegalCaptureDoesNotMakeAPositionDifferent() {
        // No black pawn can take on e3.
        val withEp = Position.fromFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")
        val withoutEp = Position.fromFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1")
        assertTrue(withEp.samePositionAs(withoutEp))
        assertEquals(withEp.repetitionKey, withoutEp.repetitionKey)
        assertNotEquals(withEp, withoutEp, "equality still compares the FEN field")
    }

    @Test
    fun anEnPassantCaptureThatIsOnlyPseudoLegalDoesNotMakeAPositionDifferent() {
        // Position 3 after 1.e4: f4xe3 would expose the h4 king to the b4 rook.
        val withEp = Position.fromFen("8/2p5/3p4/KP5r/1R2Pp1k/8/6P1/8 b - e3 0 1")
        val withoutEp = Position.fromFen("8/2p5/3p4/KP5r/1R2Pp1k/8/6P1/8 b - - 0 1")
        assertTrue(withEp.legalMoves().none { it.isEnPassant })
        assertTrue(withEp.samePositionAs(withoutEp))
        assertEquals(withEp.repetitionKey, withoutEp.repetitionKey)
    }

    @Test
    fun aLegalEnPassantCaptureMakesAPositionDifferent() {
        val withEp = Position.fromFen("rnbqkbnr/ppp1pppp/8/8/3pP3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")
        val withoutEp = Position.fromFen("rnbqkbnr/ppp1pppp/8/8/3pP3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1")
        assertTrue(withEp.legalMoves().any { it.isEnPassant })
        assertFalse(withEp.samePositionAs(withoutEp))
        assertFalse(withoutEp.samePositionAs(withEp))
        assertNotEquals(withEp.repetitionKey, withoutEp.repetitionKey)
    }
}
