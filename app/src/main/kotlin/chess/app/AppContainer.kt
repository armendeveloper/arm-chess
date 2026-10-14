package chess.app

import chess.app.ai.AiPlayer
import chess.app.ai.RandomMoveAiPlayer
import kotlin.random.Random
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Dispatchers are injected so tests can swap in a `StandardTestDispatcher` (design §14). */
data class AppDispatchers(
    val main: CoroutineDispatcher = Dispatchers.Main,
    val default: CoroutineDispatcher = Dispatchers.Default,
    val io: CoroutineDispatcher = Dispatchers.IO,
)

/**
 * Manual dependency container (ADR-7). One instance per process, owned by [ChessApplication].
 *
 * Later tasks add, all built from here:
 * - T-17: the save `DataStore` and `GameRepository`
 * - T-18: the stats `DataStore`, `StatsRepository` and `NewGameCoordinator`
 * - T-19: `EngineAiPlayer` in place of T-15's random-move computer (same dispatcher and seam)
 */
class AppContainer(
    val dispatchers: AppDispatchers = AppDispatchers(),
) {
    /**
     * App-lifetime scope for writes that must outlive a screen, e.g. saving straight after a move
     * and then pressing Back (design §5.1). A failed child does not cancel its siblings.
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatchers.io)

    /** Measures the computer's think time against the 400 ms minimum (design US-6). */
    val timeSource: TimeSource = TimeSource.Monotonic

    /**
     * A computer opponent for one Game screen. It chooses moves on [AppDispatchers.default], never
     * on the main thread (tech design §6.5).
     *
     * TEMPORARY (T-15): a random-move player. T-19 returns `EngineAiPlayer` here.
     */
    fun newAiPlayer(): AiPlayer = RandomMoveAiPlayer(dispatchers.default, Random.Default)
}
