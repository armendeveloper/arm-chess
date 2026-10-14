package chess.engine.rules

import chess.engine.board.PieceColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** US-5 game-end conditions other than repetition (see [RepetitionTest]), in the order of design §4.3. */
class GameStatusTest {
    private fun status(fen: String): GameStatus = game(fen).status

    @Test
    fun theStartPositionIsOngoing() {
        val start = Game.start()
        assertEquals(GameStatus.Ongoing, start.status)
        assertFalse(start.status.isOver)
    }

    // Checkmate.

    @Test
    fun foolsMateIsCheckmateForBlack() {
        val game = Game.start().play("f2f3", "e7e5", "g2g4", "d8h4")
        assertEquals(GameStatus.Checkmate(winner = PieceColor.BLACK), game.status)
        assertTrue(game.status.isOver)
    }

    @Test
    fun aBackRankMateIsCheckmateForWhite() {
        val before = game("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1")
        assertEquals(GameStatus.Ongoing, before.status)
        val game = before.play("a1a8")
        assertEquals(GameStatus.Checkmate(winner = PieceColor.WHITE), game.status)
    }

    @Test
    fun aCheckmateGivenInTheStartFenIsDetected() {
        assertEquals(GameStatus.Checkmate(winner = PieceColor.WHITE), status("R5k1/5ppp/8/8/8/8/8/6K1 b - - 0 1"))
    }

    @Test
    fun aCheckThatCanBeAnsweredIsNotMate() {
        // The back-rank check with an escape square on h7.
        assertEquals(GameStatus.Ongoing, game("6k1/5pp1/8/8/8/8/8/R5K1 w - - 0 1").play("a1a8").status)
    }

    // Stalemate.

    @Test
    fun aQueenStalemateIsADraw() {
        assertEquals(GameStatus.Stalemate, status("k7/8/1Q6/8/8/8/8/7K b - - 0 1"))
    }

    @Test
    fun aKingAndPawnStalemateIsADraw() {
        assertEquals(GameStatus.Stalemate, status("5k2/5P2/5K2/8/8/8/8/8 b - - 0 1"))
    }

    @Test
    fun aMoveThatLeavesTheOpponentNoMovesAndNoCheckIsStalemate() {
        val game = game("7k/8/6K1/5Q2/8/8/8/8 w - - 0 1").play("f5f7")
        assertEquals(GameStatus.Stalemate, game.status)
        assertIs<GameStatus.Draw>(game.status)
        assertTrue(game.status.isOver)
    }

    // Insufficient material: exactly the four PRD cases.

    @Test
    fun kingAgainstKingIsADraw() {
        assertEquals(GameStatus.InsufficientMaterial, status("8/8/4k3/8/8/4K3/8/8 w - - 0 1"))
    }

    @Test
    fun kingAndBishopAgainstKingIsADrawForEitherColour() {
        assertEquals(GameStatus.InsufficientMaterial, status("8/8/4k3/8/8/4K3/8/2B5 w - - 0 1"))
        assertEquals(GameStatus.InsufficientMaterial, status("8/8/4k3/8/8/4K3/8/5b2 w - - 0 1"))
    }

    @Test
    fun kingAndKnightAgainstKingIsADrawForEitherColour() {
        assertEquals(GameStatus.InsufficientMaterial, status("8/8/4k3/8/8/4K3/8/6N1 w - - 0 1"))
        assertEquals(GameStatus.InsufficientMaterial, status("8/8/4k3/8/8/4K3/8/6n1 b - - 0 1"))
    }

    @Test
    fun bishopsOnTheSameSquareColourAreADraw() {
        // c1 and f8 are both dark squares.
        assertEquals(GameStatus.InsufficientMaterial, status("5b2/8/4k3/8/8/4K3/8/2B5 w - - 0 1"))
        // d3 and h1 are both light squares.
        assertEquals(GameStatus.InsufficientMaterial, status("8/8/4k3/8/8/3BK3/8/7b w - - 0 1"))
    }

    @Test
    fun bishopsOnOppositeSquareColoursAreNotADraw() {
        // c1 is dark, c8 is light.
        assertEquals(GameStatus.Ongoing, status("2b5/8/4k3/8/8/4K3/8/2B5 w - - 0 1"))
    }

