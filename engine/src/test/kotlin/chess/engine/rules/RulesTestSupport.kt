package chess.engine.rules

import chess.engine.board.Move
import kotlin.random.Random

/** The legal move of this position written [uci] (e.g. "e2e4", "a7a8q"); fails the test if there is none. */
fun Position.move(uci: String): Move =
    legalMoves().singleOrNull { it.uci() == uci }
        ?: throw AssertionError("$uci is not a legal move in ${toFen()}")

/** Plays the UCI moves in order. */
fun Position.play(vararg ucis: String): Position = ucis.fold(this) { position, uci -> position.play(position.move(uci)) }

/** Plays the UCI moves in order. */
fun Game.play(vararg ucis: String): Game = ucis.fold(this) { game, uci -> game.play(game.position.move(uci)) }

/** Plays the UCI moves in order and returns the game after every ply (index 0 is this game). */
fun Game.playAll(vararg ucis: String): List<Game> = ucis.runningFold(this) { game, uci -> game.play(game.position.move(uci)) }

fun game(fen: String = Position.START_FEN): Game = Game.start(Position.fromFen(fen))

/**
 * Every ply of a random legal game from [start], as (position before the move, move played): uniform
 * random moves until the game ends or [maxPlies] plies have been played. The same [seed] gives the same game.
 */
fun randomGame(seed: Int, maxPlies: Int = 200, start: Position = Position.START): List<Pair<Position, Move>> =
    randomGameOf(seed, maxPlies, start).let { game -> game.moves.indices.map { game.positions[it] to game.moves[it] } }

/** The [Game] of [randomGame]. */
fun randomGameOf(seed: Int, maxPlies: Int = 200, start: Position = Position.START): Game {
    val random = Random(seed)
    var game = Game.start(start)
    while (game.moves.size < maxPlies && !game.status.isOver) {
        game = game.play(game.position.legalMoves().random(random))
    }
    return game
}
