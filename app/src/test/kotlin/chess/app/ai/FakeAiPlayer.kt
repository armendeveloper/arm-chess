package chess.app.ai

import chess.engine.board.Move
import chess.engine.rules.Game
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * A scriptable [AiPlayer] for ViewModel and screen tests. It "thinks" for [thinkTime] (virtual time
 * under `runTest`), then plays the first of [script] that is legal, or else the first legal move.
 */
class FakeAiPlayer(
    var thinkTime: Duration = 50.milliseconds,
    script: List<String> = emptyList(),
) : AiPlayer {
    private val script = ArrayDeque(script)

    /** Every game the AI was asked to move in, in order. */
    val calls = mutableListOf<Game>()

    /** The levels it was asked to play at. */
    val levels = mutableListOf<Int>()

    /** How many searches were cancelled while thinking. */
    var cancellations = 0
        private set

    /** Thrown (once) instead of answering, to simulate an engine failure. */
    var failNext: Exception? = null

    override suspend fun chooseMove(game: Game, level: Int): Move {
        calls += game
        levels += level
        try {
            delay(thinkTime)
        } catch (e: CancellationException) {
            cancellations++
            throw e
        }
        failNext?.let {
            failNext = null
            throw it
        }
        val legal = game.position.legalMoves()
        val scripted = script.removeFirstOrNull()?.let { uci -> legal.firstOrNull { it.uci() == uci } }
        return scripted ?: legal.first()
    }
}
