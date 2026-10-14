package chess.app.ui.game.board

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import chess.app.ui.a11y.AndroidStrings
import chess.app.ui.a11y.SquareDescriber
import chess.app.ui.game.BoardUi
import chess.app.ui.game.Side
import chess.app.ui.theme.ArmChessTheme
import chess.app.ui.theme.BoardMetrics

/**
 * The chess board (design §4.3.3, tech design §7): a square, read-only view of [board].
 *
 * - **Drawing.** One draw pass paints the design layers in order (see [boardMarks]): squares, check
 *   tint, coordinates, last-move corners, pieces, then the cues with their halos. The player's side
 *   is at the bottom.
 * - **Input.** One pointer handler for the whole board. While [BoardUi.interactive] is true, a tap
 *   reports the square under the finger to [onSquareClick]; the ViewModel decides what it means
 *   (T-15). T-25 replaces the tap detector with tap-or-drag handling.
 * - **Accessibility.** 64 transparent nodes sit over the board in reading order, inside one
 *   traversal group (design §5.3). Their click actions call [onSquareClick] too.
 *
 * The board takes the largest square that fits, rounded down to a whole number of pixels per
 * square so there are no hairline seams, and centres itself in any leftover space.
 */
@Composable
fun ChessBoard(
    board: BoardUi,
    onSquareClick: (square: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ArmChessTheme.boardColors
    val painters = piecePainters()
    val textMeasurer = rememberTextMeasurer()
    val resources = LocalResources.current
    val describer = remember(resources) { SquareDescriber(AndroidStrings(resources)) }
    val paint = remember(colors, painters, textMeasurer) {
        BoardPaint(colors, painters, textMeasurer, TextStyle(fontWeight = FontWeight.Medium))
    }
    val marks = remember(board) { boardMarks(board) }
    val order = remember(board.player) { visualOrder(board.player) }
    val currentBoard by rememberUpdatedState(board)
    val currentOnSquareClick by rememberUpdatedState(onSquareClick)

    Layout(
        content = {
            order.forEach { square ->
                key(square) {
                    Box(Modifier.squareSemantics(square, board, describer, onSquareClick))
                }
            }
        },
        modifier = modifier
            .boardContainerSemantics()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val shown = currentBoard
                    if (!shown.interactive) return@detectTapGestures
                    val frame = BoardFrame.of(size.width, size.height)
                    val square = squareAtOffset(
                        offset.x - frame.left,
                        offset.y - frame.top,
                        frame.squarePx.toFloat(),
                        shown.player,
                    )
                    if (square != null) currentOnSquareClick(square)
                }
            }
            .drawBehind {
                val frame = BoardFrame.of(size.width.toInt(), size.height.toInt())
                val metrics = BoardMetrics.of(frame.squarePx.toFloat(), density)
                translate(frame.left.toFloat(), frame.top.toFloat()) {
                    drawBoard(marks, board.player, metrics, paint)
                }
            },
    ) { measurables, constraints ->
        val side = boardSide(constraints, fallback = DEFAULT_BOARD.roundToPx())
        val width = constraints.constrainWidth(side)
        val height = constraints.constrainHeight(side)
        val frame = BoardFrame.of(width, height)
        val cell = Constraints.fixed(frame.squarePx, frame.squarePx)
        val placeables = measurables.map { it.measure(cell) }
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                placeable.place(frame.left + (index % 8) * frame.squarePx, frame.top + (index / 8) * frame.squarePx)
            }
        }
    }
}

/** Where the 8 × 8 grid sits inside the board's layout box: whole-pixel squares, centred. */
private data class BoardFrame(val squarePx: Int, val left: Int, val top: Int) {
    companion object {
        fun of(width: Int, height: Int): BoardFrame {
            val squarePx = minOf(width, height).coerceAtLeast(0) / 8
            return BoardFrame(squarePx, (width - 8 * squarePx) / 2, (height - 8 * squarePx) / 2)
        }
    }
}

/** The largest square side the constraints allow; [fallback] when both dimensions are unbounded. */
private fun boardSide(constraints: Constraints, fallback: Int): Int = when {
    constraints.hasBoundedWidth && constraints.hasBoundedHeight -> minOf(constraints.maxWidth, constraints.maxHeight)
    constraints.hasBoundedWidth -> constraints.maxWidth
    constraints.hasBoundedHeight -> constraints.maxHeight
    else -> fallback
}

/** Used only when the board is given unbounded space: 45 dp squares (design §4.1, 360 dp phone). */
private val DEFAULT_BOARD = 360.dp

@Preview(name = "Start, White", widthDp = 360, heightDp = 360)
@Composable
private fun ChessBoardStartPreview() {
    ArmChessTheme { ChessBoard(board = BoardFixtures.start(Side.White), onSquareClick = {}) }
}

@Preview(name = "Check, selected king", widthDp = 360, heightDp = 360)
@Composable
private fun ChessBoardCheckPreview() {
    ArmChessTheme { ChessBoard(board = BoardFixtures.kingInCheckSelected, onSquareClick = {}) }
}

@Preview(name = "All cues, Black, dark theme", widthDp = 360, heightDp = 360)
@Composable
private fun ChessBoardAllCuesPreview() {
    ArmChessTheme(darkTheme = true) {
        Surface {
            ChessBoard(board = BoardFixtures.allCuesAsBlack, onSquareClick = {})
        }
    }
}

@Preview(name = "Small squares (24 dp)", widthDp = 200, heightDp = 200)
@Composable
private fun ChessBoardSmallPreview() {
    ArmChessTheme {
        Column(Modifier.padding(4.dp)) {
            ChessBoard(board = BoardFixtures.allCuesAsBlack, onSquareClick = {}, modifier = Modifier.size(192.dp))
        }
    }
}
