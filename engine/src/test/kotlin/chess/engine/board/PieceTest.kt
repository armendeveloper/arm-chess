package chess.engine.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PieceTest {
    @Test
    fun codesCombineColourAndTypeAndSplitBack() {
        for (color in PieceColor.entries) {
            for (type in PieceType.entries) {
                val piece = Piece.of(color.code, type.code)
                assertNotEquals(Piece.NONE, piece)
                assertEquals(color.code, Piece.colorOf(piece))
                assertEquals(type.code, Piece.typeOf(piece))
                assertEquals(color, Piece.color(piece))
                assertEquals(type, Piece.type(piece))
            }
        }
    }

    @Test
    fun all12CodesAreDistinctAndFitInFourBits() {
        val codes = PieceColor.entries.flatMap { c -> PieceType.entries.map { t -> Piece.of(c.code, t.code) } }
        assertEquals(12, codes.toSet().size)
        codes.forEach { assertTrue(it in 1..15, "code $it") }
    }

    @Test
    fun namedConstantsMatchOf() {
        assertEquals(Piece.of(Piece.WHITE, Piece.PAWN), Piece.WHITE_PAWN)
        assertEquals(Piece.of(Piece.WHITE, Piece.KING), Piece.WHITE_KING)
        assertEquals(Piece.of(Piece.BLACK, Piece.KNIGHT), Piece.BLACK_KNIGHT)
        assertEquals(Piece.of(Piece.BLACK, Piece.QUEEN), Piece.BLACK_QUEEN)
    }

    @Test
    fun fenCharactersRoundTrip() {
        val expected = mapOf(
            'P' to Piece.WHITE_PAWN, 'N' to Piece.WHITE_KNIGHT, 'B' to Piece.WHITE_BISHOP,
            'R' to Piece.WHITE_ROOK, 'Q' to Piece.WHITE_QUEEN, 'K' to Piece.WHITE_KING,
            'p' to Piece.BLACK_PAWN, 'n' to Piece.BLACK_KNIGHT, 'b' to Piece.BLACK_BISHOP,
            'r' to Piece.BLACK_ROOK, 'q' to Piece.BLACK_QUEEN, 'k' to Piece.BLACK_KING,
        )
        for ((ch, piece) in expected) {
            assertEquals(piece, Piece.fromFenChar(ch))
            assertEquals(ch, Piece.toFenChar(piece))
        }
        assertEquals(Piece.NONE, Piece.fromFenChar('x'))
        assertEquals(Piece.NONE, Piece.fromFenChar('1'))
    }

    @Test
    fun publicEnumsExposeCodesAndHelpers() {
        assertEquals(PieceColor.BLACK, PieceColor.WHITE.opposite)
        assertEquals(PieceColor.WHITE, PieceColor.BLACK.opposite)
        assertEquals(PieceColor.BLACK, PieceColor.fromCode(Piece.BLACK))
        assertEquals(PieceType.QUEEN, PieceType.fromCode(Piece.QUEEN))
        assertNull(PieceType.fromCodeOrNull(0))
        assertEquals('n', PieceType.KNIGHT.uciChar)
    }
}
