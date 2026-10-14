package chess.app.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import chess.app.BuildConfig
import chess.app.R
import chess.app.ui.components.SubScreen
import chess.app.ui.theme.ArmChessTheme

/**
 * S7 About (design §4.9). The copy is final; T-26 finishes the screen (version code, the
 * Cburnett notice and the generated licence list, which hides its item when missing).
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onLicences: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SubScreen(title = stringResource(R.string.about_title), onBack = onBack, modifier = modifier) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        SectionHeading(stringResource(R.string.about_privacy_heading))
        Text(
            text = stringResource(R.string.about_privacy_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        SectionHeading(stringResource(R.string.about_credits_heading))
        Text(
            text = stringResource(R.string.about_pieces),
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onLicences) {
            Text(stringResource(R.string.about_view_licence))
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.about_oss)) },
            modifier = Modifier.clickable(onClick = onLicences),
        )
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
}

@Preview(showBackground = true)
@Composable
private fun AboutScreenPreview() {
    ArmChessTheme { AboutScreen(onBack = {}, onLicences = {}) }
}
