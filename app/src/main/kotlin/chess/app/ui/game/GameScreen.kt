package chess.app.ui.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import chess.app.R
import chess.app.ui.components.BackTopAppBar
import chess.app.ui.game.board.BoardFixtures
import chess.app.ui.game.board.ChessBoard
import chess.app.ui.theme.ArmChessTheme

/** S3 Game (design §4.3), connected to its [GameViewModel]. */
@Composable
fun GameRoute(
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GameViewModel = viewModel(factory = GameViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GameScreen(state = state, onAction = viewModel::onAction, onHome = onHome, modifier = modifier)
}

/**
 * S3 Game (design §4.3), T-15's basic form: the top bar, the board and the status row in one
 * column, inside `safeDrawing`. The screen does not scroll.
 *
 * Still to come: the move list (T-22), the action row (T-20), the result panel (T-16) and the
 * adaptive stacked or side-by-side layout with its collapses (T-23).
 */
@Composable
fun GameScreen(
    state: GameUiState,
    onAction: (GameAction) -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            BackTopAppBar(
                title = levelTitle(state.level),
                navigationContentDescription = stringResource(R.string.game_home),
                onBack = onHome,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // As wide as the window, unless the height is short; T-23's GameLayout takes over sizing.
            ChessBoard(
                board = state.board,
                onSquareClick = { onAction(GameAction.SquareTap(it)) },
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            StatusRow(
                status = state.status,
                player = state.player,
                onRetry = { onAction(GameAction.RetryEngine) },
            )
        }
    }
}

/** "Level 2 · Casual" (design §4.3.2). */
@Composable
private fun levelTitle(level: Int): String {
    val name = when (level) {
        1 -> R.string.level_name_1
        2 -> R.string.level_name_2
        3 -> R.string.level_name_3
        else -> R.string.level_name_4
    }
    return stringResource(R.string.level_label, level, stringResource(name))
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun GameScreenPreview() {
    ArmChessTheme {
        GameScreen(
            state = GameUiState(
                level = 2,
                player = Side.White,
                board = BoardFixtures.start(Side.White, interactive = true),
                status = StatusUi.YourMove,
            ),
            onAction = {},
            onHome = {},
        )
    }
}
