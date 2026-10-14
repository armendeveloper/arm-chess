package chess.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import chess.app.ui.about.AboutScreen
import chess.app.ui.about.LicencesScreen
import chess.app.ui.game.GameScreen
import chess.app.ui.home.HomeScreen
import chess.app.ui.stats.StatsScreen

/**
 * The app's navigation graph (design §3.1). System back is never intercepted here; Navigation
 * Compose handles it, including predictive back (design §3.2).
 *
 * Every navigation callback is wrapped in [dropUnlessResumed], so a double tap during a transition
 * cannot push a screen twice or pop past Home.
 */
@Composable
fun ChessNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Route.Home,
        modifier = modifier,
    ) {
        composable<Route.Home> {
            HomeScreen(
                // Placeholder until T-18: New game opens the New game sheet (S2), whose Play goes to Game.
                onNewGame = dropUnlessResumed { navController.navigate(Route.Game) },
                onStats = dropUnlessResumed { navController.navigate(Route.Stats) },
                onAbout = dropUnlessResumed { navController.navigate(Route.About) },
            )
        }
        composable<Route.Game> {
            GameScreen(onHome = dropUnlessResumed { navController.popBackStack() })
        }
        composable<Route.Stats> {
            StatsScreen(onBack = dropUnlessResumed { navController.popBackStack() })
        }
        composable<Route.About> {
            AboutScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onLicences = dropUnlessResumed { navController.navigate(Route.Licences) },
            )
        }
        composable<Route.Licences> {
            LicencesScreen(onBack = dropUnlessResumed { navController.popBackStack() })
        }
    }
}
