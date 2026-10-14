package chess.engine.search

import chess.engine.SearchBench
import chess.engine.perft.PerftPositions
import org.junit.experimental.categories.Category
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Search speed on the JVM at level 4's provisional budget (1.5 M nodes, design ADR-3), for the plan's
 * Results table and the T-10 match-time estimate. Run with `./gradlew :engine:searchBench`.
 */
@Category(SearchBench::class)
class SearchBenchTest {
    private val positions = listOf(
        "start" to PerftPositions.INITIAL,
        "Kiwipete" to PerftPositions.KIWIPETE,
        "position 3" to PerftPositions.POSITION_3,
        "position 4" to PerftPositions.POSITION_4,
        "position 5" to PerftPositions.POSITION_5,
        "position 6" to PerftPositions.POSITION_6,
        "Italian" to "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 1 5",
        "K+P endgame" to "8/5pk1/6p1/8/3P4/2K5/8/8 w - - 0 1",
    )

    @Test
    fun levelFourBudgetNodesPerSecond() {
        val engine = Engine()
        repeat(3) { engine.search(gameOf(PerftPositions.POSITION_6), budget(500_000), Random(0)) } // JIT warm-up
        var totalNodes = 0L
        var totalNanos = 0L
        val lines = ArrayList<String>()
        for ((name, fen) in positions) {
            engine.newGame()
            val start = System.nanoTime()
            val result = engine.search(gameOf(fen), budget(1_500_000), Random(0))
            val nanos = System.nanoTime() - start
            totalNodes += result.nodes
            totalNanos += nanos
            lines += "%-12s %,10d nodes  %6.2f s  %,10d nodes/s  depth %2d  %s %d".format(
                name, result.nodes, nanos / 1e9, (result.nodes * 1e9 / nanos).toLong(), result.depth, result.move, result.scoreCp,
            )
        }
        lines.forEach(::println)
        println("T-8 searchBench: %,d nodes in %.2f s = %,d nodes/s".format(totalNodes, totalNanos / 1e9, (totalNodes * 1e9 / totalNanos).toLong()))
        assertTrue(totalNodes > 0)
    }
}
