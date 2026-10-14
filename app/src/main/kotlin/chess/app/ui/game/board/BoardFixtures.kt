package chess.app.ui.game.board

import chess.app.ui.game.BoardUi
import chess.app.ui.game.LastMoveUi
import chess.app.ui.game.PieceKind
import chess.app.ui.game.PieceUi
import chess.app.ui.game.Side
import chess.app.ui.game.Squares
import chess.app.ui.game.TargetKind

/**
 * Hand-built boards for previews and tests. Real boards come from the engine through T-15's
 * `GameUiMapper`; this only reads the piece-placement field of a FEN, without any rules.
 */
internal object BoardFixtures {

    const val START_PLACEMENT = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"

    /** The 64 squares of a FEN piece-placement field (rank 8 first), indexed a1 = 0. */
    fun pieces(placement: String): List<PieceUi?> {
        val squares = arrayOfNulls<PieceUi>(64)
        val ranks = placement.split('/')
        require(ranks.size == 8) { "Placement needs 8 ranks: '$placement'" }
        ranks.forEachIndexed { index, rankText ->
            val rank = 7 - index
            var file = 0
            for (ch in rankText) {
                if (ch.isDigit()) {
                    file += ch.digitToInt()
                } else {
                    require(file < 8) { "Rank ${rank + 1} is too long: '$rankText'" }
                    squares[Squares.of(file, rank)] = pieceFor(ch)
                    file++
                }
            }
            require(file == 8) { "Rank ${rank + 1} must cover 8 files: '$rankText'" }
        }
        return squares.toList()
    }

    fun start(player: Side, interactive: Boolean = false) =
        BoardUi(pieces = pieces(START_PLACEMENT), player = player, interactive = interactive)

    /**
     * White to move after 1… Qd4-f2+. The white king on e1 is selected and in check; it can go to
     * d1 or d2 or take the queen on f2.
     */
    val kingInCheckSelected: BoardUi = BoardUi(
        pieces = pieces("4k3/8/8/8/8/8/4Pq2/R3K3"),
        player = Side.White,
        selected = Squares.parse("e1"),
        targets = mapOf(
            Squares.parse("d1") to TargetKind.Move,
            Squares.parse("d2") to TargetKind.Move,
            Squares.parse("f2") to TargetKind.Capture,
        ),
        lastMove = LastMoveUi(Squares.parse("d4"), Squares.parse("f2")),
        checkSquare = Squares.parse("e1"),
        interactive = true,
    )

    /**
     * Every cue at once, seen by Black, as a design review sheet rather than a legal position. The
     * g4 pawn is selected after White's f2-f4: a quiet move to g3, a capture on h3 and en passant
     * on f3. Castling (g8), promotion (b1) and capture-promotion (a1) targets and a check on e8 are
     * added so all six target kinds and the check badge show together.
     */
    val allCuesAsBlack: BoardUi = BoardUi(
        pieces = pieces("r3k2r/4p3/8/8/5Pp1/7N/1p6/R3K2R"),
        player = Side.Black,
        selected = Squares.parse("g4"),
        targets = mapOf(
            Squares.parse("g3") to TargetKind.Move,
            Squares.parse("h3") to TargetKind.Capture,
            Squares.parse("f3") to TargetKind.EnPassant,
            Squares.parse("g8") to TargetKind.Castling,
            Squares.parse("b1") to TargetKind.Promotion,
            Squares.parse("a1") to TargetKind.CapturePromotion,
        ),
        lastMove = LastMoveUi(Squares.parse("f2"), Squares.parse("f4")),
        checkSquare = Squares.parse("e8"),
        interactive = true,
    )

    private fun pieceFor(ch: Char): PieceUi {
        val kind = when (ch.lowercaseChar()) {
            'k' -> PieceKind.King
            'q' -> PieceKind.Queen
            'r' -> PieceKind.Rook
            'b' -> PieceKind.Bishop
            'n' -> PieceKind.Knight
            'p' -> PieceKind.Pawn
            else -> throw IllegalArgumentException("Not a piece letter: '$ch'")
        }
        return PieceUi(if (ch.isUpperCase()) Side.White else Side.Black, kind)
    }
}
