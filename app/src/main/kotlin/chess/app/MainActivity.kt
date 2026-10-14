package chess.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import chess.app.nav.ChessNavHost
import chess.app.ui.theme.ArmChessTheme

/**
 * The single activity (design §6.1): splash, edge-to-edge, theme and the navigation host.
 * Back is never overridden; Navigation Compose handles it, including predictive back.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        // Stub: T-17 keeps the splash up while the save loads, for at most 1.5 s (design §4.2).
        splashScreen.setKeepOnScreenCondition { false }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArmChessTheme {
                ChessNavHost()
            }
        }
    }
}
