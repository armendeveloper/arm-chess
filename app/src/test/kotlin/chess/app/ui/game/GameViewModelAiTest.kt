package chess.app.ui.game

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import chess.app.ai.FakeAiPlayer
import chess.app.ai.RandomMoveAiPlayer
import chess.app.testutil.MainDispatcherRule
import chess.engine.rules.Game
import chess.engine.rules.Position
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import org.junit.Rule
import org.junit.Test

/**
 * The computer's turn (US-6, design US-6 flow, tech design §6.5): it starts only after the player
 * moves, the move is shown no earlier than 400 ms after the turn began, the board stays locked
 * while it thinks, failures are reported, and leaving the screen cancels the search.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelAiTest {

    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun computerRepliesOnlyAfterThePlayerMoves() = runTest {
        val ai = FakeAiPlayer()
        val vm = viewModel(ai)
        advanceUntilIdle()
        assertTrue(ai.calls.isEmpty())

        vm.tap("e2")
        advanceUntilIdle()
        assertTrue(ai.calls.isEmpty(), "selecting is not a move")

        vm.tap("e4")
        runCurrent()
        assertEquals(1, ai.calls.size)
        assertEquals(listOf("e2e4"), ai.calls.single().moves.map { it.uci() })
        assertEquals(listOf(2), ai.levels, "the game's level is passed to the computer")
    }

    @Test
    fun aFastAnswerIsShownExactlyAt400ms() = runTest {
        val ai = FakeAiPlayer(thinkTime = 50.milliseconds, script = listOf("e7e5"))
        val vm = viewModel(ai)
        vm.tap("e2")
        vm.tap("e4")

        advanceTimeBy(399)
        assertEquals(StatusUi.Thinking(check = false), vm.uiState.value.status)
        assertEquals(LastMoveUi(sq("e2"), sq("e4")), vm.uiState.value.board.lastMove)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(400, currentTime)
        assertEquals(LastMoveUi(sq("e7"), sq("e5")), vm.uiState.value.board.lastMove)
        assertEquals(StatusUi.YourMove, vm.uiState.value.status)
        assertTrue(vm.uiState.value.board.interactive)
    }

    @Test
    fun aSlowAnswerIsShownAsSoonAsItArrives() = runTest {
        val ai = FakeAiPlayer(thinkTime = 700.milliseconds, script = listOf("e7e5"))
        val vm = viewModel(ai)
        vm.tap("e2")
        vm.tap("e4")

        advanceTimeBy(699)
        assertEquals(StatusUi.Thinking(check = false), vm.uiState.value.status)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(700, currentTime)
        assertEquals(LastMoveUi(sq("e7"), sq("e5")), vm.uiState.value.board.lastMove)
    }

    @Test
    fun theBoardStaysLockedWhileTheComputerThinks() = runTest {
        val ai = FakeAiPlayer(thinkTime = 1_000.milliseconds)
        val vm = viewModel(ai)
        vm.tap("e2")
        vm.tap("e4")
        advanceTimeBy(500)
        assertFalse(vm.uiState.value.board.interactive)
        vm.tap("d2")
        assertNull(vm.uiState.value.board.selected)
    }

    @Test
    fun theComputerMovesFirstWhenThePlayerIsBlack() = runTest {
        val ai = FakeAiPlayer(thinkTime = 10.milliseconds, script = listOf("d2d4"))
        val vm = viewModel(ai, player = Side.Black)
        runCurrent()
        assertEquals(Side.Black, vm.uiState.value.board.player)
        assertEquals(StatusUi.Thinking(check = false), vm.uiState.value.status)
        assertEquals(1, ai.calls.size)

        advanceTimeBy(400)
        runCurrent()
        assertEquals(LastMoveUi(sq("d2"), sq("d4")), vm.uiState.value.board.lastMove)
        assertEquals(StatusUi.YourMove, vm.uiState.value.status)

        vm.tap("d7")
        vm.tap("d5")
        assertEquals(2, ai.calls.size)
    }

    @Test
    fun theComputerCanEndTheGame() = runTest {
        // Fool's mate: the computer (Black) mates with Qh4#.
        val start = Game.replay(Position.START_FEN, listOf("f2f3", "e7e5"))
        val ai = FakeAiPlayer(script = listOf("d8h4"))
        val vm = viewModel(ai, startGame = start)
        vm.tap("g2")
        vm.tap("g4")
        advanceUntilIdle()
        assertEquals(StatusUi.GameOver(GameEnd.CheckmateLoss), vm.uiState.value.status)
        assertEquals(sq("e1"), vm.uiState.value.board.checkSquare)
        assertFalse(vm.uiState.value.board.interactive)
        assertEquals(1, ai.calls.size, "no search after the game ends")
    }

    @Test
    fun thePlayersMatingMoveEndsTheGameWithoutAskingTheComputer() = runTest {
        // Scholar's-mate shape: Qxf7#.
        val start = Game.replay(
            Position.START_FEN,
            listOf("e2e4", "e7e5", "f1c4", "b8c6", "d1h5", "g8f6"),
        )
        val ai = FakeAiPlayer()
        val vm = viewModel(ai, startGame = start)
        vm.tap("h5")
        vm.tap("f7")
        advanceUntilIdle()
        assertEquals(StatusUi.GameOver(GameEnd.CheckmateWin), vm.uiState.value.status)
        assertTrue(ai.calls.isEmpty())
    }

    @Test
    fun anEngineFailureIsReportedAndTryAgainRetries() = runTest {
        val ai = FakeAiPlayer(script = listOf("e7e5")).apply { failNext = IllegalStateException("boom") }
        val errors = mutableListOf<Throwable>()
        val vm = GameViewModel(ai = ai, timeSource = testTimeSource, onAiError = { errors += it })
        vm.tap("e2")
        vm.tap("e4")
        advanceUntilIdle()
        assertEquals(StatusUi.EngineError, vm.uiState.value.status)
        assertFalse(vm.uiState.value.board.interactive)
        assertEquals("boom", errors.single().message)

        vm.onAction(GameAction.RetryEngine)
        runCurrent()
        assertEquals(StatusUi.Thinking(check = false), vm.uiState.value.status)
        advanceUntilIdle()
        assertEquals(LastMoveUi(sq("e7"), sq("e5")), vm.uiState.value.board.lastMove)
        assertEquals(StatusUi.YourMove, vm.uiState.value.status)
        assertEquals(2, ai.calls.size)
    }

    @Test
    fun retryIsIgnoredWhenThereIsNoError() = runTest {
        val ai = FakeAiPlayer()
        val vm = viewModel(ai)
        vm.onAction(GameAction.RetryEngine)
        advanceUntilIdle()
        assertTrue(ai.calls.isEmpty())
        assertEquals(StatusUi.YourMove, vm.uiState.value.status)
    }

    @Test
    fun leavingTheScreenCancelsTheSearch() = runTest {
        val ai = FakeAiPlayer(thinkTime = 2_000.milliseconds)
        val store = ViewModelStore()
        val factory = viewModelFactory { initializer { GameViewModel(ai = ai, timeSource = testTimeSource) } }
        val vm = ViewModelProvider.create(store, factory)[GameViewModel::class]
        vm.tap("e2")
        vm.tap("e4")
        advanceTimeBy(100)
        assertEquals(1, ai.calls.size)

        store.clear()
        advanceUntilIdle()
        assertEquals(1, ai.cancellations)
        assertEquals(LastMoveUi(sq("e2"), sq("e4")), vm.uiState.value.board.lastMove, "no stale move applied")
    }

    @Test
    fun aWholeGameAgainstTheRandomComputerStaysLegal() = runTest {
        val random = RandomMoveAiPlayer(StandardTestDispatcher(testScheduler), Random(2026))
        val vm = GameViewModel(ai = random, timeSource = testTimeSource)
        var plies = 0
        while (vm.uiState.value.status !is StatusUi.GameOver && plies < 200) {
            val state = vm.uiState.value
            assertTrue(state.board.interactive, "player's turn after ${state.board.lastMove}")
            // The player plays the first own piece (in square order) with a legal move, to its first target.
            val from = (0..63).first { sq ->
                state.board.isOwnPiece(sq) && run {
                    vm.onAction(GameAction.SquareTap(sq))
                    runCurrent()
                    vm.uiState.value.board.selected == sq
                }
            }
            val to = vm.uiState.value.board.targets.keys.min()
            vm.onAction(GameAction.SquareTap(to))
            runCurrent()
            assertEquals(LastMoveUi(from, to), vm.uiState.value.board.lastMove)
            advanceUntilIdle()
            plies += 2
        }
        assertTrue(plies >= 6, "the game lasted only $plies plies")
    }

    private fun TestScope.viewModel(
        ai: FakeAiPlayer,
        player: Side = Side.White,
        startGame: Game = Game.start(),
    ) = GameViewModel(ai = ai, player = player, startGame = startGame, timeSource = testTimeSource)

    private fun GameViewModel.tap(name: String) {
        onAction(GameAction.SquareTap(sq(name)))
        main.dispatcher.scheduler.runCurrent()
    }

    private fun sq(name: String) = Squares.parse(name)
}
