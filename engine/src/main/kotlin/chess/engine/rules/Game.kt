package chess.engine.rules

import chess.engine.board.FenException
import chess.engine.board.Move
import java.util.Collections

/**
 * An immutable game (design §4.1): the start position, the moves played, and every position along
 * the way, so that undo and repetition checks are simple. Safe to share between threads; [play] and
 * [undo] return new games.
 *
 * [positions] has one more entry than [moves]: `positions[i + 1]` is `positions[i]` after `moves[i]`.
 * Keeping every position costs a few hundred bytes per ply.
 */
class Game private constructor(positions: List<Position>, moves: List<Move>) {
    /** Every position, oldest first: the start position, then the position after each move. */
    val positions: List<Position> = Collections.unmodifiableList(positions)

    /** The moves played, in order. */
    val moves: List<Move> = Collections.unmodifiableList(moves)

    val startPosition: Position get() = positions.first()

    /** The current position. */
    val position: Position get() = positions.last()

    /** Ongoing, or how the game ended (design §4.3). Computed once. */
    val status: GameStatus by lazy { GameStatus.of(this.positions) }

    /**
     * The game after [move], which must be legal in [position].
     *
     * @throws IllegalMoveException when the move is not legal or the game is already over (no move
     *   is legal then, even if the board would allow it: draws are automatic).
     */
    fun play(move: Move): Game {
        if (status.isOver) throw IllegalMoveException("${move.uci()} played after the game ended ($status)")
        val next = position.play(move)
        return Game(positions + next, moves + move)
    }

    /**
     * The game as it was [plies] moves ago (0 up to the number of moves). The status is recomputed,
     * so undoing a finished game makes it ongoing again.
     *
     * @throws IllegalArgumentException when [plies] is negative or more than the moves played.
     */
    fun undo(plies: Int = 1): Game {
        require(plies in 0..moves.size) { "Cannot undo $plies plies of a ${moves.size}-ply game" }
        if (plies == 0) return this
        val keep = moves.size - plies
        return Game(positions.subList(0, keep + 1).toList(), moves.subList(0, keep).toList())
    }

    /**
     * The legal moves of the piece on [sq] (0..63): empty for an empty square, an opponent's piece,
     * or a game that is over.
     */
    fun legalMovesFrom(sq: Int): List<Move> {
        if (status.isOver) return emptyList()
        return position.legalMoves().filter { it.from == sq }
    }

    /**
     * The moves in SAN (design §4.4), one entry per move, with piece [letters] (K Q R B N by default).
     * Check and mate marks come from the stored positions, so nothing is replayed.
     */
    fun sanList(letters: SanLetters = SanLetters.INTERNATIONAL): List<String> =
        moves.indices.map { San.format(positions[it], moves[it], positions[it + 1], letters) }

    /** Equal when both games have the same positions (every FEN field) and moves. */
    override fun equals(other: Any?): Boolean =
        this === other || (other is Game && moves == other.moves && positions == other.positions)

    override fun hashCode(): Int = 31 * positions.hashCode() + moves.hashCode()

    override fun toString(): String = "Game(${startPosition.toFen()}, ${moves.joinToString(" ") { it.uci() }})"

    companion object {
        /** A new game from [startPosition], the standard start by default. */
        fun start(startPosition: Position = Position.START): Game = Game(listOf(startPosition), emptyList())

        /**
         * Rebuilds a game from its start FEN and moves in UCI form (as saved, design ADR-5). Every move
         * must be legal, and none may follow the end of the game.
         *
         * @throws FenException when [startFen] is not a valid position.
         * @throws IllegalMoveException when a move is malformed, illegal, or played after the end.
         */
        fun replay(startFen: String, ucis: List<String>): Game {
            val positions = ArrayList<Position>(ucis.size + 1)
            val moves = ArrayList<Move>(ucis.size)
            positions += Position.fromFen(startFen)
            for ((index, uci) in ucis.withIndex()) {
                val position = positions.last()
                val status = GameStatus.of(positions)
                if (status.isOver) {
                    throw IllegalMoveException("Move ${index + 1} ($uci) played after the game ended ($status)")
                }
                val move = Uci.parseOrNull(position, uci)
                    ?: throw IllegalMoveException("Move ${index + 1} ($uci) is not legal in ${position.toFen()}")
                moves += move
                positions += position.play(move)
            }
            return Game(positions, moves)
        }
    }
}
