package chess.engine.rules

import chess.engine.board.FenException
import chess.engine.board.Move
import chess.engine.board.Piece
import chess.engine.board.PieceColor
import chess.engine.board.Square
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GameTest {
    private fun sq(name: String) = Square.parse(name)

    @Test
    fun aNewGameHasOnePositionAndNoMoves() {
        val game = Game.start()
        assertEquals(listOf(Position.START), game.positions)
        assertEquals(emptyList(), game.moves)
        assertEquals(Position.START, game.startPosition)
        assertEquals(Position.START, game.position)
        assertEquals(GameStatus.Ongoing, game.status)
    }

    @Test
    fun playAppendsTheMoveAndTheNextPositionWithoutChangingTheOriginal() {
        val start = Game.start()
        val e4 = start.position.move("e2e4")
        val game = start.play(e4)
        assertEquals(listOf(e4), game.moves)
        assertEquals(2, game.positions.size)
        assertEquals(Position.START.play(e4), game.position)
        assertEquals(Position.START, game.startPosition)
        assertEquals(1, start.positions.size, "the original game is unchanged")
        assertEquals(0, start.moves.size)
    }

    @Test
    fun playRejectsAnIllegalMove() {
        val start = Game.start()
        assertFailsWith<IllegalMoveException> { start.play(Move.of(sq("e2"), sq("e5"))) }
        assertFailsWith<IllegalMoveException> { start.play(Move.of(sq("e7"), sq("e5"), flags = Move.DOUBLE_PUSH)) }
    }

    @Test
    fun playRejectsAnyMoveOnceTheGameIsOver() {
        val drawn = Game.start().play("g1f3", "g8f6", "f3g1", "f6g8", "b1c3", "b8c6", "c3b1", "c6b8")
        assertEquals(GameStatus.ThreefoldRepetition, drawn.status)
        // Legal on the board, but the game has ended automatically (OQ-3).
        assertTrue(drawn.position.isLegal(drawn.position.move("e2e4")))
        assertFailsWith<IllegalMoveException> { drawn.play(drawn.position.move("e2e4")) }
    }

    @Test
    fun legalMovesFromListsThePieceOnTheSquare() {
        val game = Game.start()
        assertEquals(setOf("e2e3", "e2e4"), game.legalMovesFrom(sq("e2")).map { it.uci() }.toSet())
        assertEquals(setOf("g1f3", "g1h3"), game.legalMovesFrom(sq("g1")).map { it.uci() }.toSet())
        assertEquals(emptyList(), game.legalMovesFrom(sq("e4")), "empty square")
        assertEquals(emptyList(), game.legalMovesFrom(sq("e7")), "opponent's piece")
        assertEquals(emptyList(), game.legalMovesFrom(Square.E1), "a piece with no moves")
    }

    @Test
    fun legalMovesFromIsEmptyOnceTheGameIsOver() {
        val drawn = game("8/8/4k3/8/8/4K3/8/8 w - - 0 1")
        assertEquals(GameStatus.InsufficientMaterial, drawn.status)
        assertEquals(emptyList(), drawn.legalMovesFrom(sq("e3")))
    }

    @Test
    fun undoTwoPliesRestoresThePositionAndTheStatus() {
        val beforeMate = Game.start().play("f2f3", "e7e5")
        val mated = beforeMate.play("g2g4", "d8h4")
        assertEquals(GameStatus.Checkmate(PieceColor.BLACK), mated.status)

        val undone = mated.undo(2)
        assertEquals(beforeMate.position, undone.position)
        assertEquals(beforeMate.moves, undone.moves)
        assertEquals(beforeMate.positions, undone.positions)
        assertEquals(GameStatus.Ongoing, undone.status)
        assertEquals(beforeMate, undone)
        assertEquals(PieceColor.WHITE, undone.position.sideToMove)
    }

    @Test
    fun undoRestoresAnOngoingStatusAfterAnAutomaticDraw() {
        val drawn = Game.start().play("g1f3", "g8f6", "f3g1", "f6g8", "b1c3", "b8c6", "c3b1", "c6b8")
        assertEquals(GameStatus.ThreefoldRepetition, drawn.status)
        val undone = drawn.undo(1)
        assertEquals(GameStatus.Ongoing, undone.status)
        // Playing the same move again reaches the same draw.
        assertEquals(GameStatus.ThreefoldRepetition, undone.play("c6b8").status)
    }

    @Test
    fun undoDefaultsToOnePlyAndAcceptsZeroAndTheWholeGame() {
        val game = Game.start().play("e2e4", "e7e5", "g1f3")
        assertEquals(2, game.undo().moves.size)
        assertSame(game, game.undo(0))
        assertEquals(Game.start(), game.undo(3))
        assertFailsWith<IllegalArgumentException> { game.undo(4) }
        assertFailsWith<IllegalArgumentException> { game.undo(-1) }
    }

    @Test
    fun replayRebuildsTheGameFromTheStartFenAndUciMoves() {
        val replayed = Game.replay(Position.START_FEN, listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5"))
        assertEquals(Game.start().play("e2e4", "e7e5", "g1f3", "b8c6", "f1b5"), replayed)
        assertEquals(listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5"), replayed.moves.map { it.uci() })
        assertEquals(Game.start(), Game.replay(Position.START_FEN, emptyList()))
    }

    @Test
    fun replayResolvesCastlingEnPassantAndPromotionFromUci() {
        val castled = Game.replay("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", listOf("e1g1", "e8c8"))
        assertEquals("2kr3r/8/8/8/8/8/8/R4RK1 w - - 2 2", castled.position.toFen())
        assertTrue(castled.moves.all { it.isCastle })

        val enPassant = Game.replay("4k3/8/8/2PpP3/8/8/8/4K3 w - d6 0 1", listOf("e5d6"))
        assertTrue(enPassant.moves.single().isEnPassant)
        assertEquals(Piece.NONE, enPassant.position.pieceAt(sq("d5")))

        val underPromotion = Game.replay("4k3/P7/8/8/8/8/8/4K3 w - - 0 1", listOf("a7a8n"))
        assertEquals(Piece.WHITE_KNIGHT, underPromotion.position.pieceAt(Square.A8))
    }

    @Test
    fun replayRejectsAnIllegalOrMalformedMove() {
        val illegal = assertFailsWith<IllegalMoveException> {
            Game.replay(Position.START_FEN, listOf("e2e4", "e7e5", "e1e3"))
        }
        assertTrue("e1e3" in illegal.message.orEmpty(), illegal.message)
        assertFailsWith<IllegalMoveException> { Game.replay(Position.START_FEN, listOf("e2e4", "e2e4")) }
        assertFailsWith<IllegalMoveException> { Game.replay(Position.START_FEN, listOf("zz9")) }
        assertFailsWith<IllegalMoveException> { Game.replay(Position.START_FEN, listOf("")) }
        // A promotion needs its piece letter, and a non-promotion must not have one.
        assertFailsWith<IllegalMoveException> { Game.replay("4k3/P7/8/8/8/8/8/4K3 w - - 0 1", listOf("a7a8")) }
        assertFailsWith<IllegalMoveException> { Game.replay(Position.START_FEN, listOf("e2e4q")) }
    }

    @Test
    fun replayRejectsMovesAfterTheGameHasEnded() {
        assertFailsWith<IllegalMoveException> {
            Game.replay(Position.START_FEN, listOf("f2f3", "e7e5", "g2g4", "d8h4", "a2a3"))
        }
    }

    @Test
    fun replayRejectsAnInvalidStartFen() {
        assertFailsWith<FenException> { Game.replay("not a fen", emptyList()) }
        assertFailsWith<FenException> { Game.replay("4k3/8/8/8/8/8/8/4R1K1 w - - 0 1", emptyList()) }
    }

    @Test
    fun gamesAreEqualWhenTheirPositionsAndMovesAre() {
        val a = Game.start().play("e2e4")
        assertEquals(a, Game.start().play("e2e4"))
        assertEquals(a.hashCode(), Game.start().play("e2e4").hashCode())
        assertNotEquals(a, Game.start().play("d2d4"))
        assertNotEquals(a, Game.start())
    }

    @Test
    fun theGameListsCannotBeChangedByTheCaller() {
        val game = Game.start().play("e2e4")
        @Suppress("UNCHECKED_CAST")
        val moves = game.moves as? MutableList<Move>
        if (moves != null) assertFailsWith<UnsupportedOperationException> { moves.clear() }
        @Suppress("UNCHECKED_CAST")
        val positions = game.positions as? MutableList<Position>
        if (positions != null) assertFailsWith<UnsupportedOperationException> { positions.clear() }
        assertEquals(1, game.moves.size)
    }

    @Test
    fun replaying2000PliesTakesUnder50Milliseconds() {
        val ucis = longGame(plies = 2_000, seed = 6)
        assertEquals(2_000, ucis.size)

        val times = LongArray(5)
        var replayed: Game? = null
        for (run in times.indices) {
            val start = System.nanoTime()
            replayed = Game.replay(Position.START_FEN, ucis)
            times[run] = System.nanoTime() - start
        }
        val bestMs = times.min() / 1_000_000.0
        println("T-6 replay of 2,000 plies: best %.1f ms; runs (ms) %s".format(bestMs, times.joinToString { "%.1f".format(it / 1_000_000.0) }))
        assertEquals(GameStatus.Ongoing, replayed!!.status)
        assertEquals(2_001, replayed.positions.size)
        assertTrue(bestMs < 50.0, "replaying 2,000 plies took $bestMs ms")
    }

    /**
     * A legal game of [plies] plies that never ends: random moves that keep the game ongoing,
     * preferring piece moves over captures, and pawn pushes when the 50-move clock gets close.
     */
    private fun longGame(plies: Int, seed: Int): List<String> {
        val random = Random(seed)
        var game = Game.start()
        val ucis = ArrayList<String>(plies)
        while (ucis.size < plies) {
            val position = game.position
            val moves = position.legalMoves().shuffled(random)
            val isPawn = { m: Move -> Piece.typeOf(position.pieceAt(m.from)) == Piece.PAWN }
            val pawnPushes = moves.filter { isPawn(it) && !it.isCapture }
            val pieceMoves = moves.filter { !isPawn(it) && !it.isCapture }
            val captures = moves.filter { it.isCapture }
            val ordered = if (position.halfmoveClock >= 90) pawnPushes + pieceMoves + captures else pieceMoves + pawnPushes + captures
            val next = ordered.firstNotNullOfOrNull { move -> game.play(move).takeIf { it.status == GameStatus.Ongoing } }
                ?: error("seed $seed: no move keeps the game going at ply ${ucis.size} in ${position.toFen()}")
            ucis += next.moves.last().uci()
            game = next
        }
        return ucis
    }
}
