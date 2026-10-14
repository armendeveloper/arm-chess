package chess.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the device harness (T-2): the debug APK installs on `chess36` (or a real device),
 * the activity launches past the splash and Home shows the app name.
 */
@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun debugBuildIsUnderTest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("io.github.armendeveloper.armchess.debug", context.packageName)
    }

    @Test
    fun activityLaunchesAndShowsAppName() {
        assertEquals(Lifecycle.State.RESUMED, composeRule.activityRule.scenario.state)
        composeRule.onNodeWithText("Arm Chess").assertIsDisplayed()
    }
}
