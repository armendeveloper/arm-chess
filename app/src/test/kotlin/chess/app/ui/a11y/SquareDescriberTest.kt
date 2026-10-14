package chess.app.ui.a11y

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import chess.app.ui.game.CellUi
import chess.app.ui.game.LastMoveRole
import chess.app.ui.game.PieceKind
import chess.app.ui.game.PieceUi
import chess.app.ui.game.Side
import chess.app.ui.game.SquareActionUi
import chess.app.ui.game.Squares
import chess.app.ui.game.TargetKind
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The TalkBack square grammar (design §5.3), checked against the real English copy in
 * `strings.xml` through Robolectric's resources (tech design §8.1).
 */
@RunWith(AndroidJUnit4::class)
class SquareDescriberTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val describer = SquareDescriber(AndroidStrings(context.resources))

    // --- describe(): every example in design §5.3 ---

    @Test
    fun pieceOnly() {
        assertEquals("e4, white knight", describer.describe(cell("e4", WHITE_KNIGHT)))
    }

    @Test
    fun emptySquare() {
        assertEquals("d5, empty", describer.describe(cell("d5")))
    }

    @Test
    fun enemyPieceTheSelectedPieceCanTake() {
        assertEquals(
            "f7, black pawn, capture available",
            describer.describe(cell("f7", BLACK_PAWN, target = TargetKind.Capture)),
        )
    }

    @Test
    fun selectedKingInCheck() {
        assertEquals(
            "e1, white king, selected, in check",
            describer.describe(cell("e1", WHITE_KING, selected = true, inCheck = true)),
        )
    }

    @Test
    fun castlingDestination() {
        assertEquals(
            "g1, empty, move available, castling",
            describer.describe(cell("g1", target = TargetKind.Castling)),
        )
    }

    @Test
    fun enPassantDestination() {
        assertEquals(
            "d6, empty, capture available, en passant",
            describer.describe(cell("d6", target = TargetKind.EnPassant)),
        )
    }

    @Test
    fun promotionDestination() {
        assertEquals(
            "e8, empty, move available, promotes",
            describer.describe(cell("e8", target = TargetKind.Promotion)),
        )
    }

    @Test
    fun lastMoveTo() {
        assertEquals(
            "e5, black pawn, last move to",
            describer.describe(cell("e5", BLACK_PAWN, lastMove = LastMoveRole.To)),
        )
    }

    // --- describe(): the remaining words and their fixed order ---

    @Test
    fun lastMoveFrom() {
        assertEquals("e7, empty, last move from", describer.describe(cell("e7", lastMove = LastMoveRole.From)))
    }

    @Test
    fun plainMoveAvailable() {
        assertEquals("e3, empty, move available", describer.describe(cell("e3", target = TargetKind.Move)))
    }

    @Test
    fun captureThatPromotes() {
        assertEquals(
            "f8, black rook, capture available, promotes",
            describer.describe(cell("f8", BLACK_ROOK, target = TargetKind.CapturePromotion)),
        )
    }

    @Test
    fun stateWordsKeepTheGrammarOrder() {
        // selected → in check → last move (the selected piece's own square was the last move's destination).
        assertEquals(
            "g8, black king, selected, in check, last move to",
            describer.describe(
                cell("g8", BLACK_KING, selected = true, inCheck = true, lastMove = LastMoveRole.To),
            ),
        )
        // target words come before the last-move word.
        assertEquals(
            "d5, black queen, capture available, last move to",
            describer.describe(cell("d5", BLACK_QUEEN, target = TargetKind.Capture, lastMove = LastMoveRole.To)),
        )
        // in check without selection.
        assertEquals(
            "e8, black king, in check, last move from",
            describer.describe(cell("e8", BLACK_KING, inCheck = true, lastMove = LastMoveRole.From)),
        )
    }

    @Test
    fun everyPieceHasItsOwnName() {
        val expected = mapOf(
            PieceUi(Side.White, PieceKind.King) to "white king",
            PieceUi(Side.White, PieceKind.Queen) to "white queen",
            PieceUi(Side.White, PieceKind.Rook) to "white rook",
            PieceUi(Side.White, PieceKind.Bishop) to "white bishop",
            PieceUi(Side.White, PieceKind.Knight) to "white knight",
            PieceUi(Side.White, PieceKind.Pawn) to "white pawn",
            PieceUi(Side.Black, PieceKind.King) to "black king",
            PieceUi(Side.Black, PieceKind.Queen) to "black queen",
            PieceUi(Side.Black, PieceKind.Rook) to "black rook",
            PieceUi(Side.Black, PieceKind.Bishop) to "black bishop",
            PieceUi(Side.Black, PieceKind.Knight) to "black knight",
            PieceUi(Side.Black, PieceKind.Pawn) to "black pawn",
        )
        expected.forEach { (piece, name) ->
            assertEquals("a1, $name", describer.describe(cell("a1", piece)))
        }
    }

    @Test
    fun coordinatesAreLowerCaseFileThenRank() {
        assertEquals("a1, empty", describer.describe(cell("a1")))
        assertEquals("h8, empty", describer.describe(cell("h8")))
    }

    // --- clickLabel(): design §5.3 click actions ---

    @Test
    fun ownPieceOnThePlayersTurnCanBeSelected() {
        assertEquals("select", describer.clickLabel(cell("g1", WHITE_KNIGHT), Side.White, interactive = true))
    }

    @Test
    fun selectedPieceCanBeDeselected() {
        assertEquals(
            "deselect",
            describer.clickLabel(cell("g1", WHITE_KNIGHT, selected = true), Side.White, interactive = true),
        )
    }

    @Test
    fun legalDestinationsMoveOrCapture() {
        assertEquals("move here", describer.clickLabel(cell("f3", target = TargetKind.Move), Side.White, true))
        assertEquals("move here", describer.clickLabel(cell("g1", target = TargetKind.Castling), Side.White, true))
        assertEquals("move here", describer.clickLabel(cell("e8", target = TargetKind.Promotion), Side.White, true))
        assertEquals(
            "capture",
            describer.clickLabel(cell("f6", BLACK_KNIGHT, target = TargetKind.Capture), Side.White, true),
        )
        assertEquals("capture", describer.clickLabel(cell("d6", target = TargetKind.EnPassant), Side.White, true))
        assertEquals(
            "capture",
            describer.clickLabel(cell("f8", BLACK_ROOK, target = TargetKind.CapturePromotion), Side.White, true),
        )
    }

    @Test
    fun ownPieceForBlackPlayer() {
        assertEquals("select", describer.clickLabel(cell("e7", BLACK_PAWN), Side.Black, interactive = true))
        assertNull(describer.clickLabel(cell("e2", WHITE_PAWN), Side.Black, interactive = true))
    }

    @Test
    fun everythingElseIsReadOnly() {
        assertNull(describer.clickLabel(cell("e7", BLACK_PAWN), Side.White, interactive = true))
        assertNull(describer.clickLabel(cell("d5"), Side.White, interactive = true))
        assertNull(describer.clickLabel(cell("e5", lastMove = LastMoveRole.To), Side.White, interactive = true))
    }

    @Test
    fun notThePlayersTurnMeansNoClickActionAnywhere() {
        assertNull(describer.clickLabel(cell("g1", WHITE_KNIGHT), Side.White, interactive = false))
        assertNull(describer.clickLabel(cell("g1", WHITE_KNIGHT, selected = true), Side.White, interactive = false))
        assertNull(describer.clickLabel(cell("f3", target = TargetKind.Move), Side.White, interactive = false))
    }

    // --- actionLabel(): design §5.3 custom actions (wired onto squares in T-24) ---

    @Test
    fun quietMoveAction() {
        assertEquals("Move to f3", describer.actionLabel(SquareActionUi(sq("f3"), TargetKind.Move)))
    }

    @Test
    fun captureActionsNameTheCapturedPiece() {
        assertEquals(
            "Take knight on f6",
            describer.actionLabel(SquareActionUi(sq("f6"), TargetKind.Capture, PieceKind.Knight)),
        )
        assertEquals(
            "Take pawn on d5",
            describer.actionLabel(SquareActionUi(sq("d5"), TargetKind.Capture, PieceKind.Pawn)),
        )
        assertEquals(
            "Take bishop on c4",
            describer.actionLabel(SquareActionUi(sq("c4"), TargetKind.Capture, PieceKind.Bishop)),
        )
        assertEquals(
            "Take rook on a8",
            describer.actionLabel(SquareActionUi(sq("a8"), TargetKind.Capture, PieceKind.Rook)),
        )
        assertEquals(
            "Take queen on d8",
            describer.actionLabel(SquareActionUi(sq("d8"), TargetKind.Capture, PieceKind.Queen)),
        )
    }

    @Test
    fun enPassantAction() {
        assertEquals(
            "Take pawn en passant on d6",
            describer.actionLabel(SquareActionUi(sq("d6"), TargetKind.EnPassant, PieceKind.Pawn)),
        )
    }

    @Test
    fun castlingActions() {
        assertEquals("Castle kingside", describer.actionLabel(SquareActionUi(sq("g1"), TargetKind.Castling)))
        assertEquals("Castle queenside", describer.actionLabel(SquareActionUi(sq("c1"), TargetKind.Castling)))
        assertEquals("Castle kingside", describer.actionLabel(SquareActionUi(sq("g8"), TargetKind.Castling)))
        assertEquals("Castle queenside", describer.actionLabel(SquareActionUi(sq("c8"), TargetKind.Castling)))
    }

    @Test
    fun promotionActions() {
        assertEquals(
            "Move to e8 and promote",
            describer.actionLabel(SquareActionUi(sq("e8"), TargetKind.Promotion)),
        )
        assertEquals(
            "Take rook on f8 and promote",
            describer.actionLabel(SquareActionUi(sq("f8"), TargetKind.CapturePromotion, PieceKind.Rook)),
        )
        assertEquals(
            "Take knight on b1 and promote",
            describer.actionLabel(SquareActionUi(sq("b1"), TargetKind.CapturePromotion, PieceKind.Knight)),
        )
        assertEquals(
            "Take bishop on c8 and promote",
            describer.actionLabel(SquareActionUi(sq("c8"), TargetKind.CapturePromotion, PieceKind.Bishop)),
        )
        assertEquals(
            "Take queen on d1 and promote",
            describer.actionLabel(SquareActionUi(sq("d1"), TargetKind.CapturePromotion, PieceKind.Queen)),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun captureActionWithoutACapturedPieceIsRejected() {
        describer.actionLabel(SquareActionUi(sq("f6"), TargetKind.Capture))
    }

    private fun sq(name: String) = Squares.parse(name)

    private fun cell(
        name: String,
        piece: PieceUi? = null,
        selected: Boolean = false,
        inCheck: Boolean = false,
        target: TargetKind? = null,
        lastMove: LastMoveRole? = null,
    ) = CellUi(sq(name), piece, selected, inCheck, target, lastMove)

    private companion object {
        val WHITE_KING = PieceUi(Side.White, PieceKind.King)
        val WHITE_KNIGHT = PieceUi(Side.White, PieceKind.Knight)
        val WHITE_PAWN = PieceUi(Side.White, PieceKind.Pawn)
        val BLACK_KING = PieceUi(Side.Black, PieceKind.King)
        val BLACK_QUEEN = PieceUi(Side.Black, PieceKind.Queen)
        val BLACK_ROOK = PieceUi(Side.Black, PieceKind.Rook)
        val BLACK_KNIGHT = PieceUi(Side.Black, PieceKind.Knight)
        val BLACK_PAWN = PieceUi(Side.Black, PieceKind.Pawn)
    }
}
