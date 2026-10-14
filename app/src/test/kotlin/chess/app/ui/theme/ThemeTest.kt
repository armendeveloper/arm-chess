package chess.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Theme from seed #6B4E37 (design §6.1): follows the system dark setting, no dynamic colour. */
@RunWith(AndroidJUnit4::class)
class ThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lightThemeUsesTheWalnutLightScheme() {
        val scheme = schemeFor(darkTheme = false)
        assertEquals(Color(0xFF88511E), scheme.primary)
        assertEquals(Color(0xFFFFF8F5), scheme.surface)
        assertEquals(Color(0xFF221A14), scheme.onSurface)
        assertEquals(Color(0xFFFFDAD6), scheme.errorContainer)
    }

    @Test
    fun darkThemeUsesTheWalnutDarkScheme() {
        val scheme = schemeFor(darkTheme = true)
        assertEquals(Color(0xFFFFB77D), scheme.primary)
        assertEquals(Color(0xFF19120C), scheme.surface)
        assertEquals(Color(0xFFEFE0D6), scheme.onSurface)
        assertEquals(Color(0xFF93000A), scheme.errorContainer)
    }

    @Test
    @Config(qualifiers = "notnight")
    fun followsSystemLightMode() {
        assertEquals(LightColorScheme, schemeFor(darkTheme = null))
    }

    @Test
    @Config(qualifiers = "night")
    fun followsSystemDarkMode() {
        assertEquals(DarkColorScheme, schemeFor(darkTheme = null))
    }

    @Test
    fun boardColorsDoNotChangeWithTheTheme() {
        var lightBoard: BoardColors? = null
        var darkBoard: BoardColors? = null
        composeRule.setContent {
            ArmChessTheme(darkTheme = false) { lightBoard = ArmChessTheme.boardColors }
            ArmChessTheme(darkTheme = true) { darkBoard = ArmChessTheme.boardColors }
        }
        composeRule.runOnIdle {
            assertEquals(ArmChessBoardColors, lightBoard)
            assertEquals(ArmChessBoardColors, darkBoard)
        }
    }

    /** [darkTheme] null means "use the theme's default", which follows the system. */
    private fun schemeFor(darkTheme: Boolean?): ColorScheme {
        var scheme: ColorScheme? = null
        composeRule.setContent {
            if (darkTheme == null) {
                ArmChessTheme { scheme = MaterialTheme.colorScheme }
            } else {
                ArmChessTheme(darkTheme = darkTheme) { scheme = MaterialTheme.colorScheme }
            }
        }
        composeRule.waitForIdle()
        return checkNotNull(scheme)
    }
}
