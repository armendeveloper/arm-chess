package chess.engine.search

/**
 * The four computer levels (US-6, design ADR-3). Strength comes from the node budget and depth cap,
 * which are the same on every device. The time cap is the PRD's think-time cap; the search stops
 * 100 ms before it ([LevelParams.deadlineMs]).
 *
 * | Level | Depth cap | Node budget | Time cap → deadline | Quiescence | Randomisation |
 * |---|---|---|---|---|---|
 * | 1 Beginner | 1 | 5,000 | 500 → 400 ms | off | 50% uniform random move, else best after ±40 cp noise |
 * | 2 Casual | 2 | 30,000 | 1,000 → 900 ms | captures, 4 plies | 20% uniform pick from the top 5 |
 * | 3 Club | 6 | 250,000 | 2,000 → 1,900 ms | full | uniform pick within 20 cp of the best, while the best is under ±400 cp |
 * | 4 Strong | 64 | 1,500,000 | 4,000 → 3,900 ms | full | none |
 *
 * Level 4's node budget is provisional until T-11 measures the reference device.
 *
 * For a strength match (design §4.7), use `Levels.params(level).withoutDeadline()` and pass each game
 * its own seeded `Random`: the moves then depend only on the seed, not on machine load.
 */
object Levels {
    /** The weakest level. */
    const val MIN: Int = 1

    /** The strongest level. */
    const val MAX: Int = 4

    /** Every level, weakest first. */
    val ALL: IntRange = MIN..MAX

    private val table = listOf(
        LevelParams(
            depthCap = 1,
            nodeBudget = 5_000,
            timeCapMs = 500,
            quiescence = Quiescence.Off,
            randomisation = Randomisation.RandomMoveOrNoise(randomMoveChance = 0.5, noiseCp = 40),
        ),
        LevelParams(
            depthCap = 2,
            nodeBudget = 30_000,
            timeCapMs = 1_000,
            quiescence = Quiescence.CapturesOnly(maxPlies = 4),
            randomisation = Randomisation.PickFromTop(chance = 0.2, count = 5),
        ),
        LevelParams(
            depthCap = 6,
            nodeBudget = 250_000,
            timeCapMs = 2_000,
            quiescence = Quiescence.Full,
            randomisation = Randomisation.WithinMargin(marginCp = 20, decidedCp = 400),
        ),
        LevelParams(
            depthCap = LevelParams.MAX_DEPTH,
            nodeBudget = 1_500_000,
            timeCapMs = 4_000,
            quiescence = Quiescence.Full,
            randomisation = Randomisation.None,
        ),
    )

    /**
     * The search parameters for [level], with the deadline on (as the app plays).
     *
     * @throws IllegalArgumentException unless [level] is in [ALL].
     */
    fun params(level: Int): LevelParams {
        require(level in ALL) { "level must be in $ALL, was $level" }
        return table[level - MIN]
    }
}
