package chess.engine.board

/**
 * Perft: the number of leaf nodes of the legal move tree to a fixed depth (design §4.2). Comparing
 * it with published counts is how move generation is proven correct; [divide] splits the count by
 * root move to find the first move whose subtree disagrees with a reference engine (for example
 * Stockfish's `go perft`).
 *
 * The FEN overloads are the public entry points; the [Board] ones are for engine tests.
 */
object Perft {
    /** Leaf nodes of [fen] at [depth] (≥ 0; depth 0 is the position itself, 1 node). */
    fun perft(fen: String, depth: Int): Long = perft(Fen.parse(fen), depth)

    /** Leaf nodes under each legal root move of [fen] at [depth] (≥ 1), keyed by UCI, in UCI order. */
    fun divide(fen: String, depth: Int): Map<String, Long> = divide(Fen.parse(fen), depth)

    /** As [perft] for a [Board]; the board is made and unmade along the way and ends unchanged. */
    internal fun perft(board: Board, depth: Int): Long {
        require(depth >= 0) { "Perft depth must be at least 0, was $depth" }
        return count(board, depth, buffers(depth))
    }

    /** As [divide] for a [Board]; the board ends unchanged. */
    internal fun divide(board: Board, depth: Int): Map<String, Long> {
        require(depth >= 1) { "Divide depth must be at least 1, was $depth" }
        val buffers = buffers(depth)
        val root = buffers[depth - 1]
        val count = MoveGen.generateLegal(board, root)
        val result = sortedMapOf<String, Long>()
        for (i in 0 until count) {
            val move = Move(root[i])
            board.make(move)
            result[move.uci()] = count(board, depth - 1, buffers)
            board.unmake(move)
        }
        return result
    }

    /** One move buffer per remaining depth, so the recursion does not allocate. */
    private fun buffers(depth: Int): Array<IntArray> = Array(depth) { IntArray(MoveGen.MAX_MOVES) }

    private fun count(board: Board, depth: Int, buffers: Array<IntArray>): Long {
        if (depth == 0) return 1
        val moves = buffers[depth - 1]
        val count = MoveGen.generateLegal(board, moves)
        // Bulk counting: at depth 1 the leaves are the legal moves themselves.
        if (depth == 1) return count.toLong()
        var nodes = 0L
        for (i in 0 until count) {
            val move = Move(moves[i])
            board.make(move)
            nodes += count(board, depth - 1, buffers)
            board.unmake(move)
        }
        return nodes
    }
}
