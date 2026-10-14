package chess.engine.perft

import chess.engine.board.Fen
import chess.engine.board.Perft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Perft node counts from the Chessprogramming wiki, "Perft Results" (design §4.2, checked
 * 2026-10-14). T-4 covers the depths that need no castling, en passant or promotion; T-5 adds the
 * full gate column.
 */
class PerftTest {
    private val position3 = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"

    @Test
    fun initialPositionDepth0To4() {
        val expected = longArrayOf(1, 20, 400, 8_902, 197_281)
        for (depth in expected.indices) {
            assertEquals(expected[depth], Perft.perft(Fen.parse(Fen.START), depth), "initial position, depth $depth")
        }
    }

    @Test
    fun position3Depth1To2() {
        // No en passant, castling or promotion occurs before depth 3 here.
        assertEquals(14, Perft.perft(Fen.parse(position3), 1))
        assertEquals(191, Perft.perft(Fen.parse(position3), 2))
    }

    @Test
    fun perftLeavesTheBoardUnchanged() {
        val board = Fen.parse(Fen.START)
        val before = board.copy()
        Perft.perft(board, 3)
        assertEquals(before, board)
    }

    @Test
    fun divideSplitsTheCountByRootMove() {
        val divide = Perft.divide(Fen.parse(Fen.START), 3)
        assertEquals(20, divide.size)
        assertEquals(8_902, divide.values.sum())
        assertEquals(600, divide["e2e4"])
        assertEquals(560, divide["d2d4"])
        assertEquals(380, divide["a2a3"])
        assertEquals(440, divide["g1f3"])
        assertEquals(divide.keys.sorted(), divide.keys.toList(), "divide lists moves in UCI order")
    }

    @Test
    fun divideAtDepth1CountsEachMoveOnce() {
        val divide = Perft.divide(Fen.parse(Fen.START), 1)
        assertEquals(20, divide.size)
        assertEquals(setOf(1L), divide.values.toSet())
    }

    @Test
    fun fenOverloadsMatchTheBoardVersions() {
        assertEquals(8_902, Perft.perft(Fen.START, 3))
        assertEquals(Perft.divide(Fen.parse(Fen.START), 2), Perft.divide(Fen.START, 2))
    }

    @Test
    fun invalidDepthsAreRejected() {
        assertFailsWith<IllegalArgumentException> { Perft.perft(Fen.START, -1) }
        assertFailsWith<IllegalArgumentException> { Perft.divide(Fen.START, 0) }
    }
}
