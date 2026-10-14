package chess.app.ui.game

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import chess.app.R
import chess.app.ui.theme.ArmChessTheme

/** Test tag of the status row's live-region node; its content description is the spoken text. */
const val StatusRowTestTag = "status_row"

/**
 * The status row (design §4.3.4): a decorative indicator and the status text, which wraps to as
 * many lines as it needs. It is the screen's **polite live region** (tech design §8.2), so TalkBack
 * speaks the text whenever it changes. All words come from `strings.xml`.
 *
 * Still to come:
 * - T-24: the spoken text becomes "{event sentence} {status sentence}" from the ViewModel's
 *   structured announcement, with the U+200B repeat trick.
 * - T-25: a static `hourglass_empty` icon instead of the spinner when animations are off.
 * - T-16: the result panel replaces this row when the game ends; until then it shows the title.
 */
@Composable
fun StatusRow(
    status: StatusUi,
    player: Side,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = stringResource(status.textRes)
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (status) {
        StatusUi.Check, StatusUi.EngineError -> colors.errorContainer to colors.onErrorContainer
        is StatusUi.GameOver -> colors.secondaryContainer to colors.onSecondaryContainer
        else -> colors.surface to colors.onSurface
    }
    Surface(color = container, contentColor = content, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = text
                        testTag = StatusRowTestTag
                    },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    StatusIndicator(status, player)
                }
                Text(text = text, style = MaterialTheme.typography.titleMedium)
            }
            if (status == StatusUi.EngineError) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_try_again)) }
            }
        }
    }
}

@get:StringRes
private val StatusUi.textRes: Int
    get() = when (this) {
        StatusUi.YourMove -> R.string.status_your_move
        StatusUi.Check -> R.string.status_check
        is StatusUi.Thinking -> if (check) R.string.status_thinking_check else R.string.status_thinking
        StatusUi.NoLegalMoves -> R.string.status_no_moves
        StatusUi.EngineError -> R.string.status_engine_error
        is StatusUi.GameOver -> when (end) {
            GameEnd.CheckmateWin -> R.string.result_mate_win
            GameEnd.CheckmateLoss -> R.string.result_mate_loss
            GameEnd.Stalemate -> R.string.result_stalemate
            GameEnd.ThreefoldRepetition -> R.string.result_threefold
            GameEnd.FiftyMoveRule -> R.string.result_fifty
            GameEnd.InsufficientMaterial -> R.string.result_insufficient
        }
    }

/** The decorative icon at the start of the row (design §4.3.4); hidden from TalkBack. */
@Composable
private fun StatusIndicator(status: StatusUi, player: Side) {
    val decorative = Modifier.clearAndSetSemantics { }
    when (status) {
        StatusUi.YourMove -> TurnDot(player, decorative)
        StatusUi.Check -> CheckBadge(decorative)
        is StatusUi.Thinking -> CircularProgressIndicator(modifier = decorative.size(20.dp), strokeWidth = 2.dp)
        StatusUi.NoLegalMoves -> Icon(painterResource(R.drawable.ic_info), contentDescription = null)
        StatusUi.EngineError -> Icon(painterResource(R.drawable.ic_error), contentDescription = null)
        is StatusUi.GameOver -> Icon(painterResource(R.drawable.ic_flag), contentDescription = null)
    }
}

/** A 12 dp disc in the player's piece colour with a 1 dp outline. */
@Composable
private fun TurnDot(player: Side, modifier: Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    val fill = if (player == Side.White) Color.White else Color.Black
    Canvas(modifier.size(12.dp)) {
        drawCircle(fill)
        drawCircle(outline, style = Stroke(width = 1.dp.toPx()), radius = size.minDimension / 2 - 0.5.dp.toPx())
    }
}

/** The board's check badge (design §4.3.3): a red disc with a white "+". */
@Composable
private fun CheckBadge(modifier: Modifier) {
    val boardColors = ArmChessTheme.boardColors
    Canvas(modifier.size(20.dp)) {
        val r = size.minDimension / 2
        drawCircle(boardColors.cueHalo, radius = r)
        drawCircle(boardColors.cueCheck, radius = r - 1.5.dp.toPx())
        val arm = r * 0.5f
        val stroke = 2.dp.toPx()
        drawLine(boardColors.cueCheckGlyph, Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), stroke)
        drawLine(boardColors.cueCheckGlyph, Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), stroke)
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun StatusRowPreview() {
    ArmChessTheme {
        Column {
            StatusRow(StatusUi.YourMove, Side.White, onRetry = {})
            StatusRow(StatusUi.Check, Side.Black, onRetry = {})
            StatusRow(StatusUi.Thinking(check = false), Side.White, onRetry = {})
            StatusRow(StatusUi.NoLegalMoves, Side.White, onRetry = {})
            StatusRow(StatusUi.EngineError, Side.White, onRetry = {})
            StatusRow(StatusUi.GameOver(GameEnd.CheckmateWin), Side.White, onRetry = {})
        }
    }
}
