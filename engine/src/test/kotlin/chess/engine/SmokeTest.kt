package chess.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/** Proves the plain-JVM test runner for :engine works (T-1). */
class SmokeTest {
    @Test
    fun engineModuleIsOnTheTestClasspath() {
        assertEquals("chess.engine", Smoke.MODULE_PACKAGE)
    }
}
