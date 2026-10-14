package chess.app.ui.game

/**
 * What the Game screen can ask of [GameViewModel] (tech design §6.3, unidirectional data flow).
 * Later tasks add `Drop` (T-25), `Promote`/`PromotionCancel` (T-16), `Undo`/`Resign*` (T-20), the
 * New game sheet actions (T-18) and `ScreenStarted`/`ScreenStopped` (T-19).
 */
sealed interface GameAction {
    /** A tap on [square] (0..63), by touch or by a TalkBack or Switch Access click. */
    data class SquareTap(val square: Int) : GameAction

    /** **Try again** after the computer failed to choose a move. */
    data object RetryEngine : GameAction
}
