package chess.app.nav

import kotlinx.serialization.Serializable

/**
 * Type-safe Navigation Compose routes (design §3.1, ADR-9). The New game sheet, the promotion
 * picker and the confirmation dialogs are screen state owned by ViewModels, not routes.
 */
sealed interface Route {
    @Serializable
    data object Home : Route

    @Serializable
    data object Game : Route

    @Serializable
    data object Stats : Route

    @Serializable
    data object About : Route

    @Serializable
    data object Licences : Route
}
