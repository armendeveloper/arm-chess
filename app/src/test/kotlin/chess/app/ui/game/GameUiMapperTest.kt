package chess.app.ui.game

import chess.app.ui.game.board.BoardFixtures
import chess.engine.rules.Game
import chess.engine.rules.Position
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Engine `Game` → UI state (tech design §6.3): pieces, orientation, last move, check square,
 * dots vs rings for every move kind, interactivity and the status row's state. Pure JVM.
 */
class GameUiMapperTest {

    // --- pieces and orientation ---

    @Test
    fun startPositionAsWhite() {
        val board = GameUiMapper.board(Game.start(), Side.White, selected = null)
        assertEquals(BoardFixtures.pieces(BoardFixtures.START_PLACEMENT), board.pieces)
        assertEquals(Side.White, board.player)
        assertNull(board.selected)
        assertTrue(board.targets.isEmpty())
        assertNull(board.lastMove)
        assertNull(board.checkSquare)
        assertTrue(board.interactive)
    }

    @Test
    fun blackIsAtTheBottomAndOwnsTheBlackPieces() {
        val board = GameUiMapper.board(Game.start(), Side.Black, selected = null)
        assertEquals(Side.Black, board.player)
        assertTrue(board.isOwnPiece(sq("e8")))
        assertFalse(board.isOwnPiece(sq("e1")))
        // White moves first, so the board is locked for a Black player at the start.
        assertFalse(board.interactive)
    }

    @Test
    fun everyPieceKindAndColourIsMapped() {
        val game = Game.start(Position.fromFen("rnbqk3/p7/8/8/8/8/P7/RNBQK3 w Qq - 0 1"))
        val pieces = GameUiMapper.board(game, Side.White, null).pieces
        val expected = BoardFixtures.pieces("rnbqk3/p7/8/8/8/8/P7/RNBQK3")
        assertEquals(expected, pieces)
    }

    // --- last move and check ---

    @Test
    fun lastMoveIsTheMostRecentPly() {
        val game = Game.replay(Position.START_FEN, listOf("e2e4", "e7e5", "g1f3"))
        val board = GameUiMapper.board(game, Side.White, null)
        assertEquals(LastMoveUi(sq("g1"), sq("f3")), board.lastMove)
    }

    @Test
    fun castlingLastMoveIsTheKingsMove() {
        val game = Game.replay("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", listOf("e1g1"))
        val board = GameUiMapper.board(game, Side.White, null)
        assertEquals(LastMoveUi(sq("e1"), sq("g1")), board.lastMove)
        assertEquals(PieceUi(Side.White, PieceKind.Rook), board.pieces[sq("f1")])
    }

    @Test
    fun checkSquareIsTheCheckedKing() {
        val white = GameUiMapper.board(game("4k3/8/8/8/8/8/8/4K2r w - - 0 1"), Side.White, null)
        assertEquals(sq("e1"), white.checkSquare)

        val black = GameUiMapper.board(game("4k2R/8/8/8/8/8/8/4K3 b - - 0 1"), Side.White, null)
        assertEquals(sq("e8"), black.checkSquare)
    }

    @Test
    fun checkmatedKingIsMarkedAndTheBoardIsLocked() {
        val board = GameUiMapper.board(foolsMate(), Side.White, null)
        assertEquals(sq("e1"), board.checkSquare)
        assertFalse(board.interactive)
    }

    // --- targets: dots and rings ---

    @Test
    fun quietMovesAreDots() {
        val board = GameUiMapper.board(Game.start(), Side.White, selected = sq("g1"))
        assertEquals(sq("g1"), board.selected)
        assertEquals(mapOf(sq("f3") to TargetKind.Move, sq("h3") to TargetKind.Move), board.targets)
    }

    @Test
    fun capturesAreRings() {
        val game = Game.replay(Position.START_FEN, listOf("e2e4", "d7d5"))
        val board = GameUiMapper.board(game, Side.White, selected = sq("e4"))
        assertEquals(mapOf(sq("e5") to TargetKind.Move, sq("d5") to TargetKind.Capture), board.targets)
        assertTrue(board.targets.getValue(sq("d5")).isCapture)
    }

    @Test
    fun enPassantDestinationIsACaptureRingOnAnEmptySquare() {
        val game = Game.replay(Position.START_FEN, listOf("e2e4", "a7a6", "e4e5", "d7d5"))
        val board = GameUiMapper.board(game, Side.White, selected = sq("e5"))
        assertEquals(mapOf(sq("e6") to TargetKind.Move, sq("d6") to TargetKind.EnPassant), board.targets)
        assertTrue(board.targets.getValue(sq("d6")).isCapture)
        assertNull(board.pieces[sq("d6")])
    }

    @Test
    fun castlingTargetsTheKingsDestination() {
        val board = GameUiMapper.board(game("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"), Side.White, sq("e1"))
        assertEquals(TargetKind.Castling, board.targets[sq("g1")])
        assertEquals(TargetKind.Castling, board.targets[sq("c1")])
        assertEquals(TargetKind.Move, board.targets[sq("f1")])
        assertEquals(TargetKind.Move, board.targets[sq("d1")])
        assertNull(board.targets[sq("h1")], "the rook's square is not a target")
        assertEquals(setOf("c1", "d1", "d2", "e2", "f2", "f1", "g1").map(::sq).toSet(), board.targets.keys)
    }

