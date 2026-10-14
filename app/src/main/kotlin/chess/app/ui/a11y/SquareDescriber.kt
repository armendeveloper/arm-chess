package chess.app.ui.a11y

import androidx.annotation.StringRes
import chess.app.R
import chess.app.ui.game.CellUi
import chess.app.ui.game.LastMoveRole
import chess.app.ui.game.PieceKind
import chess.app.ui.game.PieceUi
import chess.app.ui.game.Side
import chess.app.ui.game.SquareActionUi
import chess.app.ui.game.Squares
import chess.app.ui.game.TargetKind

/**
 * TalkBack text for board squares (design §5.3, tech design §8.1). Pure: the same cell always gives
 * the same text, and every word comes from [strings].
 *
 * Description grammar: `{coordinate}, {contents}[, {state}]*`, with the states in a fixed order:
 * selected, in check, the target phrase, last move from / to. Each state is appended through the
 * `a11y_square_with_state` format string, so a translation controls the separator.
 */
class SquareDescriber(private val strings: Strings) {

    fun describe(cell: CellUi): String {
        val contents = cell.piece?.let { strings.get(pieceName(it)) } ?: strings.get(R.string.a11y_square_empty)
        var text = strings.get(R.string.a11y_square, spokenCoordinate(cell.square), contents)
        stateIds(cell).forEach { id ->
            text = strings.get(R.string.a11y_square_with_state, text, strings.get(id))
        }
        return text
    }

    /**
     * The click label (TalkBack: "Double-tap to …"), or null when a tap would do nothing, which
     * leaves the square read-only. Own pieces get **select** even without legal moves: activating
     * one gives the "has no legal moves" feedback.
     */
    fun clickLabel(cell: CellUi, player: Side, interactive: Boolean): String? {
        if (!interactive) return null
        val id = when {
            cell.selected -> R.string.a11y_click_deselect
            cell.target != null ->
                if (cell.target.isCapture) R.string.a11y_click_capture else R.string.a11y_click_move
            cell.piece?.side == player -> R.string.a11y_click_select
            else -> return null
        }
        return strings.get(id)
    }

    /** The custom-action label for a move to [action]'s square (design §5.3 table). */
    fun actionLabel(action: SquareActionUi): String {
        val to = spokenCoordinate(action.to)
        return when (action.kind) {
            TargetKind.Move -> strings.get(R.string.a11y_action_move, to)
            TargetKind.Promotion -> strings.get(R.string.a11y_action_promote, to)
            TargetKind.EnPassant -> strings.get(R.string.a11y_action_en_passant, to)
            TargetKind.Castling ->
                if (Squares.file(action.to) > E_FILE) {
                    strings.get(R.string.a11y_action_castle_kingside)
                } else {
                    strings.get(R.string.a11y_action_castle_queenside)
                }
            TargetKind.Capture -> strings.get(takeAction(captured(action)), to)
            TargetKind.CapturePromotion -> strings.get(takePromoteAction(captured(action)), to)
        }
    }

    /**
     * The coordinate as TalkBack should say it. One place to switch to the "A 1" form if QA finds
     * TalkBack saying "uh 1" (design §5.3, tech design §8.1).
     */
    fun spokenCoordinate(square: Int): String = Squares.name(square)

    private fun stateIds(cell: CellUi): List<Int> = buildList {
        if (cell.selected) add(R.string.a11y_state_selected)
        if (cell.inCheck) add(R.string.a11y_state_in_check)
        cell.target?.let { add(targetPhrase(it)) }
        when (cell.lastMove) {
            LastMoveRole.From -> add(R.string.a11y_state_last_move_from)
            LastMoveRole.To -> add(R.string.a11y_state_last_move_to)
            null -> Unit
        }
    }

    private fun captured(action: SquareActionUi): PieceKind =
        requireNotNull(action.captured) { "${action.kind} to ${Squares.name(action.to)} needs the captured piece" }

    private companion object {
        /** Castling destinations are the c- or g-file; kingside is right of the king's e-file. */
        const val E_FILE = 4

        @StringRes
        fun targetPhrase(kind: TargetKind): Int = when (kind) {
            TargetKind.Move -> R.string.a11y_target_move
            TargetKind.Castling -> R.string.a11y_target_castling
            TargetKind.Promotion -> R.string.a11y_target_promotion
            TargetKind.Capture -> R.string.a11y_target_capture
            TargetKind.EnPassant -> R.string.a11y_target_en_passant
            TargetKind.CapturePromotion -> R.string.a11y_target_capture_promotion
        }

        @StringRes
        fun pieceName(piece: PieceUi): Int = when (piece.side) {
            Side.White -> when (piece.kind) {
                PieceKind.King -> R.string.a11y_piece_white_king
                PieceKind.Queen -> R.string.a11y_piece_white_queen
                PieceKind.Rook -> R.string.a11y_piece_white_rook
                PieceKind.Bishop -> R.string.a11y_piece_white_bishop
                PieceKind.Knight -> R.string.a11y_piece_white_knight
                PieceKind.Pawn -> R.string.a11y_piece_white_pawn
            }
            Side.Black -> when (piece.kind) {
                PieceKind.King -> R.string.a11y_piece_black_king
                PieceKind.Queen -> R.string.a11y_piece_black_queen
                PieceKind.Rook -> R.string.a11y_piece_black_rook
                PieceKind.Bishop -> R.string.a11y_piece_black_bishop
                PieceKind.Knight -> R.string.a11y_piece_black_knight
                PieceKind.Pawn -> R.string.a11y_piece_black_pawn
            }
        }

        @StringRes
        fun takeAction(captured: PieceKind): Int = when (captured) {
            PieceKind.Pawn -> R.string.a11y_action_take_pawn
            PieceKind.Knight -> R.string.a11y_action_take_knight
            PieceKind.Bishop -> R.string.a11y_action_take_bishop
            PieceKind.Rook -> R.string.a11y_action_take_rook
            PieceKind.Queen -> R.string.a11y_action_take_queen
            PieceKind.King -> throw IllegalArgumentException("A king is never captured")
        }

        @StringRes
        fun takePromoteAction(captured: PieceKind): Int = when (captured) {
            PieceKind.Knight -> R.string.a11y_action_take_knight_promote
            PieceKind.Bishop -> R.string.a11y_action_take_bishop_promote
            PieceKind.Rook -> R.string.a11y_action_take_rook_promote
            PieceKind.Queen -> R.string.a11y_action_take_queen_promote
            PieceKind.Pawn, PieceKind.King ->
                throw IllegalArgumentException("A promoting capture never takes a ${captured.name.lowercase()}")
        }
    }
}
