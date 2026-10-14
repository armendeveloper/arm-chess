package chess.app.ai

import chess.engine.board.Move
import chess.engine.rules.Game
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * TEMPORARY (plan T-15, deleted in T-19): a computer that plays a uniformly random legal move and
 * ignores the level. It exists so the game can be played end to end before the search engine
 * (T-8, T-9) is wired in, and it goes through the same seam the engine will use: [AiPlayer],
 * running on the injected [dispatcher] (the container's `Dispatchers.Default`).
 */
class RandomMoveAiPlayer(
    private val dispatcher: CoroutineDispatcher,
    private val random: Random,
) : AiPlayer {

    override suspend fun chooseMove(game: Game, level: Int): Move = withContext(dispatcher) {
        check(!game.status.isOver) { "No move to choose: the game is over (${game.status})" }
        val moves = game.position.legalMoves()
        check(moves.isNotEmpty()) { "No legal move in ${game.position.toFen()}" }
        ensureActive()
        moves[random.nextInt(moves.size)]
    }
}
