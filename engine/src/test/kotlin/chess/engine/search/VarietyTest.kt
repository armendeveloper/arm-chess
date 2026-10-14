package chess.engine.search

import chess.engine.LevelChecks
import chess.engine.board.Move
import org.junit.experimental.categories.Category
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * US-6 variety (design §4.7): 5 fixed middlegame positions × 10 seeds at the real level parameters
 * (deadline off). Levels 1–3 each choose at least 2 different moves in at least one position;
 * level 4 has no randomisation and always plays the same move. Run with `./gradlew :engine:levelChecks`.
 */
@Category(LevelChecks::class)
class VarietyTest {
    /** Distinct moves chosen in each position over [seeds], each search by a fresh engine as at the start of a game. */
    private fun distinctMoves(level: Int, seeds: IntRange = 1..10): List<Set<Move>> =
        POSITIONS.map { fen ->
            val game = gameOf(fen)
            seeds.map { seed -> Engine().search(game, Levels.params(level).withoutDeadline(), Random(seed)).move }.toSet()
        }

    @Test
    fun levelsOneToThreeChooseDifferentMovesAcrossSeeds() {
        for (level in 1..3) {
            val distinct = distinctMoves(level)
            println("T-9 variety, level $level: distinct moves per position ${distinct.map { it.size }}")
            assertTrue(distinct.any { it.size >= 2 }, "level $level chose one move per position: $distinct")
        }
    }

    @Test
    fun levelFourAlwaysChoosesTheSameMove() {
        val distinct = distinctMoves(level = 4, seeds = 1..2)
        println("T-9 variety, level 4: distinct moves per position ${distinct.map { it.size }}")
        for ((index, moves) in distinct.withIndex()) assertEquals(1, moves.size, "position ${index + 1}: $moves")
    }

    companion object {
        /** Quiet, balanced middlegames with several reasonable moves each. */
        val POSITIONS = listOf(
            // Italian Game, after 5.d3.
            "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/2NP1N2/PPP2PPP/R1BQK2R b KQkq - 0 5",
            // Queen's Gambit Declined, after 4.Bg5 Be7 5.e3.
            "rnbqk2r/ppp1bppp/4pn2/3p2B1/2PP4/2N1P3/PP3PPP/R2QKBNR b KQkq - 0 5",
            // Sicilian, Najdorf after 6.Be2 e5.
            "rnbqkb1r/1p3ppp/p2p1n2/4p3/3NP3/2N5/PPP1BPPP/R1BQK2R w KQkq - 0 7",
            // Ruy Lopez, closed, after 9.h3.
            "r1bq1rk1/2p1bppp/p1np1n2/1p2p3/4P3/1BP2N1P/PP1P1PP1/RNBQR1K1 b - - 0 9",
            // King's Indian, classical set-up with ...Nc6; White to move.
            "r1bq1rk1/ppp1ppbp/2np1np1/8/2PPP3/2N2N2/PP2BPPP/R1BQ1RK1 w - - 3 7",
        )
    }
}
