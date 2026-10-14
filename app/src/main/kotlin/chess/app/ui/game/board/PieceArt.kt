package chess.app.ui.game.board

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import chess.app.R
import chess.app.ui.game.PieceKind
import chess.app.ui.game.PieceUi
import chess.app.ui.game.Side

/**
 * The piece artwork (Cburnett, design §6.3; T-13). This is the only place that names the piece
 * drawables, so swapping the art set is a one-file change (tech design §7). Each drawable is 45 dp
 * with its own padding and is scaled to fill the square.
 */
internal object PieceArt {

    val allPieces: List<PieceUi> = Side.entries.flatMap { side -> PieceKind.entries.map { PieceUi(side, it) } }

    @DrawableRes
    fun drawable(piece: PieceUi): Int = when (piece.side) {
        Side.White -> when (piece.kind) {
            PieceKind.King -> R.drawable.piece_wk
            PieceKind.Queen -> R.drawable.piece_wq
            PieceKind.Rook -> R.drawable.piece_wr
            PieceKind.Bishop -> R.drawable.piece_wb
            PieceKind.Knight -> R.drawable.piece_wn
            PieceKind.Pawn -> R.drawable.piece_wp
        }
        Side.Black -> when (piece.kind) {
            PieceKind.King -> R.drawable.piece_bk
            PieceKind.Queen -> R.drawable.piece_bq
            PieceKind.Rook -> R.drawable.piece_br
            PieceKind.Bishop -> R.drawable.piece_bb
            PieceKind.Knight -> R.drawable.piece_bn
            PieceKind.Pawn -> R.drawable.piece_bp
        }
    }
}

/** One vector painter per piece. `painterResource` caches the parsed vectors per configuration. */
@Composable
internal fun piecePainters(): Map<PieceUi, Painter> =
    PieceArt.allPieces.associateWith { painterResource(PieceArt.drawable(it)) }
