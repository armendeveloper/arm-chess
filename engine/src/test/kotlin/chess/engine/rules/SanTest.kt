package chess.engine.rules

import chess.engine.board.Move
import chess.engine.board.PieceColor
import chess.engine.board.Square
import chess.engine.perft.PerftPositions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SanTest {
    private class Case(val fen: String, val uci: String, val san: String)

    private val start = Position.START_FEN
    private val twoBlackKnights = "1n2k3/8/5n2/8/8/8/8/4K3 b - - 0 1"
    private val rooksOnTheEFile = "k7/8/8/8/8/4R3/8/4R2K w - - 0 1"
    private val threeQueens = "2k5/8/8/8/4Q2Q/K7/8/4r2Q w - - 0 1"
    private val promotion = "k7/4P3/8/8/8/8/8/4K3 w - - 0 1"
    private val blackPromotion = "3r3k/8/8/4b3/8/8/1pK5/R6r b - - 0 1"
    private val rooksOnTheFirstRank = "4k3/8/8/8/8/8/4K3/R6R w - - 0 1"
    private val knightsOnTheGFile = "4k3/8/8/6N1/8/8/8/4K1N1 w - - 0 1"
    private val captureOrPromote = "1r2k3/P7/8/8/8/8/8/4K3 w - - 0 1"
    private val pawnCapture = "4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1"

    private val cases = listOf(
        Case(start, "e2e4", "e4"),
        Case(start, "g1f3", "Nf3"),
        Case(start, "b1c3", "Nc3"),
        // Disambiguation by file, by rank, and by both.
        Case(twoBlackKnights, "b8d7", "Nbd7"),
        Case(twoBlackKnights, "f6d7", "Nfd7"),
        Case(rooksOnTheEFile, "e1e2", "R1e2"),
        Case(rooksOnTheEFile, "e3e2", "R3e2"),
        Case(rooksOnTheFirstRank, "a1d1", "Rad1"),
        Case(rooksOnTheFirstRank, "h1d1", "Rhd1"),
        Case(knightsOnTheGFile, "g1f3", "N1f3"),
        Case(knightsOnTheGFile, "g5f3", "N5f3"),
        Case(threeQueens, "h4e1", "Qh4xe1"),
        Case(threeQueens, "h1e1", "Q1xe1"),
        Case(threeQueens, "e4e1", "Qexe1"),
        // A pinned knight that cannot reach e2 does not make the other one ambiguous.
        Case("4k3/8/8/b7/8/2N5/8/4K1N1 w - - 0 1", "g1e2", "Ne2"),
        // Captures, pawn captures and en passant.
        Case(pawnCapture, "e4d5", "exd5"),
        Case(pawnCapture, "e4e5", "e5"),
        Case("4k3/8/8/4p3/8/5N2/8/4K3 w - - 0 1", "f3e5", "Nxe5"),
        Case("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1", "e5d6", "exd6"),
        Case("8/4k3/8/3pP3/8/8/8/4K3 w - d6 0 1", "e5d6", "exd6+"),
        Case("4k3/8/8/8/8/8/3p4/4K3 w - - 0 1", "e1d2", "Kxd2"),
        Case(start, "e2e3", "e3"),
        // Promotions, with and without check, and an underpromotion that mates.
        Case(promotion, "e7e8q", "e8=Q+"),
        Case(promotion, "e7e8r", "e8=R+"),
        Case(promotion, "e7e8b", "e8=B"),
        Case(promotion, "e7e8n", "e8=N"),
        Case("8/4P3/5k2/8/8/8/8/4K3 w - - 0 1", "e7e8n", "e8=N+"),
        Case(captureOrPromote, "a7b8q", "axb8=Q+"),
        Case(captureOrPromote, "a7a8q", "a8=Q"),
        Case(blackPromotion, "b2a1n", "bxa1=N#"),
        Case(blackPromotion, "b2a1q", "bxa1=Q"),
        // Castling.
        Case("4k3/8/8/8/8/8/8/4K2R w K - 0 1", "e1g1", "O-O"),
        Case("3k4/8/8/8/8/8/8/R3K3 w Q - 0 1", "e1c1", "O-O-O+"),
        Case("4k2r/8/8/8/8/8/8/4K3 b k - 0 1", "e8g8", "O-O"),
        Case("r3k3/8/8/8/8/8/8/4K3 b q - 0 1", "e8c8", "O-O-O"),
        // Check, mate, and a stalemating move (no suffix).
        Case("4k3/8/8/8/8/8/8/R3K3 w - - 0 1", "a1a8", "Ra8+"),
        Case("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1", "a1a8", "Ra8#"),
        Case("k7/8/2K5/8/8/8/8/1Q6 w - - 0 1", "b1b6", "Qb6"),
    )

    @Test
    fun formatMatchesTheTable() {
        val failures = cases.mapNotNull { case ->
            val position = Position.fromFen(case.fen)
            val actual = San.format(position, position.move(case.uci))
            if (actual == case.san) null else "${case.uci} in ${case.fen}: expected ${case.san}, was $actual"
        }
        assertEquals(emptyList(), failures)
        assertEquals(38, cases.size)
    }

    @Test
    fun parseReadsEveryTableEntryBack() {
        for (case in cases) {
            val position = Position.fromFen(case.fen)
            assertEquals(position.move(case.uci), San.parse(position, case.san), "${case.san} in ${case.fen}")
        }
    }

    @Test
    fun formatRejectsAMoveThatIsNotLegal() {
        val start = Position.START
        assertFailsWith<IllegalMoveException> { San.format(start, Move.of(Square.parse("e2"), Square.parse("e5"))) }
        assertFailsWith<IllegalMoveException> { San.format(start, Move.of(Square.parse("e7"), Square.parse("e5"), flags = Move.DOUBLE_PUSH)) }
    }

    @Test
    fun theOperaGameMatchesItsPublishedPgn() {
        val ucis = listOf(
            "e2e4", "e7e5", "g1f3", "d7d6", "d2d4", "c8g4", "d4e5", "g4f3", "d1f3", "d6e5",
            "f1c4", "g8f6", "f3b3", "d8e7", "b1c3", "c7c6", "c1g5", "b7b5", "c3b5", "c6b5",
            "c4b5", "b8d7", "e1c1", "a8d8", "d1d7", "d8d7", "h1d1", "e7e6", "b5d7", "f6d7",
            "b3b8", "d7b8", "d1d8",
        )
        // Morphy v Duke Karl of Brunswick and Count Isouard, Paris 1858.
        val pgn = "1. e4 e5 2. Nf3 d6 3. d4 Bg4 4. dxe5 Bxf3 5. Qxf3 dxe5 6. Bc4 Nf6 7. Qb3 Qe7 " +
            "8. Nc3 c6 9. Bg5 b5 10. Nxb5 cxb5 11. Bxb5+ Nbd7 12. O-O-O Rd8 13. Rxd7 Rxd7 " +
            "14. Rd1 Qe6 15. Bxd7+ Nxd7 16. Qb8+ Nxb8 17. Rd8#"
        val game = Game.replay(Position.START_FEN, ucis)
        assertEquals(sanMoves(pgn), game.sanList())
        assertEquals(GameStatus.Checkmate(PieceColor.WHITE), game.status)
    }

    @Test
    fun theImmortalGameMatchesItsPublishedPgn() {
        val ucis = listOf(
            "e2e4", "e7e5", "f2f4", "e5f4", "f1c4", "d8h4", "e1f1", "b7b5", "c4b5", "g8f6",
            "g1f3", "h4h6", "d2d3", "f6h5", "f3h4", "h6g5", "h4f5", "c7c6", "g2g4", "h5f6",
            "h1g1", "c6b5", "h2h4", "g5g6", "h4h5", "g6g5", "d1f3", "f6g8", "c1f4", "g5f6",
            "b1c3", "f8c5", "c3d5", "f6b2", "f4d6", "c5g1", "e4e5", "b2a1", "f1e2", "b8a6",
            "f5g7", "e8d8", "f3f6", "g8f6", "d6e7",
        )
        // Anderssen v Kieseritzky, London 1851.
        val pgn = "1. e4 e5 2. f4 exf4 3. Bc4 Qh4+ 4. Kf1 b5 5. Bxb5 Nf6 6. Nf3 Qh6 7. d3 Nh5 " +
            "8. Nh4 Qg5 9. Nf5 c6 10. g4 Nf6 11. Rg1 cxb5 12. h4 Qg6 13. h5 Qg5 14. Qf3 Ng8 " +
            "15. Bxf4 Qf6 16. Nc3 Bc5 17. Nd5 Qxb2 18. Bd6 Bxg1 19. e5 Qxa1+ 20. Ke2 Na6 " +
            "21. Nxg7+ Kd8 22. Qf6+ Nxf6 23. Be7#"
        val game = Game.replay(Position.START_FEN, ucis)
        assertEquals(sanMoves(pgn), game.sanList())
        assertEquals(GameStatus.Checkmate(PieceColor.WHITE), game.status)

        // And the other way: the published SAN rebuilds the same game.
        val parsed = sanMoves(pgn).fold(Game.start()) { g, san -> g.play(San.parse(g.position, san)) }
        assertEquals(game, parsed)
    }

    @Test
    fun sanListMatchesFormatPlyByPly() {
        for (seed in 1..5) {
            val game = randomGameOf(seed)
            val expected = game.moves.indices.map { San.format(game.positions[it], game.moves[it]) }
            assertEquals(expected, game.sanList(), "seed $seed")
        }
        assertEquals(emptyList(), Game.start().sanList())
    }

    @Test
    fun everyLegalMoveOfThePerftPositionsHasADistinctSanThatParsesBack() {
        for (fen in PerftPositions.ALL) assertRoundTrips(Position.fromFen(fen), fen)
    }

    @Test
    fun everyPositionOfRandomGamesRoundTripsThroughSan() {
        for (seed in 1..30) {
            for ((position, _) in randomGame(seed)) assertRoundTrips(position, "seed $seed")
        }
    }

    @Test
    fun randomGamesFromThePerftPositionsRoundTripThroughSan() {
        for ((index, fen) in PerftPositions.ALL.withIndex()) {
            for (seed in 1..3) {
                for ((position, _) in randomGame(seed * 100 + index, maxPlies = 120, start = Position.fromFen(fen))) {
                    assertRoundTrips(position, "$fen seed $seed")
                }
            }
        }
    }

    private fun assertRoundTrips(position: Position, context: String) {
        val sans = position.legalMoves().map { move ->
            val san = San.format(position, move)
            assertEquals(move, San.parse(position, san), "$san in ${position.toFen()} ($context)")
            san
        }
        assertEquals(sans.size, sans.toSet().size, "duplicate SAN in ${position.toFen()}: $sans")
    }

    /** The SAN tokens of PGN movetext: drops move numbers. */
    private fun sanMoves(pgn: String): List<String> = pgn.split(' ').filter { it.isNotEmpty() && !it.endsWith('.') }
}
