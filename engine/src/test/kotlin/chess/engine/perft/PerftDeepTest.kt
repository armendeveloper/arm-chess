package chess.engine.perft

import chess.engine.PerftDeep
import chess.engine.board.Perft
import org.junit.experimental.categories.Category
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The release-depth perft column of design §4.2, about 610 M leaf nodes. The default `:engine:test`
 * excludes this class; run it with `./gradlew :engine:perftDeep` before every release.
 */
@Category(PerftDeep::class)
class PerftDeepTest {
    @Test
    fun initialPositionDepth6() {
        assertEquals(119_060_324, Perft.perft(PerftPositions.INITIAL, 6))
    }

    @Test
    fun kiwipeteDepth5() {
        assertEquals(193_690_690, Perft.perft(PerftPositions.KIWIPETE, 5))
    }

    @Test
    fun position3Depth6() {
        assertEquals(11_030_083, Perft.perft(PerftPositions.POSITION_3, 6))
    }

    @Test
    fun position4Depth5() {
        assertEquals(15_833_292, Perft.perft(PerftPositions.POSITION_4, 5))
    }

    @Test
    fun position4MirroredDepth5() {
        assertEquals(15_833_292, Perft.perft(PerftPositions.POSITION_4_MIRRORED, 5))
    }

    @Test
    fun position5Depth5() {
        assertEquals(89_941_194, Perft.perft(PerftPositions.POSITION_5, 5))
    }

    @Test
    fun position6Depth5() {
        assertEquals(164_075_551, Perft.perft(PerftPositions.POSITION_6, 5))
    }
}
