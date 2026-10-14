package chess.engine.search

import chess.engine.board.Attacks
import chess.engine.board.Board
import chess.engine.board.Move
import chess.engine.board.MoveGen
import chess.engine.board.Piece
import chess.engine.rules.Draws
import chess.engine.rules.Game
import kotlin.math.abs
import kotlin.math.max

/** Score constants, in centipawns from the side to move's point of view. */
internal object Score {
    const val DRAW: Int = 0

    /** Checkmate on the board; mate in n plies from the root scores `MATE - n`. */
    const val MATE: Int = 32_000

    /** Scores at least this far from 0 are mate scores. */
    const val MATE_BOUND: Int = MATE - 1_000

    /** Above any real score; never stored. */
    const val INFINITY: Int = 32_500
}

/** Sees each root move once it is fully searched: its index in this iteration's order, its score, and the node count so far. */
internal fun interface RootMoveListener {
    fun onRootMove(depth: Int, index: Int, move: Move, score: Int, nodes: Long)
}

/**
 * The search behind [Engine] (design §4.6): negamax alpha-beta with iterative deepening, quiescence,
 * a check extension, move ordering ([Ordering]) and an optional transposition table ([tt]; null
 * turns it off, for tests).
 *
 * Draws inside the search score 0: a position that repeats one on the current path or in the game
 * since the last pawn move or capture (a twofold repetition is enough), the 50-move rule (unless the
 * side to move is mated), stalemate and the PRD's insufficient-material cases.
 *
 * The search works on one mutable [Board] with preallocated move buffers, so it does not allocate
 * per node. Not thread-safe.
 */
internal class Search(private val tt: TranspositionTable?) {
    private val ordering = Ordering(MAX_PLY)
    private val moveBuffers = Array(MAX_PLY) { IntArray(MoveGen.MAX_MOVES) }
    private val scoreBuffers = Array(MAX_PLY) { IntArray(MoveGen.MAX_MOVES) }

    /** Keys of the positions before the current one: game history since the last irreversible move, then the search path. */
    private var keys = LongArray(INITIAL_KEYS)
    private var keyCount = 0

    // The state of the running search.
    private var board = Board()
    private var nodes = 0L
    private var nodeBudget = Long.MAX_VALUE
    private var deadlineNanos = Long.MAX_VALUE
    private var startNanos = 0L
    private var nanoTime: () -> Long = System::nanoTime
    private var shouldStop: () -> Boolean = { false }
    private var quiescencePlies = Int.MAX_VALUE
    private var quiescencePromotions = true
    private var iterationDepth = 0
    private var stopped = false

    /** Why the search ended: [StopReason.DepthCap] unless a limit was reached. */
    private var stopReason = StopReason.DepthCap

    /** Observes every fully searched root move; for tests of the iteration logic. Null in production. */
    var rootMoveListener: RootMoveListener? = null

    /** Forgets everything learned in earlier searches (a new game). */
    fun clear() {
        tt?.clear()
        ordering.clear()
    }

