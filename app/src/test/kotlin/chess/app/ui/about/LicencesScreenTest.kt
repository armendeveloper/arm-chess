package chess.app.ui.about

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import chess.app.R
import chess.app.ui.theme.ArmChessTheme
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** T-26: S7b Licences (design §4.9): every bundled library, each opening its text, then the Cburnett notice. */
@RunWith(AndroidJUnit4::class)
class LicencesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun listsEveryLibraryInTheBundledReport() {
        val libraries = assertNotNull(OpenSourceLicences.load(context.assets))
        setLicences()

        composeRule.onNode(hasText("Libraries") and isHeading()).assertIsDisplayed()
        libraries.forEach { library ->
            // onNodeWithText also asserts that each library has exactly one row.
            composeRule.onNodeWithText(detail(library)).assertExists().assertHasClickAction()
        }
    }

    @Test
    fun listsMaterial3() {
        val libraries = assertNotNull(OpenSourceLicences.load(context.assets))
        val material3 = libraries.single { it.coordinates == "androidx.compose.material3:material3" }
        setLicences()
        // One row: name, coordinates and version, and the licence name.
        composeRule.onNode(
            hasText("Compose Material3 Components") and hasText(detail(material3)) and hasText("Apache License 2.0"),
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tappingALibraryShowsItsFullLicenceText() {
        val libraries = assertNotNull(OpenSourceLicences.load(context.assets))
        val material3 = libraries.single { it.coordinates == "androidx.compose.material3:material3" }
        setLicences()

        composeRule.onNodeWithText(detail(material3)).performScrollTo().performClick()

        composeRule.onNode(isDialog()).assertIsDisplayed()
        composeRule.onNodeWithText("Licence: Apache License 2.0").assertExists()
        composeRule.assertLicenceTextShown(raw(R.raw.licence_apache_2_0))

        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun showsTheFullCburnettBsdNotice() {
        setLicences()
        composeRule.onNode(hasText("Chess pieces") and isHeading()).performScrollTo().assertIsDisplayed()
        composeRule.assertLicenceTextShown(raw(R.raw.cburnett_bsd), scrollTo = true)
    }

    @Test
    fun theBundledNoticeIsTheOneRecordedInTheRepository() {
        // The licence record is the source of truth for the notice (docs/licences/pieces-cburnett.md).
        val record = File("../docs/licences/pieces-cburnett.md").readText().replace("\r\n", "\n")
        val block = record.substringAfter("## Licence text (BSD 3-Clause)").substringAfter("```\n").substringBefore("\n```")
        assertEquals(block, raw(R.raw.cburnett_bsd))
        listOf(
            "Copyright © Colin M.L. Burnett (Cburnett)",
            "1. Redistributions of source code must retain",
            "2. Redistributions in binary form must reproduce",
            "3. Neither the name of Colin M.L. Burnett",
            "THIS SOFTWARE IS PROVIDED BY COLIN M.L. BURNETT \"AS IS\"",
        ).forEach { assertTrue(it in block, "notice is missing \"$it\"") }
    }

    @Test
    fun theLibraryListIsHiddenWhenTheReportIsMissing() {
        setLicences(libraries = null)
        composeRule.onNodeWithText("Libraries").assertDoesNotExist()
        composeRule.assertLicenceTextShown(raw(R.raw.cburnett_bsd))
    }

    private fun setLicences(libraries: List<LibraryLicence>? = OpenSourceLicences.load(context.assets)) {
        composeRule.setContent {
            ArmChessTheme { LicencesScreen(onBack = {}, libraries = libraries) }
        }
    }

    private fun detail(library: LibraryLicence): String =
        context.getString(R.string.licences_library_detail, library.coordinates, library.version)

    private fun raw(id: Int): String = readRawText(context.resources, id)
}
