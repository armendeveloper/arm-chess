package chess.app.ui.game.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import chess.app.ui.game.BoardUi
import chess.app.ui.game.PieceUi
import chess.app.ui.game.Side
import chess.app.ui.game.Squares
import chess.app.ui.theme.BoardColors
import chess.app.ui.theme.BoardMetrics

// --- Orientation (design §4.3.3): the player's side is at the bottom. ---
// White sees a8 top-left and h1 bottom-right; Black sees h1 top-left and a8 bottom-right.

/** The square shown at visual [row] (0 = top) and [col] (0 = left). */
internal fun squareAt(row: Int, col: Int, player: Side): Int = when (player) {
    Side.White -> Squares.of(file = col, rank = 7 - row)
    Side.Black -> Squares.of(file = 7 - col, rank = row)
}

internal fun visualRow(square: Int, player: Side): Int = when (player) {
    Side.White -> 7 - Squares.rank(square)
    Side.Black -> Squares.rank(square)
}

internal fun visualCol(square: Int, player: Side): Int = when (player) {
    Side.White -> Squares.file(square)
    Side.Black -> 7 - Squares.file(square)
}

/** All 64 squares in reading order: row by row from the top, left to right (design §5.3). */
internal fun visualOrder(player: Side): List<Int> = List(64) { squareAt(it / 8, it % 8, player) }

/** The square under a point [x], [y] in board pixels, or null if the point is off the board. */
internal fun squareAtOffset(x: Float, y: Float, squarePx: Float, player: Side): Int? {
    if (squarePx <= 0f || x < 0f || y < 0f) return null
    val col = (x / squarePx).toInt()
    val row = (y / squarePx).toInt()
    if (col > 7 || row > 7) return null
    return squareAt(row, col, player)
}

/** a1 is dark; light squares have an odd file + rank. */
internal fun isLightSquare(square: Int): Boolean = (Squares.file(square) + Squares.rank(square)) % 2 == 1

// --- What to draw ---

/** One thing the board draws on one square. [layer] gives the drawing order, bottom first. */
internal sealed interface BoardMark {
    val square: Int
    val layer: Layer

    /** Design §4.3.3 drawing layers, bottom to top. */
    enum class Layer { Squares, CheckTint, Coordinates, LastMoveCorner, Pieces, Selected, Targets, CheckBadge }

    data class SquareFill(override val square: Int, val light: Boolean, val lastMove: Boolean) : BoardMark {
        override val layer get() = Layer.Squares
    }

    data class CheckTint(override val square: Int) : BoardMark {
        override val layer get() = Layer.CheckTint
    }

    /** Rank digit, top-left of each square in the leftmost visual file. */
    data class RankLabel(override val square: Int, val text: String, val onLight: Boolean) : BoardMark {
        override val layer get() = Layer.Coordinates
    }

    /** File letter, bottom-right of each square in the bottom visual rank. */
    data class FileLabel(override val square: Int, val text: String, val onLight: Boolean) : BoardMark {
        override val layer get() = Layer.Coordinates
    }

    data class LastMoveCorner(override val square: Int) : BoardMark {
        override val layer get() = Layer.LastMoveCorner
    }

    data class PieceMark(override val square: Int, val piece: PieceUi) : BoardMark {
        override val layer get() = Layer.Pieces
    }

    data class SelectedBorder(override val square: Int) : BoardMark {
        override val layer get() = Layer.Selected
    }

    data class MoveDot(override val square: Int) : BoardMark {
        override val layer get() = Layer.Targets
    }

    data class CaptureRing(override val square: Int) : BoardMark {
        override val layer get() = Layer.Targets
    }

    data class CheckBadge(override val square: Int) : BoardMark {
        override val layer get() = Layer.CheckBadge
    }
}

/** Everything the board draws for [board], bottom layer first. Pure, so the JVM tests pin it. */
internal fun boardMarks(board: BoardUi): List<BoardMark> = buildList {
    val player = board.player
    val lastMoveSquares = board.lastMove?.let { setOf(it.from, it.to) }.orEmpty()

    for (square in 0..63) {
        add(BoardMark.SquareFill(square, isLightSquare(square), square in lastMoveSquares))
    }
    board.checkSquare?.let { add(BoardMark.CheckTint(it)) }
    for (square in 0..63) {
        if (visualCol(square, player) == 0) {
            add(BoardMark.RankLabel(square, Squares.rankName(Squares.rank(square)), isLightSquare(square)))
        }
        if (visualRow(square, player) == 7) {
            add(BoardMark.FileLabel(square, Squares.fileName(Squares.file(square)), isLightSquare(square)))
        }
    }
    board.lastMove?.let {
        add(BoardMark.LastMoveCorner(it.from))
        add(BoardMark.LastMoveCorner(it.to))
    }
    board.pieces.forEachIndexed { square, piece -> if (piece != null) add(BoardMark.PieceMark(square, piece)) }
    board.selected?.let { add(BoardMark.SelectedBorder(it)) }
    board.targets.toSortedMap().forEach { (square, kind) ->
        add(if (kind.isCapture) BoardMark.CaptureRing(square) else BoardMark.MoveDot(square))
    }
    board.checkSquare?.let { add(BoardMark.CheckBadge(it)) }
}

// --- How to draw it ---

/** Fixed inputs for [drawBoard] that don't change from frame to frame. */
internal class BoardPaint(
    val colors: BoardColors,
    val pieces: Map<PieceUi, Painter>,
    val textMeasurer: TextMeasurer,
    /** Coordinate text style without a size; the size comes from [BoardMetrics.coordSize]. */
    val coordStyle: TextStyle,
)

