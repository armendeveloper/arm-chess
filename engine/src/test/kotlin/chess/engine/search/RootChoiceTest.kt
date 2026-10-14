package chess.engine.search

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The level randomisation rules on made-up root scores (design ADR-3): what is drawn before the
 * search, the root window that keeps the candidates' scores exact, and the pick after the search.
 */
class RootChoiceTest {
    private val levelOne = Randomisation.RandomMoveOrNoise(randomMoveChance = 0.5, noiseCp = 40)
    private val levelTwo = Randomisation.PickFromTop(chance = 0.2, count = 5)
    private val levelThree = Randomisation.WithinMargin(marginCp = 20, decidedCp = 400)

    private fun draws(randomisation: Randomisation, n: Int, seed: Int = 1): Map<MoveChoice, Int> {
        val random = Random(seed)
        return (0 until n).map { RootChoice.draw(randomisation, random).planned }.groupingBy { it }.eachCount()
    }

    // --- The draw before the search ---------------------------------------------------------

    @Test
    fun noRandomisationAlwaysPlaysTheBestMoveAndDrawsNothing() {
        val random = Random(7)
        repeat(100) { assertEquals(MoveChoice.Best, RootChoice.draw(Randomisation.None, random).planned) }
        assertEquals(Random(7).nextLong(), random.nextLong(), "None must not consume the random source")
    }

    @Test
    fun levelOneDrawsAUniformRandomMoveHalfTheTimeAndNoiseOtherwise() {
        val counts = draws(levelOne, 10_000)
        assertEquals(setOf(MoveChoice.RandomMove, MoveChoice.NoisyBest), counts.keys)
        assertTrue(counts.getValue(MoveChoice.RandomMove) in 4_700..5_300, "$counts")

        assertEquals(setOf(MoveChoice.NoisyBest), draws(levelOne.copy(randomMoveChance = 0.0), 1_000).keys)
        assertEquals(setOf(MoveChoice.RandomMove), draws(levelOne.copy(randomMoveChance = 1.0), 1_000).keys)
    }

    @Test
    fun levelTwoPicksFromTheTopOneTimeInFive() {
        val counts = draws(levelTwo, 10_000)
        assertEquals(setOf(MoveChoice.FromTop, MoveChoice.Best), counts.keys)
        assertTrue(counts.getValue(MoveChoice.FromTop) in 1_800..2_200, "$counts")
    }

    @Test
    fun levelThreeAlwaysPicksWithinTheMargin() {
        assertEquals(setOf(MoveChoice.WithinMargin), draws(levelThree, 1_000).keys)
    }

    // --- The root window ----------------------------------------------------------------------

    private fun alphas(choice: RootChoice, scores: List<Int>): List<Int> {
        choice.newIteration()
        return scores.map { score -> choice.alpha().also { choice.add(score) } } + choice.alpha()
    }

    private fun planned(randomisation: Randomisation, kind: MoveChoice): RootChoice {
        val random = Random(3)
        repeat(1_000) {
            val choice = RootChoice.draw(randomisation, random)
            if (choice.planned == kind) return choice
        }
        throw AssertionError("$randomisation never drew $kind")
    }

    @Test
    fun theBestMoveAndAUniformRandomMoveUseTheNormalWindow() {
        val inf = Score.INFINITY
        // alpha is the best score so far, as in a plain alpha-beta root.
        assertEquals(listOf(-inf, 30, 50, 50, 50), alphas(RootChoice.draw(Randomisation.None, Random(0)), listOf(30, 50, 10, 50)))
        assertEquals(listOf(-inf, 30, 50, 50), alphas(planned(levelOne, MoveChoice.RandomMove), listOf(30, 50, 10)))
        assertEquals(listOf(-inf, 30, 50, 50), alphas(planned(levelTwo, MoveChoice.Best), listOf(30, 50, 10)))
    }

