package chess.engine.rules

import chess.engine.board.Move
import chess.engine.board.Piece
import chess.engine.board.Square

/**
 * The piece letters of SAN. The engine never chooses a language: the move list uses [INTERNATIONAL]
 * (K Q R B N) for now, and T-29 may pass localized letters (e.g. Russian Кр Ф Л С К) without any change
 * to [San]. Castling stays `O-O` / `O-O-O` and squares stay a1..h8 whatever the letters.
 *
 * A letter may be several characters long. Letters must be distinct and must not contain whitespace
 * or anything else SAN uses: the files a..h, digits, `x`, `=`, `+`, `#`, `-`, `.`, `!`, `?`.
 */
data class SanLetters(
    val king: String,
    val queen: String,
    val rook: String,
    val bishop: String,
    val knight: String,
) {
    init {
        val all = listOf(king, queen, rook, bishop, knight)
        require(all.all { letter -> letter.isNotEmpty() && letter.none { it.isWhitespace() || it in RESERVED } }) {
            "SAN piece letters must be non-empty and free of whitespace and of '$RESERVED': $all"
        }
        require(all.toSet().size == all.size) { "SAN piece letters must be distinct: $all" }
    }

    /** The letter of a piece type code (Piece.KNIGHT..Piece.KING). */
    fun of(type: Int): String = when (type) {
        Piece.KING -> king
        Piece.QUEEN -> queen
        Piece.ROOK -> rook
        Piece.BISHOP -> bishop
        Piece.KNIGHT -> knight
        else -> throw IllegalArgumentException("Pawns have no SAN letter (type $type)")
    }

    /** (letter, type code), longest letter first, so "Кр" is tried before "К". */
    internal val byLength: List<Pair<String, Int>> =
        listOf(king to Piece.KING, queen to Piece.QUEEN, rook to Piece.ROOK, bishop to Piece.BISHOP, knight to Piece.KNIGHT)
            .sortedByDescending { it.first.length }

    companion object {
        private const val RESERVED = "abcdefgh0123456789x=+#-.!?"

        /** K Q R B N. */
        val INTERNATIONAL: SanLetters = SanLetters(king = "K", queen = "Q", rook = "R", bishop = "B", knight = "N")
    }
}

/**
 * Standard algebraic notation (design §4.4; US-11 move list, US-8 speech): `e4`, `Nbd7`, `R1e2`,
 * `Qh4xe1`, `exd6` (en passant, no suffix), `e8=Q`, `O-O`, `O-O-O`, with `+` for check and `#` for
 * mate, worked out from the resulting position. Disambiguation looks only at legal moves, so a pinned
 * piece never adds a file or rank.
 *
 * The output is notation, not prose; piece letters come from [SanLetters].
 */
object San {
    /**
     * The SAN of [move], which must be legal in [position].
     *
     * @throws IllegalMoveException when it is not.
     */
    fun format(position: Position, move: Move, letters: SanLetters = SanLetters.INTERNATIONAL): String {
        if (!position.isLegal(move)) throw IllegalMoveException("${move.uci()} is not legal in ${position.toFen()}")
        return format(position, move, position.play(move), letters)
    }

    /** The SAN of [move] from [before] to [after]; the caller guarantees `before.play(move) == after`. */
    internal fun format(before: Position, move: Move, after: Position, letters: SanLetters): String {
        val san = StringBuilder(8)
        appendMove(san, before, move, letters)
        if (after.isCheck) san.append(if (after.legalMoves().isEmpty()) '#' else '+')
        return san.toString()
    }

    /**
     * The legal move of [position] written [text] in SAN with [letters].
     *
     * Strict SAN is accepted, and so are inputs that leave something out or say more than needed:
     * a missing `+`/`#` (and `+` for mate), `0-0` / `0-0-0`, a promotion without `=` (`e8Q`), a missing
     * `x`, extra disambiguation (`Ng1f3`, or `e2e4`), a trailing ` e.p.`, annotations (`!`, `?`), and
     * surrounding spaces. Anything that claims something false is rejected: an `x` that captures
     * nothing, a `+`/`#`/`e.p.` that does not hold, and a promotion piece on a move that does not
     * promote. Piece letters are case sensitive.
     *
     * @throws IllegalMoveException when [text] is malformed, ambiguous or names no legal move.
     */
    fun parse(position: Position, text: String, letters: SanLetters = SanLetters.INTERNATIONAL): Move {
        val failure = StringBuilder()
        return resolve(position, text, letters, failure)
            ?: throw IllegalMoveException("'$text' is $failure in ${position.toFen()}")
    }

    /** Like [parse], but null instead of an exception. */
    fun parseOrNull(position: Position, text: String, letters: SanLetters = SanLetters.INTERNATIONAL): Move? =
        resolve(position, text, letters, StringBuilder())

    private fun appendMove(san: StringBuilder, position: Position, move: Move, letters: SanLetters) {
        if (move.isCastle) {
            san.append(if (Square.file(move.to) == FILE_G) SHORT_CASTLE else LONG_CASTLE)
            return
        }
        val type = Piece.typeOf(position.pieceAt(move.from))
        if (type == Piece.PAWN) {
            if (move.isCapture) san.append(FILES[Square.file(move.from)]).append('x')
            san.append(Square.name(move.to))
            if (move.isPromotion) san.append('=').append(letters.of(move.promotion))
            return
        }
        san.append(letters.of(type))
        if (type != Piece.KING) appendDisambiguation(san, position, move, type)
        if (move.isCapture) san.append('x')
        san.append(Square.name(move.to))
    }

