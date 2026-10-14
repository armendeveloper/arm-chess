package chess.app.ui.about

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import chess.app.R
import chess.app.ui.components.SubScreen
import chess.app.ui.theme.ArmChessTheme

/**
 * S7b Licences (design §4.9): every third-party library in the build's licence report, each opening
 * its licence text, then the full Cburnett BSD notice for the piece art. With no report ([libraries]
 * null or empty) the library section is left out and only the notice is shown.
 */
@Composable
fun LicencesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    libraries: List<LibraryLicence>? = rememberBundledLibraries(),
) {
    // Saved by coordinate so the open dialog survives rotation and process death.
    var openCoordinates by rememberSaveable { mutableStateOf<String?>(null) }

    SubScreen(title = stringResource(R.string.about_oss), onBack = onBack, modifier = modifier) {
        if (!libraries.isNullOrEmpty()) {
            SectionHeading(stringResource(R.string.licences_libraries_heading))
            libraries.forEach { library ->
                LinkRow(
                    headline = library.displayName,
                    supporting = listOf(
                        stringResource(R.string.licences_library_detail, library.coordinates, library.version),
                        library.licences.joinToString { it.name },
                    ),
                    onClickLabel = stringResource(R.string.about_view_licence),
                    onClick = { openCoordinates = library.coordinates },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        SectionHeading(stringResource(R.string.licences_pieces_heading))
        Text(
            text = stringResource(R.string.about_pieces),
            style = MaterialTheme.typography.bodyMedium,
        )
        LicenceText(rememberLicenceText(R.raw.cburnett_bsd))
    }

    libraries?.firstOrNull { it.coordinates == openCoordinates }?.let { library ->
        LibraryLicenceDialog(library = library, onDismiss = { openCoordinates = null })
    }
}

/** The licence of one library. Licences without a bundled text show their name and URL only. */
@Composable
private fun LibraryLicenceDialog(library: LibraryLicence, onDismiss: () -> Unit) {
    val details = buildList {
        add(stringResource(R.string.licences_library_detail, library.coordinates, library.version))
        library.licences.forEach { licence ->
            add(stringResource(R.string.licences_library_licence, licence.name))
            if (OpenSourceLicences.textFor(licence.spdxId) == null) licence.url?.let(::add)
        }
    }
    val texts = library.licences.mapNotNull { OpenSourceLicences.textFor(it.spdxId) }.distinct()
        .map { rememberLicenceText(it) }
    LicenceTextDialog(
        title = library.displayName,
        details = details,
        text = texts.joinToString("\n\n"),
        onDismiss = onDismiss,
    )
}

@Preview(showBackground = true)
@Composable
private fun LicencesScreenPreview() {
    val apache = LicenceInfo("Apache-2.0", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0")
    ArmChessTheme {
        LicencesScreen(
            onBack = {},
            libraries = listOf(
                LibraryLicence("androidx.core", "core", "1.19.1", "Core", listOf(apache)),
                LibraryLicence("org.jetbrains.kotlin", "kotlin-stdlib", "2.4.21", "Kotlin Stdlib", listOf(apache)),
            ),
        )
    }
}