    fun run(game: Game, params: LevelParams, nanoTime: () -> Long, shouldStop: () -> Boolean): SearchResult {
        start(game, params, nanoTime, shouldStop)

        val rootMoves = IntArray(MoveGen.MAX_MOVES)
        val rootScores = IntArray(MoveGen.MAX_MOVES)
        val count = MoveGen.generateLegal(board, rootMoves)
        check(count > 0) { "An ongoing game has legal moves" }
        if (count == 1) {
            val only = Move(rootMoves[0])
            board.make(only)
            val score = -Eval.evaluate(board)
            board.unmake(only)
            return result(only, score, depth = 0, StopReason.OnlyMove)
        }
        orderRootMoves(rootMoves, rootScores, count)

        var bestMove = Move(rootMoves[0])
        var bestScore = -Score.INFINITY
        var completedDepth = 0
        for (depth in 1..params.depthCap) {
            if (depth > 1 && budgetOrDeadlineReached()) break
            iterationDepth = depth

            var alpha = -Score.INFINITY
            var iterationBest = -1
            keys[keyCount++] = board.key
            for (i in 0 until count) {
                val move = Move(rootMoves[i])
                board.make(move)
                val score = -negamax(depth - 1, -Score.INFINITY, -alpha, ply = 1)
                board.unmake(move)
                if (stopped) break
                rootScores[i] = score
                rootMoveListener?.onRootMove(depth, i, move, score, nodes)
                if (score > alpha) {
                    alpha = score
                    iterationBest = i
                }
            }
            keyCount--

            if (stopped) {
                // The previous best move was searched first. Use this iteration's result only if
                // another move has already beaten it, or if no iteration completed at all.
                if (iterationBest > 0 || (iterationBest == 0 && completedDepth == 0)) {
                    bestMove = Move(rootMoves[iterationBest])
                    bestScore = rootScores[iterationBest]
                }
                break
            }
            bestMove = Move(rootMoves[iterationBest])
            bestScore = alpha
            completedDepth = depth
            sortRootMoves(rootMoves, rootScores, count, iterationBest)
            // A proven mate cannot get shorter (or, when being mated, longer) with more depth.
            if (abs(bestScore) >= Score.MATE_BOUND && Score.MATE - abs(bestScore) <= depth) break
        }
        if (bestScore == -Score.INFINITY) bestScore = 0 // cancelled before any move was scored
        return result(bestMove, bestScore, completedDepth, stopReason)
    }

    private fun start(game: Game, params: LevelParams, nanoTime: () -> Long, shouldStop: () -> Boolean) {
        this.nanoTime = nanoTime
        this.shouldStop = shouldStop
        startNanos = nanoTime()
        nodeBudget = params.nodeBudget
        deadlineNanos = params.deadlineMs?.let { it * 1_000_000 } ?: Long.MAX_VALUE
        when (val q = params.quiescence) {
            Quiescence.Off -> { quiescencePlies = 0; quiescencePromotions = false }
            is Quiescence.CapturesOnly -> { quiescencePlies = q.maxPlies; quiescencePromotions = false }
            Quiescence.Full -> { quiescencePlies = Int.MAX_VALUE; quiescencePromotions = true }
        }
        nodes = 0
        stopped = false
        stopReason = StopReason.DepthCap
        iterationDepth = 0
        tt?.newSearch()
        ordering.newSearch()

        board = game.position.toBoard()
        val positions = game.positions
        val last = positions.lastIndex
        val earliest = max(0, last - positions[last].halfmoveClock)
        val needed = last - earliest + MAX_PLY + 1
        if (keys.size < needed) keys = LongArray(needed)
        keyCount = 0
        for (i in earliest until last) keys[keyCount++] = positions[i].repetitionKey
    }

    private fun result(move: Move, score: Int, depth: Int, reason: StopReason): SearchResult =
        SearchResult(move, score, depth, nodes, (nanoTime() - startNanos) / 1_000_000, reason)

    /**
     * Checked before each iteration after the first: sets [stopReason] when the node budget or the
     * deadline has been reached. `shouldStop` is only polled by [checkLimits].
     */
    private fun budgetOrDeadlineReached(): Boolean {
        stopReason = when {
            nodes >= nodeBudget -> StopReason.NodeBudget
            deadlinePassed() -> StopReason.Deadline
            else -> return false
        }
        return true
    }

    /** Reads the clock only when there is a deadline. */
    private fun deadlinePassed(): Boolean = deadlineNanos != Long.MAX_VALUE && nanoTime() - startNanos >= deadlineNanos

    /**
     * The periodic stop check, every [Engine.CHECK_INTERVAL] nodes. Cancellation is honoured at once;
     * the node budget and the deadline only after depth 1, which always completes.
     */
    private fun checkLimits() {
        if (shouldStop()) {
            stop(StopReason.Cancelled)
        } else if (iterationDepth > 1) {
            if (nodes >= nodeBudget) {
                stop(StopReason.NodeBudget)
            } else if (deadlinePassed()) {
                stop(StopReason.Deadline)
            }
        }
    }