/** Draws [marks] (from [boardMarks]) onto a board whose squares are [metrics]`.square` pixels. */
internal fun DrawScope.drawBoard(marks: List<BoardMark>, player: Side, metrics: BoardMetrics, paint: BoardPaint) {
    val s = metrics.square
    val colors = paint.colors
    val coordStyle = paint.coordStyle.copy(fontSize = metrics.coordSize.toSp())
    for (mark in marks) {
        val topLeft = Offset(visualCol(mark.square, player) * s, visualRow(mark.square, player) * s)
        when (mark) {
            is BoardMark.SquareFill -> drawRect(mark.fillColor(colors), topLeft, Size(s, s))
            is BoardMark.CheckTint -> drawCheckTint(topLeft, s, colors.cueCheck)
            is BoardMark.RankLabel -> {
                val layout = paint.textMeasurer.measure(mark.text, coordStyle)
                drawText(
                    layout,
                    color = if (mark.onLight) colors.coordOnLight else colors.coordOnDark,
                    topLeft = topLeft + Offset(metrics.coordInset, metrics.coordInset),
                )
            }
            is BoardMark.FileLabel -> {
                val layout = paint.textMeasurer.measure(mark.text, coordStyle)
                val size = layout.size
                drawText(
                    layout,
                    color = if (mark.onLight) colors.coordOnLight else colors.coordOnDark,
                    topLeft = topLeft + Offset(
                        s - metrics.coordInset - size.width,
                        s - metrics.coordInset - size.height,
                    ),
                )
            }
            is BoardMark.LastMoveCorner -> drawLastMoveCorner(topLeft, s, metrics.lastMoveCorner, colors.cueMark)
            is BoardMark.PieceMark -> paint.pieces[mark.piece]?.let { painter ->
                translate(topLeft.x, topLeft.y) { with(painter) { draw(Size(s, s)) } }
            }
            is BoardMark.SelectedBorder -> drawSelectedBorder(topLeft, metrics, colors)
            is BoardMark.MoveDot -> {
                val centre = topLeft + Offset(s / 2, s / 2)
                drawCircle(colors.cueHalo, metrics.dotDiameter / 2 + metrics.halo, centre)
                drawCircle(colors.cueInk, metrics.dotDiameter / 2, centre)
            }
            is BoardMark.CaptureRing -> {
                val centre = topLeft + Offset(s / 2, s / 2)
                val radius = metrics.ringDiameter / 2 - metrics.ringStroke / 2
                // Halo on both edges: a wider white ring under the ink ring.
                drawCircle(colors.cueHalo, radius, centre, style = Stroke(metrics.ringStroke + 2 * metrics.halo))
                drawCircle(colors.cueInk, radius, centre, style = Stroke(metrics.ringStroke))
            }
            is BoardMark.CheckBadge -> drawCheckBadge(topLeft, metrics, colors)
        }
    }
}

private fun BoardMark.SquareFill.fillColor(colors: BoardColors): Color = when {
    light && lastMove -> colors.squareLightLastMove
    light -> colors.squareLight
    lastMove -> colors.squareDarkLastMove
    else -> colors.squareDark
}

/** Decorative radial tint: check red at 55% in the centre, fading to 0% at 70% of the radius. */
private fun DrawScope.drawCheckTint(topLeft: Offset, s: Float, check: Color) {
    val centre = topLeft + Offset(s / 2, s / 2)
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(check.copy(alpha = 0.55f), check.copy(alpha = 0f)),
            center = centre,
            radius = 0.7f * s / 2,
        ),
        topLeft = topLeft,
        size = Size(s, s),
    )
}

/** A right triangle in the bottom-left corner. */
private fun DrawScope.drawLastMoveCorner(topLeft: Offset, s: Float, leg: Float, mark: Color) {
    val path = Path().apply {
        moveTo(topLeft.x, topLeft.y + s)
        lineTo(topLeft.x, topLeft.y + s - leg)
        lineTo(topLeft.x + leg, topLeft.y + s)
        close()
    }
    drawPath(path, mark)
}

/** An inset square border along the edge, with the halo on its inner side. */
private fun DrawScope.drawSelectedBorder(topLeft: Offset, metrics: BoardMetrics, colors: BoardColors) {
    val s = metrics.square
    val w = metrics.selectedStroke
    val h = metrics.halo
    drawRect(colors.cueInk, topLeft + Offset(w / 2, w / 2), Size(s - w, s - w), style = Stroke(w))
    drawRect(colors.cueHalo, topLeft + Offset(w + h / 2, w + h / 2), Size(s - 2 * w - h, s - 2 * w - h), style = Stroke(h))
}

/** A red disc in the top-right corner with a white "+" and the halo around it. */
private fun DrawScope.drawCheckBadge(topLeft: Offset, metrics: BoardMetrics, colors: BoardColors) {
    val s = metrics.square
    val r = metrics.checkBadge / 2
    val centre = topLeft + Offset(s - metrics.coordInset - r, metrics.coordInset + r)
    drawCircle(colors.cueHalo, r + metrics.halo, centre)
    drawCircle(colors.cueCheck, r, centre)
    val arm = r * 0.55f
    val stroke = metrics.checkGlyphStroke
    drawLine(colors.cueCheckGlyph, centre - Offset(arm, 0f), centre + Offset(arm, 0f), stroke, StrokeCap.Butt)
    drawLine(colors.cueCheckGlyph, centre - Offset(0f, arm), centre + Offset(0f, arm), stroke, StrokeCap.Butt)
}
