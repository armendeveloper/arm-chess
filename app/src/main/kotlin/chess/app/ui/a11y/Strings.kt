package chess.app.ui.a11y

import android.content.res.Resources
import androidx.annotation.StringRes

/**
 * Read access to string resources for the pure a11y formatters (tech design §8.1), so they can be
 * built and tested without Compose. Every user-visible word comes from `strings.xml`.
 */
interface Strings {
    fun get(@StringRes id: Int, vararg args: Any): String
}

/** Production [Strings]: the app's resources in the current locale. */
class AndroidStrings(private val resources: Resources) : Strings {
    override fun get(@StringRes id: Int, vararg args: Any): String = resources.getString(id, *args)
}
