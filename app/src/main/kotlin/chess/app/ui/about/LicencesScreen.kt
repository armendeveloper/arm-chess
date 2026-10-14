package chess.app.ui.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import chess.app.R
import chess.app.ui.components.SubScreen
import chess.app.ui.theme.ArmChessTheme

/** S7b Licences (design §4.9). Placeholder: T-26 adds the library list and the Cburnett notice. */
@Composable
fun LicencesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SubScreen(title = stringResource(R.string.about_oss), onBack = onBack, modifier = modifier) {}
}

@Preview(showBackground = true)
@Composable
private fun LicencesScreenPreview() {
    ArmChessTheme { LicencesScreen(onBack = {}) }
}
