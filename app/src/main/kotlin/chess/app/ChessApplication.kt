package chess.app

import android.app.Application
import android.os.StrictMode

class ChessApplication : Application() {

    /** The process-wide dependency container (ADR-7). */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableStrictMode()
        container = AppContainer()
    }

    /** Debug only: log accidental main-thread disk I/O and slow calls (design §12). */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build(),
        )
    }
}
