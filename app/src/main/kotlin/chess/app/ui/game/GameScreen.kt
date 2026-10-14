package chess.app.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import chess.app.R
import chess.app.ui.components.BackTopAppBar
import chess.app.ui.theme.ArmChessTheme

/**
 * S3 Game (design §4.3). Placeholder with only the top bar: its `arrow_back` is labelled "Home"
 * and does the same as system back (design §4.3.2). The title ("Level 2 · Casual"), board and
 * controls arrive with T-14 and T-15. The screen does not scroll; everything sits in `safeDrawing`.
 */
@Composable
fun GameScreen(
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            BackTopAppBar(
                title = null,
                navigationContentDescription = stringResource(R.string.game_home),
                onBack = onHome,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding))
    }
}

@Preview(showBackground = true)
@Composable
private fun GameScreenPreview() {
    ArmChessTheme { GameScreen(onHome = {}) }
}
