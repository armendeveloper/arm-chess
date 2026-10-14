package chess.app.ui.game.board

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import chess.app.ui.game.BoardUi
import chess.app.ui.game.LastMoveUi
import chess.app.ui.game.PieceKind
import chess.app.ui.game.PieceUi
import chess.app.ui.game.Side
import chess.app.ui.game.Squares
import chess.app.ui.game.TargetKind
import chess.app.ui.theme.ArmChessBoardColors
import chess.app.ui.theme.ArmChessTheme
import chess.app.ui.theme.BoardMetrics
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Canvas really draws the layers (design §4.3.3) in the right place for each orientation.
 * Robolectric's native graphics are not pixel-exact for anti-aliased edges (ADR-8), so these
 * tests sample pixels well inside each mark and allow a small per-channel tolerance.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp-xhdpi")
class ChessBoardRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val colors = ArmChessBoardColors

    @Test
    fun squareShadesAlternateWithA1Dark() {
        val px = render(emptyBoard(Side.White))
        assertColor(colors.squareLight, px.center(row = 0, col = 0), "a8") // light
        assertColor(colors.squareDark, px.center(row = 0, col = 1), "b8")
        assertColor(colors.squareDark, px.center(row = 7, col = 0), "a1")
        assertColor(colors.squareLight, px.center(row = 7, col = 7), "h1")
        assertColor(colors.squareLight, px.center(row = 4, col = 4), "e4")
    }

    @Test
    fun pieceIsDrawnOnItsSquareForWhite() {
        val board = emptyBoard(Side.White).withPiece("e4", PieceUi(Side.White, PieceKind.Queen))
        val px = render(board)
        assertTrue(px.inkedFraction(row = 4, col = 4, colors.squareLight) > 0.15f, "queen on e4 (row 4, col 4)")
        assertEquals(0f, px.inkedFraction(row = 3, col = 3, colors.squareLight), "nothing on d5 (row 3, col 3)")
    }

    @Test
    fun pieceIsDrawnOnItsSquareForBlack() {
        val board = emptyBoard(Side.Black).withPiece("e4", PieceUi(Side.White, PieceKind.Queen))
        val px = render(board)
        assertTrue(px.inkedFraction(row = 3, col = 3, colors.squareLight) > 0.15f, "queen on e4 (row 3, col 3)")
        assertEquals(0f, px.inkedFraction(row = 4, col = 4, colors.squareLight), "nothing on d5 (row 4, col 4)")
    }

    @Test
    fun lastMoveTintsBothSquaresAndMarksTheirBottomLeftCorner() {
        val board = emptyBoard(Side.White).copy(lastMove = LastMoveUi(sq("d2"), sq("d4")))
        val px = render(board)
        // d4 is dark, d2 is dark (row 4/6, col 3).
        assertColor(colors.squareDarkLastMove, px.center(row = 4, col = 3), "d4 tint")
        assertColor(colors.squareDarkLastMove, px.center(row = 6, col = 3), "d2 tint")
        val leg = px.metrics().let { it.lastMoveCorner / it.square }
        assertColor(colors.cueMark, px.at(row = 4, col = 3, fx = leg / 4, fy = 1f - leg / 4), "d4 corner")
        assertColor(colors.squareDarkLastMove, px.at(row = 4, col = 3, fx = 0.96f, fy = 0.04f), "d4 other corner")
    }

    @Test
    fun selectedSquareHasAnInkBorderWithAHaloInside() {
        val board = emptyBoard(Side.White).copy(selected = sq("e4"))
        val px = render(board)
        val m = px.metrics()
        val borderMid = m.selectedStroke / 2 / m.square
        val haloMid = (m.selectedStroke + m.halo / 2) / m.square
        assertColor(colors.cueInk, px.at(row = 4, col = 4, fx = borderMid, fy = 0.5f), "border")
        assertColor(colors.cueHalo, px.at(row = 4, col = 4, fx = haloMid, fy = 0.5f), "halo")
        assertColor(colors.squareLight, px.center(row = 4, col = 4), "centre")
    }

    @Test
    fun quietTargetIsADotAndCaptureIsAHollowRing() {
        val board = emptyBoard(Side.White).copy(
            targets = mapOf(sq("e4") to TargetKind.Move, sq("c4") to TargetKind.EnPassant),
        )
        val px = render(board)
        assertColor(colors.cueInk, px.center(row = 4, col = 4), "dot")
        assertColor(colors.squareLight, px.center(row = 4, col = 2), "ring is hollow")
        // The ring's ink band is ringStroke wide, inside the 0.92 s outer diameter.
        val m = px.metrics()
        val bandMid = (m.ringDiameter - m.ringStroke) / 2 / m.square
        assertColor(colors.cueInk, px.at(row = 4, col = 2, fx = 0.5f, fy = 0.5f - bandMid), "ring band")
    }

    @Test
    fun checkShowsARedBadgeTopRightAndATint() {
        val board = emptyBoard(Side.White).copy(checkSquare = sq("e4"))
        val px = render(board)
        // Badge: max(14 dp, 0.36 s) across, inset 2 dp from the top-right corner. Sample off the "+" arms.
        val m = px.metrics()
        val r = m.checkBadge / 2 / m.square
        val inset = m.coordInset / m.square
        val cx = 1f - inset - r
        val cy = inset + r
        assertColor(colors.cueCheck, px.at(row = 4, col = 4, fx = cx + r * 0.55f, fy = cy + r * 0.55f), "badge")
        assertColor(colors.cueCheckGlyph, px.at(row = 4, col = 4, fx = cx, fy = cy), "plus glyph")
        val centre = px.center(row = 4, col = 4)
        assertTrue(centre.green < colors.squareLight.green - 0.1f, "centre is tinted red: $centre")
    }

    @Test
    fun coordinatesAreDrawnOnTheEdgeSquares() {
        val px = render(emptyBoard(Side.White))
        // The rank digit sits in the top-left of a-file squares; the file letter bottom-right of rank 1.
        assertTrue(px.inkedFraction(row = 7, col = 0, colors.squareDark, region = 0f..0.4f, 0f..0.4f) > 0f, "1 on a1")
        assertTrue(px.inkedFraction(row = 7, col = 7, colors.squareLight, region = 0.6f..1f, 0.6f..1f) > 0f, "h on h1")
        assertEquals(0f, px.inkedFraction(row = 3, col = 3, colors.squareLight), "no label on d5")
    }

    // --- helpers ---

    private fun render(board: BoardUi): PixelMap {
        composeRule.setContent {
            ArmChessTheme {
                ChessBoard(board = board, onSquareClick = {}, modifier = Modifier.size(BOARD_DP.dp))
            }
        }
        return composeRule.onNodeWithTag(ChessBoardTestTag).captureToImage().toPixelMap()
    }

    /** The board is 320 dp wide, so its pixel width gives the density. */
    private fun PixelMap.metrics() = BoardMetrics.of(squarePx = width / 8f, density = width / BOARD_DP)

    private fun PixelMap.at(row: Int, col: Int, fx: Float, fy: Float): Color {
        val s = width / 8f
        val x = ((col + fx) * s).roundToInt().coerceIn(0, width - 1)
        val y = ((row + fy) * s).roundToInt().coerceIn(0, height - 1)
        return this[x, y]
    }

    private fun PixelMap.center(row: Int, col: Int) = at(row, col, 0.5f, 0.5f)

    /** Share of pixels in a cell (or part of it) that differ clearly from [background]. */
    private fun PixelMap.inkedFraction(
        row: Int,
        col: Int,
        background: Color,
        region: ClosedFloatingPointRange<Float> = 0f..1f,
        regionY: ClosedFloatingPointRange<Float> = 0f..1f,
    ): Float {
        val s = width / 8
        var inked = 0
        var total = 0
        val xs = (col * s + (region.start * s).toInt()) until (col * s + (region.endInclusive * s).toInt())
        val ys = (row * s + (regionY.start * s).toInt()) until (row * s + (regionY.endInclusive * s).toInt())
        for (y in ys) for (x in xs) {
            total++
            if (!close(this[x, y], background, 0.06f)) inked++
        }
        return inked.toFloat() / total
    }

    private fun assertColor(expected: Color, actual: Color, what: String) {
        assertTrue(close(expected, actual, 0.03f), "$what: expected $expected but was $actual")
    }

    private fun close(a: Color, b: Color, tolerance: Float) =
        abs(a.red - b.red) <= tolerance && abs(a.green - b.green) <= tolerance && abs(a.blue - b.blue) <= tolerance

    private fun sq(name: String) = Squares.parse(name)

    private fun emptyBoard(player: Side) = BoardUi(pieces = List(64) { null }, player = player)

    private fun BoardUi.withPiece(square: String, piece: PieceUi) =
        copy(pieces = pieces.toMutableList().apply { this[sq(square)] = piece })

    private companion object {
        const val BOARD_DP = 320f
    }
}
