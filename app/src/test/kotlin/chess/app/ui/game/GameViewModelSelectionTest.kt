package chess.app.ui.game

import chess.app.ai.FakeAiPlayer
import chess.app.testutil.MainDispatcherRule
import chess.engine.rules.Game
import chess.engine.rules.Position
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import org.junit.Rule
import org.junit.Test

/**
 * Tap-to-move (US-3, design §4.3.3 "Board interactions" and the US-3 flow): select, move, reselect,
 * deselect, illegal taps, the no-legal-moves hint, and taps while the board is locked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelSelectionTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val ai = FakeAiPlayer()

    @Test
    fun newGameIsWhiteAtLevelTwoWithNothingSelected() = runTest {
        val vm = viewModel()
        val state = vm.uiState.value
        assertEquals(Side.White, state.player)
        assertEquals(2, state.level)
        assertEquals(StatusUi.YourMove, state.status)
        assertTrue(state.board.interactive)
        assertNull(state.board.selected)
        assertNull(state.board.lastMove)
        assertTrue(ai.calls.isEmpty(), "the computer must not move on the player's turn")
    }

    @Test
    fun tapOwnPieceWithLegalMovesSelectsItAndShowsTargets() = runTest {
        val vm = viewModel()
        vm.tap("e2")
        val board = vm.uiState.value.board
        assertEquals(sq("e2"), board.selected)
        assertEquals(mapOf(sq("e3") to TargetKind.Move, sq("e4") to TargetKind.Move), board.targets)
        assertEquals(StatusUi.YourMove, vm.uiState.value.status)
    }

    @Test
    fun tapLegalDestinationPlaysTheMoveAndClearsTheSelection() = runTest {
        val vm = viewModel()
        vm.tap("e2")
        vm.tap("e4")
        val state = vm.uiState.value
        assertEquals(PieceUi(Side.White, PieceKind.Pawn), state.board.pieces[sq("e4")])
        assertNull(state.board.pieces[sq("e2")])
        assertEquals(LastMoveUi(sq("e2"), sq("e4")), state.board.lastMove)
        assertNull(state.board.selected)
        assertTrue(state.board.targets.isEmpty())
        assertEquals(StatusUi.Thinking(check = false), state.status)
        assertFalse(state.board.interactive, "the board locks on the computer's turn")
    }

    @Test
    fun tapAnotherOwnPieceMovesTheSelection() = runTest {
        val vm = viewModel()
        vm.tap("g1")
        vm.tap("b1")
        val board = vm.uiState.value.board
        assertEquals(sq("b1"), board.selected)
        assertEquals(mapOf(sq("a3") to TargetKind.Move, sq("c3") to TargetKind.Move), board.targets)
    }

    @Test
    fun tapTheSelectedPieceAgainDeselects() = runTest {
        val vm = viewModel()
        vm.tap("e2")
        vm.tap("e2")
        assertNull(vm.uiState.value.board.selected)
        assertTrue(vm.uiState.value.board.targets.isEmpty())
    }

    @Test
    fun tapAnIllegalEmptySquareDeselectsSilently() = runTest {
        val vm = viewModel()
        vm.tap("e2")
        vm.tap("e5")
        assertNull(vm.uiState.value.board.selected)
        assertEquals(StatusUi.YourMove, vm.uiState.value.status)
        assertNull(vm.uiState.value.board.lastMove, "no move was played")
    }

    @Test
    fun tapAnEnemyPieceThatIsNotATargetDeselects() = runTest {
        val vm = viewModel()
        vm.tap("e2")
        vm.tap("e7")
        assertNull(vm.uiState.value.board.selected)
        assertNull(vm.uiState.value.board.lastMove)
    }

    @Test
    fun withNothingSelectedEmptyAndEnemySquaresDoNothing() = runTest {
        val vm = viewModel()
        val before = vm.uiState.value
        vm.tap("e4")
        vm.tap("e7")
        assertEquals(before, vm.uiState.value)
    }

    @Test
    fun ownPieceWithNoLegalMovesSelectsNothingAndShowsTheHintFor2500ms() = runTest {
        val vm = viewModel()
        vm.tap("a1")
        assertNull(vm.uiState.value.board.selected)
        assertEquals(StatusUi.NoLegalMoves, vm.uiState.value.status)

        advanceTimeBy(2_499)
        assertEquals(StatusUi.NoLegalMoves, vm.uiState.value.status)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(StatusUi.YourMove, vm.uiState.value.status)
    }

    @Test
    fun reselectingAPieceWithNoLegalMovesClearsTheSelectionAndShowsTheHint() = runTest {
        val vm = viewModel()
        vm.tap("e2")
        vm.tap("d1")
        assertNull(vm.uiState.value.board.selected)
        assertEquals(StatusUi.NoLegalMoves, vm.uiState.value.status)
    }

    @Test
    fun theNextTapEndsTheHintAndARepeatRestartsIts2500ms() = runTest {
        val vm = viewModel()
        vm.tap("a1")
        vm.tap("e2")
        assertEquals(StatusUi.YourMove, vm.uiState.value.status)
        assertEquals(sq("e2"), vm.uiState.value.board.selected)

        vm.tap("h1")
        advanceTimeBy(2_000)
        vm.tap("a1")
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(StatusUi.NoLegalMoves, vm.uiState.value.status, "the second hint has its own 2.5 s")
        advanceTimeBy(501)
        runCurrent()
        assertEquals(StatusUi.YourMove, vm.uiState.value.status)
    }

    @Test
    fun tapsDuringTheComputersTurnAreIgnored() = runTest {
        val vm = viewModel()
        vm.tap("e2")
        vm.tap("e4")
        val thinking = vm.uiState.value
        vm.tap("d2")
        vm.tap("g1")
        vm.tap("e4")
        assertEquals(thinking, vm.uiState.value)
        assertNull(vm.uiState.value.board.selected)

        // An own piece tapped while locked must not leave a hint behind for the player's turn.
        vm.tap("a1")
        advanceTimeBy(400)
        runCurrent()
        assertEquals(StatusUi.YourMove, vm.uiState.value.status)
        assertNull(vm.uiState.value.board.selected)
    }

    @Test
    fun tapsAfterTheGameEndsAreIgnored() = runTest {
        val mated = Game.replay(Position.START_FEN, listOf("f2f3", "e7e5", "g2g4", "d8h4"))
        val vm = viewModel(startGame = mated)
        assertEquals(StatusUi.GameOver(GameEnd.CheckmateLoss), vm.uiState.value.status)
        assertFalse(vm.uiState.value.board.interactive)
        val before = vm.uiState.value
        vm.tap("e1")
        vm.tap("a2")
        assertEquals(before, vm.uiState.value)
        assertTrue(ai.calls.isEmpty())
    }

    @Test
    fun castlingIsPlayedByTappingTheKingsDestination() = runTest {
        val vm = viewModel(startGame = Game.start(Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")))
        vm.tap("e1")
        assertEquals(TargetKind.Castling, vm.uiState.value.board.targets[sq("g1")])
        vm.tap("g1")
        val pieces = vm.uiState.value.board.pieces
        assertEquals(PieceUi(Side.White, PieceKind.King), pieces[sq("g1")])
        assertEquals(PieceUi(Side.White, PieceKind.Rook), pieces[sq("f1")])
        assertNull(pieces[sq("h1")])
    }

    @Test
    fun enPassantIsPlayedByTappingTheEmptyDestination() = runTest {
        val start = Game.replay(Position.START_FEN, listOf("e2e4", "a7a6", "e4e5", "d7d5"))
        val vm = viewModel(startGame = start)
        vm.tap("e5")
        vm.tap("d6")
        val pieces = vm.uiState.value.board.pieces
        assertEquals(PieceUi(Side.White, PieceKind.Pawn), pieces[sq("d6")])
        assertNull(pieces[sq("d5")], "the captured pawn is removed")
    }

    @Test
    fun promotionAutoQueensUntilThePickerArrives() = runTest {
        // T-16 replaces this with the promotion picker (S4).
        val vm = viewModel(startGame = Game.start(Position.fromFen("1n2k3/P7/8/8/8/8/8/4K3 w - - 0 1")))
        vm.tap("a7")
        assertEquals(TargetKind.Promotion, vm.uiState.value.board.targets[sq("a8")])
        vm.tap("b8")
        assertEquals(PieceUi(Side.White, PieceKind.Queen), vm.uiState.value.board.pieces[sq("b8")])
        assertEquals("a7b8q", ai.calls.single().moves.last().uci())
    }

    @Test
    fun aMoveThatGivesCheckShowsTheCheckSquare() = runTest {
        val vm = viewModel(startGame = Game.start(Position.fromFen("4k3/8/8/8/8/8/8/R3K3 w - - 0 1")))
        vm.tap("a1")
        vm.tap("a8")
        assertEquals(sq("e8"), vm.uiState.value.board.checkSquare)
        assertEquals(StatusUi.Thinking(check = true), vm.uiState.value.status)
    }

    @Test
    fun accessibilityAndTouchUseTheSameAction() = runTest {
        val vm = viewModel()
        vm.onAction(GameAction.SquareTap(sq("g1")))
        vm.onAction(GameAction.SquareTap(sq("f3")))
        assertEquals(LastMoveUi(sq("g1"), sq("f3")), vm.uiState.value.board.lastMove)
    }

    private fun TestScope.viewModel(startGame: Game = Game.start()) =
        GameViewModel(ai = ai, startGame = startGame, timeSource = testTimeSource).also { runCurrent() }

    private fun GameViewModel.tap(name: String) {
        onAction(GameAction.SquareTap(sq(name)))
        main.dispatcher.scheduler.runCurrent()
    }

    private fun sq(name: String) = Squares.parse(name)
}
