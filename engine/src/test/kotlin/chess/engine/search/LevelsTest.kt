package chess.engine.search

import chess.engine.board.Move
import chess.engine.perft.PerftPositions
import chess.engine.rules.Game
import chess.engine.rules.GameStatus
import kotlin.math.ceil
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Levels 1–4 (design ADR-3, US-6): the parameter table, exact root scores, seeded determinism, each
 * level's randomisation rate on real searches, legality and the time caps. The K+Q v K and variety
 * checks at full strength are in [KqkMateTest] and [VarietyTest].
 */
class LevelsTest {
    // --- The table ----------------------------------------------------------------------------

    @Test
    fun paramsMatchTheAdr3Table() {
        assertEquals(
            LevelParams(1, 5_000, 500, Quiescence.Off, Randomisation.RandomMoveOrNoise(randomMoveChance = 0.5, noiseCp = 40)),
            Levels.params(1),
        )
        assertEquals(
            LevelParams(2, 30_000, 1_000, Quiescence.CapturesOnly(maxPlies = 4), Randomisation.PickFromTop(chance = 0.2, count = 5)),
            Levels.params(2),
        )
        assertEquals(LevelParams(6, 250_000, 2_000, Quiescence.Full, Randomisation.WithinMargin(marginCp = 20, decidedCp = 400)), Levels.params(3))
        assertEquals(LevelParams(64, 1_500_000, 4_000, Quiescence.Full, Randomisation.None), Levels.params(4))
        assertEquals(listOf(400L, 900L, 1_900L, 3_900L), Levels.ALL.map { Levels.params(it).deadlineMs })
        assertEquals(1..4, Levels.ALL)
    }

