package chess.engine.search

import chess.engine.board.Move
import chess.engine.perft.PerftPositions
import chess.engine.rules.Game
import chess.engine.rules.GameStatus
import chess.engine.rules.play
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EngineTest {
    private fun assertLegal(game: Game, result: SearchResult, context: String = "") {
        assertNotEquals(Move.NONE, result.move, "Move.NONE $context")
        assertTrue(game.position.isLegal(result.move), "${result.move} is not legal in ${game.position.toFen()} $context")
    }

    @Test
    fun aFinishedGameIsRejected() {
        val mated = Game.start().play("f2f3", "e7e5", "g2g4", "d8h4")
        assertTrue(mated.status is GameStatus.Checkmate)
        assertFailsWith<IllegalArgumentException> { Engine().search(mated, budget(1_000), Random(0)) }
        val stalemate = gameOf("k7/8/1Q6/8/8/8/8/K7 b - - 0 1")
        assertEquals(GameStatus.Stalemate, stalemate.status)
        assertFailsWith<IllegalArgumentException> { Engine().search(stalemate, budget(1_000), Random(0)) }
        val repeated = Game.start().play("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8")
        assertEquals(GameStatus.ThreefoldRepetition, repeated.status)
        assertFailsWith<IllegalArgumentException> { Engine().search(repeated, budget(1_000), Random(0)) }
    }

    @Test
    fun anOnlyMoveIsPlayedWithoutSearching() {
        // The rook on a1 checks the king on h1, and Kh2 is the only way out.
        val game = gameOf("7k/8/8/8/8/8/6P1/r6K w - - 0 1")
        assertEquals(1, game.position.legalMoves().size)
        val result = searchFresh(game, budget(100_000))
        assertEquals(game.position.legalMoves().single(), result.move)
        assertEquals(StopReason.OnlyMove, result.stopReason)
        assertEquals(0, result.depth)
        assertTrue(result.nodes <= 1, "${result.nodes}")
    }

    @Test
    fun aFixedDepthSearchStopsAtTheDepthCap() {
        val result = searchFresh(Game.start(), fixedDepth(3))
        assertEquals(StopReason.DepthCap, result.stopReason)
        assertEquals(3, result.depth)
        assertLegal(Game.start(), result)
    }

    @Test
    fun theNodeBudgetIsRespectedWithinOneCheckInterval() {
        for (fen in listOf(PerftPositions.INITIAL, PerftPositions.KIWIPETE, PerftPositions.POSITION_6)) {
            val game = gameOf(fen)
            val result = searchFresh(game, budget(20_000))
            assertEquals(StopReason.NodeBudget, result.stopReason, fen)
            assertTrue(result.nodes in 20_000L until 20_000L + 1_024, "$fen: ${result.nodes} nodes")
            assertTrue(result.depth >= 2, "$fen: depth ${result.depth}")
            assertLegal(game, result, fen)
        }
    }

    @Test
    fun shouldStopIsHonouredWithinOneCheckInterval() {
        val game = gameOf(PerftPositions.KIWIPETE)
        var calls = 0
        val result = searchFresh(game, budget(Long.MAX_VALUE / 2)) { ++calls >= 5 }
        assertEquals(StopReason.Cancelled, result.stopReason)
        assertEquals(5, calls, "the search stops at the first check that sees the flag")
        // Checks happen every 1,024 nodes, and nothing is searched after the flag is seen.
        assertTrue(result.nodes in 4L * 1_024 + 1..5L * 1_024, "${result.nodes} nodes")
        assertLegal(game, result)
    }

    @Test
    fun cancellingDuringDepthOneStillReturnsALegalMove() {
        for (fen in PerftPositions.ALL) {
            val game = gameOf(fen)
            val result = searchFresh(game, budget(Long.MAX_VALUE / 2)) { true }
            assertEquals(StopReason.Cancelled, result.stopReason, fen)
            assertTrue(result.nodes <= 1_024, "$fen: ${result.nodes}")
            assertLegal(game, result, fen)
        }
    }

    @Test
    fun aDeadlineAlreadyPassedStillCompletesDepthOne() {
        for (fen in PerftPositions.ALL) {
            val game = gameOf(fen)
            var calls = 0
            // The first reading is the start; every later reading is an hour on.
            val clock = { if (calls++ == 0) 0L else 3_600_000_000_000L }
            val params = LevelParams(depthCap = 64, nodeBudget = 1_000_000, timeCapMs = 500, quiescence = Quiescence.Full)
            val result = searchFresh(game, params, nanoTime = clock)
            assertEquals(StopReason.Deadline, result.stopReason, fen)
            assertEquals(1, result.depth, fen)
            assertLegal(game, result, fen)
            assertEquals(searchFresh(game, fixedDepth(1)).move, result.move, "$fen: the depth-1 move")
        }
    }

    /**
     * A position whose depth-1 search alone takes several stop-check intervals (about 6,300 nodes:
     * many captures to resolve). Found by random play; the precondition below re-checks it.
     */
    private val heavyDepthOne: Game = gameOf("r1b5/1pp3k1/p1n1p2p/P2p1pPn/2PPPN1p/qP2BP2/2R2b2/1N1K1B1R w - - 0 31")

    @Test
    fun depthOneCompletesEvenWhenItsOwnStopChecksSeeTheLimitsPassed() {
        val game = heavyDepthOne
        val depthOne = searchFresh(game, fixedDepth(1))
        assertTrue(depthOne.nodes > 2L * Engine.CHECK_INTERVAL, "depth 1 takes only ${depthOne.nodes} nodes")

        val pastBudget = searchFresh(game, budget(1))
        assertEquals(StopReason.NodeBudget, pastBudget.stopReason)
        assertEquals(1, pastBudget.depth)
        assertEquals(depthOne.move, pastBudget.move)
        assertEquals(depthOne.nodes, pastBudget.nodes, "nothing searched after depth 1")

        var calls = 0
        val pastDeadline = searchFresh(game, LevelParams(64, Long.MAX_VALUE / 2, 500, Quiescence.Full), nanoTime = { if (calls++ == 0) 0L else 3_600_000_000_000L })
        assertEquals(StopReason.Deadline, pastDeadline.stopReason)
        assertEquals(1, pastDeadline.depth)
        assertEquals(depthOne.move, pastDeadline.move)
    }

    @Test
    fun theDeadlineIsTheTimeCapMinusTheMargin() {
        val params = LevelParams(depthCap = 64, nodeBudget = Long.MAX_VALUE / 2, timeCapMs = 300, quiescence = Quiescence.Full)
        assertEquals(200L, params.deadlineMs)
        assertEquals(null, params.withoutDeadline().deadlineMs)

        // A fake clock that advances 1 ms per reading: the search reads it once per 1,024 nodes.
        var now = 0L
        val game = gameOf(PerftPositions.KIWIPETE)
        val result = searchFresh(game, params, nanoTime = { now.also { now += 1_000_000 } })
        assertEquals(StopReason.Deadline, result.stopReason)
        assertTrue(result.elapsedMs in 200L..202L, "elapsed ${result.elapsedMs} ms")
        assertTrue(result.depth >= 2, "depth ${result.depth}")
        assertLegal(game, result)
    }

    @Test
    fun levelParamsRejectNonsense() {
        assertFailsWith<IllegalArgumentException> { LevelParams(0, 1_000, null, Quiescence.Full) }
        assertFailsWith<IllegalArgumentException> { LevelParams(LevelParams.MAX_DEPTH + 1, 1_000, null, Quiescence.Full) }
        assertFailsWith<IllegalArgumentException> { LevelParams(4, 0, null, Quiescence.Full) }
        assertFailsWith<IllegalArgumentException> { LevelParams(4, 1_000, 100, Quiescence.Full) }
        assertFailsWith<IllegalArgumentException> { Quiescence.CapturesOnly(maxPlies = 0) }
    }

    @Test
    fun everyReturnedMoveIsLegalAcrossFiveHundredRandomPositions() {
        val engine = Engine()
        val params = LevelParams(depthCap = 3, nodeBudget = 1_000, timeCapMs = null, quiescence = Quiescence.Full)
        for ((index, game) in randomGames(count = 500, seed = 500, minPlies = 1, maxPlies = 120).withIndex()) {
            if (index % 50 == 0) engine.newGame()
            val result = engine.search(game, params, Random(index))
            assertLegal(game, result, "position $index")
        }
    }

    @Test
    fun logsJvmNodesPerSecond() {
        val positions = listOf(PerftPositions.INITIAL, PerftPositions.KIWIPETE, PerftPositions.POSITION_4, PerftPositions.POSITION_6)
        val engine = Engine()
        engine.search(gameOf(PerftPositions.POSITION_5), budget(200_000), Random(0)) // JIT warm-up
        var nodes = 0L
        val start = System.nanoTime()
        for (fen in positions) {
            engine.newGame()
            nodes += engine.search(gameOf(fen), budget(250_000), Random(0)).nodes
        }
        val seconds = (System.nanoTime() - start) / 1e9
        val nps = (nodes / seconds).toLong()
        println("T-8 JVM search speed: %,d nodes in %.2f s = %,d nodes/s".format(nodes, seconds, nps))
        assertTrue(nps > 0)
    }
}