    @Test
    fun castlingAsBlackToo() {
        val board = GameUiMapper.board(game("r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1"), Side.Black, sq("e8"))
        assertEquals(TargetKind.Castling, board.targets[sq("g8")])
        assertEquals(TargetKind.Castling, board.targets[sq("c8")])
        assertTrue(board.interactive)
    }

    @Test
    fun promotionIsItsOwnKindAndCapturePromotionAnother() {
        val board = GameUiMapper.board(game("1n2k3/P7/8/8/8/8/8/4K3 w - - 0 1"), Side.White, sq("a7"))
        // Four promotion moves per destination collapse into one target each.
        assertEquals(mapOf(sq("a8") to TargetKind.Promotion, sq("b8") to TargetKind.CapturePromotion), board.targets)
    }

    @Test
    fun noTargetsForASelectionWithoutLegalMoves() {
        val board = GameUiMapper.board(Game.start(), Side.White, selected = sq("a1"))
        assertEquals(sq("a1"), board.selected)
        assertTrue(board.targets.isEmpty())
    }

    // --- status ---

    @Test
    fun playersTurnIsYourMoveOrCheck() {
        assertEquals(StatusUi.YourMove, GameUiMapper.status(Game.start(), Side.White))
        assertEquals(StatusUi.Check, GameUiMapper.status(game("4k3/8/8/8/8/8/8/4K2r w - - 0 1"), Side.White))
    }

    @Test
    fun computersTurnIsThinkingWithOrWithoutCheck() {
        assertEquals(StatusUi.Thinking(check = false), GameUiMapper.status(Game.start(), Side.Black))
        assertEquals(
            StatusUi.Thinking(check = true),
            GameUiMapper.status(game("4k3/8/8/8/8/8/8/4K2r w - - 0 1"), Side.Black),
        )
    }

    @Test
    fun hintReplacesYourMoveOnThePlayersTurn() {
        assertEquals(StatusUi.NoLegalMoves, GameUiMapper.status(Game.start(), Side.White, hint = true))
    }

    @Test
    fun engineErrorIsShownOnTheComputersTurn() {
        assertEquals(StatusUi.EngineError, GameUiMapper.status(Game.start(), Side.Black, engineError = true))
    }

    @Test
    fun checkmateIsAWinOrALossFromThePlayersSide() {
        assertEquals(StatusUi.GameOver(GameEnd.CheckmateLoss), GameUiMapper.status(foolsMate(), Side.White))
        assertEquals(StatusUi.GameOver(GameEnd.CheckmateWin), GameUiMapper.status(foolsMate(), Side.Black))
    }

    @Test
    fun everyDrawHasItsOwnEnding() {
        assertEquals(
            StatusUi.GameOver(GameEnd.Stalemate),
            GameUiMapper.status(game("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1"), Side.White),
        )
        assertEquals(
            StatusUi.GameOver(GameEnd.InsufficientMaterial),
            GameUiMapper.status(game("4k3/8/8/8/8/8/8/4K3 w - - 0 1"), Side.White),
        )
        assertEquals(
            StatusUi.GameOver(GameEnd.FiftyMoveRule),
            GameUiMapper.status(game("4k3/8/8/8/8/8/8/R3K3 w - - 100 80"), Side.White),
        )
        val shuffle = listOf("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8")
        assertEquals(
            StatusUi.GameOver(GameEnd.ThreefoldRepetition),
            GameUiMapper.status(Game.replay(Position.START_FEN, shuffle), Side.White),
        )
    }

    @Test
    fun gameOverWinsOverHintAndEngineError() {
        assertEquals(
            StatusUi.GameOver(GameEnd.CheckmateLoss),
            GameUiMapper.status(foolsMate(), Side.White, hint = true, engineError = true),
        )
    }

    // --- whole state ---

    @Test
    fun mapBuildsTheWholeScreenState() {
        val game = Game.replay(Position.START_FEN, listOf("e2e4"))
        val state = GameUiMapper.map(game, Side.White, level = 2, selected = null)
        assertEquals(2, state.level)
        assertEquals(Side.White, state.player)
        assertEquals(StatusUi.Thinking(check = false), state.status)
        assertFalse(state.board.interactive)
        assertEquals(LastMoveUi(sq("e2"), sq("e4")), state.board.lastMove)
    }

    @Test
    fun sidesConvertBothWays() {
        Side.entries.forEach { assertEquals(it, GameUiMapper.side(GameUiMapper.color(it))) }
    }

    @Test
    fun selectionOutsideTheBoardIsRejected() {
        assertFailsWith<IllegalArgumentException> { GameUiMapper.board(Game.start(), Side.White, selected = 64) }
    }

    private fun sq(name: String) = Squares.parse(name)

    private fun game(fen: String) = Game.start(Position.fromFen(fen))

    private fun foolsMate() = Game.replay(Position.START_FEN, listOf("f2f3", "e7e5", "g2g4", "d8h4"))
}
