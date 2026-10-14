package chess.engine.search

import chess.engine.board.Attacks
import chess.engine.board.Board
import chess.engine.board.Fen
import chess.engine.board.Move
import chess.engine.board.MoveGen
import chess.engine.rules.Game
import chess.engine.rules.GameStatus
import chess.engine.rules.Position
import kotlin.random.Random

/** Fixed-depth search with no deadline and a budget that never binds: the same tree on every run. */
fun fixedDepth(depth: Int, quiescence: Quiescence = Quiescence.Full): LevelParams =
    LevelParams(depthCap = depth, nodeBudget = Long.MAX_VALUE, timeCapMs = null, quiescence = quiescence)

/** A node-budgeted search with no deadline, as the strength match runs the levels (design §4.7). */
fun budget(nodes: Long, depthCap: Int = LevelParams.MAX_DEPTH): LevelParams =
    LevelParams(depthCap = depthCap, nodeBudget = nodes, timeCapMs = null, quiescence = Quiescence.Full)

fun gameOf(fen: String): Game = Game.start(Position.fromFen(fen))

/** Searches [game] with a fresh engine. */
fun searchFresh(game: Game, params: LevelParams, nanoTime: () -> Long = System::nanoTime, shouldStop: () -> Boolean = { false }): SearchResult =
    Engine().search(game, params, Random(0), nanoTime, shouldStop)

/** Plies to mate encoded in [score] (positive: the side to move mates), or null for a non-mate score. */
fun matePlies(score: Int): Int? = when {
    score >= SearchResult.MATE_SCORE - 512 -> SearchResult.MATE_SCORE - score
    score <= -SearchResult.MATE_SCORE + 512 -> -(SearchResult.MATE_SCORE + score)
    else -> null
}

/**
 * [fen] with the colours swapped and the board flipped top to bottom: the same position from the
 * other side's point of view. Castling rights, the side to move and the en passant square follow.
 */
fun mirrorFen(fen: String): String {
    val fields = fen.trim().split(' ')
    val placement = fields[0].split('/').reversed().joinToString("/") { rank ->
        rank.map { if (it.isLetter()) (if (it.isUpperCase()) it.lowercaseChar() else it.uppercaseChar()) else it }.joinToString("")
    }
    val side = if (fields[1] == "w") "b" else "w"
    val castling = if (fields[2] == "-") {
        "-"
    } else {
        val swapped = fields[2].map { if (it.isUpperCase()) it.lowercaseChar() else it.uppercaseChar() }
        "KQkq".filter { it in swapped }
    }
    val ep = if (fields[3] == "-") "-" else "${fields[3][0]}${if (fields[3][1] == '3') '6' else '3'}"
    return (listOf(placement, side, castling, ep) + fields.drop(4)).joinToString(" ")
}

/**
 * Ongoing games reached by seeded random play from the start position, between [minPlies] and
 * [maxPlies] plies long. Games that end on the way are skipped.
 */
fun randomGames(count: Int, seed: Int, minPlies: Int = 4, maxPlies: Int = 80): List<Game> {
    val random = Random(seed)
    val games = ArrayList<Game>(count)
    while (games.size < count) {
        val plies = random.nextInt(minPlies, maxPlies + 1)
        var game = Game.start()
        for (i in 0 until plies) {
            val moves = game.position.legalMoves()
            game = game.play(moves[random.nextInt(moves.size)])
            if (game.status != GameStatus.Ongoing) break
        }
        if (game.status == GameStatus.Ongoing) games += game
    }
    return games
}

/**
 * Brute-force mate prover, independent of the engine's search: true when the side to move in [fen]
 * can force checkmate within [moves] of its own moves. Used to check the test positions themselves.
 */
fun hasForcedMate(fen: String, moves: Int): Boolean = MateProver(Fen.parse(fen)).mateIn(moves)

/** True when, after [move] in [fen], every defence loses to mate within [moves] - 1 further attacking moves. */
fun moveForcesMate(fen: String, move: Move, moves: Int): Boolean {
    val board = Fen.parse(fen)
    board.make(move)
    return MateProver(board).allRepliesMated(moves)
}

private class MateProver(private val board: Board) {
    fun mateIn(n: Int): Boolean {
        val moves = IntArray(MoveGen.MAX_MOVES)
        val count = MoveGen.generateLegal(board, moves)
        for (i in 0 until count) {
            val move = Move(moves[i])
            board.make(move)
            val mates = allRepliesMated(n)
            board.unmake(move)
            if (mates) return true
        }
        return false
    }

    /** The defender is to move: true when it is mated now, or every reply allows mate in [n] - 1. */
    fun allRepliesMated(n: Int): Boolean {
        val moves = IntArray(MoveGen.MAX_MOVES)
        val count = MoveGen.generateLegal(board, moves)
        if (count == 0) return Attacks.isInCheck(board, board.sideToMove)
        if (n <= 1) return false
        for (i in 0 until count) {
            val move = Move(moves[i])
            board.make(move)
            val mated = mateIn(n - 1)
            board.unmake(move)
            if (!mated) return false
        }
        return true
    }
}
