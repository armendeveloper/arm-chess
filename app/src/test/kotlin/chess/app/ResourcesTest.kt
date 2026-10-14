package chess.app

import android.content.Context
import android.graphics.drawable.VectorDrawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The icon set (design §4.11) and the copy deck (design §4.10) are bundled. */
@RunWith(AndroidJUnit4::class)
class ResourcesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun materialSymbolIconsInflateAsVectors() {
        val icons = listOf(
            R.drawable.ic_arrow_back,
            R.drawable.ic_undo,
            R.drawable.ic_flag,
            R.drawable.ic_add,
            R.drawable.ic_format_list_numbered,
            R.drawable.ic_info,
            R.drawable.ic_error,
            R.drawable.ic_hourglass_empty,
            R.drawable.ic_crop_free,
        )
        icons.forEach { id ->
            assertIs<VectorDrawable>(context.getDrawable(id), context.resources.getResourceEntryName(id))
        }
    }

    @Test
    fun everyCopyDeckKeyIsAString() {
        val missing = COPY_DECK_KEYS.filter { key ->
            runCatching { R.string::class.java.getField(key) }.isFailure
        }
        assertTrue(missing.isEmpty(), "missing strings: $missing")
    }

    @Test
    fun copyUsesTheAgreedNameAndPunctuation() {
        assertEquals("Arm Chess", context.getString(R.string.app_name))
        assertEquals("Checking for a saved game…", context.getString(R.string.home_loading))
        assertEquals(
            "Level 2 · Casual · You play White",
            context.getString(R.string.home_in_progress_meta, 2, "Casual", "White"),
        )
        assertEquals("Couldn't restore your last game", context.getString(R.string.restore_failed_title))
    }

    @Test
    fun resultMetaIsAPlural() {
        val res = context.resources
        assertEquals(
            "Level 1 · Beginner · 1 move",
            res.getQuantityString(R.plurals.result_meta, 1, 1, "Beginner", 1),
        )
        assertEquals(
            "Level 4 · Strong · 42 moves",
            res.getQuantityString(R.plurals.result_meta, 42, 4, "Strong", 42),
        )
    }

    private companion object {
        val COPY_DECK_KEYS = listOf(
            "app_name", "home_tagline", "home_in_progress_title", "home_in_progress_meta",
            "home_turn_player", "home_turn_computer", "home_continue", "home_new_game", "home_stats",
            "home_about", "home_loading", "restore_failed_title", "restore_failed_body", "action_ok",
            "action_cancel", "new_game_title", "new_game_side", "side_white", "side_black", "side_random",
            "side_random_hint", "new_game_level", "level_name_1", "level_name_2", "level_name_3",
            "level_name_4", "level_desc_1", "level_desc_2", "level_desc_3", "level_desc_4", "level_label",
            "level_option", "new_game_play", "abandon_title", "abandon_body", "abandon_confirm", "game_home",
            "status_your_move", "status_check", "status_thinking", "status_thinking_check",
            "status_no_moves", "status_engine_error", "action_try_again", "status_loading",
            "you_play_white", "you_play_black", "storage_full", "window_too_small", "action_undo",
            "action_resign", "action_new_game", "action_moves", "resign_title", "resign_body",
            "resign_confirm", "moves_title", "moves_empty", "promo_title", "piece_queen", "piece_rook",
            "piece_bishop", "piece_knight", "result_mate_win", "result_mate_loss", "result_stalemate",
            "result_threefold", "result_fifty", "result_insufficient", "result_resigned",
            "result_expl_mate_win", "result_expl_mate_loss", "result_expl_stalemate_computer",
            "result_expl_stalemate_player", "result_expl_threefold", "result_expl_fifty",
            "result_expl_insufficient", "result_home", "stats_title", "stats_games", "stats_started",
            "stats_completed", "stats_abandoned", "stats_started_note", "stats_by_level", "stats_col_level",
            "stats_col_won", "stats_col_drawn", "stats_col_lost", "stats_empty_hint", "stats_reset",
            "stats_reset_title", "stats_reset_body", "stats_reset_confirm", "stats_reset_done",
            "stats_read_error", "stats_private", "about_title", "about_version", "about_privacy_heading",
            "about_privacy_body", "about_credits_heading", "about_pieces", "about_view_licence", "about_oss",
        )
    }
}
