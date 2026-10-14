package chess.engine.board

/**
 * Zobrist hashing (design §4.1, §4.3): a 64-bit key that is the XOR of one random number per
 * (piece, square), one for Black to move, one per castling-rights combination and one per en passant
 * file. [Board] keeps its key up to date incrementally in `make` and `unmake`; [compute] builds it
 * from scratch, which tests use to check the incremental updates.
 *
 * The en passant file is hashed only when the side to move has a **legal** en passant capture
 * ([Board.hasLegalEnPassant]), not merely because the FEN records an en passant square. So two
 * positions with the same placement, side to move and castling rights get the same key exactly when
 * their en passant capture availability is the same, which is US-5's definition of the same position.
 * The clocks are not hashed.
 *
 * The numbers come from a fixed seed, so keys are the same on every run and every device.
 */
internal object Zobrist {
    /** "ARMCHESS" in ASCII. Any constant works; changing it changes every key. */
    private const val SEED: Long = 0x41524D4348455353L

    /** Indexed by `(piece shl 6) or square` for piece codes 0..15; entries for [Piece.NONE] are 0. */
    private val pieceSquare = LongArray(16 * 64)

    /** Indexed by a [CastlingRights] combination (0..15): the XOR of the keys of its rights. */
    private val castlingKeys = LongArray(16)

    private val enPassantKeys = LongArray(8)

    /** XORed in when Black is to move. */
    val SIDE: Long

    init {
        val random = SplitMix64(SEED)
        for (piece in 1 until 16) {
            for (sq in 0 until 64) pieceSquare[(piece shl 6) or sq] = random.next()
        }
        val rightKeys = LongArray(4) { random.next() }
        for (rights in castlingKeys.indices) {
            var key = 0L
            for (bit in 0 until 4) if (rights and (1 shl bit) != 0) key = key xor rightKeys[bit]
            castlingKeys[rights] = key
        }
        for (file in 0 until 8) enPassantKeys[file] = random.next()
        SIDE = random.next()
    }

    /** Key of [piece] on [sq] (0..63); 0 for [Piece.NONE], so an empty square can be XORed safely. */
    fun piece(piece: Int, sq: Int): Long = pieceSquare[(piece shl 6) or sq]

    /** Key of a [CastlingRights] combination; 0 for none. */
    fun castling(rights: Int): Long = castlingKeys[rights]

    /** Key of an en passant capture onto [file] (0..7). */
    fun enPassant(file: Int): Long = enPassantKeys[file]

    /** The key of [board] computed from scratch. The board is unchanged afterwards. */
    fun compute(board: Board): Long {
        var key = 0L
        for (sq in 0 until 64) key = key xor piece(board.pieceAt(sq), sq)
        if (board.sideToMove == Piece.BLACK) key = key xor SIDE
        key = key xor castling(board.castlingRights)
        if (board.hasLegalEnPassant()) key = key xor enPassant(Square.file(board.epSquare))
        return key
    }

    /** SplitMix64 (Steele, Lea and Flood), a small generator whose output is fixed by its seed. */
    private class SplitMix64(private var state: Long) {
        fun next(): Long {
            state += -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
            var z = state
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
            return z xor (z ushr 31)
        }
    }
}
