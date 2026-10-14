package chess.app.ai

import chess.engine.board.Move
import chess.engine.rules.Game

/**
 * The computer opponent, as the Game screen sees it (tech design §6.5). The ViewModel calls this
 * from the main thread; each implementation does its work on its own injected dispatcher, so the
 * main thread is never blocked (US-6).
 *
 * - T-15: [RandomMoveAiPlayer], a stand-in that plays any legal move.
 * - T-19: `EngineAiPlayer` (the real search at the game's level) replaces it behind this interface.
 */
interface AiPlayer {
    /**
     * A legal move for the side to move in [game], played at [level] (1–4).
     *
     * Suspends until the move is chosen. Cancelling the caller cancels the search and throws
     * `CancellationException`; any other exception means the computer failed (design 4.3.8).
     *
     * @throws IllegalStateException when [game] is over or has no legal move.
     */
    suspend fun chooseMove(game: Game, level: Int): Move
}
