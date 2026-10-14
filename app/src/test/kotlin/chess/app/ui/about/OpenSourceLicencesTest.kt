package chess.app.ui.about

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import chess.app.R
import java.io.FileNotFoundException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T-26: the licence report that the licensee plugin bundles (tech design §9.4) is parsed for S7b,
 * and everything in it is on the allow-list and has its licence text inside the app.
 */
@RunWith(AndroidJUnit4::class)
class OpenSourceLicencesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun parsesSpdxAndUnknownLicencesAndSortsByName() {
        val libraries = OpenSourceLicences.parse(
            """
            [
              {
                "groupId": "org.example",
                "artifactId": "zeta",
                "version": "2.0",
                "name": "Zeta",
                "spdxLicenses": [
                  { "identifier": "Apache-2.0", "name": "Apache License 2.0", "url": "https://www.apache.org/licenses/LICENSE-2.0" }
                ],
                "scm": { "url": "https://example.org/zeta" }
              },
              {
                "groupId": "org.example",
                "artifactId": "alpha",
                "version": "1.0",
                "unknownLicenses": [
                  { "name": "Custom", "url": "https://example.org/licence" }
                ]
              }
            ]
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                LibraryLicence(
                    groupId = "org.example",
                    artifactId = "alpha",
                    version = "1.0",
                    name = null,
                    licences = listOf(LicenceInfo(spdxId = null, name = "Custom", url = "https://example.org/licence")),
                ),
                LibraryLicence(
                    groupId = "org.example",
                    artifactId = "zeta",
                    version = "2.0",
                    name = "Zeta",
                    licences = listOf(
                        LicenceInfo(
                            spdxId = "Apache-2.0",
                            name = "Apache License 2.0",
                            url = "https://www.apache.org/licenses/LICENSE-2.0",
                        ),
                    ),
                ),
            ),
            libraries,
        )
        assertEquals("org.example:alpha", libraries[0].displayName)
        assertEquals("Zeta", libraries[1].displayName)
        assertEquals("org.example:zeta", libraries[1].coordinates)
    }

    @Test
    fun aMissingReportReadsAsNull() {
        assertNull(OpenSourceLicences.read { throw FileNotFoundException(OpenSourceLicences.ASSET_PATH) })
    }

    @Test
    fun aMalformedReportReadsAsNull() {
        assertNull(OpenSourceLicences.read { "{ not a list".byteInputStream() })
    }

    @Test
    fun theReportIsBundledAndListsTheRuntimeLibraries() {
        val libraries = assertNotNull(OpenSourceLicences.load(context.assets), "licensee asset missing")
        val coordinates = libraries.map { it.coordinates }.toSet()

        // A sample of the runtime classpath (app/build.gradle.kts): direct and transitive.
        listOf(
            "androidx.compose.material3:material3",
            "androidx.activity:activity-compose",
            "androidx.navigation:navigation-compose",
            "androidx.core:core-splashscreen",
            "org.jetbrains.kotlin:kotlin-stdlib",
            "org.jetbrains.kotlinx:kotlinx-coroutines-android",
        ).forEach { assertTrue(it in coordinates, "$it missing from the bundled report") }
        // First-party modules are not third-party libraries.
        assertTrue(libraries.none { it.artifactId == "engine" })
    }

    @Test
    fun everyBundledLibraryIsOnTheAllowListAndHasItsLicenceTextInTheApp() {
        val libraries = assertNotNull(OpenSourceLicences.load(context.assets))
        libraries.forEach { library ->
            assertTrue(library.licences.isNotEmpty(), "${library.coordinates} has no licence")
            library.licences.forEach { licence ->
                assertTrue(licence.spdxId in ALLOW_LIST, "${library.coordinates}: ${licence.name} is not allowed")
                assertNotNull(
                    OpenSourceLicences.textFor(licence.spdxId),
                    "${library.coordinates}: no bundled text for ${licence.spdxId}",
                )
            }
        }
    }

    @Test
    fun theApacheTextIsTheFullLicence() {
        val text = context.resources.openRawResource(R.raw.licence_apache_2_0).bufferedReader().use { it.readText() }
        assertTrue(text.startsWith("Apache License\nVersion 2.0, January 2004\nhttp://www.apache.org/licenses/\n"))
        (1..9).forEach { section -> assertTrue("\n\n$section. " in text, "section $section missing") }
        assertTrue(text.trimEnd().endsWith("END OF TERMS AND CONDITIONS"))
        assertEquals(R.raw.licence_apache_2_0, OpenSourceLicences.textFor("Apache-2.0"))
        assertNull(OpenSourceLicences.textFor("GPL-2.0-only"))
        assertNull(OpenSourceLicences.textFor(null))
    }

    @Test
    fun licenceTextsAreShownWithUnixLineEndingsAndNoTrailingBlankLines() {
        assertEquals("Line one\n\nLine two", normaliseLicenceText("Line one\r\n\r\nLine two\r\n\r\n"))
    }

    private companion object {
        /** Mirrors `licensee { allow(...) }` in app/build.gradle.kts. */
        val ALLOW_LIST = setOf("Apache-2.0", "MIT", "BSD-3-Clause")
    }
}
