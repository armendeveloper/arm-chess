package chess.app.ui.game.board

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import chess.app.ui.game.BoardUi
import chess.app.ui.game.LastMoveUi
import chess.app.ui.game.Side
import chess.app.ui.game.Squares
import chess.app.ui.theme.ArmChessTheme
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The 64-node accessibility overlay (design §5.3, tech design §7 and §8): reading order for both
 * orientations, descriptions from the string resources, click labels only where a tap does
 * something, and taps (touch or accessibility) reporting the right square.
 */
@RunWith(AndroidJUnit4::class)
class ChessBoardSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val clicks = mutableListOf<Int>()

    @Test
    fun sixtyFourSquaresInWhitesReadingOrder() {
        show(startBoard(Side.White))
        val squares = squareNodes()
        assertEquals(64, squares.size)
        assertEquals("a8, black rook", squares.first().description)
        assertEquals("h1, white rook", squares.last().description)
        assertEquals(visualOrder(Side.White).map(Squares::name), squares.map { it.description.substringBefore(',') })
        assertRowMajorOnScreen(squares)
    }

    @Test
    fun sixtyFourSquaresInBlacksReadingOrder() {
        show(startBoard(Side.Black))
        val squares = squareNodes()
        assertEquals(64, squares.size)
        assertEquals("h1, white rook", squares.first().description)
        assertEquals("a8, black rook", squares.last().description)
        assertEquals(visualOrder(Side.Black).map(Squares::name), squares.map { it.description.substringBefore(',') })
        assertRowMajorOnScreen(squares)
    }

    @Test
    fun boardIsOneTraversalGroupWithoutCollectionInfo() {
        show(startBoard(Side.White))
        val board = composeRule.onNodeWithTag(ChessBoardTestTag).fetchSemanticsNode()
        assertEquals(true, board.config.getOrNull(SemanticsProperties.IsTraversalGroup))
        assertNull(board.config.getOrNull(SemanticsProperties.CollectionInfo))
        squareNodes().forEach { assertNull(it.config.getOrNull(SemanticsProperties.CollectionItemInfo)) }
    }

    @Test
    fun selectedKingInCheckIsDescribedFromTheFixture() {
        show(BoardFixtures.kingInCheckSelected)
        assertEquals("e1, white king, selected, in check", node("e1").description)
        assertEquals("d1, empty, move available", node("d1").description)
        assertEquals("f2, black queen, capture available, last move to", node("f2").description)
        assertEquals("d4, empty, last move from", node("d4").description)
        assertEquals("e2, white pawn", node("e2").description)
        assertEquals("h8, empty", node("h8").description)
    }

    @Test
    fun clickActionsOnlyWhereATapDoesSomething() {
        show(BoardFixtures.kingInCheckSelected)
        assertEquals("deselect", node("e1").clickLabel)
        assertEquals("move here", node("d1").clickLabel)
        assertEquals("capture", node("f2").clickLabel)
        assertEquals("select", node("e2").clickLabel) // own piece, even without legal moves
        assertEquals("select", node("a1").clickLabel)

        // Read-only: enemy pieces, empty non-targets, last-move squares that aren't targets.
        assertFalse(node("e8").hasClick)
        assertFalse(node("h8").hasClick)
        assertFalse(node("d4").hasClick)
        assertFalse(node("e4").hasClick)
    }

    @Test
    fun notThePlayersTurnMakesEverySquareReadOnly() {
        show(BoardFixtures.kingInCheckSelected.copy(interactive = false))
        val squares = squareNodes()
        assertEquals(64, squares.size)
        assertTrue(squares.none { it.hasClick })
        // The descriptions stay: the player can still explore the board.
        assertEquals("e1, white king, selected, in check", node("e1").description)
    }

    @Test
    fun accessibilityClickReportsTheSquare() {
        show(BoardFixtures.kingInCheckSelected)
        composeRule.onNodeWithTag(squareTestTag(sq("d1"))).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag(squareTestTag(sq("e2"))).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(listOf(sq("d1"), sq("e2")), clicks) }
    }

    @Test
    fun touchReportsTheSquareUnderTheFingerAsWhite() {
        show(startBoard(Side.White).copy(interactive = true))
        tapCell(row = 6, col = 4) // e2
        tapCell(row = 0, col = 0) // a8: enemy piece; the ViewModel decides what a tap there means
        tapCell(row = 4, col = 3) // d4, empty
        composeRule.runOnIdle { assertEquals(listOf(sq("e2"), sq("a8"), sq("d4")), clicks) }
    }

    @Test
    fun touchReportsTheSquareUnderTheFingerAsBlack() {
        show(startBoard(Side.Black).copy(interactive = true))
        tapCell(row = 6, col = 4) // d7
        tapCell(row = 0, col = 0) // h1
        composeRule.runOnIdle { assertEquals(listOf(sq("d7"), sq("h1")), clicks) }
    }

    @Test
    fun touchIsIgnoredWhenTheBoardIsNotInteractive() {
        show(startBoard(Side.White))
        tapCell(row = 6, col = 4)
        composeRule.runOnIdle { assertTrue(clicks.isEmpty()) }
    }

    @Test
    fun squareNodesFollowTheBoardWhenItChanges() {
        var board by mutableStateOf(startBoard(Side.White))
        composeRule.setContent {
            ArmChessTheme {
                ChessBoard(board = board, onSquareClick = { clicks += it }, modifier = Modifier.size(320.dp))
            }
        }
        assertEquals("e5, empty", node("e5").description)
        board = board.copy(
            pieces = board.pieces.toMutableList().apply {
                this[sq("e5")] = this[sq("e7")]
                this[sq("e7")] = null
            },
            lastMove = LastMoveUi(sq("e7"), sq("e5")),
        )
        composeRule.waitForIdle()
        assertEquals("e5, black pawn, last move to", node("e5").description)
        assertEquals("e7, empty, last move from", node("e7").description)
    }

    // --- helpers ---

    private fun show(board: BoardUi) {
        composeRule.setContent {
            ArmChessTheme {
                ChessBoard(board = board, onSquareClick = { clicks += it }, modifier = Modifier.size(320.dp))
            }
        }
    }

    private fun squareNodes(): List<SemanticsNode> =
        composeRule.onNodeWithTag(ChessBoardTestTag).onChildren().fetchSemanticsNodes()

    private fun node(name: String): SemanticsNode =
        composeRule.onNodeWithTag(squareTestTag(sq(name))).fetchSemanticsNode()

    private fun tapCell(row: Int, col: Int) {
        composeRule.onNodeWithTag(ChessBoardTestTag).performTouchInput {
            val s = width / 8f
            click(Offset((col + 0.5f) * s, (row + 0.5f) * s))
        }
    }

    /** Tree order is the reading order, and it matches the on-screen grid: row by row, left to right. */
    private fun assertRowMajorOnScreen(nodes: List<SemanticsNode>) {
        val origin = nodes.first().boundsInRoot.topLeft
        val size = nodes.first().boundsInRoot.width
        assertTrue(size > 0f)
        nodes.forEachIndexed { index, node ->
            val expected = Offset(origin.x + (index % 8) * size, origin.y + (index / 8) * size)
            assertEquals(expected.x, node.boundsInRoot.left, 0.5f, "x of node $index")
            assertEquals(expected.y, node.boundsInRoot.top, 0.5f, "y of node $index")
        }
    }

    private val SemanticsNode.description: String
        get() = config[SemanticsProperties.ContentDescription].single()

    private val SemanticsNode.hasClick: Boolean
        get() = config.getOrNull(SemanticsActions.OnClick) != null

    private val SemanticsNode.clickLabel: String?
        get() = config.getOrNull(SemanticsActions.OnClick)?.label

    private fun sq(name: String) = Squares.parse(name)

    private fun startBoard(player: Side) =
        BoardUi(pieces = BoardFixtures.pieces(BoardFixtures.START_PLACEMENT), player = player)
}
