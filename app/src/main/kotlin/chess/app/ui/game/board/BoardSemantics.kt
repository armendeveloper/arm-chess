package chess.app.ui.game.board

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import chess.app.ui.a11y.SquareDescriber
import chess.app.ui.game.BoardUi
import chess.app.ui.game.Squares

/** Test tag of the board container; its semantics children are the 64 squares in reading order. */
const val ChessBoardTestTag = "chess_board"

/** Test tag of one square's semantics node, e.g. "square_e4". */
fun squareTestTag(square: Int): String = "square_" + Squares.name(square)

/**
 * The board container's semantics (design §5.3): one traversal group, so TalkBack reads the 64
 * squares together. `collectionInfo` is deliberately not set: TalkBack's "row 1, column 1" would
 * contradict the rank numbers.
 */
internal fun Modifier.boardContainerSemantics(): Modifier = semantics {
    isTraversalGroup = true
    testTag = ChessBoardTestTag
}

/**
 * One square's node in the accessibility overlay (tech design §7). It replaces all semantics with
 * the [SquareDescriber] text, and adds a labelled click action only when a tap would do something;
 * every other square is read-only. The node has no pointer input, so touches go to the board's
 * single gesture handler. T-24 adds the custom move actions and keyboard focus here.
 */
internal fun Modifier.squareSemantics(
    square: Int,
    board: BoardUi,
    describer: SquareDescriber,
    onSquareClick: (Int) -> Unit,
): Modifier {
    val cell = board.cell(square)
    val description = describer.describe(cell)
    val clickLabel = describer.clickLabel(cell, board.player, board.interactive)
    return clearAndSetSemantics {
        testTag = squareTestTag(square)
        contentDescription = description
        if (clickLabel != null) {
            onClick(label = clickLabel) {
                onSquareClick(square)
                true
            }
        }
    }
}