    @Test
    fun noiseLowersAlphaByTwiceTheNoise() {
        // A move more than 2 × 40 cp below the best can never win after ±40 cp of noise.
        val inf = Score.INFINITY
        assertEquals(listOf(-inf, 100 - 81, 100 - 81, 120 - 81), alphas(planned(levelOne, MoveChoice.NoisyBest), listOf(100, 20, 120)))
    }

    @Test
    fun withinMarginLowersAlphaByTheMargin() {
        val inf = Score.INFINITY
        assertEquals(listOf(-inf, 79, 79, 89), alphas(planned(levelThree, MoveChoice.WithinMargin), listOf(100, 60, 110)))
        // A margin wider than any score leaves every root move with a full window (all scores exact).
        val everything = planned(Randomisation.WithinMargin(1_000_000, decidedCp = Int.MAX_VALUE), MoveChoice.WithinMargin)
        assertEquals(listOf(-inf, -inf, -inf, -inf), alphas(everything, listOf(-Score.MATE + 2, 0, Score.MATE - 1)))
    }

    @Test
    fun topFiveKeepsAlphaBelowTheFifthBestScore() {
        val inf = Score.INFINITY
        val choice = planned(levelTwo, MoveChoice.FromTop)
        assertEquals(
            listOf(-inf, -inf, -inf, -inf, -inf, 9, 19, 19, 29, 39),
            alphas(choice, listOf(10, 50, 20, 40, 30, 60, 5, 70, 80)),
        )
        // The window resets every iteration.
        assertEquals(listOf(-inf, -inf), alphas(choice, listOf(100)))
    }

    // --- The pick after the search --------------------------------------------------------------

    private fun pickCounts(choice: RootChoice, scores: IntArray, bestIndex: Int, n: Int, seed: Int = 11): IntArray {
        val random = Random(seed)
        val counts = IntArray(scores.size)
        repeat(n) { counts[choice.choose(scores, scores.size, bestIndex, random)]++ }
        return counts
    }

    @Test
    fun theBestChoiceReturnsTheBestIndexWithoutDrawing() {
        val choice = RootChoice.draw(Randomisation.None, Random(0))
        val random = Random(5)
        assertEquals(2, choice.choose(intArrayOf(10, 20, 30), 3, bestIndex = 2, random))
        assertEquals(MoveChoice.Best, choice.applied)
        assertEquals(Random(5).nextLong(), random.nextLong())
    }

    @Test
    fun aUniformRandomMoveIsUniformOverTheScoredMoves() {
        val counts = pickCounts(planned(levelOne, MoveChoice.RandomMove), IntArray(10) { -it * 100 }, bestIndex = 0, n = 10_000)
        for ((index, count) in counts.withIndex()) assertTrue(count in 850..1_150, "index $index: ${counts.toList()}")
    }

    @Test
    fun noiseOnlyLetsMovesWithinTwiceTheNoiseWin() {
        val choice = planned(levelOne, MoveChoice.NoisyBest)
        val counts = pickCounts(choice, intArrayOf(100, 70, 19, 0, 100 - 80), bestIndex = 0, n = 10_000)
        assertEquals(MoveChoice.NoisyBest, choice.applied)
        assertEquals(0, counts[2], "19 is more than 80 below 100: ${counts.toList()}")
        assertEquals(0, counts[3], counts.toList().toString())
        // P(70 + n1 > 100 + n0) for n uniform in -40..40 is about 0.19; exactly 80 below needs both extremes.
        assertTrue(counts[1] in 1_500..2_400, counts.toList().toString())
        assertTrue(counts[4] in 0..20, counts.toList().toString())
        assertEquals(10_000, counts.sum())
    }

    @Test
    fun topFivePicksUniformlyAmongTheFiveBestScores() {
        val choice = planned(levelTwo, MoveChoice.FromTop)
        val scores = intArrayOf(40, 90, 10, 70, 80, 60, 50, 20)
        val counts = pickCounts(choice, scores, bestIndex = 1, n = 10_000)
        assertEquals(MoveChoice.FromTop, choice.applied)
        for (index in listOf(1, 4, 3, 5, 6)) assertTrue(counts[index] in 1_800..2_200, "index $index: ${counts.toList()}")
        for (index in listOf(0, 2, 7)) assertEquals(0, counts[index], "index $index: ${counts.toList()}")
    }

