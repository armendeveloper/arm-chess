package chess.engine.board

/**
 * A move packed into one `Int` (design §4.1), so move lists are plain `IntArray`s and the search
 * does not allocate:
 *
 * ```
 * bits  0..5   from square (0..63)
 * bits  6..11  to square (0..63)
 * bits 12..14  promotion piece type (Piece.KNIGHT..Piece.QUEEN), 0 if none
 * bits 15..18  flags: CAPTURE, DOUBLE_PUSH, EN_PASSANT, CASTLE
 * ```
 *
 * Conventions for move generation: an en passant capture carries both [EN_PASSANT] and [CAPTURE];
 * castling is encoded as the king's move (e1g1, e1c1, e8g8, e8c8) with [CASTLE].
 */
@JvmInline
value class Move(val bits: Int) {
    val from: Int get() = bits and SQUARE_MASK
    val to: Int get() = (bits shr TO_SHIFT) and SQUARE_MASK

    /** Promotion type code (Piece.KNIGHT..Piece.QUEEN), or Piece.NONE. */
    val promotion: Int get() = (bits shr PROMO_SHIFT) and PROMO_MASK

    /** Promotion as a [PieceType] for the app, or null. */
    val promotionType: PieceType? get() = PieceType.fromCodeOrNull(promotion)

    /** The four flag bits, as the [CAPTURE] / [DOUBLE_PUSH] / [EN_PASSANT] / [CASTLE] constants. */
    val flags: Int get() = bits and FLAGS_MASK

    val isCapture: Boolean get() = bits and CAPTURE != 0
    val isDoublePush: Boolean get() = bits and DOUBLE_PUSH != 0
    val isEnPassant: Boolean get() = bits and EN_PASSANT != 0
    val isCastle: Boolean get() = bits and CASTLE != 0
    val isPromotion: Boolean get() = promotion != Piece.NONE

    /** Long algebraic UCI form, e.g. "e2e4", "e7e8q". */
    fun uci(): String {
        val base = Square.name(from) + Square.name(to)
        val promo = promotionType ?: return base
        return base + promo.uciChar
    }

    override fun toString(): String = uci()

    companion object {
        private const val SQUARE_MASK = 0x3F
        private const val TO_SHIFT = 6
        private const val PROMO_SHIFT = 12
        private const val PROMO_MASK = 0x7

        const val CAPTURE: Int = 1 shl 15
        const val DOUBLE_PUSH: Int = 1 shl 16
        const val EN_PASSANT: Int = 1 shl 17
        const val CASTLE: Int = 1 shl 18
        private const val FLAGS_MASK = CAPTURE or DOUBLE_PUSH or EN_PASSANT or CASTLE

        /** "No move" (a1a1, never legal); a null value without boxing. */
        val NONE: Move = Move(0)

        /**
         * Packs a move. Inputs are not range-checked (this is on the move generation hot path):
         * [from] and [to] must be 0..63, [promotion] 0 or Piece.KNIGHT..Piece.QUEEN, and [flags]
         * a combination of the flag constants.
         */
        fun of(from: Int, to: Int, promotion: Int = Piece.NONE, flags: Int = 0): Move =
            Move(from or (to shl TO_SHIFT) or (promotion shl PROMO_SHIFT) or flags)
    }
}
