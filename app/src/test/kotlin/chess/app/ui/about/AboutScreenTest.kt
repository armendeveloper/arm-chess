package chess.app.ui.about

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import chess.app.BuildConfig
import chess.app.R
import chess.app.ui.theme.ArmChessTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** T-26: S7 About (design §4.9): version, privacy promise, artwork credit and licence entry points. */
@RunWith(AndroidJUnit4::class)
class AboutScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun showsTheAppNameAndTheVersionNameAndCodeFromBuildConfig() {
        setAbout()
        composeRule.onNodeWithText("Arm Chess").assertIsDisplayed()
        composeRule.onNodeWithText("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            .assertIsDisplayed()
    }

    @Test
    fun versionLineMatchesTheDesign() {
        assertEquals("Version 1.0.0 (1)", context.getString(R.string.about_version, "1.0.0", 1))
    }

    @Test
    fun showsThePrivacyPromiseUnderAHeading() {
        setAbout()
        composeRule.onNode(hasText("Privacy") and isHeading()).assertIsDisplayed()
        composeRule.onNodeWithText(
            "This app works fully offline. It has no internet access and collects no data. " +
                "Your games and stats stay on this device.",
        ).assertIsDisplayed()
    }

    @Test
    fun showsTheCburnettAttributionUnderCredits() {
        setAbout()
        composeRule.onNode(hasText("Credits") and isHeading()).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "Chess pieces by Colin M.L. Burnett (Cburnett), used under the BSD 3-clause licence.",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun viewLicenceShowsTheFullBsdNoticeAndOkClosesIt() {
        setAbout()
        composeRule.onNodeWithText("View licence").performScrollTo().performClick()

        composeRule.onNode(isDialog()).assertIsDisplayed()
        composeRule.assertLicenceTextShown(cburnettNotice())

        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun openSourceLicencesOpensTheLicencesScreen() {
        var opened = 0
        setAbout(onLicences = { opened++ })
        composeRule.onNodeWithText("Open-source licences").performScrollTo().assertHasClickAction().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun openSourceLicencesIsHiddenWhenTheLicenceListIsMissing() {
        setAbout(licencesAvailable = false)
        composeRule.onNodeWithText("Open-source licences").assertDoesNotExist()
        // The artwork notice is still reachable.
        composeRule.onNodeWithText("View licence").assertExists()
    }

    @Test
    fun openSourceLicencesLinesUpWithTheColumnText() {
        setAbout()
        val credits = composeRule.onNodeWithText("Credits").getUnclippedBoundsInRoot()
        val row = composeRule.onNodeWithText("Open-source licences", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertEquals(credits.left, row.left)
    }

    private fun setAbout(onLicences: () -> Unit = {}, licencesAvailable: Boolean = true) {
        composeRule.setContent {
            ArmChessTheme {
                AboutScreen(onBack = {}, onLicences = onLicences, licencesAvailable = licencesAvailable)
            }
        }
    }

    private fun cburnettNotice(): String = readRawText(context.resources, R.raw.cburnett_bsd)
}
