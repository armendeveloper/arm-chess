package chess.engine.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SquareTest {
    @Test
    fun cornerAndCentreSquaresHaveTheDesignIndices() {
        assertEquals(0, Square.parse("a1"))
        assertEquals(7, Square.parse("h1"))
        assertEquals(28, Square.parse("e4"))
        assertEquals(56, Square.parse("a8"))
        assertEquals(63, Square.parse("h8"))
    }

    @Test
    fun namedConstantsMatchTheirNames() {
        val named = mapOf(
            Square.A1 to "a1", Square.B1 to "b1", Square.C1 to "c1", Square.D1 to "d1",
            Square.E1 to "e1", Square.F1 to "f1", Square.G1 to "g1", Square.H1 to "h1",
            Square.A8 to "a8", Square.B8 to "b8", Square.C8 to "c8", Square.D8 to "d8",
            Square.E8 to "e8", Square.F8 to "f8", Square.G8 to "g8", Square.H8 to "h8",
        )
        for ((sq, name) in named) assertEquals(name, Square.name(sq))
    }

    @Test
    fun nameAndParseRoundTripForAll64Squares() {
        val files = "abcdefgh"
        for (sq in 0 until 64) {
            val expected = "${files[sq % 8]}${sq / 8 + 1}"
            assertEquals(expected, Square.name(sq))
            assertEquals(sq, Square.parse(expected))
            assertEquals(sq, Square.parseOrNull(expected))
        }
    }

    @Test
    fun fileRankAndOfAgree() {
        for (rank in 0 until 8) {
            for (file in 0 until 8) {
                val sq = Square.of(file, rank)
                assertEquals(file, Square.file(sq))
                assertEquals(rank, Square.rank(sq))
            }
        }
        assertEquals(4, Square.file(Square.parse("e7")))
        assertEquals(6, Square.rank(Square.parse("e7")))
    }

    @Test
    fun conversionTo0x88AndBackRoundTripsAndStaysOnBoard() {
        val seen = HashSet<Int>()
        for (sq in 0 until 64) {
            val sq88 = Square.to0x88(sq)
            assertTrue(Square.isOnBoard0x88(sq88), "0x88 index $sq88 for ${Square.name(sq)} must be on board")
            assertEquals(Square.rank(sq) * 16 + Square.file(sq), sq88)
            assertEquals(sq, Square.from0x88(sq88))
            assertTrue(seen.add(sq88))
        }
        assertEquals(0x00, Square.to0x88(Square.A1))
        assertEquals(0x34, Square.to0x88(Square.parse("e4")))
        assertEquals(0x77, Square.to0x88(Square.H8))
    }

    @Test
    fun exactly64Of128IndicesAreOnBoardIn0x88() {
        val onBoard = (0 until 128).filter { Square.isOnBoard0x88(it) }
        assertEquals(64, onBoard.size)
        assertEquals((0 until 64).toList(), onBoard.map { Square.from0x88(it) })
    }

    @Test
    fun stepsOffTheBoardAreDetectedIn0x88() {
        val h4 = Square.to0x88(Square.parse("h4"))
        val a4 = Square.to0x88(Square.parse("a4"))
        assertFalse(Square.isOnBoard0x88(h4 + 1), "east of h-file")
        assertFalse(Square.isOnBoard0x88(a4 - 1), "west of a-file")
        assertFalse(Square.isOnBoard0x88(Square.to0x88(Square.H8) + 16), "north of rank 8")
        assertFalse(Square.isOnBoard0x88(Square.to0x88(Square.A1) - 16), "south of rank 1")
    }

    @Test
    fun invalidNamesAreRejected() {
        for (bad in listOf("", "e", "e44", "i1", "a0", "a9", "E4", "4e", " e4")) {
            assertNull(Square.parseOrNull(bad), "'$bad' should not parse")
            val e = assertFailsWith<IllegalArgumentException> { Square.parse(bad) }
            assertTrue(e.message!!.contains("'$bad'"), e.message)
        }
    }

    @Test
    fun nameRejectsOutOfRangeIndices() {
        assertFailsWith<IllegalArgumentException> { Square.name(-1) }
        assertFailsWith<IllegalArgumentException> { Square.name(64) }
    }
}
