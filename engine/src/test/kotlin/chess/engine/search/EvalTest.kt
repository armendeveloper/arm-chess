package chess.engine.search

import chess.engine.board.Fen
import chess.engine.perft.PerftPositions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EvalTest {
    private fun white(fen: String) = Eval.evaluateWhite(Fen.parse(fen))
    private fun toMove(fen: String) = Eval.evaluate(Fen.parse(fen))

    /** 50+ positions: the perft set, endgames, and middlegames from seeded random play. */
    private val symmetryPositions: List<String> = buildList {
        addAll(PerftPositions.ALL)
        add("4k3/8/8/8/8/8/8/1Q2K3 w - - 0 1")
        add("8/8/8/3k4/8/8/8/1Q2K3 b - - 0 1")
        add("4k3/8/8/8/8/8/1b6/R3K3 b - - 0 1")
        add("8/5pk1/6p1/8/3P4/2K5/8/8 w - - 0 1")
        add("r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4")
        add("2kr3r/pp1q1ppp/2n1bn2/3p4/3P4/2NB1N2/PP3PPP/R2Q1RK1 b - - 3 12")
        add("8/8/4k3/8/2B5/4K3/8/8 w - - 0 1")
        add("8/2p5/3p4/KP5r/1R2Pp1k/8/6P1/8 b - e3 0 1")
        randomGames(count = 42, seed = 8, minPlies = 6, maxPlies = 120).forEach { add(it.position.toFen()) }
    }

    @Test
    fun evalOfTheMirroredPositionIsTheNegationFromWhitesViewAndTheSameForTheSideToMove() {
        assertTrue(symmetryPositions.size >= 50, "need at least 50 positions, have ${symmetryPositions.size}")
        for (fen in symmetryPositions) {
            val mirrored = mirrorFen(fen)
            assertEquals(white(fen), -white(mirrored), "white-relative eval of $fen vs $mirrored")
            assertEquals(toMove(fen), toMove(mirrored), "side-to-move eval of $fen vs $mirrored")
        }
    }

    @Test
    fun theStartPositionIsWorthOnlyTheTempoBonus() {
        assertEquals(Eval.TEMPO, toMove(PerftPositions.INITIAL))
        assertEquals(Eval.TEMPO, white(PerftPositions.INITIAL))
    }

    @Test
    fun sideToMoveEvalIsWhiteEvalSignedBySide() {
        val fen = "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 4 4"
        assertEquals(-white(fen), toMove(fen))
    }

    @Test
    fun materialDominatesTheScore() {
        // White has an extra queen.
        assertTrue(white("rnb1kbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") > 800)
        // Black, to move, has an extra rook.
        assertTrue(toMove("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/1NBQKBNR b Kkq - 0 1") > 400)
    }

    @Test
    fun aSideThatCannotMateGetsNoCreditForItsMaterial() {
        assertEquals(0, toMove("4k3/8/8/8/8/8/8/2B1K3 w - - 0 1"), "K+B v K")
        assertEquals(0, toMove("4k3/8/8/8/8/8/8/1N2K3 b - - 0 1"), "K+N v K")
        assertEquals(0, toMove("2b1k3/8/8/8/8/8/8/2B1K3 w - - 0 1"), "K+B v K+B")
        assertTrue(toMove("4k3/8/8/8/8/8/8/R2bK3 b - - 0 1") < -100, "Black's lone bishop against a rook is still behind")
        assertTrue(white("4k3/8/8/8/8/8/P7/2B1K3 w - - 0 1") > 150, "a pawn keeps the winning chances")
    }

    @Test
    fun mopUpRewardsAnEdgedLoneKingAndCloseKings() {
        val sq = { name: String -> chess.engine.board.Square.parse(name) }
        assertEquals(0 + 4 * (14 - 2), Eval.mopUp(loneKing = sq("d4"), ownKing = sq("d6")), "centre, kings two apart")
        assertEquals(10 * 6 + 4 * (14 - 3), Eval.mopUp(loneKing = sq("a8"), ownKing = sq("b6")), "corner, kings three apart")
        assertTrue(Eval.mopUp(sq("h1"), sq("e4")) > Eval.mopUp(sq("e5"), sq("e3")))
        // It applies only when the other side has nothing but its king: one black pawn on a7 (worth
        // about 100 there in the endgame) also switches the mop-up term off.
        val loneKing = white("k7/8/8/8/8/8/8/1Q2K3 w - - 0 1")
        val withPawn = white("k7/p7/8/8/8/8/8/1Q2K3 w - - 0 1")
        assertTrue(loneKing - withPawn >= 90 + Eval.mopUp(sq("a8"), sq("e1")), "$loneKing vs $withPawn")
    }

    @Test
    fun mopUpDrivesTheLoneKingToTheCornerAndBringsTheKingsTogether() {
        val centre = white("8/8/8/3k4/8/8/8/1Q2K3 w - - 0 1")
        val corner = white("k7/8/8/8/8/8/8/1Q2K3 w - - 0 1")
        assertTrue(corner > centre, "corner $corner should beat centre $centre")
        val kingsApart = white("k7/8/8/8/8/8/8/1Q4K1 w - - 0 1")
        val kingsClose = white("k7/8/2K5/8/8/8/8/1Q6 w - - 0 1")
        assertTrue(kingsClose > kingsApart, "close $kingsClose should beat apart $kingsApart")
    }

    @Test
    fun endgameTablesReplaceMiddlegameTablesAsMaterialComesOff() {
        // With only pawns left, a central king is better than a king on its home square.
        assertTrue(white("4k3/pppp4/8/8/4K3/8/PPPP4/8 w - - 0 1") > white("4k3/pppp4/8/8/8/8/PPPP4/4K3 w - - 0 1"))
        // With all pieces on, the king is safer at home.
        assertTrue(
            white("rnbqkbnr/pppppppp/8/8/4K3/8/PPPPPPPP/RNBQ1BNR w kq - 0 1") <
                white("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w kq - 0 1"),
        )
    }
}