    @Test
    fun levelsOutsideOneToFourAreRejected() {
        for (level in listOf(0, 5, -1, Int.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException>("level $level") { Levels.params(level) }
        }
    }

    @Test
    fun eachLevelSearchesAtLeastWhatTheLevelBelowDoes() {
        // ADR-3: monotonic budgets and caps keep the levels ordered even when a deadline binds.
        for (level in 2..4) {
            val below = Levels.params(level - 1)
            val params = Levels.params(level)
            assertTrue(params.depthCap > below.depthCap, "depth cap, level $level")
            assertTrue(params.nodeBudget > below.nodeBudget, "node budget, level $level")
            assertTrue(params.timeCapMs!! > below.timeCapMs!!, "time cap, level $level")
        }
    }

    @Test
    fun aMatchRunsALevelWithoutTheDeadlineAndNothingElseChanges() {
        for (level in Levels.ALL) {
            val match = Levels.params(level).withoutDeadline()
            assertEquals(null, match.deadlineMs)
            assertEquals(Levels.params(level).copy(timeCapMs = null), match)
        }
    }

    // --- Exact root scores ---------------------------------------------------------------------

    /** Every root score [Search] reports at [depth], by move. */
    private fun rootScores(game: Game, params: LevelParams, random: Random, depth: Int): Pair<SearchResult, Map<Move, Int>> {
        val scores = LinkedHashMap<Move, Int>()
        val search = Search(tt = null)
        search.rootMoveListener = RootMoveListener { d, _, move, score, _ -> if (d == depth) scores[move] = score }
        val result = search.run(game, params, random, System::nanoTime) { false }
        return result to scores
    }

    private val exactPositions = listOf(PerftPositions.KIWIPETE, PerftPositions.POSITION_4, PerftPositions.POSITION_6, PerftPositions.INITIAL) +
        randomGames(count = 8, seed = 909, minPlies = 10, maxPlies = 40).filter { it.position.legalMoves().size > 1 }.take(6).map { it.position.toFen() }

    @Test
    fun theLoweredRootWindowGivesExactScoresToEveryCandidate() {
        // Quiescence is off so that alpha-beta's values do not depend on the window at all (delta
        // pruning in quiescence does); then a score inside the window must equal the full-window score.
        val depth = 3
        val base = fixedDepth(depth, Quiescence.Off)
        val cases = listOf(
            Randomisation.WithinMargin(20, decidedCp = 400) to { best: Int, _: List<Int> -> best - 20 },
            Randomisation.RandomMoveOrNoise(randomMoveChance = 0.0, noiseCp = 40) to { best: Int, _: List<Int> -> best - 80 },
            Randomisation.PickFromTop(chance = 1.0, count = 5) to { _: Int, exact: List<Int> -> exact.sortedDescending().take(5).last() },
        )
        for (fen in exactPositions) {
            val game = gameOf(fen)
            val (_, reference) = rootScores(game, base.copy(randomisation = Randomisation.WithinMargin(1_000_000, decidedCp = Int.MAX_VALUE)), Random(0), depth)
            assertEquals(game.position.legalMoves().toSet(), reference.keys, fen)
            val best = reference.values.max()
            for ((randomisation, threshold) in cases) {
                val (result, scores) = rootScores(game, base.copy(randomisation = randomisation), Random(0), depth)
                val cutoff = threshold(best, reference.values.toList())
                for ((move, score) in scores) {
                    val exact = reference.getValue(move)
                    if (exact >= cutoff) {
                        assertEquals(exact, score, "$fen $randomisation $move: a candidate's score must be exact")
                    } else {
                        assertTrue(score >= exact && score < cutoff, "$fen $randomisation $move: $score must bound $exact and stay below $cutoff")
                    }
                }
                assertTrue(reference.getValue(result.move) >= cutoff, "$fen $randomisation: picked ${result.move}, outside the candidates")
                assertEquals(reference.getValue(result.move), result.scoreCp, "$fen $randomisation: the reported score")
            }
        }
    }

    // --- Determinism -----------------------------------------------------------------------------

    /** The level's parameters with no deadline, and for levels 3 and 4 a small budget so that the default suite stays fast. */
    private fun quick(level: Int, budget: Long = 20_000): LevelParams {
        val params = Levels.params(level).withoutDeadline()
        return if (level >= 3) params.copy(nodeBudget = budget) else params
    }

    /** Self-play from the start position, one engine and one seeded [Random] for the whole game. */
    private fun selfPlay(params: LevelParams, seed: Int, plies: Int): List<SearchResult> {
        val engine = Engine()
        val random = Random(seed)
        var game = Game.start()
        val results = ArrayList<SearchResult>()
        while (results.size < plies && game.status == GameStatus.Ongoing) {
            val result = engine.search(game, params, random)
            results += result.copy(elapsedMs = 0)
            game = game.play(result.move)
        }
        return results
    }

    @Test
    fun theSameSeedPlaysTheSameMovesAtEveryLevel() {
        for (level in Levels.ALL) {
            val params = quick(level, budget = 5_000)
            for (seed in listOf(1, 2)) {
                assertEquals(selfPlay(params, seed, plies = 8), selfPlay(params, seed, plies = 8), "level $level seed $seed")
            }
        }
        // A different seed is a different game at level 1 (the moves are half random).
        assertNotEquals(selfPlay(quick(1), 1, plies = 8).map { it.move }, selfPlay(quick(1), 2, plies = 8).map { it.move })
    }

    @Test
    fun levelFourNeverDeviatesFromTheSearchsBestMove() {
        val params = quick(4, budget = 8_000)
        assertEquals(Randomisation.None, params.randomisation)
        for ((index, game) in randomGames(count = 15, seed = 404, minPlies = 2, maxPlies = 60).withIndex()) {
            val plain = Engine(ttEntriesLog2 = 14).search(game, params, Random(0))
            for (seed in 1..2) {
                val result = Engine(ttEntriesLog2 = 14).search(game, params, Random(seed * 1_000 + index))
                assertEquals(plain.copy(elapsedMs = 0), result.copy(elapsedMs = 0), "position $index seed $seed")
                assertEquals(MoveChoice.Best, result.choice)
            }
        }
    }

    // --- Randomisation rates on real searches ------------------------------------------------------

    @Test
    fun levelOnePlaysAUniformRandomMoveAboutHalfTheTimeAndOtherwiseANoisyBest() {
        val params = Levels.params(1).withoutDeadline()
        val plain = params.copy(randomisation = Randomisation.None)
        // A small table, cleared before every search, so that both searches of a position see the same state.
        val engine = Engine(ttEntriesLog2 = 12)
        var random = 0
        var notBest = 0
        var noisyNotBest = 0
        val games = randomGames(count = 1_000, seed = 101, minPlies = 2, maxPlies = 80)
        for ((index, game) in games.withIndex()) {
            engine.newGame()
            val best = engine.search(game, plain, Random(0))
            engine.newGame()
            val result = engine.search(game, params, Random(index))
            if (best.stopReason == StopReason.OnlyMove) {
                assertEquals(MoveChoice.Best, result.choice)
                continue
            }
            when (result.choice) {
                MoveChoice.RandomMove -> random++
                MoveChoice.NoisyBest -> {
                    // Depth 1 without quiescence: a noisy pick is at most 2 × 40 cp worse than the best.
                    assertTrue(best.scoreCp - result.scoreCp in 0..80, "position $index: ${best.scoreCp} vs ${result.scoreCp}")
                    if (result.move != best.move) noisyNotBest++
                }
                else -> throw AssertionError("position $index: unexpected ${result.choice}")
            }
            if (result.move != best.move) notBest++
        }
        println("T-9 level 1 over ${games.size} decisions: $random uniform random moves, $notBest not the best move ($noisyNotBest of them from noise)")
        assertTrue(random in 400..600, "uniform random moves: $random of 1,000")
        // A random move is rarely the best one, so at least ~45% of moves are not the best; noise adds a few more.
        assertTrue(notBest in 400..750, "moves that are not the best: $notBest of 1,000")
        assertTrue(noisyNotBest > 0, "the noise never changed a move")
    }

    @Test
    fun levelOnesRandomMovesAreUniformOverTheLegalMoves() {
        val params = Levels.params(1).withoutDeadline().copy(randomisation = Randomisation.RandomMoveOrNoise(randomMoveChance = 1.0, noiseCp = 40))
        val game = Game.start()
        val engine = Engine()
        val counts = HashMap<Move, Int>()
        repeat(2_000) { seed ->
            val result = engine.search(game, params, Random(seed))
            assertEquals(MoveChoice.RandomMove, result.choice)
            counts.merge(result.move, 1, Int::plus)
        }
        assertEquals(game.position.legalMoves().toSet(), counts.keys)
        // 100 expected per move (20 moves); a 3.5 sigma band.
        for ((move, count) in counts) assertTrue(count in 65..135, "$move: $count, $counts")
    }

    /** Runs [params] on [game] with the root scores of the final, complete iteration (the budget must not bind). */
    private fun pickWithScores(game: Game, params: LevelParams, seed: Int): Pair<SearchResult, Map<Move, Int>> {
        val scores = HashMap<Int, LinkedHashMap<Move, Int>>()
        val search = Search(TranspositionTable(entriesLog2 = 16))
        search.rootMoveListener = RootMoveListener { d, _, move, score, _ -> scores.getOrPut(d) { LinkedHashMap() }[move] = score }
        val result = search.run(game, params, Random(seed), System::nanoTime) { false }
        assertTrue(result.stopReason == StopReason.DepthCap || result.stopReason == StopReason.OnlyMove, "${result.stopReason}")
        return result to (scores[result.depth] ?: emptyMap())
    }

    @Test
    fun levelTwoPicksFromTheTopFiveAboutOneMoveInFive() {
        val params = Levels.params(2).withoutDeadline().copy(nodeBudget = Long.MAX_VALUE)
        var fromTop = 0
        var decisions = 0
        for ((index, game) in randomGames(count = 400, seed = 202, minPlies = 2, maxPlies = 60).withIndex()) {
            val (result, scores) = pickWithScores(game, params, seed = index)
            if (result.stopReason == StopReason.OnlyMove) continue
            decisions++
            val ranked = scores.values.sortedDescending()
            val best = ranked.first()
            when (result.choice) {
                MoveChoice.Best -> assertEquals(best, result.scoreCp, "position $index")
                MoveChoice.FromTop -> {
                    fromTop++
                    val fifth = ranked.take(5).last()
                    assertTrue(scores.getValue(result.move) >= fifth, "position $index: ${result.move} is not in the top 5 of $scores")
                }
                else -> throw AssertionError("position $index: unexpected ${result.choice}")
            }
        }
        println("T-9 level 2: $fromTop of $decisions decisions picked from the top 5")
        // 20% expected; sd is about 2% at 400 decisions, so a ±6 point band is about 3 sigma.
        assertTrue(fromTop.toDouble() / decisions in 0.14..0.26, "$fromTop of $decisions")
    }

    @Test
    fun levelThreePicksOnlyMovesWithinTwentyCentipawnsOfTheBest() {
        val params = Levels.params(3).withoutDeadline().copy(depthCap = 3, nodeBudget = Long.MAX_VALUE)
        var notBest = 0
        var decisions = 0
        for ((index, game) in randomGames(count = 50, seed = 303, minPlies = 2, maxPlies = 60).withIndex()) {
            val (result, scores) = pickWithScores(game, params, seed = index)
            if (result.stopReason == StopReason.OnlyMove) continue
            decisions++
            val best = scores.values.max()
            assertTrue(result.choice == MoveChoice.WithinMargin || result.choice == MoveChoice.Best, "${result.choice}")
            assertEquals(scores.getValue(result.move), result.scoreCp, "position $index")
            assertTrue(best - result.scoreCp in 0..20, "position $index: ${result.scoreCp} vs best $best")
            if (result.scoreCp != best) notBest++
        }
        println("T-9 level 3: $notBest of $decisions decisions played a move below the best score (within 20 cp)")
        assertTrue(notBest > 0, "level 3 never deviated from the best score")
    }

    // --- Legality ---------------------------------------------------------------------------------

    @Test
    fun everyLevelOnlyEverPlaysLegalMoves() {
        for (level in Levels.ALL) {
            val params = quick(level, budget = 3_000)
            val engine = Engine()
            val random = Random(level)
            var game = Game.start()
            repeat(if (level <= 2) 300 else 150) { ply ->
                if (game.status != GameStatus.Ongoing) {
                    game = Game.start()
                    engine.newGame()
                }
                val result = engine.search(game, params, random)
                assertNotEquals(Move.NONE, result.move)
                assertTrue(game.position.isLegal(result.move), "level $level ply $ply: ${result.move} in ${game.position.toFen()}")
                game = game.play(result.move)
            }
        }
    }

    @Test
    fun anOnlyMoveOrACancelledSearchIsNeverRandomised() {
        val only = gameOf("7k/8/8/8/8/8/6P1/r6K w - - 0 1")
        for (level in Levels.ALL) {
            val random = Random(1)
            val result = Engine().search(only, Levels.params(level), random)
            assertEquals(StopReason.OnlyMove, result.stopReason)
            assertEquals(MoveChoice.Best, result.choice)
            assertEquals(Random(1).nextLong(), random.nextLong(), "level $level drew from the random source for an only move")
        }
        // `shouldStop` is first polled at 1,024 nodes, so a search shorter than that is never cancelled:
        // level 1 always, and level 2 here when it draws the normal window. Several seeds make sure that
        // level 2's top-5 draws (a wider window, so more nodes) are among the cancelled searches.
        val games = listOf(PerftPositions.KIWIPETE, PerftPositions.POSITION_4, PerftPositions.POSITION_5).map(::gameOf)
        for (level in 2..4) {
            var cancelled = 0
            for (seed in 0 until 10) {
                for (game in games) {
                    val result = Engine(ttEntriesLog2 = 14).search(game, Levels.params(level), Random(seed)) { true }
                    assertTrue(game.position.isLegal(result.move))
                    if (result.stopReason == StopReason.Cancelled) {
                        cancelled++
                        assertEquals(MoveChoice.Best, result.choice, "level $level seed $seed")
                    }
                }
            }
            assertTrue(cancelled >= if (level == 2) 2 else 30, "level $level: only $cancelled of 30 searches were cancelled")
        }
    }

    // --- Time caps ---------------------------------------------------------------------------------

    /**
     * A clock driven by the search's own node count: every stop check polls `shouldStop` once per
     * [Engine.CHECK_INTERVAL] nodes, and the clock moves on [microsPerNode] for each node since.
     */
    private class NodeClock(private val microsPerNode: Double) {
        private var polls = 0L
        val shouldStop: () -> Boolean = { polls++; false }
        val nanoTime: () -> Long = { (polls * Engine.CHECK_INTERVAL * microsPerNode * 1_000).toLong() }

        /** One check interval of this clock, in whole milliseconds rounded up. */
        val intervalMs: Long get() = ceil(Engine.CHECK_INTERVAL * microsPerNode / 1_000).toLong()
    }

    private val timedPositions = listOf(PerftPositions.INITIAL, PerftPositions.KIWIPETE, PerftPositions.POSITION_4, PerftPositions.POSITION_6)

    private fun assertStopsInTime(level: Int, fen: String, microsPerNode: Double): SearchResult {
        val params = Levels.params(level)
        val deadline = params.deadlineMs!!
        val game = gameOf(fen)
        val clock = NodeClock(microsPerNode)
        val result = Engine().search(game, params, Random(0), clock.nanoTime, clock.shouldStop)
        val context = "level $level, $microsPerNode µs/node, $fen: ${result.stopReason} after ${result.elapsedMs} ms, ${result.nodes} nodes, depth ${result.depth}"
        assertTrue(result.elapsedMs <= deadline + clock.intervalMs, context)
        assertTrue(game.position.isLegal(result.move), context)
        if (result.stopReason == StopReason.Deadline) assertTrue(result.elapsedMs >= deadline, context)
        return result
    }

    @Test
    fun everyLevelStopsByItsDeadlinePlusOneCheckInterval() {
        for (level in Levels.ALL) {
            val params = Levels.params(level)
            // A slow device, on which the node budget would take 10 × the cap: the deadline binds first.
            val slow = 10.0 * params.timeCapMs!! * 1_000 / params.nodeBudget
            for (fen in timedPositions) assertStopsInTime(level, fen, slow)

            // The plan's clock, 1 ms per 1,000 nodes. On it every level's node budget runs out before
            // its deadline (the budgets are meant to fit the caps), so level 4, whose 1.5 M nodes take
            // a second of real time, is only checked on the slow clock above.
            assertTrue(params.nodeBudget / 1_000 < params.deadlineMs!!, "level $level")
            if (level < 4) assertStopsInTime(level, PerftPositions.KIWIPETE, microsPerNode = 1.0)
        }
    }

    @Test
    fun onASlowDeviceTheDeadlineIsWhatStopsLevelsThreeAndFour() {
        for (level in 3..4) {
            val params = Levels.params(level)
            val clock = NodeClock(10.0 * params.timeCapMs!! * 1_000 / params.nodeBudget)
            val result = Engine().search(gameOf(PerftPositions.KIWIPETE), params, Random(0), clock.nanoTime, clock.shouldStop)
            assertEquals(StopReason.Deadline, result.stopReason, "level $level")
            assertTrue(result.elapsedMs in params.deadlineMs!!..params.deadlineMs!! + clock.intervalMs, "level $level: ${result.elapsedMs} ms")
            assertTrue(result.nodes < params.nodeBudget / 5, "level $level: ${result.nodes} nodes")
        }
    }
}
