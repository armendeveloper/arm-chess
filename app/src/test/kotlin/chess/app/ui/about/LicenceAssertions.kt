package chess.app.ui.about

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import kotlin.test.assertTrue

/**
 * Asserts that a licence text is shown in full: every paragraph, word for word, exactly once.
 * With [scrollTo], each paragraph is also scrolled into view and must be displayed.
 */
fun ComposeTestRule.assertLicenceTextShown(text: String, scrollTo: Boolean = false) {
    val paragraphs = licenceParagraphs(text)
    assertTrue(paragraphs.isNotEmpty())
    assertTrue(paragraphs.joinToString("\n\n") == normaliseLicenceText(text), "paragraph split loses text")
    paragraphs.forEach { paragraph ->
        val node = onNodeWithText(paragraph, useUnmergedTree = true).assertExists()
        if (scrollTo) node.performScrollTo().assertIsDisplayed()
    }
}
