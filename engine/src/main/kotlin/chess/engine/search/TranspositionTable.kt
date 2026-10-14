package chess.engine.search

import chess.engine.board.Move

/**
 * The transposition table (design §4.6): `2^entriesLog2` entries in two `LongArray`s, the Zobrist
 * key and a packed data word, so it never allocates after construction. 2^18 entries take 4 MiB.
 *
 * Entries come in buckets of two. The first slot is depth-preferred: it is only replaced by an entry
 * searched at least as deep, by the same position, or when it is left over from an earlier search.
 * The second slot is always replaced.
 *
 * Mate scores are stored relative to the node and loaded relative to the root ([store] and [score]
 * take the ply), so a mate found along one path keeps its distance when reached along another.
 *
 * Data word layout (an empty slot is 0, because [bound] is never 0 in a stored entry):
 * ```
 * bits  0..18  Move.bits
 * bits 19..34  score + 32768
 * bits 35..42  depth (0..255)
 * bits 43..44  bound (UPPER, LOWER, EXACT)
 * bits 45..50  search generation (0..63)
 * ```
 */
internal class TranspositionTable(entriesLog2: Int = DEFAULT_ENTRIES_LOG2) {
    init {
        require(entriesLog2 in 1..MAX_ENTRIES_LOG2) { "entriesLog2 must be in 1..$MAX_ENTRIES_LOG2, was $entriesLog2" }
    }

    val entries: Int = 1 shl entriesLog2
    private val keys = LongArray(entries)
    private val data = LongArray(entries)

    /** The first slot of a key's bucket is `index(key) and bucketMask`. */
    private val bucketMask = (entries - 1) and 1.inv()
    private var generation = 0

    /** Empties every slot. */
    fun clear() {
        keys.fill(0L)
        data.fill(0L)
        generation = 0
    }

    /** Marks the start of a new search, so that entries from earlier searches can be replaced first. */
    fun newSearch() {
        generation = (generation + 1) and GENERATION_MASK
    }

    /** The data word stored for [key], or 0 (see [isHit]). */
    fun probe(key: Long): Long {
        val slot = slotOf(key)
        if (keys[slot] == key && data[slot] != 0L) return data[slot]
        if (keys[slot + 1] == key && data[slot + 1] != 0L) return data[slot + 1]
        return 0L
    }

    /**
     * Stores a search result for the position [key], found [ply] plies from the root. [move] may be
     * [Move.NONE] (no best move, e.g. a fail-low); then a move already stored for the key is kept.
     */
    fun store(key: Long, move: Move, score: Int, depth: Int, bound: Int, ply: Int) {
        val first = slotOf(key)
        val slot = when {
            keys[first] == key -> first
            keys[first + 1] == key -> first + 1
            data[first] == 0L || generationOf(data[first]) != generation || depth >= depth(data[first]) -> first
            else -> first + 1
        }
        var bits = move.bits
        if (bits == Move.NONE.bits && keys[slot] == key && data[slot] != 0L) bits = move(data[slot]).bits
        keys[slot] = key
        data[slot] = bits.toLong() or
            ((toStored(score, ply) + SCORE_OFFSET).toLong() shl SCORE_SHIFT) or
            (depth.coerceIn(0, MAX_DEPTH).toLong() shl DEPTH_SHIFT) or
            (bound.toLong() shl BOUND_SHIFT) or
            (generation.toLong() shl GENERATION_SHIFT)
    }

    private fun slotOf(key: Long): Int = (key xor (key ushr 32)).toInt() and bucketMask

    companion object {
        const val DEFAULT_ENTRIES_LOG2: Int = 18
        const val MAX_ENTRIES_LOG2: Int = 26

        /** The score is at most this (a fail-low: the true score may be lower). */
        const val UPPER: Int = 1

        /** The score is at least this (a fail-high / beta cut-off). */
        const val LOWER: Int = 2

        /** The score is exact. */
        const val EXACT: Int = 3

        private const val MOVE_MASK = (1L shl 19) - 1
        private const val SCORE_SHIFT = 19
        private const val SCORE_OFFSET = 32_768
        private const val DEPTH_SHIFT = 35
        private const val MAX_DEPTH = 255
        private const val BOUND_SHIFT = 43
        private const val GENERATION_SHIFT = 45
        private const val GENERATION_MASK = 63

        fun isHit(entry: Long): Boolean = entry != 0L

        fun move(entry: Long): Move = Move((entry and MOVE_MASK).toInt())

        /** The stored score as seen from the root, for a node [ply] plies from it. */
        fun score(entry: Long, ply: Int): Int {
            val stored = ((entry ushr SCORE_SHIFT) and 0xFFFF).toInt() - SCORE_OFFSET
            return when {
                stored >= Score.MATE_BOUND -> stored - ply
                stored <= -Score.MATE_BOUND -> stored + ply
                else -> stored
            }
        }

        fun depth(entry: Long): Int = ((entry ushr DEPTH_SHIFT) and 0xFF).toInt()

        fun bound(entry: Long): Int = ((entry ushr BOUND_SHIFT) and 3).toInt()

        private fun generationOf(entry: Long): Int = ((entry ushr GENERATION_SHIFT) and GENERATION_MASK.toLong()).toInt()

        /** A root-relative mate score made relative to the node [ply] plies from the root. */
        private fun toStored(score: Int, ply: Int): Int = when {
            score >= Score.MATE_BOUND -> score + ply
            score <= -Score.MATE_BOUND -> score - ply
            else -> score
        }
    }
}
