package chess.app.ui.game.board

import chess.app.ui.game.BoardUi
import chess.app.ui.game.LastMoveUi
import chess.app.ui.game.PieceKind
import chess.app.ui.game.PieceUi
import chess.app.ui.game.Side
import chess.app.ui.game.Squares
import chess.app.ui.game.TargetKind
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/**
 * What the board draws, and where (design §4.3.3): orientation, the mark list per layer, and the
 * square under a touch. The Canvas only executes this list, so these JVM tests pin the rendering.
 */
class BoardDrawingTest {

    // --- Orientation ---

    @Test
    fun whiteReadsFromA8ToH1() {
        val order = visualOrder(Side.White).map(Squares::name)
        assertEquals(64, order.size)
        assertEquals(listOf("a8", "b8", "c8", "d8", "e8", "f8", "g8", "h8", "a7"), order.take(9))
        assertEquals("h1", order.last())
        assertEquals((0..63).toSet(), visualOrder(Side.White).toSet())
    }

    @Test
    fun blackReadsFromH1ToA8() {
        val order = visualOrder(Side.Black).map(Squares::name)
        assertEquals(64, order.size)
        assertEquals(listOf("h1", "g1", "f1", "e1", "d1", "c1", "b1", "a1", "h2"), order.take(9))
        assertEquals("a8", order.last())
        assertEquals((0..63).toSet(), visualOrder(Side.Black).toSet())
    }

    @Test
    fun visualRowAndColumnInvertSquareAt() {
        for (player in Side.entries) {
            for (row in 0..7) for (col in 0..7) {
                val square = squareAt(row, col, player)
                assertEquals(row, visualRow(square, player))
                assertEquals(col, visualCol(square, player))
            }
        }
        assertEquals(Squares.parse("e1"), squareAt(row = 7, col = 4, player = Side.White))
        assertEquals(Squares.parse("e1"), squareAt(row = 0, col = 3, player = Side.Black))
    }

    @Test
    fun touchPositionMapsToTheSquareUnderIt() {
        val s = 10f
        assertEquals("a8", Squares.name(squareAtOffset(5f, 5f, s, Side.White)!!))
        assertEquals("h1", Squares.name(squareAtOffset(75f, 75f, s, Side.White)!!))
        assertEquals("e2", Squares.name(squareAtOffset(45f, 65f, s, Side.White)!!))
        assertEquals("h1", Squares.name(squareAtOffset(5f, 5f, s, Side.Black)!!))
        assertEquals("a8", Squares.name(squareAtOffset(75f, 75f, s, Side.Black)!!))
        assertEquals("d7", Squares.name(squareAtOffset(45f, 65f, s, Side.Black)!!))
        // Edges belong to the next square; the far edge is still on the board.
        assertEquals("b8", Squares.name(squareAtOffset(10f, 0f, s, Side.White)!!))
        assertEquals("h1", Squares.name(squareAtOffset(79.99f, 79.99f, s, Side.White)!!))
    }

    @Test
    fun touchesOutsideTheBoardHitNoSquare() {
        assertNull(squareAtOffset(-1f, 5f, 10f, Side.White))
        assertNull(squareAtOffset(5f, -0.5f, 10f, Side.White))
        assertNull(squareAtOffset(80f, 5f, 10f, Side.White))
        assertNull(squareAtOffset(5f, 80f, 10f, Side.White))
        assertNull(squareAtOffset(5f, 5f, 0f, Side.White))
    }

    // --- Squares and pieces ---

    @Test
    fun everySquareIsFilledWithItsShade() {
        val fills = boardMarks(startBoard(Side.White)).filterIsInstance<BoardMark.SquareFill>()
        assertEquals((0..63).toList(), fills.map { it.square }.sorted())
        val shade = fills.associate { Squares.name(it.square) to it.light }
        assertEquals(false, shade["a1"])
        assertEquals(true, shade["h1"])
        assertEquals(true, shade["a8"])
        assertEquals(false, shade["h8"])
        assertEquals(true, shade["e4"])
        assertEquals(false, shade["d4"])
        assertTrue(fills.none { it.lastMove })
    }

    @Test
    fun piecesAreDrawnOnTheirSquaresInBothOrientations() {
        for (player in Side.entries) {
            val pieces = boardMarks(startBoard(player)).filterIsInstance<BoardMark.PieceMark>()
            assertEquals(32, pieces.size)
            val byName = pieces.associate { Squares.name(it.square) to it.piece }
            assertEquals(PieceUi(Side.White, PieceKind.King), byName["e1"])
            assertEquals(PieceUi(Side.White, PieceKind.Queen), byName["d1"])
            assertEquals(PieceUi(Side.Black, PieceKind.King), byName["e8"])
            assertEquals(PieceUi(Side.Black, PieceKind.Pawn), byName["a7"])
            assertNull(byName["e4"])
        }
        // Where the white king lands on screen depends on the orientation.
        val e1 = Squares.parse("e1")
        assertEquals(7 to 4, visualRow(e1, Side.White) to visualCol(e1, Side.White))
        assertEquals(0 to 3, visualRow(e1, Side.Black) to visualCol(e1, Side.Black))
    }

    // --- Coordinates ---

