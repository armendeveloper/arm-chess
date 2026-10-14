package chess.engine.board

import chess.engine.perft.PerftPositions
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ZobristTest {
    private fun key(fen: String): Long = Fen.parse(fen).key

    private fun play(board: Board, vararg ucis: String) {
        for (uci in ucis) {
            val move = MoveGen.legalMoves(board).singleOrNull { it.uci() == uci }
                ?: throw AssertionError("$uci is not a legal move in ${Fen.format(board)}")
            board.make(move)
        }
    }

    @Test
    fun theKeyIsDeterministicAndParsingComputesItFromScratch() {
        assertEquals(key(Fen.START), key(Fen.START))
        for (fen in PerftPositions.ALL) {
            val board = Fen.parse(fen)
            assertEquals(Zobrist.compute(board), board.key, fen)
            assertEquals(board.key, board.copy().key, "copy keeps the key: $fen")
        }
        assertEquals(0L, Board().key, "the empty board hashes to 0")
    }

    @Test
    fun theKeyDependsOnPlacementSideToMoveAndEachCastlingRight() {
        val fens = listOf(
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1",
            "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1",
            "r3k2r/8/8/8/8/8/8/R3K2R w Qkq - 0 1",
            "r3k2r/8/8/8/8/8/8/R3K2R w Kkq - 0 1",
            "r3k2r/8/8/8/8/8/8/R3K2R w KQq - 0 1",
            "r3k2r/8/8/8/8/8/8/R3K2R w KQk - 0 1",
            "r3k2r/8/8/8/8/8/8/R3K2R w - - 0 1",
            "r3k2r/8/8/8/8/8/8/R4K1R w kq - 0 1",
            "r3k2r/8/8/8/8/8/8/R3K1R1 w Qkq - 0 1",
            "r3k2r/8/8/8/8/8/8/R3KR2 w Qkq - 0 1",
        )
        val keys = fens.map(::key)
        assertEquals(fens.size, keys.toSet().size, "every variation needs its own key")
    }

    @Test
    fun theKeyIgnoresTheClocks() {
        assertEquals(
            key("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"),
            key("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 37 90"),
        )
    }

    @Test
    fun aDoublePushWithNoPawnAbleToCaptureLeavesTheEnPassantFileOutOfTheKey() {
        val board = Fen.parse(Fen.START)
        play(board, "e2e4")
        assertEquals(Square.parse("e3"), board.epSquare, "the board still records the en passant square")
        assertEquals(key("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"), board.key)
        assertEquals(key("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"), board.key)
    }

    @Test
    fun aDoublePushNextToAPawnThatCanCaptureEnPassantPutsTheFileInTheKey() {
        val board = Fen.parse("rnbqkbnr/ppp1pppp/8/8/3p4/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        play(board, "e2e4")
        val withEp = key("rnbqkbnr/ppp1pppp/8/8/3pP3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")
        val withoutEp = key("rnbqkbnr/ppp1pppp/8/8/3pP3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1")
        assertEquals(withEp, board.key)
        assertNotEquals(withoutEp, board.key)
        assertTrue(board.hasLegalEnPassant())
    }

    @Test
    fun anEnPassantCaptureThatWouldExposeTheKingAlongTheRankIsNotInTheKey() {
        // Position 3 after 1.e4: f4xe3 is pseudo-legal but leaves the h4 king to the b4 rook.
        val board = Fen.parse(PerftPositions.POSITION_3)
        play(board, "e2e4")
        assertEquals(Square.parse("e3"), board.epSquare)
        assertTrue(!board.hasLegalEnPassant())
        assertEquals(key("8/2p5/3p4/KP5r/1R2Pp1k/8/6P1/8 b - - 0 1"), board.key)
        assertEquals(key("8/2p5/3p4/KP5r/1R2Pp1k/8/6P1/8 b - e3 0 1"), board.key)
    }

    @Test
    fun anEnPassantCaptureThatRemovesTheCheckingPawnIsInTheKey() {
        // 1.d4+ checks the e5 king; e4xd3 en passant answers the check.
        val board = Fen.parse("8/8/8/4k3/4p3/8/3P4/4K3 w - - 0 1")
        play(board, "d2d4")
        assertTrue(board.hasLegalEnPassant())
        assertNotEquals(key("8/8/8/4k3/3Pp3/8/8/4K3 b - - 0 1"), board.key)
        assertEquals(key("8/8/8/4k3/3Pp3/8/8/4K3 b - d3 0 1"), board.key)
    }

    @Test
    fun theIncrementalKeyEqualsTheKeyFromScratchThroughThreeRandom200PlyGames() {
        for (seed in 1..3) {
            val random = Random(seed)
            val board = Fen.parse(Fen.START)
            val startKey = board.key
            val made = ArrayList<Move>()
            val keys = ArrayList<Long>()
            var restarts = 0
            while (made.size < 200) {
                val moves = MoveGen.legalMoves(board)
                if (moves.isEmpty()) {
                    // Mate or stalemate before ply 200: step back one ply and pick another line.
                    board.unmake(made.removeAt(made.lastIndex))
                    keys.removeAt(keys.lastIndex)
                    restarts++
                    continue
                }
                val move = moves[random.nextInt(moves.size)]
                board.make(move)
                made += move
                keys += board.key
                assertEquals(Zobrist.compute(board), board.key, "seed $seed, ply ${made.size}: ${move.uci()} in ${Fen.format(board)}")
            }
            assertTrue(restarts < 50, "seed $seed restarted $restarts times")
            for (i in made.indices.reversed()) {
                assertEquals(keys[i], board.key, "seed $seed, before unmaking ply ${i + 1}")
                board.unmake(made[i])
                assertEquals(Zobrist.compute(board), board.key, "seed $seed, after unmaking ply ${i + 1}")
            }
            assertEquals(startKey, board.key)
        }
    }
}
