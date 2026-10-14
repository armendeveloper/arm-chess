package chess.engine.board

/** A malformed or impossible FEN. The message says which field is wrong and why. */
class FenException(message: String) : IllegalArgumentException(message)

/**
 * Forsyth-Edwards Notation for [Board] (design §4.1). Internal: the app goes through
 * `Position.fromFen`/`toFen` (T-6).
 *
 * [parse] checks structure and the rules that a saved or pasted FEN must not break: 8 ranks of
 * 8 files, exactly one king per side, no pawns on ranks 1 and 8, castling rights that match the
 * king and rook squares, an en passant square on the right rank behind a pawn that has just
 * double-pushed, and numeric clocks. The clocks may be omitted (4 fields), as in the Kiwipete perft
 * FEN; they then default to `0 1`. The side not to move being in check is rejected by
 * `Position.fromFen`, the public entry point; [parse] still accepts it (perft and board tests).
 * The parsed board's Zobrist key is computed before it is returned.
 */
internal object Fen {
    const val START: String = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    private val WHITESPACE = Regex("\\s+")

    /** Castling letters in canonical FEN order, with their rights and required squares. */
    private class CastlingSpec(val letter: Char, val right: Int, val color: Int, val kingSquare: Int, val rookSquare: Int)

    private val CASTLING = listOf(
        CastlingSpec('K', CastlingRights.WHITE_KINGSIDE, Piece.WHITE, Square.E1, Square.H1),
        CastlingSpec('Q', CastlingRights.WHITE_QUEENSIDE, Piece.WHITE, Square.E1, Square.A1),
        CastlingSpec('k', CastlingRights.BLACK_KINGSIDE, Piece.BLACK, Square.E8, Square.H8),
        CastlingSpec('q', CastlingRights.BLACK_QUEENSIDE, Piece.BLACK, Square.E8, Square.A8),
    )

    fun parse(fen: String): Board {
        val trimmed = fen.trim()
        if (trimmed.isEmpty()) throw FenException("FEN is empty")
        val fields = trimmed.split(WHITESPACE)
        if (fields.size != 6 && fields.size != 4) {
            throw FenException("FEN must have 6 fields (or 4 without clocks), found ${fields.size}")
        }

        val board = Board()
        parsePlacement(fields[0], board)
        checkKings(board)
        checkPawnRanks(board)
        board.sideToMove = parseSideToMove(fields[1])
        board.castlingRights = parseCastling(fields[2], board)
        board.epSquare = parseEnPassant(fields[3], board)
        if (fields.size == 6) {
            board.halfmoveClock = parseCounter(fields[4], min = 0) {
                "Halfmove clock must be a non-negative integer, found '$it'"
            }
            board.fullmoveNumber = parseCounter(fields[5], min = 1) {
                "Fullmove number must be a positive integer, found '$it'"
            }
        }
        board.resetKey()
        return board
    }

    fun format(board: Board): String = buildString {
        for (rank in 7 downTo 0) {
            var empty = 0
            for (file in 0 until 8) {
                val piece = board.pieceAt(Square.of(file, rank))
                if (piece == Piece.NONE) {
                    empty++
                } else {
                    if (empty > 0) append(empty)
                    empty = 0
                    append(Piece.toFenChar(piece))
                }
            }
            if (empty > 0) append(empty)
            if (rank > 0) append('/')
        }
        append(if (board.sideToMove == Piece.WHITE) " w " else " b ")
        val castling = CASTLING.filter { board.castlingRights and it.right != 0 }.map { it.letter }
        append(if (castling.isEmpty()) "-" else castling.joinToString(""))
        append(' ')
        append(if (board.epSquare == Square.NONE) "-" else Square.name(board.epSquare))
        append(' ').append(board.halfmoveClock)
        append(' ').append(board.fullmoveNumber)
    }

    private fun parsePlacement(placement: String, board: Board) {
        val ranks = placement.split('/')
        if (ranks.size != 8) throw FenException("Piece placement must have 8 ranks, found ${ranks.size}")
        for ((index, text) in ranks.withIndex()) {
            val rank = 7 - index
            val rankName = rank + 1
            // Validate characters and width before placing anything, so a long rank cannot overflow.
            var width = 0
            for (ch in text) {
                width += when {
                    ch in '1'..'8' -> ch - '0'
                    Piece.fromFenChar(ch) != Piece.NONE -> 1
                    else -> throw FenException("Invalid character '$ch' in rank $rankName")
                }
            }
            if (width != 8) throw FenException("Rank $rankName has $width files, expected 8")

            var file = 0
            for (ch in text) {
                if (ch in '1'..'8') {
                    file += ch - '0'
                } else {
                    board.setPiece(Square.of(file, rank), Piece.fromFenChar(ch))
                    file++
                }
            }
        }
    }