    @Test
    fun topFiveWithFewerMovesPicksAmongAllOfThem() {
        val counts = pickCounts(planned(levelTwo, MoveChoice.FromTop), intArrayOf(5, -300, 50), bestIndex = 2, n = 3_000)
        for (count in counts) assertTrue(count in 850..1_150, counts.toList().toString())
    }

    @Test
    fun withinMarginPicksUniformlyAmongMovesWithinTheMargin() {
        val choice = planned(levelThree, MoveChoice.WithinMargin)
        val scores = intArrayOf(80, 100, 79, 95, 100, -50)
        val counts = pickCounts(choice, scores, bestIndex = 1, n = 8_000)
        assertEquals(MoveChoice.WithinMargin, choice.applied)
        for (index in listOf(0, 1, 3, 4)) assertTrue(counts[index] in 1_800..2_200, "index $index: ${counts.toList()}")
        assertEquals(0, counts[2], "79 is 21 below the best")
        assertEquals(0, counts[5])
    }

    @Test
    fun withinMarginNeverGivesUpAForcedMateOrPicksAmongMatedLines() {
        val choice = planned(levelThree, MoveChoice.WithinMargin)
        val mating = intArrayOf(Score.MATE - 7, Score.MATE - 3, Score.MATE - 9, 500)
        val counts = pickCounts(choice, mating, bestIndex = 1, n = 500)
        assertEquals(500, counts[1], "mate in 3 plies must be played: ${counts.toList()}")
        assertEquals(MoveChoice.Best, choice.applied)

        val mated = intArrayOf(-Score.MATE + 4, -Score.MATE + 10, -Score.MATE + 8)
        assertEquals(500, pickCounts(choice, mated, bestIndex = 1, n = 500)[1], "the longest defence")
    }

    @Test
    fun withinMarginPlaysTheBestMoveOnceTheGameIsDecided() {
        // Level 3 varies its moves only while |best| < 400 cp. In a won ending such as K+Q v K the
        // progress moves are only a few centipawns apart, and random picks among them wander until
        // the 50-move rule.
        val choice = planned(levelThree, MoveChoice.WithinMargin)
        for (best in listOf(400, 950, -400, -1_200)) {
            val counts = pickCounts(choice, intArrayOf(best - 5, best, best - 15), bestIndex = 1, n = 300)
            assertEquals(300, counts[1], "best $best: ${counts.toList()}")
            assertEquals(MoveChoice.Best, choice.applied, "best $best")
        }
        for (best in listOf(399, -399, 0)) {
            val counts = pickCounts(choice, intArrayOf(best - 5, best, best - 15), bestIndex = 1, n = 3_000)
            for (count in counts) assertTrue(count in 850..1_150, "best $best: ${counts.toList()}")
            assertEquals(MoveChoice.WithinMargin, choice.applied, "best $best")
        }
    }

    @Test
    fun randomisationParametersAreValidated() {
        assertFailsWith<IllegalArgumentException> { Randomisation.RandomMoveOrNoise(randomMoveChance = 1.5, noiseCp = 40) }
        assertFailsWith<IllegalArgumentException> { Randomisation.RandomMoveOrNoise(randomMoveChance = -0.1, noiseCp = 40) }
        assertFailsWith<IllegalArgumentException> { Randomisation.RandomMoveOrNoise(randomMoveChance = 0.5, noiseCp = -1) }
        assertFailsWith<IllegalArgumentException> { Randomisation.PickFromTop(chance = 2.0, count = 5) }
        assertFailsWith<IllegalArgumentException> { Randomisation.PickFromTop(chance = 0.2, count = 0) }
        assertFailsWith<IllegalArgumentException> { Randomisation.WithinMargin(marginCp = -1, decidedCp = 400) }
        assertFailsWith<IllegalArgumentException> { Randomisation.WithinMargin(marginCp = 20, decidedCp = 0) }
    }
}
