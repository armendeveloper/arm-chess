package chess.engine.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoveTest {
    private val promotions = intArrayOf(Piece.NONE, Piece.KNIGHT, Piece.BISHOP, Piece.ROOK, Piece.QUEEN)

    /** All 16 combinations of the 4 flag bits. */
    private val flagCombinations: List<Int> = (0 until 16).map { mask ->
        var flags = 0
        if (mask and 1 != 0) flags = flags or Move.CAPTURE
        if (mask and 2 != 0) flags = flags or Move.DOUBLE_PUSH
        if (mask and 4 != 0) flags = flags or Move.EN_PASSANT
        if (mask and 8 != 0) flags = flags or Move.CASTLE
        flags
    }

    @Test
    fun flagConstantsAreDistinctSingleBits() {
        val flags = listOf(Move.CAPTURE, Move.DOUBLE_PUSH, Move.EN_PASSANT, Move.CASTLE)
        flags.forEach { assertEquals(1, Integer.bitCount(it)) }
        assertEquals(16, flagCombinations.toSet().size)
    }

    @Test
    fun packUnpackCoversEveryFromToPromotionAndFlagCombination() {
        for (from in 0 until 64) {
            for (to in 0 until 64) {
                for (promo in promotions) {
                    for (flags in flagCombinations) {
                        val m = Move.of(from, to, promo, flags)
                        if (m.from != from || m.to != to || m.promotion != promo || m.flags != flags) {
                            throw AssertionError("pack/unpack mismatch for from=$from to=$to promo=$promo flags=$flags: got $m")
                        }
                        if (m.isCapture != (flags and Move.CAPTURE != 0) ||
                            m.isDoublePush != (flags and Move.DOUBLE_PUSH != 0) ||
                            m.isEnPassant != (flags and Move.EN_PASSANT != 0) ||
                            m.isCastle != (flags and Move.CASTLE != 0) ||
                            m.isPromotion != (promo != Piece.NONE)
                        ) {
                            throw AssertionError("flag accessor mismatch for flags=$flags promo=$promo")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun differentFieldsGiveDifferentMoves() {
        val base = Move.of(Square.parse("e7"), Square.parse("e8"), Piece.QUEEN, 0)
        assertNotEquals(base, Move.of(Square.parse("e7"), Square.parse("e8"), Piece.KNIGHT, 0))
        assertNotEquals(base, Move.of(Square.parse("e7"), Square.parse("e8"), Piece.QUEEN, Move.CAPTURE))
        assertNotEquals(base, Move.of(Square.parse("e7"), Square.parse("d8"), Piece.QUEEN, 0))
        assertEquals(base, Move.of(Square.parse("e7"), Square.parse("e8"), Piece.QUEEN, 0))
    }

    @Test
    fun nonePacksToZeroAndIsNotARealMove() {
        assertEquals(0, Move.NONE.bits)
        assertEquals(Move.NONE.from, Move.NONE.to)
    }

    @Test
    fun uciForQuietMovesCastlingAndPromotions() {
        assertEquals("e2e4", Move.of(Square.parse("e2"), Square.parse("e4"), flags = Move.DOUBLE_PUSH).uci())
        assertEquals("e1g1", Move.of(Square.E1, Square.G1, flags = Move.CASTLE).uci())
        assertEquals("e8c8", Move.of(Square.E8, Square.C8, flags = Move.CASTLE).uci())
        assertEquals("e5d6", Move.of(Square.parse("e5"), Square.parse("d6"), flags = Move.CAPTURE or Move.EN_PASSANT).uci())
        assertEquals("e7e8q", Move.of(Square.parse("e7"), Square.parse("e8"), Piece.QUEEN).uci())
        assertEquals("b2a1n", Move.of(Square.parse("b2"), Square.A1, Piece.KNIGHT, Move.CAPTURE).uci())
        assertEquals("a7a8r", Move.of(Square.parse("a7"), Square.A8, Piece.ROOK).uci())
        assertEquals("h7h8b", Move.of(Square.parse("h7"), Square.H8, Piece.BISHOP).uci())
        assertEquals("e7e8q", Move.of(Square.parse("e7"), Square.parse("e8"), Piece.QUEEN).toString())
    }

    @Test
    fun promotionTypeIsExposedForTheApp() {
        assertEquals(PieceType.KNIGHT, Move.of(Square.parse("e7"), Square.parse("e8"), Piece.KNIGHT).promotionType)
        assertNull(Move.of(Square.parse("e2"), Square.parse("e3")).promotionType)
        assertFalse(Move.of(Square.parse("e2"), Square.parse("e3")).isPromotion)
        assertTrue(Move.of(Square.parse("e7"), Square.parse("e8"), Piece.ROOK).isPromotion)
    }
}
