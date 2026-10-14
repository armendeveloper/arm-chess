package chess.engine.perft

import chess.engine.board.Fen
import chess.engine.board.Perft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The perft gate (design §4.2): node counts from the Chessprogramming wiki, "Perft Results"
 * (checked 2026-10-14), at the depths of the gate column, which the default `:engine:test` runs.
 * Together they exercise castling, en passant (including rank pins), promotion and
 * under-promotion. The deeper release column is [PerftDeepTest], run by `:engine:perftDeep`.
 */
class PerftTest {
    /** Asserts perft of [fen] at depth 1, 2, ... against [expected], in order. */
    private fun assertPerft(name: String, fen: String, vararg expected: Long) {
        val board = Fen.parse(fen)
        for ((index, nodes) in expected.withIndex()) {
            val depth = index + 1
            assertEquals(nodes, Perft.perft(board, depth), "$name, depth $depth")
        }
    }

    @Test
    fun initialPositionDepth0To5() {
        assertEquals(1, Perft.perft(PerftPositions.INITIAL, 0), "initial position, depth 0")
        assertPerft("initial position", PerftPositions.INITIAL, 20, 400, 8_902, 197_281, 4_865_609)
    }

    @Test
    fun kiwipeteDepth1To4() {
        assertPerft("Kiwipete", PerftPositions.KIWIPETE, 48, 2_039, 97_862, 4_085_603)
    }

    @Test
    fun position3Depth1To5() {
        assertPerft("position 3", PerftPositions.POSITION_3, 14, 191, 2_812, 43_238, 674_624)
    }

    @Test
    fun position4Depth1To4() {
        assertPerft("position 4", PerftPositions.POSITION_4, 6, 264, 9_467, 422_333)
    }

    @Test
    fun position4MirroredDepth1To4() {
        assertPerft("position 4 mirrored", PerftPositions.POSITION_4_MIRRORED, 6, 264, 9_467, 422_333)
    }

    @Test
    fun position5Depth1To4() {
        assertPerft("position 5", PerftPositions.POSITION_5, 44, 1_486, 62_379, 2_103_487)
    }

    @Test
    fun position6Depth1To4() {
        assertPerft("position 6", PerftPositions.POSITION_6, 46, 2_079, 89_890, 3_894_594)
    }

    @Test
    fun perftLeavesTheBoardUnchanged() {
        for (fen in PerftPositions.ALL) {
            val board = Fen.parse(fen)
            val before = board.copy()
            Perft.perft(board, 3)
            assertEquals(before, board, fen)
        }
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
    fun divideListsCastlingAndPromotionMovesInUciForm() {
        val kiwipete = Perft.divide(PerftPositions.KIWIPETE, 1)
        assertEquals(48, kiwipete.size)
        assertEquals(1L, kiwipete["e1g1"])
        assertEquals(1L, kiwipete["e1c1"])

        val position4 = Perft.divide(PerftPositions.POSITION_4, 2)
        assertEquals(setOf("b4c5", "c4c5", "d2d4", "f1f2", "f3d4", "g1h1"), position4.keys)
        assertEquals(264, position4.values.sum())
        val mirrored = Perft.divide(PerftPositions.POSITION_4_MIRRORED, 1)
        assertEquals(setOf("b5c4", "c5c4", "d7d5", "f8f7", "f6d5", "g8h8"), mirrored.keys)
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
