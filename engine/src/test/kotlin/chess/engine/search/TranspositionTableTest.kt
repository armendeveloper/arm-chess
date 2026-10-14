package chess.engine.search

import chess.engine.board.Move
import chess.engine.board.Piece
import chess.engine.board.Square
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranspositionTableTest {
    private val key = 0x1234_5678_9ABC_DEF1L

    /** Keys that land in the same bucket as [key]: they differ only in bits the index ignores. */
    private val sameBucket1 = key xor (1L shl 60)
    private val sameBucket2 = key xor (1L shl 61)

    private val promotionCapture = Move.of(Square.parse("b7"), Square.parse("a8"), Piece.KNIGHT, Move.CAPTURE)

    @Test
    fun storesAndRetrievesMoveScoreDepthAndBound() {
        val tt = TranspositionTable(entriesLog2 = 10)
        tt.store(key, promotionCapture, score = -1234, depth = 7, bound = TranspositionTable.LOWER, ply = 3)
        val entry = tt.probe(key)
        assertTrue(TranspositionTable.isHit(entry))
        assertEquals(promotionCapture, TranspositionTable.move(entry))
        assertEquals(-1234, TranspositionTable.score(entry, ply = 9), "ordinary scores do not depend on the ply")
        assertEquals(7, TranspositionTable.depth(entry))
        assertEquals(TranspositionTable.LOWER, TranspositionTable.bound(entry))
    }

    @Test
    fun aDifferentKeyMisses() {
        val tt = TranspositionTable(entriesLog2 = 10)
        tt.store(key, promotionCapture, score = 5, depth = 1, bound = TranspositionTable.EXACT, ply = 0)
        assertFalse(TranspositionTable.isHit(tt.probe(key + 1)))
        assertFalse(TranspositionTable.isHit(tt.probe(sameBucket1)))
    }

    @Test
    fun mateScoresAreStoredRelativeToTheNodeAndLoadedRelativeToTheRoot() {
        val tt = TranspositionTable(entriesLog2 = 10)
        val mate = SearchResult.MATE_SCORE
        // Found at ply 3: the side to move there mates at ply 5 (2 plies later).
        tt.store(key, Move.NONE, score = mate - 5, depth = 4, bound = TranspositionTable.EXACT, ply = 3)
        // Reached again at ply 7 by another path: still mate 2 plies later, so at ply 9 from the root.
        assertEquals(mate - 9, TranspositionTable.score(tt.probe(key), ply = 7))
        assertEquals(mate - 3, TranspositionTable.score(tt.probe(key), ply = 1))

        // Being mated works the same way, with the sign reversed.
        tt.store(sameBucket1, Move.NONE, score = -mate + 6, depth = 4, bound = TranspositionTable.UPPER, ply = 2)
        assertEquals(-mate + 10, TranspositionTable.score(tt.probe(sameBucket1), ply = 6))
    }

    @Test
    fun theDeeperEntryStaysAndAShallowerOneUsesTheAlwaysReplaceSlot() {
        val tt = TranspositionTable(entriesLog2 = 4)
        tt.store(key, promotionCapture, score = 10, depth = 8, bound = TranspositionTable.EXACT, ply = 0)
        tt.store(sameBucket1, Move.NONE, score = 20, depth = 2, bound = TranspositionTable.EXACT, ply = 0)
        assertEquals(8, TranspositionTable.depth(tt.probe(key)), "the deep entry survives")
        assertEquals(20, TranspositionTable.score(tt.probe(sameBucket1), 0))

        // A third shallow key in the bucket replaces the always-replace slot, not the deep entry.
        tt.store(sameBucket2, Move.NONE, score = 30, depth = 1, bound = TranspositionTable.EXACT, ply = 0)
        assertTrue(TranspositionTable.isHit(tt.probe(key)))
        assertFalse(TranspositionTable.isHit(tt.probe(sameBucket1)))
        assertEquals(30, TranspositionTable.score(tt.probe(sameBucket2), 0))

        // An entry at least as deep takes the depth-preferred slot.
        tt.store(sameBucket1, Move.NONE, score = 40, depth = 9, bound = TranspositionTable.EXACT, ply = 0)
        assertEquals(40, TranspositionTable.score(tt.probe(sameBucket1), 0))
        assertFalse(TranspositionTable.isHit(tt.probe(key)))
    }

    @Test
    fun entriesFromAnEarlierSearchGiveWayEvenWhenDeeper() {
        val tt = TranspositionTable(entriesLog2 = 4)
        tt.store(key, Move.NONE, score = 10, depth = 12, bound = TranspositionTable.EXACT, ply = 0)
        tt.newSearch()
        tt.store(sameBucket1, Move.NONE, score = 20, depth = 1, bound = TranspositionTable.EXACT, ply = 0)
        assertFalse(TranspositionTable.isHit(tt.probe(key)), "the stale deep entry was replaced")
        assertEquals(20, TranspositionTable.score(tt.probe(sameBucket1), 0))
    }

    @Test
    fun restoringTheSameKeyKeepsTheOldMoveWhenTheNewOneIsNone() {
        val tt = TranspositionTable(entriesLog2 = 10)
        tt.store(key, promotionCapture, score = 10, depth = 3, bound = TranspositionTable.LOWER, ply = 0)
        tt.store(key, Move.NONE, score = -5, depth = 4, bound = TranspositionTable.UPPER, ply = 0)
        val entry = tt.probe(key)
        assertEquals(promotionCapture, TranspositionTable.move(entry))
        assertEquals(-5, TranspositionTable.score(entry, 0))
        assertEquals(TranspositionTable.UPPER, TranspositionTable.bound(entry))
    }

    @Test
    fun clearEmptiesTheTable() {
        val tt = TranspositionTable(entriesLog2 = 10)
        tt.store(key, promotionCapture, score = 10, depth = 3, bound = TranspositionTable.EXACT, ply = 0)
        tt.clear()
        assertFalse(TranspositionTable.isHit(tt.probe(key)))
    }

    @Test
    fun theDefaultSizeIsTwoToTheEighteenEntries() {
        assertEquals(1 shl 18, TranspositionTable().entries)
    }
}
