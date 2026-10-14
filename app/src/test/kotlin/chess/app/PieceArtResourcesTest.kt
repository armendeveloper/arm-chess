package chess.app

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.VectorDrawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** T-13: the 12 Cburnett piece drawables and the adaptive launcher icon resolve and inflate. */
@RunWith(AndroidJUnit4::class)
class PieceArtResourcesTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val pieces = mapOf(
        "piece_wk" to R.drawable.piece_wk,
        "piece_wq" to R.drawable.piece_wq,
        "piece_wr" to R.drawable.piece_wr,
        "piece_wb" to R.drawable.piece_wb,
        "piece_wn" to R.drawable.piece_wn,
        "piece_wp" to R.drawable.piece_wp,
        "piece_bk" to R.drawable.piece_bk,
        "piece_bq" to R.drawable.piece_bq,
        "piece_br" to R.drawable.piece_br,
        "piece_bb" to R.drawable.piece_bb,
        "piece_bn" to R.drawable.piece_bn,
        "piece_bp" to R.drawable.piece_bp,
    )

    @Test
    fun allTwelvePieceDrawablesInflateAsVectorDrawablesWithNonZeroSize() {
        assertEquals(12, pieces.size)
        for ((name, id) in pieces) {
            assertEquals(name, context.resources.getResourceEntryName(id))
            val drawable = context.getDrawable(id)
            assertTrue("$name should be a VectorDrawable but was $drawable", drawable is VectorDrawable)
            assertTrue("$name intrinsic width", drawable!!.intrinsicWidth > 0)
            assertTrue("$name intrinsic height", drawable.intrinsicHeight > 0)
            assertEquals("$name should be square", drawable.intrinsicWidth, drawable.intrinsicHeight)
        }
    }

    @Test
    fun launcherIconsAreAdaptiveWithForegroundBackgroundAndMonochromeLayers() {
        for (id in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
            val name = context.resources.getResourceEntryName(id)
            val icon = context.getDrawable(id)
            assertTrue("$name should be an AdaptiveIconDrawable but was $icon", icon is AdaptiveIconDrawable)
            icon as AdaptiveIconDrawable
            assertNotNull("$name background", icon.background)
            assertTrue("$name foreground", icon.foreground is VectorDrawable)
            assertTrue("$name monochrome (themed icon)", icon.monochrome is VectorDrawable)
        }
    }

    @Test
    fun manifestDeclaresLauncherIcon() {
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertEquals(R.mipmap.ic_launcher, appInfo.icon)
    }
}
