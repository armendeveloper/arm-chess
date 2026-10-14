package chess.engine.search

import chess.engine.LevelChecks
import chess.engine.board.PieceColor
import chess.engine.rules.Draws
import chess.engine.rules.Game
import chess.engine.rules.GameStatus
import org.junit.experimental.categories.Category
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * US-6 basic competence (design §4.7): from 10 fixed K+Q v K positions, levels 3 and 4 mate a
 * level-1 defender before the 50-move rule, 10/10. Deadlines are off, so the result does not
 * depend on machine load. Run with `./gradlew :engine:levelChecks`.
 */
@Category(LevelChecks::class)
class KqkMateTest {
    // Level 3 plays each position with 3 seeds: its randomisation and the defender's both depend on
    // the seed, and a single seed per position once hid an 8% failure rate (see the T-9 build log).
    @Test
    fun levelThreeMatesALevelOneDefenderFromAllTenPositions() = assertMatesFromEveryPosition(attackerLevel = 3, seeds = 3)

    @Test
    fun levelFourMatesALevelOneDefenderFromAllTenPositions() = assertMatesFromEveryPosition(attackerLevel = 4, seeds = 1)

    @Test
    fun thePositionsAreLegalOngoingAndFresh() {
        for (fen in POSITIONS) {
            val game = gameOf(fen)
            assertEquals(GameStatus.Ongoing, game.status, fen)
            assertEquals(0, game.position.halfmoveClock, fen)
            assertEquals(3, fen.substringBefore(' ').count { it.isLetter() }, "$fen is not K+Q v K")
        }
    }

    private fun assertMatesFromEveryPosition(attackerLevel: Int, seeds: Int) {
        val lines = ArrayList<String>()
        for ((index, fen) in POSITIONS.withIndex()) {
            val start = gameOf(fen)
            val attacker = start.position.sideToMove
            val movesToMate = ArrayList<Int>()
            for (s in 0 until seeds) {
                val seed = attackerLevel * 1_000 + index * 10 + s
                val game = playOut(start, attacker, attackerLevel, seed)
                val context = "position ${index + 1} ($fen), seed $seed: ${game.moves.map { it.uci() }}"
                assertEquals(GameStatus.Checkmate(winner = attacker), game.status, context)
                assertTrue(game.position.halfmoveClock < Draws.FIFTY_MOVE_HALFMOVES, context)
                movesToMate += (game.moves.size + 1) / 2
            }
            lines += "position ${index + 1}: mate in $movesToMate moves"
        }
        println("T-9 K+Q v K, level $attackerLevel attacking a level-1 defender:\n" + lines.joinToString("\n"))
    }

    /** Plays [start] out: [attacker] at [attackerLevel], the other side at level 1, one seeded [Random] per game. */
    private fun playOut(start: Game, attacker: PieceColor, attackerLevel: Int, seed: Int): Game {
        val attackerEngine = Engine()
        val defenderEngine = Engine()
        val attackerParams = Levels.params(attackerLevel).withoutDeadline()
        val defenderParams = Levels.params(1).withoutDeadline()
        val random = Random(seed)
        var game = start
        while (game.status == GameStatus.Ongoing) {
            val attacking = game.position.sideToMove == attacker
            val result = if (attacking) {
                attackerEngine.search(game, attackerParams, random)
            } else {
                defenderEngine.search(game, defenderParams, random)
            }
            game = game.play(result.move)
        }
        return game
    }

    companion object {
        /** K+Q v K with the queen's side to move and the halfmove clock at 0: 7 with White attacking, 3 with Black. */
        val POSITIONS = listOf(
            "8/8/8/4k3/8/8/8/3QK3 w - - 0 1",
            "8/8/3k4/8/8/8/8/Q6K w - - 0 1",
            "k7/8/8/8/8/8/8/6QK w - - 0 1",
            "8/8/8/8/8/2k5/8/K6Q w - - 0 1",
            "7k/8/8/8/8/8/8/K2Q4 w - - 0 1",
            "8/5k2/8/8/8/8/1Q6/4K3 w - - 0 1",
            "4k3/8/8/8/8/8/8/Q3K3 w - - 0 1",
            "4k3/3q4/8/8/8/8/8/4K3 b - - 0 1",
            "8/8/8/8/2K5/8/8/q1k5 b - - 0 1",
            "8/8/2K5/8/8/5k2/8/7q b - - 0 1",
        )
    }
}