    private fun stop(reason: StopReason) {
        stopped = true
        stopReason = reason
    }

    private fun countNode() {
        nodes++
        if (nodes and CHECK_MASK == 0L) checkLimits()
    }

    private fun negamax(depthIn: Int, alphaIn: Int, beta: Int, ply: Int): Int {
        if (isRepetition() || Draws.isInsufficientMaterial(board)) return Score.DRAW
        val us = board.sideToMove
        val inCheck = Attacks.isInCheck(board, us)
        val depth = if (inCheck) depthIn + 1 else depthIn
        if (depth <= 0) return quiescence(alphaIn, beta, ply, qPly = 0)
        countNode()
        if (stopped) return 0
        if (ply >= MAX_PLY - 1) return Eval.evaluate(board)

        // The 50-move rule draws unless the side to move is mated, which the move loop finds out.
        val fiftyMoveDraw = board.halfmoveClock >= Draws.FIFTY_MOVE_HALFMOVES
        val key = board.key
        var ttMove = Move.NONE
        if (tt != null && !fiftyMoveDraw) {
            val entry = tt.probe(key)
            if (TranspositionTable.isHit(entry)) {
                ttMove = TranspositionTable.move(entry)
                if (TranspositionTable.depth(entry) >= depth) {
                    val score = TranspositionTable.score(entry, ply)
                    when (TranspositionTable.bound(entry)) {
                        TranspositionTable.EXACT -> return score
                        TranspositionTable.LOWER -> if (score >= beta) return score
                        TranspositionTable.UPPER -> if (score <= alphaIn) return score
                    }
                }
            }
        }

        val moves = moveBuffers[ply]
        val scores = scoreBuffers[ply]
        val count = MoveGen.generatePseudoLegal(board, moves)
        for (i in 0 until count) scores[i] = ordering.score(board, Move(moves[i]), ttMove, ply)

        var alpha = alphaIn
        var best = -Score.INFINITY
        var bestMove = Move.NONE
        var legal = 0
        keys[keyCount++] = key
        for (i in 0 until count) {
            val move = Move(pickNext(moves, scores, i, count))
            board.make(move)
            if (Attacks.isAttacked(board, board.kingSquare(us), 1 - us)) {
                board.unmake(move)
                continue
            }
            legal++
            if (fiftyMoveDraw) {
                board.unmake(move)
                break
            }
            val score = -negamax(depth - 1, -beta, -alpha, ply + 1)
            board.unmake(move)
            if (stopped) {
                keyCount--
                return 0
            }
            if (score > best) {
                best = score
                bestMove = move
                if (score > alpha) {
                    alpha = score
                    if (alpha >= beta) {
                        if (!move.isCapture && !move.isPromotion) ordering.onQuietCutoff(us, move, ply, depth)
                        break
                    }
                }
            }
        }
        keyCount--

        if (legal == 0) return if (inCheck) -Score.MATE + ply else Score.DRAW
        if (fiftyMoveDraw) return Score.DRAW
        if (tt != null) {
            val bound = when {
                best >= beta -> TranspositionTable.LOWER
                best > alphaIn -> TranspositionTable.EXACT
                else -> TranspositionTable.UPPER
            }
            tt.store(key, if (bound == TranspositionTable.UPPER) Move.NONE else bestMove, best, depth, bound, ply)
        }
        return best
    }

