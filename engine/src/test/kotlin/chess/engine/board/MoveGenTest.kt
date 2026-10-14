package chess.engine.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoveGenTest {
    private fun legalUcis(fen: String): Set<String> =
        MoveGen.legalMoves(Fen.parse(fen)).map { it.uci() }.toSet()

    private fun legalUcisFrom(fen: String, from: String): Set<String> =
        legalUcis(fen).filter { it.startsWith(from) }.toSet()

    private fun legalMove(fen: String, uci: String): Move =
        MoveGen.legalMoves(Fen.parse(fen)).single { it.uci() == uci }

    @Test
    fun startPositionHasTheTwentyStandardMoves() {
        val expected = (0 until 8).flatMap { file ->
            val f = "abcdefgh"[file]
            listOf("${f}2${f}3", "${f}2${f}4")
        }.toSet() + setOf("b1a3", "b1c3", "g1f3", "g1h3")
        assertEquals(expected, legalUcis(Fen.START))
    }

    @Test
    fun blackMovesDownTheBoardFromItsStartPosition() {
        val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
        val moves = legalUcis(fen)
        assertEquals(20, moves.size)
        assertTrue("e7e5" in moves && "e7e6" in moves && "g8f6" in moves && "b8c6" in moves)
    }

    @Test
    fun movesCarryTheDoublePushAndCaptureFlags() {
        assertEquals(Move.DOUBLE_PUSH, legalMove(Fen.START, "e2e4").flags)
        assertEquals(0, legalMove(Fen.START, "e2e3").flags)
        assertEquals(0, legalMove(Fen.START, "g1f3").flags)

        val fen = "4k3/8/8/3p1P2/4P3/8/8/4K3 w - - 0 1"
        assertEquals(Move.CAPTURE, legalMove(fen, "e4d5").flags)
        assertEquals(0, legalMove(fen, "e4e5").flags)
    }

    @Test
    fun pawnsCaptureOnlyEnemyPiecesAndOnlyDiagonally() {
        assertEquals(setOf("e4e5", "e4d5"), legalUcisFrom("4k3/8/8/3p1P2/4P3/8/8/4K3 w - - 0 1", "e4"))
        assertEquals(setOf("d5d4", "d5e4"), legalUcisFrom("4k3/8/8/3p4/4P3/8/8/4K3 b - - 0 1", "d5"))
    }

    @Test
    fun pawnPushesStopAtBlockers() {
        assertEquals(setOf("e2e3"), legalUcisFrom("4k3/8/8/8/4n3/8/4P3/4K3 w - - 0 1", "e2"), "double push blocked on e4")
        assertEquals(emptySet(), legalUcisFrom("4k3/8/8/8/8/4n3/4P3/4K3 w - - 0 1", "e2"), "single push blocked on e3")
        assertEquals(setOf("e3e4"), legalUcisFrom("4k3/8/8/8/8/4P3/8/4K3 w - - 0 1", "e3"), "no double push off the start rank")
    }

    @Test
    fun slidersStopAtTheFirstPieceAndCaptureOnlyEnemies() {
        // Rook on d4: own pawn on d6 blocks, black knight on f4 can be captured.
        val fen = "4k3/8/3P4/8/3R1n2/8/8/4K3 w - - 0 1"
        assertEquals(
            setOf("d4d5", "d4c4", "d4b4", "d4a4", "d4e4", "d4f4", "d4d3", "d4d2", "d4d1"),
            legalUcisFrom(fen, "d4"),
        )
        assertEquals(Move.CAPTURE, legalMove(fen, "d4f4").flags)
    }

    @Test
    fun pinnedRookMayOnlyMoveAlongThePinningFile() {
        // Black rook e8 pins the white rook e4 to the king on e1.
        val fen = "4r2k/8/8/8/4R3/8/8/4K3 w - - 0 1"
        assertEquals(setOf("e4e2", "e4e3", "e4e5", "e4e6", "e4e7", "e4e8"), legalUcisFrom(fen, "e4"))
    }

    @Test
    fun pinnedKnightCannotMoveAtAll() {
        // Black bishop h8 pins the white knight b2 to the king on a1 along the long diagonal.
        val fen = "4k2b/8/8/8/8/8/1N6/K7 w - - 0 1"
        assertEquals(emptySet(), legalUcisFrom(fen, "b2"))
        assertEquals(setOf("a1a2", "a1b1"), legalUcis(fen))
    }

    @Test
    fun pinnedBishopMayOnlyMoveAlongThePinningDiagonal() {
        // Black bishop c5 pins the white bishop f2 to the king on g1.
        val fen = "4k3/8/8/2b5/8/8/5B2/6K1 w - - 0 1"
        assertEquals(setOf("f2e3", "f2d4", "f2c5"), legalUcisFrom(fen, "f2"))
    }

    @Test
    fun diagonallyPinnedPawnMayOnlyCaptureThePinner() {
        // Black bishop c3 pins the white pawn d2 to the king on e1; pushing would expose the king.
        val fen = "4k3/8/8/8/8/2b5/3P4/4K3 w - - 0 1"
        assertEquals(setOf("d2c3"), legalUcisFrom(fen, "d2"))
    }

    @Test
    fun kingCannotStepOntoAttackedSquares() {
        // Black rook d8 covers d1 and d2.
        assertEquals(setOf("e1e2", "e1f1", "e1f2"), legalUcis("3r3k/8/8/8/8/8/8/4K3 w - - 0 1"))
    }

    @Test
    fun kingCannotRetreatAlongTheCheckingRay() {
        // Rook e8 checks the king on e4; e3 is still on the rook's file once the king leaves e4.
        val moves = legalUcisFrom("4r2k/8/8/8/4K3/8/8/8 w - - 0 1", "e4")
        assertTrue("e4e3" !in moves && "e4e5" !in moves)
        assertEquals(setOf("e4d3", "e4d4", "e4d5", "e4f3", "e4f4", "e4f5"), moves)
    }

    @Test
    fun inCheckOnlyEvasionsAreLegal() {
        // Rook e8 checks the king on e1: block with Nc3 (e2, e4), Bb5 (e2) or Ra4 (e4), capture with Bb5xe8, or step aside.
        val fen = "4r2k/8/8/1B6/R7/2N5/8/4K3 w - - 0 1"
        assertEquals(
            setOf("c3e2", "c3e4", "b5e2", "b5e8", "a4e4", "e1d1", "e1d2", "e1f1", "e1f2"),
            legalUcis(fen),
        )
    }

    @Test
    fun pseudoLegalGenerationIncludesMovesThatLeaveTheKingInCheck() {
        val board = Fen.parse("4k2b/8/8/8/8/8/1N6/K7 w - - 0 1")
        val buffer = IntArray(MoveGen.MAX_MOVES)
        val count = MoveGen.generatePseudoLegal(board, buffer)
        val ucis = (0 until count).map { Move(buffer[it]).uci() }.toSet()
        assertTrue("b2d3" in ucis, "the pinned knight's moves are pseudo-legal")
        assertEquals(setOf("a1a2", "a1b1"), legalUcis("4k2b/8/8/8/8/8/1N6/K7 w - - 0 1"))
    }

    // Castling.

    private val castlingBase = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"

    private fun castles(fen: String): Set<String> =
        MoveGen.legalMoves(Fen.parse(fen)).filter { it.isCastle }.map { it.uci() }.toSet()

    @Test
    fun bothSidesCastleOnBothWingsAsKingMovesWithTheCastleFlag() {
        assertEquals(setOf("e1g1", "e1c1"), castles(castlingBase))
        assertEquals(Move.CASTLE, legalMove(castlingBase, "e1g1").flags)
        assertEquals(Move.CASTLE, legalMove(castlingBase, "e1c1").flags)
        assertEquals(setOf("e8g8", "e8c8"), castles("r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1"))
    }

    @Test
    fun castlingNeedsTheRight() {
        assertEquals(setOf("e1c1"), castles("r3k2r/8/8/8/8/8/8/R3K2R w Qkq - 0 1"))
        assertEquals(setOf("e1g1"), castles("r3k2r/8/8/8/8/8/8/R3K2R w Kkq - 0 1"))
        assertEquals(emptySet(), castles("r3k2r/8/8/8/8/8/8/R3K2R w kq - 0 1"))
        assertEquals(setOf("e8g8"), castles("r3k2r/8/8/8/8/8/8/R3K2R b KQk - 0 1"))
    }

    @Test
    fun castlingNeedsEmptySquaresBetweenKingAndRook() {
        assertEquals(setOf("e1c1"), castles("4k3/8/8/8/8/8/8/R3K1NR w KQ - 0 1"), "knight on g1")
        assertEquals(setOf("e1c1"), castles("4k3/8/8/8/8/8/8/R3KB1R w KQ - 0 1"), "bishop on f1")
        assertEquals(setOf("e1g1"), castles("4k3/8/8/8/8/8/8/R2QK2R w KQ - 0 1"), "queen on d1")
        assertEquals(setOf("e1g1"), castles("4k3/8/8/8/8/8/8/R1B1K2R w KQ - 0 1"), "bishop on c1")
        assertEquals(setOf("e1g1"), castles("4k3/8/8/8/8/8/8/RN2K2R w KQ - 0 1"), "knight on b1")
        assertEquals(setOf("e1g1"), castles("4k3/8/8/8/8/8/8/Rn2K2R w KQ - 0 1"), "enemy knight on b1")
    }

    @Test
    fun noCastlingOutOfCheck() {
        assertEquals(emptySet(), castles("7k/8/8/8/4r3/8/8/R3K2R w KQ - 0 1"))
    }

    @Test
    fun noCastlingThroughAnAttackedSquare() {
        assertEquals(setOf("e1c1"), castles("7k/8/8/8/5r2/8/8/R3K2R w KQ - 0 1"), "f1 attacked")
        assertEquals(setOf("e1g1"), castles("7k/8/8/8/3r4/8/8/R3K2R w KQ - 0 1"), "d1 attacked")
        assertEquals(setOf("e8c8"), castles("r3k2r/8/8/8/8/8/8/5RK1 b kq - 0 1"), "f8 attacked")
    }

    @Test
    fun noCastlingIntoCheck() {
        assertEquals(setOf("e1c1"), castles("7k/8/8/8/6r1/8/8/R3K2R w KQ - 0 1"), "g1 attacked")
        assertEquals(setOf("e1g1"), castles("7k/8/8/8/2r5/8/8/R3K2R w KQ - 0 1"), "c1 attacked")
    }

    @Test
    fun queensideCastlingIsAllowedWhenOnlyTheRookCrossesAnAttackedSquare() {
        assertEquals(setOf("e1g1", "e1c1"), castles("7k/8/8/8/1r6/8/8/R3K2R w KQ - 0 1"), "b1 attacked")
        assertEquals(setOf("e1g1", "e1c1"), castles("7k/8/8/8/8/8/8/R3K2R w KQ - 0 1"))
    }

    @Test
    fun castlingIsGeneratedWhenTheRookIsAttacked() {
        assertEquals(setOf("e1g1", "e1c1"), castles("7k/8/8/8/7r/8/8/R3K2R w KQ - 0 1"), "h1 attacked")
    }

    // En passant.

    @Test
    fun enPassantCapturesOntoTheTargetSquareWithBothFlags() {
        val fen = "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1"
        assertEquals(setOf("e5e6", "e5d6"), legalUcisFrom(fen, "e5"))
        assertEquals(Move.EN_PASSANT or Move.CAPTURE, legalMove(fen, "e5d6").flags)
        assertEquals(setOf("e5e6"), legalUcisFrom("4k3/8/8/3pP3/8/8/8/4K3 w - - 0 1", "e5"), "no target square")
    }

    @Test
    fun enPassantFromBothSidesAndForBlack() {
        val white = "4k3/8/8/2PpP3/8/8/8/4K3 w - d6 0 1"
        assertTrue("c5d6" in legalUcis(white) && "e5d6" in legalUcis(white))
        val black = "4k3/8/8/8/3pPp2/8/8/4K3 b - e3 0 1"
        assertEquals(Move.EN_PASSANT or Move.CAPTURE, legalMove(black, "d4e3").flags)
        assertEquals(Move.EN_PASSANT or Move.CAPTURE, legalMove(black, "f4e3").flags)
    }

    @Test
    fun enPassantThatExposesTheKingAlongTheRankIsIllegal() {
        // Position 3 after 1.Rb1 c5: bxc6 would empty both b5 and c5 and open the h5 rook onto a5.
        val white = "8/8/3p4/KPp4r/5p1k/8/4P1P1/1R6 w - c6 0 2"
        assertEquals(setOf("b5b6"), legalUcisFrom(white, "b5"))
        val pseudo = IntArray(MoveGen.MAX_MOVES)
        val count = MoveGen.generatePseudoLegal(Fen.parse(white), pseudo)
        assertTrue((0 until count).any { Move(pseudo[it]).uci() == "b5c6" }, "the capture is pseudo-legal")

        // Position 3 after 1.e4: fxe3 would open the b4 rook onto the king on h4.
        val black = "8/2p5/3p4/KP5r/1R2Pp1k/8/6P1/8 b - e3 0 1"
        assertEquals(setOf("f4f3"), legalUcisFrom(black, "f4"))
    }

    @Test
    fun enPassantThatCapturesTheCheckingPawnIsLegal() {
        // 1.d4+ checks the king on e5; exd3 removes the checker.
        assertTrue("e4d3" in legalUcis("8/8/8/4k3/3Pp3/8/8/4K3 b - d3 0 1"))
    }

    @Test
    fun diagonallyPinnedPawnMayCaptureEnPassantOnlyAlongThePin() {
        // Black bishop h8 pins the e5 pawn to the king on a1; d6 is off the a1-h8 diagonal.
        assertEquals(emptySet(), legalUcisFrom("4k2b/8/8/3pP3/8/8/8/K7 w - d6 0 1", "e5"))
        // Black bishop b8 pins the e5 pawn to the king on h2; d6 is on the b8-h2 diagonal.
        assertEquals(setOf("e5d6"), legalUcisFrom("1b2k3/8/8/3pP3/8/8/7K/8 w - d6 0 1", "e5"))
    }

    // Promotion.

    @Test
    fun pushOntoTheLastRankBecomesFourPromotions() {
        assertEquals(setOf("a7a8q", "a7a8r", "a7a8b", "a7a8n"), legalUcisFrom("4k3/P7/8/8/8/8/8/4K3 w - - 0 1", "a7"))
        assertEquals(setOf("h2h1q", "h2h1r", "h2h1b", "h2h1n"), legalUcisFrom("4k3/8/8/8/8/8/7p/K7 b - - 0 1", "h2"))
        val queen = legalMove("4k3/P7/8/8/8/8/8/4K3 w - - 0 1", "a7a8q")
        assertEquals(PieceType.QUEEN, queen.promotionType)
        assertEquals(0, queen.flags)
    }

    @Test
    fun captureOntoTheLastRankBecomesFourCapturePromotions() {
        val fen = "nr2k3/P7/8/8/8/8/8/4K3 w - - 0 1"
        assertEquals(setOf("a7b8q", "a7b8r", "a7b8b", "a7b8n"), legalUcisFrom(fen, "a7"), "a8 is blocked")
        for (uci in listOf("a7b8q", "a7b8r", "a7b8b", "a7b8n")) {
            assertEquals(Move.CAPTURE, legalMove(fen, uci).flags, uci)
        }
    }

    @Test
    fun noPawnMoveOntoTheLastRankIsGeneratedWithoutAPromotion() {
        for (fen in listOf("1r2k3/P7/8/8/8/8/8/4K3 w - - 0 1", "4k3/8/8/8/8/8/6p1/4K2R b - - 0 1")) {
            val board = Fen.parse(fen)
            val buffer = IntArray(MoveGen.MAX_MOVES)
            val count = MoveGen.generatePseudoLegal(board, buffer)
            val pawnMoves = (0 until count).map { Move(buffer[it]) }.filter { Piece.typeOf(board.pieceAt(it.from)) == Piece.PAWN }
            assertEquals(8, pawnMoves.size, fen)
            assertTrue(pawnMoves.all { it.isPromotion }, fen)
        }
    }

    @Test
    fun generationDoesNotChangeTheBoard() {
        val board = Fen.parse("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq -")
        val before = board.copy()
        MoveGen.legalMoves(board)
        assertEquals(before, board)
    }
}
