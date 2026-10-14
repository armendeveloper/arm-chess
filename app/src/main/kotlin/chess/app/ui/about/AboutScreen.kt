package chess.app.ui.about

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import chess.app.BuildConfig
import chess.app.R
import chess.app.ui.components.SubScreen
import chess.app.ui.theme.ArmChessTheme

/**
 * S7 About (design §4.9): the app name and version, the privacy promise and the piece-art credit.
 * **View licence** shows the full Cburnett BSD notice in a dialog. **Open-source licences** opens S7b
 * and is hidden when the build has no licence report ([licencesAvailable]).
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onLicences: () -> Unit,
    modifier: Modifier = Modifier,
    licencesAvailable: Boolean = !rememberBundledLibraries().isNullOrEmpty(),
) {
    var showPiecesLicence by rememberSaveable { mutableStateOf(false) }

    SubScreen(title = stringResource(R.string.about_title), onBack = onBack, modifier = modifier) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
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
        TextButton(onClick = { showPiecesLicence = true }) {
            Text(stringResource(R.string.about_view_licence))
        }
        if (licencesAvailable) {
            LinkRow(
                headline = stringResource(R.string.about_oss),
                onClick = onLicences,
                showChevron = true,
            )
        }
    }

    if (showPiecesLicence) {
        LicenceTextDialog(
            title = stringResource(R.string.licences_pieces_heading),
            details = listOf(stringResource(R.string.about_pieces)),
            text = rememberLicenceText(R.raw.cburnett_bsd),
            onDismiss = { showPiecesLicence = false },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AboutScreenPreview() {
    ArmChessTheme { AboutScreen(onBack = {}, onLicences = {}, licencesAvailable = true) }
}
