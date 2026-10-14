package chess.engine.search

import chess.engine.board.Attacks
import chess.engine.board.Fen
import chess.engine.board.Move
import chess.engine.board.Piece
import chess.engine.board.PieceColor
import chess.engine.board.PieceType
import chess.engine.rules.Game
import chess.engine.rules.GameStatus
import chess.engine.rules.Position
import chess.engine.rules.play
import chess.engine.perft.PerftPositions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SearchTest {
    private val mate = SearchResult.MATE_SCORE

    // --- Mates -------------------------------------------------------------------------------

    private val mateInOne = listOf(
        "6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1", // back rank: Ra8#
        "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 2 3", // Qxf7#
        "rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq g3 0 2", // fool's mate: Qh4#
        "k7/8/1K6/8/8/8/7Q/8 w - - 0 1", // queen and king: Qh8#
        "6rk/6pp/8/6N1/8/8/8/6K1 w - - 0 1", // smothered: Nf7#
    )

    private val mateInTwo = listOf(
        "r5k1/5ppp/8/8/8/8/4RPPP/4R1K1 w - - 0 1", // Re8+ Rxe8 Rxe8#
        "7k/8/8/8/8/8/R7/1R4K1 w - - 0 1", // the rook ladder
        "kbK5/pp6/1P6/8/8/8/8/R7 w - - 0 1", // 1.Ra6! bxa6 2.b7#
    )

    private val mateInThree = listOf(
        "rr4k1/5ppp/8/8/8/4Q3/4RPPP/4R1K1 w - - 0 1", // Re8+ Rxe8 Rxe8+ Rxe8 Qxe8#
    )

    @Test
    fun theMateTestPositionsAreWhatTheySayTheyAre() {
        for (fen in mateInOne) assertTrue(hasForcedMate(fen, 1), "mate in 1: $fen")
        for (fen in mateInTwo) {
            assertTrue(hasForcedMate(fen, 2), "mate in 2: $fen")
            assertFalse(hasForcedMate(fen, 1), "no mate in 1: $fen")
        }
        for (fen in mateInThree) {
            assertTrue(hasForcedMate(fen, 3), "mate in 3: $fen")
            assertFalse(hasForcedMate(fen, 2), "no mate in 2: $fen")
        }
    }

    @Test
    fun findsMateInOne() {
        for (fen in mateInOne) {
            val game = gameOf(fen)
            val result = searchFresh(game, fixedDepth(4))
            assertTrue(game.play(result.move).status is GameStatus.Checkmate, "$fen: ${result.move} is not mate")
            assertEquals(mate - 1, result.scoreCp, fen)
            assertEquals(1, result.mateInPlies, fen)
        }
    }

    @Test
    fun findsMateInTwoAtDepthFour() {
        for (fen in mateInTwo) {
            val result = searchFresh(gameOf(fen), fixedDepth(4))
            assertEquals(mate - 3, result.scoreCp, "$fen: ${result.move}")
            assertTrue(moveForcesMate(fen, result.move, 2), "$fen: ${result.move} does not force mate in 2")
        }
    }

    @Test
    fun findsMateInThree() {
        for (fen in mateInThree) {
            val result = searchFresh(gameOf(fen), fixedDepth(6))
            assertEquals(mate - 5, result.scoreCp, "$fen: ${result.move}")
            assertTrue(moveForcesMate(fen, result.move, 3), "$fen: ${result.move} does not force mate in 3")
        }
    }

    @Test
    fun theCheckExtensionFindsACheckingMateInTwoAtDepthTwo() {
        // Without the extension, mate in 2 needs depth 3; every white move here gives check.
        val result = searchFresh(gameOf("r5k1/5ppp/8/8/8/8/4RPPP/4R1K1 w - - 0 1"), fixedDepth(2))
        assertEquals(mate - 3, result.scoreCp, "${result.move}")
    }

    @Test
    fun theSideBeingMatedSeesItComing() {
        // Black to move (Kb8, h6 or h5); whatever Black does, Rd8# follows.
        val result = searchFresh(gameOf("k7/7p/1K6/8/8/8/8/3R4 b - - 0 1"), fixedDepth(3))
        assertEquals(-(mate - 2), result.scoreCp)
        assertEquals(-2, result.mateInPlies)
    }

    @Test
    fun aMateOnTheHundredthHalfmoveWinsInsteadOfDrawing() {
        val result = searchFresh(gameOf("k7/8/1K6/8/8/8/8/2R5 w - - 99 80"), fixedDepth(3))
        assertEquals("c1c8", result.move.uci())
        assertEquals(mate - 1, result.scoreCp)
    }

    // --- Material ----------------------------------------------------------------------------

    @Test
    fun takesFreeMaterial() {
        assertEquals("d1g4", searchFresh(gameOf("rnb1kbnr/pppp1ppp/8/4p3/4P1q1/8/PPPP1PPP/RNBQKBNR w KQkq - 2 3"), budget(50_000)).move.uci())
        assertEquals("d2d5", searchFresh(gameOf("4k3/8/8/3r4/8/8/3R4/4K3 w - - 0 1"), budget(50_000)).move.uci())
    }

    private val queenAttacked = listOf(
        "rnb1kbnr/pppp1ppp/8/4p1q1/3P4/8/PPP1PPPP/RNBQKBNR b KQkq - 0 3", // Bc1 attacks the queen on g5
        "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/4P3/8/PPPP1PPP/RNB1KBNR w KQkq - 4 4", // Nf6 attacks Qh5; Qxf7+ and Qxe5+ lose it
        "rnbqkbnr/pppp1ppp/8/4p3/3Q4/8/PPP1PPPP/RNB1KBNR w KQkq - 0 3", // e5 pawn attacks Qd4
        "rnbqkbnr/ppp2ppp/3p4/4p3/4P1Q1/8/PPPP1PPP/RNB1KBNR w KQkq - 0 3", // Bc8 attacks Qg4; Qxc8+ loses it
        "rnb1kbnr/pppp1ppp/8/4p3/4P2q/5N2/PPPP1PPP/RNBQKB1R b KQkq - 3 3", // Nf3 attacks Qh4; Qxf2+ loses it
    )

    @Test
    fun doesNotLeaveTheQueenEnPriseAtFiftyThousandNodes() {
        for (fen in queenAttacked) {
            val game = gameOf(fen)
            val mover = game.position.sideToMove
            val result = searchFresh(game, budget(50_000))
            val after = game.position.play(result.move)
            assertFalse(queenEnPrise(after, mover), "$fen: ${result.move} leaves the queen en prise")
            assertTrue(result.scoreCp > -200, "$fen: ${result.move} scored ${result.scoreCp}")
        }
    }

    /**
     * True when the opponent (to move in [position]) can take a queen of [owner] with a cheaper
     * piece, or take it when nothing defends it.
     */
    private fun queenEnPrise(position: Position, owner: PieceColor): Boolean {
        val board = Fen.parse(position.toFen())
        val queen = Piece.of(owner.code, Piece.QUEEN)
        for (move in position.legalMoves()) {
            if (board.pieceAt(move.to) != queen) continue
            val attacker = Piece.type(board.pieceAt(move.from))
            if (attacker != PieceType.QUEEN) return true
            if (!Attacks.isAttacked(board, move.to, owner.code)) return true
        }
        return false
    }

    // --- Quiescence --------------------------------------------------------------------------

    private val poisonedPawn = "4k3/8/2p5/3p4/8/8/3Q4/4K3 w - - 0 1" // Qxd5?? cxd5

    @Test
    fun withoutQuiescenceADepthOneSearchGrabsADefendedPawn() {
        assertEquals("d2d5", searchFresh(gameOf(poisonedPawn), fixedDepth(1, Quiescence.Off)).move.uci())
    }

    @Test
    fun quiescenceResolvesTheRecaptureAtDepthOne() {
        val result = searchFresh(gameOf(poisonedPawn), fixedDepth(1, Quiescence.Full))
        assertNotEquals("d2d5", result.move.uci())
        assertTrue(result.scoreCp > 500, "still a queen up: ${result.scoreCp}")
    }

    @Test
    fun capturesOnlyQuiescenceStopsAtItsPlyLimit() {
        // Rxd5 Rxd5 Rxd5 wins a pawn, but only if the sequence is seen to the end: stopped after
        // Black's recapture, it looks like a rook for a pawn.
        val fen = "3rk3/8/8/3p4/8/8/3R4/3RK3 w - - 0 1"
        val full = searchFresh(gameOf(fen), fixedDepth(1, Quiescence.Full))
        assertEquals("d2d5", full.move.uci())
        val unlimitedCaptures = searchFresh(gameOf(fen), fixedDepth(1, Quiescence.CapturesOnly(maxPlies = 64)))
        assertEquals(full.copy(elapsedMs = 0, nodes = 0), unlimitedCaptures.copy(elapsedMs = 0, nodes = 0))
        val limited = searchFresh(gameOf(fen), fixedDepth(1, Quiescence.CapturesOnly(maxPlies = 1)))
        assertNotEquals("d2d5", limited.move.uci())
    }

    // --- Draws inside the search --------------------------------------------------------------

    @Test
    fun theWinningSideNeverStalemates() {
        // Qb6 and Qc7 stalemate; there is no mate in one.
        val fen = "k7/8/8/1Q6/8/8/8/K7 w - - 0 1"
        val game = gameOf(fen)
        val result = searchFresh(game, fixedDepth(4))
        assertNotEquals(GameStatus.Stalemate, game.play(result.move).status, "${result.move}")
        assertTrue(result.scoreCp > 500, "${result.scoreCp}")
    }

    @Test
    fun capturingIntoInsufficientMaterialScoresAsADraw() {
        val result = searchFresh(gameOf("4k3/8/8/8/8/8/1b6/R3K3 b - - 0 1"), fixedDepth(3))
        assertEquals("b2a1", result.move.uci())
        assertEquals(0, result.scoreCp)
    }

    @Test
    fun keepsTheGameAliveWhenTheFiftyMoveRuleIsOneHalfmoveAway() {
        val game = gameOf("4k3/8/8/8/8/8/P7/R3K3 w - - 99 80")
        val result = searchFresh(game, fixedDepth(4))
        assertEquals(0, game.play(result.move).position.halfmoveClock, "${result.move} lets the 50-move rule draw")
        assertTrue(result.scoreCp > 300, "${result.scoreCp}")
    }

    @Test
    fun avoidsAThreefoldRepetitionWhenThreePawnsAhead() {
        // White's knight shuffles f3-g1 while Black's shuffles g8-f6. Ng1-f3 now would be the third occurrence.
        val game = gameOf("4k1n1/pppp4/8/8/8/5N2/PPPPPPP1/4K3 b - - 0 1")
            .play("g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8")
        assertEquals(GameStatus.ThreefoldRepetition, game.play(game.position.legalMoves().single { it.uci() == "g1f3" }).status)
        for (depth in 1..4) {
            val result = searchFresh(game, fixedDepth(depth))
            assertNotEquals("g1f3", result.move.uci(), "depth $depth")
            assertTrue(result.scoreCp > 200, "depth $depth: ${result.scoreCp}")
        }
    }

    @Test
    fun theLosingSideTakesARepetitionDraw() {
        // Black is a rook down. ...Nf6-g8 repeats a position from the game, which the search scores
        // as a draw (design §4.6: a repetition since the last irreversible move scores 0).
        val game = gameOf("4k1n1/8/8/8/8/5N2/8/R3K3 b - - 0 1")
            .play("g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1")
        val result = searchFresh(game, fixedDepth(2))
        assertEquals("f6g8", result.move.uci())
        assertEquals(0, result.scoreCp)
    }

    // --- Transposition table ------------------------------------------------------------------

    private val ttPositions = listOf(
        PerftPositions.INITIAL,
        PerftPositions.KIWIPETE,
        PerftPositions.POSITION_3,
        PerftPositions.POSITION_4,
        PerftPositions.POSITION_5,
        PerftPositions.POSITION_6,
        "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/4P3/8/PPPP1PPP/RNB1KBNR w KQkq - 4 4",
        "rnb1kbnr/pppp1ppp/8/4p3/4P1q1/8/PPPP1PPP/RNBQKBNR w KQkq - 2 3",
        "r5k1/5ppp/8/8/8/8/4RPPP/4R1K1 w - - 0 1",
        "8/5pk1/6p1/8/3P4/2K5/8/8 w - - 0 1",
    )

    @Test
    fun theTranspositionTableDoesNotChangeTheResultAtAFixedDepth() {
        for (fen in ttPositions) {
            val game = gameOf(fen)
            for (depth in 1..4) {
                val with = Search(TranspositionTable(entriesLog2 = 16)).run(game, fixedDepth(depth), kotlin.random.Random(0), System::nanoTime) { false }
                val without = Search(tt = null).run(game, fixedDepth(depth), kotlin.random.Random(0), System::nanoTime) { false }
                assertEquals(without.scoreCp, with.scoreCp, "$fen depth $depth score")
                assertEquals(without.move, with.move, "$fen depth $depth move")
                assertTrue(with.nodes <= without.nodes, "$fen depth $depth: the TT should not cost nodes (${with.nodes} > ${without.nodes})")
            }
        }
    }

    // --- Iterative deepening -----------------------------------------------------------------

    private data class RootMoveSeen(val depth: Int, val index: Int, val move: Move, val score: Int, val nodes: Long)

    private fun recordRootMoves(game: Game, params: LevelParams): List<RootMoveSeen> {
        val seen = ArrayList<RootMoveSeen>()
        val search = Search(TranspositionTable(entriesLog2 = 16))
        search.rootMoveListener = RootMoveListener { depth, index, move, score, nodes -> seen += RootMoveSeen(depth, index, move, score, nodes) }
        search.run(game, params, kotlin.random.Random(0), System::nanoTime) { false }
        return seen
    }

    @Test
    fun eachIterationSearchesThePreviousBestMoveFirst() {
        val seen = recordRootMoves(gameOf(PerftPositions.KIWIPETE), fixedDepth(5))
        for (depth in 2..5) {
            val previous = seen.filter { it.depth == depth - 1 }
            val best = previous.first { it.score == previous.maxOf { p -> p.score } }
            assertEquals(best.move, seen.first { it.depth == depth }.move, "depth $depth")
        }
    }

    @Test
    fun aStoppedIterationKeepsAnImprovementOverThePreviousBestMove() {
        val candidates = listOf(PerftPositions.KIWIPETE, PerftPositions.POSITION_4, PerftPositions.POSITION_5, PerftPositions.POSITION_6) +
            queenAttacked + ttPositions
        for (fen in candidates) {
            val game = gameOf(fen)
            val seen = recordRootMoves(game, fixedDepth(5))
            for (depth in 2..5) {
                val iteration = seen.filter { it.depth == depth }
                val first = iteration.first()
                val improvement = iteration.drop(1).firstOrNull { it.score > first.score } ?: continue
                // Leave at least one whole check interval between the improvement and the end of the iteration.
                if (iteration.last().nodes - improvement.nodes < 2L * Engine.CHECK_INTERVAL) continue

                // The budget is checked at the next multiple of 1,024 nodes after the improvement.
                val nodeBudget = improvement.nodes + 1
                val stopAt = (nodeBudget + Engine.CHECK_INTERVAL - 1) / Engine.CHECK_INTERVAL * Engine.CHECK_INTERVAL
                val completed = iteration.filter { it.nodes < stopAt }
                val expected = completed.first { it.score == completed.maxOf { c -> c.score } }

                val result = Search(TranspositionTable(entriesLog2 = 16)).run(game, budget(nodeBudget), kotlin.random.Random(0), System::nanoTime) { false }
                assertEquals(StopReason.NodeBudget, result.stopReason, fen)
                assertEquals(stopAt, result.nodes, fen)
                assertEquals(depth - 1, result.depth, "$fen: depth $depth was not completed")
                assertNotEquals(first.move, result.move, "$fen: the improvement at depth $depth was dropped")
                assertEquals(expected.move, result.move, fen)
                assertEquals(expected.score, result.scoreCp, fen)
                return
            }
        }
        throw AssertionError("No candidate position has a long enough iteration whose first move is beaten")
    }

    // --- Determinism --------------------------------------------------------------------------

    @Test
    fun theSameSearchGivesTheSameResult() {
        for (fen in listOf(PerftPositions.KIWIPETE, PerftPositions.POSITION_6)) {
            val game = gameOf(fen)
            val a = searchFresh(game, budget(30_000))
            val b = searchFresh(game, budget(30_000))
            assertEquals(a.copy(elapsedMs = 0), b.copy(elapsedMs = 0), fen)
        }
    }

    @Test
    fun aNewGameClearsEverythingThatCarriesOverBetweenSearches() {
        val engine = Engine()
        val game = gameOf(PerftPositions.POSITION_6)
        val first = engine.search(game, budget(20_000), kotlin.random.Random(1))
        engine.search(gameOf(PerftPositions.KIWIPETE), budget(20_000), kotlin.random.Random(1))
        engine.newGame()
        val again = engine.search(game, budget(20_000), kotlin.random.Random(1))
        assertEquals(first.copy(elapsedMs = 0), again.copy(elapsedMs = 0))
    }

    @Test
    fun theRandomSourceDoesNotAffectTheSearch() {
        val game = Game.start()
        val a = Engine().search(game, budget(20_000), kotlin.random.Random(1))
        val b = Engine().search(game, budget(20_000), kotlin.random.Random(99))
        assertEquals(a.copy(elapsedMs = 0), b.copy(elapsedMs = 0))
    }
}
