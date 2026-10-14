package chess.app.ui.theme

import kotlin.test.assertEquals
import org.junit.Test

/** Board geometry tokens (design §6.2): fractions of the square with dp minimums. */
class BoardMetricsTest {

    @Test
    fun fractionsWinOnA45dpSquare() {
        val m = BoardMetrics.of(squarePx = 45f, density = 1f)
        assertEquals(45f, m.square, TOL)
        assertEquals(13.5f, m.dotDiameter, TOL) // 0.30 s > 12 dp
        assertEquals(41.4f, m.ringDiameter, TOL) // 0.92 s
        assertEquals(3.6f, m.ringStroke, TOL) // 0.08 s > 3 dp
        assertEquals(3.6f, m.selectedStroke, TOL)
        assertEquals(1.5f, m.halo, TOL)
        assertEquals(16.2f, m.checkBadge, TOL) // 0.36 s > 14 dp
        assertEquals(2.25f, m.checkGlyphStroke, TOL) // 0.05 s
        assertEquals(9.9f, m.lastMoveCorner, TOL) // 0.22 s > 8 dp
        assertEquals(10.8f, m.coordSize, TOL) // 0.24 s
        assertEquals(2f, m.coordInset, TOL)
    }

    @Test
    fun dpMinimumsWinOnASmallSquare() {
        // A 24 dp square (the design's floor) on a 2x screen.
        val m = BoardMetrics.of(squarePx = 48f, density = 2f)
        assertEquals(24f, m.dotDiameter, TOL) // 12 dp
        assertEquals(6f, m.ringStroke, TOL) // 3 dp
        assertEquals(6f, m.selectedStroke, TOL)
        assertEquals(3f, m.halo, TOL) // 1.5 dp
        assertEquals(28f, m.checkBadge, TOL) // 14 dp
        assertEquals(16f, m.lastMoveCorner, TOL) // 8 dp
        assertEquals(44.16f, m.ringDiameter, TOL) // always 0.92 s
        assertEquals(11.52f, m.coordSize, TOL) // always 0.24 s: scales with the board, not the font
        assertEquals(4f, m.coordInset, TOL)
    }

    private companion object {
        const val TOL = 0.001f
    }
}
