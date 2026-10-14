package chess.engine

/**
 * JUnit category marker for the longer level checks (T-9): K+Q v K conversion and variety at the
 * real level parameters. The default `:engine:test` excludes it; `:engine:levelChecks` runs only it
 * (see `engine/build.gradle.kts`).
 */
interface LevelChecks