    @Test
    fun materialOutsideThePrdListIsNotADraw() {
        val notDraws = mapOf(
            "two knights" to "8/8/4k3/8/8/4K3/8/1N4N1 w - - 0 1",
            "a pawn" to "8/8/4k3/8/8/4K3/4P3/8 w - - 0 1",
            "two same-coloured bishops on one side" to "8/8/4k3/8/8/4K3/3B4/2B5 w - - 0 1",
            "bishop against knight" to "8/8/4k3/8/8/4K3/8/2B3n1 w - - 0 1",
            "knight against knight" to "8/8/4k3/8/8/4K3/8/1N4n1 w - - 0 1",
            "a rook" to "8/8/4k3/8/8/4K3/8/R7 w - - 0 1",
            "a queen" to "8/8/4k3/8/8/4K3/8/7q w - - 0 1",
            "same-coloured bishops plus a pawn" to "5b2/8/4k3/8/8/4K3/7P/2B5 w - - 0 1",
        )
        for ((name, fen) in notDraws) assertEquals(GameStatus.Ongoing, status(fen), name)
    }

    @Test
    fun aCaptureThatLeavesInsufficientMaterialEndsTheGame() {
        val before = game("8/8/4k3/8/8/2n1K3/8/B7 w - - 0 1")
        assertEquals(GameStatus.Ongoing, before.status, "bishop against knight is not on the list")
        assertEquals(GameStatus.InsufficientMaterial, before.play("a1c3").status)
    }

    // The 50-move rule.

    @Test
    fun theFiftyMoveRuleDrawsAtExactly100Halfmoves() {
        val games = game("8/8/4k3/8/8/4K3/8/R7 w - - 98 80").playAll("a1a2", "e6e7")
        assertEquals(98, games[0].position.halfmoveClock)
        assertEquals(GameStatus.Ongoing, games[0].status)
        assertEquals(99, games[1].position.halfmoveClock)
        assertEquals(GameStatus.Ongoing, games[1].status)
        assertEquals(100, games[2].position.halfmoveClock)
        assertEquals(GameStatus.FiftyMoveRule, games[2].status)
        assertIs<GameStatus.Draw>(games[2].status)
    }

    @Test
    fun aStartFenWithTheClockAt100IsAlreadyDrawn() {
        assertEquals(GameStatus.FiftyMoveRule, status("8/8/4k3/8/8/4K3/8/R7 w - - 100 80"))
    }

    @Test
    fun aCaptureOrPawnMoveOnThe100thHalfmoveResetsTheClock() {
        val capture = game("8/8/4k3/8/8/4K3/8/R5n1 w - - 99 80").play("a1g1")
        assertEquals(0, capture.position.halfmoveClock)
        assertEquals(GameStatus.Ongoing, capture.status)
        val pawn = game("8/8/4k3/8/8/4K3/P7/R7 w - - 99 80").play("a2a3")
        assertEquals(0, pawn.position.halfmoveClock)
        assertEquals(GameStatus.Ongoing, pawn.status)
    }

    // Precedence (design §4.3).

    @Test
    fun checkmateOnThe100thHalfmoveWins() {
        val game = game("6k1/5ppp/8/8/8/8/8/R5K1 w - - 99 70").play("a1a8")
        assertEquals(100, game.position.halfmoveClock)
        assertEquals(GameStatus.Checkmate(winner = PieceColor.WHITE), game.status)
    }

    @Test
    fun stalemateOnThe100thHalfmoveIsStalemate() {
        val game = game("7k/8/6K1/5Q2/8/8/8/8 w - - 99 70").play("f5f7")
        assertEquals(100, game.position.halfmoveClock)
        assertEquals(GameStatus.Stalemate, game.status)
    }

    @Test
    fun insufficientMaterialComesBeforeTheFiftyMoveRule() {
        assertEquals(GameStatus.InsufficientMaterial, status("8/8/4k3/8/8/4K3/8/8 w - - 100 80"))
    }

    @Test
    fun onlyOngoingIsNotOver() {
        val over = listOf(
            GameStatus.Checkmate(PieceColor.WHITE),
            GameStatus.Checkmate(PieceColor.BLACK),
            GameStatus.Stalemate,
            GameStatus.InsufficientMaterial,
            GameStatus.ThreefoldRepetition,
            GameStatus.FiftyMoveRule,
        )
        for (status in over) assertTrue(status.isOver, status.toString())
        assertFalse(GameStatus.Ongoing.isOver)
        assertEquals(4, over.count { it is GameStatus.Draw })
    }
}
