package chess.engine.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Automatic threefold repetition (US-5, plan decision OQ-3): the same position for the third time,
 * where "same" means placement, side to move, castling rights and en passant capture availability.
 * The occurrences need not be consecutive.
 *
 * Each sequence goes through different squares on each cycle, so that no position other than the one
 * under test occurs three times.
 */
class RepetitionTest {
    private val ongoing = GameStatus.Ongoing
    private val threefold = GameStatus.ThreefoldRepetition

    /** The FEN without its two clock fields. */
    private fun Game.fenWithoutClocks(): String = position.toFen().split(' ').take(4).joinToString(" ")

    @Test
    fun aKnightShuffleDrawsOnTheThirdNonConsecutiveOccurrence() {
        // The start position occurs at plies 0, 4 and 8, with different positions in between.
        val games = Game.start().playAll(
            "g1f3", "g8f6", "f3g1", "f6g8",
            "b1c3", "b8c6", "c3b1", "c6b8",
        )
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -", games[4].fenWithoutClocks())
        for (ply in 0..7) assertEquals(ongoing, games[ply].status, "ply $ply")
        assertEquals(threefold, games[8].status)
        assertTrue(games[8].status.isOver)
    }

    @Test
    fun aTwofoldRepetitionIsNotADraw() {
        assertEquals(ongoing, Game.start().play("g1f3", "g8f6", "f3g1", "f6g8").status)
    }

    @Test
    fun theThirdOccurrenceMayComeWithBlackToMove() {
        // The position after 1.Nf3 (Black to move) occurs at plies 1, 5 and 9; the start position
        // only at plies 0 and 4.
        val games = Game.start().playAll(
            "g1f3", "g8f6", "f3g1", "f6g8",
            "g1f3", "b8c6", "f3g5", "c6b8",
            "g5f3",
        )
        for (ply in 0..8) assertEquals(ongoing, games[ply].status, "ply $ply")
        assertEquals(threefold, games[9].status)
    }

    @Test
    fun lostCastlingRightsMakeTheSamePlacementADifferentPosition() {
        // The placement occurs at plies 0, 4, 8 and 12. At ply 0 both sides may still castle; the
        // king walks lose those rights, so only plies 4, 8 and 12 are the same position.
        val games = game("rn2k2r/8/8/8/8/8/8/RN2K2R w KQkq - 0 1").playAll(
            "e1f1", "e8f8", "f1e1", "f8e8",
            "b1c3", "b8c6", "c3b1", "c6b8",
            "b1a3", "b8a6", "a3b1", "a6b8",
        )
        assertEquals("rn2k2r/8/8/8/8/8/8/RN2K2R w - -", games[8].fenWithoutClocks())
        for (ply in 0..11) assertEquals(ongoing, games[ply].status, "ply $ply")
        assertEquals(threefold, games[12].status)
    }

    @Test
    fun aRookMoveThatLosesOneRightAlsoBreaksTheRepetition() {
        // Rh1-g1-h1 and Ke8-d8-e8 leave only White's queenside right, so ply 0 differs from 4, 8 and 12.
        val games = game("rn2k2r/8/8/8/8/8/8/RN2K2R w KQkq - 0 1").playAll(
            "h1g1", "e8d8", "g1h1", "d8e8",
            "b1c3", "b8c6", "c3b1", "c6b8",
            "b1a3", "b8a6", "a3b1", "a6b8",
        )
        assertEquals("rn2k2r/8/8/8/8/8/8/RN2K2R w Q -", games[4].fenWithoutClocks())
        for (ply in 0..11) assertEquals(ongoing, games[ply].status, "ply $ply")
        assertEquals(threefold, games[12].status)
    }

    @Test
    fun aDoublePushWithNoLegalEnPassantCaptureRepeatsWithTheLaterOccurrences() {
        // After 1.e4 the FEN records e3, but no black pawn can take there. That position (Black to
        // move) occurs at plies 1, 5 and 9; hashing the raw FEN square would miss the draw.
        val games = Game.start().playAll(
            "e2e4",
            "g8f6", "g1f3", "f6g8", "f3g1",
            "g8f6", "g1f3", "f6g8", "f3g1",
        )
        assertEquals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3", games[1].fenWithoutClocks())
        for (ply in 0..8) assertEquals(ongoing, games[ply].status, "ply $ply")
        assertEquals(threefold, games[9].status)
    }

    @Test
    fun aLegalEnPassantCaptureMakesTheFirstOccurrenceDifferent() {
        // After 1.e4 h6 2.e5 d5, exd6 en passant is legal (ply 4). The same placement without that
        // capture occurs at plies 8, 12 and 16, so ply 12 is not yet a draw; ply 16 is.
        val games = Game.start().playAll(
            "e2e4", "h7h6", "e4e5", "d7d5",
            "g1f3", "b8c6", "f3g1", "c6b8",
            "g1h3", "g8f6", "h3g1", "f6g8",
            "b1a3", "b8a6", "a3b1", "a6b8",
        )
        assertEquals("rnbqkbnr/ppp1ppp1/7p/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6", games[4].fenWithoutClocks())
        assertTrue(games[4].position.legalMoves().any { it.isEnPassant })
        for (ply in 0..15) assertEquals(ongoing, games[ply].status, "ply $ply")
        assertEquals(threefold, games[16].status)
    }

    @Test
    fun repetitionIsDetectedInAGameStartedFromAFen() {
        val games = game("4k3/8/8/8/8/8/8/R3K3 w - - 30 60").playAll(
            "a1a2", "e8d8", "a2a1", "d8e8",
            "a1a2", "e8d8", "a2a1", "d8e8",
        )
        for (ply in 0..7) assertEquals(ongoing, games[ply].status, "ply $ply")
        assertEquals(threefold, games[8].status)
    }

    @Test
    fun repetitionIsCheckedBeforeTheFiftyMoveRule() {
        // Ply 8 is both the third occurrence and the 100th halfmove: design §4.3 puts repetition first.
        val games = game("4k3/8/8/8/8/8/8/R3K3 w - - 92 60").playAll(
            "a1a2", "e8d8", "a2a1", "d8e8",
            "a1a2", "e8d8", "a2a1", "d8e8",
        )
        assertEquals(100, games[8].position.halfmoveClock)
        assertEquals(threefold, games[8].status)
    }
}