    private fun checkKings(board: Board) {
        for (color in PieceColor.entries) {
            val king = Piece.of(color.code, Piece.KING)
            val count = (0 until 64).count { board.pieceAt(it) == king }
            if (count != 1) throw FenException("${colorName(color.code)} must have exactly one king, found $count")
        }
    }

    private fun checkPawnRanks(board: Board) {
        for (rank in intArrayOf(0, 7)) {
            for (file in 0 until 8) {
                val sq = Square.of(file, rank)
                val piece = board.pieceAt(sq)
                if (Piece.typeOf(piece) == Piece.PAWN) {
                    throw FenException(
                        "${colorName(Piece.colorOf(piece))} pawn on ${Square.name(sq)}: pawns cannot stand on rank 1 or 8",
                    )
                }
            }
        }
    }

    private fun parseSideToMove(field: String): Int = when (field) {
        "w" -> Piece.WHITE
        "b" -> Piece.BLACK
        else -> throw FenException("Side to move must be 'w' or 'b', found '$field'")
    }

    private fun parseCastling(field: String, board: Board): Int {
        if (field == "-") return CastlingRights.NONE
        if (field.isEmpty() || field.length > 4 || field.any { ch -> CASTLING.none { it.letter == ch } }) {
            throw FenException("Invalid castling field '$field': use '-' or letters from 'KQkq'")
        }
        var rights = CastlingRights.NONE
        for (ch in field) {
            val spec = CASTLING.first { it.letter == ch }
            if (rights and spec.right != 0) throw FenException("Castling field '$field' repeats '$ch'")
            rights = rights or spec.right
        }
        for (spec in CASTLING) {
            if (rights and spec.right == 0) continue
            val name = colorName(spec.color).lowercase()
            if (board.pieceAt(spec.kingSquare) != Piece.of(spec.color, Piece.KING)) {
                throw FenException("Castling right '${spec.letter}' needs the $name king on ${Square.name(spec.kingSquare)}")
            }
            if (board.pieceAt(spec.rookSquare) != Piece.of(spec.color, Piece.ROOK)) {
                throw FenException("Castling right '${spec.letter}' needs a $name rook on ${Square.name(spec.rookSquare)}")
            }
        }
        return rights
    }

    /**
     * The en passant square must sit directly behind a pawn of the side that just moved, on rank 6
     * when White is to move (Black just pushed) or rank 3 when Black is to move, and both it and the
     * pawn's start square must be empty.
     */
    private fun parseEnPassant(field: String, board: Board): Int {
        if (field == "-") return Square.NONE
        val sq = Square.parseOrNull(field) ?: throw FenException("Invalid en passant square '$field'")
        val toMove = board.sideToMove
        val expectedRank = if (toMove == Piece.WHITE) 5 else 2
        if (Square.rank(sq) != expectedRank) {
            throw FenException(
                "En passant square $field must be on rank ${expectedRank + 1} when ${colorName(toMove)} is to move",
            )
        }
        val mover = 1 - toMove
        val forward = if (mover == Piece.WHITE) 8 else -8
        val pawnSquare = sq + forward
        val startSquare = sq - forward
        if (board.pieceAt(pawnSquare) != Piece.of(mover, Piece.PAWN)) {
            throw FenException(
                "En passant square $field needs a ${colorName(mover).lowercase()} pawn on ${Square.name(pawnSquare)}",
            )
        }
        if (board.pieceAt(sq) != Piece.NONE || board.pieceAt(startSquare) != Piece.NONE) {
            throw FenException("En passant squares $field and ${Square.name(startSquare)} must be empty")
        }
        return sq
    }

    private inline fun parseCounter(field: String, min: Int, message: (String) -> String): Int {
        val value = if (field.isNotEmpty() && field.all { it in '0'..'9' }) field.toIntOrNull() else null
        if (value == null || value < min) throw FenException(message(field))
        return value
    }

    private fun colorName(color: Int): String = if (color == Piece.WHITE) "White" else "Black"
}
