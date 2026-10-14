package chess.app.ui.about

import android.content.res.AssetManager
import android.content.res.Resources
import android.util.Log
import androidx.annotation.RawRes
import chess.app.R
import java.io.FileNotFoundException
import java.io.InputStream
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** One licence of a library, as the licensee plugin reports it. [spdxId] is null for a licence it can't identify. */
data class LicenceInfo(val spdxId: String?, val name: String, val url: String?)

/** A third-party library on the app's runtime classpath (tech design §9.4). */
data class LibraryLicence(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val name: String?,
    val licences: List<LicenceInfo>,
) {
    val coordinates: String get() = "$groupId:$artifactId"

    /** The POM name, or the Maven coordinate when the POM has none. */
    val displayName: String get() = name?.takeUnless { it.isBlank() } ?: coordinates
}

/**
 * Reads the licence report that `app.cash.licensee` bundles into each variant's assets
 * (`bundleAndroidAsset = true` in app/build.gradle.kts). The report is generated from that variant's
 * runtime classpath, and the build fails before bundling if any licence is off the allow-list.
 */
object OpenSourceLicences {
    const val ASSET_PATH = "app/cash/licensee/artifacts.json"

    private const val TAG = "OpenSourceLicences"

    /** Null when the report is missing or unreadable; S7 then hides its "Open-source licences" item (design §4.9). */
    fun load(assets: AssetManager): List<LibraryLicence>? = read { assets.open(ASSET_PATH) }

    fun read(open: () -> InputStream): List<LibraryLicence>? =
        try {
            parse(open().bufferedReader().use { it.readText() })
        } catch (e: FileNotFoundException) {
            Log.w(TAG, "No licence report in this build: $ASSET_PATH", e)
            null
        } catch (e: JSONException) {
            Log.e(TAG, "Licence report $ASSET_PATH is malformed", e)
            null
        }

    /** Parses licensee's `artifacts.json`, sorted by display name for the list. */
    fun parse(json: String): List<LibraryLicence> {
        val array = JSONArray(json)
        return (0 until array.length())
            .map { parseLibrary(array.getJSONObject(it)) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, LibraryLicence::displayName).thenBy { it.coordinates })
    }

    /**
     * The full text bundled for an SPDX licence, or null. Only the allow-listed licences that the app
     * actually ships are here; a test fails if the report contains a licence without a text.
     */
    @RawRes
    fun textFor(spdxId: String?): Int? = when (spdxId) {
        "Apache-2.0" -> R.raw.licence_apache_2_0
        else -> null
    }

    private fun parseLibrary(json: JSONObject) = LibraryLicence(
        groupId = json.getString("groupId"),
        artifactId = json.getString("artifactId"),
        version = json.getString("version"),
        name = json.optStringOrNull("name"),
        licences = json.licences("spdxLicenses", spdx = true) + json.licences("unknownLicenses", spdx = false),
    )

    private fun JSONObject.licences(key: String, spdx: Boolean): List<LicenceInfo> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { index ->
            val licence = array.getJSONObject(index)
            val url = licence.optStringOrNull("url")
            LicenceInfo(
                spdxId = if (spdx) licence.getString("identifier") else null,
                name = licence.optStringOrNull("name") ?: url.orEmpty(),
                url = url,
            )
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null
}

/** A bundled licence text from `res/raw`, with line endings normalised (a Windows checkout may have CRLF). */
fun readRawText(resources: Resources, @RawRes id: Int): String =
    normaliseLicenceText(resources.openRawResource(id).bufferedReader().use { it.readText() })

internal fun normaliseLicenceText(text: String): String = text.replace("\r\n", "\n").trimEnd()
