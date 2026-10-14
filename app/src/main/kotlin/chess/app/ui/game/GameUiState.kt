package chess.app.ui.game

import androidx.compose.runtime.Immutable

// Game screen UI state (tech design §6.3). T-14 added the board part; T-15 adds `GameUiState`
// itself and `StatusUi`, filled from the engine's `Game` by `GameUiMapper`. Nothing here refers to
// engine types, so the screen can be previewed and tested without an engine.

/**
 * Everything the Game screen shows (tech design §6.3), derived from the ViewModel's private engine
 * `Game` by [GameUiMapper]. Later tasks add fields here rather than new flows:
 * - T-16: `overlay` (the promotion picker) and `result` (the result panel)
 * - T-17: a loading `phase`
 * - T-20: `canUndo` / `canResign`; T-22: `sanMoves`; T-24: the structured `announcement`
 *
 * @property level the computer's level, 1–4.
 * @property player the human's side; it is at the bottom of the board.
 */
@Immutable
data class GameUiState(
    val level: Int,
    val player: Side,
    val board: BoardUi,
    val status: StatusUi,
)

/**
 * The status row's state (design §4.3.4). Structured, not text: the row looks up the words in
 * `strings.xml`, so the ViewModel stays free of strings and its tests compare data.
 */
sealed interface StatusUi {
    /** The player's turn. */
    data object YourMove : StatusUi

    /** The player's turn, and the player's king is in check. */
    data object Check : StatusUi

    /** The computer's turn; [check] is true when the computer's king is in check. */
    data class Thinking(val check: Boolean) : StatusUi

    /** A transient hint after tapping an own piece that has no legal moves (2.5 s). */
    data object NoLegalMoves : StatusUi

    /** The computer failed to choose a move; the row offers **Try again** (design 4.3.8). */
    data object EngineError : StatusUi

    /**
     * The game has ended. Until T-16's result panel replaces the controls, the row shows the
     * result's title (design 4.3.7).
     */
    data class GameOver(val end: GameEnd) : StatusUi
}

/** How a game ended, from the player's point of view (design 4.3.7). Resignation arrives with T-20. */
enum class GameEnd {
    CheckmateWin,
    CheckmateLoss,
    Stalemate,
    ThreefoldRepetition,
    FiftyMoveRule,
    InsufficientMaterial,
}

/** A side of the board. The player's side is drawn at the bottom. */
enum class Side {
    White,
    Black;

    val opposite: Side
        get() = if (this == White) Black else White
}

enum class PieceKind { King, Queen, Rook, Bishop, Knight, Pawn }

@Immutable
data class PieceUi(val side: Side, val kind: PieceKind)

/**
 * Why a square is a legal destination of the selected piece. It decides the cue (dot or ring),
 * the TalkBack state words and the click label (design §4.3.3, §5.3).
 */
enum class TargetKind(val isCapture: Boolean) {
    /** Quiet move to an empty square: dot, "move available". */
    Move(isCapture = false),

    /** The king's castling destination: dot, "move available, castling". */
    Castling(isCapture = false),

    /** A pawn's quiet move to the last rank: dot, "move available, promotes". */
    Promotion(isCapture = false),

    /** An enemy piece the selected piece can take: ring, "capture available". */
    Capture(isCapture = true),

    /** The en passant destination (an empty square): ring, "capture available, en passant". */
    EnPassant(isCapture = true),

    /** A capture on the last rank by a pawn: ring, "capture available, promotes". */
    CapturePromotion(isCapture = true),
}

/** The last move's squares, for the tint, the corner mark and "last move from / to". */
@Immutable
data class LastMoveUi(val from: Int, val to: Int)

enum class LastMoveRole { From, To }

/**
 * Everything the board shows. Squares are `Int`s from 0 to 63 with a1 = 0 and h8 = 63, the same
 * numbering as the engine (see [Squares]).
 *
 * @property pieces 64 entries indexed by square; null is an empty square.
 * @property player the human's side. It is drawn at the bottom, and its pieces are the "own"
 *   pieces that get a **select** click action.
 * @property selected the selected square, if any.
 * @property targets the selected piece's legal destinations.
 * @property checkSquare the square of a king in check, if any.
 * @property interactive true on the player's turn while the game is on. When false, taps are
 *   ignored and no square has a click action (design §5.3).
 */
@Immutable
data class BoardUi(
    val pieces: List<PieceUi?>,
    val player: Side,
    val selected: Int? = null,
    val targets: Map<Int, TargetKind> = emptyMap(),
    val lastMove: LastMoveUi? = null,
    val checkSquare: Int? = null,
    val interactive: Boolean = false,
) {
    init {
        require(pieces.size == 64) { "A board has 64 squares, got ${pieces.size}" }
        selected?.let(::requireSquare)
        checkSquare?.let(::requireSquare)
        targets.keys.forEach(::requireSquare)
        lastMove?.let {
            requireSquare(it.from)
            requireSquare(it.to)
        }
    }

    /** The 64 cells, indexed by square. */
    val cells: List<CellUi>
        get() = List(64, ::cell)

    fun cell(square: Int): CellUi {
        requireSquare(square)
        return CellUi(
            square = square,
            piece = pieces[square],
            selected = square == selected,
            inCheck = square == checkSquare,
            target = targets[square],
            lastMove = when (square) {
                lastMove?.to -> LastMoveRole.To
                lastMove?.from -> LastMoveRole.From
                else -> null
            },
        )
    }

    /** True when [square] holds one of the player's pieces. */
    fun isOwnPiece(square: Int): Boolean = pieces[square]?.side == player

    private fun requireSquare(square: Int) {
        require(square in 0..63) { "Square must be in 0..63, was $square" }
    }
}

/** One square as the board draws and describes it. */
@Immutable
data class CellUi(
    val square: Int,
    val piece: PieceUi?,
    val selected: Boolean = false,
    val inCheck: Boolean = false,
    val target: TargetKind? = null,
    val lastMove: LastMoveRole? = null,
)

/**
 * A move the player can make from a square, for its TalkBack custom action (design §5.3). T-24
 * puts these on the squares; T-14 only provides the labels ([chess.app.ui.a11y.SquareDescriber]).
 *
 * @property captured the piece taken by a capture, en passant (a pawn) or capture-promotion.
 */
@Immutable
data class SquareActionUi(val to: Int, val kind: TargetKind, val captured: PieceKind? = null)

/** Square numbering helpers: a1 = 0, b1 = 1, …, h8 = 63, so `square = rank * 8 + file`. */
object Squares {
    // Algebraic coordinates are notation, not prose: they stay Latin in every language (plan T-29).
    private const val FILES = "abcdefgh"

    fun of(file: Int, rank: Int): Int = rank * 8 + file

    /** 0..7 for files a..h. */
    fun file(square: Int): Int = square and 7

    /** 0..7 for ranks 1..8. */
    fun rank(square: Int): Int = square shr 3

    /** File letter, "a".."h". */
    fun fileName(file: Int): String = FILES[file].toString()

    /** Rank digit, "1".."8". */
    fun rankName(rank: Int): String = (rank + 1).toString()

    /** "e4" for 28. */
    fun name(square: Int): String {
        require(square in 0..63) { "Square must be in 0..63, was $square" }
        return fileName(file(square)) + rankName(rank(square))
    }

    /** 28 for "e4". Lower case only. */
    fun parse(name: String): Int {
        require(name.length == 2) { "Invalid square name '$name'" }
        val file = FILES.indexOf(name[0])
        val rank = name[1] - '1'
        require(file >= 0 && rank in 0..7) { "Invalid square name '$name'" }
        return of(file, rank)
    }
}
