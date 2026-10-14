package chess.app

import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChessApplicationTest {

    private val app: ChessApplication = ApplicationProvider.getApplicationContext()

    @Test
    fun applicationOwnsOneContainerForTheProcess() {
        assertSame(app.container, app.container)
        assertTrue(app.container.appScope.isActive)
    }

    @Test
    fun containerUsesTheStandardDispatchers() {
        val dispatchers = app.container.dispatchers
        assertSame(Dispatchers.Main, dispatchers.main)
        assertSame(Dispatchers.Default, dispatchers.default)
        assertSame(Dispatchers.IO, dispatchers.io)
    }

    @Test
    fun backupIsDisabled() {
        // Design §9.2 / decision H-5: games and stats stay on this device.
        assertEquals(0, app.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
}