    /**
     * Resolves captures (and, in [Quiescence.Full], queen promotions) until the position is quiet,
     * with stand-pat, MVV-LVA order and delta pruning. In [Quiescence.Off] it is the static eval.
     */
    private fun quiescence(alphaIn: Int, beta: Int, ply: Int, qPly: Int): Int {
        countNode()
        if (stopped) return 0
        val standPat = Eval.evaluate(board)
        if (qPly >= quiescencePlies || ply >= MAX_PLY - 1 || standPat >= beta) return standPat
        var alpha = max(alphaIn, standPat)

        val moves = moveBuffers[ply]
        val scores = scoreBuffers[ply]
        val pseudo = MoveGen.generatePseudoLegal(board, moves)
        var count = 0
        for (i in 0 until pseudo) {
            val move = Move(moves[i])
            if (move.isPromotion && move.promotion != Piece.QUEEN) continue
            if (move.isCapture || (quiescencePromotions && move.isPromotion)) {
                moves[count] = move.bits
                scores[count] = ordering.tacticalScore(board, move)
                count++
            }
        }

        val us = board.sideToMove
        var best = standPat
        for (i in 0 until count) {
            val move = Move(pickNext(moves, scores, i, count))
            if (!move.isPromotion) {
                val victim = if (move.isEnPassant) Piece.PAWN else Piece.typeOf(board.pieceAt(move.to))
                if (standPat + Eval.PIECE_VALUES[victim] + DELTA_MARGIN <= alpha) continue
            }
            board.make(move)
            if (Attacks.isAttacked(board, board.kingSquare(us), 1 - us)) {
                board.unmake(move)
                continue
            }
            val score = -quiescence(-beta, -alpha, ply + 1, qPly + 1)
            board.unmake(move)
            if (stopped) return 0
            if (score > best) {
                best = score
                if (score > alpha) {
                    alpha = score
                    if (alpha >= beta) break
                }
            }
        }
        return best
    }

    /**
     * True when the current position occurred before, since the last pawn move or capture, on the
     * search path or in the game. Only positions with the same side to move can match.
     */
    private fun isRepetition(): Boolean {
        val key = board.key
        val earliest = max(0, keyCount - board.halfmoveClock)
        var i = keyCount - 2
        while (i >= earliest) {
            if (keys[i] == key) return true
            i -= 2
        }
        return false
    }

    /** Root move order for depth 1: the transposition-table move, then by [Ordering]. */
    private fun orderRootMoves(moves: IntArray, scores: IntArray, count: Int) {
        val ttMove = tt?.probe(board.key)?.let { if (TranspositionTable.isHit(it)) TranspositionTable.move(it) else null } ?: Move.NONE
        for (i in 0 until count) scores[i] = ordering.score(board, Move(moves[i]), ttMove, 0)
        for (i in 0 until count) pickNext(moves, scores, i, count)
    }

    /** After an iteration: its best move first, then the rest by their scores (stable). */
    private fun sortRootMoves(moves: IntArray, scores: IntArray, count: Int, best: Int) {
        scores[best] = Score.INFINITY
        for (i in 1 until count) {
            val move = moves[i]
            val score = scores[i]
            var j = i - 1
            while (j >= 0 && scores[j] < score) {
                moves[j + 1] = moves[j]
                scores[j + 1] = scores[j]
                j--
            }
            moves[j + 1] = move
            scores[j + 1] = score
        }
    }

    /** Swaps the best-scored move of `[from, count)` into `from` and returns its bits. */
    private fun pickNext(moves: IntArray, scores: IntArray, from: Int, count: Int): Int {
        var bestIndex = from
        for (i in from + 1 until count) if (scores[i] > scores[bestIndex]) bestIndex = i
        if (bestIndex != from) {
            val move = moves[from]
            moves[from] = moves[bestIndex]
            moves[bestIndex] = move
            val score = scores[from]
            scores[from] = scores[bestIndex]
            scores[bestIndex] = score
        }
        return moves[from]
    }

    companion object {
        /** The deepest ply the search reaches, extensions and quiescence included. */
        const val MAX_PLY: Int = 128

        private const val CHECK_MASK = Engine.CHECK_INTERVAL - 1L
        private const val INITIAL_KEYS = 256 + MAX_PLY

        /** Quiescence skips a capture that cannot lift the score to alpha even with this to spare. */
        private const val DELTA_MARGIN = 200
    }
}
