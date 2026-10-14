package chess.engine.board

/**
 * Piece codes (design §4.1). A piece is an `Int`: `(color shl 3) or type`, so white pieces are 1..6,
 * black pieces 9..14 and [NONE] is 0. Colour codes double as array indices (white 0, black 1).
 *
 * The engine works on these raw codes so that board arrays stay `IntArray`s. [PieceColor] and
 * [PieceType] are the readable forms for the app and for tests.
 */
object Piece {
    const val NONE: Int = 0

    // Colour codes.
    const val WHITE: Int = 0
    const val BLACK: Int = 1

    // Type codes. Promotion fields in Move use KNIGHT..QUEEN, so they fit in 3 bits.
    const val PAWN: Int = 1
    const val KNIGHT: Int = 2
    const val BISHOP: Int = 3
    const val ROOK: Int = 4
    const val QUEEN: Int = 5
    const val KING: Int = 6

    const val WHITE_PAWN: Int = PAWN
    const val WHITE_KNIGHT: Int = KNIGHT
    const val WHITE_BISHOP: Int = BISHOP
    const val WHITE_ROOK: Int = ROOK
    const val WHITE_QUEEN: Int = QUEEN
    const val WHITE_KING: Int = KING
    const val BLACK_PAWN: Int = 8 or PAWN
    const val BLACK_KNIGHT: Int = 8 or KNIGHT
    const val BLACK_BISHOP: Int = 8 or BISHOP
    const val BLACK_ROOK: Int = 8 or ROOK
    const val BLACK_QUEEN: Int = 8 or QUEEN
    const val BLACK_KING: Int = 8 or KING

    /** FEN letters indexed by type code (index 0 unused). */
    private const val FEN_LETTERS = " pnbrqk"

    fun of(color: Int, type: Int): Int = (color shl 3) or type

    /** Colour code of a non-empty piece. */
    fun colorOf(piece: Int): Int = piece shr 3

    /** Type code of a piece; 0 for [NONE]. */
    fun typeOf(piece: Int): Int = piece and 7

    fun color(piece: Int): PieceColor = PieceColor.fromCode(colorOf(piece))

    fun type(piece: Int): PieceType = PieceType.fromCode(typeOf(piece))

    /** FEN letter: uppercase for white, lowercase for black. [piece] must not be [NONE]. */
    fun toFenChar(piece: Int): Char {
        val letter = FEN_LETTERS[typeOf(piece)]
        return if (colorOf(piece) == WHITE) letter.uppercaseChar() else letter
    }

    /** Piece code for a FEN letter, or [NONE] if [ch] is not one of `PNBRQKpnbrqk`. */
    fun fromFenChar(ch: Char): Int {
        val type = FEN_LETTERS.indexOf(ch.lowercaseChar())
        if (type < PAWN) return NONE
        return of(if (ch.isUpperCase()) WHITE else BLACK, type)
    }
}

enum class PieceColor(val code: Int) {
    WHITE(Piece.WHITE),
    BLACK(Piece.BLACK);

    val opposite: PieceColor
        get() = if (this == WHITE) BLACK else WHITE

    companion object {
        fun fromCode(code: Int): PieceColor = when (code) {
            Piece.WHITE -> WHITE
            Piece.BLACK -> BLACK
            else -> throw IllegalArgumentException("Unknown colour code $code")
        }
    }
}

enum class PieceType(val code: Int, val uciChar: Char) {
    PAWN(Piece.PAWN, 'p'),
    KNIGHT(Piece.KNIGHT, 'n'),
    BISHOP(Piece.BISHOP, 'b'),
    ROOK(Piece.ROOK, 'r'),
    QUEEN(Piece.QUEEN, 'q'),
    KING(Piece.KING, 'k');

    companion object {
        private val byCode = arrayOfNulls<PieceType>(8).also { table -> entries.forEach { table[it.code] = it } }

        fun fromCodeOrNull(code: Int): PieceType? = if (code in byCode.indices) byCode[code] else null

        fun fromCode(code: Int): PieceType =
            fromCodeOrNull(code) ?: throw IllegalArgumentException("Unknown piece type code $code")
    }
}