    @Test
    fun whiteCoordinatesSitOnTheAFileAndTheFirstRank() {
        val marks = boardMarks(startBoard(Side.White))
        val ranks = marks.filterIsInstance<BoardMark.RankLabel>()
        val files = marks.filterIsInstance<BoardMark.FileLabel>()
        assertEquals(
            listOf("a1" to "1", "a2" to "2", "a3" to "3", "a4" to "4", "a5" to "5", "a6" to "6", "a7" to "7", "a8" to "8"),
            ranks.map { Squares.name(it.square) to it.text }.sortedBy { it.second },
        )
        assertEquals(
            listOf("a1" to "a", "b1" to "b", "c1" to "c", "d1" to "d", "e1" to "e", "f1" to "f", "g1" to "g", "h1" to "h"),
            files.map { Squares.name(it.square) to it.text }.sortedBy { it.second },
        )
    }

    @Test
    fun blackCoordinatesSitOnTheHFileAndTheEighthRank() {
        val marks = boardMarks(startBoard(Side.Black))
        val ranks = marks.filterIsInstance<BoardMark.RankLabel>()
        val files = marks.filterIsInstance<BoardMark.FileLabel>()
        assertEquals(
            listOf("h1" to "1", "h2" to "2", "h3" to "3", "h4" to "4", "h5" to "5", "h6" to "6", "h7" to "7", "h8" to "8"),
            ranks.map { Squares.name(it.square) to it.text }.sortedBy { it.second },
        )
        assertEquals(
            listOf("a8" to "a", "b8" to "b", "c8" to "c", "d8" to "d", "e8" to "e", "f8" to "f", "g8" to "g", "h8" to "h"),
            files.map { Squares.name(it.square) to it.text }.sortedBy { it.second },
        )
    }

    @Test
    fun coordinateColourFollowsTheSquareShade() {
        val marks = boardMarks(startBoard(Side.White))
        val a1 = marks.filterIsInstance<BoardMark.RankLabel>().single { it.square == Squares.parse("a1") }
        val a2 = marks.filterIsInstance<BoardMark.RankLabel>().single { it.square == Squares.parse("a2") }
        val h1 = marks.filterIsInstance<BoardMark.FileLabel>().single { it.square == Squares.parse("h1") }
        assertEquals(false, a1.onLight)
        assertEquals(true, a2.onLight)
        assertEquals(true, h1.onLight)
    }

    // --- Cues ---

    @Test
    fun lastMoveTintsAndMarksBothSquares() {
        val e7 = Squares.parse("e7")
        val e5 = Squares.parse("e5")
        val marks = boardMarks(startBoard(Side.White).copy(lastMove = LastMoveUi(e7, e5)))
        val tinted = marks.filterIsInstance<BoardMark.SquareFill>().filter { it.lastMove }.map { it.square }
        assertEquals(setOf(e7, e5), tinted.toSet())
        assertEquals(setOf(e7, e5), marks.filterIsInstance<BoardMark.LastMoveCorner>().map { it.square }.toSet())
    }

    @Test
    fun checkDrawsATintAndABadgeOnTheKing() {
        val e1 = Squares.parse("e1")
        val marks = boardMarks(startBoard(Side.White).copy(checkSquare = e1))
        assertEquals(listOf(e1), marks.filterIsInstance<BoardMark.CheckTint>().map { it.square })
        assertEquals(listOf(e1), marks.filterIsInstance<BoardMark.CheckBadge>().map { it.square })
    }

    @Test
    fun selectionDrawsABorder() {
        val g1 = Squares.parse("g1")
        val marks = boardMarks(startBoard(Side.White).copy(selected = g1))
        assertEquals(listOf(g1), marks.filterIsInstance<BoardMark.SelectedBorder>().map { it.square })
    }

    @Test
    fun quietTargetsAreDotsAndCapturesAreRings() {
        val targets = mapOf(
            Squares.parse("a3") to TargetKind.Move,
            Squares.parse("g1") to TargetKind.Castling,
            Squares.parse("e8") to TargetKind.Promotion,
            Squares.parse("f7") to TargetKind.Capture,
            Squares.parse("d6") to TargetKind.EnPassant,
            Squares.parse("f8") to TargetKind.CapturePromotion,
        )
        val marks = boardMarks(startBoard(Side.White).copy(targets = targets))
        assertEquals(
            setOf("a3", "g1", "e8"),
            marks.filterIsInstance<BoardMark.MoveDot>().map { Squares.name(it.square) }.toSet(),
        )
        assertEquals(
            setOf("f7", "d6", "f8"),
            marks.filterIsInstance<BoardMark.CaptureRing>().map { Squares.name(it.square) }.toSet(),
        )
    }

    @Test
    fun noCuesWithoutCueState() {
        val marks = boardMarks(startBoard(Side.White))
        assertTrue(
            marks.none {
                it is BoardMark.CheckTint || it is BoardMark.LastMoveCorner || it is BoardMark.SelectedBorder ||
                    it is BoardMark.MoveDot || it is BoardMark.CaptureRing || it is BoardMark.CheckBadge
            },
        )
    }

    @Test
    fun marksComeInTheDesignLayerOrder() {
        val board = startBoard(Side.White).copy(
            selected = Squares.parse("e1"),
            targets = mapOf(Squares.parse("f7") to TargetKind.Capture, Squares.parse("a3") to TargetKind.Move),
            lastMove = LastMoveUi(Squares.parse("e7"), Squares.parse("e5")),
            checkSquare = Squares.parse("e1"),
        )
        val layers = boardMarks(board).map { it.layer }
        assertEquals(layers.sorted(), layers, "marks must be drawn bottom layer first")
        assertEquals(BoardMark.Layer.entries.toList(), layers.distinct())
    }

    private fun startBoard(player: Side) =
        BoardUi(pieces = BoardFixtures.pieces(BoardFixtures.START_PLACEMENT), player = player)
}
