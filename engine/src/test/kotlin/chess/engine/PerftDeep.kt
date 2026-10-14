package chess.engine

/**
 * JUnit category marker for the release-depth perft runs (design §4.2). The default `:engine:test`
 * excludes it; `:engine:perftDeep` runs only it (see `engine/build.gradle.kts`).
 */
interface PerftDeep
