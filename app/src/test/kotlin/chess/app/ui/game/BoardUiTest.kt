package chess.app.ui.game

import chess.app.ui.game.board.BoardFixtures
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** The board's UI model: 64 cells derived from the pieces plus the cue sets (tech design §6.3). */
class BoardUiTest {

    @Test
    fun squaresUseA1AsZeroAndH8As63() {
        assertEquals(0, Squares.parse("a1"))
        assertEquals(7, Squares.parse("h1"))
        assertEquals(28, Squares.parse("e4"))
        assertEquals(63, Squares.parse("h8"))
        assertEquals("e4", Squares.name(28))
        assertEquals(4, Squares.file(28))
        assertEquals(3, Squares.rank(28))
        assertEquals(28, Squares.of(file = 4, rank = 3))
        (0..63).forEach { assertEquals(it, Squares.parse(Squares.name(it))) }
    }

    @Test
    fun invalidSquareNamesAreRejected() {
        listOf("", "e", "i1", "a9", "a0", "E4", "e44").forEach { name ->
            assertFailsWith<IllegalArgumentException>(name) { Squares.parse(name) }
        }
    }

    @Test
    fun placementParsesTheStartPosition() {
        val pieces = BoardFixtures.pieces(BoardFixtures.START_PLACEMENT)
        assertEquals(64, pieces.size)
        assertEquals(PieceUi(Side.White, PieceKind.Rook), pieces[Squares.parse("a1")])
        assertEquals(PieceUi(Side.White, PieceKind.King), pieces[Squares.parse("e1")])
        assertEquals(PieceUi(Side.White, PieceKind.Pawn), pieces[Squares.parse("h2")])
        assertEquals(PieceUi(Side.Black, PieceKind.Queen), pieces[Squares.parse("d8")])
        assertEquals(PieceUi(Side.Black, PieceKind.Knight), pieces[Squares.parse("g8")])
        assertNull(pieces[Squares.parse("e4")])
        assertEquals(32, pieces.count { it != null })
    }

    @Test
    fun boardNeeds64Squares() {
        assertFailsWith<IllegalArgumentException> { BoardUi(pieces = List(63) { null }, player = Side.White) }
    }

    @Test
    fun cueSquaresMustBeOnTheBoard() {
        val empty = List<PieceUi?>(64) { null }
        assertFailsWith<IllegalArgumentException> { BoardUi(empty, Side.White, selected = 64) }
        assertFailsWith<IllegalArgumentException> { BoardUi(empty, Side.White, checkSquare = -1) }
        assertFailsWith<IllegalArgumentException> {
            BoardUi(empty, Side.White, targets = mapOf(70 to TargetKind.Move))
        }
        assertFailsWith<IllegalArgumentException> { BoardUi(empty, Side.White, lastMove = LastMoveUi(0, 64)) }
    }

    @Test
    fun cellsCarryTheirCues() {
        val board = BoardUi(
            pieces = BoardFixtures.pieces(BoardFixtures.START_PLACEMENT),
            player = Side.White,
            selected = Squares.parse("g1"),
            targets = mapOf(Squares.parse("f3") to TargetKind.Move, Squares.parse("h3") to TargetKind.Move),
            lastMove = LastMoveUi(from = Squares.parse("e7"), to = Squares.parse("e5")),
            checkSquare = Squares.parse("e1"),
            interactive = true,
        )

        val g1 = board.cell(Squares.parse("g1"))
        assertEquals(PieceUi(Side.White, PieceKind.Knight), g1.piece)
        assertTrue(g1.selected)
        assertNull(g1.target)

        assertEquals(TargetKind.Move, board.cell(Squares.parse("f3")).target)
        assertEquals(LastMoveRole.From, board.cell(Squares.parse("e7")).lastMove)
        assertEquals(LastMoveRole.To, board.cell(Squares.parse("e5")).lastMove)
        assertTrue(board.cell(Squares.parse("e1")).inCheck)
        assertFalse(board.cell(Squares.parse("e2")).inCheck)

        val cells = board.cells
        assertEquals(64, cells.size)
        cells.forEachIndexed { index, cell -> assertEquals(index, cell.square) }
    }

    @Test
    fun ownPiecesBelongToThePlayer() {
        val pieces = BoardFixtures.pieces(BoardFixtures.START_PLACEMENT)
        val asWhite = BoardUi(pieces, Side.White)
        val asBlack = BoardUi(pieces, Side.Black)
        assertTrue(asWhite.isOwnPiece(Squares.parse("e2")))
        assertFalse(asWhite.isOwnPiece(Squares.parse("e7")))
        assertFalse(asWhite.isOwnPiece(Squares.parse("e4")))
        assertTrue(asBlack.isOwnPiece(Squares.parse("e7")))
        assertFalse(asBlack.isOwnPiece(Squares.parse("e2")))
    }

    @Test
    fun targetKindsKnowWhetherTheyCapture() {
        assertFalse(TargetKind.Move.isCapture)
        assertFalse(TargetKind.Castling.isCapture)
        assertFalse(TargetKind.Promotion.isCapture)
        assertTrue(TargetKind.Capture.isCapture)
        assertTrue(TargetKind.EnPassant.isCapture)
        assertTrue(TargetKind.CapturePromotion.isCapture)
    }
}
