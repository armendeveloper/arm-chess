package chess.engine.board

/**
 * Square helpers. In the public API a square is an `Int` from 0 to 63 (a1 = 0, b1 = 1, ..., h8 = 63),
 * so `sq = rank * 8 + file` (design §4.1).
 *
 * [Board] stores pieces in a 128-entry 0x88 array (ADR-2), where `sq88 = rank * 16 + file`. An index
 * is on the board exactly when `sq88 and 0x88 == 0`, which makes off-board tests for ray and leap
 * steps a single mask.
 */
object Square {
    /** "No square", e.g. no en passant target or no king on the board. */
    const val NONE: Int = -1

    const val A1: Int = 0
    const val B1: Int = 1
    const val C1: Int = 2
    const val D1: Int = 3
    const val E1: Int = 4
    const val F1: Int = 5
    const val G1: Int = 6
    const val H1: Int = 7
    const val A8: Int = 56
    const val B8: Int = 57
    const val C8: Int = 58
    const val D8: Int = 59
    const val E8: Int = 60
    const val F8: Int = 61
    const val G8: Int = 62
    const val H8: Int = 63

    private const val FILES = "abcdefgh"
    private const val ON_BOARD_BITS = 0x77

    /** The square on [file] (0 = a) and [rank] (0 = rank 1). Both must be in 0..7. */
    fun of(file: Int, rank: Int): Int = rank * 8 + file

    /** File index 0..7 (a..h). */
    fun file(sq: Int): Int = sq and 7

    /** Rank index 0..7 (rank 1..8). */
    fun rank(sq: Int): Int = sq shr 3

    /** 0..63 to 0x88: shifts the rank bits up one place. */
    fun to0x88(sq: Int): Int = sq + (sq and 56)

    /** 0x88 to 0..63. [sq88] must be on the board. */
    fun from0x88(sq88: Int): Int = (sq88 + (sq88 and 7)) shr 1

    /**
     * True when the 0x88 index [sq88] is a real square. Exact for any `Int`, including negative
     * results of a step: only the rank and file bits (0x77) may be set. For indices in 0..127 this is
     * the classic `sq88 and 0x88 == 0` test.
     */
    fun isOnBoard0x88(sq88: Int): Boolean = sq88 and ON_BOARD_BITS.inv() == 0

    /** Algebraic name, e.g. 28 -> "e4". */
    fun name(sq: Int): String {
        require(sq in 0..63) { "Square index must be in 0..63, was $sq" }
        return "${FILES[file(sq)]}${rank(sq) + 1}"
    }

    /** Parses a lowercase algebraic name such as "e4", or returns null. */
    fun parseOrNull(name: String): Int? {
        if (name.length != 2) return null
        val file = name[0] - 'a'
        val rank = name[1] - '1'
        if (file !in 0..7 || rank !in 0..7) return null
        return of(file, rank)
    }

    /** Parses a lowercase algebraic name such as "e4". */
    fun parse(name: String): Int =
        parseOrNull(name) ?: throw IllegalArgumentException("Invalid square name '$name'")
}
