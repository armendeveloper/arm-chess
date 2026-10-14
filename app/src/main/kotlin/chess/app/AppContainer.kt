package chess.app

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
 * - T-19: the AI dispatcher, `Random` and the time source for `EngineAiPlayer`
 */
class AppContainer(
    val dispatchers: AppDispatchers = AppDispatchers(),
) {
    /**
     * App-lifetime scope for writes that must outlive a screen, e.g. saving straight after a move
     * and then pressing Back (design §5.1). A failed child does not cancel its siblings.
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatchers.io)
}
