package chess.app.nav

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import chess.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Navigation between the placeholder screens (design §3.1, §3.2). */
@RunWith(AndroidJUnit4::class)
class ChessNavHostTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeShowsTitleAndTheThreeEntryPoints() {
        composeRule.onNode(hasText("Arm Chess") and isHeading()).assertIsDisplayed()
        composeRule.onNodeWithText("Play the computer. Offline, no account, no ads.").assertIsDisplayed()
        composeRule.onNodeWithText("New game").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("Stats").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("About").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun statsOpensFromHomeAndSystemBackReturnsHome() {
        composeRule.onNodeWithText("Stats").performClick()
        assertOnStats()

        pressSystemBack()
        assertOnHome()
    }

    @Test
    fun statsBackButtonReturnsHome() {
        composeRule.onNodeWithText("Stats").performClick()
        assertOnStats()

        composeRule.onNodeWithContentDescription("Back").performClick()
        assertOnHome()
    }

    @Test
    fun placeholderGameIsReachableAndSystemBackReturnsHome() {
        composeRule.onNodeWithText("New game").performClick()
        assertOnGame()

        pressSystemBack()
        assertOnHome()
    }

    @Test
    fun gameHomeButtonReturnsHome() {
        composeRule.onNodeWithText("New game").performClick()
        assertOnGame()

        composeRule.onNodeWithContentDescription("Home").performClick()
        assertOnHome()
    }

    @Test
    fun aboutAndLicencesGoBackOneLevelAtATime() {
        composeRule.onNodeWithText("About").performClick()
        assertOnAbout()

        composeRule.onNodeWithText("Open-source licences").performScrollTo().performClick()
        composeRule.onNode(hasText("Open-source licences") and isHeading()).assertIsDisplayed()
        composeRule.onNodeWithText("Privacy").assertDoesNotExist()

        pressSystemBack()
        assertOnAbout()

        composeRule.onNodeWithContentDescription("Back").performClick()
        assertOnHome()
    }

    private fun assertOnHome() {
        composeRule.onNodeWithText("Play the computer. Offline, no account, no ads.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Home").assertDoesNotExist()
    }

    private fun assertOnStats() {
        composeRule.onNode(hasText("Stats") and isHeading()).assertIsDisplayed()
        composeRule.onNodeWithText("Stats are stored only on this device.").assertIsDisplayed()
        composeRule.onNodeWithText("New game").assertDoesNotExist()
    }

    private fun assertOnGame() {
        composeRule.onNodeWithContentDescription("Home").assertIsDisplayed()
        composeRule.onNodeWithText("New game").assertDoesNotExist()
    }

    private fun assertOnAbout() {
        composeRule.onNode(hasText("About") and isHeading()).assertIsDisplayed()
        composeRule.onNodeWithText("Privacy").assertIsDisplayed()
        composeRule.onNodeWithText("New game").assertDoesNotExist()
    }

    private fun pressSystemBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }
}
