package chess.engine

/**
 * JUnit category marker for the longer search speed benchmark (T-8). The default `:engine:test`
 * excludes it; `:engine:searchBench` runs only it (see `engine/build.gradle.kts`).
 */
interface SearchBench