    /** File if that is enough, else rank if that is enough, else both. */
    private fun appendDisambiguation(san: StringBuilder, position: Position, move: Move, type: Int) {
        var rivals = false
        var sameFile = false
        var sameRank = false
        for (other in position.legalMoves()) {
            if (other.to != move.to || other.from == move.from) continue
            if (Piece.typeOf(position.pieceAt(other.from)) != type) continue
            rivals = true
            if (Square.file(other.from) == Square.file(move.from)) sameFile = true
            if (Square.rank(other.from) == Square.rank(move.from)) sameRank = true
        }
        if (!rivals) return
        if (!sameFile) {
            san.append(FILES[Square.file(move.from)])
        } else if (!sameRank) {
            san.append(Square.rank(move.from) + 1)
        } else {
            san.append(Square.name(move.from))
        }
    }

    /** The move, or null with a short reason (for the exception message) appended to [failure]. */
    private fun resolve(position: Position, text: String, letters: SanLetters, failure: StringBuilder): Move? {
        fun fail(reason: String): Move? {
            failure.append(reason)
            return null
        }

        var body = text.trim().trimEnd('!', '?')
        var claimsEnPassant = false
        var claimsCheck = false
        var claimsMate = false
        // " e.p." may come before or after the check mark.
        repeat(2) {
            if (!claimsEnPassant && body.endsWith(EN_PASSANT)) {
                claimsEnPassant = true
                body = body.removeSuffix(EN_PASSANT).trimEnd()
            }
            if (!claimsCheck && !claimsMate) {
                when {
                    body.endsWith('#') -> claimsMate = true
                    body.endsWith('+') -> claimsCheck = true
                }
                if (claimsCheck || claimsMate) body = body.dropLast(1)
            }
        }

        val candidates = castlingCandidates(position, body) ?: pieceCandidates(position, body, letters) ?: return fail("malformed")
        val matches = candidates.filter { !claimsEnPassant || it.isEnPassant }
        val move = when (matches.size) {
            0 -> return fail("not legal")
            1 -> matches[0]
            else -> return fail("ambiguous")
        }
        if (claimsCheck || claimsMate) {
            val after = position.play(move)
            if (!after.isCheck) return fail("not check")
            if (claimsMate && after.legalMoves().isNotEmpty()) return fail("not mate")
        }
        return move
    }

    /** The legal castling moves [body] can mean, or null when it is not castling notation. */
    private fun castlingCandidates(position: Position, body: String): List<Move>? {
        val match = CASTLING.matchEntire(body) ?: return null
        val file = if (match.groupValues[2].isEmpty()) FILE_G else FILE_C
        return position.legalMoves().filter { it.isCastle && Square.file(it.to) == file }
    }

    /** The legal moves [body] can mean, or null when it is not piece or pawn notation. */
    private fun pieceCandidates(position: Position, body: String, letters: SanLetters): List<Move>? {
        val (letter, type) = letters.byLength.firstOrNull { body.startsWith(it.first) } ?: ("" to Piece.PAWN)
        val match = MOVE.matchEntire(body.substring(letter.length)) ?: return null
        val (fromFileText, fromRankText, captureText, toText, promotionText) = match.destructured
        val to = Square.parse(toText)
        val promotion = when {
            promotionText.isEmpty() -> Piece.NONE
            type != Piece.PAWN -> return null
            else -> letters.byLength.firstOrNull { it.first == promotionText }?.second
                ?.takeIf { it != Piece.KING } ?: return null
        }
        // A pawn move without a file stays on its file: "d5" is a push, never a capture.
        val fromFile = if (fromFileText.isNotEmpty()) fromFileText[0] - 'a' else if (type == Piece.PAWN) Square.file(to) else -1
        val fromRank = if (fromRankText.isNotEmpty()) fromRankText[0] - '1' else -1
        val claimsCapture = captureText.isNotEmpty()
        return position.legalMoves().filter { move ->
            !move.isCastle &&
                move.to == to &&
                move.promotion == promotion &&
                Piece.typeOf(position.pieceAt(move.from)) == type &&
                (fromFile < 0 || Square.file(move.from) == fromFile) &&
                (fromRank < 0 || Square.rank(move.from) == fromRank) &&
                (!claimsCapture || move.isCapture)
        }
    }

    private const val FILES = "abcdefgh"
    private const val FILE_C = 2
    private const val FILE_G = 6
    private const val SHORT_CASTLE = "O-O"
    private const val LONG_CASTLE = "O-O-O"
    private const val EN_PASSANT = "e.p."

    /** `O-O` or `O-O-O`, also with zeros. */
    private val CASTLING = Regex("([O0])-\\1(-\\1)?")

    /** After the piece letter: from file, from rank, `x`, to square, promotion (with or without `=`). */
    private val MOVE = Regex("([a-h]?)([1-8]?)(x?)([a-h][1-8])(?:=?(.+))?")
}
