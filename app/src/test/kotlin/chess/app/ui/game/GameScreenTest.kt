package chess.app.ui.game

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import chess.app.ai.FakeAiPlayer
import chess.app.ui.game.board.ChessBoardTestTag
import chess.app.ui.game.board.squareTestTag
import chess.app.ui.theme.ArmChessTheme
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.ZERO
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The first playable slice (plan T-15) through the real [GameRoute], `ChessBoard` and [StatusRow]:
 * touches on the board reach the ViewModel, and the computer's replies arrive after 400 ms.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
class GameScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var homeClicks = 0

    @Test
    fun tapE2ThenE4MakesTheMove() {
        show(FakeAiPlayer(thinkTime = ZERO))
        tap("e2")
        assertEquals("e2, white pawn, selected", description("e2"))
        assertEquals("e4, empty, move available", description("e4"))

        tap("e4")
        assertEquals("e4, white pawn, last move to", description("e4"))
        assertEquals("e2, empty, last move from", description("e2"))
        assertEquals("Computer is thinking…", status())
    }

    @Test
    fun playsAFewMovesAgainstTheComputerThroughTheRealBoard() {
        show(FakeAiPlayer(thinkTime = ZERO, script = listOf("e7e5", "b8c6", "g8f6")))
        assertEquals("Your move", status())

        play("e2", "e4")
        assertEquals("Computer is thinking…", status())
        computerTurn()
        assertEquals("e5, black pawn, last move to", description("e5"))
        assertEquals("Your move", status())

        play("g1", "f3")
        computerTurn()
        assertEquals("c6, black knight, last move to", description("c6"))

        play("f1", "c4")
        computerTurn()
        assertEquals("f6, black knight, last move to", description("f6"))
        assertEquals("c4, white bishop", description("c4"))
        assertEquals("f3, white knight", description("f3"))
        assertEquals("Your move", status())
    }

    @Test
    fun theComputersMoveWaitsForTheMinimumDelay() {
        show(FakeAiPlayer(thinkTime = ZERO, script = listOf("e7e5")))
        play("e2", "e4")
        idleMainLooper(300)
        assertEquals("e5, empty", description("e5"))
        assertEquals("Computer is thinking…", status())
        idleMainLooper(150)
        assertEquals("e5, black pawn, last move to", description("e5"))
    }

    @Test
    fun aTapWithNoLegalMovesShowsTheHint() {
        show(FakeAiPlayer(thinkTime = ZERO))
        tap("a1")
        assertEquals("No legal moves for that piece", status())
        assertEquals("a1, white rook", description("a1"))
    }

    @Test
    fun titleIsTheLevelAndHomeGoesBack() {
        show(FakeAiPlayer(thinkTime = ZERO))
        composeRule.onNode(hasText("Level 2 · Casual") and isHeading()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Home").performClick()
        composeRule.runOnIdle { assertEquals(1, homeClicks) }
    }

    @Test
    fun blackPlayerSeesTheBoardFromBlacksSideAndTheComputerOpens() {
        show(FakeAiPlayer(thinkTime = ZERO, script = listOf("d2d4")), player = Side.Black)
        val first = composeRule.onNodeWithTag(ChessBoardTestTag).onChildren()[0].fetchSemanticsNode()
        assertEquals("h1, white rook", first.config[SemanticsProperties.ContentDescription].single())
        assertEquals("Computer is thinking…", status())
        computerTurn()
        assertEquals("d4, white pawn, last move to", description("d4"))
        assertEquals("Your move", status())
        play("d7", "d5")
        assertEquals("d5, black pawn, last move to", description("d5"))
    }

    // --- helpers ---

    private fun show(ai: FakeAiPlayer, player: Side = Side.White) {
        val vm = GameViewModel(ai = ai, player = player)
        composeRule.setContent {
            ArmChessTheme { GameRoute(onHome = { homeClicks++ }, viewModel = vm) }
        }
        composeRule.waitForIdle()
    }

    private fun play(from: String, to: String) {
        tap(from)
        tap(to)
    }

    /** A real touch in the middle of the square, delivered to the board's single pointer handler. */
    private fun tap(square: String) {
        composeRule.onNodeWithTag(squareTestTag(Squares.parse(square))).performTouchInput { click() }
        composeRule.waitForIdle()
    }

    /** Lets the computer's minimum 400 ms pass on the main looper's clock. */
    private fun computerTurn() = idleMainLooper(450)

    private fun idleMainLooper(millis: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
        composeRule.waitForIdle()
    }

    private fun description(square: String): String =
        composeRule.onNodeWithTag(squareTestTag(Squares.parse(square))).fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].single()

    private fun status(): String =
        composeRule.onNodeWithTag(StatusRowTestTag).fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].single()
}
