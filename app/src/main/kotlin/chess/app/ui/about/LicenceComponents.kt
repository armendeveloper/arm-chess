package chess.app.ui.about

import androidx.annotation.RawRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import chess.app.R

/** Titled section heading on S7 and S7b (design §4.9: titleMedium, a TalkBack heading). */
@Composable
internal fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * A full-width tappable row whose text lines up with the column text around it. A Material
 * `ListItem` adds its own 16 dp start padding, which pushed the text out of line (T-12 follow-up).
 * The row is one TalkBack node, and its minimum height keeps the 48 dp touch target.
 */
@Composable
internal fun LinkRow(
    headline: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: List<String> = emptyList(),
    onClickLabel: String? = null,
    showChevron: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClickLabel = onClickLabel, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = headline, style = MaterialTheme.typography.bodyLarge)
            supporting.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showChevron) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The bundled licence text of a `res/raw` file. Read once per composition; the files are about 10 KB. */
@Composable
internal fun rememberLicenceText(@RawRes id: Int): String {
    val resources = LocalResources.current
    return remember(resources, id) { readRawText(resources, id) }
}

/** The licence report bundled by the build, or null when it is missing (see [OpenSourceLicences.load]). */
@Composable
internal fun rememberBundledLibraries(): List<LibraryLicence>? {
    val assets = LocalContext.current.assets
    return remember(assets) { OpenSourceLicences.load(assets) }
}

/**
 * A licence shown in full: the title, optional detail lines (library coordinates, licence name),
 * then the legal text, which scrolls. The text is the original English (it is a legal document).
 */
@Composable
internal fun LicenceTextDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    details: List<String> = emptyList(),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        },
        title = { Text(text = title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                details.forEach { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
                LicenceText(text)
            }
        },
    )
}

/**
 * A legal text, one `Text` per paragraph (paragraphs are separated by a blank line). Short nodes keep
 * TalkBack reading paragraph by paragraph, and avoid laying out a 9 KB licence as a single text block.
 */
@Composable
internal fun LicenceText(text: String) {
    licenceParagraphs(text).forEach { paragraph ->
        Text(text = paragraph, style = MaterialTheme.typography.bodySmall)
    }
}

internal fun licenceParagraphs(text: String): List<String> =
    normaliseLicenceText(text).split("\n\n").filter { it.isNotBlank() }
