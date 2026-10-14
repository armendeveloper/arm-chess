package chess.engine.rules

import chess.engine.perft.PerftPositions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SanParseTest {
    private val start = Position.START_FEN
    private val twoBlackKnights = "1n2k3/8/5n2/8/8/8/8/4K3 b - - 0 1"
    private val rooksOnTheEFile = "k7/8/8/8/8/4R3/8/4R2K w - - 0 1"
    private val threeQueens = "2k5/8/8/8/4Q2Q/K7/8/4r2Q w - - 0 1"
    private val promotion = "k7/4P3/8/8/8/8/8/4K3 w - - 0 1"
    private val blackPromotion = "3r3k/8/8/4b3/8/8/1pK5/R6r b - - 0 1"
    private val pinnedKnight = "4k3/8/8/b7/8/2N5/8/4K1N1 w - - 0 1"
    private val pawnCapture = "4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1"
    private val enPassant = "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1"
    private val rookCheck = "4k3/8/8/8/8/8/8/R3K3 w - - 0 1"
    private val backRankMate = "6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1"
    private val shortCastle = "4k3/8/8/8/8/8/8/4K2R w K - 0 1"
    private val longCastleCheck = "3k4/8/8/8/8/8/8/R3K3 w Q - 0 1"

    private fun assertParses(fen: String, san: String, uci: String) {
        val position = Position.fromFen(fen)
        assertEquals(position.move(uci), San.parse(position, san), "'$san' in $fen")
    }

    private fun assertRejected(fen: String, san: String) {
        val position = Position.fromFen(fen)
        assertFailsWith<IllegalMoveException>("'$san' in $fen") { San.parse(position, san) }
        assertNull(San.parseOrNull(position, san), "'$san' in $fen")
    }

    @Test
    fun acceptsAMissingCheckOrMateMarkAndPlusForMate() {
        assertParses(rookCheck, "Ra8", "a1a8")
        assertParses(backRankMate, "Ra8", "a1a8")
        assertParses(backRankMate, "Ra8+", "a1a8")
        assertParses(longCastleCheck, "O-O-O", "e1c1")
        assertParses(blackPromotion, "bxa1=N", "b2a1n")
    }

    @Test
    fun acceptsZerosForCastling() {
        assertParses(shortCastle, "0-0", "e1g1")
        assertParses(longCastleCheck, "0-0-0", "e1c1")
        assertParses(longCastleCheck, "0-0-0+", "e1c1")
        assertParses("4k2r/8/8/8/8/8/8/4K3 b k - 0 1", "0-0", "e8g8")
    }

    @Test
    fun acceptsAPromotionWithoutTheEqualsSign() {
        assertParses(promotion, "e8Q+", "e7e8q")
        assertParses(promotion, "e8Q", "e7e8q")
        assertParses(promotion, "e8N", "e7e8n")
        assertParses(blackPromotion, "bxa1N#", "b2a1n")
    }

    @Test
    fun acceptsAMissingCaptureMarkAndExtraDisambiguation() {
        assertParses(pawnCapture, "ed5", "e4d5")
        assertParses("4k3/8/8/4p3/8/5N2/8/4K3 w - - 0 1", "Ne5", "f3e5")
        assertParses(threeQueens, "Qh4e1", "h4e1")
        assertParses(pinnedKnight, "Nge2", "g1e2")
        assertParses(pinnedKnight, "N1e2", "g1e2")
        assertParses(pinnedKnight, "Ng1e2", "g1e2")
        assertParses(twoBlackKnights, "Nb8d7", "b8d7")
        assertParses(start, "e2e4", "e2e4")
    }

    @Test
    fun acceptsAnnotationsSurroundingSpaceAndAnEnPassantSuffix() {
        assertParses(start, "e4!", "e2e4")
        assertParses(start, "Nf3?!", "g1f3")
        assertParses(backRankMate, "Ra8#!!", "a1a8")
        assertParses(start, " Nf3 ", "g1f3")
        assertParses(enPassant, "exd6 e.p.", "e5d6")
        assertParses(enPassant, "exd6e.p.", "e5d6")
        val enPassantCheck = "8/4k3/8/3pP3/8/8/8/4K3 w - d6 0 1"
        assertParses(enPassantCheck, "exd6+ e.p.", "e5d6")
        assertParses(enPassantCheck, "exd6 e.p.+", "e5d6")
    }

    @Test
    fun theExceptionSaysWhyForLogs() {
        val error = assertFailsWith<IllegalMoveException> { San.parse(Position.fromFen(twoBlackKnights), "Nd7") }
        assertEquals("'Nd7' is ambiguous in $twoBlackKnights", error.message)
    }

    @Test
    fun rejectsAmbiguousMoves() {
        assertRejected(twoBlackKnights, "Nd7")
        assertRejected(rooksOnTheEFile, "Re2")
        assertRejected(rooksOnTheEFile, "Ree2")
        assertRejected(threeQueens, "Qxe1")
        assertRejected(threeQueens, "Qe1")
        assertRejected(threeQueens, "Qhxe1") // h4 and h1
        assertRejected(threeQueens, "Q4xe1") // h4 and e4
    }

    @Test
    fun rejectsIllegalMoves() {
        assertRejected(start, "e5")
        assertRejected(start, "Nf4")
        assertRejected(start, "Ke2")
        assertRejected(start, "O-O")
        assertRejected(start, "0-0-0")
        assertRejected(start, "e6") // Black's move
        assertRejected(pawnCapture, "d5") // occupied: a pawn without a file is a push
        assertRejected(pawnCapture, "xd5")
        assertRejected(pawnCapture, "fxd5")
        assertRejected(shortCastle, "Kg1") // castling is written O-O
        assertRejected(Position.START.play("f2f3", "e7e5", "g2g4", "d8h4").toFen(), "Kf2")
    }

    @Test
    fun rejectsWrongClaims() {
        assertRejected(start, "e4+")
        assertRejected(start, "e4#")
        assertRejected(start, "Nxf3")
        assertRejected(rookCheck, "Ra8#")
        assertRejected(pawnCapture, "exd5+")
        assertRejected(pawnCapture, "exd5 e.p.")
        assertRejected(start, "e4 e.p.")
    }

    @Test
    fun rejectsAMissingOrImpossiblePromotion() {
        assertRejected(promotion, "e8")
        assertRejected(promotion, "e8+")
        assertRejected(promotion, "e8=K")
        assertRejected(promotion, "e8=P")
        assertRejected(promotion, "e8=")
        assertRejected(promotion, "e8=QQ")
        assertRejected(start, "e4=Q")
        assertRejected(start, "Nf3=Q")
    }

    @Test
    fun rejectsMalformedText() {
        for (text in listOf("", "   ", "e", "4", "Zf3", "nf3", "NF3", "Pe4", "e2-e4", "Ng1-f3", "N f3", "e9", "i4", "O-O-O-O", "O-0", "o-o", "--", "+", "Nf3++")) {
            assertRejected(start, text)
        }
    }

    @Test
    fun internationalLettersAreTheDefault() {
        val letters = SanLetters.INTERNATIONAL
        assertEquals(listOf("K", "Q", "R", "B", "N"), listOf(letters.king, letters.queen, letters.rook, letters.bishop, letters.knight))
    }

    @Test
    fun formatAndParseUseInjectedLetters() {
        // Russian notation, an open question for T-29: Кр (king) Ф (queen) Л (rook) С (bishop) К (knight).
        val russian = SanLetters(king = "Кр", queen = "Ф", rook = "Л", bishop = "С", knight = "К")
        val start = Position.START
        assertEquals("Кf3", San.format(start, start.move("g1f3"), russian))
        assertEquals("e4", San.format(start, start.move("e2e4"), russian))
        val queens = Position.fromFen(threeQueens)
        assertEquals("Фh4xe1", San.format(queens, queens.move("h4e1"), russian))
        val promote = Position.fromFen(promotion)
        assertEquals("e8=Ф+", San.format(promote, promote.move("e7e8q"), russian))
        assertEquals("e8=К", San.format(promote, promote.move("e7e8n"), russian))
        assertEquals("Крd2", San.format(promote, promote.move("e1d2"), russian))
        val castle = Position.fromFen(shortCastle)
        assertEquals("O-O", San.format(castle, castle.move("e1g1"), russian))

        assertEquals(start.move("g1f3"), San.parse(start, "Кf3", russian))
        assertEquals(promote.move("e1d2"), San.parse(promote, "Крd2", russian))
        assertEquals(promote.move("e7e8q"), San.parse(promote, "e8Ф", russian))
        assertNull(San.parseOrNull(start, "Nf3", russian))

        val opera = Game.replay(Position.START_FEN, listOf("e2e4", "e7e5", "g1f3", "d7d6", "d2d4", "c8g4"))
        assertEquals(listOf("e4", "e5", "Кf3", "d6", "d4", "Сg4"), opera.sanList(russian))

        for (fen in PerftPositions.ALL) {
            val position = Position.fromFen(fen)
            for (move in position.legalMoves()) {
                assertEquals(move, San.parse(position, San.format(position, move, russian), russian), "$move in $fen")
            }
        }
    }

    @Test
    fun lettersMustBeUsableInNotation() {
        assertFailsWith<IllegalArgumentException> { SanLetters(king = "", queen = "Q", rook = "R", bishop = "B", knight = "N") }
        assertFailsWith<IllegalArgumentException> { SanLetters(king = "K", queen = "Q", rook = "R", bishop = "N", knight = "N") }
        assertFailsWith<IllegalArgumentException> { SanLetters(king = "K", queen = "Q", rook = "R", bishop = "b", knight = "N") }
        assertFailsWith<IllegalArgumentException> { SanLetters(king = "K", queen = "Q", rook = "R1", bishop = "B", knight = "N") }
        assertFailsWith<IllegalArgumentException> { SanLetters(king = "K", queen = "Q", rook = "R", bishop = "B", knight = "N ") }
        assertFailsWith<IllegalArgumentException> { SanLetters(king = "K", queen = "Q+", rook = "R", bishop = "B", knight = "N") }
    }
}
