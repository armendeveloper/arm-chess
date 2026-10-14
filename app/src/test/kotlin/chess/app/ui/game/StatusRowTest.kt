package chess.app.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import chess.app.ui.theme.ArmChessTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The status row (design §4.3.4): every state's text comes from `strings.xml` (scope change: hy/ru),
 * and the row is the screen's polite live region (tech design §8.2) whose description is that text.
 */
@RunWith(AndroidJUnit4::class)
class StatusRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun everyStateShowsItsCopyDeckText() {
        val expected = mapOf(
            StatusUi.YourMove to "Your move",
            StatusUi.Check to "Check! Your move",
            StatusUi.Thinking(check = false) to "Computer is thinking…",
            StatusUi.Thinking(check = true) to "Check! Computer is thinking…",
            StatusUi.NoLegalMoves to "No legal moves for that piece",
            StatusUi.EngineError to "The computer couldn't make a move.",
            StatusUi.GameOver(GameEnd.CheckmateWin) to "Checkmate. You win",
            StatusUi.GameOver(GameEnd.CheckmateLoss) to "Checkmate. Computer wins",
            StatusUi.GameOver(GameEnd.Stalemate) to "Draw by stalemate",
            StatusUi.GameOver(GameEnd.ThreefoldRepetition) to "Draw by threefold repetition",
            StatusUi.GameOver(GameEnd.FiftyMoveRule) to "Draw by 50-move rule",
            StatusUi.GameOver(GameEnd.InsufficientMaterial) to "Draw by insufficient material",
        )
        var status: StatusUi by mutableStateOf(StatusUi.YourMove)
        composeRule.setContent {
            ArmChessTheme { StatusRow(status = status, player = Side.White, onRetry = {}) }
        }
        expected.forEach { (state, text) ->
            status = state
            composeRule.waitForIdle()
            assertEquals(text, spoken(), "spoken text for $state")
            composeRule.onNodeWithText(text).assertIsDisplayed()
        }
        // Every GameEnd has a title.
        assertEquals(GameEnd.entries.size, expected.keys.count { it is StatusUi.GameOver })
    }

    @Test
    fun isAPoliteLiveRegion() {
        composeRule.setContent {
            ArmChessTheme { StatusRow(status = StatusUi.YourMove, player = Side.Black, onRetry = {}) }
        }
        val node = composeRule.onNodeWithTag(StatusRowTestTag).fetchSemanticsNode()
        assertEquals(LiveRegionMode.Polite, node.config.getOrNull(SemanticsProperties.LiveRegion))
    }

    @Test
    fun engineErrorOffersTryAgain() {
        var retries = 0
        composeRule.setContent {
            ArmChessTheme { StatusRow(status = StatusUi.EngineError, player = Side.White, onRetry = { retries++ }) }
        }
        composeRule.onNodeWithText("Try again").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun tryAgainIsOnlyShownForAnEngineError() {
        composeRule.setContent {
            ArmChessTheme { StatusRow(status = StatusUi.Thinking(check = false), player = Side.White, onRetry = {}) }
        }
        composeRule.onNodeWithText("Try again").assertDoesNotExist()
    }

    private fun spoken(): String =
        composeRule.onNodeWithTag(StatusRowTestTag).fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].single()
}
