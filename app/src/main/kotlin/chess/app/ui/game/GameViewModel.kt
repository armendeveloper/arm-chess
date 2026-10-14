package chess.app.ui.game

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import chess.app.ChessApplication
import chess.app.ai.AiPlayer
import chess.engine.board.Move
import chess.engine.board.Piece
import chess.engine.board.PieceType
import chess.engine.rules.Game
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Game screen's state holder (tech design §6.1, §6.3, §6.5).
 *
 * It owns the engine [Game] privately and publishes [uiState], derived by the pure [GameUiMapper].
 * The screen sends [GameAction]s to [onAction].
 *
 * - **Tap to move** (design §4.3.3, US-3): select an own piece with legal moves, move the selection
 *   to another own piece, tap the selected piece to deselect, tap a target to move, and any other
 *   tap deselects silently. An own piece with no legal moves selects nothing and shows a 2.5 s hint.
 *   While the computer is thinking or the game is over, taps do nothing.
 * - **The computer's turn** (§6.5, US-6): one `aiJob` asks [ai] for a move. [ai] works on its own
 *   dispatcher, so the main thread stays free. The move is applied no earlier than [MIN_AI_DISPLAY]
 *   after the turn began, and only if the game has not changed since ([generation]). Clearing the
 *   ViewModel (leaving the screen) cancels the search with [viewModelScope].
 *
 * T-15 starts every game in memory from [startGame] (a new game as White at level 2, from Home's
 * **New game**). T-17 loads the saved game instead, and T-18 takes the side and level from the New
 * game sheet.
 *
 * @param timeSource measures the computer's think time; tests pass the virtual-time source.
 * @param onAiError reports a failed search; the default logs it. No user data is logged.
 */
class GameViewModel(
    private val ai: AiPlayer,
    private val player: Side = Side.White,
    private val level: Int = DEFAULT_LEVEL,
    startGame: Game = Game.start(),
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val onAiError: (Throwable) -> Unit = { Log.e(TAG, "The computer couldn't choose a move", it) },
) : ViewModel() {

    init {
        require(level in 1..4) { "Level must be 1..4, was $level" }
    }

    private var game: Game = startGame
    private var selected: Int? = null
    private var hint = false
    private var engineError = false

    /** Incremented on every change to [game], so a search started for an older game is never applied. */
    private var generation = 0
    private var aiJob: Job? = null
    private var hintJob: Job? = null

    private val _uiState = MutableStateFlow(snapshot())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    init {
        startAiTurnIfNeeded()
    }

    fun onAction(action: GameAction) {
        when (action) {
            is GameAction.SquareTap -> onSquareTap(action.square)
            GameAction.RetryEngine -> onRetryEngine()
        }
    }

    private fun onSquareTap(square: Int) {
        require(square in 0..63) { "Square must be in 0..63, was $square" }
        // The computer's turn or a finished game: the board ignores input (design US-3 branches).
        if (!GameUiMapper.isPlayersTurn(game, player)) return
        clearHint()

        val current = selected
        val targetMoves = current?.let { from -> game.legalMovesFrom(from).filter { it.to == square } }.orEmpty()
        when {
            targetMoves.isNotEmpty() -> {
                playerMove(targetMoves)
                return
            }
            square == current -> selected = null
            isOwnPiece(square) -> {
                if (game.legalMovesFrom(square).isNotEmpty()) {
                    selected = square
                } else {
                    selected = null
                    showHint()
                }
            }
            else -> selected = null
        }
        publish()
    }

    /** [moves] are the legal moves from the selected square to the tapped one: one, or four promotions. */
    private fun playerMove(moves: List<Move>) {
        val move = moves.singleOrNull() ?: choosePromotion(moves)
        applyMove(move)
    }

    /**
     * TEMPORARY (T-15): always promotes to a queen. T-16 replaces this with the promotion picker:
     * `Overlay.Promotion(from, to)`, then `GameAction.Promote(type)` plays the chosen move.
     */
    private fun choosePromotion(moves: List<Move>): Move =
        moves.first { it.promotionType == PieceType.QUEEN }

    private fun applyMove(move: Move) {
        game = game.play(move)
        generation++
        selected = null
        engineError = false
        publish()
        // T-17 saves here, after every applied move.
        startAiTurnIfNeeded()
    }

    private fun startAiTurnIfNeeded() {
        if (game.status.isOver || GameUiMapper.isPlayersTurn(game, player)) return
        val gen = generation
        val searched = game
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            val mark = timeSource.markNow()
            val move = try {
                ai.chooseMove(searched, level)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onAiError(e)
                engineError = true
                publish()
                return@launch
            }
            val remaining = MIN_AI_DISPLAY - mark.elapsedNow()
            if (remaining.isPositive()) delay(remaining)
            // T-19: hold the move here while a confirmation dialog or the New game sheet is open.
            if (gen == generation) applyMove(move)
        }
    }

    private fun onRetryEngine() {
        if (!engineError) return
        engineError = false
        publish()
        startAiTurnIfNeeded()
    }

    private fun showHint() {
        hint = true
        hintJob = viewModelScope.launch {
            delay(HINT_DURATION)
            hint = false
            publish()
        }
    }

    private fun clearHint() {
        hintJob?.cancel()
        hintJob = null
        hint = false
    }

    private fun isOwnPiece(square: Int): Boolean {
        val piece = game.position.pieceAt(square)
        return piece != Piece.NONE && GameUiMapper.side(Piece.color(piece)) == player
    }

    private fun publish() {
        _uiState.value = snapshot()
    }

    private fun snapshot(): GameUiState =
        GameUiMapper.map(game, player, level, selected, hint, engineError)

    companion object {
        private const val TAG = "GameViewModel"

        /** T-15's in-memory new game is at level 2 (Casual), the first-run default (design §4.4). */
        const val DEFAULT_LEVEL = 2

        /** The computer's move is never shown sooner than this after its turn began (design US-6). */
        val MIN_AI_DISPLAY: Duration = 400.milliseconds

        /** How long "No legal moves for that piece" stays in the status row (design §4.3.4). */
        val HINT_DURATION: Duration = 2_500.milliseconds

        /** Builds the ViewModel from the process's [chess.app.AppContainer] (ADR-7). */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ChessApplication).container
                GameViewModel(ai = container.newAiPlayer(), timeSource = container.timeSource)
            }
        }
    }
}
