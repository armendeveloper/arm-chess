package chess.app.ui.stats

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import chess.app.R
import chess.app.ui.components.SubScreen
import chess.app.ui.theme.ArmChessTheme

/** S6 Stats (design §4.8). Placeholder: the counts, table and reset arrive with T-21. */
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SubScreen(title = stringResource(R.string.stats_title), onBack = onBack, modifier = modifier) {
        Text(
            text = stringResource(R.string.stats_private),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StatsScreenPreview() {
    ArmChessTheme { StatsScreen(onBack = {}) }
}
