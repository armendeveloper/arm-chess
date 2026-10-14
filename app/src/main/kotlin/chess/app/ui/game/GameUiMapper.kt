package chess.app.ui.game

import chess.engine.board.Move
import chess.engine.board.Piece
import chess.engine.board.PieceColor
import chess.engine.board.PieceType
import chess.engine.rules.Game
import chess.engine.rules.GameStatus

/**
 * Builds the Game screen's UI state from the engine's [Game] (tech design §6.3). Pure, so it is
 * tested on the JVM by feeding it FENs and comparing structures. This is the only place where
 * engine types are turned into the UI model.
 */
object GameUiMapper {

    /**
     * The whole screen state.
     *
     * @param selected the selected square, kept by the ViewModel.
     * @param hint true while the "no legal moves for that piece" hint is showing.
     * @param engineError true after the computer failed to choose a move.
     */
    fun map(
        game: Game,
        player: Side,
        level: Int,
        selected: Int?,
        hint: Boolean = false,
        engineError: Boolean = false,
    ): GameUiState = GameUiState(
        level = level,
        player = player,
        board = board(game, player, selected),
        status = status(game, player, hint, engineError),
    )

    /**
     * The board: pieces, the selected square and its targets, the last move, the checked king, and
     * whether taps count (the player's turn in an ongoing game).
     */
    fun board(game: Game, player: Side, selected: Int?): BoardUi {
        val position = game.position
        return BoardUi(
            pieces = List(64) { square -> piece(position.pieceAt(square)) },
            player = player,
            selected = selected,
            targets = if (selected == null) emptyMap() else targets(game.legalMovesFrom(selected)),
            lastMove = game.moves.lastOrNull()?.let { LastMoveUi(it.from, it.to) },
            checkSquare = if (position.isCheck) position.kingSquare(position.sideToMove) else null,
            interactive = isPlayersTurn(game, player),
        )
    }

    /**
     * The legal moves of one piece as board targets, keyed by destination. Castling targets the
     * king's destination (the engine encodes castling as the king's move), en passant targets the
     * empty square the pawn lands on, and the four promotion moves to one square become one target.
     */
    fun targets(moves: List<Move>): Map<Int, TargetKind> = moves.associate { move ->
        move.to to when {
            move.isCastle -> TargetKind.Castling
            move.isEnPassant -> TargetKind.EnPassant
            move.isPromotion && move.isCapture -> TargetKind.CapturePromotion
            move.isPromotion -> TargetKind.Promotion
            move.isCapture -> TargetKind.Capture
            else -> TargetKind.Move
        }
    }

    /** The status row's state. A finished game wins over the hint and an engine error. */
    fun status(game: Game, player: Side, hint: Boolean = false, engineError: Boolean = false): StatusUi {
        val end = gameEnd(game.status, player)
        val check = game.position.isCheck
        return when {
            end != null -> StatusUi.GameOver(end)
            !isPlayersTurn(game, player) -> if (engineError) StatusUi.EngineError else StatusUi.Thinking(check)
            hint -> StatusUi.NoLegalMoves
            check -> StatusUi.Check
            else -> StatusUi.YourMove
        }
    }

    /** True when the game is on and it is [player]'s move. */
    fun isPlayersTurn(game: Game, player: Side): Boolean =
        !game.status.isOver && side(game.position.sideToMove) == player

    fun side(color: PieceColor): Side = if (color == PieceColor.WHITE) Side.White else Side.Black

    fun color(side: Side): PieceColor = if (side == Side.White) PieceColor.WHITE else PieceColor.BLACK

    private fun gameEnd(status: GameStatus, player: Side): GameEnd? = when (status) {
        GameStatus.Ongoing -> null
        is GameStatus.Checkmate -> if (side(status.winner) == player) GameEnd.CheckmateWin else GameEnd.CheckmateLoss
        GameStatus.Stalemate -> GameEnd.Stalemate
        GameStatus.ThreefoldRepetition -> GameEnd.ThreefoldRepetition
        GameStatus.FiftyMoveRule -> GameEnd.FiftyMoveRule
        GameStatus.InsufficientMaterial -> GameEnd.InsufficientMaterial
    }

    private fun piece(code: Int): PieceUi? {
        if (code == Piece.NONE) return null
        val kind = when (Piece.type(code)) {
            PieceType.KING -> PieceKind.King
            PieceType.QUEEN -> PieceKind.Queen
            PieceType.ROOK -> PieceKind.Rook
            PieceType.BISHOP -> PieceKind.Bishop
            PieceType.KNIGHT -> PieceKind.Knight
            PieceType.PAWN -> PieceKind.Pawn
        }
        return PieceUi(side(Piece.color(code)), kind)
    }
}
