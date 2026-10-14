package chess.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import chess.app.testutil.Wcag
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/** Board tokens (design §6.2) and the contrast pairs that design §5.6 says must pass. */
class BoardColorsTest {

    private val board = ArmChessBoardColors

    @Test
    fun wcagHelperMatchesKnownValues() {
        assertEquals(21.0, Wcag.contrastRatio(Color.Black, Color.White), 0.001)
        assertEquals(1.0, Wcag.contrastRatio(Color.White, Color.White), 0.001)
    }

    @Test
    fun tokensHaveTheDesignValues() {
        assertArgb(0xFFF0D9B5, board.squareLight)
        assertArgb(0xFF8C6A4E, board.squareDark)
        assertArgb(0xFFE8D37A, board.squareLightLastMove)
        assertArgb(0xFF857030, board.squareDarkLastMove)
        assertArgb(0xFF6B4E37, board.coordOnLight)
        assertArgb(0xFFFFFFFF, board.coordOnDark)
        assertArgb(0xFF0D47A1, board.cueInk)
        assertArgb(0xFFFFFFFF, board.cueHalo)
        assertArgb(0xFFB71C1C, board.cueCheck)
        assertArgb(0xFFFFFFFF, board.cueCheckGlyph)
        assertArgb(0xFF212121, board.cueMark)
    }

    @Test
    fun luminanceMatchesTheDesignTable() {
        assertLuminance(0.715, board.squareLight)
        assertLuminance(0.164, board.squareDark)
        assertLuminance(0.651, board.squareLightLastMove)
        assertLuminance(0.168, board.squareDarkLastMove)
        assertLuminance(0.089, board.coordOnLight)
        assertLuminance(0.072, board.cueInk)
        assertLuminance(0.110, board.cueCheck)
        assertLuminance(0.015, board.cueMark)
    }

    @Test
    fun coordinatesMeetTextContrastOnEverySquareType() {
        assertContrast(TEXT, board.coordOnLight, board.squareLight)
        assertContrast(TEXT, board.coordOnLight, board.squareLightLastMove)
        assertContrast(TEXT, board.coordOnDark, board.squareDark)
        assertContrast(TEXT, board.coordOnDark, board.squareDarkLastMove)
    }

    @Test
    fun inkCuesHaveAnEdgeWithNonTextContrastOnEverySquareType() {
        // Light squares: the ink core itself passes.
        assertContrast(NON_TEXT, board.cueInk, board.squareLight)
        assertContrast(NON_TEXT, board.cueInk, board.squareLightLastMove)
        // Dark squares: ink alone fails (1.8:1), so the white halo carries the edge (halo rule, §5.6).
        assertTrue(Wcag.contrastRatio(board.cueInk, board.squareDark) < NON_TEXT)
        assertContrast(NON_TEXT, board.cueHalo, board.squareDark)
        assertContrast(NON_TEXT, board.cueHalo, board.squareDarkLastMove)
        assertContrast(NON_TEXT, board.cueInk, board.cueHalo)
    }

    @Test
    fun checkCuesHaveAnEdgeWithNonTextContrastOnEverySquareType() {
        assertContrast(NON_TEXT, board.cueCheck, board.squareLight)
        assertContrast(NON_TEXT, board.cueCheck, board.squareLightLastMove)
        // Dark squares: the white halo again (check red alone is 1.3:1).
        assertTrue(Wcag.contrastRatio(board.cueCheck, board.squareDark) < NON_TEXT)
        assertContrast(NON_TEXT, board.cueHalo, board.squareDark)
        assertContrast(NON_TEXT, board.cueCheckGlyph, board.cueCheck)
    }

    @Test
    fun lastMoveMarkPassesOnEverySquareType() {
        assertContrast(NON_TEXT, board.cueMark, board.squareLight)
        assertContrast(NON_TEXT, board.cueMark, board.squareLightLastMove)
        assertContrast(NON_TEXT, board.cueMark, board.squareDark)
        assertContrast(NON_TEXT, board.cueMark, board.squareDarkLastMove)
    }

    private fun assertArgb(expected: Long, actual: Color) =
        assertEquals(expected.toInt(), actual.toArgb(), "expected #%08X".format(expected))

    private fun assertLuminance(expected: Double, color: Color) =
        assertEquals(expected, Wcag.relativeLuminance(color), 0.001)

    private fun assertContrast(minimum: Double, foreground: Color, background: Color) {
        val ratio = Wcag.contrastRatio(foreground, background)
        assertTrue(
            ratio >= minimum,
            "#%08X on #%08X is %.2f:1, needs %.1f:1"
                .format(foreground.toArgb(), background.toArgb(), ratio, minimum),
        )
    }

    private companion object {
        const val TEXT = 4.5 // WCAG 1.4.3
        const val NON_TEXT = 3.0 // WCAG 1.4.11
    }
}
