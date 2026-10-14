package chess.app.ai

import chess.app.AppContainer
import chess.app.AppDispatchers
import chess.engine.rules.Game
import chess.engine.rules.Position
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The temporary random-move computer (plan T-15; replaced by `EngineAiPlayer` in T-19). It goes
 * through the same [AiPlayer] seam as the real engine: it switches to the injected dispatcher, so
 * the caller's (main) thread never does the work (US-6).
 */
class RandomMoveAiPlayerTest {

    @Test
    fun choosesALegalMoveInEveryPosition() = runTest {
        val ai = RandomMoveAiPlayer(StandardTestDispatcher(testScheduler), Random(7))
        val fens = listOf(
            Position.START_FEN,
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1",
            "rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3",
            "4k3/P7/8/8/8/8/8/4K3 w - - 0 1",
            "4k3/8/8/8/8/8/8/4K2r w - - 0 1",
        )
        fens.forEach { fen ->
            val game = Game.start(Position.fromFen(fen))
            repeat(20) {
                val move = ai.chooseMove(game, level = 2)
                assertTrue(game.position.isLegal(move), "$move in $fen")
            }
        }
    }

    @Test
    fun theSameSeedPlaysTheSameMoves() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val a = RandomMoveAiPlayer(dispatcher, Random(42))
        val b = RandomMoveAiPlayer(dispatcher, Random(42))
        val game = Game.start()
        assertEquals(List(10) { a.chooseMove(game, 2) }, List(10) { b.chooseMove(game, 2) })
    }

    @Test
    fun variesItsMoves() = runTest {
        val ai = RandomMoveAiPlayer(StandardTestDispatcher(testScheduler), Random(1))
        val moves = List(40) { ai.chooseMove(Game.start(), 2) }.toSet()
        assertTrue(moves.size > 5, "only ${moves.size} distinct moves")
    }

    @Test
    fun runsOnTheInjectedDispatcher() = runTest {
        val dispatcher = RecordingDispatcher(StandardTestDispatcher(testScheduler))
        val ai = RandomMoveAiPlayer(dispatcher, Random(3))
        ai.chooseMove(Game.start(), level = 1)
        assertTrue(dispatcher.dispatches > 0, "the move was chosen without switching to the AI dispatcher")
    }

    @Test
    fun theContainerRunsTheComputerOnItsDefaultDispatcher() {
        val recording = RecordingDispatcher(kotlinx.coroutines.Dispatchers.Default)
        val container = AppContainer(AppDispatchers(default = recording))
        val move = runBlocking { container.newAiPlayer().chooseMove(Game.start(), level = 2) }
        assertTrue(Position.START.isLegal(move))
        assertTrue(recording.dispatches > 0)
    }

    @Test
    fun cancellingTheCallerCancelsTheChoice() = runTest {
        val ai = RandomMoveAiPlayer(StandardTestDispatcher(testScheduler), Random(5))
        val choice = async { ai.chooseMove(Game.start(), 2) }
        choice.cancel()
        assertTrue(choice.isCancelled)
    }

    @Test
    fun refusesAFinishedGame() = runTest {
        val ai = RandomMoveAiPlayer(StandardTestDispatcher(testScheduler), Random(5))
        val mated = Game.replay(Position.START_FEN, listOf("f2f3", "e7e5", "g2g4", "d8h4"))
        assertFailsWith<IllegalStateException> { ai.chooseMove(mated, 2) }
    }

    /** Counts dispatches, so a test can see that work really moved to this dispatcher. */
    private class RecordingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        @Volatile
        var dispatches = 0
            private set

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatches++
            delegate.dispatch(context, block)
        }
    }
}
